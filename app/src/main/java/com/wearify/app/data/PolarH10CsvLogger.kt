package com.wearify.app.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Writes evaluation-only Polar streams independently from the primary wearable stream. */
class PolarH10CsvLogger(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val dateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    private val pendingEcg = StringBuilder()
    private val pendingAcc = StringBuilder()
    private val pendingHr = StringBuilder()
    private val pendingPackets = StringBuilder()
    private val pendingEvents = StringBuilder()

    private var ecgTarget: File? = null
    private var accTarget: File? = null
    private var hrTarget: File? = null
    private var packetTarget: File? = null
    private var eventTarget: File? = null
    private var ecgSamplesWritten = 0L
    private var accSamplesWritten = 0L
    private var hrMeasurementsWritten = 0L
    private var packetsWritten = 0L

    private val _status = MutableStateFlow(PolarLogStatus())
    val status: StateFlow<PolarLogStatus> = _status.asStateFlow()

    suspend fun startSession(
        activity: DataCollectionActivity,
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            stopSessionLocked()
            clearPending()
            ecgSamplesWritten = 0L
            accSamplesWritten = 0L
            hrMeasurementsWritten = 0L
            packetsWritten = 0L

            val stamp = fileStamp.format(Date())
            val sessionId = "polar_h10_${stamp}_${activity.fileToken}"
            ecgTarget = createTarget("${sessionId}_ecg_raw.csv", ECG_HEADER)
            accTarget = createTarget("${sessionId}_acc_raw.csv", ACC_HEADER)
            hrTarget = createTarget("${sessionId}_hr_rr_raw.csv", HR_HEADER)
            packetTarget = createTarget("${sessionId}_ble_packets.csv", PACKET_HEADER)
            eventTarget = createTarget("${sessionId}_events.csv", EVENT_HEADER)
            _status.value = PolarLogStatus(
                isRecording = true,
                sessionId = sessionId,
                ecgRelativePath = requireNotNull(ecgTarget).path,
                accRelativePath = requireNotNull(accTarget).path,
                hrRelativePath = requireNotNull(hrTarget).path,
                packetRelativePath = requireNotNull(packetTarget).path,
                eventRelativePath = requireNotNull(eventTarget).path,
            )
            sessionId
        }
    }

    suspend fun appendEcgFrame(frame: PolarEcgFrame) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = ecgTarget ?: return@withLock
            frame.samples.forEach { sample ->
                pendingEcg.append(sample.timestampMs).append(',')
                    .append(dateTime.format(Date(sample.timestampMs))).append(',')
                    .append(sample.sensorTimestampNs).append(',')
                    .append(sample.arrivalWallTimeMs).append(',')
                    .append(sample.arrivalElapsedNs).append(',')
                    .append(sample.ecgUv).append('\n')
            }
            ecgSamplesWritten += frame.samples.size
            updateCounts()
            flushIfNeeded(pendingEcg, target)
        }
    }

    suspend fun appendAccFrame(frame: PolarAccFrame) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = accTarget ?: return@withLock
            frame.samples.forEach { sample ->
                pendingAcc.append(sample.timestampMs).append(',')
                    .append(dateTime.format(Date(sample.timestampMs))).append(',')
                    .append(sample.sensorTimestampNs).append(',')
                    .append(sample.arrivalWallTimeMs).append(',')
                    .append(sample.arrivalElapsedNs).append(',')
                    .append(sample.xMg).append(',')
                    .append(sample.yMg).append(',')
                    .append(sample.zMg).append('\n')
            }
            accSamplesWritten += frame.samples.size
            updateCounts()
            flushIfNeeded(pendingAcc, target)
        }
    }

    suspend fun appendHeartRate(measurement: PolarHrMeasurement) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = hrTarget ?: return@withLock
            pendingHr.append(measurement.arrivalWallTimeMs).append(',')
                .append(dateTime.format(Date(measurement.arrivalWallTimeMs))).append(',')
                .append(measurement.arrivalElapsedNs).append(',')
                .append(measurement.heartRateBpm).append(',')
                .append(measurement.energyExpendedKj ?: "").append(',')
                .append(csvEscape(measurement.rrIntervalsMs.joinToString(";"))).append('\n')
            hrMeasurementsWritten++
            updateCounts()
            flushIfNeeded(pendingHr, target)
        }
    }

    suspend fun appendPacket(packet: PolarBlePacket) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = packetTarget ?: return@withLock
            pendingPackets.append(packet.arrivalWallTimeMs).append(',')
                .append(packet.arrivalElapsedNs).append(',')
                .append(csvEscape(packet.direction)).append(',')
                .append(csvEscape(packet.characteristic)).append(',')
                .append(packet.payloadHex).append('\n')
            packetsWritten++
            updateCounts()
            flushIfNeeded(pendingPackets, target)
        }
    }

    suspend fun appendEvent(event: PolarCaptureEvent) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = eventTarget ?: return@withLock
            pendingEvents.append(event.wallTimeMs).append(',')
                .append(dateTime.format(Date(event.wallTimeMs))).append(',')
                .append(csvEscape(event.event)).append(',')
                .append(csvEscape(event.detail)).append('\n')
            flushIfNeeded(pendingEvents, target)
        }
    }

    suspend fun stopSession() = withContext(Dispatchers.IO) {
        mutex.withLock { stopSessionLocked() }
    }

    private fun stopSessionLocked() {
        runCatching {
            flush(pendingEcg, ecgTarget)
            flush(pendingAcc, accTarget)
            flush(pendingHr, hrTarget)
            flush(pendingPackets, packetTarget)
            flush(pendingEvents, eventTarget)
        }.onFailure { error ->
            _status.value = _status.value.copy(lastError = error.message)
        }
        ecgTarget = null
        accTarget = null
        hrTarget = null
        packetTarget = null
        eventTarget = null
        _status.value = _status.value.copy(isRecording = false)
    }

    private fun clearPending() {
        pendingEcg.clear()
        pendingAcc.clear()
        pendingHr.clear()
        pendingPackets.clear()
        pendingEvents.clear()
    }

    private fun updateCounts() {
        _status.value = _status.value.copy(
            isRecording = true,
            ecgSamplesWritten = ecgSamplesWritten,
            accSamplesWritten = accSamplesWritten,
            hrMeasurementsWritten = hrMeasurementsWritten,
            packetsWritten = packetsWritten,
            lastError = null,
        )
    }

    private fun flushIfNeeded(buffer: StringBuilder, target: File) {
        if (buffer.length >= FLUSH_CHARS) flush(buffer, target)
    }

    private fun flush(buffer: StringBuilder, target: File?) {
        if (target == null || buffer.isEmpty()) return
        val text = buffer.toString()
        buffer.clear()
        writeText(target, text, append = true)
    }

    private fun createTarget(fileName: String, header: String): File {
        val directory = File(appContext.filesDir, "recordings/polar_h10")
        directory.mkdirs()
        val file = File(directory, fileName)
        writeText(file, header, append = false)
        return file
    }

    private fun writeText(target: File, text: String, append: Boolean) {
        FileOutputStream(target, append).bufferedWriter().use { it.write(text) }
    }

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private companion object {
        private const val FLUSH_CHARS = 32_768
        private const val ECG_HEADER =
            "aligned_timestamp_ms,aligned_time_iso,sensor_timestamp_ns,arrival_wall_time_ms,arrival_elapsed_ns,ecg_uv\n"
        private const val ACC_HEADER =
            "aligned_timestamp_ms,aligned_time_iso,sensor_timestamp_ns,arrival_wall_time_ms,arrival_elapsed_ns,acc_x_mg,acc_y_mg,acc_z_mg\n"
        private const val HR_HEADER =
            "arrival_wall_time_ms,arrival_time_iso,arrival_elapsed_ns,heart_rate_bpm,energy_expended_kj,rr_intervals_ms\n"
        private const val PACKET_HEADER =
            "arrival_wall_time_ms,arrival_elapsed_ns,direction,characteristic,payload_hex\n"
        private const val EVENT_HEADER = "wall_time_ms,wall_time_iso,event,detail\n"
    }
}
