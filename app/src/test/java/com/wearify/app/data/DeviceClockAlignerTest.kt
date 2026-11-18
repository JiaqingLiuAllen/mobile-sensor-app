package com.wearify.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClockAlignerTest {

    @Test
    fun alignsLaterFramesByDeviceUptimeNotBleArrivalTime() {
        val aligner = DeviceClockAligner()

        val firstAlignedMs = aligner.alignMillis(
            deviceUptimeUs = 1_000_000L,
            receivedAtWallMs = 10_100L,
            receivedAtElapsedUs = 50_000_000L,
        )
        val secondAlignedMs = aligner.alignMillis(
            deviceUptimeUs = 1_050_000L,
            receivedAtWallMs = 10_300L,
            receivedAtElapsedUs = 50_200_000L,
        )

        assertEquals(10_100L, firstAlignedMs)
        assertEquals(10_150L, secondAlignedMs)
    }

    @Test
    fun fallsBackToReceiveTimeWithoutDeviceUptime() {
        val aligner = DeviceClockAligner()

        val alignedMs = aligner.alignMillis(
            deviceUptimeUs = null,
            receivedAtWallMs = 20_000L,
            receivedAtElapsedUs = 7_000_000L,
        )

        assertEquals(20_000L, alignedMs)
    }

    @Test
    fun combinesMicMillisWithMicrosSubMillisecondPart() {
        val frame = ContactMicFrame(
            timestampMs = 1_000L,
            receivedAtMs = 1_002L,
            deviceUptimeMs = 987L,
            deviceUptimeUs = 987_654L,
            sequence = 7L,
            sampleRateHz = 4_000,
            samples = listOf(1, 2, 3),
        )

        assertEquals(987_654L, frame.deviceEndUptimeUs())
    }

    @Test
    fun keepsMicrosSubMillisecondPartAfterMicrosWrap() {
        val frame = ContactMicFrame(
            timestampMs = 1_000L,
            receivedAtMs = 1_002L,
            deviceUptimeMs = 4_500_000L,
            deviceUptimeUs = 123_456L,
            sequence = 7L,
            sampleRateHz = 4_000,
            samples = listOf(1, 2, 3),
        )

        assertEquals(4_500_000_456L, frame.deviceEndUptimeUs())
    }
}
