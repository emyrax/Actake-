package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.CustomCategory
import com.example.data.model.SquadCheckin
import com.example.data.model.SquadPool
import com.example.data.model.StakeCommitment
import com.example.data.model.UserProfile
import com.example.solana.SolanaSeekerWalletManager
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Device-local persistence store used when a user runs Actake natively via Solana Seeker Wallet (SIWS)
 * without Google Sign-In. Makes ZERO Firestore calls while persisting profile, $ACT points,
 * active & completed stakes, custom categories, squads, and verified Vision AI check-ins locally.
 */
class LocalActakeStore(context: Context) : ActakeDataSource {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val profileFlow = MutableStateFlow<UserProfile?>(null)
    private val stakesFlow = MutableStateFlow<List<StakeCommitment>>(emptyList())
    private val categoriesFlow = MutableStateFlow<List<CustomCategory>>(emptyList())
    private val squadsFlow = MutableStateFlow<List<SquadPool>>(emptyList())
    private val checkinsBySquadFlow = MutableStateFlow<Map<String, List<SquadCheckin>>>(emptyMap())

    init {
        loadFromPrefsLocked()
    }

    override fun observeUserProfile(): Flow<UserProfile?> = profileFlow

    override fun observeStakes(): Flow<List<StakeCommitment>> = stakesFlow.map { list ->
        list.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    override fun observeCompletedStakes(): Flow<List<StakeCommitment>> = stakesFlow.map { list ->
        list.filter { it.status == "COMPLETED" }
            .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
    }

    override fun observeCustomCategories(): Flow<List<CustomCategory>> = categoriesFlow.map { list ->
        list.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    override fun observeSquads(): Flow<List<SquadPool>> = squadsFlow.map { list ->
        list.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    override fun observeSquadCheckins(squadId: String): Flow<List<SquadCheckin>> =
        checkinsBySquadFlow.map { all ->
            all[squadId].orEmpty().sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    override suspend fun ensureStarterProfileAndData(initialWalletAddress: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val safeWallet = initialWalletAddress.trim().take(64)
                val existing = profileFlow.value
                if (existing == null) {
                    val now = Timestamp.now()
                    val displayLabel = if (safeWallet.length >= 12) {
                        "Seeker ${SolanaSeekerWalletManager.shortenAddress(safeWallet)}"
                    } else {
                        "Seeker Wallet"
                    }
                    val starterProfile = UserProfile(
                        userId = GUEST_UID,
                        displayName = displayLabel,
                        walletAddress = safeWallet,
                        streakCount = 1,
                        actPoints = 150,
                        recentVerifiedObjects = emptyList(),
                        usdcBalance = 150.0,
                        skrBalance = 500.0,
                        lockedBalance = 15.0,
                        weekBitmap = 1 shl ActakeRepository.currentDayBitIndex(),
                        alarmSound = "Siren",
                        strictnessEnabled = true,
                        createdAt = now,
                        updatedAt = now
                    )
                    val starterStake = StakeCommitment(
                        id = "local_stake_${UUID.randomUUID().toString().take(8)}",
                        userId = GUEST_UID,
                        title = "Morning Wakeup",
                        categoryId = "WAKEUP",
                        categoryContextHint = "Everyday morning hygiene and kitchen objects such as a ceramic coffee mug, toothbrush, sink faucet, or curtains",
                        missionType = "OBJECT_SNAP",
                        mode = "SOLO",
                        targetPrompt = "AI-randomized morning hygiene or kitchen item",
                        stakeAmount = 15.0,
                        tokenSymbol = "USDC",
                        skrBoosted = false,
                        wakeTimeLabel = "06:00 AM",
                        wakeHour = 6,
                        wakeMinute = 0,
                        durationDays = 7,
                        completedDays = 1,
                        alarmSound = "Siren",
                        status = "ACTIVE",
                        earnedAmount = 0.0,
                        createdAt = now,
                        updatedAt = now
                    )
                    val starterSquad = SquadPool(
                        id = "local_squad_${UUID.randomUUID().toString().take(8)}",
                        creatorId = GUEST_UID,
                        name = "7AM Campus Wake Club",
                        wakeTimeLabel = "07:00 AM",
                        missionTitle = "Morning Wakeup",
                        stakeAmount = 10.0,
                        tokenSymbol = "USDC",
                        potAmount = 10.0,
                        slashedAmount = 0.0,
                        participantCount = 1,
                        checkedInCount = 0,
                        memberUids = listOf(GUEST_UID),
                        joinable = true,
                        status = "ACTIVE",
                        createdAt = now,
                        updatedAt = now
                    )

                    profileFlow.value = starterProfile
                    stakesFlow.value = listOf(starterStake)
                    squadsFlow.value = listOf(starterSquad)
                    checkinsBySquadFlow.value = emptyMap()
                    persistLocked()
                } else if (safeWallet.isNotBlank() && existing.walletAddress.isBlank()) {
                    profileFlow.value = existing.copy(
                        walletAddress = safeWallet,
                        displayName = "Seeker ${SolanaSeekerWalletManager.shortenAddress(safeWallet)}",
                        updatedAt = Timestamp.now()
                    )
                    persistLocked()
                }
                Result.success(Unit)
            }
        }

    override suspend fun createCustomCategory(
        name: String,
        contextHint: String
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Timestamp.now()
            val safeName = name.trim().ifBlank { "Custom Habit" }.take(60)
            val safeHint = contextHint.trim().ifBlank { "Everyday items from $safeName" }.take(180)
            val newId = "custom_cat_${UUID.randomUUID().toString().take(8)}"
            val category = CustomCategory(
                id = newId,
                userId = GUEST_UID,
                name = safeName,
                contextHint = safeHint,
                iconKey = "custom",
                createdAt = now,
                updatedAt = now
            )
            categoriesFlow.value = listOf(category) + categoriesFlow.value
            persistLocked()
            Result.success(newId)
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
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Timestamp.now()
            val safeTitle = title.trim().ifBlank { "Morning Wakeup" }.take(80)
            val safeCatId = categoryId.trim().ifBlank { "WAKEUP" }.take(64)
            val safeHint = categoryContextHint.trim().take(200)
            val safePrompt = targetPrompt.trim().ifBlank { "AI-randomized item for $safeTitle" }.take(160)
            val safeAmount = stakeAmount.coerceIn(1.0, 1000.0)
            val safeToken = if (tokenSymbol == "SKR") "SKR" else "USDC"
            val safeHour = wakeHour.coerceIn(0, 23)
            val safeMinute = wakeMinute.coerceIn(0, 59)
            val safeDays = durationDays.coerceIn(1, 60)
            val wakeLabel = ActakeRepository.formatWakeTime(safeHour, safeMinute)

            val newId = "local_stake_${UUID.randomUUID().toString().take(8)}"
            val newStake = StakeCommitment(
                id = newId,
                userId = GUEST_UID,
                squadId = squadId.take(128),
                title = safeTitle,
                categoryId = safeCatId,
                categoryContextHint = safeHint,
                missionType = missionType,
                mode = mode,
                targetPrompt = safePrompt,
                stakeAmount = safeAmount,
                tokenSymbol = safeToken,
                skrBoosted = safeToken == "SKR",
                wakeTimeLabel = wakeLabel,
                wakeHour = safeHour,
                wakeMinute = safeMinute,
                durationDays = safeDays,
                completedDays = 0,
                alarmSound = alarmSound,
                status = "ACTIVE",
                earnedAmount = 0.0,
                createdAt = now,
                updatedAt = now
            )

            stakesFlow.value = listOf(newStake) + stakesFlow.value
            profileFlow.value?.let { current ->
                profileFlow.value = current.copy(
                    lockedBalance = (current.lockedBalance + safeAmount).coerceAtMost(100000.0),
                    updatedAt = now
                )
            }
            persistLocked()
            Result.success(newId)
        }
    }

    override suspend fun updateAlarmPreferences(
        alarmSound: String,
        strictnessEnabled: Boolean,
        activeStakeId: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Timestamp.now()
            profileFlow.value?.let { current ->
                profileFlow.value = current.copy(
                    alarmSound = alarmSound,
                    strictnessEnabled = strictnessEnabled,
                    updatedAt = now
                )
            }
            if (!activeStakeId.isNullOrBlank()) {
                stakesFlow.value = stakesFlow.value.map { stake ->
                    if (stake.id == activeStakeId) {
                        stake.copy(alarmSound = alarmSound, updatedAt = now)
                    } else {
                        stake
                    }
                }
            }
            persistLocked()
            Result.success(Unit)
        }
    }

    override suspend fun updateWalletAddress(walletAddress: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val now = Timestamp.now()
                val cleaned = walletAddress.trim().take(64)
                profileFlow.value?.let { current ->
                    profileFlow.value = current.copy(
                        walletAddress = cleaned,
                        updatedAt = now
                    )
                }
                persistLocked()
                Result.success(Unit)
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
    ): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Timestamp.now()
            val currentProfile = profileFlow.value ?: profile
            val newStreak = (currentProfile.streakCount + 1).coerceAtMost(10000)
            val newPoints = (currentProfile.actPoints + pointsAwarded).coerceIn(0, 10_000_000)
            val newRecentObjects = (currentProfile.recentVerifiedObjects + verifiedObjectLabel.trim().take(60))
                .filter { it.isNotBlank() }
                .takeLast(7)
            val newBitmap = (currentProfile.weekBitmap or (1 shl ActakeRepository.currentDayBitIndex())) and 127
            val stakeReturned = stake?.stakeAmount ?: 15.0
            val newLocked = (currentProfile.lockedBalance - stakeReturned).coerceAtLeast(0.0)
            val tokenSym = stake?.tokenSymbol ?: "USDC"
            val newUsdc = if (tokenSym == "USDC") {
                (currentProfile.usdcBalance + payoutBonus).coerceAtMost(1000000.0)
            } else {
                currentProfile.usdcBalance
            }
            val newSkr = if (tokenSym == "SKR") {
                (currentProfile.skrBalance + payoutBonus).coerceAtMost(1000000.0)
            } else {
                currentProfile.skrBalance
            }

            profileFlow.value = currentProfile.copy(
                streakCount = newStreak,
                actPoints = newPoints,
                recentVerifiedObjects = newRecentObjects,
                weekBitmap = newBitmap,
                lockedBalance = newLocked,
                usdcBalance = newUsdc,
                skrBalance = newSkr,
                updatedAt = now
            )

            if (stake != null && stake.id.isNotBlank() && stake.status == "ACTIVE") {
                stakesFlow.value = stakesFlow.value.map { item ->
                    if (item.id == stake.id) {
                        val nextCompleted = (item.completedDays + 1).coerceAtMost(item.durationDays)
                        val nextStatus = if (nextCompleted >= item.durationDays) "COMPLETED" else "ACTIVE"
                        val totalEarned = (item.earnedAmount + stakeReturned + payoutBonus)
                            .coerceIn(0.0, 100000.0)
                        item.copy(
                            completedDays = nextCompleted,
                            status = nextStatus,
                            proofHash = proofHashHex.take(128),
                            oracleSignature = oracleSignatureHex.take(256),
                            earnedAmount = totalEarned,
                            updatedAt = now
                        )
                    } else {
                        item
                    }
                }
            }

            if (squad != null && squad.id.isNotBlank()) {
                val timeLabel = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
                val newCheckin = SquadCheckin(
                    id = "local_checkin_${UUID.randomUUID().toString().take(8)}",
                    squadId = squad.id,
                    userId = GUEST_UID,
                    memberUids = squad.memberUids,
                    userName = currentProfile.displayName.ifBlank { "Seeker" }.take(60),
                    missionTitle = "$missionTitle ($verifiedObjectLabel)".take(80),
                    verifiedTimeLabel = timeLabel,
                    streakCount = newStreak,
                    status = "VERIFIED",
                    aiConfidence = confidence.coerceIn(0.0, 1.0),
                    aiReason = reason.take(240),
                    proofHash = proofHashHex.ifBlank { "sha256_verified" }.take(128),
                    oracleSignature = oracleSignatureHex.ifBlank { "ed25519_attested" }.take(256),
                    payoutEarned = (stakeReturned + payoutBonus).coerceIn(0.0, 10000.0),
                    thumbsCount = 0,
                    fireCount = 0,
                    createdAt = now,
                    updatedAt = now
                )
                val updatedMap = checkinsBySquadFlow.value.toMutableMap()
                val existingList = updatedMap[squad.id].orEmpty()
                updatedMap[squad.id] = listOf(newCheckin) + existingList
                checkinsBySquadFlow.value = updatedMap

                squadsFlow.value = squadsFlow.value.map { item ->
                    if (item.id == squad.id && item.status == "ACTIVE") {
                        item.copy(
                            checkedInCount = (item.checkedInCount + 1).coerceAtMost(item.participantCount),
                            updatedAt = now
                        )
                    } else {
                        item
                    }
                }
            }

            persistLocked()
            Result.success(newStreak to newBitmap)
        }
    }

    override suspend fun createSquad(
        name: String,
        wakeTimeLabel: String,
        missionTitle: String,
        stakeAmount: Double,
        tokenSymbol: String
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Timestamp.now()
            val safeName = name.trim().ifBlank { "Morning Accountability Squad" }.take(80)
            val safeWake = wakeTimeLabel.trim().ifBlank { "06:30 AM" }.take(16)
            val safeMission = missionTitle.trim().ifBlank { "Morning Wakeup" }.take(80)
            val safeAmount = stakeAmount.coerceIn(1.0, 1000.0)
            val safeToken = if (tokenSymbol == "SKR") "SKR" else "USDC"

            val newId = "local_squad_${UUID.randomUUID().toString().take(8)}"
            val newSquad = SquadPool(
                id = newId,
                creatorId = GUEST_UID,
                name = safeName,
                wakeTimeLabel = safeWake,
                missionTitle = safeMission,
                stakeAmount = safeAmount,
                tokenSymbol = safeToken,
                potAmount = safeAmount,
                slashedAmount = 0.0,
                participantCount = 1,
                checkedInCount = 0,
                memberUids = listOf(GUEST_UID),
                joinable = true,
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now
            )
            squadsFlow.value = listOf(newSquad) + squadsFlow.value
            persistLocked()
            Result.success(newId)
        }
    }

    override suspend fun joinSquadById(rawSquadIdOrBlinkUrl: String): Result<SquadPool> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val cleanedId = rawSquadIdOrBlinkUrl.trim()
                    .substringAfterLast("/")
                    .substringBefore("?")
                    .trim()
                val localMatch = squadsFlow.value.firstOrNull { it.id == cleanedId }
                if (localMatch != null) {
                    Result.success(localMatch)
                } else {
                    Result.failure(
                        IllegalStateException(
                            "Link Google Cloud Backup in Settings to join cloud squad pools by Pool ID."
                        )
                    )
                }
            }
        }

    override suspend fun reactToCheckin(
        squadId: String,
        checkinId: String,
        isFire: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Timestamp.now()
            val updatedMap = checkinsBySquadFlow.value.toMutableMap()
            val list = updatedMap[squadId].orEmpty().map { item ->
                if (item.id == checkinId) {
                    if (isFire) item.copy(fireCount = item.fireCount + 1, updatedAt = now)
                    else item.copy(thumbsCount = item.thumbsCount + 1, updatedAt = now)
                } else {
                    item
                }
            }
            updatedMap[squadId] = list
            checkinsBySquadFlow.value = updatedMap
            persistLocked()
            Result.success(Unit)
        }
    }

    private fun loadFromPrefsLocked() {
        prefs.getString(KEY_PROFILE, null)?.let { json ->
            runCatching { decodeProfile(JSONObject(json)) }.onSuccess { profileFlow.value = it }
        }
        prefs.getString(KEY_STAKES, null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { idx -> decodeStake(arr.getJSONObject(idx)) }
            }.onSuccess { stakesFlow.value = it }
        }
        prefs.getString(KEY_CATEGORIES, null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { idx -> decodeCategory(arr.getJSONObject(idx)) }
            }.onSuccess { categoriesFlow.value = it }
        }
        prefs.getString(KEY_SQUADS, null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { idx -> decodeSquad(arr.getJSONObject(idx)) }
            }.onSuccess { squadsFlow.value = it }
        }
        prefs.getString(KEY_CHECKINS, null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { idx -> decodeCheckin(arr.getJSONObject(idx)) }.groupBy { it.squadId }
            }.onSuccess { checkinsBySquadFlow.value = it }
        }
    }

    private fun persistLocked() {
        val editor = prefs.edit()
        profileFlow.value?.let { editor.putString(KEY_PROFILE, encodeProfile(it).toString()) }

        val stakesArr = JSONArray()
        stakesFlow.value.forEach { stakesArr.put(encodeStake(it)) }
        editor.putString(KEY_STAKES, stakesArr.toString())

        val catArr = JSONArray()
        categoriesFlow.value.forEach { catArr.put(encodeCategory(it)) }
        editor.putString(KEY_CATEGORIES, catArr.toString())

        val squadsArr = JSONArray()
        squadsFlow.value.forEach { squadsArr.put(encodeSquad(it)) }
        editor.putString(KEY_SQUADS, squadsArr.toString())

        val checkinsArr = JSONArray()
        checkinsBySquadFlow.value.values.flatten().forEach { checkinsArr.put(encodeCheckin(it)) }
        editor.putString(KEY_CHECKINS, checkinsArr.toString())

        editor.apply()
    }

    private fun encodeProfile(p: UserProfile): JSONObject = JSONObject().apply {
        put("userId", p.userId)
        put("displayName", p.displayName)
        put("walletAddress", p.walletAddress)
        put("streakCount", p.streakCount)
        put("actPoints", p.actPoints)
        put("recentVerifiedObjects", JSONArray(p.recentVerifiedObjects))
        put("usdcBalance", p.usdcBalance)
        put("skrBalance", p.skrBalance)
        put("lockedBalance", p.lockedBalance)
        put("weekBitmap", p.weekBitmap)
        put("alarmSound", p.alarmSound)
        put("strictnessEnabled", p.strictnessEnabled)
        put("createdAtMs", p.createdAt?.toDate()?.time ?: System.currentTimeMillis())
        put("updatedAtMs", p.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
    }

    private fun decodeProfile(o: JSONObject): UserProfile {
        val recentArr = o.optJSONArray("recentVerifiedObjects")
        val recent = if (recentArr != null) {
            List(recentArr.length()) { idx -> recentArr.optString(idx) }
        } else {
            emptyList()
        }
        return UserProfile(
            userId = o.optString("userId", GUEST_UID),
            displayName = o.optString("displayName", "Seeker Wallet"),
            walletAddress = o.optString("walletAddress", ""),
            streakCount = o.optInt("streakCount", 1),
            actPoints = o.optInt("actPoints", 150),
            recentVerifiedObjects = recent,
            usdcBalance = o.optDouble("usdcBalance", 150.0),
            skrBalance = o.optDouble("skrBalance", 500.0),
            lockedBalance = o.optDouble("lockedBalance", 15.0),
            weekBitmap = o.optInt("weekBitmap", 1),
            alarmSound = o.optString("alarmSound", "Siren"),
            strictnessEnabled = o.optBoolean("strictnessEnabled", true),
            createdAt = Timestamp(Date(o.optLong("createdAtMs", System.currentTimeMillis()))),
            updatedAt = Timestamp(Date(o.optLong("updatedAtMs", System.currentTimeMillis())))
        )
    }

    private fun encodeCategory(c: CustomCategory): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("userId", c.userId)
        put("name", c.name)
        put("contextHint", c.contextHint)
        put("iconKey", c.iconKey)
        put("createdAtMs", c.createdAt?.toDate()?.time ?: System.currentTimeMillis())
        put("updatedAtMs", c.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
    }

    private fun decodeCategory(o: JSONObject): CustomCategory = CustomCategory(
        id = o.optString("id", ""),
        userId = o.optString("userId", GUEST_UID),
        name = o.optString("name", "Custom Habit"),
        contextHint = o.optString("contextHint", ""),
        iconKey = o.optString("iconKey", "custom"),
        createdAt = Timestamp(Date(o.optLong("createdAtMs", System.currentTimeMillis()))),
        updatedAt = Timestamp(Date(o.optLong("updatedAtMs", System.currentTimeMillis())))
    )

    private fun encodeStake(s: StakeCommitment): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("userId", s.userId)
        put("squadId", s.squadId)
        put("title", s.title)
        put("categoryId", s.categoryId)
        put("categoryContextHint", s.categoryContextHint)
        put("missionType", s.missionType)
        put("mode", s.mode)
        put("targetPrompt", s.targetPrompt)
        put("stakeAmount", s.stakeAmount)
        put("tokenSymbol", s.tokenSymbol)
        put("skrBoosted", s.skrBoosted)
        put("wakeTimeLabel", s.wakeTimeLabel)
        put("wakeHour", s.wakeHour)
        put("wakeMinute", s.wakeMinute)
        put("durationDays", s.durationDays)
        put("completedDays", s.completedDays)
        put("alarmSound", s.alarmSound)
        put("status", s.status)
        put("proofHash", s.proofHash)
        put("oracleSignature", s.oracleSignature)
        put("earnedAmount", s.earnedAmount)
        put("createdAtMs", s.createdAt?.toDate()?.time ?: System.currentTimeMillis())
        put("updatedAtMs", s.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
    }

    private fun decodeStake(o: JSONObject): StakeCommitment = StakeCommitment(
        id = o.optString("id", ""),
        userId = o.optString("userId", GUEST_UID),
        squadId = o.optString("squadId", ""),
        title = o.optString("title", "Morning Wakeup"),
        categoryId = o.optString("categoryId", "WAKEUP"),
        categoryContextHint = o.optString("categoryContextHint", ""),
        missionType = o.optString("missionType", "OBJECT_SNAP"),
        mode = o.optString("mode", "SOLO"),
        targetPrompt = o.optString("targetPrompt", ""),
        stakeAmount = o.optDouble("stakeAmount", 15.0),
        tokenSymbol = o.optString("tokenSymbol", "USDC"),
        skrBoosted = o.optBoolean("skrBoosted", false),
        wakeTimeLabel = o.optString("wakeTimeLabel", "06:00 AM"),
        wakeHour = o.optInt("wakeHour", 6),
        wakeMinute = o.optInt("wakeMinute", 0),
        durationDays = o.optInt("durationDays", 7),
        completedDays = o.optInt("completedDays", 0),
        alarmSound = o.optString("alarmSound", "Siren"),
        status = o.optString("status", "ACTIVE"),
        proofHash = o.optString("proofHash", ""),
        oracleSignature = o.optString("oracleSignature", ""),
        earnedAmount = o.optDouble("earnedAmount", 0.0),
        createdAt = Timestamp(Date(o.optLong("createdAtMs", System.currentTimeMillis()))),
        updatedAt = Timestamp(Date(o.optLong("updatedAtMs", System.currentTimeMillis())))
    )

    private fun encodeSquad(sq: SquadPool): JSONObject = JSONObject().apply {
        put("id", sq.id)
        put("creatorId", sq.creatorId)
        put("name", sq.name)
        put("wakeTimeLabel", sq.wakeTimeLabel)
        put("missionTitle", sq.missionTitle)
        put("stakeAmount", sq.stakeAmount)
        put("tokenSymbol", sq.tokenSymbol)
        put("potAmount", sq.potAmount)
        put("slashedAmount", sq.slashedAmount)
        put("participantCount", sq.participantCount)
        put("checkedInCount", sq.checkedInCount)
        put("memberUids", JSONArray(sq.memberUids))
        put("joinable", sq.joinable)
        put("status", sq.status)
        put("createdAtMs", sq.createdAt?.toDate()?.time ?: System.currentTimeMillis())
        put("updatedAtMs", sq.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
    }

    private fun decodeSquad(o: JSONObject): SquadPool {
        val membersArr = o.optJSONArray("memberUids")
        val members = if (membersArr != null) {
            List(membersArr.length()) { idx -> membersArr.optString(idx) }
        } else {
            listOf(GUEST_UID)
        }
        return SquadPool(
            id = o.optString("id", ""),
            creatorId = o.optString("creatorId", GUEST_UID),
            name = o.optString("name", "7AM Campus Wake Club"),
            wakeTimeLabel = o.optString("wakeTimeLabel", "07:00 AM"),
            missionTitle = o.optString("missionTitle", "Morning Wakeup"),
            stakeAmount = o.optDouble("stakeAmount", 10.0),
            tokenSymbol = o.optString("tokenSymbol", "USDC"),
            potAmount = o.optDouble("potAmount", 10.0),
            slashedAmount = o.optDouble("slashedAmount", 0.0),
            participantCount = o.optInt("participantCount", 1),
            checkedInCount = o.optInt("checkedInCount", 0),
            memberUids = members,
            joinable = o.optBoolean("joinable", true),
            status = o.optString("status", "ACTIVE"),
            createdAt = Timestamp(Date(o.optLong("createdAtMs", System.currentTimeMillis()))),
            updatedAt = Timestamp(Date(o.optLong("updatedAtMs", System.currentTimeMillis())))
        )
    }

    private fun encodeCheckin(c: SquadCheckin): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("squadId", c.squadId)
        put("userId", c.userId)
        put("memberUids", JSONArray(c.memberUids))
        put("userName", c.userName)
        put("missionTitle", c.missionTitle)
        put("verifiedTimeLabel", c.verifiedTimeLabel)
        put("streakCount", c.streakCount)
        put("status", c.status)
        put("aiConfidence", c.aiConfidence)
        put("aiReason", c.aiReason)
        put("proofHash", c.proofHash)
        put("oracleSignature", c.oracleSignature)
        put("payoutEarned", c.payoutEarned)
        put("thumbsCount", c.thumbsCount)
        put("fireCount", c.fireCount)
        put("createdAtMs", c.createdAt?.toDate()?.time ?: System.currentTimeMillis())
        put("updatedAtMs", c.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
    }

    private fun decodeCheckin(o: JSONObject): SquadCheckin {
        val membersArr = o.optJSONArray("memberUids")
        val members = if (membersArr != null) {
            List(membersArr.length()) { idx -> membersArr.optString(idx) }
        } else {
            listOf(GUEST_UID)
        }
        return SquadCheckin(
            id = o.optString("id", ""),
            squadId = o.optString("squadId", ""),
            userId = o.optString("userId", GUEST_UID),
            memberUids = members,
            userName = o.optString("userName", "Seeker"),
            missionTitle = o.optString("missionTitle", "Verified Mission"),
            verifiedTimeLabel = o.optString("verifiedTimeLabel", "06:00 AM"),
            streakCount = o.optInt("streakCount", 1),
            status = o.optString("status", "VERIFIED"),
            aiConfidence = o.optDouble("aiConfidence", 0.95),
            aiReason = o.optString("aiReason", ""),
            proofHash = o.optString("proofHash", ""),
            oracleSignature = o.optString("oracleSignature", ""),
            payoutEarned = o.optDouble("payoutEarned", 18.4),
            thumbsCount = o.optInt("thumbsCount", 0),
            fireCount = o.optInt("fireCount", 0),
            createdAt = Timestamp(Date(o.optLong("createdAtMs", System.currentTimeMillis()))),
            updatedAt = Timestamp(Date(o.optLong("updatedAtMs", System.currentTimeMillis())))
        )
    }

    companion object {
        const val GUEST_UID = "seeker_local"
        private const val PREFS_NAME = "actake_local_guest_store"
        private const val KEY_PROFILE = "profile_json"
        private const val KEY_STAKES = "stakes_json"
        private const val KEY_CATEGORIES = "categories_json"
        private const val KEY_SQUADS = "squads_json"
        private const val KEY_CHECKINS = "checkins_json"
    }
}
