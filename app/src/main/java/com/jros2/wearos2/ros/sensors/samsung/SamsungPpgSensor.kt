package com.jros2.wearos2.ros.sensors.samsung

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jros2.wearos2.ros.BaseWearSensor
import com.jros2.wearos2.ros.stampHeader
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

/**
 * Publishes Samsung Health PPG readings, mirroring the official
 * galaxy-watch-health-sensors-example:
 *  - Heart rate via [HealthTrackerType.HEART_RATE_CONTINUOUS] runs continuously while
 *    the bridge is up.
 *  - Blood oxygen via [HealthTrackerType.SPO2_ON_DEMAND] is a one-shot measurement that
 *    the user triggers with [measureSpo2]; it runs until the sensor reports
 *    MEASUREMENT_COMPLETED (or a timeout), then stops — exactly like the example's
 *    Start button.
 *
 * Requires the Samsung Health Sensor API AAR plus the `android.permission.health.*`
 * runtime permissions on Android 16 / Health Platform (see the manifest and MainActivity).
 */
class SamsungPpgSensor : BaseWearSensor(
    "samsung_ppg",
    "Samsung SpO2 & Heart Rate",
    "samsung_health",
    "Connecting to Samsung Health...",
) {
    private val TAG = "SamsungPpgSensor"

    // Dedicated SpO2 state for the on-demand measurement UI.
    private val _spo2Measuring = MutableStateFlow(false)
    val spo2Measuring: StateFlow<Boolean> = _spo2Measuring
    private val _spo2Result = MutableStateFlow("Idle")
    val spo2Result: StateFlow<String> = _spo2Result

    private var hrPublisher: ROS2Publisher<SamsungHealthHeartRate>? = null
    private var spo2Publisher: ROS2Publisher<SamsungHealthSpO2>? = null

    private val hrMsg = SamsungHealthHeartRate()
    private val spo2Msg = SamsungHealthSpO2()

    private var healthTrackingService: HealthTrackingService? = null
    private var hrTracker: HealthTracker? = null
    private var spo2Tracker: HealthTracker? = null
    private val handler = Handler(Looper.getMainLooper())

    private var hrRunning = false
    private var spo2Running = false
    private var spo2Timeout: Runnable? = null

    /** True once connected and the SpO2 tracker is ready to measure. */
    val isSpo2Ready: Boolean
        get() = spo2Tracker != null

    private val hrListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(list: List<DataPoint>) {
            if (!enabled.value) return
            for (dataPoint in list) {
                val status = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
                val heartRate = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
                val ibiList: List<Int>? = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)
                val ibiStatusList: List<Int>? = dataPoint.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST)

                stampHeader(hrMsg.header, "watch_heart_rate")
                hrMsg.status = status
                hrMsg.heartRate = heartRate
                hrMsg.ibi = if (!ibiList.isNullOrEmpty()) ibiList.last() else 0
                hrMsg.ibiQuality = if (!ibiStatusList.isNullOrEmpty()) ibiStatusList.last() else 1

                hrPublisher?.publish(hrMsg)
                _messageCount.value++
                _displayValue.value = if (status == HR_STATUS_FIND_HR) {
                    "HR: $heartRate bpm"
                } else {
                    "HR: -- (status $status)"
                }
            }
        }

        override fun onFlushCompleted() {}

        override fun onError(trackerError: HealthTracker.TrackerError) {
            Log.e(TAG, "HR tracker error: $trackerError")
            hrRunning = false
            _displayValue.value = "HR Error: $trackerError"
        }
    }

    private val spo2Listener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(list: List<DataPoint>) {
            if (!enabled.value) return
            for (dataPoint in list) {
                val status = dataPoint.getValue(ValueKey.SpO2Set.STATUS)
                val spo2Value = if (status == SPO2_MEASUREMENT_COMPLETED) {
                    dataPoint.getValue(ValueKey.SpO2Set.SPO2)
                } else {
                    0
                }

                stampHeader(spo2Msg.header, "watch_spo2")
                spo2Msg.status = status
                spo2Msg.spo2 = spo2Value
                spo2Publisher?.publish(spo2Msg)
                _messageCount.value++

                _spo2Result.value = when (status) {
                    SPO2_CALCULATING -> "Calculating..."
                    SPO2_MEASUREMENT_COMPLETED -> "SpO2: $spo2Value%"
                    SPO2_LOW_SIGNAL -> "Low signal, hold still"
                    SPO2_DEVICE_MOVING -> "Device moving, hold still"
                    else -> "Status $status"
                }

                // On-demand measurement is finished once we get a completed reading.
                if (status == SPO2_MEASUREMENT_COMPLETED) {
                    finishSpo2Measurement()
                }
            }
        }

        override fun onFlushCompleted() {}

        override fun onError(trackerError: HealthTracker.TrackerError) {
            Log.e(TAG, "SpO2 tracker error: $trackerError")
            _spo2Result.value = "SpO2 Error: $trackerError"
            finishSpo2Measurement()
        }
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "Connected to HealthTrackingService")
            _displayValue.value = "Connected. Starting HR..."

            val capabilities = try {
                healthTrackingService?.trackingCapability?.supportHealthTrackerTypes
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading capabilities", e)
                null
            }

            if (capabilities == null || HealthTrackerType.HEART_RATE_CONTINUOUS in capabilities) {
                try {
                    hrTracker = healthTrackingService?.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                    startHeartRate()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to init HR tracker", e)
                    _displayValue.value = "HR init failed: ${e.message}"
                }
            } else {
                _displayValue.value = "Heart rate not supported"
            }

            // Get the SpO2 tracker ready but do NOT start measuring — it is on demand.
            if (capabilities == null || HealthTrackerType.SPO2_ON_DEMAND in capabilities) {
                try {
                    spo2Tracker = healthTrackingService?.getHealthTracker(HealthTrackerType.SPO2_ON_DEMAND)
                    _spo2Result.value = "Ready — tap to measure"
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to init SpO2 tracker", e)
                    _spo2Result.value = "SpO2 init failed"
                }
            } else {
                _spo2Result.value = "SpO2 not supported"
            }
        }

        override fun onConnectionEnded() {
            Log.i(TAG, "Disconnected from HealthTrackingService")
            _displayValue.value = "Disconnected"
        }

        override fun onConnectionFailed(e: HealthTrackerException) {
            Log.e(TAG, "Connection failed", e)
            _displayValue.value = "Connection Failed: ${e.errorCode}"
        }
    }

    private fun startHeartRate() {
        if (hrRunning) return
        handler.post {
            try {
                hrTracker?.setEventListener(hrListener)
                hrRunning = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting HR listener", e)
            }
        }
    }

    /**
     * Start a single on-demand SpO2 measurement. No-op if the sensor is disabled, not
     * connected yet, or a measurement is already running. The measurement stops itself
     * on completion or after [SPO2_MEASUREMENT_DURATION_MS].
     */
    fun measureSpo2() {
        if (!enabled.value) {
            _spo2Result.value = "Sensor disabled"
            return
        }
        val tracker = spo2Tracker
        if (tracker == null) {
            _spo2Result.value = "Not ready — start bridge"
            return
        }
        if (_spo2Measuring.value) return

        _spo2Measuring.value = true
        _spo2Result.value = "Measuring — hold still"
        handler.post {
            try {
                tracker.setEventListener(spo2Listener)
                spo2Running = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting SpO2 measurement", e)
                _spo2Result.value = "Start failed: ${e.message}"
                _spo2Measuring.value = false
            }
        }

        spo2Timeout?.let { handler.removeCallbacks(it) }
        val timeout = Runnable {
            if (_spo2Measuring.value) {
                _spo2Result.value = "Measurement failed — try again"
                finishSpo2Measurement()
            }
        }
        spo2Timeout = timeout
        handler.postDelayed(timeout, SPO2_MEASUREMENT_DURATION_MS)
    }

    private fun finishSpo2Measurement() {
        spo2Timeout?.let { handler.removeCallbacks(it) }
        spo2Timeout = null
        if (spo2Running) {
            try {
                spo2Tracker?.unsetEventListener()
            } catch (e: Exception) {
                Log.e(TAG, "Failed stopping SpO2 listener", e)
            }
            spo2Running = false
        }
        _spo2Measuring.value = false
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
        handler.removeCallbacksAndMessages(null)
        spo2Timeout = null
        handler.post {
            try {
                if (hrRunning) hrTracker?.unsetEventListener()
            } catch (_: Exception) {
            }
            try {
                if (spo2Running) spo2Tracker?.unsetEventListener()
            } catch (_: Exception) {
            }
            hrRunning = false
            spo2Running = false
        }
        _spo2Measuring.value = false
        _spo2Result.value = "Idle"
        healthTrackingService?.disconnectService()

        hrPublisher = null
        spo2Publisher = null
        hrTracker = null
        spo2Tracker = null
    }

    private companion object {
        // Values mirrored from the Samsung example's HeartRateStatus / SpO2Status.
        const val HR_STATUS_FIND_HR = 1
        const val SPO2_CALCULATING = 0
        const val SPO2_MEASUREMENT_COMPLETED = 2
        const val SPO2_DEVICE_MOVING = -4
        const val SPO2_LOW_SIGNAL = -5
        const val SPO2_MEASUREMENT_DURATION_MS = 35_000L
    }
}
