package com.example

import android.app.Application
import com.example.alerts.ApproachAlertService
import com.example.alerts.CommuteDelayWorker
import com.example.data.local.PersistentAppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class WinRahApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PersistentAppLogger.initialize(this)
        container = AppContainer(this)
        ApproachAlertService.ensureChannels(this)

        // Keep the commute delay check in step with the commute and the supporter status.
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        appScope.launch {
            container.prefs.prefs.map { it.commute to it.isSupporter }.distinctUntilChanged()
                .collect { (commute, supporter) ->
                    // WorkManager may be unavailable (tests, broken init); the alert is best-effort.
                    runCatching { CommuteDelayWorker.sync(this@WinRahApplication, commute, supporter) }
                        .onFailure { PersistentAppLogger.write("COMMUTE_WORK_SYNC_FAILED", it) }
                }
        }
        // Re-check purchases at every launch: Play is the truth, the cache only bridges offline gaps.
        container.billing.refresh()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            PersistentAppLogger.write(
                "UNCAUGHT_EXCEPTION thread=${thread.name}",
                error,
            )
            previousHandler?.uncaughtException(thread, error)
        }
    }
}
