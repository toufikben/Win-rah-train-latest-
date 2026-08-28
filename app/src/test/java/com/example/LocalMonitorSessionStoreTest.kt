package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalMonitorSessionStore
import com.example.model.MonitorBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalMonitorSessionStoreTest {
    @Test
    fun sessionBindingSurvivesStoreRecreationAndClearsOnExplicitStop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("winrah_local_state.db")

        val original = LocalMonitorSessionStore(context)
        val expected = MonitorBinding(
            sessionId = "real-session-001",
            tripId = "real-trip-001",
            trainId = "real-train-001",
        )
        original.save(expected)
        original.close()

        // A fresh store instance represents the new process after Process Death.
        val recreatedProcessStore = LocalMonitorSessionStore(context)
        assertEquals(expected, recreatedProcessStore.load())

        recreatedProcessStore.clear()
        recreatedProcessStore.close()

        val afterExplicitStop = LocalMonitorSessionStore(context)
        assertNull(afterExplicitStop.load())
        afterExplicitStop.close()
        context.deleteDatabase("winrah_local_state.db")
    }
}
