package com.wearify.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.wearify.app.data.BleScanDebugStatus
import com.wearify.app.data.publicDeviceName
import com.wearify.app.data.BleConnectionState
import com.wearify.app.data.ContactMicFrame
import com.wearify.app.data.DEFAULT_DEVICE_NAME
import com.wearify.app.data.DeviceClockAligner
import com.wearify.app.data.NusLineAssembler
import com.wearify.app.data.ScannedBleDevice
import com.wearify.app.data.SensorParser
import com.wearify.app.data.SensorSample
import com.wearify.app.data.deviceEndUptimeUs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BleRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lineAssembler = NusLineAssembler()
    private val deviceClockAligner = DeviceClockAligner()

    private val _devices = MutableStateFlow<List<ScannedBleDevice>>(emptyList())
    val devices: StateFlow<List<ScannedBleDevice>> = _devices.asStateFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _samples = MutableSharedFlow<SensorSample>(extraBufferCapacity = 1024)
    val samples: SharedFlow<SensorSample> = _samples.asSharedFlow()

    private val _contactMicFrames = MutableSharedFlow<ContactMicFrame>(extraBufferCapacity = 128)
    val contactMicFrames: SharedFlow<ContactMicFrame> = _contactMicFrames.asSharedFlow()

    private val _malformedLineCount = MutableStateFlow(0)
    val malformedLineCount: StateFlow<Int> = _malformedLineCount.asStateFlow()

    private val _scanDebugStatus = MutableStateFlow(currentScanDebugStatus(lastError = null))
    val scanDebugStatus: StateFlow<BleScanDebugStatus> = _scanDebugStatus.asStateFlow()

    private val scannedDevices = linkedMapOf<android.bluetooth.BluetoothDevice, ScannedBleDevice>()
    private val scanCallbacks = mutableListOf<ScanCallback>()
    private var nusManager: NusBleManager? = null
    private var scanTargetOnly = false
    private var scanTargetDeviceName = DEFAULT_DEVICE_NAME
    private var connectionGeneration = 0L
    private var activeDeviceName: String? = null

    fun startScan(
        targetOnly: Boolean = false,
        targetDeviceName: String = DEFAULT_DEVICE_NAME,
    ) {
        refreshScanDebugStatus()
        if (_connectionState.value is BleConnectionState.Connected) {
            stopScanOnly()
            scannedDevices.clear()
            _devices.value = emptyList()
            return
        }
        if (!hasRequiredBlePermissions()) {
            _connectionState.value = BleConnectionState.Error("Bluetooth permissions are required")
            refreshScanDebugStatus("missing runtime permission")
            return
        }
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || scanner == null) {
            _connectionState.value = BleConnectionState.Error("Bluetooth is off or unavailable")
            refreshScanDebugStatus("bluetooth off or scanner unavailable")
            return
        }

        stopScanOnly()
        scanTargetOnly = targetOnly
        scanTargetDeviceName = targetDeviceName.trim().ifBlank { DEFAULT_DEVICE_NAME }
        scannedDevices.clear()
        _devices.value = emptyList()
        _connectionState.value = if (targetOnly) {
            BleConnectionState.Connecting(scanTargetDeviceName)
        } else {
            BleConnectionState.Scanning
        }
        _scanDebugStatus.value = currentScanDebugStatus(lastError = null).copy(
            scanStarts = _scanDebugStatus.value.scanStarts + 1,
            rawCallbacks = 0,
            batchCallbacks = 0,
            lastName = null,
            lastRssi = null,
            lastError = null,
        )

        startScannerPass(
            scanner = scanner,
            label = if (targetOnly) "target-$scanTargetDeviceName" else "manual-unfiltered",
        )
    }

    fun stopScan() {
        stopScanOnly()
        scanTargetOnly = false
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Idle
        }
    }

    fun connect(device: ScannedBleDevice) {
        stopScanOnly()
        val generation = ++connectionGeneration
        disconnect(closeOnly = true)
        lineAssembler.clear()
        deviceClockAligner.clear()
        activeDeviceName = device.displayName
        WearifyForegroundService.start(appContext)
        _connectionState.value = BleConnectionState.Connecting(device.displayName)

        nusManager = NusBleManager(
            context = appContext,
            deviceName = device.displayName,
            onStateChanged = { state ->
                handleConnectionState(generation, state)
            },
            onBytesReceived = { bytes ->
                handleBytes(generation, bytes)
            },
        ).also { manager ->
            manager.connect(device.bluetoothDevice)
                // Keep the GATT request alive across a firmware watchdog reboot.
                // Manual disconnect() still closes the manager and cancels this.
                .useAutoConnect(true)
                .enqueue()
        }
    }

    fun disconnect(closeOnly: Boolean = false) {
        stopScanOnly()
        if (!closeOnly) {
            connectionGeneration++
            activeDeviceName = null
        }
        nusManager?.disconnect()?.enqueue()
        nusManager?.close()
        nusManager = null
        lineAssembler.clear()
        deviceClockAligner.clear()
        scanTargetOnly = false
        WearifyForegroundService.stop(appContext)
        if (!closeOnly) {
            _connectionState.value = BleConnectionState.Disconnected("Disconnected")
        }
    }

    fun clearMalformedCount() {
        _malformedLineCount.value = 0
    }

    private fun handleConnectionState(
        generation: Long,
        state: BleConnectionState,
    ) {
        if (generation != connectionGeneration) return

        _connectionState.value = state
        when (state) {
            is BleConnectionState.Connected -> activeDeviceName = state.deviceName
            is BleConnectionState.Connecting -> activeDeviceName = state.deviceName
            is BleConnectionState.Disconnected -> {
                lineAssembler.clear()
                deviceClockAligner.clear()
                if (activeDeviceName != null && nusManager != null) {
                    WearifyForegroundService.start(appContext)
                } else {
                    WearifyForegroundService.stop(appContext)
                }
            }
            is BleConnectionState.Error -> {
                if (activeDeviceName != null && nusManager != null) {
                    WearifyForegroundService.start(appContext)
                } else {
                    WearifyForegroundService.stop(appContext)
                }
            }
            BleConnectionState.Idle -> Unit
            BleConnectionState.Scanning -> Unit
        }
    }

    private fun handleBytes(
        generation: Long,
        bytes: ByteArray,
    ) {
        if (generation != connectionGeneration) return
        if (bytes.isNotEmpty()) {
            markConnectedFromData()
        }
        val lines = lineAssembler.append(bytes)
        val receivedAtWallMs = System.currentTimeMillis()
        val receivedAtElapsedUs = SystemClock.elapsedRealtimeNanos() / 1_000L
        lines.forEach { line ->
            if (line.startsWith("MIC:", ignoreCase = true)) {
                val parsed = SensorParser.parseContactMicLine(
                    line = line,
                    receivedAtMs = receivedAtWallMs,
                )
                if (parsed.malformedFieldCount > 0) {
                    _malformedLineCount.value += parsed.malformedFieldCount
                }
                parsed.frame?.let { frame ->
                    val alignedFrame = frame.withAlignedTimestamp(
                        receivedAtWallMs = receivedAtWallMs,
                        receivedAtElapsedUs = receivedAtElapsedUs,
                    )
                    if (!_contactMicFrames.tryEmit(alignedFrame)) {
                        scope.launch {
                            _contactMicFrames.emit(alignedFrame)
                        }
                    }
                }
                return@forEach
            }

            val parsed = SensorParser.parseLine(
                line = line,
                receivedAtMs = receivedAtWallMs,
            )
            if (parsed.malformedFieldCount > 0) {
                _malformedLineCount.value += parsed.malformedFieldCount
            }
            parsed.sample?.let { sample ->
                val alignedSample = sample.withAlignedTimestamp(
                    receivedAtWallMs = receivedAtWallMs,
                    receivedAtElapsedUs = receivedAtElapsedUs,
                )
                if (!_samples.tryEmit(alignedSample)) {
                    scope.launch {
                        _samples.emit(alignedSample)
                    }
                }
            }
        }
    }

    private fun markConnectedFromData() {
        val state = _connectionState.value
        if (state is BleConnectionState.Connected) return

        stopScanOnly()
        val deviceName = when (state) {
            is BleConnectionState.Connecting -> state.deviceName
            else -> activeDeviceName ?: DEFAULT_DEVICE_NAME
        }
        activeDeviceName = deviceName
        WearifyForegroundService.start(appContext)
        _connectionState.value = BleConnectionState.Connected(deviceName)
    }

    private fun SensorSample.withAlignedTimestamp(
        receivedAtWallMs: Long,
        receivedAtElapsedUs: Long,
    ): SensorSample {
        val alignedTimestampMs = deviceClockAligner.alignMillis(
            deviceUptimeUs = deviceUptimeMs?.let { it * 1_000L },
            receivedAtWallMs = receivedAtWallMs,
            receivedAtElapsedUs = receivedAtElapsedUs,
        )
        return copy(
            timestampMs = alignedTimestampMs,
            receivedAtMs = receivedAtWallMs,
        )
    }

    private fun ContactMicFrame.withAlignedTimestamp(
        receivedAtWallMs: Long,
        receivedAtElapsedUs: Long,
    ): ContactMicFrame {
        val alignedTimestampMs = deviceClockAligner.alignMillis(
            deviceUptimeUs = deviceEndUptimeUs(),
            receivedAtWallMs = receivedAtWallMs,
            receivedAtElapsedUs = receivedAtElapsedUs,
        )
        return copy(
            timestampMs = alignedTimestampMs,
            receivedAtMs = receivedAtWallMs,
        )
    }

    private fun upsertScanResult(result: ScanResult, isBatch: Boolean = false) {
        val device = result.device ?: return
        val name = publicDeviceName(result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull())

        val scanned = ScannedBleDevice(
            name = name,
            rssi = result.rssi,
            bluetoothDevice = device,
        )
        if (scanTargetOnly && !scanned.matchesTargetName(scanTargetDeviceName)) {
            return
        }

        updateScanDebugFromResult(result, name, isBatch = isBatch)
        scannedDevices[device] = scanned
        _devices.value = scannedDevices.values
            .sortedWith(
                compareByDescending<ScannedBleDevice> { it.matchesTargetName(scanTargetDeviceName) }
                    .thenByDescending { it.hasXiaoName }
                    .thenByDescending { it.hasDisplayName }
                    .thenByDescending { it.rssi },
            )
    }

    private fun startScannerPass(
        scanner: android.bluetooth.le.BluetoothLeScanner,
        label: String,
    ) {
        val callback = createScanCallback(label)
        scanCallbacks += callback

        try {
            scanner.startScan(null, scanSettings(), callback)
        } catch (securityException: SecurityException) {
            scanCallbacks -= callback
            _connectionState.value = BleConnectionState.Error("Bluetooth permission denied")
            refreshScanDebugStatus("Bluetooth permission denied")
        } catch (illegalArgument: IllegalArgumentException) {
            scanCallbacks -= callback
            _connectionState.value = BleConnectionState.Error("BLE scan could not start: ${illegalArgument.message}")
            refreshScanDebugStatus("scan start failed: ${illegalArgument.message}")
        }
    }

    private fun createScanCallback(label: String): ScanCallback =
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                upsertScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> upsertScanResult(result, isBatch = true) }
            }

            override fun onScanFailed(errorCode: Int) {
                _connectionState.value = BleConnectionState.Error("BLE scan failed in $label ($errorCode)")
                refreshScanDebugStatus("scan failed in $label ($errorCode)")
            }
        }

    private fun scanSettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

    private fun stopScanOnly() {
        val callbacks = scanCallbacks.toList()
        if (callbacks.isEmpty()) {
            scanTargetOnly = false
            return
        }
        try {
            callbacks.forEach { callback ->
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
            }
        } catch (_: SecurityException) {
        } finally {
            scanCallbacks.clear()
            scanTargetOnly = false
        }
    }

    private fun hasRequiredBlePermissions(): Boolean {
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

    private fun updateScanDebugFromResult(
        result: ScanResult,
        name: String?,
        isBatch: Boolean,
    ) {
        val previous = _scanDebugStatus.value
        _scanDebugStatus.value = currentScanDebugStatus(lastError = previous.lastError).copy(
            scanStarts = previous.scanStarts,
            rawCallbacks = previous.rawCallbacks + if (isBatch) 0 else 1,
            batchCallbacks = previous.batchCallbacks + if (isBatch) 1 else 0,
            lastName = name,
            lastRssi = result.rssi,
            lastError = previous.lastError,
        )
    }

    private fun refreshScanDebugStatus(lastError: String? = _scanDebugStatus.value.lastError) {
        val previous = _scanDebugStatus.value
        _scanDebugStatus.value = currentScanDebugStatus(lastError = lastError).copy(
            scanStarts = previous.scanStarts,
            rawCallbacks = previous.rawCallbacks,
            batchCallbacks = previous.batchCallbacks,
            lastName = previous.lastName,
            lastRssi = previous.lastRssi,
            lastError = lastError,
        )
    }

    private fun currentScanDebugStatus(lastError: String?): BleScanDebugStatus =
        BleScanDebugStatus(
            hasScanPermission = hasPermission(Manifest.permission.BLUETOOTH_SCAN),
            hasConnectPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT),
            hasFineLocationPermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
            hasCoarseLocationPermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
            isLocationEnabled = isLocationEnabled(),
            lastError = lastError,
        )

    private fun isLocationEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            runCatching {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            }.getOrDefault(false)
        }

    private fun hasPermission(permission: String): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            (permission == Manifest.permission.BLUETOOTH_SCAN || permission == Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            true
        } else {
            ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        }
}
