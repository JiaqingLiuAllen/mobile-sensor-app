package com.wearify.app.data

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PpgAnalyzerTest {
    @Test
    fun estimatesHeartRateFromCleanPpg() {
        val heartRateBpm = 72.0
        val samples = cleanPpgSamples(heartRateBpm)

        val analysis = PpgAnalyzer.analyze(samples)

        assertNotNull(analysis.bpm)
        assertEquals(heartRateBpm, analysis.bpm!!, 3.0)
        assertTrue(analysis.processedPoints.isNotEmpty())
        assertTrue(analysis.peaks.size >= 8)
        assertTrue(analysis.qualityScore >= 60.0)
    }

    @Test
    fun rejectsFlatLowSignalAsHeartRate() {
        val sampleRateHz = 50
        val samples = List(12 * sampleRateHz) { index ->
            SensorSample(
                timestampMs = (index * 1_000L / sampleRateHz),
                deviceUptimeMs = null,
                als = null,
                uv = null,
                tempC = null,
                ax = 0.0,
                ay = 0.0,
                az = 1.0,
                gx = 0.0,
                gy = 0.0,
                gz = 0.0,
                ir = 1_200.0,
            )
        }

        val analysis = PpgAnalyzer.analyze(samples)

        assertNull(analysis.bpm)
        assertEquals(PpgQualityVerdict.LowSignal, analysis.verdict)
        assertTrue(analysis.peaks.isEmpty())
    }

    @Test
    fun suppressesHeartRateWhenFirmwareMarksMotionArtifact() {
        val samples = cleanPpgSamples(72.0, ppgFlags = 0x09)

        val analysis = PpgAnalyzer.analyze(samples)

        assertNull(analysis.bpm)
        assertEquals(PpgQualityVerdict.MotionArtifact, analysis.verdict)
        assertTrue("motion_artifact" in analysis.reasons)
    }

    @Test
    fun countsDoubleHeadMorphologyOncePerCardiacCycle() {
        val samples = doubleHeadedPpgSamples(heartRateBpm = 72.0)

        val analysis = PpgAnalyzer.analyze(samples)

        assertNotNull(analysis.bpm)
        assertEquals(72.0, analysis.bpm!!, 3.0)
        assertNull("double head must not become a 2x-rate candidate", analysis.halfRateCandidateBpm)
        assertTrue("should not count both lobes as separate beats", analysis.peaks.size in 12..16)
    }

    @Test
    fun proposesTwoXRateOnlyWhenRealIntermediatePeaksRepeat() {
        val samples = alternatingAmplitudePpgSamples(heartRateBpm = 72.0)

        val analysis = PpgAnalyzer.analyze(samples)

        assertNotNull("repeated weak pulses should form a guarded 2x candidate", analysis.halfRateCandidateBpm)
        assertEquals(72.0, analysis.halfRateCandidateBpm!!, 4.0)
        assertTrue("base interpretation should remain available", (analysis.bpm ?: 0.0) < 50.0)
    }

    @Test
    fun preservesRateWhenDoubleHeadMorphologyHidesOccasionalBeat() {
        val samples = doubleHeadedPpgSamples(
            heartRateBpm = 72.0,
            suppressEvery = 4,
        )

        val analysis = PpgAnalyzer.analyze(samples)

        assertNotNull(analysis.bpm)
        assertEquals(72.0, analysis.bpm!!, 4.0)
        assertTrue("fixture must contain detector omissions", analysis.peaks.size < 13)
    }

    private fun cleanPpgSamples(heartRateBpm: Double, ppgFlags: Int? = null): List<SensorSample> {
        val sampleRateHz = 50
        return List(12 * sampleRateHz) { index ->
            val t = index / sampleRateHz.toDouble()
            val pulse = sin(2.0 * PI * (heartRateBpm / 60.0) * t)
            val baseline = 86_000.0 + 900.0 * sin(2.0 * PI * 0.08 * t)
            SensorSample(
                timestampMs = (index * 1_000L / sampleRateHz),
                deviceUptimeMs = null,
                als = null,
                uv = null,
                tempC = null,
                ax = 0.0,
                ay = 0.0,
                az = 1.0,
                gx = 0.0,
                gy = 0.0,
                gz = 0.0,
                ir = baseline + 2_400.0 * pulse,
                ppgFlags = ppgFlags,
            )
        }
    }

    private fun doubleHeadedPpgSamples(
        heartRateBpm: Double,
        suppressEvery: Int? = null,
    ): List<SensorSample> {
        val sampleRateHz = 50
        val durationSeconds = 12
        val cycleSeconds = 60.0 / heartRateBpm
        val beatTimes = generateSequence(0.35) { it + cycleSeconds }
            .takeWhile { it < durationSeconds }
            .toList()
        return List(durationSeconds * sampleRateHz) { index ->
            val t = index / sampleRateHz.toDouble()
            val pulse = beatTimes.withIndex().sumOf { (beatIndex, beatTime) ->
                val amplitude = if (
                    suppressEvery != null && (beatIndex + 1) % suppressEvery == 0
                ) {
                    0.12
                } else {
                    1.0
                }
                val firstLobe = exp(-0.5 * ((t - beatTime) / 0.075).pow(2.0))
                val secondLobe = 0.72 * exp(-0.5 * ((t - beatTime - 0.18) / 0.070).pow(2.0))
                amplitude * (firstLobe + secondLobe)
            }
            val baseline = 86_000.0 + 500.0 * sin(2.0 * PI * 0.08 * t)
            SensorSample(
                timestampMs = (index * 1_000L / sampleRateHz),
                deviceUptimeMs = null,
                als = null,
                uv = null,
                tempC = null,
                ax = 0.0,
                ay = 0.0,
                az = 1.0,
                gx = 0.0,
                gy = 0.0,
                gz = 0.0,
                ir = baseline + 2_500.0 * pulse,
                ppgFlags = 0x01,
            )
        }
    }

    private fun alternatingAmplitudePpgSamples(heartRateBpm: Double): List<SensorSample> {
        val sampleRateHz = 50
        val durationSeconds = 12
        val cycleSeconds = 60.0 / heartRateBpm
        val beatTimes = generateSequence(0.35) { it + cycleSeconds }
            .takeWhile { it < durationSeconds }
            .toList()
        return List(durationSeconds * sampleRateHz) { index ->
            val t = index / sampleRateHz.toDouble()
            val pulse = beatTimes.withIndex().sumOf { (beatIndex, beatTime) ->
                val amplitude = if (beatIndex % 2 == 0) 1.0 else 0.16
                amplitude * exp(-0.5 * ((t - beatTime) / 0.080).pow(2.0))
            }
            SensorSample(
                timestampMs = index * 1_000L / sampleRateHz,
                deviceUptimeMs = null,
                als = null,
                uv = null,
                tempC = null,
                ax = 0.0,
                ay = 0.0,
                az = 1.0,
                gx = 0.0,
                gy = 0.0,
                gz = 0.0,
                ir = 86_000.0 + 2_500.0 * pulse,
                ppgFlags = 0x01,
            )
        }
    }
}
