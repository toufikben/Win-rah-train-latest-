package com.example

import android.app.Application
import com.example.data.local.PersistentAppLogger

class WinRahApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PersistentAppLogger.initialize(this)

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
