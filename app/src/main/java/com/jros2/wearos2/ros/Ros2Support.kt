package com.jros2.wearos2.ros

import std_msgs.Header
import us.ihmc.jros2.ROS2QoSProfile

/** Stamp a [std_msgs/Header][Header] with the current wall-clock time and a frame id. */
fun stampHeader(header: Header, frameId: String) {
    val nowMs = System.currentTimeMillis()
    header.stamp.sec = (nowMs / 1000).toInt()
    header.stamp.nanosec = ((nowMs % 1000) * 1_000_000).toInt()
    header.setFrameId(frameId)
}

/**
 * A RELIABLE / VOLATILE / KEEP_LAST(10) profile — matches what a standard ROS 2 publisher
 * offers. Subscribers need this (jros2's default subscription QoS is BEST_EFFORT, which
 * silently drops most of a reliable stream over Wi-Fi).
 */
fun reliableQos(): ROS2QoSProfile = ROS2QoSProfile().apply {
    history(ROS2QoSProfile.History.KEEP_LAST)
    depth(10)
    reliability(ROS2QoSProfile.Reliability.RELIABLE)
    durability(ROS2QoSProfile.Durability.VOLATILE)
}

/**
 * Prefix [rawTopic] with a ROS 2 [namespace] (e.g. `imu` under `watch` → `/watch/imu`),
 * preserving any leading slash on [rawTopic]. A blank namespace returns [rawTopic] as-is.
 */
fun resolveTopic(namespace: String, rawTopic: String): String = when {
    namespace.isBlank() -> rawTopic
    rawTopic.startsWith("/") -> "/$namespace$rawTopic"
    else -> "/$namespace/$rawTopic"
}
