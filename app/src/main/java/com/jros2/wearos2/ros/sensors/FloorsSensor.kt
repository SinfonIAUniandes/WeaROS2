package com.jros2.wearos2.ros.sensors

import android.content.Context
import com.jros2.wearos2.ros.WearSensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import std_msgs.Float32
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

class FloorsSensor : WearSensor {
    override val id = "floors_daily"
    override val name = "Daily Floors"
    override val topicName = "floors_daily"
    var resolvedTopicName: String = topicName

    override val enabled = MutableStateFlow(true)
    private val _messageCount = MutableStateFlow(0L)
    override val messageCount: StateFlow<Long> = _messageCount
    private val _displayValue = MutableStateFlow("Waiting for data")
    override val displayValue: StateFlow<String> = _displayValue

    private val msg = Float32()
    private var publisher: ROS2Publisher<Float32>? = null

    override fun start(node: ROS2Node, context: Context) {
        publisher = node.createPublisher(ROS2Topic(resolvedTopicName, Float32::class.java))
        _displayValue.value = "Registered. Waiting for floors..."
    }

    override fun stop() {
        publisher = null
    }

    fun onFloorsReceived(floors: Double) {
        if (!enabled.value) return
        msg.data = floors.toFloat()
        publisher?.publish(msg)
        _messageCount.value++
        _displayValue.value = "%.1f floors".format(floors)
    }
}
