package com.jros2.wearos2.ros

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import us.ihmc.jros2.ROS2Node

/**
 * A single ROS 2 feature the bridge can start and stop on the shared [ROS2Node]. This
 * covers publishers (sensors), subscribers (audio playback, notifications) and interactive
 * controls (joystick) alike — anything with a lifecycle, an on/off toggle, and a bit of
 * status to show in the UI.
 *
 * To add a new feature: implement this interface (usually via [BaseWearSensor]) and add one
 * instance to [WearSensorBridge.sensors]. Nothing else in the bridge needs to change — it
 * resolves the topic and calls [start]/[stop] generically.
 */
interface WearSensor {
    /** Stable key used to persist a per-feature topic override in settings. */
    val id: String

    /** Human-readable label shown in the UI. */
    val name: String

    /** Default topic name, before namespace/override resolution. */
    val topicName: String

    /**
     * Fully-resolved topic (namespace + any per-feature override). The bridge assigns this
     * immediately before [start]; implementations read it when creating their pub/sub.
     */
    var resolvedTopicName: String

    /** UI toggle; implementations should skip work when this is `false`. */
    val enabled: MutableStateFlow<Boolean>

    /** Number of messages published/received so far. */
    val messageCount: StateFlow<Long>

    /** Short status string shown next to the feature in the UI. */
    val displayValue: StateFlow<String>

    fun start(node: ROS2Node, context: Context)

    fun stop()
}
