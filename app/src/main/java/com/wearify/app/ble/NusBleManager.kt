package com.wearify.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.wearify.app.data.BleConnectionState
import java.util.UUID
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver

private val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
private val NUS_TX_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

@SuppressLint("MissingPermission")
class NusBleManager(
    context: Context,
    private val deviceName: String,
    private val onStateChanged: (BleConnectionState) -> Unit,
    private val onBytesReceived: (ByteArray) -> Unit,
) : BleManager(context), ConnectionObserver {

    private var txCharacteristic: BluetoothGattCharacteristic? = null

    init {
        setConnectionObserver(this)
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val nusService = gatt.getService(NUS_SERVICE_UUID) ?: return false
        txCharacteristic = nusService.getCharacteristic(NUS_TX_UUID)
        val properties = txCharacteristic?.properties ?: return false
        return properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    }

    override fun initialize() {
        txCharacteristic?.let { characteristic ->
            setNotificationCallback(characteristic).with { _, data ->
                data.value?.let(onBytesReceived)
            }
            requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH).enqueue()
            requestMtu(247).enqueue()
            enableNotifications(characteristic).enqueue()
        }
    }

    override fun onServicesInvalidated() {
        txCharacteristic = null
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        onStateChanged(BleConnectionState.Connecting(deviceName))
    }

    override fun onDeviceConnected(device: BluetoothDevice) = Unit

    override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
        onStateChanged(BleConnectionState.Error("Failed to connect to $deviceName ($reason)"))
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        onStateChanged(BleConnectionState.Connected(deviceName))
    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit

    override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
        onStateChanged(BleConnectionState.Disconnected("Disconnected from $deviceName ($reason)"))
    }
}
