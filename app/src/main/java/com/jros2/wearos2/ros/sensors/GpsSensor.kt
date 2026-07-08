package com.jros2.wearos2.ros.sensors

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.jros2.wearos2.ros.BaseWearSensor
import com.jros2.wearos2.ros.stampHeader
import sensor_msgs.NavSatFix
import sensor_msgs.NavSatStatus
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

class GpsSensor : BaseWearSensor("gps", "GPS", "gps", "Waiting for fix") {
    private val msg = NavSatFix()
    private var publisher: ROS2Publisher<NavSatFix>? = null
    private var locationManager: LocationManager? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!enabled.value) return
            stampHeader(msg.header, "watch_gps")
            msg.latitude = location.latitude
            msg.longitude = location.longitude
            msg.altitude = location.altitude
            msg.status.setStatus(NavSatStatus.STATUS_FIX)
            msg.status.setService(NavSatStatus.SERVICE_GPS)
            if (location.hasAccuracy()) {
                val variance = location.accuracy.toDouble() * location.accuracy.toDouble()
                msg.positionCovariance[0] = variance
                msg.positionCovariance[4] = variance
                msg.positionCovariance[8] = variance * 4
                msg.positionCovarianceType = NavSatFix.COVARIANCE_TYPE_DIAGONAL_KNOWN
            }
            publisher?.publish(msg)
            _messageCount.value++
            _displayValue.value = "%.5f, %.5f".format(location.latitude, location.longitude)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun start(node: ROS2Node, context: Context) {
        publisher = node.createPublisher(ROS2Topic(resolvedTopicName, NavSatFix::class.java))
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        var registered = false
        try {
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
                registered = true
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener)
                registered = true
            }
            val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                listener.onLocationChanged(lastKnown)
            } else if (registered) {
                _displayValue.value = "Searching for signal"
            } else {
                _displayValue.value = "Provider unavailable"
            }
        } catch (e: SecurityException) {
            _displayValue.value = "Permission denied"
        }
    }

    override fun stop() {
        try {
            locationManager?.removeUpdates(listener)
        } catch (_: SecurityException) {
        }
        publisher = null
    }
}