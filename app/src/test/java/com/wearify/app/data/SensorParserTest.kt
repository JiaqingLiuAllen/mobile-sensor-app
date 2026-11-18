package com.wearify.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class SensorParserTest {

    @Test
    fun parsesCompleteSensorLine() {
        val parsed = SensorParser.parseLine(
            line = "T:123,RR:2,IQ:8,IF:3,RC:1,WS:8,PE:7,PX:5,PB:2,PR:4,ALS:10,UV:20,Temp:24.5,Ax:1.1,Ay:-2.2,Az:3.3,Gx:4,Gy:5,Gz:6,IR:123456,PC:G,PN:77,LP:24,PQ:88,PF:1,PA:12",
            receivedAtMs = 999,
        )

        val sample = requireNotNull(parsed.sample)
        assertEquals(0, parsed.malformedFieldCount)
        assertEquals(999, sample.timestampMs)
        assertEquals(999, sample.receivedAtMs)
        assertEquals(123L, sample.deviceUptimeMs)
        assertEquals(10.0, sample.als!!, 0.0001)
        assertEquals(20.0, sample.uv!!, 0.0001)
        assertEquals(24.5, sample.tempC!!, 0.0001)
        assertEquals(-2.2, sample.ay!!, 0.0001)
        assertEquals(123456.0, sample.ir!!, 0.0001)
        assertEquals("G", sample.ppgChannel)
        assertEquals(77L, sample.ppgSequence)
        assertEquals(24, sample.ppgLedPower)
        assertEquals(88, sample.ppgQualityPct)
        assertEquals(1, sample.ppgFlags)
        assertEquals(12, sample.ppgAcPerMille)
        assertEquals(7, sample.ppgErrorCode)
        assertEquals(5L, sample.ppgRecoveryAttemptCount)
        assertEquals(2L, sample.ppgBrownoutCount)
        assertEquals(4L, sample.ppgFirmwareResetCount)
        assertEquals(2L, sample.resetReason)
        assertEquals(8, sample.i2cQuarantineMask)
        assertEquals(3L, sample.i2cBusFaultCount)
        assertEquals(1L, sample.i2cBusRecoveryCount)
        assertEquals(8, sample.watchdogSensorMask)
    }

    @Test
    fun retainsLegacyIrChannelMetadata() {
        val parsed = SensorParser.parseLine(
            line = "T:123,IR:123456,PC:IR,LP:16",
            receivedAtMs = 999,
        )

        val sample = requireNotNull(parsed.sample)
        assertEquals("IR", sample.ppgChannel)
        assertEquals(16, sample.ppgLedPower)
    }

    @Test
    fun toleratesOutOfOrderAndExtraFields() {
        val parsed = SensorParser.parseLine(
            line = "IR:42,Extra:ignored,Ay:2.0,T:55,Ax:1.0",
            receivedAtMs = 1000,
        )

        val sample = requireNotNull(parsed.sample)
        assertEquals(0, parsed.malformedFieldCount)
        assertEquals(55L, sample.deviceUptimeMs)
        assertEquals(1.0, sample.ax!!, 0.0001)
        assertEquals(2.0, sample.ay!!, 0.0001)
        assertEquals(42.0, sample.ir!!, 0.0001)
    }

    @Test
    fun normalizesLegacyImuMilliUnits() {
        val parsed = SensorParser.parseLine(
            line = "T:123,Ax:1000,Ay:-250,Az:980,Gx:120000,Gy:-50000,Gz:250",
            receivedAtMs = 1000,
        )

        val sample = requireNotNull(parsed.sample)
        assertEquals(1.0, sample.ax!!, 0.0001)
        assertEquals(-0.25, sample.ay!!, 0.0001)
        assertEquals(0.98, sample.az!!, 0.0001)
        assertEquals(120.0, sample.gx!!, 0.0001)
        assertEquals(-50.0, sample.gy!!, 0.0001)
        assertEquals(0.25, sample.gz!!, 0.0001)
    }

    @Test
    fun countsBadFieldsWithoutDroppingGoodValues() {
        val parsed = SensorParser.parseLine(
            line = "T:nope,Ax:1.0,Ay:bad-token,Az:3.0,broken",
            receivedAtMs = 1000,
        )

        val sample = requireNotNull(parsed.sample)
        assertEquals(3, parsed.malformedFieldCount)
        assertNull(sample.deviceUptimeMs)
        assertEquals(1.0, sample.ax!!, 0.0001)
        assertNull(sample.ay)
        assertEquals(3.0, sample.az!!, 0.0001)
    }

    @Test
    fun dropsLineWithoutSensorValues() {
        val parsed = SensorParser.parseLine("T:123,broken", receivedAtMs = 1000)

        assertNull(parsed.sample)
        assertEquals(2, parsed.malformedFieldCount)
    }

    @Test
    fun keepsFaultTelemetryWhenAllI2cSensorsAreOffline() {
        val parsed = SensorParser.parseLine(
            line = "T:123,RR:2,IQ:15,IF:4,RC:1,WS:8,PE:1,PX:6,PB:0,PR:0",
            receivedAtMs = 1000,
        )

        val sample = requireNotNull(parsed.sample)
        assertEquals(15, sample.i2cQuarantineMask)
        assertEquals(4L, sample.i2cBusFaultCount)
        assertEquals(1, sample.ppgErrorCode)
        assertEquals(6L, sample.ppgRecoveryAttemptCount)
        assertNull(sample.ir)
        assertNull(sample.ax)
    }

    @Test
    fun assemblesLinesAcrossBleChunks() {
        val assembler = NusLineAssembler()

        assertEquals(emptyList<String>(), assembler.append("T:1,Ax".toByteArray()))
        assertEquals(
            listOf("T:1,Ax:2"),
            assembler.append(":2\nT:2,".toByteArray()),
        )
        assertEquals(
            listOf("T:2,Ax:3", "T:3,Ax:4"),
            assembler.append("Ax:3\nT:3,Ax:4\n".toByteArray()),
        )
    }

    @Test
    fun parsesContactMicLine() {
        val payload = Base64.getEncoder().encodeToString(
            byteArrayOf(
                0x00,
                0x00,
                0x00,
                0x04,
                0x00,
                0xFC.toByte(),
            ),
        )

        val parsed = SensorParser.parseContactMicLine(
            line = "MIC:7,T:987,TU:987654,R:4000,L:3,B:$payload",
            receivedAtMs = 12_345,
        )

        val frame = requireNotNull(parsed.frame)
        assertEquals(0, parsed.malformedFieldCount)
        assertEquals(12_345L, frame.timestampMs)
        assertEquals(12_345L, frame.receivedAtMs)
        assertEquals(7L, frame.sequence)
        assertEquals(987L, frame.deviceUptimeMs)
        assertEquals(987_654L, frame.deviceUptimeUs)
        assertEquals(4_000, frame.sampleRateHz)
        assertEquals(listOf(0, 1024, -1024), frame.samples)
    }

    @Test
    fun rollingContactMicBufferKeepsWindow() {
        val buffer = RollingContactMicBuffer(windowMs = 2)
        val firstWindow = buffer.append(
            ContactMicFrame(
                timestampMs = 1_000,
                deviceUptimeMs = null,
                sequence = 1,
                sampleRateHz = 4_000,
                samples = listOf(1, 2, 3, 4),
            ),
        )
        val secondWindow = buffer.append(
            ContactMicFrame(
                timestampMs = 1_010,
                deviceUptimeMs = null,
                sequence = 2,
                sampleRateHz = 4_000,
                samples = listOf(5, 6, 7, 8),
            ),
        )

        assertEquals(listOf(1, 2, 3, 4), firstWindow.map { it.amplitude })
        assertEquals(listOf(5, 6, 7, 8), secondWindow.map { it.amplitude })
    }

    private fun requireNotNull(sample: SensorSample?): SensorSample {
        assertNotNull(sample)
        return sample!!
    }

    private fun requireNotNull(frame: ContactMicFrame?): ContactMicFrame {
        assertNotNull(frame)
        return frame!!
    }
}
