package com.wearify.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wearify.app.data.WearifyUiState

@Composable
fun LiveScreen(
    uiState: WearifyUiState,
) {
    WearifyUpdateDashboard(
        sensorData = uiState.lastSample.toSensorData(),
        samples = uiState.samples,
        contactMicPoints = uiState.contactMicPoints,
        contactMicAnalysis = uiState.contactMicAnalysis,
        ppgAnalysis = uiState.ppgAnalysis,
        dataLogStatus = uiState.dataLogStatus,
        enabledLiveMetrics = uiState.enabledLiveMetrics,
        modifier = Modifier.fillMaxSize(),
    )
}
