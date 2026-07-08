package com.jros2.wearos2.ros

import android.content.Context
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import com.jros2.wearos2.SettingsManager
import com.jros2.wearos2.ros.sensors.AudioPlayerSensor
import com.jros2.wearos2.ros.sensors.ButtonPublisher
import com.jros2.wearos2.ros.sensors.FloorsSensor
import com.jros2.wearos2.ros.sensors.GpsSensor
import com.jros2.wearos2.ros.sensors.ImuSensor
import com.jros2.wearos2.ros.sensors.JoystickController
import com.jros2.wearos2.ros.sensors.MicrophoneSensor
import com.jros2.wearos2.ros.sensors.NotificationSensor
import com.jros2.wearos2.ros.sensors.SliderController
import com.jros2.wearos2.ros.sensors.StepsSensor
import com.jros2.wearos2.ros.sensors.samsung.SamsungPpgSensor
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

    val joystick = JoystickController()
    val samsung = SamsungPpgSensor()
    val button = ButtonPublisher()
    val slider = SliderController()

    val sensors = listOf(
        ImuSensor(),
        GpsSensor(),
        MicrophoneSensor(),
        samsung,
        StepsSensor(),
        FloorsSensor(),
        AudioPlayerSensor(),
        NotificationSensor(),
        joystick,
        button,
        slider
    )

    init {
        // Restore each feature's on/off preference (defaults to on).
        sensors.forEach { it.enabled.value = settings.isSensorEnabled(it.id, true) }
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val passiveListenerCallback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            val stepsData = dataPoints.getData(DataType.STEPS_DAILY)
            if (stepsData.isNotEmpty()) {
                val latestSteps = stepsData.last().value
                sensors.filterIsInstance<StepsSensor>().forEach {
                    it.onStepsReceived(latestSteps)
                }
            }

            val floorsData = dataPoints.getData(DataType.FLOORS_DAILY)
            if (floorsData.isNotEmpty()) {
                val latestFloors = floorsData.last().value
                sensors.filterIsInstance<FloorsSensor>().forEach {
                    it.onFloorsReceived(latestFloors)
                }
            }
        }
    }

    fun start() {
        if (_isRunning.value) return
        log("Starting ROS2 bridge...")
        scope.launch {
            try {
                val node = ROS2Node("wear_sensor_node", settings.domainId)
                rosNode = node
                val namespace = settings.namespace
                withContext(Dispatchers.Main) {
                    sensors.forEach { sensor ->
                        val rawTopic = settings.getTopicName(sensor.id, sensor.topicName)
                        val fullTopic = resolveTopic(namespace, rawTopic)
                        try {
                            sensor.resolvedTopicName = fullTopic
                            sensor.start(node, context)
                            log("${sensor.name} active on $fullTopic")
                        } catch (t: Throwable) {
                            val msg = "${t.javaClass.simpleName}: ${t.message}"
                            val cause = t.cause?.let { " Cause: ${it.javaClass.simpleName}: ${it.message}" } ?: ""
                            log("Failed starting ${sensor.name}: $msg$cause")
                        }
                    }

                    // Register passive listener callback for daily steps/floors
                    try {
                        val passiveClient = HealthServices.getClient(context).passiveMonitoringClient
                        val capabilities = passiveClient.getCapabilitiesAsync().await()
                        val supportedTypes = mutableSetOf<DataType<*, *>>()

                        if (DataType.STEPS_DAILY in capabilities.supportedDataTypesPassiveMonitoring) {
                            supportedTypes.add(DataType.STEPS_DAILY)
                            log("Passive daily steps supported")
                        } else {
                            log("Passive daily steps NOT supported")
                        }

                        if (DataType.FLOORS_DAILY in capabilities.supportedDataTypesPassiveMonitoring) {
                            supportedTypes.add(DataType.FLOORS_DAILY)
                            log("Passive daily floors supported")
                        } else {
                            log("Passive daily floors NOT supported")
                        }

                        if (supportedTypes.isNotEmpty()) {
                            val config = PassiveListenerConfig.builder()
                                .setDataTypes(supportedTypes)
                                .build()
                            passiveClient.setPassiveListenerCallback(config, passiveListenerCallback)
                            log("Registered passive callback")
                        }
                    } catch (e: Exception) {
                        log("Failed setting passive client: ${e.message}")
                    }
                }
                _isRunning.value = true
            } catch (t: Throwable) {
                val msg = "${t.javaClass.simpleName}: ${t.message}"
                val cause = t.cause?.let { " Cause: ${it.javaClass.simpleName}: ${it.message}" } ?: ""
                log("ROS2 Error: $msg$cause")
            }
        }
    }

    fun stop() {
        if (!_isRunning.value) return
        sensors.forEach { it.stop() }
        
        try {
            val passiveClient = HealthServices.getClient(context).passiveMonitoringClient
            passiveClient.clearPassiveListenerCallbackAsync()
            log("Cleared passive callback")
        } catch (e: Exception) {
            log("Failed clearing passive callback: ${e.message}")
        }

        rosNode?.close()
        rosNode = null
        _isRunning.value = false
        log("Bridge stopped")
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