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
import com.jros2.wearos2.ros.WearSensorBridge
import com.jros2.wearos2.presentation.theme.WeaROS2Theme

class MainActivity : ComponentActivity() {
    private lateinit var bridge: WearSensorBridge
    private lateinit var multicastLock: WifiManager.MulticastLock

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableRosDiscovery()
        requestRuntimePermissions()
        bridge = WearSensorBridge(this)
        setContent {
            WeaROS2Theme {
                WearHome(bridge)
            }
        }
    }

    private fun enableRosDiscovery() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("wear_ros2_multicast_lock")
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()
    }

    private fun requestRuntimePermissions() {
        val wanted = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
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
fun WearHome(bridge: WearSensorBridge) {
    val isRunning by bridge.isRunning.collectAsState()
    val logs by bridge.logs.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val context = LocalContext.current
    var permissionsHint by remember { mutableStateOf("") }

    DisposableEffect(context) {
        permissionsHint = buildString {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                append("GPS sin permiso. ")
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                append("Micrófono sin permiso.")
            }
        }.trim()
        onDispose { }
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
                        Text("WeaROS2 Sensores", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Card(onClick = { if (isRunning) bridge.stop() else bridge.start() }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(if (isRunning) "Detener bridge" else "Iniciar bridge")
                            Text(if (isRunning) "Publicando ROS2" else "Idle")
                        }
                    }
                }
                if (permissionsHint.isNotBlank()) {
                    item {
                        Card(onClick = { }) {
                            Text(
                                text = permissionsHint,
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                textAlign = TextAlign.Center
                            )
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

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WeaROS2Theme {
        WearHome(WearSensorBridge(androidx.compose.ui.platform.LocalContext.current))
    }
}