package com.jros2.wearos2.ros.sensors

import android.content.Context
import com.jros2.wearos2.ros.WearSensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import std_msgs.Int64
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

class StepsSensor : WearSensor {
    override val id = "steps_daily"
    override val name = "Daily Steps"
    override val topicName = "steps_daily"
    var resolvedTopicName: String = topicName

    override val enabled = MutableStateFlow(true)
    private val _messageCount = MutableStateFlow(0L)
    override val messageCount: StateFlow<Long> = _messageCount
    private val _displayValue = MutableStateFlow("Waiting for data")
    override val displayValue: StateFlow<String> = _displayValue

    private val msg = Int64()
    private var publisher: ROS2Publisher<Int64>? = null

    override fun start(node: ROS2Node, context: Context) {
        publisher = node.createPublisher(ROS2Topic(resolvedTopicName, Int64::class.java))
        _displayValue.value = "Registered. Waiting for steps..."
    }

    override fun stop() {
        publisher = null
    }

    fun onStepsReceived(steps: Long) {
        if (!enabled.value) return
        msg.data = steps
        publisher?.publish(msg)
        _messageCount.value++
        _displayValue.value = "$steps steps"
    }
}
