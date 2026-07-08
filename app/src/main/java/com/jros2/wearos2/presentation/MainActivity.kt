package com.jros2.wearos2.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jros2.wearos2.SettingsManager
import com.jros2.wearos2.ros.RosBridgeHolder
import com.jros2.wearos2.ros.WearSensorBridge
import com.jros2.wearos2.service.BridgeService
import com.jros2.wearos2.presentation.theme.WeaROS2Theme

/** The screens the app can show. Add a case here plus a branch in [MainActivity] to grow. */
private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Joystick : Screen
    data object Slider : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var bridge: WearSensorBridge
    private lateinit var settings: SettingsManager

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clearJavaCPPCache()
        enableEdgeToEdge()
        requestRuntimePermissions()
        settings = SettingsManager(this)
        // Shared, process-wide bridge. The foreground BridgeService drives its lifecycle so
        // it keeps running while the app is minimized; this Activity just observes/controls it.
        bridge = RosBridgeHolder.get(this)
        setContent {
            WeaROS2Theme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                when (screen) {
                    Screen.Settings -> WearSettings(bridge, settings) {
                        if (bridge.isRunning.value) stopBridge()
                        screen = Screen.Home
                    }
                    Screen.Joystick -> JoystickScreen(bridge.joystick) { screen = Screen.Home }
                    Screen.Slider -> SliderScreen(bridge.slider) { screen = Screen.Home }
                    Screen.Home -> WearHome(
                        bridge = bridge,
                        onRequestPermissions = { requestRuntimePermissions() },
                        onOpenSettings = { openAppSettings() },
                        onSettingsClick = { screen = Screen.Settings },
                        onJoystickClick = {
                            // The joystick publishes on the shared ROS2 node, so make sure
                            // the bridge is running before opening the control.
                            if (!bridge.isRunning.value) startBridge()
                            screen = Screen.Joystick
                        },
                        onSliderClick = {
                            if (!bridge.isRunning.value) startBridge()
                            screen = Screen.Slider
                        },
                        onStartBridge = { startBridge() },
                        onStopBridge = { stopBridge() },
                    )
                }
            }
        }
    }

    /** Start the bridge inside the foreground service so it survives minimizing the app. */
    private fun startBridge() {
        ContextCompat.startForegroundService(this, BridgeService.startIntent(this))
    }

    /** Stop the bridge and tear the foreground service down. */
    private fun stopBridge() {
        startService(BridgeService.stopIntent(this))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    private fun clearJavaCPPCache() {
        try {
            val cacheDir = java.io.File(applicationContext.cacheDir, "javacpp")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
            val filesDir = java.io.File(applicationContext.filesDir, "javacpp")
            if (filesDir.exists()) {
                filesDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun requestRuntimePermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.BODY_SENSORS)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Android 16 (Baklava, API 36) replaced BODY_SENSORS with granular Health
            // permissions, which the Samsung Health Sensor API requires for HR/SpO2.
            if (Build.VERSION.SDK_INT >= 36) {
                add("android.permission.health.READ_HEART_RATE")
                add("android.permission.health.READ_OXYGEN_SATURATION")
            }
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionRequest.launch(missing.toTypedArray())
        }
    }
}
