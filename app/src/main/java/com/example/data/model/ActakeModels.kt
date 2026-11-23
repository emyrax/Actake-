package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Seeker",
    val walletAddress: String = "",
    val streakCount: Int = 0,
    val actPoints: Int = 150,
    val recentVerifiedObjects: List<String> = emptyList(),
    val usdcBalance: Double = 142.50,
    val skrBalance: Double = 500.0,
    val lockedBalance: Double = 15.0,
    val weekBitmap: Int = 62, // Mon..Fri bits set by default for starter streak strip
    val alarmSound: String = "Siren",
    val strictnessEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StakeCommitment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Morning Wakeup",
    val categoryId: String = "WAKEUP",
    val categoryContextHint: String = "Morning hygiene & kitchen objects (mug, toothbrush, faucet, curtains)",
    val missionType: String = "OBJECT_SNAP", // OBJECT_SNAP | POSE_REP | GEO_HUNT
    val mode: String = "SOLO", // SOLO | SQUAD | GPS_HUNT
    val targetPrompt: String = "AI-randomized morning hygiene or kitchen item",
    val stakeAmount: Double = 15.0,
    val tokenSymbol: String = "USDC", // USDC | SKR
    val skrBoosted: Boolean = false,
    val wakeTimeLabel: String = "06:00 AM",
    val wakeHour: Int = 6,
    val wakeMinute: Int = 0,
    val durationDays: Int = 7,
    val completedDays: Int = 0,
    val alarmSound: String = "Siren",
    val status: String = "ACTIVE", // ACTIVE | COMPLETED | SLASHED
    val squadId: String = "",
    val proofHash: String = "",
    val oracleSignature: String = "",
    val earnedAmount: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomCategory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val contextHint: String = "",
    val iconKey: String = "custom",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SquadPool(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val name: String = "7AM Campus Wake Club",
    val wakeTimeLabel: String = "07:00 AM",
    val missionTitle: String = "Morning Wakeup",
    val stakeAmount: Double = 10.0,
    val tokenSymbol: String = "USDC",
    val potAmount: Double = 185.0,
    val slashedAmount: Double = 30.0,
    val participantCount: Int = 1,
    val checkedInCount: Int = 0,
    val memberUids: List<String> = emptyList(),
    val joinable: Boolean = true,
    val status: String = "ACTIVE", // ACTIVE | SETTLED
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SquadCheckin(
    @DocumentId val id: String = "",
    val squadId: String = "",
    val userId: String = "",
    val memberUids: List<String> = emptyList(),
    val userName: String = "",
    val missionTitle: String = "",
    val verifiedTimeLabel: String = "06:00 AM",
    val streakCount: Int = 1,
    val status: String = "VERIFIED", // VERIFIED | MISSED
    val aiConfidence: Double = 0.0,
    val aiReason: String = "",
    val proofHash: String = "",
    val oracleSignature: String = "",
    val payoutEarned: Double = 0.0,
    val thumbsCount: Int = 0,
    val fireCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Broad anti-cheat Habit Category. Users pick only the category — never a specific object —
 * so the Vision AI randomizes the target item at check-in time.
 */
data class HabitCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val contextHint: String,
    val missionType: String,
    val defaultMode: String,
    val defaultStake: Double,
    val iconKey: String,
    val isCustom: Boolean = false
)

/**
 * Dynamic anti-cheat target randomized by Gemini Flash when an alarm rings or check-in starts.
 */
data class RandomizedTargetPrompt(
    val targetObject: String,
    val visualCue: String,
    val referenceImagePrompt: String,
    val antiCheatRule: String,
    val presetAssetKey: String // "mug" | "notebook" | "dumbbell" | "outdoors" | "custom"
)

data class GeoHuntCheckpoint(
    val id: String,
    val title: String,
    val category: String, // Running | Campus Hunt | Gym Check-in
    val scheduleLabel: String,
    val potAmount: Double,
    val radiusMeters: Double,
    val offsetNorthMeters: Double,
    val offsetEastMeters: Double,
    val targetPrompt: String,
    val activeHunters: Int
)

data class VisionVerificationResult(
    val verified: Boolean,
    val confidence: Double,
    val reason: String,
    val detectedObject: String,
    val livePhotoConfirmed: Boolean,
    val proofHashHex: String,
    val oracleSignatureHex: String,
    val settlementMs: Long
)

data class CelebrationReceipt(
    val verifiedTimeLabel: String,
    val streakCount: Int,
    val weekBitmap: Int,
    val stakeReturned: Double,
    val splitBonus: Double,
    val totalEarned: Double,
    val tokenSymbol: String,
    val settlementMs: Long,
    val missionTitle: String,
    val proofHashHex: String,
    val oracleSignatureHex: String,
    val squadId: String,
    val pointsEarned: Int = 50,
    val verifiedTargetObject: String = "",
    val x402FeeUsdc: Double = 0.0,
    val x402Sponsored: Boolean = false,
    val x402Header: String = ""
)

enum class AlarmSoundOption(val label: String, val subtitle: String, val frequencyHz: Int) {
    MAYHEM("Mayhem", "Rapid industrial siren bursts", 1320),
    SIREN("Siren", "Max-volume emergency wake sweep", 980),
    ROOSTER("Rooster", "Sharp morning crow cadence", 1180),
    BABY_CRYING("Baby Crying", "High-urgency alternating pitch", 860),
    BUZZER("Buzzer", "Relentless buzzer pulse", 520);

    companion object {
        fun fromLabel(raw: String): AlarmSoundOption =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: SIREN
    }
}

val DefaultHabitCategories = listOf(
    HabitCategory(
        id = "WAKEUP",
        title = "Morning Wakeup",
        subtitle = "Hygiene & kitchen items",
        contextHint = "Everyday morning hygiene and kitchen objects such as a ceramic coffee mug, toothbrush, running sink faucet, open window curtains, or bathroom mirror",
        missionType = "OBJECT_SNAP",
        defaultMode = "SOLO",
        defaultStake = 15.0,
        iconKey = "wakeup"
    ),
    HabitCategory(
        id = "READING",
        title = "Reading & Study",
        subtitle = "Books, notes & desk focus",
        contextHint = "Study and deep-focus items such as a pen resting on an open notebook, an open book page, a study desk lamp, or handwritten study notes",
        missionType = "OBJECT_SNAP",
        defaultMode = "SOLO",
        defaultStake = 10.0,
        iconKey = "study"
    ),
    HabitCategory(
        id = "GYM",
        title = "Gym & Fitness",
        subtitle = "Weights, mat & gear",
        contextHint = "Workout and movement gear such as a dumbbell, running shoes, yoga mat, gym water bottle, jump rope, or weight rack",
        missionType = "OBJECT_SNAP",
        defaultMode = "SQUAD",
        defaultStake = 15.0,
        iconKey = "gym"
    ),
    HabitCategory(
        id = "OUTDOORS",
        title = "Outdoors & Grass",
        subtitle = "Daylight, nature & steps",
        contextHint = "Real outdoor daylight items such as green grass or leaves, blue morning sky, tree bark, street sign, or outdoor walkway",
        missionType = "GEO_HUNT",
        defaultMode = "GPS_HUNT",
        defaultStake = 15.0,
        iconKey = "grass"
    ),
    HabitCategory(
        id = "HYDRATION",
        title = "Hydration & Kitchen",
        subtitle = "Water, cup & nutrition",
        contextHint = "Kitchen hydration and healthy routine objects such as a full glass of water, reusable water bottle, ceramic cup, kettle, or fresh fruit",
        missionType = "OBJECT_SNAP",
        defaultMode = "SOLO",
        defaultStake = 10.0,
        iconKey = "water"
    )
)

val DefaultGeoHuntZones = listOf(
    GeoHuntCheckpoint(
        id = "stadium_run",
        title = "6:30 AM Stadium Run",
        category = "Running",
        scheduleLabel = "6:30 AM",
        potAmount = 250.0,
        radiusMeters = 50.0,
        offsetNorthMeters = 18.0,
        offsetEastMeters = 12.0,
        targetPrompt = "Real outdoor running path, sneakers on pavement, park, or sky outdoors",
        activeHunters = 42
    ),
    GeoHuntCheckpoint(
        id = "campus_quad",
        title = "7:00 AM Campus Hunt",
        category = "Campus Hunt",
        scheduleLabel = "7:00 AM",
        potAmount = 180.0,
        radiusMeters = 50.0,
        offsetNorthMeters = 110.0,
        offsetEastMeters = 45.0,
        targetPrompt = "Outdoor walkway, campus building exterior, trees, or sunlight outdoors",
        activeHunters = 38
    ),
    GeoHuntCheckpoint(
        id = "iron_gym",
        title = "6:00 AM Gym Check-in",
        category = "Gym Check-in",
        scheduleLabel = "6:00 AM",
        potAmount = 320.0,
        radiusMeters = 50.0,
        offsetNorthMeters = -85.0,
        offsetEastMeters = 90.0,
        targetPrompt = "Real gym dumbbells, fitness studio, or workout equipment",
        activeHunters = 29
    )
)
