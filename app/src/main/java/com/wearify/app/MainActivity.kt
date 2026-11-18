package com.wearify.app

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wearify.app.ui.WearifyApp
import com.wearify.app.ui.WearifyTheme
import com.wearify.app.viewmodel.SensorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.configureTransparentSystemBars()
        val isDebuggable =
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        setContent {
            WearifyTheme {
                val context = LocalContext.current
                val viewModel: SensorViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val debugDemo = remember {
                    isDebuggable
                        && intent.getBooleanExtra(EXTRA_DEBUG_DEMO_SESSION, false)
                }
                val debugOpenAsk = remember {
                    isDebuggable
                        && intent.getBooleanExtra(EXTRA_DEBUG_OPEN_ASK, false)
                }
                var permissionsGranted by remember { mutableStateOf(hasBlePermissions(context)) }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    permissionsGranted = hasBlePermissions(context)
                }

                LaunchedEffect(Unit) {
                    permissionsGranted = hasBlePermissions(context)
                    if (debugDemo) viewModel.startDemo()
                }

                WearifyApp(
                    uiState = uiState,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = {
                        launcher.launch(requiredRuntimePermissions())
                    },
                    onStartAutoConnect = viewModel::startAutoConnect,
                    onStartScan = viewModel::startScan,
                    onStopScan = viewModel::stopScan,
                    onStartDemo = viewModel::startDemo,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onTargetDeviceNameChange = viewModel::updateTargetDeviceName,
                    onAvatarAppearanceChange = viewModel::updateAvatarAppearance,
                    onDataCollectionActivityChange = viewModel::selectDataCollectionActivity,
                    onLiveDisplayMetricToggle = viewModel::setLiveDisplayMetricEnabled,
                    onResetSettings = viewModel::resetSettings,
                    startOnAsk = debugOpenAsk,
                )
            }
        }
    }
}

private const val EXTRA_DEBUG_DEMO_SESSION = "wearify_debug_demo_session"
private const val EXTRA_DEBUG_OPEN_ASK = "wearify_debug_open_ask"

@Suppress("DEPRECATION")
private fun Window.configureTransparentSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    statusBarColor = Color.TRANSPARENT
    navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isStatusBarContrastEnforced = false
        isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(this, decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}

fun requiredRuntimePermissions(): Array<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions.toTypedArray()
}

fun hasBlePermissions(context: Context): Boolean {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
