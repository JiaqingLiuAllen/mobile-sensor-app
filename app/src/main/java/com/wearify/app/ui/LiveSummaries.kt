package com.wearify.app.ui

import com.wearify.app.data.ContactMicAnalysis
import com.wearify.app.data.ContactMicQualityVerdict
import com.wearify.app.data.SensorSample
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

internal enum class MotionLevel(val label: String) {
    Unknown("Collecting"),
    Still("Still"),
    Light("Light movement"),
    Moving("Moving"),
}

internal data class MotionSummary(
    val level: MotionLevel,
    val score: Double?,
)

internal enum class LightLevel(val label: String) {
    Unknown("Collecting"),
    Dim("Dim"),
    Moderate("Moderate"),
    Bright("Bright"),
}

internal enum class UvExposureLevel(val label: String) {
    Unknown("Collecting"),
    Low("Low"),
    Moderate("Moderate"),
    High("High"),
}

internal enum class EnvironmentEstimate(val label: String) {
    Indoor("Indoors"),
    Outdoor("Likely outdoors"),
}

internal data class EnvironmentSummary(
    val environment: EnvironmentEstimate,
    val light: LightLevel,
    val uvExposure: UvExposureLevel,
    val nearBodyTemperatureC: Double?,
)

internal data class HeartSoundSummary(
    val s1ToS2Ms: Long?,
    val timingLabel: String,
    val supportLabel: String,
)

internal data class StableHeartRateState(
    val displayedBpm: Double? = null,
    val pendingBpm: Double? = null,
    val pendingCount: Int = 0,
    val isCurrent: Boolean = false,
)

internal fun summarizeMotion(samples: List<SensorSample>): MotionSummary {
    val recent = recentSamples(samples, 5_000L)
    val accelerationMagnitudes = recent.mapNotNull { sample ->
        val ax = sample.ax?.takeIf(Double::isFinite) ?: return@mapNotNull null
        val ay = sample.ay?.takeIf(Double::isFinite) ?: return@mapNotNull null
        val az = sample.az?.takeIf(Double::isFinite) ?: return@mapNotNull null
        sqrt(ax * ax + ay * ay + az * az)
    }
    if (accelerationMagnitudes.size < 10) return MotionSummary(MotionLevel.Unknown, null)

    val gyroMagnitudes = recent.mapNotNull { sample ->
        val gx = sample.gx?.takeIf(Double::isFinite) ?: return@mapNotNull null
        val gy = sample.gy?.takeIf(Double::isFinite) ?: return@mapNotNull null
        val gz = sample.gz?.takeIf(Double::isFinite) ?: return@mapNotNull null
        sqrt(gx * gx + gy * gy + gz * gz)
    }
    val accelerationVariation = standardDeviation(accelerationMagnitudes)
    val gyroMedian = median(gyroMagnitudes) ?: 0.0
    val accelerationScore = ramp(accelerationVariation, 0.015, 0.12)
    val gyroScore = ramp(gyroMedian, 2.0, 28.0)
    val score = max(accelerationScore, gyroScore)
    val level = when {
        score < 0.28 -> MotionLevel.Still
        score < 0.62 -> MotionLevel.Light
        else -> MotionLevel.Moving
    }
    return MotionSummary(level, score)
}

internal fun summarizeEnvironment(samples: List<SensorSample>): EnvironmentSummary {
    val recent = recentSamples(samples, 10_000L)
    val uv = median(recent.mapNotNull { it.uv?.takeIf(Double::isFinite) })
    val als = median(recent.mapNotNull { it.als?.takeIf(Double::isFinite) })
    val temperature = median(recent.mapNotNull { it.tempC?.takeIf(Double::isFinite) })

    val lightLevel = when {
        als == null -> LightLevel.Unknown
        als < 1_000.0 -> LightLevel.Dim
        als < 10_000.0 -> LightLevel.Moderate
        else -> LightLevel.Bright
    }
    val uvLevel = when {
        uv == null -> UvExposureLevel.Unknown
        uv < 5.0 -> UvExposureLevel.Low
        uv < 50.0 -> UvExposureLevel.Moderate
        else -> UvExposureLevel.High
    }
    val weightedScores = buildList {
        uv?.let { add(0.75 to ramp(it, 2.0, 8.0)) }
        als?.let { add(0.25 to ramp(it, 2_000.0, 2_500.0)) }
    }
    val outdoorScore = if (weightedScores.isEmpty()) {
        0.5
    } else {
        weightedScores.sumOf { (weight, score) -> weight * score } /
            weightedScores.sumOf { it.first }
    }
    val environment = if (outdoorScore > 0.5) {
        EnvironmentEstimate.Outdoor
    } else {
        EnvironmentEstimate.Indoor
    }
    return EnvironmentSummary(environment, lightLevel, uvLevel, temperature)
}

internal fun summarizeHeartSound(analysis: ContactMicAnalysis): HeartSoundSummary {
    val supported = analysis.verdict in setOf(
        ContactMicQualityVerdict.GoodContact,
        ContactMicQualityVerdict.MarginalContact,
    )
    val gap = if (supported) median(analysis.s1s2Pairs.map { it.gapMs.toDouble() })?.toLong() else null
    val timing = when {
        gap == null -> "Timing not ready"
        analysis.regularityCvPct == null && analysis.bpm != null -> "Rate from strongest cycle peak"
        analysis.regularityCvPct == null -> "Cycle timing collecting"
        analysis.regularityCvPct <= 12.0 -> "Cycle timing steady"
        analysis.regularityCvPct <= 25.0 -> "Some cycle variation"
        else -> "Cycle timing variable"
    }
    val support = when {
        analysis.verdict == ContactMicQualityVerdict.Waiting -> "Collecting heart sounds"
        !supported -> "Heart-sound timing withheld"
        else -> "S1 → S2 sound interval"
    }
    return HeartSoundSummary(gap, timing, support)
}

internal fun updateStableHeartRate(
    previous: StableHeartRateState,
    candidateBpm: Double?,
): StableHeartRateState {
    val candidate = candidateBpm?.takeIf { it.isFinite() && it in 40.0..200.0 }
        ?: return previous.copy(isCurrent = false)
    val displayed = previous.displayedBpm
        ?: return StableHeartRateState(displayedBpm = candidate, isCurrent = true)
    val delta = candidate - displayed
    if (abs(delta) <= 8.0) {
        val smoothed = displayed + delta * 0.22
        return StableHeartRateState(
            displayedBpm = (smoothed * 10.0).toInt() / 10.0,
            isCurrent = true,
        )
    }

    val continuesPending = previous.pendingBpm?.let { abs(candidate - it) <= 6.0 } == true
    val pendingCount = if (continuesPending) previous.pendingCount + 1 else 1
    if (pendingCount < 3) {
        return previous.copy(
            pendingBpm = candidate,
            pendingCount = pendingCount,
            isCurrent = false,
        )
    }
    val boundedStep = delta.coerceIn(-4.0, 4.0)
    return StableHeartRateState(
        displayedBpm = ((displayed + boundedStep) * 10.0).toInt() / 10.0,
        isCurrent = true,
    )
}

private fun recentSamples(samples: List<SensorSample>, windowMs: Long): List<SensorSample> {
    val end = samples.lastOrNull()?.timestampMs ?: return emptyList()
    return samples.filter { it.timestampMs >= end - windowMs }
}

private fun ramp(value: Double, low: Double, high: Double): Double =
    ((value - low) / (high - low)).coerceIn(0.0, 1.0)

private fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else {
        sorted[middle]
    }
}

private fun standardDeviation(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val mean = values.average()
    return sqrt(values.sumOf { (it - mean).pow(2) } / values.size)
}
