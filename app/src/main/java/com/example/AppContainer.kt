package com.example

import android.content.Context
import com.example.billing.SupportBilling
import com.example.data.NetworkRepository
import com.example.data.prefs.UserPrefsRepository
import com.example.data.remote.BackendService

/** Process-wide objects, created once (no DI framework needed at this size). */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val network = NetworkRepository(BackendService.api)
    val prefs = UserPrefsRepository(app)
    val billing by lazy { SupportBilling(app, prefs) }
}
