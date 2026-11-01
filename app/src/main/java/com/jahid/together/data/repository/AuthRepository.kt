package com.jahid.together.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jahid.together.data.model.UserProfile
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun register(email: String, pass: String, displayName: String): Result<UserProfile> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw Exception("ব্যবহারকারী তৈরি ব্যর্থ হয়েছে।")
            val profile = UserProfile(
                uid = user.uid,
                email = user.email ?: email,
                displayName = displayName.ifBlank { "আমার প্রিয়" },
                photoBase64 = "",
                pairId = "",
                partnerUid = "",
                isSharingLocation = true,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("users").document(user.uid).set(profile).await()
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, pass: String): Result<UserProfile> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw Exception("লগইন ব্যর্থ হয়েছে।")
            val doc = firestore.collection("users").document(user.uid).get().await()
            val profile = doc.toObject(UserProfile::class.java) ?: UserProfile(
                uid = user.uid,
                email = user.email ?: email,
                displayName = user.displayName ?: "আমার প্রিয়"
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String): Result<UserProfile?> {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            Result.success(doc.toObject(UserProfile::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(name: String, bitmap: Bitmap?): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("ব্যবহারকারী লগইন নেই"))
        return try {
            val updates = mutableMapOf<String, Any>("displayName" to name)
            if (bitmap != null) {
                // Resize & Compress bitmap to keep under 50KB for safe Firestore document storage
                val scaled = resizeBitmap(bitmap, 256)
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                val bytes = stream.toByteArray()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                updates["photoBase64"] = base64
            }
            firestore.collection("users").document(uid).set(updates, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFcmToken(token: String): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Not logged in"))
        return try {
            firestore.collection("users").document(uid)
                .update("fcmToken", token).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleLocationSharing(enabled: Boolean): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Not logged in"))
        return try {
            firestore.collection("users").document(uid)
                .update("isSharingLocation", enabled).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun deleteAccount(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("লগইন নেই"))
        val uid = user.uid
        return try {
            // Delete user doc
            firestore.collection("users").document(uid).delete().await()
            // Delete auth user
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val targetW: Int
        val targetH: Int
        if (width > height) {
            targetW = maxDim
            targetH = (maxDim / ratio).toInt()
        } else {
            targetH = maxDim
            targetW = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
