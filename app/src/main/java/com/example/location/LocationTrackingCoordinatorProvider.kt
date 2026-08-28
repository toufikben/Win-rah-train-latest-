package com.example.location

import android.content.Context

object LocationTrackingCoordinatorProvider {
    @Volatile
    private var instance: LocationTrackingCoordinator? = null

    @Synchronized
    fun get(context: Context): LocationTrackingCoordinator {
        return instance ?: LocationTrackingCoordinator(context.applicationContext).also { instance = it }
    }
}
