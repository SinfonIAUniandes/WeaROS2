package com.jros2.wearos2.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.jros2.wearos2.SettingsManager
import com.jros2.wearos2.ros.WearSensorBridge

/**
 * Editor for the ROS 2 domain id, namespace, and per-feature topic overrides. Values are
 * loaded from and saved back to [SettingsManager]; the feature list is driven entirely by
 * [WearSensorBridge.sensors], so new features appear here automatically.
 */
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
                        Text("Domain ID", color = AppColors.Muted)
                        BasicTextField(
                            value = domainId,
                            onValueChange = { domainId = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = TextStyle(color = AppColors.OnSurface),
                            cursorBrush = SolidColor(AppColors.OnSurface),
                            modifier = Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(8.dp)).padding(10.dp)
                        )
                    }
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text("Namespace", color = AppColors.Muted)
                        BasicTextField(
                            value = namespace,
                            onValueChange = { namespace = it },
                            singleLine = true,
                            textStyle = TextStyle(color = AppColors.OnSurface),
                            cursorBrush = SolidColor(AppColors.OnSurface),
                            modifier = Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(8.dp)).padding(10.dp)
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
                    val enabled by sensor.enabled.collectAsState()
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        // Enable/disable this feature (persisted, takes effect live).
                        SwitchButton(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                sensor.enabled.value = checked
                                settings.setSensorEnabled(sensor.id, checked)
                            },
                            label = { Text(sensor.name) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BasicTextField(
                            value = topicNames[sensor.id] ?: "",
                            onValueChange = { topicNames[sensor.id] = it },
                            singleLine = true,
                            enabled = enabled,
                            textStyle = TextStyle(color = if (enabled) AppColors.OnSurface else AppColors.Muted),
                            cursorBrush = SolidColor(AppColors.OnSurface),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).background(AppColors.Surface, RoundedCornerShape(8.dp)).padding(10.dp)
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
