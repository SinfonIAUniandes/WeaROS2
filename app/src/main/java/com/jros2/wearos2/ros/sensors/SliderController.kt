package com.jros2.wearos2.ros.sensors

import android.content.Context
import com.jros2.wearos2.ros.BaseWearSensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import std_msgs.Float32
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

/**
 * Publishes a normalized 0.0–1.0 value as [std_msgs/Float32][Float32] — a generic scalar
 * for things like robot volume, speed limit, brightness, etc. Driven by the on-screen
 * round slider (touch + rotary bezel/crown); the UI calls [setValue] as the knob moves.
 *
 * The current value is exposed via [value] so the slider screen can restore its position.
 */
class SliderController : BaseWearSensor("slider", "Slider", "slider", "0%") {
    private val msg = Float32()
    private var publisher: ROS2Publisher<Float32>? = null

    private val _value = MutableStateFlow(0f)
    val value: StateFlow<Float> = _value

    override fun start(node: ROS2Node, context: Context) {
        publisher = node.createPublisher(ROS2Topic(resolvedTopicName, Float32::class.java))
    }

    override fun stop() {
        publisher = null
    }

    /** [raw] is clamped to [0, 1]; the normalized value is published if the bridge is up. */
    fun setValue(raw: Float) {
        val clamped = raw.coerceIn(0f, 1f)
        _value.value = clamped
        val pub = publisher ?: return
        if (!enabled.value) return
        try {
            msg.data = clamped
            pub.publish(msg)
            _messageCount.value++
            _displayValue.value = "%.0f%%".format(clamped * 100)
        } catch (t: Throwable) {
            _displayValue.value = "Err: ${t.javaClass.simpleName}"
        }
    }
}
