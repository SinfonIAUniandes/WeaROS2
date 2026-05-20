package com.jros2.wearos2.ros.sensors

import android.content.Context
import com.jros2.wearos2.ros.WearSensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import us.ihmc.jros2.ROS2Node

class BloodOxygenSensor : WearSensor {
    override val id = "spo2"
    override val name = "Blood Oxygen (SpO2)"
    override val topicName = "spo2"
    var resolvedTopicName: String = topicName

    override val enabled = MutableStateFlow(false)
    private val _messageCount = MutableStateFlow(0L)
    override val messageCount: StateFlow<Long> = _messageCount
    
    // SpO2 on WearOS 4+ requires the proprietary Samsung Health Sensor SDK (.aar)
    private val _displayValue = MutableStateFlow("Requires Samsung SDK")
    override val displayValue: StateFlow<String> = _displayValue

    override fun start(node: ROS2Node, context: Context) {
        // Implementation blocked until samsung-health-sensor-api.aar is provided
    }

    override fun stop() {
        // Nothing to stop
    }
}
