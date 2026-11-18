package com.wearify.app.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PpgAnalyzer {
    private const val ANALYSIS_WINDOW_MS = 12_000L
    private const val MIN_ANALYSIS_SECONDS = 3.0
    private const val BASELINE_WINDOW_SECONDS = 2.0
    private const val SMOOTH_WINDOW_SECONDS = 0.24
    private const val BPM_WINDOW_MS = 8_000L
    private const val MIN_PEAK_DISTANCE_SECONDS = 0.35
    private const val RELAXED_PEAK_DISTANCE_SECONDS = 0.20
    private const val PRIMARY_THRESHOLD_FRACTION = 0.18
    private const val RELAXED_THRESHOLD_FRACTION = 0.045
    private const val HALF_RATE_MIN_SUPPORTING_INTERVALS = 3
    private const val HALF_RATE_MIN_SUPPORT_FRACTION = 0.75
    private const val HALF_RATE_MAX_TIMING_CV = 0.20
    private const val HALF_RATE_MIN_TEMPLATE_CORRELATION = 0.75
    private const val HALF_RATE_MAX_BPM = 180.0
    private const val MIN_IBI_MS = 300.0
    private const val MAX_IBI_MS = 2_000.0
    private const val LOW_RAW_CONTACT_THRESHOLD = 2_000.0
    private const val SATURATION_THRESHOLD = 250_000.0
    private const val FIRMWARE_CONTACT_FLAG = 0x01
    private const val FIRMWARE_SATURATION_FLAG = 0x02
    private const val FIRMWARE_MOTION_FLAG = 0x08
    private const val FIRMWARE_STATUS_WINDOW_MS = 2_000L

    fun analyze(samples: List<SensorSample>): PpgAnalysis {
        val newestTimestamp = samples.lastOrNull()?.timestampMs
            ?: return PpgAnalysis(reasons = listOf("no_samples"))
        val windowStart = newestTimestamp - ANALYSIS_WINDOW_MS
        val rawPoints = samples
            .asSequence()
            .filter { it.timestampMs in windowStart..newestTimestamp }
            .mapNotNull { sample ->
                sample.ir?.let {
                    RawPpgSample(
                        timestampMs = sample.timestampMs,
                        signalTimestampMs = sample.deviceUptimeMs ?: sample.timestampMs,
                        ir = it,
                    )
                }
            }
            .toList()

        if (rawPoints.size < 3) {
            return PpgAnalysis(
                verdict = PpgQualityVerdict.Waiting,
                reasons = listOf("waiting_for_ppg"),
                rawIr = rawPoints.lastOrNull()?.ir,
            )
        }

        val sampleRateHz = estimateSampleRate(rawPoints.map { it.signalTimestampMs })
        val durationSeconds = (rawPoints.last().signalTimestampMs - rawPoints.first().signalTimestampMs)
            .coerceAtLeast(0L) / 1_000.0
        val rawValues = rawPoints.map { it.ir }
        val rawMedian = median(rawValues)
        val rawMax = rawValues.maxOrNull() ?: 0.0
        val recentFirmwareFlags = samples.asSequence()
            .filter { it.timestampMs >= newestTimestamp - FIRMWARE_STATUS_WINDOW_MS }
            .mapNotNull { it.ppgFlags }
            .toList()
        val latestFirmwareFlags = recentFirmwareFlags.lastOrNull()
        val firmwareMotion = recentFirmwareFlags.any { (it and FIRMWARE_MOTION_FLAG) != 0 }
        val saturated = rawMax >= SATURATION_THRESHOLD ||
            recentFirmwareFlags.any { (it and FIRMWARE_SATURATION_FLAG) != 0 }
        val noContact = rawMedian < LOW_RAW_CONTACT_THRESHOLD ||
            (latestFirmwareFlags != null && (latestFirmwareFlags and FIRMWARE_CONTACT_FLAG) == 0)

        if (durationSeconds < MIN_ANALYSIS_SECONDS) {
            return PpgAnalysis(
                sampleRateHz = sampleRateHz,
                verdict = PpgQualityVerdict.Waiting,
                reasons = listOf("warming_up"),
                rawIr = rawPoints.last().ir,
            )
        }

        val baselineWindow = oddWindow(sampleRateHz * BASELINE_WINDOW_SECONDS, minWindow = 5)
        val smoothWindow = oddWindow(sampleRateHz * SMOOTH_WINDOW_SECONDS, minWindow = 3)
        val baseline = movingMedian(rawValues, baselineWindow)
        val detrended = rawValues.zip(baseline) { raw, base -> raw - base }
        val smoothed = movingAverage(detrended, smoothWindow)
        val smoothedP2p = percentile(smoothed, 95.0) - percentile(smoothed, 5.0)
        val acDcRatioPct = if (rawMedian > 0.0) {
            abs(smoothedP2p) / rawMedian * 100.0
        } else {
            null
        }

        val normalized = normalize(smoothed)
        val primaryPeaks = detectPeaks(
            displayTimestamps = rawPoints.map { it.timestampMs },
            signalTimestamps = rawPoints.map { it.signalTimestampMs },
            smoothed = smoothed,
            normalized = normalized,
            sampleRateHz = sampleRateHz,
        )
        val relaxedPeaks = detectPeaks(
            displayTimestamps = rawPoints.map { it.timestampMs },
            signalTimestamps = rawPoints.map { it.signalTimestampMs },
            smoothed = smoothed,
            normalized = normalized,
            sampleRateHz = sampleRateHz,
            minimumDistanceSeconds = RELAXED_PEAK_DISTANCE_SECONDS,
            thresholdFraction = RELAXED_THRESHOLD_FRACTION,
        )
        val halfRateResolution = resolvePersistentHalfRateCandidate(
            primaryPeaks = primaryPeaks,
            relaxedPeaks = relaxedPeaks,
            smoothed = smoothed,
            sampleRateHz = sampleRateHz,
            newestTimestamp = newestTimestamp,
        )
        val unguardedBpmReport = estimateBpm(primaryPeaks, newestTimestamp)
        val unguardedHalfRateReport = halfRateResolution?.let {
            estimateBpm(it.resolvedPeaks, newestTimestamp)
        }
        val motionScore = max(
            estimateMotionScore(samples, newestTimestamp),
            if (firmwareMotion) 100.0 else 0.0,
        )
        val bpmReport = if (firmwareMotion) BpmReport() else unguardedBpmReport
        val halfRateReport = if (firmwareMotion) null else unguardedHalfRateReport
        val qualityBpmReport = halfRateReport ?: bpmReport
        val qualityScore = computeQualityScore(
            acDcRatioPct = acDcRatioPct,
            bpmReport = qualityBpmReport,
            peakCount = halfRateResolution?.resolvedPeaks?.size ?: primaryPeaks.size,
            motionScore = motionScore,
            durationSeconds = durationSeconds,
        )

        val reasons = buildList {
            if (saturated) add("saturated")
            if (noContact) add("low_raw_ir")
            if ((acDcRatioPct ?: 0.0) < 0.05) add("weak_pulse")
            if (motionScore >= 70.0) add("motion_artifact")
            if (qualityBpmReport.bpm == null) add("unstable_peaks")
            if (halfRateReport?.bpm != null) add("half_rate_candidate")
        }.ifEmpty { listOf("ok") }

        val verdict = when {
            saturated -> PpgQualityVerdict.Saturated
            noContact || (acDcRatioPct ?: 0.0) < 0.05 -> PpgQualityVerdict.LowSignal
            motionScore >= 70.0 -> PpgQualityVerdict.MotionArtifact
            qualityBpmReport.bpm != null && qualityScore >= 70.0 -> PpgQualityVerdict.GoodContact
            qualityScore >= 45.0 -> PpgQualityVerdict.MarginalContact
            else -> PpgQualityVerdict.LowSignal
        }

        val processedPoints = rawPoints.mapIndexed { index, point ->
            PpgPoint(
                timestampMs = point.timestampMs,
                rawIr = point.ir,
                detrended = detrended[index],
                smoothed = smoothed[index],
                normalized = normalized[index],
            )
        }

        return PpgAnalysis(
            sampleRateHz = sampleRateHz,
            processedPoints = processedPoints,
            peaks = primaryPeaks.map { peak ->
                PpgPeak(
                    timestampMs = peak.timestampMs,
                    normalized = peak.normalized,
                )
            },
            bpm = bpmReport.bpm,
            halfRateCandidateBpm = halfRateReport?.bpm,
            qualityScore = qualityScore,
            verdict = verdict,
            reasons = reasons,
            rawIr = rawPoints.last().ir,
            acDcRatioPct = acDcRatioPct,
            motionScore = motionScore,
        )
    }

    private fun estimateSampleRate(timestampsMs: List<Long>): Double {
        if (timestampsMs.size < 2) return 25.0
        val deltas = timestampsMs
            .zipWithNext { a, b -> (b - a).toDouble() }
            .filter { it > 0.0 }
        if (deltas.isEmpty()) return 25.0
        return 1_000.0 / median(deltas)
    }

    private fun oddWindow(rawWindow: Double, minWindow: Int): Int {
        val rounded = ceil(rawWindow).roundToInt().coerceAtLeast(minWindow)
        return if (rounded % 2 == 0) rounded + 1 else rounded
    }

    private fun movingMedian(values: List<Double>, window: Int): List<Double> {
        val half = window / 2
        return values.indices.map { index ->
            val start = max(0, index - half)
            val endExclusive = min(values.size, index + half + 1)
            median(values.subList(start, endExclusive))
        }
    }

    private fun movingAverage(values: List<Double>, window: Int): List<Double> {
        val half = window / 2
        val prefix = DoubleArray(values.size + 1)
        values.forEachIndexed { index, value ->
            prefix[index + 1] = prefix[index] + value
        }
        return values.indices.map { index ->
            val start = max(0, index - half)
            val endExclusive = min(values.size, index + half + 1)
            (prefix[endExclusive] - prefix[start]) / (endExclusive - start).coerceAtLeast(1)
        }
    }

    private fun normalize(values: List<Double>): List<Double> {
        if (values.isEmpty()) return emptyList()
        val mean = values.average()
        val variance = values.sumOf { (it - mean).pow(2.0) } / values.size.coerceAtLeast(1)
        val std = sqrt(variance).coerceAtLeast(1e-6)
        return values.map { ((it - mean) / std).coerceIn(-3.0, 3.0) }
    }

    private fun detectPeaks(
        displayTimestamps: List<Long>,
        signalTimestamps: List<Long>,
        smoothed: List<Double>,
        normalized: List<Double>,
        sampleRateHz: Double,
        minimumDistanceSeconds: Double = MIN_PEAK_DISTANCE_SECONDS,
        thresholdFraction: Double = PRIMARY_THRESHOLD_FRACTION,
    ): List<DetectedPeak> {
        if (smoothed.size < 3) return emptyList()
        val p2p = percentile(smoothed, 95.0) - percentile(smoothed, 5.0)
        if (abs(p2p) < 25.0) return emptyList()

        val minDistanceSamples = (sampleRateHz * minimumDistanceSeconds)
            .roundToInt()
            .coerceAtLeast(1)
        val threshold = percentile(smoothed, 55.0) + p2p * thresholdFraction
        val peaks = mutableListOf<Int>()

        for (index in 1 until smoothed.lastIndex) {
            val isLocalMax = smoothed[index] > smoothed[index - 1] &&
                smoothed[index] >= smoothed[index + 1]
            if (!isLocalMax || smoothed[index] < threshold) continue

            val lastPeak = peaks.lastOrNull()
            if (lastPeak != null && index - lastPeak < minDistanceSamples) {
                if (smoothed[index] > smoothed[lastPeak]) {
                    peaks[peaks.lastIndex] = index
                }
            } else {
                peaks += index
            }
        }

        return peaks.map { index ->
            DetectedPeak(
                sampleIndex = index,
                timestampMs = displayTimestamps[index],
                signalTimestampMs = signalTimestamps[index],
                value = smoothed[index],
                normalized = normalized[index],
            )
        }
    }

    private fun resolvePersistentHalfRateCandidate(
        primaryPeaks: List<DetectedPeak>,
        relaxedPeaks: List<DetectedPeak>,
        smoothed: List<Double>,
        sampleRateHz: Double,
        newestTimestamp: Long,
    ): HalfRateResolution? {
        val recentPrimary = primaryPeaks.filter {
            it.timestampMs >= newestTimestamp - BPM_WINDOW_MS
        }
        val recentRelaxed = relaxedPeaks.filter {
            it.timestampMs >= newestTimestamp - BPM_WINDOW_MS
        }
        if (
            recentPrimary.size < HALF_RATE_MIN_SUPPORTING_INTERVALS + 1 ||
            recentRelaxed.size <= recentPrimary.size
        ) {
            return null
        }

        val primaryIntervals = recentPrimary.zipWithNext { left, right ->
            (right.signalTimestampMs - left.signalTimestampMs).toDouble()
        }.filter { it > 0.0 }
        if (primaryIntervals.size < HALF_RATE_MIN_SUPPORTING_INTERVALS) return null

        val primaryMedianMs = median(primaryIntervals)
        val proposedIbiMs = primaryMedianMs / 2.0
        val proposedBpm = 60_000.0 / proposedIbiMs
        if (proposedBpm !in 40.0..HALF_RATE_MAX_BPM) return null

        val stablePrimaryIntervals = primaryIntervals.count {
            abs(it - primaryMedianMs) <= max(100.0, primaryMedianMs * 0.15)
        }
        if (
            stablePrimaryIntervals.toDouble() / primaryIntervals.size <
            HALF_RATE_MIN_SUPPORT_FRACTION
        ) {
            return null
        }

        val midpointToleranceMs = max(80.0, proposedIbiMs * 0.16)
        val inserted = mutableListOf<DetectedPeak>()
        var eligibleIntervals = 0
        var supportedIntervals = 0
        for ((left, right) in recentPrimary.zipWithNext()) {
            val intervalMs = (right.signalTimestampMs - left.signalTimestampMs).toDouble()
            if (abs(intervalMs - primaryMedianMs) > max(100.0, primaryMedianMs * 0.15)) {
                continue
            }
            eligibleIntervals += 1
            val targetMs = (left.signalTimestampMs + right.signalTimestampMs) / 2.0
            val candidate = recentRelaxed
                .asSequence()
                .filter { it.signalTimestampMs > left.signalTimestampMs }
                .filter { it.signalTimestampMs < right.signalTimestampMs }
                .minByOrNull { abs(it.signalTimestampMs - targetMs) }
            if (candidate != null && abs(candidate.signalTimestampMs - targetMs) <= midpointToleranceMs) {
                inserted += candidate
                supportedIntervals += 1
            }
        }
        if (
            eligibleIntervals < HALF_RATE_MIN_SUPPORTING_INTERVALS ||
            supportedIntervals < HALF_RATE_MIN_SUPPORTING_INTERVALS ||
            supportedIntervals.toDouble() / eligibleIntervals < HALF_RATE_MIN_SUPPORT_FRACTION
        ) {
            return null
        }

        val resolvedPeaks = (recentPrimary + inserted)
            .distinctBy { it.sampleIndex }
            .sortedBy { it.signalTimestampMs }
        if (resolvedPeaks.size < recentPrimary.size + HALF_RATE_MIN_SUPPORTING_INTERVALS) {
            return null
        }
        val resolvedIntervals = resolvedPeaks.zipWithNext { left, right ->
            (right.signalTimestampMs - left.signalTimestampMs).toDouble()
        }
        val normalizedIntervals = resolvedIntervals.map { interval ->
            if (interval > proposedIbiMs * 1.5) interval / 2.0 else interval
        }
        val meanIbi = normalizedIntervals.average()
        val sdIbi = sqrt(
            normalizedIntervals.sumOf { (it - meanIbi).pow(2.0) } /
                normalizedIntervals.size.coerceAtLeast(1),
        )
        if (meanIbi <= 0.0 || sdIbi / meanIbi > HALF_RATE_MAX_TIMING_CV) return null

        val templateCorrelation = templateCorrelation(
            values = smoothed,
            peaks = resolvedPeaks,
            periodSamples = proposedIbiMs / 1_000.0 * sampleRateHz,
        )
        if (templateCorrelation < HALF_RATE_MIN_TEMPLATE_CORRELATION) return null

        return HalfRateResolution(resolvedPeaks = resolvedPeaks)
    }

    private fun templateCorrelation(
        values: List<Double>,
        peaks: List<DetectedPeak>,
        periodSamples: Double,
    ): Double {
        if (!periodSamples.isFinite() || periodSamples <= 0.0) return 0.0
        val halfWidth = max((periodSamples * 0.32).roundToInt(), 2)
        val segments = peaks.mapNotNull { peak ->
            val start = peak.sampleIndex - halfWidth
            val endExclusive = peak.sampleIndex + halfWidth + 1
            if (start < 0 || endExclusive > values.size) return@mapNotNull null
            val segment = values.subList(start, endExclusive)
            val mean = segment.average()
            val centered = segment.map { it - mean }
            val scale = sqrt(centered.sumOf { it * it }).coerceAtLeast(1e-9)
            centered.map { it / scale }
        }
        if (segments.size < 4) return 0.0
        val template = segments.first().indices.map { index ->
            median(segments.map { it[index] })
        }
        val templateScale = sqrt(template.sumOf { it * it }).coerceAtLeast(1e-9)
        val correlations = segments.map { segment ->
            segment.indices.sumOf { index -> segment[index] * template[index] } /
                templateScale
        }
        return median(correlations)
    }

    private fun estimateBpm(peaks: List<DetectedPeak>, newestTimestamp: Long): BpmReport {
        val recentPeaks = peaks.filter { it.timestampMs >= newestTimestamp - BPM_WINDOW_MS }
        if (recentPeaks.size < 3) return BpmReport()

        val observedIntervals = recentPeaks
            .zipWithNext { a, b -> (b.signalTimestampMs - a.signalTimestampMs).toDouble() }
            .filter { it in MIN_IBI_MS..MAX_IBI_MS }
        if (observedIntervals.size < 2) return BpmReport()

        // Motion can suppress one lobe of a pulse or turn two neighboring
        // pulses into a double-headed shape. The detector may then omit one
        // beat, leaving an otherwise regular IBI sequence with an isolated
        // interval near 2x (occasionally 3x) the usual cycle. Normalize only
        // those unambiguous integer multiples. Non-multiples remain unchanged
        // and are rejected by the variability gate below.
        val referenceIbi = median(observedIntervals)
        val referenceSupport = observedIntervals.count {
            abs(it - referenceIbi) <= max(80.0, referenceIbi * 0.18)
        }
        val intervals = if (referenceSupport >= 2) {
            observedIntervals.map { interval ->
                val cycleMultiple = (interval / referenceIbi).roundToInt()
                val perCycle = interval / cycleMultiple.coerceAtLeast(1)
                if (
                    cycleMultiple in 2..3 &&
                    abs(perCycle - referenceIbi) <= max(80.0, referenceIbi * 0.18)
                ) {
                    perCycle
                } else {
                    interval
                }
            }
        } else {
            observedIntervals
        }

        val meanIbi = intervals.average()
        val sd = sqrt(intervals.sumOf { (it - meanIbi).pow(2.0) } / intervals.size)
        val cv = if (meanIbi > 0.0) sd / meanIbi else Double.POSITIVE_INFINITY
        if (cv > 0.40) return BpmReport(intervalCv = cv)

        return BpmReport(
            bpm = 60_000.0 / meanIbi,
            intervalCv = cv,
        )
    }

    private fun estimateMotionScore(samples: List<SensorSample>, newestTimestamp: Long): Double {
        val recent = samples.filter { it.timestampMs >= newestTimestamp - 2_000L }
        if (recent.size < 3) return 0.0

        val accelDeviation = recent.mapNotNull { sample ->
            val ax = sample.ax ?: return@mapNotNull null
            val ay = sample.ay ?: return@mapNotNull null
            val az = sample.az ?: return@mapNotNull null
            if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return@mapNotNull null
            abs(sqrt(ax * ax + ay * ay + az * az) - 1.0)
        }
        val gyroMagnitude = recent.mapNotNull { sample ->
            val gx = sample.gx ?: return@mapNotNull null
            val gy = sample.gy ?: return@mapNotNull null
            val gz = sample.gz ?: return@mapNotNull null
            if (!gx.isFinite() || !gy.isFinite() || !gz.isFinite()) return@mapNotNull null
            sqrt(gx * gx + gy * gy + gz * gz)
        }

        val accelScore = accelDeviation
            .takeIf { it.isNotEmpty() }
            ?.let { ((it.average() - 0.03) / 0.18 * 100.0).coerceIn(0.0, 100.0) }
            ?: 0.0
        val gyroScore = gyroMagnitude
            .takeIf { it.isNotEmpty() }
            ?.let { ((it.average() - 8.0) / 90.0 * 100.0).coerceIn(0.0, 100.0) }
            ?: 0.0

        return max(accelScore, gyroScore)
    }

    private fun computeQualityScore(
        acDcRatioPct: Double?,
        bpmReport: BpmReport,
        peakCount: Int,
        motionScore: Double,
        durationSeconds: Double,
    ): Double {
        val signalScore = acDcRatioPct
            ?.let { ((it - 0.05) / 0.75 * 100.0).coerceIn(0.0, 100.0) }
            ?: 0.0
        val peakScore = when {
            bpmReport.bpm != null -> 100.0 - (bpmReport.intervalCv * 180.0).coerceIn(0.0, 55.0)
            peakCount >= 3 -> 42.0
            peakCount >= 1 -> 20.0
            else -> 0.0
        }
        val coverageScore = (durationSeconds / (ANALYSIS_WINDOW_MS / 1_000.0) * 100.0)
            .coerceIn(0.0, 100.0)

        return (
            signalScore * 0.42 +
                peakScore * 0.38 +
                coverageScore * 0.20 -
                motionScore * 0.34
            ).coerceIn(0.0, 100.0)
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    private fun percentile(values: List<Double>, percentile: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val rank = (percentile.coerceIn(0.0, 100.0) / 100.0) * (sorted.size - 1)
        val lower = floor(rank).toInt()
        val upper = ceil(rank).toInt()
        if (lower == upper) return sorted[lower]
        val weight = rank - lower
        return sorted[lower] * (1.0 - weight) + sorted[upper] * weight
    }

    private data class DetectedPeak(
        val sampleIndex: Int,
        val timestampMs: Long,
        val signalTimestampMs: Long,
        val value: Double,
        val normalized: Double,
    )

    private data class RawPpgSample(
        val timestampMs: Long,
        val signalTimestampMs: Long,
        val ir: Double,
    )

    private data class BpmReport(
        val bpm: Double? = null,
        val intervalCv: Double = 1.0,
    )

    private data class HalfRateResolution(
        val resolvedPeaks: List<DetectedPeak>,
    )
}
