package com.wearify.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wearify.app.data.AvatarAppearance
import com.wearify.app.data.DataCollectionActivity
import com.wearify.app.data.LiveDisplayMetric
import com.wearify.app.data.ScannedBleDevice
import com.wearify.app.data.WearifyUiState

private const val ROUTE_HOME = "home"
private const val ROUTE_LIVE = "live"
private const val ROUTE_ASK = "ask"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun WearifyApp(
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
    startOnAsk: Boolean = false,
) {
    val navController = rememberNavController()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = if (startOnAsk) ROUTE_ASK else ROUTE_HOME,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    uiState = uiState,
                    onOpenSettings = { navController.navigateTopLevel(ROUTE_SETTINGS) },
                    onOpenChat = { navController.navigateTopLevel(ROUTE_ASK) },
                )
            }
            composable(ROUTE_LIVE) {
                LiveScreen(uiState = uiState)
            }
            composable(ROUTE_ASK) {
                ChatScreen(
                    messages = messages,
                    onSend = { messages.add(ChatMessage(it, fromUser = true)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    uiState = uiState,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = onRequestPermissions,
                    onStartAutoConnect = onStartAutoConnect,
                    onStartScan = onStartScan,
                    onStopScan = onStopScan,
                    onStartDemo = onStartDemo,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onTargetDeviceNameChange = onTargetDeviceNameChange,
                    onAvatarAppearanceChange = onAvatarAppearanceChange,
                    onDataCollectionActivityChange = onDataCollectionActivityChange,
                    onLiveDisplayMetricToggle = onLiveDisplayMetricToggle,
                    onResetSettings = onResetSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (!isKeyboardVisible) {
            WearifyBottomNavigation(
                navController = navController,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f),
            )
        }
    }
}

@Composable
private fun WearifyBottomNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val destinations = listOf(
        WearifyDestination(ROUTE_HOME, Icons.Default.Home, "Home"),
        WearifyDestination(ROUTE_LIVE, Icons.AutoMirrored.Filled.ShowChart, "Data"),
        WearifyDestination(ROUTE_ASK, Icons.Default.SmartToy, "Ask"),
        WearifyDestination(ROUTE_SETTINGS, Icons.Default.Settings, "Settings"),
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .offset(y = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(start = 20.dp, top = 8.dp, end = 20.dp)
                .fillMaxWidth()
                .height(68.dp),
            color = WearifySurface.copy(alpha = 0.94f),
            contentColor = WearifyMuted,
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(1.dp, WearifySeparator.copy(alpha = 0.42f)),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    WearifyNavigationIcon(
                        destination = destination,
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigateTopLevel(destination.route)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WearifyNavigationIcon(
    destination: WearifyDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val iconColor = if (selected) WearifyBlue else WearifyMuted
    val iconBackground = if (selected) WearifySoftBlue else Color.Transparent

    IconButton(onClick = onClick, modifier = Modifier.size(width = 64.dp, height = 56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.contentDescription,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
            }
            androidx.compose.material3.Text(
                text = destination.contentDescription,
                style = MaterialTheme.typography.labelSmall,
                color = iconColor,
            )
        }
    }
}

private data class WearifyDestination(
    val route: String,
    val icon: ImageVector,
    val contentDescription: String,
)

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
