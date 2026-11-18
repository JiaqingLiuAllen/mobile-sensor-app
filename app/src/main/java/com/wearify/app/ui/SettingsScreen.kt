package com.wearify.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.wearify.app.data.DEFAULT_DEVICE_NAME
import com.wearify.app.data.AvatarAppearance
import com.wearify.app.data.AvatarGenderPresentation
import com.wearify.app.data.AvatarHairColor
import com.wearify.app.data.BleConnectionState
import com.wearify.app.data.DataCollectionActivity
import com.wearify.app.data.LiveDisplayMetric
import com.wearify.app.data.ScannedBleDevice
import com.wearify.app.data.WearifyUiState
import kotlinx.coroutines.delay

private val SettingsBottomClearance = 116.dp

@Composable
fun SettingsScreen(
    uiState: WearifyUiState,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onStartAutoConnect: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onStartDemo: () -> Unit,
    onConnect: (ScannedBleDevice) -> Unit,
    onDisconnect: () -> Unit,
    onTargetDeviceNameChange: (String) -> Unit,
    onAvatarAppearanceChange: (AvatarAppearance) -> Unit,
    onDataCollectionActivityChange: (DataCollectionActivity) -> Unit,
    onLiveDisplayMetricToggle: (LiveDisplayMetric, Boolean) -> Unit,
    onResetSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var acknowledgementOpen by remember { mutableStateOf(false) }
    var targetDraft by rememberSaveable { mutableStateOf(uiState.targetDeviceName) }
    var targetSaved by remember { mutableStateOf(false) }
    var manualDevicesOpen by rememberSaveable { mutableStateOf(false) }
    var dataOpen by rememberSaveable { mutableStateOf(false) }
    var personalizationOpen by rememberSaveable { mutableStateOf(false) }
    var visualizationOpen by rememberSaveable { mutableStateOf(false) }
    val normalizedTargetDraft = targetDraft.trim().ifBlank { DEFAULT_DEVICE_NAME }
    val targetCanCommit = normalizedTargetDraft != uiState.resolvedTargetDeviceName

    LaunchedEffect(uiState.targetDeviceName) {
        targetDraft = uiState.targetDeviceName
    }
    LaunchedEffect(targetSaved) {
        if (targetSaved) {
            delay(2_200L)
            targetSaved = false
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    top = 18.dp,
                    end = 20.dp,
                    bottom = SettingsBottomClearance,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsHeader()

            GarmentSettingsSection(
                uiState = uiState,
                permissionsGranted = permissionsGranted,
                manualDevicesOpen = manualDevicesOpen,
                targetDraft = targetDraft,
                targetCanCommit = targetCanCommit,
                targetSaved = targetSaved,
                onTargetDraftChange = { targetDraft = it; targetSaved = false },
                onSaveTarget = {
                    onTargetDeviceNameChange(normalizedTargetDraft)
                    targetSaved = true
                },
                onRequestPermissions = onRequestPermissions,
                onStartAutoConnect = onStartAutoConnect,
                onDisconnect = onDisconnect,
                onToggleManual = {
                    manualDevicesOpen = !manualDevicesOpen
                    if (manualDevicesOpen && permissionsGranted) onStartScan() else onStopScan()
                },
                onRefresh = onStartScan,
                onConnect = onConnect,
                onStartDemo = onStartDemo,
            )

            CollapsibleSettingsSection(
                title = "Data",
                subtitle = "Recording activity",
                icon = Icons.Default.Storage,
                accent = WearifyBlue,
                expanded = dataOpen,
                onToggle = { dataOpen = !dataOpen },
            ) {
                SettingsSubsection(title = "Data collection") {
                    DataCollectionSettingsContent(
                        activity = uiState.dataCollectionActivity,
                        isRecording = uiState.dataLogStatus.isRecording,
                        onActivityChange = onDataCollectionActivityChange,
                    )
                }
            }

            CollapsibleSettingsSection(
                title = "Personalization",
                subtitle = "Choose your guide",
                icon = Icons.Default.Person,
                accent = WearifyViolet,
                expanded = personalizationOpen,
                onToggle = { personalizationOpen = !personalizationOpen },
            ) {
                GuideAppearanceSettingsContent(
                    appearance = uiState.avatarAppearance,
                    onAppearanceChange = onAvatarAppearanceChange,
                )
            }

            CollapsibleSettingsSection(
                title = "Visualization",
                subtitle = "Choose visible data cards",
                icon = Icons.Default.Visibility,
                accent = WearifyCyan,
                expanded = visualizationOpen,
                onToggle = { visualizationOpen = !visualizationOpen },
            ) {
                Column {
                    LiveDisplayMetric.values().forEachIndexed { index, metric ->
                        LiveMetricToggleRow(
                            metric = metric,
                            checked = metric in uiState.enabledLiveMetrics,
                            onCheckedChange = { checked ->
                                onLiveDisplayMetricToggle(metric, checked)
                            },
                        )
                        if (index != LiveDisplayMetric.values().lastIndex) {
                            SettingsDivider()
                        }
                    }
                }
            }

            SettingsSection(title = "Preference") {
                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    title = "Reset Defaults",
                    detail = "Restore collection labels, device, and Data cards",
                    accent = AppleBlue,
                    onClick = onResetSettings,
                )
            }

            SettingsSection(title = "About") {
                SettingsActionRow(
                    icon = Icons.Default.Info,
                    title = "Acknowledgement",
                    detail = "Credits and affiliation",
                    accent = ApplePurple,
                    onClick = { acknowledgementOpen = true },
                )
            }
        }
    }

    if (acknowledgementOpen) {
        AlertDialog(
            onDismissRequest = { acknowledgementOpen = false },
            title = {
                Text(
                    text = "Acknowledgement",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Multimodal garment sensing",
                        color = WearifyMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider(color = WearifySeparator.copy(alpha = 0.70f))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Smart Clothes Research Team",
                            color = WearifyInk,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "University of Notre Dame",
                            color = WearifyMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "Acknowledged for contribution and institutional affiliation.",
                        color = WearifySecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { acknowledgementOpen = false }) {
                    Text("Done")
                }
            },
            containerColor = WearifySurface,
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun GuideAppearanceSettingsContent(
    appearance: AvatarAppearance,
    onAppearanceChange: (AvatarAppearance) -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.width(96.dp).height(130.dp),
                    color = WearifySoftBlue,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, WearifyBlue.copy(alpha = .14f)),
                ) {
                    CustomizableGarmentAvatar(
                        appearance = appearance,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Current guide appearance",
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "Your on-screen guide",
                        style = MaterialTheme.typography.titleMedium,
                        color = WearifyInk,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Pick one fixed character, then adjust skin and hair color. Your data profile stays separate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WearifyMuted,
                    )
                }
            }

            AppearanceLabel("Gender presentation")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarGenderPresentation.values().forEach { option ->
                    AppearanceChoiceChip(
                        label = option.label,
                        selected = appearance.genderPresentation == option,
                        onClick = {
                            onAppearanceChange(appearance.copy(genderPresentation = option))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AppearanceLabel("Skin tone")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarSkinToneColors.forEachIndexed { index, color ->
                    val selected = appearance.skinToneIndex == index
                    Surface(
                        modifier = Modifier
                            .size(if (selected) 44.dp else 38.dp)
                            .semantics { contentDescription = "Skin tone ${index + 1}" },
                        shape = CircleShape,
                        color = color,
                        border = BorderStroke(
                            if (selected) 3.dp else 1.dp,
                            if (selected) WearifyBlue else WearifyLine,
                        ),
                        onClick = {
                            onAppearanceChange(appearance.copy(skinToneIndex = index))
                        },
                    ) {}
                }
            }

            Text(
                "Each gender has a fixed hairstyle.",
                color = WearifyMuted,
                style = MaterialTheme.typography.bodySmall,
            )

            AppearanceLabel("Hair color")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AvatarHairColor.values().forEach { option ->
                    val selected = appearance.hairColor == option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .semantics { contentDescription = "${option.label} hair" },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(option.argb.toInt()),
                        border = BorderStroke(
                            if (selected) 3.dp else 1.dp,
                            if (selected) WearifyBlue else WearifyLine,
                        ),
                        onClick = {
                            onAppearanceChange(appearance.copy(hairColor = option))
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun AppearanceLabel(text: String) {
    Text(
        text = text,
        color = WearifySecondaryText,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun AppearanceChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = if (selected) WearifySoftBlue else WearifySurface,
        contentColor = if (selected) WearifyBlue else WearifyInk,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) WearifyBlue else WearifyLine),
        onClick = onClick,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun DataCollectionSettingsContent(
    activity: DataCollectionActivity,
    isRecording: Boolean,
    onActivityChange: (DataCollectionActivity) -> Unit,
) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Label new recordings by activity.", color = WearifyMuted, style = MaterialTheme.typography.bodySmall)
        CollectionLabelDropdown(
            label = "Activity / posture",
            selected = activity,
            options = DataCollectionActivity.values().toList(),
            optionLabel = DataCollectionActivity::menuLabel,
            enabled = !isRecording,
            onSelect = onActivityChange,
        )
    }
}

@Composable
private fun <T> CollectionLabelDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = label,
            color = WearifySecondaryText,
            style = MaterialTheme.typography.labelLarge,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = true },
                color = if (enabled) WearifySurface else WearifyCanvas,
                contentColor = WearifyInk,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (expanded) WearifyBlue else WearifyLine,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = optionLabel(selected),
                        color = if (enabled) WearifyInk else WearifyMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = WearifyDataFont,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Choose $label",
                        tint = if (enabled) WearifyBlue else WearifySage,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(WearifySurface),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel(option),
                                color = WearifyInk,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = WearifyDataFont,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                        trailingIcon = {
                            if (option == selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = WearifyBlue,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GarmentSettingsSection(
    uiState: WearifyUiState,
    permissionsGranted: Boolean,
    manualDevicesOpen: Boolean,
    targetDraft: String,
    targetCanCommit: Boolean,
    targetSaved: Boolean,
    onTargetDraftChange: (String) -> Unit,
    onSaveTarget: () -> Unit,
    onRequestPermissions: () -> Unit,
    onStartAutoConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleManual: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (ScannedBleDevice) -> Unit,
    onStartDemo: () -> Unit,
) {
    val connected = uiState.connectionState is BleConnectionState.Connected
    val connecting = uiState.connectionState is BleConnectionState.Connecting ||
        (uiState.connectionState is BleConnectionState.Scanning && !manualDevicesOpen)
    SettingsSection(title = "Bluetooth connection") {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsIconCircle(icon = Icons.Default.Bluetooth, accent = WearifyPine, size = 42.dp, iconSize = 22.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = when (val state = uiState.connectionState) {
                            is BleConnectionState.Connected -> state.deviceName
                            is BleConnectionState.Connecting -> state.deviceName
                            else -> uiState.resolvedTargetDeviceName
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = WearifyInk,
                    )
                    Text(
                        text = when {
                            connected -> "Connected and recording"
                            connecting -> "Looking for your garment…"
                            else -> "Ready to connect"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = WearifyMuted,
                    )
                }
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(if (connected) WearifyPine else WearifySage),
                )
            }
            if (!permissionsGranted) {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WearifyPine),
                    shape = RoundedCornerShape(50.dp),
                ) { Text("Allow Bluetooth access") }
            } else {
                Button(
                    onClick = if (connected) onDisconnect else onStartAutoConnect,
                    enabled = !connecting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (connected) WearifySoftPink else WearifyPine),
                    shape = RoundedCornerShape(50.dp),
                ) {
                    Icon(if (connected) Icons.Default.LinkOff else Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            connected -> "Disconnect"
                            connecting -> "Connecting…"
                            else -> "Connect garment"
                        },
                        color = if (connected) WearifyCoral else Color.White,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleManual).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Choose another device", style = MaterialTheme.typography.titleMedium, color = WearifyInk)
                Icon(
                    if (manualDevicesOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (manualDevicesOpen) "Hide nearby devices" else "Show nearby devices",
                    tint = WearifyPine,
                )
            }
            if (manualDevicesOpen) {
                OutlinedTextField(
                    value = targetDraft,
                    onValueChange = onTargetDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Preferred device name") },
                    trailingIcon = {
                        IconButton(enabled = targetCanCommit, onClick = onSaveTarget) {
                            Icon(Icons.Default.Check, contentDescription = "Save preferred device", tint = if (targetCanCommit) WearifyPine else WearifySage)
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WearifyPine,
                        unfocusedBorderColor = WearifyLine,
                        focusedContainerColor = WearifySurface,
                        unfocusedContainerColor = WearifySurface,
                    ),
                )
                if (targetSaved) {
                    Text("Preferred device updated", style = MaterialTheme.typography.bodySmall, color = WearifyPine)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Nearby devices", style = MaterialTheme.typography.labelLarge, color = WearifyMuted)
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
                if (uiState.devices.isEmpty()) {
                    Text("No nearby devices found yet.", style = MaterialTheme.typography.bodyMedium, color = WearifyMuted)
                } else {
                    uiState.devices.forEach { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onConnect(device) }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = WearifyPine)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.displayName, style = MaterialTheme.typography.bodyLarge, color = WearifyInk)
                                Text("${device.rssi} dBm", style = MaterialTheme.typography.labelSmall, color = WearifyMuted, fontFamily = WearifyDataFont)
                            }
                            Text("Connect", style = MaterialTheme.typography.labelLarge, color = WearifyPine)
                        }
                    }
                }
                TextButton(onClick = onStartDemo, modifier = Modifier.align(Alignment.End)) { Text("Use demo session") }
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(WearifySoftGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = WearifyPine,
                modifier = Modifier.size(29.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Settings",
                color = WearifyInk,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Connections, data, and display",
                color = WearifyMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun CollapsibleSettingsSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WearifyGlass,
        contentColor = WearifyInk,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            if (expanded) accent.copy(alpha = 0.28f) else WearifySeparator.copy(alpha = 0.60f),
        ),
        tonalElevation = 1.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsIconCircle(icon = icon, accent = accent, size = 40.dp, iconSize = 21.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        color = WearifyInk,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        color = WearifyMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = accent,
                )
            }
            if (expanded) {
                HorizontalDivider(color = WearifySeparator.copy(alpha = 0.65f))
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsSubsection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp),
            color = WearifySecondaryText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WearifyCanvas,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, WearifyLine.copy(alpha = 0.78f)),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = WearifySecondaryText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WearifyGlass,
            contentColor = WearifyInk,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, WearifySeparator.copy(alpha = 0.60f)),
            tonalElevation = 1.dp,
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun LiveMetricToggleRow(
    metric: LiveDisplayMetric,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val featured = metric == LiveDisplayMetric.HeartRate
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconCircle(
            icon = metric.icon,
            accent = metric.accent,
            size = 34.dp,
            iconSize = 19.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = metric.title,
                color = WearifyInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (featured) {
                Text(
                    text = metric.detail,
                    color = WearifyMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppleGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = WearifySeparator,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconCircle(icon = icon, accent = accent)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = WearifyInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                color = WearifyMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsIconCircle(
    icon: ImageVector,
    accent: Color,
    size: androidx.compose.ui.unit.Dp = 34.dp,
    iconSize: androidx.compose.ui.unit.Dp = 19.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun SettingsDivider() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(62.dp))
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
            color = WearifySeparator.copy(alpha = 0.55f),
        )
    }
}

private val LiveDisplayMetric.icon: ImageVector
    get() = when (this) {
        LiveDisplayMetric.HeartRate -> Icons.Default.Favorite
        LiveDisplayMetric.ContactMic -> Icons.Default.GraphicEq
        LiveDisplayMetric.Temperature -> Icons.Default.Thermostat
        LiveDisplayMetric.Motion -> Icons.AutoMirrored.Filled.DirectionsWalk
        LiveDisplayMetric.Uv -> Icons.Default.WbSunny
        LiveDisplayMetric.AmbientLight -> Icons.Default.Home
    }

private val LiveDisplayMetric.accent: Color
    get() = when (this) {
        LiveDisplayMetric.HeartRate -> ApplePink
        LiveDisplayMetric.ContactMic -> AppleTeal
        LiveDisplayMetric.Temperature -> AppleOrange
        LiveDisplayMetric.Motion -> AppleBlue
        LiveDisplayMetric.Uv -> AppleYellow
        LiveDisplayMetric.AmbientLight -> AppleGreen
    }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsPreview() {
    WearifyTheme {
        SettingsScreen(
            uiState = WearifyUiState(),
            permissionsGranted = true,
            onRequestPermissions = {}, onStartAutoConnect = {}, onStartScan = {}, onStopScan = {},
            onStartDemo = {}, onConnect = {}, onDisconnect = {}, onTargetDeviceNameChange = {},
            onAvatarAppearanceChange = {},
            onDataCollectionActivityChange = {},
            onLiveDisplayMetricToggle = { _, _ -> }, onResetSettings = {},
        )
    }
}
