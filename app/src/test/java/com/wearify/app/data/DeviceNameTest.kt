package com.wearify.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNameTest {
    @Test
    fun hidesHardwareAddressesEmbeddedInDeviceNames() {
        for (separator in listOf(":", "-")) {
            val address = List(6) { "aB" }.joinToString(separator)
            assertNull(publicDeviceName("Wearable $address"))
        }
        assertEquals("Demo Sensor", publicDeviceName("Demo Sensor"))
    }
}
