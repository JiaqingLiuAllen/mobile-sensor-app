package com.wearify.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PolarH10ModelsTest {
    @Test
    fun rollingEcgBufferKeepsOnlyRequestedWindow() {
        val buffer = RollingPolarEcgBuffer(windowMs = 1_000L)

        val result = buffer.append(
            PolarEcgFrame(
                frameEndSensorTimestampNs = 0L,
                arrivalWallTimeMs = 0L,
                arrivalElapsedNs = 0L,
                samples = listOf(ecg(1_000L), ecg(1_800L), ecg(2_100L)),
            ),
        )

        assertEquals(listOf(1_800L, 2_100L), result.map { it.timestampMs })
    }

    @Test
    fun rollingAccBufferKeepsOnlyRequestedWindow() {
        val buffer = RollingPolarAccBuffer(windowMs = 1_000L)

        val result = buffer.append(
            PolarAccFrame(
                frameEndSensorTimestampNs = 0L,
                arrivalWallTimeMs = 0L,
                arrivalElapsedNs = 0L,
                samples = listOf(acc(100L), acc(1_000L), acc(1_200L)),
            ),
        )

        assertEquals(listOf(1_000L, 1_200L), result.map { it.timestampMs })
    }

    private fun ecg(timestampMs: Long) = PolarEcgSample(
        timestampMs = timestampMs,
        sensorTimestampNs = 0L,
        arrivalWallTimeMs = timestampMs,
        arrivalElapsedNs = 0L,
        ecgUv = 1,
    )

    private fun acc(timestampMs: Long) = PolarAccSample(
        timestampMs = timestampMs,
        sensorTimestampNs = 0L,
        arrivalWallTimeMs = timestampMs,
        arrivalElapsedNs = 0L,
        xMg = 1,
        yMg = 2,
        zMg = 3,
    )
}
