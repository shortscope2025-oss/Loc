package com.jahid.together.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jahid.together.data.model.InviteCode
import com.jahid.together.data.model.PairInfo
import com.jahid.together.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class PairRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val random = SecureRandom()
    private val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Exclude confusing chars 0,1,I,O

    private fun generateRandomCode(length: Int = 6): String {
        return (1..length)
            .map { codeChars[random.nextInt(codeChars.length)] }
            .joinToString("")
    }

    suspend fun createInviteCode(currentUser: UserProfile): Result<InviteCode> {
        return try {
            if (currentUser.pairId.isNotBlank()) {
                return Result.failure(Exception("আপনি ইতিমধ্যে আপনার সঙ্গীর সাথে যুক্ত আছেন। নতুন কোড তৈরি করা যাবে না।"))
            }

            val code = generateRandomCode()
            val invite = InviteCode(
                code = code,
                creatorUid = currentUser.uid,
                creatorEmail = currentUser.email,
                creatorName = currentUser.displayName,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000L), // 24 hours
                isUsed = false
            )

            firestore.collection("invite_codes").document(code).set(invite).await()
            Result.success(invite)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinWithInviteCode(currentUser: UserProfile, inputCode: String): Result<PairInfo> {
        val cleanCode = inputCode.trim().uppercase()
        if (cleanCode.length != 6) {
            return Result.failure(Exception("আমন্ত্রণ কোড অবশ্যই ৬ অক্ষরের হতে হবে।"))
        }

        if (currentUser.pairId.isNotBlank()) {
            return Result.failure(Exception("আপনি ইতিমধ্যে অন্য অ্যাকাউন্টের সাথে যুক্ত আছেন।"))
        }

        return try {
            val inviteDoc = firestore.collection("invite_codes").document(cleanCode).get().await()
            if (!inviteDoc.exists()) {
                return Result.failure(Exception("আমন্ত্রণ কোডটি সঠিক নয় বা পাওয়া যায়নি।"))
            }

            val invite = inviteDoc.toObject(InviteCode::class.java)
                ?: return Result.failure(Exception("কোডের তথ্য প্রক্রিয়া করা যায়নি।"))

            val now = System.currentTimeMillis()
            if (invite.isUsed) {
                return Result.failure(Exception("এই কোডটি ইতিমধ্যে ব্যবহার করা হয়েছে।"))
            }
            if (invite.expiresAt < now) {
                return Result.failure(Exception("কোডটির মেয়াদের ২৪ ঘণ্টা শেষ হয়ে গেছে। নতুন কোড নিন।"))
            }
            if (invite.creatorUid == currentUser.uid) {
                return Result.failure(Exception("নিজের তৈরি কোডে নিজে যুক্ত হওয়া যাবে না। এটি আপনার সঙ্গীকে পাঠান।"))
            }

            val partnerDoc = firestore.collection("users").document(invite.creatorUid).get().await()
            val partner = partnerDoc.toObject(UserProfile::class.java)
                ?: return Result.failure(Exception("সঙ্গীর অ্যাকাউন্ট খুঁজে পাওয়া যায়নি।"))

            if (partner.pairId.isNotBlank()) {
                return Result.failure(Exception("সঙ্গী ইতিমধ্যে অন্য কারও সাথে যুক্ত আছেন।"))
            }

            // Create new Pair
            val pairRef = firestore.collection("pairs").document()
            val pairId = pairRef.id
            val pairInfo = PairInfo(
                pairId = pairId,
                user1Uid = invite.creatorUid,
                user2Uid = currentUser.uid,
                user1Name = partner.displayName,
                user2Name = currentUser.displayName,
                homeLocation = null,
                isActive = true,
                createdAt = now
            )

            // Batch write
            val batch = firestore.batch()
            batch.set(pairRef, pairInfo)
            batch.update(firestore.collection("invite_codes").document(cleanCode), "isUsed", true)

            // Update user 1
            batch.update(
                firestore.collection("users").document(invite.creatorUid),
                mapOf("pairId" to pairId, "partnerUid" to currentUser.uid)
            )

            // Update user 2
            batch.update(
                firestore.collection("users").document(currentUser.uid),
                mapOf("pairId" to pairId, "partnerUid" to invite.creatorUid)
            )

            batch.commit().await()
            Result.success(pairInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePairInfo(pairId: String): Flow<PairInfo?> = callbackFlow {
        if (pairId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("pairs").document(pairId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val pair = snapshot.toObject(PairInfo::class.java)
                    trySend(pair)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    fun observePartnerProfile(partnerUid: String): Flow<UserProfile?> = callbackFlow {
        if (partnerUid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(partnerUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toObject(UserProfile::class.java)
                    trySend(profile)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun unpair(pairId: String, currentUid: String, partnerUid: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            if (pairId.isNotBlank()) {
                batch.update(firestore.collection("pairs").document(pairId), "isActive", false)
            }
            if (currentUid.isNotBlank()) {
                batch.update(
                    firestore.collection("users").document(currentUid),
                    mapOf("pairId" to "", "partnerUid" to "")
                )
            }
            if (partnerUid.isNotBlank()) {
                batch.update(
                    firestore.collection("users").document(partnerUid),
                    mapOf("pairId" to "", "partnerUid" to "")
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
