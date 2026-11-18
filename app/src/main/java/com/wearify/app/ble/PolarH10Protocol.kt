package com.wearify.app.ble

import com.wearify.app.data.POLAR_H10_ACC_SAMPLE_RATE_HZ
import com.wearify.app.data.POLAR_H10_ECG_SAMPLE_RATE_HZ
import kotlin.math.roundToLong

internal const val POLAR_ECG_MEASUREMENT_TYPE = 0
internal const val POLAR_ACC_MEASUREMENT_TYPE = 2
internal const val POLAR_ACC_FRAME_TYPE_16_BIT = 1

internal data class DecodedPolarEcgFrame(
    val timestampNs: Long,
    val samplesUv: List<Int>,
)

internal data class PolarAccVector(
    val xMg: Int,
    val yMg: Int,
    val zMg: Int,
)

internal data class DecodedPolarAccFrame(
    val timestampNs: Long,
    val samples: List<PolarAccVector>,
)

internal data class DecodedPolarHrMeasurement(
    val heartRateBpm: Int,
    val energyExpendedKj: Int?,
    val rrIntervalsMs: List<Double>,
)

internal object PolarH10Protocol {
    val startEcgCommand: ByteArray = startStreamCommand(
        measurementType = POLAR_ECG_MEASUREMENT_TYPE,
        settings = listOf(0 to POLAR_H10_ECG_SAMPLE_RATE_HZ, 1 to 14),
    )
    val startAccCommand: ByteArray = startStreamCommand(
        measurementType = POLAR_ACC_MEASUREMENT_TYPE,
        settings = listOf(0 to POLAR_H10_ACC_SAMPLE_RATE_HZ, 1 to 16, 2 to 2),
    )
    val stopEcgCommand: ByteArray = byteArrayOf(0x03, POLAR_ECG_MEASUREMENT_TYPE.toByte())
    val stopAccCommand: ByteArray = byteArrayOf(0x03, POLAR_ACC_MEASUREMENT_TYPE.toByte())

    fun decodeEcgPacket(data: ByteArray): DecodedPolarEcgFrame {
        val header = parsePmdHeader(data)
        require(header.measurementType == POLAR_ECG_MEASUREMENT_TYPE) {
            "Expected ECG PMD type 0, got ${header.measurementType}"
        }
        require(header.frameType == 0) { "Unsupported ECG PMD frame type ${header.frameType}" }
        require(header.payloadSize % 3 == 0) { "ECG payload is not a whole number of 24-bit samples" }

        val samples = buildList(header.payloadSize / 3) {
            var offset = PMD_HEADER_BYTES
            while (offset < data.size) {
                add(readSignedInt24Le(data, offset))
                offset += 3
            }
        }
        return DecodedPolarEcgFrame(header.timestampNs, samples)
    }

    fun decodeAccPacket(data: ByteArray): DecodedPolarAccFrame {
        val header = parsePmdHeader(data)
        require(header.measurementType == POLAR_ACC_MEASUREMENT_TYPE) {
            "Expected accelerometer PMD type 2, got ${header.measurementType}"
        }
        require(header.frameType == POLAR_ACC_FRAME_TYPE_16_BIT) {
            "Unsupported accelerometer PMD frame type ${header.frameType}"
        }
        require(header.payloadSize % 6 == 0) {
            "Accelerometer payload is not a whole number of xyz samples"
        }

        val samples = buildList(header.payloadSize / 6) {
            var offset = PMD_HEADER_BYTES
            while (offset < data.size) {
                add(
                    PolarAccVector(
                        xMg = readSignedInt16Le(data, offset),
                        yMg = readSignedInt16Le(data, offset + 2),
                        zMg = readSignedInt16Le(data, offset + 4),
                    ),
                )
                offset += 6
            }
        }
        return DecodedPolarAccFrame(header.timestampNs, samples)
    }

    fun decodeHrMeasurement(data: ByteArray): DecodedPolarHrMeasurement {
        require(data.size >= 2) { "Heart-rate packet is too short" }
        val flags = data.u8(0)
        var offset = 1
        val hrWidth = if (flags and 0x01 != 0) 2 else 1
        require(data.size >= offset + hrWidth) { "Heart-rate packet has no complete HR value" }
        val heartRate = if (hrWidth == 2) readUnsignedInt16Le(data, offset) else data.u8(offset)
        offset += hrWidth

        val energy = if (flags and 0x08 != 0) {
            require(data.size >= offset + 2) { "Heart-rate packet has a truncated energy field" }
            readUnsignedInt16Le(data, offset).also { offset += 2 }
        } else {
            null
        }

        val rrIntervals = mutableListOf<Double>()
        if (flags and 0x10 != 0) {
            require((data.size - offset) % 2 == 0) { "Heart-rate packet has a truncated RR interval" }
            while (offset < data.size) {
                rrIntervals += readUnsignedInt16Le(data, offset) * 1_000.0 / 1_024.0
                offset += 2
            }
        }
        return DecodedPolarHrMeasurement(heartRate, energy, rrIntervals)
    }

    fun sampleTimestampsNs(
        lastTimestampNs: Long,
        count: Int,
        sampleRateHz: Int,
    ): List<Long> {
        require(count >= 0) { "count must be non-negative" }
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        return List(count) { index ->
            lastTimestampNs - (
                (count - 1 - index) * 1_000_000_000.0 / sampleRateHz
                ).roundToLong()
        }
    }

    fun isSuccessfulStartResponse(data: ByteArray, measurementType: Int): Boolean =
        data.size >= 4 &&
            data.u8(0) == 0xF0 &&
            data.u8(1) == 0x02 &&
            data.u8(2) == measurementType &&
            data.u8(3) == 0

    fun isStartResponse(data: ByteArray, measurementType: Int): Boolean =
        data.size >= 4 &&
            data.u8(0) == 0xF0 &&
            data.u8(1) == 0x02 &&
            data.u8(2) == measurementType

    private fun startStreamCommand(
        measurementType: Int,
        settings: List<Pair<Int, Int>>,
    ): ByteArray {
        val bytes = mutableListOf(0x02.toByte(), measurementType.toByte())
        settings.forEach { (settingType, value) ->
            bytes += settingType.toByte()
            bytes += 0x01
            bytes += (value and 0xFF).toByte()
            bytes += ((value ushr 8) and 0xFF).toByte()
        }
        return bytes.toByteArray()
    }

    private fun parsePmdHeader(data: ByteArray): PmdHeader {
        require(data.size >= PMD_HEADER_BYTES) { "PMD packet is too short (${data.size} bytes)" }
        return PmdHeader(
            measurementType = data.u8(0),
            timestampNs = readLongLe(data, 1),
            frameType = data.u8(9),
            payloadSize = data.size - PMD_HEADER_BYTES,
        )
    }

    private fun readSignedInt24Le(data: ByteArray, offset: Int): Int {
        val unsigned = data.u8(offset) or
            (data.u8(offset + 1) shl 8) or
            (data.u8(offset + 2) shl 16)
        return if (unsigned and 0x800000 != 0) unsigned - 0x1000000 else unsigned
    }

    private fun readSignedInt16Le(data: ByteArray, offset: Int): Int =
        readUnsignedInt16Le(data, offset).toShort().toInt()

    private fun readUnsignedInt16Le(data: ByteArray, offset: Int): Int =
        data.u8(offset) or (data.u8(offset + 1) shl 8)

    private fun readLongLe(data: ByteArray, offset: Int): Long {
        var result = 0L
        repeat(8) { index ->
            result = result or (data.u8(offset + index).toLong() shl (index * 8))
        }
        return result
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

    private data class PmdHeader(
        val measurementType: Int,
        val timestampNs: Long,
        val frameType: Int,
        val payloadSize: Int,
    )

    private const val PMD_HEADER_BYTES = 10
}
