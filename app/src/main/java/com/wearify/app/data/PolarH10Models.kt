package com.wearify.app.data

const val POLAR_H10_ECG_SAMPLE_RATE_HZ = 130
const val POLAR_H10_ACC_SAMPLE_RATE_HZ = 25
const val POLAR_H10_LIVE_WINDOW_MS = 60_000L

sealed interface PolarH10ConnectionState {
    data object Idle : PolarH10ConnectionState
    data object Scanning : PolarH10ConnectionState
    data class Connecting(val deviceName: String) : PolarH10ConnectionState
    data class StartingStreams(val deviceName: String) : PolarH10ConnectionState
    data class Recording(val deviceName: String) : PolarH10ConnectionState
    data class Disconnected(val reason: String? = null) : PolarH10ConnectionState
    data class Error(val message: String) : PolarH10ConnectionState
}

val PolarH10ConnectionState.isBusy: Boolean
    get() = this is PolarH10ConnectionState.Scanning ||
        this is PolarH10ConnectionState.Connecting ||
        this is PolarH10ConnectionState.StartingStreams

val PolarH10ConnectionState.isRecording: Boolean
    get() = this is PolarH10ConnectionState.Recording

data class PolarEcgSample(
    val timestampMs: Long,
    val sensorTimestampNs: Long,
    val arrivalWallTimeMs: Long,
    val arrivalElapsedNs: Long,
    val ecgUv: Int,
)

data class PolarEcgFrame(
    val frameEndSensorTimestampNs: Long,
    val arrivalWallTimeMs: Long,
    val arrivalElapsedNs: Long,
    val samples: List<PolarEcgSample>,
)

data class PolarAccSample(
    val timestampMs: Long,
    val sensorTimestampNs: Long,
    val arrivalWallTimeMs: Long,
    val arrivalElapsedNs: Long,
    val xMg: Int,
    val yMg: Int,
    val zMg: Int,
)

data class PolarAccFrame(
    val frameEndSensorTimestampNs: Long,
    val arrivalWallTimeMs: Long,
    val arrivalElapsedNs: Long,
    val samples: List<PolarAccSample>,
)

data class PolarHrMeasurement(
    val arrivalWallTimeMs: Long,
    val arrivalElapsedNs: Long,
    val heartRateBpm: Int,
    val energyExpendedKj: Int?,
    val rrIntervalsMs: List<Double>,
)

data class PolarBlePacket(
    val arrivalWallTimeMs: Long,
    val arrivalElapsedNs: Long,
    val direction: String,
    val characteristic: String,
    val payloadHex: String,
)

data class PolarCaptureEvent(
    val wallTimeMs: Long,
    val event: String,
    val detail: String = "",
)

data class PolarLogStatus(
    val isRecording: Boolean = false,
    val sessionId: String? = null,
    val ecgRelativePath: String? = null,
    val accRelativePath: String? = null,
    val hrRelativePath: String? = null,
    val packetRelativePath: String? = null,
    val eventRelativePath: String? = null,
    val ecgSamplesWritten: Long = 0,
    val accSamplesWritten: Long = 0,
    val hrMeasurementsWritten: Long = 0,
    val packetsWritten: Long = 0,
    val lastError: String? = null,
)

class RollingPolarEcgBuffer(
    private val windowMs: Long = POLAR_H10_LIVE_WINDOW_MS,
) {
    private val samples = ArrayDeque<PolarEcgSample>()

    fun append(frame: PolarEcgFrame): List<PolarEcgSample> {
        samples.addAll(frame.samples)
        val newest = samples.lastOrNull()?.timestampMs ?: return emptyList()
        val cutoff = newest - windowMs
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }
        return samples.toList()
    }

    fun clear() = samples.clear()
}

class RollingPolarAccBuffer(
    private val windowMs: Long = POLAR_H10_LIVE_WINDOW_MS,
) {
    private val samples = ArrayDeque<PolarAccSample>()

    fun append(frame: PolarAccFrame): List<PolarAccSample> {
        samples.addAll(frame.samples)
        val newest = samples.lastOrNull()?.timestampMs ?: return emptyList()
        val cutoff = newest - windowMs
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }
        return samples.toList()
    }

    fun clear() = samples.clear()
}
