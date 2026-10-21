package com.example

import com.example.location.GpsOwner
import com.example.location.GpsOwnerSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationOwnershipTest {
    @Test
    fun firstOwnerStartsUpdates() {
        val owners = GpsOwnerSet()
        assertTrue(owners.add(GpsOwner.UI))
        assertFalse(owners.add(GpsOwner.DESTINATION_ALARM))
    }

    @Test
    fun releaseBroadcastKeepsAlarmOwner() {
        val owners = GpsOwnerSet()
        owners.add(GpsOwner.DESTINATION_ALARM)
        owners.add(GpsOwner.BROADCAST_SERVICE)

        assertFalse("alarm still needs GPS", owners.remove(GpsOwner.BROADCAST_SERVICE))
        assertTrue(GpsOwner.DESTINATION_ALARM in owners)
        assertTrue("last owner gone", owners.remove(GpsOwner.DESTINATION_ALARM))
    }

    @Test
    fun releasingAnAbsentOwnerDoesNotStopOthers() {
        val owners = GpsOwnerSet()
        owners.add(GpsOwner.UI)
        assertFalse(owners.remove(GpsOwner.BROADCAST_SERVICE))
        assertTrue(GpsOwner.UI in owners)
    }
}
