package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CustomCategory
import com.example.data.model.SquadCheckin
import com.example.data.model.SquadPool
import com.example.data.model.StakeCommitment
import com.example.data.model.UserProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ActakeRepository(private val db: FirebaseFirestore) : ActakeDataSource {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing cloud sync.")
    }

    override fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override fun observeStakes(): Flow<List<StakeCommitment>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/stakes"
        emitAll(
            db.collection("users").document(uid).collection("stakes")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StakeCommitment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeCompletedStakes(): Flow<List<StakeCommitment>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/stakes"
        emitAll(
            db.collection("users").document(uid).collection("stakes")
                .whereEqualTo("status", "COMPLETED")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StakeCommitment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeCustomCategories(): Flow<List<CustomCategory>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/categories"
        emitAll(
            db.collection("users").document(uid).collection("categories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomCategory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeSquads(): Flow<List<SquadPool>> = flow {
        val uid = requireUserId()
        val path = "squads"
        emitAll(
            db.collection("squads")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SquadPool::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeSquadCheckins(squadId: String): Flow<List<SquadCheckin>> = flow {
        val uid = requireUserId()
        if (squadId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "squads/$squadId/checkins"
        emitAll(
            db.collection("squads").document(squadId).collection("checkins")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SquadCheckin::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun ensureStarterProfileAndData(initialWalletAddress: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userDocRef = db.collection("users").document(uid)
            docPath = userDocRef.path
            val existing = userDocRef.get().await()
            val displayName = (auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Seeker")
                .take(60)
            val safeWallet = initialWalletAddress.trim().take(64)

            if (!existing.exists()) {
                val profilePayload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "walletAddress" to safeWallet,
                    "streakCount" to 1,
                    "actPoints" to 150,
                    "recentVerifiedObjects" to emptyList<String>(),
                    "usdcBalance" to 150.0,
                    "skrBalance" to 500.0,
                    "lockedBalance" to 15.0,
                    "weekBitmap" to (1 shl currentDayBitIndex()),
                    "alarmSound" to "Siren",
                    "strictnessEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(profilePayload).await()

                val stakeRef = userDocRef.collection("stakes").document()
                docPath = stakeRef.path
                stakeRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to "Morning Wakeup",
                        "categoryId" to "WAKEUP",
                        "categoryContextHint" to "Everyday morning hygiene and kitchen objects such as a ceramic coffee mug, toothbrush, sink faucet, or curtains",
                        "missionType" to "OBJECT_SNAP",
                        "mode" to "SOLO",
                        "targetPrompt" to "AI-randomized morning hygiene or kitchen item",
                        "stakeAmount" to 15.0,
                        "tokenSymbol" to "USDC",
                        "skrBoosted" to false,
                        "wakeTimeLabel" to "06:00 AM",
                        "wakeHour" to 6,
                        "wakeMinute" to 0,
                        "durationDays" to 7,
                        "completedDays" to 1,
                        "alarmSound" to "Siren",
                        "status" to "ACTIVE",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                val squadRef = db.collection("squads").document()
                docPath = squadRef.path
                squadRef.set(
                    mapOf(
                        "creatorId" to uid,
                        "name" to "7AM Campus Wake Club",
                        "wakeTimeLabel" to "07:00 AM",
                        "missionTitle" to "Morning Wakeup",
                        "stakeAmount" to 10.0,
                        "tokenSymbol" to "USDC",
                        "potAmount" to 10.0,
                        "slashedAmount" to 0.0,
                        "participantCount" to 1,
                        "checkedInCount" to 0,
                        "memberUids" to listOf(uid),
                        "joinable" to true,
                        "status" to "ACTIVE",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (safeWallet.isNotBlank() && existing.getString("walletAddress").isNullOrBlank()) {
                userDocRef.update(
                    mapOf(
                        "walletAddress" to safeWallet,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun createCustomCategory(
        name: String,
        contextHint: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories"
        return try {
            val uid = requireUserId()
            val safeName = name.trim().ifBlank { "Custom Habit" }.take(60)
            val safeHint = contextHint.trim().ifBlank { "Everyday physical items from $safeName" }.take(180)
            val catRef = db.collection("users").document(uid).collection("categories").document()
            docPath = catRef.path
            catRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to safeName,
                    "contextHint" to safeHint,
                    "iconKey" to "custom",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(catRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun createStake(
        title: String,
        categoryId: String,
        categoryContextHint: String,
        missionType: String,
        mode: String,
        targetPrompt: String,
        stakeAmount: Double,
        tokenSymbol: String,
        wakeHour: Int,
        wakeMinute: Int,
        durationDays: Int,
        alarmSound: String,
        squadId: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/stakes"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().ifBlank { "Morning Wakeup" }.take(80)
            val safeCategoryId = categoryId.trim().ifBlank { "WAKEUP" }.take(64)
            val safeHint = categoryContextHint.trim().take(200)
            val safePrompt = targetPrompt.trim().ifBlank { "AI-randomized item for $safeTitle" }.take(160)
            val safeAmount = stakeAmount.coerceIn(1.0, 1000.0)
            val safeToken = if (tokenSymbol == "SKR") "SKR" else "USDC"
            val safeHour = wakeHour.coerceIn(0, 23)
            val safeMinute = wakeMinute.coerceIn(0, 59)
            val safeDays = durationDays.coerceIn(1, 60)
            val wakeLabel = formatWakeTime(safeHour, safeMinute)

            val stakeRef = db.collection("users").document(uid).collection("stakes").document()
            docPath = stakeRef.path

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "title" to safeTitle,
                "categoryId" to safeCategoryId,
                "categoryContextHint" to safeHint,
                "missionType" to missionType,
                "mode" to mode,
                "targetPrompt" to safePrompt,
                "stakeAmount" to safeAmount,
                "tokenSymbol" to safeToken,
                "skrBoosted" to (safeToken == "SKR"),
                "wakeTimeLabel" to wakeLabel,
                "wakeHour" to safeHour,
                "wakeMinute" to safeMinute,
                "durationDays" to safeDays,
                "completedDays" to 0,
                "alarmSound" to alarmSound,
                "status" to "ACTIVE",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (squadId.isNotBlank()) {
                payload["squadId"] = squadId.take(128)
            }

            stakeRef.set(payload).await()

            val userRef = db.collection("users").document(uid)
            docPath = userRef.path
            userRef.update(
                mapOf(
                    "lockedBalance" to FieldValue.increment(safeAmount),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(stakeRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateAlarmPreferences(
        alarmSound: String,
        strictnessEnabled: Boolean,
        activeStakeId: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            docPath = userRef.path
            userRef.update(
                mapOf(
                    "alarmSound" to alarmSound,
                    "strictnessEnabled" to strictnessEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (!activeStakeId.isNullOrBlank()) {
                val stakeRef = userRef.collection("stakes").document(activeStakeId)
                docPath = stakeRef.path
                stakeRef.update(
                    mapOf(
                        "alarmSound" to alarmSound,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateWalletAddress(walletAddress: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleaned = walletAddress.trim().take(64)
            val userRef = db.collection("users").document(uid)
            docPath = userRef.path
            userRef.update(
                mapOf(
                    "walletAddress" to cleaned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun recordVerifiedCheckin(
        profile: UserProfile,
        stake: StakeCommitment?,
        squad: SquadPool?,
        missionTitle: String,
        verifiedObjectLabel: String,
        confidence: Double,
        reason: String,
        proofHashHex: String,
        oracleSignatureHex: String,
        payoutBonus: Double,
        pointsAwarded: Int
    ): Result<Pair<Int, Int>> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val newStreak = (profile.streakCount + 1).coerceAtMost(10000)
            val newPoints = (profile.actPoints + pointsAwarded).coerceIn(0, 10_000_000)
            val newRecentObjects = (profile.recentVerifiedObjects + verifiedObjectLabel.trim().take(60))
                .filter { it.isNotBlank() }
                .takeLast(7)
            val newBitmap = (profile.weekBitmap or (1 shl currentDayBitIndex())) and 127
            val stakeReturned = stake?.stakeAmount ?: 15.0
            val newLocked = (profile.lockedBalance - stakeReturned).coerceAtLeast(0.0)
            val newUsdc = if ((stake?.tokenSymbol ?: "USDC") == "USDC") {
                (profile.usdcBalance + payoutBonus).coerceAtMost(1000000.0)
            } else {
                profile.usdcBalance
            }
            val newSkr = if (stake?.tokenSymbol == "SKR") {
                (profile.skrBalance + payoutBonus).coerceAtMost(1000000.0)
            } else {
                profile.skrBalance
            }

            val userRef = db.collection("users").document(uid)
            docPath = userRef.path
            userRef.update(
                mapOf(
                    "streakCount" to newStreak,
                    "actPoints" to newPoints,
                    "recentVerifiedObjects" to newRecentObjects,
                    "weekBitmap" to newBitmap,
                    "lockedBalance" to newLocked,
                    "usdcBalance" to newUsdc,
                    "skrBalance" to newSkr,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (stake != null && stake.id.isNotBlank() && stake.status == "ACTIVE") {
                val stakeRef = userRef.collection("stakes").document(stake.id)
                docPath = stakeRef.path
                val nextCompleted = (stake.completedDays + 1).coerceAtMost(stake.durationDays)
                val nextStatus = if (nextCompleted >= stake.durationDays) "COMPLETED" else "ACTIVE"
                val totalEarned = (stake.earnedAmount + stakeReturned + payoutBonus)
                    .coerceIn(0.0, 100000.0)
                stakeRef.update(
                    mapOf(
                        "completedDays" to nextCompleted,
                        "status" to nextStatus,
                        "proofHash" to proofHashHex.take(128),
                        "oracleSignature" to oracleSignatureHex.take(256),
                        "earnedAmount" to totalEarned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (squad != null && squad.id.isNotBlank() && uid in squad.memberUids) {
                val checkinRef = db.collection("squads").document(squad.id)
                    .collection("checkins").document()
                docPath = checkinRef.path
                val timeLabel = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
                checkinRef.set(
                    mapOf(
                        "squadId" to squad.id,
                        "userId" to uid,
                        "memberUids" to squad.memberUids,
                        "userName" to profile.displayName.ifBlank { "Seeker" }.take(60),
                        "missionTitle" to "$missionTitle ($verifiedObjectLabel)".take(80),
                        "verifiedTimeLabel" to timeLabel,
                        "streakCount" to newStreak,
                        "status" to "VERIFIED",
                        "aiConfidence" to confidence.coerceIn(0.0, 1.0),
                        "aiReason" to reason.take(240),
                        "proofHash" to proofHashHex.ifBlank { "sha256_verified" }.take(128),
                        "oracleSignature" to oracleSignatureHex.ifBlank { "ed25519_attested" }.take(256),
                        "payoutEarned" to (stakeReturned + payoutBonus).coerceIn(0.0, 10000.0),
                        "thumbsCount" to 0,
                        "fireCount" to 0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                if (squad.status == "ACTIVE") {
                    val squadRef = db.collection("squads").document(squad.id)
                    docPath = squadRef.path
                    val nextCheckedIn = (squad.checkedInCount + 1).coerceAtMost(squad.participantCount)
                    squadRef.update(
                        mapOf(
                            "checkedInCount" to nextCheckedIn,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(newStreak to newBitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun createSquad(
        name: String,
        wakeTimeLabel: String,
        missionTitle: String,
        stakeAmount: Double,
        tokenSymbol: String
    ): Result<String> {
        var docPath = "squads"
        return try {
            val uid = requireUserId()
            val safeName = name.trim().ifBlank { "Morning Accountability Squad" }.take(80)
            val safeWake = wakeTimeLabel.trim().ifBlank { "06:30 AM" }.take(16)
            val safeMission = missionTitle.trim().ifBlank { "Morning Wakeup" }.take(80)
            val safeAmount = stakeAmount.coerceIn(1.0, 1000.0)
            val safeToken = if (tokenSymbol == "SKR") "SKR" else "USDC"

            val squadRef = db.collection("squads").document()
            docPath = squadRef.path
            squadRef.set(
                mapOf(
                    "creatorId" to uid,
                    "name" to safeName,
                    "wakeTimeLabel" to safeWake,
                    "missionTitle" to safeMission,
                    "stakeAmount" to safeAmount,
                    "tokenSymbol" to safeToken,
                    "potAmount" to safeAmount,
                    "slashedAmount" to 0.0,
                    "participantCount" to 1,
                    "checkedInCount" to 0,
                    "memberUids" to listOf(uid),
                    "joinable" to true,
                    "status" to "ACTIVE",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(squadRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun joinSquadById(rawSquadIdOrBlinkUrl: String): Result<SquadPool> {
        val cleanedId = rawSquadIdOrBlinkUrl.trim()
            .substringAfterLast("/")
            .substringBefore("?")
            .trim()
        var docPath = "squads/$cleanedId"
        return try {
            val uid = requireUserId()
            require(cleanedId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                "Enter a valid Squad Pool ID or Blink invite link."
            }
            val squadRef = db.collection("squads").document(cleanedId)
            docPath = squadRef.path
            val snapshot = squadRef.get().await()
            if (!snapshot.exists()) {
                throw IllegalArgumentException("Squad pool '$cleanedId' was not found.")
            }
            val squad = snapshot.toObject(
                SquadPool::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw IllegalStateException("Could not read squad pool.")

            if (uid !in squad.memberUids) {
                require(squad.joinable && squad.status == "ACTIVE") {
                    "This squad pool is no longer accepting new members."
                }
                val updatedMembers = squad.memberUids + uid
                squadRef.update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "participantCount" to (squad.participantCount + 1),
                        "potAmount" to (squad.potAmount + squad.stakeAmount),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(squad)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun reactToCheckin(
        squadId: String,
        checkinId: String,
        isFire: Boolean
    ): Result<Unit> {
        var docPath = "squads/$squadId/checkins/$checkinId"
        return try {
            requireUserId()
            val ref = db.collection("squads").document(squadId)
                .collection("checkins").document(checkinId)
            docPath = ref.path
            val field = if (isFire) "fireCount" else "thumbsCount"
            ref.update(
                mapOf(
                    field to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        fun formatWakeTime(hour24: Int, minute: Int): String {
            val amPm = if (hour24 >= 12) "PM" else "AM"
            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            return String.format(Locale.US, "%02d:%02d %s", hour12, minute, amPm)
        }

        fun currentDayBitIndex(): Int {
            return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
        }
    }
}
