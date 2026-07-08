package com.jros2.wearos2.ros

import android.content.Context

/**
 * Process-wide singleton for the one [WearSensorBridge]. Both the UI ([MainActivity]) and
 * the background [com.jros2.wearos2.service.BridgeService] resolve the same instance from
 * here, so the bridge keeps running independently of whether the Activity is on screen.
 *
 * The bridge is created with the application context, so holding it statically does not leak
 * an Activity. It lives for the life of the process and is reusable across start/stop cycles.
 */
object RosBridgeHolder {
    @Volatile
    private var instance: WearSensorBridge? = null

    fun get(context: Context): WearSensorBridge =
        instance ?: synchronized(this) {
            instance ?: WearSensorBridge(context.applicationContext).also { instance = it }
        }
}
