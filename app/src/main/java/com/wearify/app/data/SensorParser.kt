package com.wearify.app.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Base64
import java.util.Locale
import kotlin.math.abs

private const val LEGACY_ACCEL_MG_THRESHOLD = 32.0
private const val LEGACY_GYRO_MDPS_THRESHOLD = 4_000.0
private const val LEGACY_IMU_UNIT_SCALE = 0.001

data class ParsedSensorLine(
    val sample: SensorSample?,
    val malformedFieldCount: Int,
)

data class ParsedContactMicLine(
    val frame: ContactMicFrame?,
    val malformedFieldCount: Int,
)

class NusLineAssembler(
    private val charset: Charset = Charsets.UTF_8,
) {
    private val buffer = StringBuilder()

    @Synchronized
    fun append(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        buffer.append(bytes.toString(charset))

        val lines = mutableListOf<String>()
        while (true) {
            val index = buffer.indexOf("\n")
            if (index < 0) break
            val line = buffer.substring(0, index).trim('\r', '\n', ' ')
            buffer.delete(0, index + 1)
            if (line.isNotBlank()) lines += line
        }
        return lines
    }

    @Synchronized
    fun clear() {
        buffer.clear()
    }
}

object SensorParser {
    fun parseLine(line: String, receivedAtMs: Long = System.currentTimeMillis()): ParsedSensorLine {
        val parsedFields = parseFields(line)
        val values = parsedFields.values
        var malformedFields = parsedFields.malformedFieldCount

        fun doubleValue(vararg keys: String): Double? {
            val raw = keys.firstNotNullOfOrNull { values[it] } ?: return null
            return raw.toDoubleOrNull().also {
                if (it == null) malformedFields++
            }
        }

        fun longValue(vararg keys: String): Long? {
            val raw = keys.firstNotNullOfOrNull { values[it] } ?: return null
            return raw.toLongOrNull().also {
                if (it == null) malformedFields++
            }
        }

        fun intValue(vararg keys: String): Int? {
            val raw = keys.firstNotNullOfOrNull { values[it] } ?: return null
            return raw.toIntOrNull().also {
                if (it == null) malformedFields++
            }
        }

        fun channelValue(vararg keys: String): String? {
            val raw = keys.firstNotNullOfOrNull { values[it] } ?: return null
            return raw.uppercase(Locale.US).takeIf { it == "R" || it == "IR" || it == "G" }.also {
                if (it == null) malformedFields++
            }
        }

        val ax = doubleValue("AX")
        val ay = doubleValue("AY")
        val az = doubleValue("AZ")
        val gx = doubleValue("GX")
        val gy = doubleValue("GY")
        val gz = doubleValue("GZ")
        val accelScale = legacyImuUnitScale(listOf(ax, ay, az), LEGACY_ACCEL_MG_THRESHOLD)
        val gyroScale = legacyImuUnitScale(listOf(gx, gy, gz), LEGACY_GYRO_MDPS_THRESHOLD)

        val sample = SensorSample(
            timestampMs = receivedAtMs,
            deviceUptimeMs = longValue("T"),
            als = doubleValue("ALS"),
            uv = doubleValue("UV"),
            tempC = doubleValue("TEMP"),
            ax = ax?.times(accelScale),
            ay = ay?.times(accelScale),
            az = az?.times(accelScale),
            gx = gx?.times(gyroScale),
            gy = gy?.times(gyroScale),
            gz = gz?.times(gyroScale),
            ir = doubleValue("IR", "PPG", "PPGRAW", "PPG_RAW", "PPGIR", "PPG_IR"),
            ppgChannel = channelValue("PC", "PPG_CHANNEL"),
            ppgSequence = longValue("PN", "PPGSEQ", "PPG_SEQ"),
            ppgLedPower = intValue("LP", "PPGLED", "PPG_LED"),
            ppgQualityPct = intValue("PQ", "PPGQ", "PPG_QUALITY"),
            ppgFlags = intValue("PF", "PPGF", "PPG_FLAGS"),
            ppgAcPerMille = intValue("PA", "PPGAC", "PPG_AC_PER_MILLE"),
            ppgErrorCode = intValue("PE", "PPG_ERROR"),
            ppgRecoveryAttemptCount = longValue("PX", "PPG_RECOVERY_ATTEMPTS"),
            ppgBrownoutCount = longValue("PB", "PPG_BROWNOUTS"),
            ppgFirmwareResetCount = longValue("PR", "PPG_FIRMWARE_RESETS"),
            resetReason = longValue("RR", "RESET_REASON"),
            i2cQuarantineMask = intValue("IQ", "I2C_QUARANTINE"),
            i2cBusFaultCount = longValue("IF", "I2C_BUS_FAULTS"),
            i2cBusRecoveryCount = longValue("RC", "I2C_BUS_RECOVERIES"),
            watchdogSensorMask = intValue("WS", "WATCHDOG_SENSOR"),
        )

        val hasAnySensorValue = listOf(
            sample.als,
            sample.uv,
            sample.tempC,
            sample.ax,
            sample.ay,
            sample.az,
            sample.gx,
            sample.gy,
            sample.gz,
            sample.ir,
            sample.ppgChannel,
            sample.ppgSequence,
            sample.ppgLedPower,
            sample.ppgQualityPct,
            sample.ppgFlags,
            sample.ppgAcPerMille,
            sample.ppgErrorCode,
            sample.ppgRecoveryAttemptCount,
            sample.ppgBrownoutCount,
            sample.ppgFirmwareResetCount,
            sample.resetReason,
            sample.i2cQuarantineMask,
            sample.i2cBusFaultCount,
            sample.i2cBusRecoveryCount,
            sample.watchdogSensorMask,
        ).any { it != null }

        return ParsedSensorLine(
            sample = sample.takeIf { hasAnySensorValue },
            malformedFieldCount = malformedFields + if (hasAnySensorValue) 0 else 1,
        )
    }

    fun parseContactMicLine(
        line: String,
        receivedAtMs: Long = System.currentTimeMillis(),
    ): ParsedContactMicLine {
        val parsedFields = parseFields(line)
        val values = parsedFields.values
        var malformedFields = parsedFields.malformedFieldCount

        fun longValue(vararg keys: String): Long? {
            val raw = keys.firstNotNullOfOrNull { values[it] } ?: return null
            return raw.toLongOrNull().also {
                if (it == null) malformedFields++
            }
        }

        fun intValue(key: String): Int? {
            val raw = values[key] ?: return null
            return raw.toIntOrNull().also {
                if (it == null) malformedFields++
            }
        }

        val payload = values["B"]
        if (payload.isNullOrBlank()) {
            return ParsedContactMicLine(
                frame = null,
                malformedFieldCount = malformedFields + 1,
            )
        }

        val decoded = try {
            Base64.getDecoder().decode(payload)
        } catch (_: IllegalArgumentException) {
            null
        }

        if (decoded == null || decoded.isEmpty() || decoded.size % 2 != 0) {
            return ParsedContactMicLine(
                frame = null,
                malformedFieldCount = malformedFields + 1,
            )
        }

        val parsedSampleRate = intValue("R")
        val sampleRate = when {
            parsedSampleRate == null -> CONTACT_MIC_SAMPLE_RATE_HZ
            parsedSampleRate > 0 -> parsedSampleRate
            else -> {
                malformedFields++
                CONTACT_MIC_SAMPLE_RATE_HZ
            }
        }
        val expectedSamples = intValue("L")
        val byteBuffer = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)
        val samples = List(decoded.size / 2) {
            byteBuffer.short.toInt()
        }
        if (expectedSamples != null && expectedSamples != samples.size) {
            malformedFields++
        }

        return ParsedContactMicLine(
            frame = ContactMicFrame(
                timestampMs = receivedAtMs,
                deviceUptimeMs = longValue("T"),
                deviceUptimeUs = longValue("TU", "TUS", "MIC_TU", "MIC_TUS"),
                sequence = longValue("MIC"),
                sampleRateHz = sampleRate,
                samples = samples,
            ),
            malformedFieldCount = malformedFields,
        )
    }

    private fun parseFields(line: String): ParsedFields {
        val values = linkedMapOf<String, String>()
        var malformedFields = 0

        line.split(',')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val separator = token.indexOf(':')
                if (separator <= 0 || separator == token.lastIndex) {
                    malformedFields++
                    return@forEach
                }
                val key = token.substring(0, separator).trim().uppercase(Locale.US)
                val value = token.substring(separator + 1).trim()
                values[key] = value
            }

        return ParsedFields(
            values = values,
            malformedFieldCount = malformedFields,
        )
    }

    private data class ParsedFields(
        val values: LinkedHashMap<String, String>,
        val malformedFieldCount: Int,
    )

    private fun legacyImuUnitScale(values: List<Double?>, legacyThreshold: Double): Double =
        if (values.any { it != null && it.isFinite() && abs(it) > legacyThreshold }) {
            LEGACY_IMU_UNIT_SCALE
        } else {
            1.0
        }
}

class RollingSampleBuffer(
    private val windowMs: Long = SENSOR_WINDOW_MS,
) {
    private val samples = ArrayDeque<SensorSample>()

    fun append(sample: SensorSample): List<SensorSample> {
        samples.addLast(sample)
        val cutoff = sample.timestampMs - windowMs
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }
        return samples.toList()
    }

    fun clear() {
        samples.clear()
    }
}

class RollingContactMicBuffer(
    private val windowMs: Long = CONTACT_MIC_WINDOW_MS,
) {
    private val points = ArrayDeque<ContactMicPoint>()

    fun append(frame: ContactMicFrame): List<ContactMicPoint> {
        if (frame.samples.isEmpty()) {
            return points.toList()
        }

        val sampleRate = frame.sampleRateHz.coerceAtLeast(1)
        val samplePeriodMs = 1_000.0 / sampleRate
        val chunkSpanMs = samplePeriodMs * (frame.samples.size - 1).coerceAtLeast(0)
        val firstTimestampMs = frame.timestampMs.toDouble() - chunkSpanMs

        frame.samples.forEachIndexed { index, amplitude ->
            points.addLast(
                ContactMicPoint(
                    timestampMs = (firstTimestampMs + index * samplePeriodMs).toLong(),
                    amplitude = amplitude,
                ),
            )
        }

        val cutoff = frame.timestampMs - windowMs
        while (points.isNotEmpty() && points.first().timestampMs < cutoff) {
            points.removeFirst()
        }

        return points.toList()
    }

    fun clear() {
        points.clear()
    }
}
