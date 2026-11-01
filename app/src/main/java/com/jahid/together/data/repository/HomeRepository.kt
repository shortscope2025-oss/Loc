package com.jahid.together.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jahid.together.data.model.HomeLocation
import kotlinx.coroutines.tasks.await

class HomeRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun setHomeLocation(pairId: String, homeLocation: HomeLocation): Result<Unit> {
        if (pairId.isBlank()) return Result.failure(Exception("যুগল আইডি পাওয়া যায়নি। আগে সঙ্গীর সাথে যুক্ত হোন।"))
        if (!homeLocation.isValid()) return Result.failure(Exception("সঠিক বাড়ির অবস্থান নির্বাচন করুন।"))

        return try {
            firestore.collection("pairs").document(pairId)
                .set(mapOf("homeLocation" to homeLocation), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetHomeLocation(pairId: String): Result<Unit> {
        if (pairId.isBlank()) return Result.failure(Exception("যুগল আইডি পাওয়া যায়নি"))
        return try {
            firestore.collection("pairs").document(pairId)
                .update("homeLocation", null)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
