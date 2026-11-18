package com.wearify.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.SystemClock
import com.wearify.app.data.PolarBlePacket
import java.util.UUID
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.WriteRequest
import no.nordicsemi.android.ble.observer.ConnectionObserver

private val POLAR_PMD_SERVICE_UUID: UUID = UUID.fromString("fb005c80-02e7-f387-1cad-8acd2d8df0c8")
private val POLAR_PMD_CONTROL_UUID: UUID = UUID.fromString("fb005c81-02e7-f387-1cad-8acd2d8df0c8")
private val POLAR_PMD_DATA_UUID: UUID = UUID.fromString("fb005c82-02e7-f387-1cad-8acd2d8df0c8")
private val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

/** Raw Polar PMD transport. Decoding and time alignment stay in [PolarH10Repository]. */
@SuppressLint("MissingPermission")
internal class PolarH10BleManager(
    context: Context,
    private val deviceName: String,
    private val onStartingStreams: () -> Unit,
    private val onRecording: () -> Unit,
    private val onDisconnected: (String) -> Unit,
    private val onFatalError: (String) -> Unit,
    private val onPmdData: (ByteArray, Long, Long) -> Unit,
    private val onHeartRate: (ByteArray, Long, Long) -> Unit,
    private val onPacket: (PolarBlePacket) -> Unit,
) : BleManager(context), ConnectionObserver {
    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private var heartRateCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingStartMeasurement: Int? = null
    private var streamsStarted = false
    private var stopping = false

    init {
        setConnectionObserver(this)
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val pmd = gatt.getService(POLAR_PMD_SERVICE_UUID) ?: return false
        controlCharacteristic = pmd.getCharacteristic(POLAR_PMD_CONTROL_UUID)
        dataCharacteristic = pmd.getCharacteristic(POLAR_PMD_DATA_UUID)
        heartRateCharacteristic = gatt.getService(HEART_RATE_SERVICE_UUID)
            ?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)

        val control = controlCharacteristic ?: return false
        val data = dataCharacteristic ?: return false
        return control.canWrite() && control.canUpdate() && data.canUpdate()
    }

    override fun initialize() {
        val control = controlCharacteristic ?: return
        val data = dataCharacteristic ?: return
        setNotificationCallback(control).with { _, value ->
            value.value?.let { payload ->
                emitPacket("rx", "pmd_control", payload)
                handleControlResponse(payload)
            }
        }
        setNotificationCallback(data).with { _, value ->
            value.value?.let { payload ->
                val (wallMs, elapsedNs) = packetTimes()
                onPacket(packet("rx", "pmd_data", payload, wallMs, elapsedNs))
                onPmdData(payload, wallMs, elapsedNs)
            }
        }
        heartRateCharacteristic?.let { characteristic ->
            setNotificationCallback(characteristic).with { _, value ->
                value.value?.let { payload ->
                    val (wallMs, elapsedNs) = packetTimes()
                    onPacket(packet("rx", "heart_rate", payload, wallMs, elapsedNs))
                    onHeartRate(payload, wallMs, elapsedNs)
                }
            }
        }

        requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH).enqueue()
        requestMtu(247).enqueue()
        enableUpdates(control).enqueue()
        enableUpdates(data).enqueue()
        heartRateCharacteristic?.let { enableUpdates(it).enqueue() }
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        onStartingStreams()
        writeStartCommand(
            measurementType = POLAR_ECG_MEASUREMENT_TYPE,
            label = "start_ecg",
            command = PolarH10Protocol.startEcgCommand,
        )
    }

    fun stopAndDisconnect() {
        if (stopping) return
        stopping = true
        pendingStartMeasurement = null
        val control = controlCharacteristic
        if (!isConnected || control == null || !streamsStarted) {
            disconnectNow()
            return
        }

        emitPacket("tx", "pmd_control", PolarH10Protocol.stopAccCommand)
        emitPacket("tx", "pmd_control", PolarH10Protocol.stopEcgCommand)
        beginAtomicRequestQueue()
            .add(
                writeCharacteristic(
                    control,
                    PolarH10Protocol.stopAccCommand,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ),
            )
            .add(
                writeCharacteristic(
                    control,
                    PolarH10Protocol.stopEcgCommand,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ),
            )
            .done { disconnectNow() }
            .fail { _, _ -> disconnectNow() }
            .enqueue()
    }

    override fun onServicesInvalidated() {
        controlCharacteristic = null
        dataCharacteristic = null
        heartRateCharacteristic = null
        pendingStartMeasurement = null
        streamsStarted = false
    }

    override fun onDeviceConnecting(device: BluetoothDevice) = Unit

    override fun onDeviceConnected(device: BluetoothDevice) = Unit

    override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
        onFatalError("Failed to connect to $deviceName ($reason)")
        close()
    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit

    override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
        onDisconnected("Disconnected from $deviceName ($reason)")
        close()
    }

    private fun handleControlResponse(payload: ByteArray) {
        val measurementType = pendingStartMeasurement ?: return
        if (!PolarH10Protocol.isStartResponse(payload, measurementType)) return
        if (!PolarH10Protocol.isSuccessfulStartResponse(payload, measurementType)) {
            failAndDisconnect(
                "H10 rejected ${if (measurementType == POLAR_ECG_MEASUREMENT_TYPE) "ECG" else "ACC"} stream: ${payload.toHex()}",
            )
            return
        }

        when (measurementType) {
            POLAR_ECG_MEASUREMENT_TYPE -> writeStartCommand(
                measurementType = POLAR_ACC_MEASUREMENT_TYPE,
                label = "start_acc",
                command = PolarH10Protocol.startAccCommand,
            )

            POLAR_ACC_MEASUREMENT_TYPE -> {
                pendingStartMeasurement = null
                streamsStarted = true
                onRecording()
            }
        }
    }

    private fun writeStartCommand(
        measurementType: Int,
        label: String,
        command: ByteArray,
    ) {
        val control = controlCharacteristic
        if (control == null) {
            failAndDisconnect("Polar PMD control point disappeared before $label")
            return
        }
        pendingStartMeasurement = measurementType
        emitPacket("tx", "pmd_control", command)
        writeCharacteristic(control, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            .fail { _, status -> failAndDisconnect("Polar $label write failed ($status)") }
            .enqueue()
    }

    private fun failAndDisconnect(message: String) {
        onFatalError(message)
        disconnectNow()
    }

    private fun disconnectNow() {
        if (isConnected) {
            disconnect().enqueue()
        } else {
            close()
        }
    }

    private fun enableUpdates(characteristic: BluetoothGattCharacteristic): WriteRequest =
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            enableNotifications(characteristic)
        } else {
            enableIndications(characteristic)
        }

    private fun BluetoothGattCharacteristic.canWrite(): Boolean =
        properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
            properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0

    private fun BluetoothGattCharacteristic.canUpdate(): Boolean =
        properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
            properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

    private fun emitPacket(direction: String, characteristic: String, payload: ByteArray) {
        val (wallMs, elapsedNs) = packetTimes()
        onPacket(packet(direction, characteristic, payload, wallMs, elapsedNs))
    }

    private fun packet(
        direction: String,
        characteristic: String,
        payload: ByteArray,
        wallMs: Long,
        elapsedNs: Long,
    ) = PolarBlePacket(
        arrivalWallTimeMs = wallMs,
        arrivalElapsedNs = elapsedNs,
        direction = direction,
        characteristic = characteristic,
        payloadHex = payload.toHex(),
    )

    private fun packetTimes(): Pair<Long, Long> =
        System.currentTimeMillis() to SystemClock.elapsedRealtimeNanos()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}
