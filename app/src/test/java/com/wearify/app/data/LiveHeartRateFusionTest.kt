package com.wearify.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveHeartRateFusionTest {
    @Test
    fun usesGuardedHalfRateCandidateOnlyWhenPcgSupportsIt() {
        val trueHalfRate = fuseLiveHeartRate(
            ppg = PpgAnalysis(
                bpm = 36.0,
                halfRateCandidateBpm = 72.0,
                qualityScore = 85.0,
                verdict = PpgQualityVerdict.GoodContact,
            ),
            pcg = ContactMicAnalysis(
                bpm = 73.0,
                qualityScore = 85.0,
                verdict = ContactMicQualityVerdict.GoodContact,
            ),
        )
        assertEquals(LiveHeartRateFusionState.Fused, trueHalfRate.state)
        assertEquals(72.5, trueHalfRate.bpm!!, 0.01)

        val trueLowRate = fuseLiveHeartRate(
            ppg = PpgAnalysis(
                bpm = 60.0,
                halfRateCandidateBpm = 120.0,
                qualityScore = 85.0,
                verdict = PpgQualityVerdict.GoodContact,
            ),
            pcg = ContactMicAnalysis(
                bpm = 61.0,
                qualityScore = 85.0,
                verdict = ContactMicQualityVerdict.GoodContact,
            ),
        )
        assertEquals(LiveHeartRateFusionState.Fused, trueLowRate.state)
        assertEquals(60.5, trueLowRate.bpm!!, 0.01)
    }

    @Test
    fun fusesAcceptedPpgAndPcgRatesByQuality() {
        val fusion = fuseLiveHeartRate(
            ppg = PpgAnalysis(
                bpm = 72.0,
                qualityScore = 80.0,
                verdict = PpgQualityVerdict.GoodContact,
            ),
            pcg = ContactMicAnalysis(
                bpm = 74.0,
                qualityScore = 100.0,
                verdict = ContactMicQualityVerdict.GoodContact,
            ),
        )

        assertEquals(LiveHeartRateFusionState.Fused, fusion.state)
        assertEquals(73.1, fusion.bpm!!, 0.01)
        assertEquals(2.0, fusion.differenceBpm!!, 0.01)
    }

    @Test
    fun withholdsFusionWhenModalitiesDisagree() {
        val fusion = fuseLiveHeartRate(
            ppg = PpgAnalysis(
                bpm = 70.0,
                qualityScore = 90.0,
                verdict = PpgQualityVerdict.GoodContact,
            ),
            pcg = ContactMicAnalysis(
                bpm = 82.0,
                qualityScore = 90.0,
                verdict = ContactMicQualityVerdict.GoodContact,
            ),
        )

        assertEquals(LiveHeartRateFusionState.Disagreement, fusion.state)
        assertNull(fusion.bpm)
    }

    @Test
    fun withholdsFusionUntilBothSignalsAreUsable() {
        val missing = fuseLiveHeartRate(
            ppg = PpgAnalysis(
                bpm = 72.0,
                qualityScore = 90.0,
                verdict = PpgQualityVerdict.GoodContact,
            ),
            pcg = ContactMicAnalysis(),
        )
        val lowQuality = fuseLiveHeartRate(
            ppg = PpgAnalysis(
                bpm = 72.0,
                qualityScore = 90.0,
                verdict = PpgQualityVerdict.GoodContact,
            ),
            pcg = ContactMicAnalysis(
                bpm = 73.0,
                qualityScore = 40.0,
                verdict = ContactMicQualityVerdict.LowConfidence,
            ),
        )

        assertEquals(LiveHeartRateFusionState.WaitingForBoth, missing.state)
        assertNull(missing.bpm)
        assertEquals(LiveHeartRateFusionState.LowQuality, lowQuality.state)
        assertNull(lowQuality.bpm)
    }
}
