package com.jros2.wearos2.ros.sensors

import android.content.Context
import com.jros2.wearos2.ros.BaseWearSensor
import std_msgs.Int64
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

class StepsSensor : BaseWearSensor("steps_daily", "Daily Steps", "steps_daily", "Waiting for data") {
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
