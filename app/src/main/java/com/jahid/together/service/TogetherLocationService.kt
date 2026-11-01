package com.jahid.together.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jahid.together.MainActivity
import com.jahid.together.R
import com.jahid.together.TogetherApp
import com.jahid.together.data.model.AppNotification
import com.jahid.together.data.model.GeofenceZone
import com.jahid.together.data.model.HomeLocation
import com.jahid.together.data.model.LiveLocation
import com.jahid.together.data.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TogetherLocationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRepository: LocationRepository

    private var pairId: String = ""
    private var userId: String = ""
    private var partnerId: String = ""
    private var homeLat: Double = 0.0
    private var homeLon: Double = 0.0

    private var previousZone: GeofenceZone = GeofenceZone.UNKNOWN
    private var lastNotificationType: String? = null
    private var lastNotificationTime: Long = 0L

    private var isTracking = false
    private var locationCallback: LocationCallback? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"

        const val EXTRA_PAIR_ID = "EXTRA_PAIR_ID"
        const val EXTRA_USER_ID = "EXTRA_USER_ID"
        const val EXTRA_PARTNER_ID = "EXTRA_PARTNER_ID"
        const val EXTRA_HOME_LAT = "EXTRA_HOME_LAT"
        const val EXTRA_HOME_LON = "EXTRA_HOME_LON"

        fun startService(
            context: Context,
            pairId: String,
            userId: String,
            partnerId: String,
            homeLocation: HomeLocation? = null
        ) {
            val intent = Intent(context, TogetherLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PAIR_ID, pairId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_PARTNER_ID, partnerId)
                homeLocation?.let {
                    putExtra(EXTRA_HOME_LAT, it.latitude)
                    putExtra(EXTRA_HOME_LON, it.longitude)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TogetherLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRepository = LocationRepository()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                pairId = intent.getStringExtra(EXTRA_PAIR_ID) ?: ""
                userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
                partnerId = intent.getStringExtra(EXTRA_PARTNER_ID) ?: ""
                homeLat = intent.getDoubleExtra(EXTRA_HOME_LAT, 0.0)
                homeLon = intent.getDoubleExtra(EXTRA_HOME_LON, 0.0)

                startForegroundTracking()
            }
            ACTION_STOP -> {
                stopForegroundTracking()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, TogetherApp.CHANNEL_LOCATION_ID)
            .setContentTitle(getString(R.string.location_service_notification_title))
            .setContentText(getString(R.string.location_service_notification_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startForegroundTracking() {
        if (isTracking) return
        isTracking = true

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        requestAdaptiveLocationUpdates(isMoving = false)
    }

    @SuppressLint("MissingPermission")
    private fun requestAdaptiveLocationUpdates(isMoving: Boolean) {
        // Remove existing callback if changing interval
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val intervalMs = if (isMoving) 15_000L else 60_000L // 15s moving, 60s stationary
        val minIntervalMs = if (isMoving) 10_000L else 30_000L

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setMinUpdateDistanceMeters(if (isMoving) 10f else 25f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    handleLocationUpdate(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun handleLocationUpdate(loc: Location) {
        serviceScope.launch {
            val (batteryPct, isCharging) = LocationRepository.getBatteryInfo(this@TogetherLocationService)
            val speed = if (loc.hasSpeed()) loc.speed else 0f
            val isMoving = speed > 1.2f // ~4.3 km/h

            val liveLoc = LiveLocation(
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                speed = speed,
                altitude = loc.altitude,
                batteryLevel = batteryPct,
                isCharging = isCharging,
                isOnline = true,
                isMoving = isMoving,
                timestamp = System.currentTimeMillis()
            )

            // Publish to Firebase
            if (pairId.isNotBlank() && userId.isNotBlank()) {
                locationRepository.publishLocation(pairId, userId, liveLoc)
            }

            // Check Geofence if home is set
            if (homeLat != 0.0 && homeLon != 0.0 && partnerId.isNotBlank()) {
                val home = HomeLocation(latitude = homeLat, longitude = homeLon)
                val transition = GeofenceDetector.evaluateTransition(
                    location = liveLoc,
                    home = home,
                    previousZone = previousZone,
                    lastNotificationType = lastNotificationType,
                    lastNotificationTime = lastNotificationTime
                )

                previousZone = transition.newZone

                // If a boundary alert should be sent to the partner:
                if (transition.notificationMessage != null && transition.eventType != null) {
                    lastNotificationType = transition.eventType
                    lastNotificationTime = System.currentTimeMillis()

                    val appNotification = AppNotification(
                        title = "Together ❤️ গৃহ নিরাপত্তা",
                        body = transition.notificationMessage,
                        type = transition.eventType,
                        timestamp = System.currentTimeMillis()
                    )

                    // Store in partner's notification list
                    locationRepository.sendAppNotification(partnerId, appNotification)
                }
            }
        }
    }

    private fun stopForegroundTracking() {
        isTracking = false
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopForegroundTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
