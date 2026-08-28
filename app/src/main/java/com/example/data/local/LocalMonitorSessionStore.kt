package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.MonitorBinding

/** Durable local identity for a user-started monitor session. */
class LocalMonitorSessionStore(context: Context) {
    private val helper = Helper(context.applicationContext)

    @Synchronized
    fun save(binding: MonitorBinding) {
        val values = ContentValues().apply {
            put(COLUMN_ID, ROW_ID)
            put(COLUMN_SESSION_ID, binding.sessionId)
            binding.tripId?.let { put(COLUMN_TRIP_ID, it) } ?: putNull(COLUMN_TRIP_ID)
            binding.trainId?.let { put(COLUMN_TRAIN_ID, it) } ?: putNull(COLUMN_TRAIN_ID)
            put(COLUMN_LINE_ID, binding.lineId)
            put(COLUMN_DIRECTION, binding.direction.name)
            put(COLUMN_SAVED_AT, System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun load(): MonitorBinding? {
        helper.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_SESSION_ID, COLUMN_LINE_ID, COLUMN_DIRECTION, COLUMN_TRIP_ID, COLUMN_TRAIN_ID),
            "$COLUMN_ID = ?",
            arrayOf(ROW_ID.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val sessionId = cursor.getString(0)
            val lineId = cursor.getString(1)
            val direction = runCatching { com.example.model.TrainDirection.valueOf(cursor.getString(2)) }.getOrNull()
            val tripId = cursor.getString(3)
            val trainId = cursor.getString(4)
            if (sessionId.isBlank() || lineId.isNullOrBlank() || direction == null) return null
            return MonitorBinding(sessionId, lineId, direction, tripId, trainId)
        }
    }

    @Synchronized
    fun clear() {
        helper.writableDatabase.delete(TABLE, "$COLUMN_ID = ?", arrayOf(ROW_ID.toString()))
    }

    fun close() = helper.close()

    private class Helper(context: Context) : SQLiteOpenHelper(context, DATABASE, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY, " +
                    "$COLUMN_SESSION_ID TEXT NOT NULL, " +
                    "$COLUMN_LINE_ID TEXT NOT NULL, " +
                    "$COLUMN_DIRECTION TEXT NOT NULL, " +
                    "$COLUMN_TRIP_ID TEXT, " +
                    "$COLUMN_TRAIN_ID TEXT, " +
                    "$COLUMN_SAVED_AT INTEGER NOT NULL" +
                    ")"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COLUMN_SAVED_AT INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COLUMN_LINE_ID TEXT")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COLUMN_DIRECTION TEXT")
            }
        }
    }

    private companion object {
        const val DATABASE = "winrah_local_state.db"
        const val VERSION = 3
        const val TABLE = "monitor_session"
        const val ROW_ID = 1L
        const val COLUMN_ID = "id"
        const val COLUMN_SESSION_ID = "session_id"
        const val COLUMN_LINE_ID = "line_id"
        const val COLUMN_DIRECTION = "direction"
        const val COLUMN_TRIP_ID = "trip_id"
        const val COLUMN_TRAIN_ID = "train_id"
        const val COLUMN_SAVED_AT = "saved_at"
    }
}
