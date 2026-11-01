package com.jahid.together.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jahid.together.data.model.AppNotification
import com.jahid.together.data.model.LiveLocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LocationRepository(
    private val rtdb: FirebaseDatabase = FirebaseDatabase.getInstance("https://together-cf667-default-rtdb.firebaseio.com"),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun publishLocation(pairId: String, userId: String, location: LiveLocation): Result<Unit> {
        if (pairId.isBlank() || userId.isBlank() || !location.isValid()) {
            return Result.failure(Exception("অবস্থান তথ্য সঠিক নয়"))
        }

        return try {
            // Write to Realtime Database for ultra-low latency & zero-cost free Spark tier
            val ref = rtdb.getReference("pairs/$pairId/locations/$userId")
            ref.setValue(location).await()

            // Also update mirror in Firestore for redundancy
            firestore.collection("pairs").document(pairId)
                .collection("locations").document(userId)
                .set(location).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePartnerLocation(pairId: String, partnerUid: String): Flow<LiveLocation?> = callbackFlow {
        if (pairId.isBlank() || partnerUid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val ref = rtdb.getReference("pairs/$pairId/locations/$partnerUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val location = snapshot.getValue(LiveLocation::class.java)
                    trySend(location)
                } else {
                    // Try fallback to Firestore mirror if RTDB is empty
                    firestore.collection("pairs").document(pairId)
                        .collection("locations").document(partnerUid)
                        .get().addOnSuccessListener { doc ->
                            trySend(doc.toObject(LiveLocation::class.java))
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // If RTDB listener error, fallback to Firestore
                firestore.collection("pairs").document(pairId)
                    .collection("locations").document(partnerUid)
                    .get().addOnSuccessListener { doc ->
                        trySend(doc.toObject(LiveLocation::class.java))
                    }
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendAppNotification(targetUid: String, notification: AppNotification): Result<Unit> {
        if (targetUid.isBlank()) return Result.failure(Exception("টার্গেট ইউজার পাওয়া যায়নি"))
        return try {
            firestore.collection("users").document(targetUid)
                .collection("notifications").add(notification).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeNotifications(userId: String): Flow<List<AppNotification>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(userId)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(AppNotification::class.java) }
                    trySend(list)
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose { listener.remove() }
    }

    companion object {
        fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
            return try {
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                    context.registerReceiver(null, filter)
                }
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 100

                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                Pair(batteryPct, isCharging)
            } catch (e: Exception) {
                Pair(100, false)
            }
        }
    }
}
