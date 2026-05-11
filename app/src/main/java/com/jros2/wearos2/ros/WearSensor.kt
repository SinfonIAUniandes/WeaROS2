package com.jros2.wearos2.ros

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import std_msgs.Header
import us.ihmc.jros2.ROS2Node

interface WearSensor {
    val id: String
    val name: String
    val topicName: String
    val enabled: MutableStateFlow<Boolean>
    val messageCount: StateFlow<Long>
    val displayValue: StateFlow<String>

    fun start(node: ROS2Node, context: Context)

    fun stop()
}

fun stampHeader(header: Header, frameId: String) {
    val nowMs = System.currentTimeMillis()
    header.stamp.sec = (nowMs / 1000).toInt()
    header.stamp.nanosec = ((nowMs % 1000) * 1_000_000).toInt()
    header.setFrameId(frameId)
}