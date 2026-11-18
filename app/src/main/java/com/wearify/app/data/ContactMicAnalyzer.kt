package com.wearify.app.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object ContactMicAnalyzer {
    private const val DISPLAY_SCALE = 10_000.0
    private const val MIN_ANALYSIS_SECONDS = 1.2

    fun analyze(
        points: List<ContactMicPoint>,
        sampleRateHz: Int = CONTACT_MIC_SAMPLE_RATE_HZ,
    ): ContactMicAnalysis {
        if (points.size < (sampleRateHz * MIN_ANALYSIS_SECONDS).toInt()) {
            return ContactMicAnalysis(sampleRateHz = sampleRateHz)
        }

        val timestamps = points.map { it.timestampMs }
        val raw = points.map { it.amplitude.toDouble() }.toDoubleArray()
        val base = detrend(raw)
        val trace = extractHeartbeatDubTrace(base, sampleRateHz)
        val peaks = detectHeartSoundPeaks(trace.envelope, sampleRateHz, points.size)
        val pairs = pairHeartSoundPeaks(peaks, sampleRateHz)
        val pairStarts = pairs.map { it.first }
        val intervals = pairStarts.zipWithNext { a, b -> (b - a).toDouble() / sampleRateHz }
        val intervalBpm = intervals.takeIf { it.isNotEmpty() }?.let { 60.0 / median(it) }
        val dominantPeakBpm = if (intervalBpm == null && peaks.isNotEmpty()) {
            strongestCyclePeakBpm(trace.envelope, sampleRateHz)
        } else {
            null
        }
        val bpm = intervalBpm ?: dominantPeakBpm
        val cv = intervals.takeIf { it.size > 1 }?.let {
            val mean = it.average()
            standardDeviation(it) / max(mean, 1e-9) * 100.0
        } ?: intervals.takeIf { it.size == 1 }?.let { 0.0 }
        val quality = qualityReport(
            base = base,
            trace = trace,
            peaks = peaks,
            pairs = pairs,
            bpm = bpm,
            cv = cv,
            usedDominantCyclePeak = dominantPeakBpm != null,
            sampleRateHz = sampleRateHz,
        )

        return ContactMicAnalysis(
            sampleRateHz = sampleRateHz,
            filteredPoints = normalizedDisplayPoints(trace.filtered, timestamps),
            envelopePoints = normalizedDisplayPoints(trace.envelope, timestamps),
            s1s2Pairs = pairs.map { (s1, s2) ->
                HeartSoundPair(
                    s1TimestampMs = timestamps[s1.coerceIn(timestamps.indices)],
                    s2TimestampMs = timestamps[s2.coerceIn(timestamps.indices)],
                    gapMs = ((s2 - s1) * 1_000L) / sampleRateHz,
                )
            },
            bpm = bpm?.roundTo(1),
            regularityCvPct = cv?.roundTo(1),
            qualityScore = quality.score,
            verdict = quality.verdict,
            reasons = quality.reasons,
        )
    }

    private fun extractHeartbeatDubTrace(base: DoubleArray, sampleRateHz: Int): HeartbeatTrace {
        val low = bandPass(base, sampleRateHz, 18.0, 70.0)
        val body = bandPass(base, sampleRateHz, 70.0, 140.0)
        val lowScale = robustScale(low)
        val bodyScale = robustScale(body)
        val filtered = DoubleArray(base.size) { index ->
            0.72 * (low[index] / lowScale) + 0.28 * (body[index] / bodyScale)
        }
        val filteredScale = robustScale(filtered)
        for (i in filtered.indices) {
            filtered[i] /= filteredScale
        }

        var envelope = movingRms(filtered, sampleRateHz, windowMs = 28.0)
        envelope = lowPass(envelope, sampleRateHz, cutoffHz = 14.0)
        envelope = movingAverage(envelope, sampleRateHz, windowMs = 35.0)
        val baseline = lowPass(envelope, sampleRateHz, cutoffHz = 0.9)
        for (i in envelope.indices) {
            envelope[i] = max(envelope[i] - baseline[i], 0.0)
        }
        return HeartbeatTrace(filtered = filtered, envelope = envelope)
    }

    private fun detectHeartSoundPeaks(
        envelope: DoubleArray,
        sampleRateHz: Int,
        totalSamples: Int,
    ): List<Int> {
        if (envelope.size < 3) return emptyList()

        val height = percentile(envelope, 72.0)
        val prominence = max(
            (percentile(envelope, 92.0) - percentile(envelope, 65.0)) * 0.30,
            standardDeviation(envelope.asList()) * 0.45,
        ).coerceAtLeast(1e-6)
        val localBaseline = percentile(envelope, 65.0)
        val edge = (sampleRateHz * 0.12).toInt()
        val minDistance = (sampleRateHz * 0.10).toInt()
        val candidates = mutableListOf<Int>()

        for (i in 1 until envelope.lastIndex) {
            if (i < edge || i > totalSamples - edge) continue
            if (envelope[i] <= height) continue
            if (envelope[i] - localBaseline < prominence) continue
            if (envelope[i] >= envelope[i - 1] && envelope[i] > envelope[i + 1]) {
                candidates += i
            }
        }

        val distanceFiltered = candidates
            .sortedByDescending { envelope[it] }
            .fold(mutableListOf<Int>()) { kept, candidate ->
                if (kept.none { abs(it - candidate) < minDistance }) {
                    kept += candidate
                }
                kept
            }
            .sorted()

        return distanceFiltered.filter { peak ->
            val width = peakWidthSeconds(envelope, peak, relativeHeight = 0.60, sampleRateHz)
            width in 0.02..0.18
        }
    }

    private fun pairHeartSoundPeaks(peaks: List<Int>, sampleRateHz: Int): List<Pair<Int, Int>> {
        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < peaks.size - 1) {
            val shortGap = (peaks[i + 1] - peaks[i]).toDouble() / sampleRateHz
            val nextGap = peaks.getOrNull(i + 2)?.let { (it - peaks[i + 1]).toDouble() / sampleRateHz }
            if (shortGap in 0.10..0.38 && (nextGap == null || nextGap >= max(0.28, shortGap * 1.35))) {
                pairs += peaks[i] to peaks[i + 1]
                i += 2
            } else {
                i++
            }
        }
        return pairs
    }

    /**
     * Returns the strongest normalized autocorrelation peak in the live heart-rate range.
     * This deliberately provides an early estimate when only one clear S1/S2 pair is
     * available; later analyses still replace it with the median observed cycle interval.
     */
    private fun strongestCyclePeakBpm(envelope: DoubleArray, sampleRateHz: Int): Double? {
        if (envelope.size < 3 || sampleRateHz <= 0) return null

        // The envelope contains no useful heart-rate detail at the raw 4 kHz rate.
        // Decimating keeps this live fallback inexpensive enough to run every 200 ms.
        val stride = max(sampleRateHz / 100, 1)
        val effectiveSampleRateHz = sampleRateHz.toDouble() / stride
        val centered = envelope.indices.step(stride).map { envelope[it] }.toDoubleArray()
        val mean = centered.average()
        for (index in centered.indices) centered[index] -= mean

        val minLag = (effectiveSampleRateHz * 60.0 / 180.0).roundToInt().coerceAtLeast(1)
        val maxLag = minOf(
            (effectiveSampleRateHz * 60.0 / 40.0).roundToInt(),
            centered.size - 2,
        )
        if (maxLag <= minLag) return null

        val scores = DoubleArray(maxLag + 1) { Double.NaN }
        for (lag in minLag..maxLag) {
            var cross = 0.0
            var leftEnergy = 0.0
            var rightEnergy = 0.0
            for (index in 0 until centered.size - lag) {
                val left = centered[index]
                val right = centered[index + lag]
                cross += left * right
                leftEnergy += left * left
                rightEnergy += right * right
            }
            val normalization = sqrt(leftEnergy * rightEnergy)
            if (normalization > 1e-9) scores[lag] = cross / normalization
        }

        val localPeaks = (minLag + 1 until maxLag).filter { lag ->
            scores[lag].isFinite() &&
                scores[lag] >= scores[lag - 1] &&
                scores[lag] > scores[lag + 1]
        }
        val strongestLag = (localPeaks.maxByOrNull { scores[it] }
            ?: (minLag..maxLag).filter { scores[it].isFinite() }.maxByOrNull { scores[it] })
            ?: return null
        return 60.0 * effectiveSampleRateHz / strongestLag
    }

    private fun qualityReport(
        base: DoubleArray,
        trace: HeartbeatTrace,
        peaks: List<Int>,
        pairs: List<Pair<Int, Int>>,
        bpm: Double?,
        cv: Double?,
        usedDominantCyclePeak: Boolean,
        sampleRateHz: Int,
    ): QualityReport {
        val env = trace.envelope
        val thumpContrast = (percentile(env, 95.0) / max(percentile(env, 50.0), 1e-6)).coerceAtMost(20.0)
        val heartBand = bandPass(base, sampleRateHz, 18.0, 160.0)
        val frictionBand = bandPass(base, sampleRateHz, 320.0, 1200.0)
        val frictionRatio = rms(frictionBand) / max(rms(heartBand), 1e-9)
        val reasons = mutableListOf<String>()
        var score = 100.0

        when {
            pairs.isEmpty() -> {
                score -= 45.0
                reasons += "too_few_clear_pairs"
            }
            pairs.size == 1 -> {
                score -= 22.0
                reasons += "few_clear_pairs"
            }
        }

        if (bpm == null) {
            score -= 20.0
            reasons += "no_stable_bpm"
        } else if (bpm < 45.0 || bpm > 160.0) {
            score -= 35.0
            reasons += "implausible_bpm"
        }
        if (usedDominantCyclePeak) {
            reasons += "bpm_from_dominant_cycle_peak"
        }

        if (cv != null && cv > 25.0) {
            score -= minOf(35.0, 12.0 + (cv - 25.0) * 0.8)
            reasons += "unstable_intervals"
        }

        when {
            thumpContrast < 2.2 -> {
                score -= 25.0
                reasons += "weak_heart_signal"
            }
            thumpContrast < 3.2 -> {
                score -= 12.0
                reasons += "low_thump_contrast"
            }
        }

        when {
            frictionRatio > 0.85 -> {
                score -= 25.0
                reasons += "rubbing_or_motion"
            }
            frictionRatio > 0.55 -> {
                score -= 12.0
                reasons += "possible_rubbing"
            }
        }

        val clipped = score.coerceIn(0.0, 100.0).roundTo(1)
        val verdict = when {
            peaks.isEmpty() -> ContactMicQualityVerdict.Waiting
            clipped >= 70.0 -> ContactMicQualityVerdict.GoodContact
            clipped >= 45.0 -> ContactMicQualityVerdict.MarginalContact
            else -> ContactMicQualityVerdict.LowConfidence
        }
        return QualityReport(
            score = clipped,
            verdict = verdict,
            reasons = reasons.ifEmpty { listOf("none") },
        )
    }

    private fun bandPass(
        input: DoubleArray,
        sampleRateHz: Int,
        lowHz: Double,
        highHz: Double,
    ): DoubleArray =
        lowPass(
            highPass(input, sampleRateHz, cutoffHz = lowHz),
            sampleRateHz,
            cutoffHz = highHz,
        )

    private fun lowPass(input: DoubleArray, sampleRateHz: Int, cutoffHz: Double): DoubleArray =
        forwardBackward(input, Biquad.lowPass(sampleRateHz.toDouble(), cutoffHz))

    private fun highPass(input: DoubleArray, sampleRateHz: Int, cutoffHz: Double): DoubleArray =
        forwardBackward(input, Biquad.highPass(sampleRateHz.toDouble(), cutoffHz))

    private fun forwardBackward(input: DoubleArray, coeffs: Biquad): DoubleArray {
        if (input.isEmpty()) return input
        val forward = processBiquad(input, coeffs)
        forward.reverse()
        val backward = processBiquad(forward, coeffs)
        backward.reverse()
        return backward
    }

    private fun processBiquad(input: DoubleArray, coeffs: Biquad): DoubleArray {
        val out = DoubleArray(input.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in input.indices) {
            val x0 = input[i]
            val y0 = coeffs.b0 * x0 + coeffs.b1 * x1 + coeffs.b2 * x2 - coeffs.a1 * y1 - coeffs.a2 * y2
            out[i] = y0
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        return out
    }

    private fun movingRms(input: DoubleArray, sampleRateHz: Int, windowMs: Double): DoubleArray {
        val halfWindow = max((sampleRateHz * windowMs / 1000.0 / 2.0).roundToInt(), 1)
        val prefix = DoubleArray(input.size + 1)
        for (i in input.indices) {
            prefix[i + 1] = prefix[i] + input[i] * input[i]
        }
        return DoubleArray(input.size) { index ->
            val start = max(index - halfWindow, 0)
            val end = minOf(index + halfWindow + 1, input.size)
            sqrt((prefix[end] - prefix[start]) / max(end - start, 1))
        }
    }

    private fun movingAverage(input: DoubleArray, sampleRateHz: Int, windowMs: Double): DoubleArray {
        val halfWindow = max((sampleRateHz * windowMs / 1000.0 / 2.0).roundToInt(), 1)
        val prefix = DoubleArray(input.size + 1)
        for (i in input.indices) {
            prefix[i + 1] = prefix[i] + input[i]
        }
        return DoubleArray(input.size) { index ->
            val start = max(index - halfWindow, 0)
            val end = minOf(index + halfWindow + 1, input.size)
            (prefix[end] - prefix[start]) / max(end - start, 1)
        }
    }

    private fun peakWidthSeconds(
        envelope: DoubleArray,
        peak: Int,
        relativeHeight: Double,
        sampleRateHz: Int,
    ): Double {
        val threshold = envelope[peak] * relativeHeight
        var left = peak
        while (left > 0 && envelope[left] > threshold) left--
        var right = peak
        while (right < envelope.lastIndex && envelope[right] > threshold) right++
        return (right - left).toDouble() / sampleRateHz
    }

    private fun normalizedDisplayPoints(values: DoubleArray, timestamps: List<Long>): List<ContactMicPoint> {
        val scale = robustScale(values)
        return values.mapIndexed { index, value ->
            ContactMicPoint(
                timestampMs = timestamps[index],
                amplitude = ((value / scale).coerceIn(-3.0, 3.0) * DISPLAY_SCALE / 3.0).roundToInt(),
            )
        }
    }

    private fun detrend(input: DoubleArray): DoubleArray {
        val mean = input.average()
        return DoubleArray(input.size) { index -> input[index] - mean }
    }

    private fun robustScale(input: DoubleArray): Double =
        max(percentile(input.map { abs(it) }.toDoubleArray(), 95.0), 1e-6)

    private fun rms(input: DoubleArray): Double =
        sqrt(input.fold(0.0) { sum, value -> sum + value * value } / max(input.size, 1))

    private fun percentile(input: DoubleArray, percentile: Double): Double {
        if (input.isEmpty()) return 0.0
        val sorted = input.copyOf()
        sorted.sort()
        val rank = (percentile / 100.0) * (sorted.size - 1)
        val low = rank.toInt().coerceIn(sorted.indices)
        val high = (low + 1).coerceAtMost(sorted.lastIndex)
        val fraction = rank - low
        return sorted[low] * (1.0 - fraction) + sorted[high] * fraction
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

    private fun standardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean).pow(2) } / values.size)
    }

    private fun Double.roundTo(decimals: Int): Double {
        val scale = 10.0.pow(decimals)
        return kotlin.math.round(this * scale) / scale
    }

    private data class HeartbeatTrace(
        val filtered: DoubleArray,
        val envelope: DoubleArray,
    )

    private data class QualityReport(
        val score: Double,
        val verdict: ContactMicQualityVerdict,
        val reasons: List<String>,
    )

    private data class Biquad(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double,
    ) {
        companion object {
            private const val BUTTERWORTH_Q = 0.70710678118

            fun lowPass(sampleRateHz: Double, cutoffHz: Double): Biquad {
                val omega = 2.0 * PI * cutoffHz / sampleRateHz
                val alpha = sin(omega) / (2.0 * BUTTERWORTH_Q)
                val cosOmega = cos(omega)
                val a0 = 1.0 + alpha
                return Biquad(
                    b0 = ((1.0 - cosOmega) / 2.0) / a0,
                    b1 = (1.0 - cosOmega) / a0,
                    b2 = ((1.0 - cosOmega) / 2.0) / a0,
                    a1 = (-2.0 * cosOmega) / a0,
                    a2 = (1.0 - alpha) / a0,
                )
            }

            fun highPass(sampleRateHz: Double, cutoffHz: Double): Biquad {
                val omega = 2.0 * PI * cutoffHz / sampleRateHz
                val alpha = sin(omega) / (2.0 * BUTTERWORTH_Q)
                val cosOmega = cos(omega)
                val a0 = 1.0 + alpha
                return Biquad(
                    b0 = ((1.0 + cosOmega) / 2.0) / a0,
                    b1 = -(1.0 + cosOmega) / a0,
                    b2 = ((1.0 + cosOmega) / 2.0) / a0,
                    a1 = (-2.0 * cosOmega) / a0,
                    a2 = (1.0 - alpha) / a0,
                )
            }
        }
    }
}
