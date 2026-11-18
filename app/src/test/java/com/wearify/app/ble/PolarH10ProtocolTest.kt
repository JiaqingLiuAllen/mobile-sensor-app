package com.wearify.app.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PolarH10ProtocolTest {
    @Test
    fun decodesRawEcgSamplesAndTimestamp() {
        val payload = byteArrayOf(POLAR_ECG_MEASUREMENT_TYPE.toByte()) +
            littleEndianLong(1_000_000_000L) + byteArrayOf(0) +
            signedInt24(12) + signedInt24(-2) + signedInt24(500)

        val frame = PolarH10Protocol.decodeEcgPacket(payload)

        assertEquals(1_000_000_000L, frame.timestampNs)
        assertEquals(listOf(12, -2, 500), frame.samplesUv)
    }

    @Test
    fun rejectsUnknownEcgFrameFormat() {
        val payload = byteArrayOf(POLAR_ECG_MEASUREMENT_TYPE.toByte()) +
            littleEndianLong(1L) + byteArrayOf(1)

        assertThrows(IllegalArgumentException::class.java) {
            PolarH10Protocol.decodeEcgPacket(payload)
        }
    }

    @Test
    fun decodesRawAccSamplesAndTimestamp() {
        val payload = byteArrayOf(POLAR_ACC_MEASUREMENT_TYPE.toByte()) +
            littleEndianLong(2_000_000_000L) + byteArrayOf(POLAR_ACC_FRAME_TYPE_16_BIT.toByte()) +
            signedInt16(10) + signedInt16(-20) + signedInt16(1_000) +
            signedInt16(-30) + signedInt16(40) + signedInt16(990)

        val frame = PolarH10Protocol.decodeAccPacket(payload)

        assertEquals(2_000_000_000L, frame.timestampNs)
        assertEquals(
            listOf(PolarAccVector(10, -20, 1_000), PolarAccVector(-30, 40, 990)),
            frame.samples,
        )
    }

    @Test
    fun parsesHrAndRrIntervals() {
        val measurement = PolarH10Protocol.decodeHrMeasurement(
            byteArrayOf(0x10, 72, 0x00, 0x04, 0x00, 0x02),
        )

        assertEquals(72, measurement.heartRateBpm)
        assertEquals(listOf(1_000.0, 500.0), measurement.rrIntervalsMs)
    }

    @Test
    fun buildsStreamCommandsAndSampleTimes() {
        assertArrayEquals(
            byteArrayOf(2, 0, 0, 1, 130.toByte(), 0, 1, 1, 14, 0),
            PolarH10Protocol.startEcgCommand,
        )
        assertArrayEquals(
            byteArrayOf(2, 2, 0, 1, 25, 0, 1, 1, 16, 0, 2, 1, 2, 0),
            PolarH10Protocol.startAccCommand,
        )
        assertEquals(
            listOf(980_000_000L, 990_000_000L, 1_000_000_000L),
            PolarH10Protocol.sampleTimestampsNs(1_000_000_000L, 3, 100),
        )
        assertTrue(
            PolarH10Protocol.isSuccessfulStartResponse(
                byteArrayOf(0xF0.toByte(), 0x02, POLAR_ECG_MEASUREMENT_TYPE.toByte(), 0),
                POLAR_ECG_MEASUREMENT_TYPE,
            ),
        )
    }

    private fun signedInt24(value: Int): ByteArray {
        val masked = value and 0xFFFFFF
        return byteArrayOf(
            (masked and 0xFF).toByte(),
            ((masked ushr 8) and 0xFF).toByte(),
            ((masked ushr 16) and 0xFF).toByte(),
        )
    }

    private fun signedInt16(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
    )

    private fun littleEndianLong(value: Long): ByteArray =
        ByteArray(8) { index -> ((value ushr (index * 8)) and 0xFF).toByte() }
}
