package com.wearify.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RollingSampleBufferTest {

    @Test
    fun trimsSamplesOutsideWindow() {
        val buffer = RollingSampleBuffer(windowMs = 1_000)

        buffer.append(sampleAt(0))
        buffer.append(sampleAt(500))
        val remaining = buffer.append(sampleAt(1_200))

        assertEquals(listOf(500L, 1_200L), remaining.map { it.timestampMs })
    }

    @Test
    fun clearRemovesWindowContents() {
        val buffer = RollingSampleBuffer(windowMs = 1_000)

        buffer.append(sampleAt(100))
        buffer.clear()
        val remaining = buffer.append(sampleAt(200))

        assertEquals(listOf(200L), remaining.map { it.timestampMs })
    }

    private fun sampleAt(timestamp: Long) = SensorSample(
        timestampMs = timestamp,
        deviceUptimeMs = timestamp,
        als = null,
        uv = null,
        tempC = null,
        ax = timestamp.toDouble(),
        ay = null,
        az = null,
        gx = null,
        gy = null,
        gz = null,
        ir = null,
    )
}
