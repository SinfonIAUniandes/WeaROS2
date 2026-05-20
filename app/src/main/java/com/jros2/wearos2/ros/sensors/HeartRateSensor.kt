package com.jros2.wearos2.ros.sensors

import android.content.Context
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import com.jros2.wearos2.ros.WearSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import std_msgs.Float32
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

class HeartRateSensor : WearSensor {
    override val id = "heart_rate"
    override val name = "Heart Rate"
    override val topicName = "heart_rate"
    var resolvedTopicName: String = topicName

    override val enabled = MutableStateFlow(true)
    private val _messageCount = MutableStateFlow(0L)
    override val messageCount: StateFlow<Long> = _messageCount
    private val _displayValue = MutableStateFlow("Waiting for data")
    override val displayValue: StateFlow<String> = _displayValue

    private val msg = Float32()
    private var publisher: ROS2Publisher<Float32>? = null

    private var measureClient: androidx.health.services.client.MeasureClient? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    private val callback = object : MeasureCallback {
        override fun onDataReceived(data: DataPointContainer) {
            if (!enabled.value) return
            val heartRateData = data.getData(DataType.HEART_RATE_BPM)
            if (heartRateData.isNotEmpty()) {
                // Get the most recent value
                val hr = heartRateData.last().value.toFloat()
                msg.data = hr
                publisher?.publish(msg)
                _messageCount.value++
                _displayValue.value = "%.1f bpm".format(hr)
            }
        }

        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (availability is DataTypeAvailability) {
                _displayValue.value = "State: ${availability.name}"
            }
        }
    }

    override fun start(node: ROS2Node, context: Context) {
        publisher = node.createPublisher(ROS2Topic(resolvedTopicName, Float32::class.java))
        measureClient = HealthServices.getClient(context).measureClient
        scope = CoroutineScope(Dispatchers.Main)

        job = scope?.launch {
            try {
                _displayValue.value = "Fetching capabilities..."
                val capabilities = measureClient?.getCapabilitiesAsync()?.await()
                val supportsHeartRate = capabilities?.supportedDataTypesMeasure?.contains(DataType.HEART_RATE_BPM) == true

                if (supportsHeartRate) {
                    _displayValue.value = "Registering..."
                    measureClient?.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
                    _displayValue.value = "Registered. Waiting for pulse..."
                } else {
                    _displayValue.value = "Not Supported by OS"
                }
            } catch (e: Exception) {
                _displayValue.value = "API Error: ${e.message}"
            }
        }
    }

    override fun stop() {
        job?.cancel()
        measureClient?.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        publisher = null
        measureClient = null
    }
}
