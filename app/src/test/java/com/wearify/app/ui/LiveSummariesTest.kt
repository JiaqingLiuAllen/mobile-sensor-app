package com.wearify.app.ui

import com.wearify.app.data.ContactMicAnalysis
import com.wearify.app.data.ContactMicQualityVerdict
import com.wearify.app.data.HeartSoundPair
import com.wearify.app.data.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSummariesTest {
    @Test
    fun constantGravityAtAnyOrientationIsStillRatherThanFullyMoving() {
        val samples = (0 until 100).map { index ->
            sample(index, ax = 0.70, ay = 0.70, az = 0.14, gx = 0.4, gy = 0.3, gz = 0.2)
        }

        val summary = summarizeMotion(samples)

        assertEquals(MotionLevel.Still, summary.level)
        assertTrue(summary.score!! < 0.28)
    }

    @Test
    fun changingAccelerationAndRotationAreClassifiedAsMoving() {
        val samples = (0 until 100).map { index ->
            val phase = if (index % 2 == 0) 0.55 else 1.35
            sample(index, ax = phase, ay = 0.3, az = 0.7, gx = 34.0, gy = 18.0, gz = 12.0)
        }

        assertEquals(MotionLevel.Moving, summarizeMotion(samples).level)
    }

    @Test
    fun daylightUvAndAlsProduceHighLevelOutdoorLabels() {
        val samples = (0 until 100).map { index ->
            sample(index, uv = 85.0, als = 18_000.0, tempC = 29.4)
        }

        val summary = summarizeEnvironment(samples)

        assertEquals(EnvironmentEstimate.Outdoor, summary.environment)
        assertEquals(UvExposureLevel.High, summary.uvExposure)
        assertEquals(LightLevel.Bright, summary.light)
        assertEquals(29.4, summary.nearBodyTemperatureC!!, 0.01)
    }

    @Test
    fun intermediateOpticalContextStillProducesBinaryEnvironment() {
        val samples = (0 until 100).map { index ->
            sample(index, uv = 25.0, als = 5_000.0)
        }

        val summary = summarizeEnvironment(samples)

        assertEquals(EnvironmentEstimate.Outdoor, summary.environment)
        assertEquals(UvExposureLevel.Moderate, summary.uvExposure)
        assertEquals(LightLevel.Moderate, summary.light)
    }

    @Test
    fun heartSoundSummaryUsesSupportedS1S2TimingWithoutClinicalClaim() {
        val summary = summarizeHeartSound(
            ContactMicAnalysis(
                s1s2Pairs = listOf(
                    HeartSoundPair(1_000, 1_280, 280),
                    HeartSoundPair(1_800, 2_090, 290),
                ),
                regularityCvPct = 7.0,
                verdict = ContactMicQualityVerdict.GoodContact,
            ),
        )

        assertEquals(285L, summary.s1ToS2Ms)
        assertEquals("Cycle timing steady", summary.timingLabel)
        assertEquals("S1 → S2 sound interval", summary.supportLabel)
    }

    @Test
    fun unstableHeartRateJumpRequiresPersistenceAndMovesGradually() {
        var state = updateStableHeartRate(StableHeartRateState(), 60.0)
        state = updateStableHeartRate(state, 100.0)
        assertEquals(60.0, state.displayedBpm!!, 0.01)
        assertFalse(state.isCurrent)
        state = updateStableHeartRate(state, 101.0)
        assertEquals(60.0, state.displayedBpm!!, 0.01)
        state = updateStableHeartRate(state, 99.0)
        assertEquals(64.0, state.displayedBpm!!, 0.01)
        assertTrue(state.isCurrent)

        val held = updateStableHeartRate(state, null)
        assertEquals(64.0, held.displayedBpm!!, 0.01)
        assertFalse(held.isCurrent)
        assertNull(StableHeartRateState().displayedBpm)
    }

    private fun sample(
        index: Int,
        ax: Double = 0.0,
        ay: Double = 0.0,
        az: Double = 1.0,
        gx: Double = 0.0,
        gy: Double = 0.0,
        gz: Double = 0.0,
        uv: Double? = 0.0,
        als: Double? = 0.0,
        tempC: Double? = 27.0,
    ): SensorSample = SensorSample(
        timestampMs = index * 50L,
        deviceUptimeMs = index * 50L,
        als = als,
        uv = uv,
        tempC = tempC,
        ax = ax,
        ay = ay,
        az = az,
        gx = gx,
        gy = gy,
        gz = gz,
        ir = null,
    )
}
