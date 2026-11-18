package com.wearify.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wearify.app.data.BleScanDebugStatus
import com.wearify.app.data.BleConnectionState
import com.wearify.app.data.PairingMode
import com.wearify.app.data.ScannedBleDevice
import com.wearify.app.data.WearifyUiState

@Composable
fun ScanScreen(
    uiState: WearifyUiState,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onStartAutoConnect: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onStartDemo: () -> Unit,
    onConnect: (ScannedBleDevice) -> Unit,
    targetDeviceName: String,
    onOpenLive: () -> Unit,
) {
    val isScanning = uiState.connectionState is BleConnectionState.Scanning
    val isManualPairing = uiState.pairingMode == PairingMode.Manual
    val isManualScanning = isManualPairing && isScanning

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OpeningPanel(
                connectionState = uiState.connectionState,
                malformedLineCount = uiState.malformedLineCount,
            )

            if (!permissionsGranted) {
                PermissionPanel(onRequestPermissions = onRequestPermissions)
            }

            ScanActions(
                permissionsGranted = permissionsGranted,
                isManualScanning = isManualScanning,
                hasLiveSession = uiState.hasLiveSession,
                onStartAutoConnect = onStartAutoConnect,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
                onStartDemo = onStartDemo,
                onOpenLive = onOpenLive,
            )

            if (isManualPairing) {
                PairingHeader(deviceCount = uiState.devices.size)
                ScanDebugPanel(scanDebugStatus = uiState.scanDebugStatus)

                if (uiState.devices.isEmpty()) {
                    EmptyDeviceList(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isScanning = isManualScanning,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.devices, key = { it.bluetoothDevice }) { device ->
                            DeviceRow(
                                device = device,
                                enabled = permissionsGranted,
                                targetDeviceName = targetDeviceName,
                                onConnect = { onConnect(device) },
                            )
                        }
                    }
                }
            } else {
                TargetPairingPanel(
                    connectionState = uiState.connectionState,
                    targetDeviceName = targetDeviceName,
                )
            }
        }
    }
}

@Composable
private fun OpeningPanel(
    connectionState: BleConnectionState,
    malformedLineCount: Int,
) {
    val status = connectionStatusCopy(connectionState)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WearifyGlass,
        contentColor = WearifyInk,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, WearifySeparator.copy(alpha = 0.60f)),
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Smart sensing",
                        style = MaterialTheme.typography.displaySmall,
                        color = WearifyInk,
                    )
                    Text(
                        text = "Connect a garment sensor",
                        color = WearifyMuted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(status.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (connectionState is BleConnectionState.Scanning) {
                            Icons.AutoMirrored.Filled.BluetoothSearching
                        } else {
                            Icons.Default.Bluetooth
                        },
                        contentDescription = null,
                        tint = status.color,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                contentColor = WearifyInk,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, status.color.copy(alpha = 0.16f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = status.color)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = status.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = status.detail,
                            color = WearifyMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = if (malformedLineCount > 0) "Bad $malformedLineCount" else "Clean",
                        color = WearifySecondaryText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingHeader(deviceCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Bluetooth Pairing",
                color = WearifyInk,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Nearby devices",
                color = WearifyMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = WearifyMuted,
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(1.dp, WearifySeparator.copy(alpha = 0.70f)),
        ) {
            Text(
                text = "$deviceCount",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ScanActions(
    permissionsGranted: Boolean,
    isManualScanning: Boolean,
    hasLiveSession: Boolean,
    onStartAutoConnect: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onStartDemo: () -> Unit,
    onOpenLive: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                modifier = Modifier
                    .weight(1.25f)
                    .height(44.dp),
                enabled = permissionsGranted,
                onClick = onStartAutoConnect,
                shape = RoundedCornerShape(50.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppleBlue,
                    contentColor = Color.White,
                    disabledContainerColor = WearifySeparator.copy(alpha = 0.50f),
                    disabledContentColor = WearifyMuted,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Auto Connect",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                enabled = permissionsGranted,
                onClick = if (isManualScanning) onStopScan else onStartScan,
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, WearifySeparator),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WearifyInk),
            ) {
                Icon(
                    imageVector = if (isManualScanning) Icons.Default.Stop else Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isManualScanning) "Stop" else "Manual",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedButton(
                modifier = Modifier
                    .weight(0.76f)
                    .height(44.dp),
                onClick = onStartDemo,
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, WearifySeparator),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WearifyInk),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Demo",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (hasLiveSession) {
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenLive,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Data")
            }
        }
    }
}

@Composable
private fun TargetPairingPanel(
    connectionState: BleConnectionState,
    targetDeviceName: String,
) {
    val status = connectionStatusCopy(connectionState)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WearifyGlass,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, WearifySeparator.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(color = status.color) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = status.color)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Bluetooth Pairing",
                    color = WearifyInk,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = targetDeviceName,
                    color = WearifyInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = status.title,
                    color = WearifyMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ScanDebugPanel(
    scanDebugStatus: BleScanDebugStatus,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WearifyGlass,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, WearifySeparator.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Scan debug",
                color = WearifyInk,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "starts ${scanDebugStatus.scanStarts} · callbacks ${scanDebugStatus.callbackTotal} · raw ${scanDebugStatus.rawCallbacks} · batch ${scanDebugStatus.batchCallbacks}",
                color = WearifyMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "BT ${yesNo(scanDebugStatus.isBluetoothEnabled)} · scan ${yesNo(scanDebugStatus.hasScanPermission)} · connect ${yesNo(scanDebugStatus.hasConnectPermission)} · fine loc ${yesNo(scanDebugStatus.hasFineLocationPermission)} · loc on ${yesNo(scanDebugStatus.isLocationEnabled)}",
                color = WearifyMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val lastSeen = scanDebugStatus.lastRssi?.let { rssi ->
                "${scanDebugStatus.lastName ?: "Unnamed"} · $rssi dBm"
            } ?: "No callback yet"
            Text(
                text = "last $lastSeen",
                color = WearifyMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            scanDebugStatus.lastError?.let { error ->
                Text(
                    text = "error $error",
                    color = AppleRed,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PermissionPanel(onRequestPermissions: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WearifyGlass,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, AppleBlue.copy(alpha = 0.18f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(color = AppleBlue) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AppleBlue)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bluetooth Access",
                    color = WearifyInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Required for pairing.",
                    color = WearifyMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onRequestPermissions,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleBlue),
            ) {
                Text("Allow")
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: ScannedBleDevice,
    enabled: Boolean,
    targetDeviceName: String,
    onConnect: () -> Unit,
) {
    val isTargetDevice = device.matchesTargetName(targetDeviceName)
    val accent = if (isTargetDevice) AppleBlue else WearifyMuted
    val background = if (isTargetDevice) WearifySoftBlue.copy(alpha = 0.82f) else WearifyGlass
    val borderColor = if (isTargetDevice) AppleBlue.copy(alpha = 0.24f) else WearifySeparator.copy(alpha = 0.70f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = background,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isTargetDevice) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(color = accent) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = accent)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = device.displayName,
                    color = WearifyInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isTargetDevice) {
                        PairingBadge(text = "Target", accent = AppleBlue)
                    } else if (device.hasXiaoName) {
                        PairingBadge(text = "xiao", accent = AppleBlue)
                    }
                    PairingBadge(text = "${device.rssi} dBm", accent = accent)
                }
                LinearProgressIndicator(
                    progress = { rssiStrength(device.rssi) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50.dp)),
                    color = accent,
                    trackColor = Color.Transparent,
                )
            }
            TextButton(
                onClick = onConnect,
                enabled = enabled,
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun EmptyDeviceList(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
) {
    Surface(
        modifier = modifier,
        color = WearifyGlass,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, WearifySeparator.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AppleBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = AppleBlue,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (isScanning) "Scanning nearby" else "No devices yet",
                color = WearifyInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isScanning) "Showing all BLE advertisements" else "Ready to pair",
                color = WearifyMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PairingBadge(
    text: String,
    accent: Color,
) {
    Surface(
        color = accent.copy(alpha = 0.10f),
        contentColor = accent,
        shape = RoundedCornerShape(50.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusIcon(
    color: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun connectionStatusCopy(connectionState: BleConnectionState): StatusCopy =
    when (connectionState) {
        BleConnectionState.Idle -> StatusCopy("Ready", "No active connection", WearifyMuted)
        BleConnectionState.Scanning -> StatusCopy("Scanning", "Looking for BLE advertisements", AppleBlue)
        is BleConnectionState.Connecting -> StatusCopy("Connecting", connectionState.deviceName, AppleOrange)
        is BleConnectionState.Connected -> StatusCopy("Connected", connectionState.deviceName, AppleGreen)
        is BleConnectionState.Disconnected -> StatusCopy("Disconnected", connectionState.reason ?: "Session paused", WearifyMuted)
        is BleConnectionState.Error -> StatusCopy("Needs attention", connectionState.message, AppleRed)
    }

private fun rssiStrength(rssi: Int): Float =
    ((rssi + 100).toFloat() / 60f).coerceIn(0.06f, 1f)

private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

private data class StatusCopy(
    val title: String,
    val detail: String,
    val color: Color,
)
