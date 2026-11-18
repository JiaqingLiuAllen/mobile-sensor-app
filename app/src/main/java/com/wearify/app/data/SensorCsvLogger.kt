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

class SensorCsvLogger(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val pendingSensors = StringBuilder()
    private val pendingMic = StringBuilder()
    private val dateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    private var sensorTarget: File? = null
    private var micTarget: File? = null
    private var samplesWritten = 0L
    private var micSamplesWritten = 0L

    private val _status = MutableStateFlow(DataLogStatus())
    val status: StateFlow<DataLogStatus> = _status.asStateFlow()

    suspend fun startSession(
        activity: DataCollectionActivity,
    ) {
        withContext(Dispatchers.IO) {
            val sessionStamp = fileStamp.format(Date())
            mutex.withLock {
                stopSessionLocked()
                samplesWritten = 0L
                micSamplesWritten = 0L
                pendingSensors.clear()
                pendingMic.clear()

                val createdSensors = createLogTarget(
                    sessionStamp = sessionStamp,
                    activity = activity,
                    suffix = "sensors",
                )
                val createdMic = createLogTarget(
                    sessionStamp = sessionStamp,
                    activity = activity,
                    suffix = "contact_mic_pcm",
                )
                writeText(createdSensors, SENSOR_CSV_HEADER, append = false)
                writeText(createdMic, MIC_CSV_HEADER, append = false)
                sensorTarget = createdSensors
                micTarget = createdMic
                _status.value = DataLogStatus(
                    isRecording = true,
                    displayName = createdSensors.name,
                    relativePath = createdSensors.path,
                    micRelativePath = createdMic.path,
                )
            }

        }
    }

    suspend fun appendSample(sample: SensorSample) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val activeTarget = sensorTarget ?: return@withLock
                pendingSensors.append(sample.toCsvLine())
                samplesWritten++

                _status.value = _status.value.copy(
                    isRecording = true,
                    samplesWritten = samplesWritten,
                    lastError = null,
                )

                if (pendingSensors.length >= FLUSH_CHARS || samplesWritten % FLUSH_EVERY_SENSOR_ROWS == 0L) {
                    flushSensorsLocked(activeTarget)
                }
            }
        }
    }

    suspend fun appendContactMicFrame(frame: ContactMicFrame) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val activeTarget = micTarget ?: return@withLock
                pendingMic.append(frame.toMicCsvLines())
                micSamplesWritten += frame.samples.size

                _status.value = _status.value.copy(
                    isRecording = true,
                    micSamplesWritten = micSamplesWritten,
                    lastError = null,
                )

                if (pendingMic.length >= FLUSH_CHARS || micSamplesWritten % FLUSH_EVERY_MIC_ROWS == 0L) {
                    flushMicLocked(activeTarget)
                }
            }
        }
    }

    suspend fun stopSession() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                stopSessionLocked()
            }
        }
    }

    private fun stopSessionLocked() {
        val activeSensorTarget = sensorTarget
        val activeMicTarget = micTarget
        if (activeSensorTarget != null) {
            runCatching { flushSensorsLocked(activeSensorTarget) }
                .onFailure { error ->
                    _status.value = _status.value.copy(lastError = error.message)
                }
        }
        if (activeMicTarget != null) {
            runCatching { flushMicLocked(activeMicTarget) }
                .onFailure { error ->
                    _status.value = _status.value.copy(lastError = error.message)
                }
        }
        sensorTarget = null
        micTarget = null
        _status.value = _status.value.copy(isRecording = false)
    }

    private fun flushSensorsLocked(activeTarget: File) {
        if (pendingSensors.isEmpty()) return
        val text = pendingSensors.toString()
        pendingSensors.clear()
        writeText(activeTarget, text, append = true)
    }

    private fun flushMicLocked(activeTarget: File) {
        if (pendingMic.isEmpty()) return
        val text = pendingMic.toString()
        pendingMic.clear()
        writeText(activeTarget, text, append = true)
    }

    private fun writeText(
        activeTarget: File,
        text: String,
        append: Boolean,
    ) {
        FileOutputStream(activeTarget, append).bufferedWriter().use { it.write(text) }
    }

    private fun createLogTarget(
        sessionStamp: String,
        activity: DataCollectionActivity,
        suffix: String,
    ): File {
        val fileName = buildDataCollectionFileName(
            sessionStamp = sessionStamp,
            activity = activity,
            suffix = suffix,
        )
        val directory = File(appContext.filesDir, "recordings")
        directory.mkdirs()
        return File(directory, fileName)
    }

    private fun SensorSample.toCsvLine(): String =
        buildString {
            append(timestampMs)
            append(',')
            append(dateTime.format(Date(timestampMs)))
            append(',')
            append(receivedAtMs)
            append(',')
            append(dateTime.format(Date(receivedAtMs)))
            append(',')
            appendNullable(deviceUptimeMs)
            append(',')
            appendNullable(als)
            append(',')
            appendNullable(uv)
            append(',')
            appendNullable(tempC)
            append(',')
            appendNullable(ax)
            append(',')
            appendNullable(ay)
            append(',')
            appendNullable(az)
            append(',')
            appendNullable(gx)
            append(',')
            appendNullable(gy)
            append(',')
            appendNullable(gz)
            append(',')
            appendNullable(ir)
            append(',')
            appendNullableText(ppgChannel)
            append(',')
            appendNullable(ppgSequence)
            append(',')
            appendNullable(ppgLedPower)
            append(',')
            appendNullable(ppgQualityPct)
            append(',')
            appendNullable(ppgFlags)
            append(',')
            appendNullable(ppgAcPerMille)
            append(',')
            appendNullable(ppgErrorCode)
            append(',')
            appendNullable(ppgRecoveryAttemptCount)
            append(',')
            appendNullable(ppgBrownoutCount)
            append(',')
            appendNullable(ppgFirmwareResetCount)
            append(',')
            appendNullable(resetReason)
            append(',')
            appendNullable(i2cQuarantineMask)
            append(',')
            appendNullable(i2cBusFaultCount)
            append(',')
            appendNullable(i2cBusRecoveryCount)
            append(',')
            appendNullable(watchdogSensorMask)
            append('\n')
        }

    private fun ContactMicFrame.toMicCsvLines(): String =
        buildString {
            if (samples.isEmpty()) return@buildString

            val sampleRate = sampleRateHz.coerceAtLeast(1)
            val samplePeriodUs = 1_000_000.0 / sampleRate
            val chunkSpanUs = samplePeriodUs * (samples.size - 1).coerceAtLeast(0)
            val firstAlignedSampleUs = timestampMs * 1_000.0 - chunkSpanUs
            val frameDeviceEndUs = deviceEndUptimeUs()
            val firstDeviceSampleUs = frameDeviceEndUs?.let { it - chunkSpanUs }

            samples.forEachIndexed { index, pcm ->
                val alignedSampleUs = (firstAlignedSampleUs + index * samplePeriodUs).toLong()
                val deviceSampleUs = firstDeviceSampleUs?.let { (it + index * samplePeriodUs).toLong() }
                append(alignedSampleUs)
                append(',')
                append(timestampMs)
                append(',')
                append(receivedAtMs)
                append(',')
                append(dateTime.format(Date(receivedAtMs)))
                append(',')
                appendNullable(deviceSampleUs)
                append(',')
                appendNullable(deviceUptimeMs)
                append(',')
                appendNullable(frameDeviceEndUs)
                append(',')
                appendNullable(deviceUptimeUs)
                append(',')
                appendNullable(sequence)
                append(',')
                append(sampleRateHz)
                append(',')
                append(index)
                append(',')
                append(pcm)
                append('\n')
            }
        }

    private fun StringBuilder.appendNullable(value: Number?) {
        if (value != null) append(value)
    }

    private fun StringBuilder.appendNullableText(value: String?) {
        if (value != null) append(value)
    }

    companion object {
        private const val FLUSH_EVERY_SENSOR_ROWS = 20L
        private const val FLUSH_EVERY_MIC_ROWS = 4_000L
        private const val FLUSH_CHARS = 8192
        private const val SENSOR_CSV_HEADER =
            "aligned_timestamp_ms,aligned_time_iso,received_host_timestamp_ms,received_host_time_iso,device_uptime_ms,als,uv,temp_c,ax,ay,az,gx,gy,gz,ir,ppg_channel,ppg_sequence,ppg_led_power,ppg_quality_pct,ppg_flags,ppg_ac_per_mille,ppg_error_code,ppg_recovery_attempt_count,ppg_brownout_count,ppg_firmware_reset_count,reset_reason,i2c_quarantine_mask,i2c_bus_fault_count,i2c_bus_recovery_count,watchdog_sensor_mask\n"
        private const val MIC_CSV_HEADER =
            "aligned_sample_time_us,frame_aligned_timestamp_ms,frame_received_host_timestamp_ms,frame_received_host_time_iso,device_sample_uptime_us,frame_device_uptime_ms,frame_device_uptime_us,frame_device_micros_us,mic_sequence,sample_rate_hz,sample_index,pcm\n"
    }
}
