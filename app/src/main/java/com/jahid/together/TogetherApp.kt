package com.jahid.together

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class TogetherApp : Application() {

    companion object {
        const val CHANNEL_LOCATION_ID = "together_location_channel"
        const val CHANNEL_GEOFENCE_ID = "together_geofence_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Foreground Location Service Channel (Low importance to avoid sound)
            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION_ID,
                getString(R.string.channel_location_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_location_desc)
                setShowBadge(false)
            }

            // Geofence & Partner Alerts Channel (High importance with sound)
            val geofenceChannel = NotificationChannel(
                CHANNEL_GEOFENCE_ID,
                getString(R.string.channel_geofence_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_geofence_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(locationChannel)
            notificationManager.createNotificationChannel(geofenceChannel)
        }
    }
}
