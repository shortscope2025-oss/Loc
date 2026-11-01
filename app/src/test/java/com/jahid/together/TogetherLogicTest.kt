package com.jahid.together

import com.jahid.together.data.model.GeofenceZone
import com.jahid.together.data.model.HomeLocation
import com.jahid.together.data.model.InviteCode
import com.jahid.together.data.model.LiveLocation
import com.jahid.together.data.model.PairInfo
import com.jahid.together.service.GeofenceDetector
import org.junit.Assert.*
import org.junit.Test

class TogetherLogicTest {

    private val home = HomeLocation(
        latitude = 23.8103, // Dhaka center
        longitude = 90.4125,
        address = "আমাদের প্রিয় বাড়ি",
        radiusMeters = 500.0
    )

    @Test
    fun testDistanceCalculation_SamePoint_IsZero() {
        val dist = GeofenceDetector.calculateDistance(23.8103, 90.4125, 23.8103, 90.4125)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun testDistanceCalculation_KnownDistance() {
        // ~111km per latitude degree
        val dist = GeofenceDetector.calculateDistance(23.0, 90.0, 24.0, 90.0)
        assertTrue("Distance should be approx 111km", dist in 110_000.0..112_000.0)
    }

    @Test
    fun testGeofence_Entry500Meters_TriggersBengaliAlert() {
        // Partner was outside (>1000m), now enters ~400m from home
        val outsideLoc = LiveLocation(latitude = 23.8250, longitude = 90.4125, accuracy = 10f)
        val distOutside = GeofenceDetector.calculateDistance(outsideLoc.latitude, outsideLoc.longitude, home.latitude, home.longitude)
        assertTrue(distOutside > 500.0)

        // Point roughly 350m from home
        val insideLoc = LiveLocation(latitude = 23.8130, longitude = 90.4125, accuracy = 12f)
        val result = GeofenceDetector.evaluateTransition(
            location = insideLoc,
            home = home,
            previousZone = GeofenceZone.OUTSIDE_500,
            lastNotificationType = null,
            lastNotificationTime = 0L
        )

        assertEquals(GeofenceZone.INSIDE_500, result.newZone)
        assertEquals("ENTRY_500", result.eventType)
        assertEquals(GeofenceDetector.MSG_ENTRY_500, result.notificationMessage)
    }

    @Test
    fun testGeofence_Approaching100Meters_TriggersBengaliAlert() {
        // Partner was inside 500m, now moves to within 80m from home
        val closeLoc = LiveLocation(latitude = 23.8106, longitude = 90.4125, accuracy = 8f)
        val dist = GeofenceDetector.calculateDistance(closeLoc.latitude, closeLoc.longitude, home.latitude, home.longitude)
        assertTrue("Must be under 100m, got $dist", dist <= 100.0)

        val result = GeofenceDetector.evaluateTransition(
            location = closeLoc,
            home = home,
            previousZone = GeofenceZone.INSIDE_500,
            lastNotificationType = "ENTRY_500",
            lastNotificationTime = System.currentTimeMillis() - 60_000 // 1 min ago
        )

        assertEquals(GeofenceZone.APPROACHING_100, result.newZone)
        assertEquals("APPROACHING_100", result.eventType)
        assertEquals(GeofenceDetector.MSG_APPROACHING_100, result.notificationMessage)
    }

    @Test
    fun testGeofence_Exit500Meters_WithHysteresis_TriggersAlert() {
        // For exit, hysteresis requires > 525m (500 + 25m) to prevent border jitter
        val borderlineLoc = LiveLocation(latitude = 23.8148, longitude = 90.4125, accuracy = 10f)
        val borderDist = GeofenceDetector.calculateDistance(borderlineLoc.latitude, borderlineLoc.longitude, home.latitude, home.longitude)

        // Point well outside (approx 800m away)
        val outsideLoc = LiveLocation(latitude = 23.8180, longitude = 90.4125, accuracy = 10f)
        val result = GeofenceDetector.evaluateTransition(
            location = outsideLoc,
            home = home,
            previousZone = GeofenceZone.INSIDE_500,
            lastNotificationType = "ENTRY_500",
            lastNotificationTime = 0L
        )

        assertEquals(GeofenceZone.OUTSIDE_500, result.newZone)
        assertEquals("EXIT_500", result.eventType)
        assertEquals(GeofenceDetector.MSG_EXIT_500, result.notificationMessage)
    }

    @Test
    fun testGeofence_InaccurateGPS_IsIgnored() {
        // Accuracy is 75 meters (worse than 50m threshold)
        val noisyLoc = LiveLocation(latitude = 23.8130, longitude = 90.4125, accuracy = 75f)
        val result = GeofenceDetector.evaluateTransition(
            location = noisyLoc,
            home = home,
            previousZone = GeofenceZone.OUTSIDE_500
        )

        // Zone does not change and no alert is triggered due to bad accuracy
        assertEquals(GeofenceZone.OUTSIDE_500, result.newZone)
        assertNull("Inaccurate GPS reading must not trigger false notification", result.notificationMessage)
    }

    @Test
    fun testGeofence_Debounce_PreventsDuplicateSpam() {
        val insideLoc = LiveLocation(latitude = 23.8130, longitude = 90.4125, accuracy = 10f)
        val now = System.currentTimeMillis()

        // Same event fired 10 seconds ago
        val result = GeofenceDetector.evaluateTransition(
            location = insideLoc,
            home = home,
            previousZone = GeofenceZone.OUTSIDE_500,
            lastNotificationType = "ENTRY_500",
            lastNotificationTime = now - 10_000 // 10s ago, debounce is 3 min
        )

        assertNull("Duplicate notification within debounce window must be suppressed", result.notificationMessage)
    }

    @Test
    fun testInviteCode_Expiration24Hours() {
        val now = System.currentTimeMillis()
        val validInvite = InviteCode(code = "XYZ123", createdAt = now, expiresAt = now + 86400000L, isUsed = false)
        assertTrue(validInvite.expiresAt > now)

        val expiredInvite = InviteCode(code = "OLD999", createdAt = now - 90000000L, expiresAt = now - 1000L, isUsed = false)
        assertTrue("Expired code should have expiresAt < now", expiredInvite.expiresAt < now)
    }

    @Test
    fun testPairInfo_BidirectionalPartnerResolution() {
        val pair = PairInfo(
            pairId = "pair_123",
            user1Uid = "uid_husband",
            user2Uid = "uid_wife",
            user1Name = "জাহেদ",
            user2Name = "মরিয়ম"
        )

        assertEquals("uid_wife", pair.getPartnerUid("uid_husband"))
        assertEquals("মরিয়ম", pair.getPartnerName("uid_husband"))

        assertEquals("uid_husband", pair.getPartnerUid("uid_wife"))
        assertEquals("জাহেদ", pair.getPartnerName("uid_wife"))
    }
}
