package com.jahid.together.service

import com.jahid.together.data.model.GeofenceZone
import com.jahid.together.data.model.HomeLocation
import com.jahid.together.data.model.LiveLocation
import kotlin.math.*

object GeofenceDetector {

    const val MAX_VALID_ACCURACY_METERS = 50.0f
    const val GEOFENCE_OUTER_RADIUS = 500.0 // meters
    const val GEOFENCE_INNER_RADIUS = 100.0 // meters
    const val HYSTERESIS_BUFFER = 25.0 // meters to prevent border fluttering
    const val MIN_ALERT_INTERVAL_MS = 180_000L // 3 minutes debounce for same event

    // Bengali Notification Messages
    const val MSG_ENTRY_500 = "আপনার কলিজা আসতেছে ❤️ গেট খুলে রাখো।"
    const val MSG_APPROACHING_100 = "গেট খুলে রাখো, আর মাত্র ১০০ মিটার দূরে!"
    const val MSG_EXIT_500 = "আপনার কলিজা চলে গেছে ❤️ গেট লাগিয়ে রাখো।"

    /**
     * Calculates distance in meters between two lat/lng coordinates using Haversine formula
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Determines current zone with hysteresis taken into account
     */
    fun determineZone(
        distance: Double,
        previousZone: GeofenceZone
    ): GeofenceZone {
        return when (previousZone) {
            GeofenceZone.OUTSIDE_500 -> {
                // To enter 500m zone, distance must strictly be <= 500m
                if (distance <= GEOFENCE_INNER_RADIUS) {
                    GeofenceZone.APPROACHING_100
                } else if (distance <= GEOFENCE_OUTER_RADIUS) {
                    GeofenceZone.INSIDE_500
                } else {
                    GeofenceZone.OUTSIDE_500
                }
            }
            GeofenceZone.INSIDE_500 -> {
                if (distance <= GEOFENCE_INNER_RADIUS) {
                    GeofenceZone.APPROACHING_100
                } else if (distance > (GEOFENCE_OUTER_RADIUS + HYSTERESIS_BUFFER)) {
                    // Must exceed 525m to trigger exit
                    GeofenceZone.OUTSIDE_500
                } else {
                    GeofenceZone.INSIDE_500
                }
            }
            GeofenceZone.APPROACHING_100 -> {
                if (distance > (GEOFENCE_OUTER_RADIUS + HYSTERESIS_BUFFER)) {
                    GeofenceZone.OUTSIDE_500
                } else if (distance > (GEOFENCE_INNER_RADIUS + HYSTERESIS_BUFFER)) {
                    GeofenceZone.INSIDE_500
                } else {
                    GeofenceZone.APPROACHING_100
                }
            }
            GeofenceZone.UNKNOWN -> {
                if (distance <= GEOFENCE_INNER_RADIUS) {
                    GeofenceZone.APPROACHING_100
                } else if (distance <= GEOFENCE_OUTER_RADIUS) {
                    GeofenceZone.INSIDE_500
                } else {
                    GeofenceZone.OUTSIDE_500
                }
            }
        }
    }

    data class TransitionResult(
        val newZone: GeofenceZone,
        val notificationMessage: String?,
        val eventType: String?,
        val distanceToHomeMeters: Double
    )

    /**
     * Evaluates a location update against the home geofence.
     * Returns a TransitionResult containing notification text if a meaningful boundary was crossed.
     */
    fun evaluateTransition(
        location: LiveLocation,
        home: HomeLocation,
        previousZone: GeofenceZone,
        lastNotificationType: String? = null,
        lastNotificationTime: Long = 0L
    ): TransitionResult {
        if (!location.isValid() || !home.isValid()) {
            return TransitionResult(previousZone, null, null, 0.0)
        }

        // Validate GPS Accuracy
        if (location.accuracy > MAX_VALID_ACCURACY_METERS) {
            val dist = calculateDistance(location.latitude, location.longitude, home.latitude, home.longitude)
            return TransitionResult(previousZone, null, null, dist)
        }

        val distance = calculateDistance(
            location.latitude, location.longitude,
            home.latitude, home.longitude
        )

        val newZone = determineZone(distance, previousZone)
        val now = System.currentTimeMillis()
        val debounceElapsed = (now - lastNotificationTime) > MIN_ALERT_INTERVAL_MS

        var message: String? = null
        var eventType: String? = null

        // Evaluate transitions:
        if (previousZone == GeofenceZone.OUTSIDE_500 && newZone == GeofenceZone.INSIDE_500) {
            if (debounceElapsed || lastNotificationType != "ENTRY_500") {
                message = MSG_ENTRY_500
                eventType = "ENTRY_500"
            }
        } else if ((previousZone == GeofenceZone.INSIDE_500 || previousZone == GeofenceZone.OUTSIDE_500)
            && newZone == GeofenceZone.APPROACHING_100) {
            if (debounceElapsed || lastNotificationType != "APPROACHING_100") {
                message = MSG_APPROACHING_100
                eventType = "APPROACHING_100"
            }
        } else if ((previousZone == GeofenceZone.INSIDE_500 || previousZone == GeofenceZone.APPROACHING_100)
            && newZone == GeofenceZone.OUTSIDE_500) {
            if (debounceElapsed || lastNotificationType != "EXIT_500") {
                message = MSG_EXIT_500
                eventType = "EXIT_500"
            }
        }

        return TransitionResult(
            newZone = newZone,
            notificationMessage = message,
            eventType = eventType,
            distanceToHomeMeters = distance
        )
    }
}
