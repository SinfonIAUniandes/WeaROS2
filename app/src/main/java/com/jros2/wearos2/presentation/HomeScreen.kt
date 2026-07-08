package com.jros2.wearos2.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.jros2.wearos2.ros.WearSensorBridge
import com.jros2.wearos2.presentation.theme.WeaROS2Theme

/**
 * The main screen: bridge start/stop, quick actions (settings, joystick, SpO2), a live
 * per-feature list, and a rolling log. Purely a view over [WearSensorBridge] — navigation
 * and permission requests are hoisted to the caller.
 */
@Composable
fun WearHome(
    bridge: WearSensorBridge,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onSettingsClick: () -> Unit,
    onJoystickClick: () -> Unit,
    onSliderClick: () -> Unit,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit,
) {
    val isRunning by bridge.isRunning.collectAsState()
    val logs by bridge.logs.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val context = LocalContext.current
    var permissionsHint by remember { mutableStateOf("") }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsHint = buildString {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        append("GPS permission missing. ")
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        append("Microphone permission missing. ")
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                        append("Body sensors permission missing. ")
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        append("Activity recognition permission missing. ")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        append("Notification permission missing.")
                    }
                }.trim()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ListHeader {
                        Text("WeaROS2", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Card(onClick = onSettingsClick) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("Settings")
                            Text("Namespace, Domain ID, etc.")
                        }
                    }
                }
                item {
                    Card(onClick = { if (isRunning) onStopBridge() else onStartBridge() }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(if (isRunning) "Stop bridge" else "Start bridge")
                            Text(if (isRunning) "Publishing ROS2 (runs in background)" else "Idle")
                        }
                    }
                }
                item {
                    Card(onClick = onJoystickClick) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("Joystick")
                            Text("Full-screen touch control")
                        }
                    }
                }
                item {
                    Card(onClick = onSliderClick) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("Slider")
                            Text("Rotary round value (volume, etc.)")
                        }
                    }
                }
                item {
                    val pressCount by bridge.button.messageCount.collectAsState()
                    Card(onClick = { if (isRunning) bridge.button.press() }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("Publish button")
                            Text(if (isRunning) "Tap to publish · sent $pressCount" else "Start bridge first")
                        }
                    }
                }
                item {
                    val spo2Measuring by bridge.samsung.spo2Measuring.collectAsState()
                    val spo2Result by bridge.samsung.spo2Result.collectAsState()
                    Card(onClick = { if (isRunning && !spo2Measuring) bridge.samsung.measureSpo2() }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("Measure SpO2")
                            Text(spo2Result)
                            Text(
                                when {
                                    !isRunning -> "Start bridge first"
                                    spo2Measuring -> "Measuring…"
                                    else -> "Tap and hold your wrist still"
                                }
                            )
                        }
                    }
                }
                if (permissionsHint.isNotBlank()) {
                    item {
                        Card(onClick = onRequestPermissions) {
                            Text(
                                text = permissionsHint + "\nTap to request permission",
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    item {
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Open OS Settings")
                        }
                    }
                }
                items(bridge.sensors.size) { index ->
                    val sensor = bridge.sensors[index]
                    val enabled by sensor.enabled.collectAsState()
                    val value by sensor.displayValue.collectAsState()
                    val count by sensor.messageCount.collectAsState()
                    SwitchButton(
                        checked = enabled,
                        onCheckedChange = { sensor.enabled.value = it },
                        label = { Text(sensor.name) },
                        secondaryLabel = { Text("$value · $count") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Logs")
                        logs.take(5).forEach { line ->
                            Text(line)
                        }
                    }
                }
                item {
                    Button(onClick = { if (!isRunning) onStartBridge() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Quick start")
                    }
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WeaROS2Theme {
        WearHome(WearSensorBridge(LocalContext.current), {}, {}, {}, {}, {}, {}, {})
    }
}
