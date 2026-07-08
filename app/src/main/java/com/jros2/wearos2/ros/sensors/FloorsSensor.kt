package com.jros2.wearos2.ros.sensors

import android.content.Context
import com.jros2.wearos2.ros.BaseWearSensor
import std_msgs.Float32
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

class FloorsSensor : BaseWearSensor("floors_daily", "Daily Floors", "floors_daily", "Waiting for data") {
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
