package com.jros2.wearos2.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.jros2.wearos2.SettingsManager
import com.jros2.wearos2.ros.WearSensorBridge
import com.jros2.wearos2.presentation.theme.WeaROS2Theme

class MainActivity : ComponentActivity() {
    private lateinit var bridge: WearSensorBridge
    private lateinit var settings: SettingsManager
    private lateinit var multicastLock: WifiManager.MulticastLock

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clearJavaCPPCache()
        enableEdgeToEdge()
        enableRosDiscovery()
        requestRuntimePermissions()
        settings = SettingsManager(this)
        bridge = WearSensorBridge(this)
        setContent {
            WeaROS2Theme {
                var showSettings by remember { mutableStateOf(false) }
                if (showSettings) {
                    WearSettings(bridge, settings) {
                        // On save/back
                        if (bridge.isRunning.value) bridge.stop()
                        showSettings = false
                    }
                } else {
                    WearHome(
                        bridge = bridge,
                        onRequestPermissions = { requestRuntimePermissions() },
                        onOpenSettings = {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", packageName, null)
                            )
                            startActivity(intent)
                        },
                        onSettingsClick = { showSettings = true }
                    )
                }
            }
        }
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

    private fun enableRosDiscovery() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("wear_ros2_multicast_lock")
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()
    }

    private fun requestRuntimePermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.BODY_SENSORS)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionRequest.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bridge.isInitialized) {
            bridge.destroy()
        }
        if (::multicastLock.isInitialized && multicastLock.isHeld) {
            multicastLock.release()
        }
    }
}

@Composable
fun WearHome(bridge: WearSensorBridge, onRequestPermissions: () -> Unit, onOpenSettings: () -> Unit, onSettingsClick: () -> Unit) {
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
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
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
                    Card(onClick = { if (isRunning) bridge.stop() else bridge.start() }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(if (isRunning) "Stop bridge" else "Start bridge")
                            Text(if (isRunning) "Publishing ROS2" else "Idle")
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
                    Button(onClick = { if (!isRunning) bridge.start() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Quick start")
                    }
                }
            }
        }
    }
}

@Composable
fun WearSettings(bridge: WearSensorBridge, settings: SettingsManager, onBack: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    var domainId by remember { mutableStateOf(settings.domainId.toString()) }
    var namespace by remember { mutableStateOf(settings.namespace) }
    val topicNames = remember {
        mutableStateMapOf<String, String>().apply {
            bridge.sensors.forEach { sensor ->
                this[sensor.id] = settings.getTopicName(sensor.id, sensor.topicName)
            }
        }
    }

    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ListHeader {
                        Text("Settings", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text("Domain ID", color = Color.Gray)
                        BasicTextField(
                            value = domainId,
                            onValueChange = { domainId = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth().background(Color.DarkGray, RoundedCornerShape(8.dp)).padding(10.dp)
                        )
                    }
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text("Namespace", color = Color.Gray)
                        BasicTextField(
                            value = namespace,
                            onValueChange = { namespace = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth().background(Color.DarkGray, RoundedCornerShape(8.dp)).padding(10.dp)
                        )
                    }
                }
                item {
                    ListHeader {
                        Text("Topics", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                items(bridge.sensors.size) { index ->
                    val sensor = bridge.sensors[index]
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text(sensor.name, color = Color.Gray)
                        BasicTextField(
                            value = topicNames[sensor.id] ?: "",
                            onValueChange = { topicNames[sensor.id] = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth().background(Color.DarkGray, RoundedCornerShape(8.dp)).padding(10.dp)
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    Button(
                        onClick = {
                            settings.domainId = domainId.toIntOrNull() ?: 0
                            settings.namespace = namespace
                            topicNames.forEach { (id, name) ->
                                settings.setTopicName(id, name)
                            }
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Save and Return")
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
        WearHome(WearSensorBridge(androidx.compose.ui.platform.LocalContext.current), {}, {}) {}
    }
}