package com.jros2.wearos2.ros

import android.content.Context
import com.jros2.wearos2.SettingsManager
import com.jros2.wearos2.ros.sensors.GpsSensor
import com.jros2.wearos2.ros.sensors.ImuSensor
import com.jros2.wearos2.ros.sensors.MicrophoneSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import us.ihmc.jros2.ROS2Node

class WearSensorBridge(private val context: Context) {
    private val settings = SettingsManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var rosNode: ROS2Node? = null

    val sensors = listOf(
        ImuSensor(),
        GpsSensor(),
        MicrophoneSensor()
    )

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun start() {
        if (_isRunning.value) return
        log("Iniciando bridge ROS2...")
        scope.launch {
            try {
                val node = ROS2Node("wear_sensor_node", settings.domainId)
                rosNode = node
                val namespace = settings.namespace
                withContext(Dispatchers.Main) {
                    sensors.forEach { sensor ->
                        val rawTopic = settings.getTopicName(sensor.id, sensor.topicName)
                        val fullTopic = if (namespace.isBlank()) {
                            rawTopic
                        } else if (rawTopic.startsWith("/")) {
                            "/$namespace$rawTopic"
                        } else {
                            "/$namespace/$rawTopic"
                        }
                        try {
                            when (sensor) {
                                is GpsSensor -> sensor.resolvedTopicName = fullTopic
                                is ImuSensor -> sensor.resolvedTopicName = fullTopic
                                is MicrophoneSensor -> sensor.resolvedTopicName = fullTopic
                            }
                            sensor.start(node, context)
                            log("${sensor.name} activo en $fullTopic")
                        } catch (t: Throwable) {
                            val msg = "${t.javaClass.simpleName}: ${t.message}"
                            val cause = t.cause?.let { " Cause: ${it.javaClass.simpleName}: ${it.message}" } ?: ""
                            log("Fallo iniciando ${sensor.name}: $msg$cause")
                        }
                    }
                }
                _isRunning.value = true
            } catch (t: Throwable) {
                val msg = "${t.javaClass.simpleName}: ${t.message}"
                val cause = t.cause?.let { " Cause: ${it.javaClass.simpleName}: ${it.message}" } ?: ""
                log("Error ROS2: $msg$cause")
            }
        }
    }

    fun stop() {
        if (!_isRunning.value) return
        sensors.forEach { it.stop() }
        rosNode?.close()
        rosNode = null
        _isRunning.value = false
        log("Bridge detenido")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private fun log(message: String) {
        val current = _logs.value.toMutableList()
        current.add(0, message)
        _logs.value = current.take(40)
    }
}