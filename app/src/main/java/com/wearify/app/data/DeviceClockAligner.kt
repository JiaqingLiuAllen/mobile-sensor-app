package com.wearify.app.data

/**
 * Maps XIAO uptime onto the phone's wall-clock-like timeline for live plotting
 * and CSV rows. The first BLE frame with a device uptime establishes the
 * session offset; subsequent sensor modalities share that same offset.
 */
class DeviceClockAligner {
    private var hostWallAnchorUs: Long? = null
    private var hostElapsedAnchorUs: Long? = null
    private var deviceToHostOffsetUs: Long? = null
    private var lastDeviceUptimeUs: Long? = null

    fun clear() {
        hostWallAnchorUs = null
        hostElapsedAnchorUs = null
        deviceToHostOffsetUs = null
        lastDeviceUptimeUs = null
    }

    fun alignMillis(
        deviceUptimeUs: Long?,
        receivedAtWallMs: Long,
        receivedAtElapsedUs: Long,
    ): Long {
        val hostUs = monotonicHostUs(receivedAtWallMs, receivedAtElapsedUs)
        val deviceUs = deviceUptimeUs ?: return roundedMillis(hostUs)
        val candidateOffsetUs = hostUs - deviceUs
        val currentOffsetUs = deviceToHostOffsetUs
        val previousDeviceUs = lastDeviceUptimeUs

        if (
            currentOffsetUs == null ||
            (previousDeviceUs != null && deviceUs + DEVICE_REBOOT_BACKSTEP_US < previousDeviceUs)
        ) {
            deviceToHostOffsetUs = candidateOffsetUs
        }

        lastDeviceUptimeUs = deviceUs
        return roundedMillis(deviceUs + requireNotNull(deviceToHostOffsetUs))
    }

    private fun monotonicHostUs(
        receivedAtWallMs: Long,
        receivedAtElapsedUs: Long,
    ): Long {
        val anchorWallUs = hostWallAnchorUs
        val anchorElapsedUs = hostElapsedAnchorUs
        if (anchorWallUs == null || anchorElapsedUs == null) {
            val wallUs = receivedAtWallMs * 1_000L
            hostWallAnchorUs = wallUs
            hostElapsedAnchorUs = receivedAtElapsedUs
            return wallUs
        }
        return anchorWallUs + (receivedAtElapsedUs - anchorElapsedUs)
    }

    private fun roundedMillis(us: Long): Long =
        if (us >= 0L) {
            (us + 500L) / 1_000L
        } else {
            (us - 500L) / 1_000L
        }

    private companion object {
        private const val DEVICE_REBOOT_BACKSTEP_US = 5_000_000L
    }
}

fun ContactMicFrame.deviceEndUptimeUs(): Long? {
    val coarseMs = deviceUptimeMs
    val fineUs = deviceUptimeUs
    if (coarseMs == null) return fineUs
    if (fineUs == null) return coarseMs * 1_000L

    val subMsUs = ((fineUs % 1_000L) + 1_000L) % 1_000L
    return coarseMs * 1_000L + subMsUs
}
