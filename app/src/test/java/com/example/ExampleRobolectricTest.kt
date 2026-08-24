package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dz.winrah.trainradar.R
import com.example.audio.TrainSoundSynthesizer
import com.example.audio.TrainSoundType
import com.example.data.TrainRepository
import com.example.model.CrowdingLevel
import com.example.model.DelayLevel
import com.example.model.TrainDirection
import com.example.notification.TrainNotificationHelper
import com.example.viewmodel.TrainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun testAppNameResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Win rah train 🚂", appName)
    }

    @Test
    fun testAllSuburbanLinesDataIntegrity() {
        val allLines = TrainRepository.suburbLines
        assertTrue("Should have suburban lines defined", allLines.isNotEmpty())
        assertEquals(5, allLines.size)

        for (line in allLines) {
            assertTrue("Line ${line.id} must have a non-empty name", line.name.isNotBlank())
            assertTrue("Line ${line.id} must have at least 2 stations", line.stations.size >= 2)

            for (station in line.stations) {
                assertTrue("Station ${station.name} latitude should be within Algeria range (35..38)", station.latitude in 35.0..38.0)
                assertTrue("Station ${station.name} longitude should be within Algeria range (2..6)", station.longitude in 2.0..6.0)
                assertTrue("Station platform should not be blank", station.defaultPlatform.isNotBlank())
            }
        }
    }

    @Test
    fun testViewModelStateAndInteractions() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TrainViewModel(app, false)

        // Default suburb check
        val defaultSuburb = viewModel.selectedSuburb.value
        assertNotNull(defaultSuburb)
        assertEquals("thnia_algiers", defaultSuburb.id)

        // Change suburb
        val ouedLine = TrainRepository.suburbLines.first { it.id == "thenia_tizi" }
        viewModel.selectSuburb(ouedLine)
        assertEquals("thenia_tizi", viewModel.selectedSuburb.value.id)

        // Direction Toggle
        viewModel.setDirectionFilter(TrainDirection.INBOUND)
        assertEquals(TrainDirection.INBOUND, viewModel.selectedDirectionFilter.value)

        // Waiting Station selection
        val firstStation = ouedLine.stations.first()
        viewModel.selectStation(firstStation)
        assertEquals(firstStation.code, viewModel.selectedStation.value.code)

        // Live reports are submitted to the backend; no fabricated local aggregate is expected.
        assertTrue(viewModel.crowdReportsMap.value.isEmpty())

        // Onboard cannot start without a real live train/trip from the backend.
        assertFalse(viewModel.isOnboardMode.value)
        assertFalse(viewModel.toggleOnboardMode(true))
        assertFalse(viewModel.isOnboardMode.value)

        // Destination Alarm
        viewModel.setDestinationAlarm(firstStation, ouedLine.id, 2.0f)
        val alarm = viewModel.destinationAlarm.value
        assertTrue(alarm.isEnabled)
        assertEquals(firstStation.code, alarm.targetStation?.code)
        assertEquals(2.0f, alarm.alertDistanceKm)

        viewModel.cancelDestinationAlarm()
        assertFalse(viewModel.destinationAlarm.value.isEnabled)
    }

    @Test
    fun testSoundSynthesizerTypes() {
        TrainSoundType.values().forEach { soundType ->
            assertNotNull(soundType.titleAr)
            assertNotNull(soundType.descriptionAr)
            assertNotNull(soundType.iconEmoji)
        }
    }

    @Test
    fun testNotificationChannelsInitialized() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TrainNotificationHelper.initNotificationChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        assertNotNull(nm)
    }
}


