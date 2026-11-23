package com.example.data.repository

import com.example.data.model.CustomCategory
import com.example.data.model.SquadCheckin
import com.example.data.model.SquadPool
import com.example.data.model.StakeCommitment
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ActakeDataSource {
    fun observeUserProfile(): Flow<UserProfile?>
    fun observeStakes(): Flow<List<StakeCommitment>>
    fun observeCompletedStakes(): Flow<List<StakeCommitment>>
    fun observeCustomCategories(): Flow<List<CustomCategory>>
    fun observeSquads(): Flow<List<SquadPool>>
    fun observeSquadCheckins(squadId: String): Flow<List<SquadCheckin>>

    suspend fun ensureStarterProfileAndData(initialWalletAddress: String = ""): Result<Unit>

    suspend fun createCustomCategory(
        name: String,
        contextHint: String
    ): Result<String>

    suspend fun createStake(
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
        squadId: String = ""
    ): Result<String>

    suspend fun updateAlarmPreferences(
        alarmSound: String,
        strictnessEnabled: Boolean,
        activeStakeId: String? = null
    ): Result<Unit>

    suspend fun updateWalletAddress(walletAddress: String): Result<Unit>

    suspend fun recordVerifiedCheckin(
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
    ): Result<Pair<Int, Int>>

    suspend fun createSquad(
        name: String,
        wakeTimeLabel: String,
        missionTitle: String,
        stakeAmount: Double,
        tokenSymbol: String
    ): Result<String>

    suspend fun joinSquadById(rawSquadIdOrBlinkUrl: String): Result<SquadPool>

    suspend fun reactToCheckin(
        squadId: String,
        checkinId: String,
        isFire: Boolean
    ): Result<Unit>
}
