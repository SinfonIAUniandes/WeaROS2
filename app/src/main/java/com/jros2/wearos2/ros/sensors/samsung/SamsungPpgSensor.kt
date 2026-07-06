package com.jros2.wearos2.ros.sensors.samsung

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jros2.wearos2.ros.WearSensor
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mobile_sensor_msgs.SamsungHealthHeartRate
import mobile_sensor_msgs.SamsungHealthSpO2
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

class SamsungPpgSensor : WearSensor {
    private val TAG = "SamsungPpgSensor"
    override val id = "samsung_ppg"
    override val name = "Samsung SpO2 & Heart Rate"
    override val topicName = "samsung_health"
    var resolvedTopicName: String = topicName

    override val enabled = MutableStateFlow(true)
    private val _messageCount = MutableStateFlow(0L)
    override val messageCount: StateFlow<Long> = _messageCount
    private val _displayValue = MutableStateFlow("Connecting to Samsung Health...")
    override val displayValue: StateFlow<String> = _displayValue

    private var hrPublisher: ROS2Publisher<SamsungHealthHeartRate>? = null
    private var spo2Publisher: ROS2Publisher<SamsungHealthSpO2>? = null

    private val hrMsg = SamsungHealthHeartRate()
    private val spo2Msg = SamsungHealthSpO2()

    private var healthTrackingService: HealthTrackingService? = null
    private var hrTracker: HealthTracker? = null
    private var spo2Tracker: HealthTracker? = null
    private val handler = Handler(Looper.getMainLooper())

    private val hrListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(list: List<DataPoint>) {
            if (!enabled.value) return
            for (dataPoint in list) {
                hrMsg.status = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
                hrMsg.heartRate = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
                
                val hrIbiList: List<Int>? = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)
                val hrIbiStatus: List<Int>? = dataPoint.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST)
                
                if (!hrIbiList.isNullOrEmpty()) {
                    hrMsg.ibi = hrIbiList.last()
                }
                if (!hrIbiStatus.isNullOrEmpty()) {
                    hrMsg.ibiQuality = hrIbiStatus.last()
                }
                
                hrPublisher?.publish(hrMsg)
                _messageCount.value++
                _displayValue.value = "HR: ${hrMsg.heartRate} bpm (Status: ${hrMsg.status})"
            }
        }
        override fun onFlushCompleted() {}
        override fun onError(trackerError: HealthTracker.TrackerError) {
            _displayValue.value = "HR Error: $trackerError"
        }
    }

    private val spo2Listener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(list: List<DataPoint>) {
            if (!enabled.value) return
            for (dataPoint in list) {
                val status = dataPoint.getValue(ValueKey.SpO2Set.STATUS)
                spo2Msg.status = status
                if (status == 2) { // SpO2Status.MEASUREMENT_COMPLETED is typically 2
                    spo2Msg.spo2 = dataPoint.getValue(ValueKey.SpO2Set.SPO2)
                }
                spo2Publisher?.publish(spo2Msg)
                _messageCount.value++
                _displayValue.value = "SpO2: ${spo2Msg.spo2}% (Status: ${spo2Msg.status})"
            }
        }
        override fun onFlushCompleted() {}
        override fun onError(trackerError: HealthTracker.TrackerError) {
            _displayValue.value = "SpO2 Error: $trackerError"
        }
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "Connected to HealthTrackingService")
            _displayValue.value = "Connected. Starting trackers..."
            
            try {
                hrTracker = healthTrackingService?.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                handler.post { hrTracker?.setEventListener(hrListener) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init HR tracker", e)
            }

            try {
                spo2Tracker = healthTrackingService?.getHealthTracker(HealthTrackerType.SPO2_ON_DEMAND)
                handler.post { spo2Tracker?.setEventListener(spo2Listener) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init SpO2 tracker", e)
            }
        }

        override fun onConnectionEnded() {
            Log.i(TAG, "Disconnected from HealthTrackingService")
            _displayValue.value = "Disconnected"
        }

        override fun onConnectionFailed(e: HealthTrackerException) {
            Log.e(TAG, "Connection failed", e)
            _displayValue.value = "Connection Failed"
        }
    }

    override fun start(node: ROS2Node, context: Context) {
        val hrTopicName = "$resolvedTopicName/heart_rate"
        val spo2TopicName = "$resolvedTopicName/spo2"
        
        hrPublisher = node.createPublisher(ROS2Topic(hrTopicName, SamsungHealthHeartRate::class.java))
        spo2Publisher = node.createPublisher(ROS2Topic(spo2TopicName, SamsungHealthSpO2::class.java))

        healthTrackingService = HealthTrackingService(connectionListener, context)
        healthTrackingService?.connectService()
    }

    override fun stop() {
        handler.post {
            hrTracker?.unsetEventListener()
            spo2Tracker?.unsetEventListener()
        }
        healthTrackingService?.disconnectService()
        
        hrPublisher = null
        spo2Publisher = null
        hrTracker = null
        spo2Tracker = null
    }
}
