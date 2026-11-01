package com.jahid.together.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoBase64: String = "",
    val pairId: String = "",
    val partnerUid: String = "",
    val fcmToken: String = "",
    val isSharingLocation: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class HomeLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "আমাদের প্রিয় বাড়ি",
    val radiusMeters: Double = 500.0,
    val setByUid: String = "",
    val setAt: Long = System.currentTimeMillis()
) {
    fun isValid(): Boolean = latitude != 0.0 && longitude != 0.0
}

@IgnoreExtraProperties
data class PairInfo(
    @DocumentId
    val pairId: String = "",
    val user1Uid: String = "",
    val user2Uid: String = "",
    val user1Name: String = "",
    val user2Name: String = "",
    val homeLocation: HomeLocation? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getPartnerUid(currentUid: String): String {
        return if (currentUid == user1Uid) user2Uid else user1Uid
    }
    fun getPartnerName(currentUid: String): String {
        return if (currentUid == user1Uid) user2Name else user1Name
    }
}

@IgnoreExtraProperties
data class LiveLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val altitude: Double = 0.0,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isOnline: Boolean = true,
    val isMoving: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isValid(): Boolean = latitude != 0.0 && longitude != 0.0
}

enum class GeofenceZone {
    OUTSIDE_500,
    INSIDE_500,
    APPROACHING_100,
    UNKNOWN
}

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "GEOFENCE", // ENTRY_500, APPROACHING_100, EXIT_500, UNPAIR, SYSTEM
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@IgnoreExtraProperties
data class InviteCode(
    @DocumentId
    val code: String = "",
    val creatorUid: String = "",
    val creatorEmail: String = "",
    val creatorName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 86400000L, // 24 hours
    val isUsed: Boolean = false
)
