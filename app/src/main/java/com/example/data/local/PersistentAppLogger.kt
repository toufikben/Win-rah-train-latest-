package com.example.data.local

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PersistentAppLogger {
    private const val TAG = "WinRahTracking"
    private const val LOG_FILE = "tracking-service.log"
    private const val BACKUP_FILE = "tracking-service.log.1"
    private const val MAX_LOG_SIZE = 512 * 1024L

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        write("LOGGER_INITIALIZED")
    }

    @Synchronized
    fun write(event: String, error: Throwable? = null) {
        val message = buildString {
            append(timestamp())
            append(" | ")
            append(event)
            error?.let {
                append(" | ")
                append(it.javaClass.simpleName)
                append(": ")
                append(it.message ?: "no-message")
                append("\n")
                append(Log.getStackTraceString(it))
            }
        }

        if (error == null) Log.i(TAG, message) else Log.e(TAG, message, error)

        runCatching {
            val context = appContext ?: return
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                val backup = File(context.filesDir, BACKUP_FILE)
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
            file.appendText("$message\n", Charsets.UTF_8)
        }.onFailure {
            Log.e(TAG, "Unable to persist tracking log", it)
        }
    }

    fun read(context: Context): String = runCatching {
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        if (file.exists()) file.readText(Charsets.UTF_8)
        else "لا توجد سجلات محفوظة."
    }.getOrElse { "تعذر قراءة السجل: ${it.message}" }

    fun clear(context: Context) {
        val directory = context.applicationContext.filesDir
        File(directory, LOG_FILE).delete()
        File(directory, BACKUP_FILE).delete()
        write("LOGGER_CLEARED")
    }

    private fun timestamp(): String = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss.SSS",
        Locale.US,
    ).format(Date())
}
