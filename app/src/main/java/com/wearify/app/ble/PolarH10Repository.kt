package com.wearify.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.wearify.app.data.DeviceClockAligner
import com.wearify.app.data.POLAR_H10_ACC_SAMPLE_RATE_HZ
import com.wearify.app.data.POLAR_H10_ECG_SAMPLE_RATE_HZ
import com.wearify.app.data.PolarAccFrame
import com.wearify.app.data.PolarAccSample
import com.wearify.app.data.PolarBlePacket
import com.wearify.app.data.PolarCaptureEvent
import com.wearify.app.data.PolarEcgFrame
import com.wearify.app.data.PolarEcgSample
import com.wearify.app.data.PolarH10ConnectionState
import com.wearify.app.data.PolarHrMeasurement
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val SCAN_TIMEOUT_MS = 10_000L
private const val CONNECT_TIMEOUT_MS = 15_000L
private const val STREAM_START_TIMEOUT_MS = 8_000L

@SuppressLint("MissingPermission")
class PolarH10Repository(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clockAligner = DeviceClockAligner()

    private val _state = MutableStateFlow<PolarH10ConnectionState>(PolarH10ConnectionState.Idle)
    val state: StateFlow<PolarH10ConnectionState> = _state.asStateFlow()

    private val _ecgFrames = MutableSharedFlow<PolarEcgFrame>(extraBufferCapacity = 64)
    val ecgFrames: SharedFlow<PolarEcgFrame> = _ecgFrames.asSharedFlow()

    private val _accFrames = MutableSharedFlow<PolarAccFrame>(extraBufferCapacity = 64)
    val accFrames: SharedFlow<PolarAccFrame> = _accFrames.asSharedFlow()

    private val _heartRate = MutableSharedFlow<PolarHrMeasurement>(extraBufferCapacity = 32)
    val heartRate: SharedFlow<PolarHrMeasurement> = _heartRate.asSharedFlow()

    private val _packets = MutableSharedFlow<PolarBlePacket>(extraBufferCapacity = 256)
    val packets: SharedFlow<PolarBlePacket> = _packets.asSharedFlow()

    private val _events = MutableSharedFlow<PolarCaptureEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<PolarCaptureEvent> = _events.asSharedFlow()

    private var manager: PolarH10BleManager? = null
    private var scanCallback: ScanCallback? = null
    private var scanTimeoutJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var streamStartTimeoutJob: Job? = null
    private var terminalError: String? = null
    private var explicitDisconnect = false

    fun startCapture() {
        if (_state.value is PolarH10ConnectionState.Scanning ||
            _state.value is PolarH10ConnectionState.Connecting ||
            _state.value is PolarH10ConnectionState.StartingStreams ||
            _state.value is PolarH10ConnectionState.Recording
        ) {
            return
        }
        if (!hasRequiredPermissions()) {
            fail("Bluetooth permissions are required for Polar H10")
            return
        }
        val scanner = bluetoothAdapter?.takeIf(BluetoothAdapter::isEnabled)?.bluetoothLeScanner
        if (scanner == null) {
            fail("Bluetooth is off or unavailable")
            return
        }

        explicitDisconnect = false
        terminalError = null
        clockAligner.clear()
        PolarH10ForegroundService.start(appContext)
        _state.value = PolarH10ConnectionState.Scanning
        emitEvent("scan_started")

        val callback = createScanCallback()
        scanCallback = callback
        scanner.startScan(null, scanSettings(), callback)
        scanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_state.value is PolarH10ConnectionState.Scanning) {
                fail("No nearby Polar H10 was found")
            }
        }
    }

    fun disconnect() {
        explicitDisconnect = true
        terminalError = null
        cancelConnectionTimeouts()
        stopScan()
        emitEvent("disconnect_requested")
        val activeManager = manager
        if (activeManager != null) {
            activeManager.stopAndDisconnect()
        } else {
            finishDisconnected("Polar capture stopped")
        }
    }

    fun close() {
        explicitDisconnect = true
        cancelConnectionTimeouts()
        stopScan()
        manager?.stopAndDisconnect()
        manager = null
        PolarH10ForegroundService.stop(appContext)
    }

    private fun createScanCallback(): ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handleScanResult(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            fail("Polar BLE scan failed ($errorCode)")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        if (_state.value !is PolarH10ConnectionState.Scanning) return
        val device = result.device ?: return
        val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()
        if (name?.startsWith("Polar H10", ignoreCase = true) != true) return

        connectCandidate(PolarScanCandidate(device, "Polar H10", result.rssi))
    }

    private fun connectCandidate(candidate: PolarScanCandidate) {
        if (_state.value !is PolarH10ConnectionState.Scanning) return
        stopScan()
        _state.value = PolarH10ConnectionState.Connecting(candidate.name)
        emitEvent("device_found", "${candidate.rssi}dBm")
        connectTimeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_state.value is PolarH10ConnectionState.Connecting) {
                fail("Timed out connecting to the heart-rate sensor")
            }
        }

        manager = PolarH10BleManager(
            context = appContext,
            deviceName = candidate.name,
            onStartingStreams = {
                connectTimeoutJob?.cancel()
                connectTimeoutJob = null
                _state.value = PolarH10ConnectionState.StartingStreams(candidate.name)
                emitEvent("ble_ready", "starting ECG and ACC")
                streamStartTimeoutJob = scope.launch {
                    delay(STREAM_START_TIMEOUT_MS)
                    if (_state.value is PolarH10ConnectionState.StartingStreams) {
                        fail("Timed out starting Polar ECG and ACC streams")
                    }
                }
            },
            onRecording = {
                streamStartTimeoutJob?.cancel()
                streamStartTimeoutJob = null
                _state.value = PolarH10ConnectionState.Recording(candidate.name)
                emitEvent("streams_started", "ECG 130 Hz; ACC 25 Hz; native HR/RR")
            },
            onDisconnected = { reason ->
                manager = null
                if (terminalError == null) finishDisconnected(reason)
            },
            onFatalError = { message -> fail(message, disconnectManager = false) },
            onPmdData = ::handlePmdPacket,
            onHeartRate = ::handleHeartRate,
            onPacket = { packet -> _packets.tryEmit(packet) },
        ).also { polarManager ->
            polarManager.connect(candidate.device)
                .retry(3, 250)
                .useAutoConnect(false)
                .enqueue()
        }
    }

    private fun handlePmdPacket(payload: ByteArray, wallMs: Long, elapsedNs: Long) {
        runCatching {
            when (payload.firstOrNull()?.toInt()?.and(0xFF)) {
                POLAR_ECG_MEASUREMENT_TYPE -> emitEcgFrame(
                    PolarH10Protocol.decodeEcgPacket(payload),
                    wallMs,
                    elapsedNs,
                )

                POLAR_ACC_MEASUREMENT_TYPE -> emitAccFrame(
                    PolarH10Protocol.decodeAccPacket(payload),
                    wallMs,
                    elapsedNs,
                )

                else -> emitEvent("unsupported_pmd_measurement", payload.firstOrNull()?.toString().orEmpty())
            }
        }.onFailure { error ->
            emitEvent("pmd_decode_error", error.message.orEmpty())
        }
    }

    private fun emitEcgFrame(frame: DecodedPolarEcgFrame, wallMs: Long, elapsedNs: Long) {
        val alignedEndMs = clockAligner.alignMillis(frame.timestampNs / 1_000L, wallMs, elapsedNs / 1_000L)
        val timestamps = PolarH10Protocol.sampleTimestampsNs(
            frame.timestampNs,
            frame.samplesUv.size,
            POLAR_H10_ECG_SAMPLE_RATE_HZ,
        )
        val samples = frame.samplesUv.zip(timestamps).map { (value, timestampNs) ->
            PolarEcgSample(
                timestampMs = alignedEndMs - ((frame.timestampNs - timestampNs) / 1_000_000.0).roundToLong(),
                sensorTimestampNs = timestampNs,
                arrivalWallTimeMs = wallMs,
                arrivalElapsedNs = elapsedNs,
                ecgUv = value,
            )
        }
        _ecgFrames.tryEmit(
            PolarEcgFrame(frame.timestampNs, wallMs, elapsedNs, samples),
        )
    }

    private fun emitAccFrame(frame: DecodedPolarAccFrame, wallMs: Long, elapsedNs: Long) {
        val alignedEndMs = clockAligner.alignMillis(frame.timestampNs / 1_000L, wallMs, elapsedNs / 1_000L)
        val timestamps = PolarH10Protocol.sampleTimestampsNs(
            frame.timestampNs,
            frame.samples.size,
            POLAR_H10_ACC_SAMPLE_RATE_HZ,
        )
        val samples = frame.samples.zip(timestamps).map { (value, timestampNs) ->
            PolarAccSample(
                timestampMs = alignedEndMs - ((frame.timestampNs - timestampNs) / 1_000_000.0).roundToLong(),
                sensorTimestampNs = timestampNs,
                arrivalWallTimeMs = wallMs,
                arrivalElapsedNs = elapsedNs,
                xMg = value.xMg,
                yMg = value.yMg,
                zMg = value.zMg,
            )
        }
        _accFrames.tryEmit(
            PolarAccFrame(frame.timestampNs, wallMs, elapsedNs, samples),
        )
    }

    private fun handleHeartRate(payload: ByteArray, wallMs: Long, elapsedNs: Long) {
        runCatching { PolarH10Protocol.decodeHrMeasurement(payload) }
            .onSuccess { measurement ->
                _heartRate.tryEmit(
                    PolarHrMeasurement(
                        arrivalWallTimeMs = wallMs,
                        arrivalElapsedNs = elapsedNs,
                        heartRateBpm = measurement.heartRateBpm,
                        energyExpendedKj = measurement.energyExpendedKj,
                        rrIntervalsMs = measurement.rrIntervalsMs,
                    ),
                )
            }
            .onFailure { error -> emitEvent("hr_decode_error", error.message.orEmpty()) }
    }

    private fun fail(message: String, disconnectManager: Boolean = true) {
        terminalError = message
        cancelConnectionTimeouts()
        stopScan()
        _state.value = PolarH10ConnectionState.Error(message)
        emitEvent("fatal_error", message)
        PolarH10ForegroundService.stop(appContext)
        if (disconnectManager) manager?.stopAndDisconnect()
    }

    private fun finishDisconnected(reason: String) {
        manager = null
        cancelConnectionTimeouts()
        stopScan()
        _state.value = PolarH10ConnectionState.Disconnected(reason)
        emitEvent(if (explicitDisconnect) "stopped_by_user" else "ble_disconnected", reason)
        PolarH10ForegroundService.stop(appContext)
    }

    private fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        val callback = scanCallback ?: return
        scanCallback = null
        runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback) }
    }

    private fun cancelConnectionTimeouts() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        streamStartTimeoutJob?.cancel()
        streamStartTimeoutJob = null
    }

    private fun emitEvent(event: String, detail: String = "") {
        _events.tryEmit(PolarCaptureEvent(System.currentTimeMillis(), event, detail))
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun scanSettings(): ScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        .build()

    private data class PolarScanCandidate(
        val device: BluetoothDevice,
        val name: String,
        val rssi: Int,
    )
}
