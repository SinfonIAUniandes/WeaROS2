package com.jros2.wearos2.ros.sensors

import android.content.Context
import com.jros2.wearos2.ros.BaseWearSensor
import std_msgs.Bool
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

/**
 * Publishes a [std_msgs/Bool][Bool] (`data: true`) every time the user presses the button
 * in the UI — a generic ROS 2 "button pressed" signal. A consumer can treat each received
 * message (or the `true` value) as the trigger.
 *
 * Note: `std_msgs/Empty` would be the more literal "trigger", but jros2 serializes it as
 * zero bytes, which is not wire-compatible with rosidl's Empty (it carries a 1-byte
 * placeholder) — so `ros2 topic echo` / rqt fail on it. `Bool` is a real field and works.
 *
 * [press] is a no-op until the bridge is running (so there is a publisher to send on).
 */
class ButtonPublisher : BaseWearSensor("button", "Button", "button", "Idle") {
    private val msg = Bool()
    private var publisher: ROS2Publisher<Bool>? = null

    override fun start(node: ROS2Node, context: Context) {
        publisher = node.createPublisher(ROS2Topic(resolvedTopicName, Bool::class.java))
    }

    override fun stop() {
        publisher = null
    }

    fun press() {
        val pub = publisher ?: return
        if (!enabled.value) return
        try {
            msg.data = true
            pub.publish(msg)
            _messageCount.value++
            _displayValue.value = "Pressed"
        } catch (t: Throwable) {
            _displayValue.value = "Err: ${t.javaClass.simpleName}"
        }
    }
}
