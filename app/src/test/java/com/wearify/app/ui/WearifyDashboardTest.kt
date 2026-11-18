package com.wearify.app.ui

import com.wearify.app.data.PpgAnalysis
import com.wearify.app.data.PpgPeak
import org.junit.Assert.assertEquals
import org.junit.Test

class WearifyDashboardTest {
    @Test
    fun allSignalsOscilloscopeIncludesEveryExistingPanel() {
        assertEquals(
            listOf(
                "PPG",
                "Heart Thump",
                "Acceleration",
                "Gyroscope",
                "Temperature",
                "UV",
                "Ambient Light",
            ),
            allOscilloscopePanelTitles(),
        )
    }

    @Test
    fun processedPpgOscilloscopeUsesAnalyzerPeakCoordinates() {
        val analysis = PpgAnalysis(
            peaks = listOf(
                PpgPeak(timestampMs = 1_000L, normalized = 1.25),
                PpgPeak(timestampMs = 1_820L, normalized = 0.93),
            ),
        )

        assertEquals(
            listOf(1_000L to 1.25, 1_820L to 0.93),
            ppgOscilloscopePeakPoints(analysis),
        )
    }

    @Test
    fun oscilloscopeAxisUnitMatchesDisplayedWindowLabels() {
        assertEquals("Time, s", oscilloscopeTimeAxisTitle(12_000L))
        assertEquals("Time, ms", oscilloscopeTimeAxisTitle(500L))
    }

    @Test
    fun oscilloscopePanelsUseOneSharedLatestTimestamp() {
        assertEquals(
            2_000L,
            sharedOscilloscopeEndTimestampMs(
                listOf(
                    listOf(1_000L to -800.0, 2_000L to -760.0),
                    listOf(1_000L to 0.12, 1_500L to 0.15),
                ),
            ),
        )
    }
}
