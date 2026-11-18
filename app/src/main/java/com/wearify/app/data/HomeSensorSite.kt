package com.wearify.app.data

data class HomeBodyContext(
    val imu: String = "collecting",
    val contactMic: String = "collecting",
    val ppg: String = "collecting",
)

data class HomeSensorSite(
    val id: String,
    val placement: String,
    val sensorLabel: String,
    val status: String = "collecting",
    val summary: String,
)
