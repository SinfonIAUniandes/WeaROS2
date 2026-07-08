package com.jros2.wearos2.ros.sensors

import android.content.Context
import com.jros2.wearos2.ros.BaseWearSensor
import com.jros2.wearos2.ros.stampHeader
import sensor_msgs.Joy
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

/**
 * Publishes [sensor_msgs/Joy][Joy] commands driven by the full-screen touch joystick.
 * `axes[0]` is the X axis (right positive) and `axes[1]` is the Y axis (up positive),
 * both normalized to [-1, 1]. The UI calls [publishAxes] on every touch move and once
 * with (0, 0) on release so the consumer sees the stick recenter.
 */
class JoystickController : BaseWearSensor("joystick", "Joystick", "joy", "x=0.00 y=0.00") {
    private val msg = Joy()
    private var publisher: ROS2Publisher<Joy>? = null

    /** True once the bridge is running and a publisher exists. */
    val isReady: Boolean
        get() = publisher != null

    override fun start(node: ROS2Node, context: Context) {
        publisher = node.createPublisher(ROS2Topic(resolvedTopicName, Joy::class.java))
    }

    override fun stop() {
        publisher = null
    }

    fun publishAxes(x: Float, y: Float) {
        val pub = publisher ?: return
        if (!enabled.value) return
        try {
            stampHeader(msg.header, "watch_joystick")
            val axes = msg.axes
            axes.clear()
            axes.add(x)
            axes.add(y)
            pub.publish(msg)
            _messageCount.value++
            _displayValue.value = "x=%.2f y=%.2f".format(x, y)
        } catch (t: Throwable) {
            _displayValue.value = "Err: ${t.javaClass.simpleName}"
        }
    }
}
