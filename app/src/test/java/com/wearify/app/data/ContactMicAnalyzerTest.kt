package com.wearify.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class ContactMicAnalyzerTest {

    @Test
    fun detectsSyntheticS1S2Pairs() {
        val points = syntheticHeartSoundPoints(durationSeconds = 6.0, bpm = 72.0)

        val analysis = ContactMicAnalyzer.analyze(points)

        assertTrue(analysis.s1s2Pairs.size >= 4)
        assertNotNull(analysis.bpm)
        assertEquals(72.0, analysis.bpm!!, 8.0)
        assertTrue(analysis.filteredPoints.isNotEmpty())
        assertTrue(analysis.envelopePoints.isNotEmpty())
    }

    @Test
    fun estimatesRateFromStrongestCyclePeakBeforeMultiplePairsAreAvailable() {
        val points = syntheticHeartSoundPoints(durationSeconds = 1.35, bpm = 72.0)

        val analysis = ContactMicAnalyzer.analyze(points)

        assertEquals(1, analysis.s1s2Pairs.size)
        assertNotNull(analysis.bpm)
        assertEquals(72.0, analysis.bpm!!, 10.0)
        assertTrue("dominant peak source should remain visible", analysis.reasons.contains("bpm_from_dominant_cycle_peak"))
    }

    private fun syntheticHeartSoundPoints(
        durationSeconds: Double,
        bpm: Double,
    ): List<ContactMicPoint> {
        val sampleRate = CONTACT_MIC_SAMPLE_RATE_HZ
        val sampleCount = (durationSeconds * sampleRate).toInt()
        val period = 60.0 / bpm
        val startTimestampMs = 10_000L

        return List(sampleCount) { index ->
            val t = index.toDouble() / sampleRate
            val phase = t % period
            fun thump(center: Double, width: Double, gain: Double): Double {
                val x = (phase - center) / width
                return gain * (1.0 - x * x) * exp(-0.5 * x * x)
            }
            val s1 = thump(center = 0.08, width = 0.026, gain = 1.0)
            val s2 = thump(center = 0.31, width = 0.022, gain = 0.70)
            val lowNoise = 0.03 * sin(2 * PI * 8.0 * t)
            ContactMicPoint(
                timestampMs = startTimestampMs + (index * 1_000L) / sampleRate,
                amplitude = ((s1 + s2 + lowNoise) * 14_000).toInt(),
            )
        }
    }
}
