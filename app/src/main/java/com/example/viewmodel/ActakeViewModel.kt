package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.model.CelebrationReceipt
import com.example.data.model.CustomCategory
import com.example.data.model.DefaultGeoHuntZones
import com.example.data.model.DefaultHabitCategories
import com.example.data.model.GeoHuntCheckpoint
import com.example.data.model.RandomizedTargetPrompt
import com.example.data.model.SquadCheckin
import com.example.data.model.SquadPool
import com.example.data.model.StakeCommitment
import com.example.data.model.UserProfile
import com.example.data.repository.ActakeDataSource
import com.example.data.repository.ActakeRepository
import com.example.data.repository.LocalActakeStore
import com.example.data.repository.SolanaChainSyncSnapshot
import com.example.data.repository.VisionOracleRepository
import com.example.solana.SiwsSession
import com.example.solana.SolanaSeekerWalletManager
import com.example.solana.X402PaymentClient
import com.example.solana.X402PaymentReceipt
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ActakeViewModel"

sealed interface DataState<out T> {
    data object Loading : DataState<Nothing>
    data class Success<T>(val data: T) : DataState<T>
    data class Error(val message: String) : DataState<Nothing>
}

data class ActiveVerificationTarget(
    val stakeId: String = "",
    val squadId: String = "",
    val categoryId: String = "WAKEUP",
    val categoryContextHint: String = "Everyday morning hygiene and kitchen objects such as a ceramic coffee mug, toothbrush, sink faucet, or curtains",
    val isCustomCategory: Boolean = false,
    val title: String = "Morning Wakeup",
    val missionType: String = "OBJECT_SNAP",
    val targetPrompt: String = "AI-randomized morning hygiene or kitchen item",
    val stakeAmount: Double = 15.0,
    val tokenSymbol: String = "USDC",
    val alarmSound: String = "Siren",
    val distanceMeters: Double? = null,
    val maxRadiusMeters: Double? = null
)

sealed interface RandomizedTargetUiState {
    data object Loading : RandomizedTargetUiState
    data class Ready(
        val target: RandomizedTargetPrompt,
        val generatedPreviewBitmap: Bitmap? = null,
        val isGeneratingCustomPreview: Boolean = false,
        val customPreviewError: String? = null
    ) : RandomizedTargetUiState
    data class Error(val message: String) : RandomizedTargetUiState
}

sealed interface VerificationUiState {
    data object Ready : VerificationUiState
    data class Scanning(val statusText: String) : VerificationUiState
    data class VerifyingOracle(val detectedLabel: String, val confidencePct: Int) : VerificationUiState
    data class Rejected(val reason: String, val confidencePct: Int) : VerificationUiState
    data class Error(val message: String) : VerificationUiState
}

data class LiveGpsTelemetry(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
    val hasPermission: Boolean = false,
    val isTracking: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActakeViewModel(
    private val appContext: Context,
    private val userId: String,
    val isGuestMode: Boolean,
    private val repository: ActakeDataSource,
    private val oracleRepository: VisionOracleRepository,
    private val walletManager: SolanaSeekerWalletManager
) : ViewModel() {

    private val x402Client = X402PaymentClient(walletManager)

    val profileState: StateFlow<DataState<UserProfile?>> = repository.observeUserProfile()
        .map<UserProfile?, DataState<UserProfile?>> { DataState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(DataState.Error((firestoreError ?: error).message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataState.Loading)

    val stakesState: StateFlow<DataState<List<StakeCommitment>>> = repository.observeStakes()
        .map<List<StakeCommitment>, DataState<List<StakeCommitment>>> { DataState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(DataState.Error((firestoreError ?: error).message ?: "Failed to load stakes"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataState.Loading)

    val completedStakesState: StateFlow<DataState<List<StakeCommitment>>> = repository.observeCompletedStakes()
        .map<List<StakeCommitment>, DataState<List<StakeCommitment>>> { DataState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(DataState.Error((firestoreError ?: error).message ?: "Failed to load stake history"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataState.Loading)

    val customCategoriesState: StateFlow<DataState<List<CustomCategory>>> = repository.observeCustomCategories()
        .map<List<CustomCategory>, DataState<List<CustomCategory>>> { DataState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(DataState.Error((firestoreError ?: error).message ?: "Failed to load custom categories"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataState.Success(emptyList()))

    val squadsState: StateFlow<DataState<List<SquadPool>>> = repository.observeSquads()
        .map<List<SquadPool>, DataState<List<SquadPool>>> { DataState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(DataState.Error((firestoreError ?: error).message ?: "Failed to load squads"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataState.Loading)

    private val _selectedSquadId = MutableStateFlow<String?>(null)
    val selectedSquadId: StateFlow<String?> = _selectedSquadId.asStateFlow()

    val checkinsState: StateFlow<DataState<List<SquadCheckin>>> = _selectedSquadId
        .flatMapLatest { squadId ->
            if (squadId.isNullOrBlank()) {
                flowOf<DataState<List<SquadCheckin>>>(DataState.Success(emptyList()))
            } else {
                repository.observeSquadCheckins(squadId)
                    .map<List<SquadCheckin>, DataState<List<SquadCheckin>>> { DataState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(DataState.Error((firestoreError ?: error).message ?: "Failed to load check-ins"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataState.Success(emptyList()))

    private val _siwsSession = MutableStateFlow(walletManager.getSavedSession())
    val siwsSession: StateFlow<SiwsSession?> = _siwsSession.asStateFlow()

    private val _activeVerificationTarget = MutableStateFlow(ActiveVerificationTarget())
    val activeVerificationTarget: StateFlow<ActiveVerificationTarget> = _activeVerificationTarget.asStateFlow()

    private val _randomizedTargetState = MutableStateFlow<RandomizedTargetUiState>(RandomizedTargetUiState.Loading)
    val randomizedTargetState: StateFlow<RandomizedTargetUiState> = _randomizedTargetState.asStateFlow()

    private val _verificationState = MutableStateFlow<VerificationUiState>(VerificationUiState.Ready)
    val verificationState: StateFlow<VerificationUiState> = _verificationState.asStateFlow()

    private val _celebrationReceipt = MutableStateFlow<CelebrationReceipt?>(null)
    val celebrationReceipt: StateFlow<CelebrationReceipt?> = _celebrationReceipt.asStateFlow()

    private val _payoutRedemptionTxSig = MutableStateFlow<String?>(null)
    val payoutRedemptionTxSig: StateFlow<String?> = _payoutRedemptionTxSig.asStateFlow()

    private val _lastX402Receipt = MutableStateFlow<X402PaymentReceipt?>(null)
    val lastX402Receipt: StateFlow<X402PaymentReceipt?> = _lastX402Receipt.asStateFlow()

    private val _gpsTelemetry = MutableStateFlow(LiveGpsTelemetry())
    val gpsTelemetry: StateFlow<LiveGpsTelemetry> = _gpsTelemetry.asStateFlow()

    private val _selectedHuntZone = MutableStateFlow(DefaultGeoHuntZones.first())
    val selectedHuntZone: StateFlow<GeoHuntCheckpoint> = _selectedHuntZone.asStateFlow()

    private val _solanaDevnetSol = MutableStateFlow<Double?>(null)
    val solanaDevnetSol: StateFlow<Double?> = _solanaDevnetSol.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastChainSync = MutableStateFlow<SolanaChainSyncSnapshot?>(null)
    val lastChainSync: StateFlow<SolanaChainSyncSnapshot?> = _lastChainSync.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val savedPubkey = walletManager.getSavedSession()?.publicKeyBase58.orEmpty()
            val result = repository.ensureStarterProfileAndData(initialWalletAddress = savedPubkey)
            result.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Could not initialize profile."
            }
        }
        viewModelScope.launch {
            squadsState.collect { state ->
                if (state is DataState.Success && state.data.isNotEmpty() && _selectedSquadId.value == null) {
                    _selectedSquadId.value = state.data.first().id
                }
            }
        }
        viewModelScope.launch {
            profileState.collect { state ->
                val profileAddr = (state as? DataState.Success)?.data?.walletAddress.orEmpty()
                val address = profileAddr.ifBlank { _siwsSession.value?.publicKeyBase58.orEmpty() }
                if (address.length in 32..44) {
                    oracleRepository.fetchSolanaDevnetBalanceSol(address)
                        .onSuccess { _solanaDevnetSol.value = it }
                } else {
                    _solanaDevnetSol.value = null
                }
            }
        }
    }

    fun clearBannerMessage() {
        _bannerMessage.value = null
    }

    /**
     * Pull-to-refresh handler for the Main Dashboard: queries live Solana Devnet slot + wallet SOL
     * balance via JSON-RPC and refreshes active stake / profile state without restarting the app.
     */
    fun refreshDashboardFromBlockchain() {
        if (_isRefreshing.value) return
        val profile = (profileState.value as? DataState.Success)?.data
        val walletAddress = profile?.walletAddress?.ifBlank {
            _siwsSession.value?.publicKeyBase58.orEmpty()
        } ?: _siwsSession.value?.publicKeyBase58.orEmpty()

        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Re-sync local/cloud store state and query live Solana Devnet RPC snapshot
                repository.ensureStarterProfileAndData(initialWalletAddress = walletAddress)
                oracleRepository.syncSolanaChainSnapshot(walletAddress)
                    .onSuccess { snapshot ->
                        _lastChainSync.value = snapshot
                        snapshot.balanceSol?.let { _solanaDevnetSol.value = it }
                        val solSuffix = snapshot.balanceSol?.let {
                            String.format(Locale.US, " • %.3f SOL", it)
                        }.orEmpty()
                        _bannerMessage.value = String.format(
                            Locale.US,
                            "Synced Solana Devnet Slot #%,d%s",
                            snapshot.confirmedSlot,
                            solSuffix
                        )
                    }
                    .onFailure { err ->
                        _bannerMessage.value = err.localizedMessage
                            ?: "Unable to reach Solana RPC. Check connection and pull again."
                    }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectSquad(squadId: String) {
        _selectedSquadId.value = squadId
    }

    fun selectHuntZone(zone: GeoHuntCheckpoint) {
        _selectedHuntZone.value = zone
    }

    fun updateGpsLocation(lat: Double, lon: Double) {
        val current = _gpsTelemetry.value
        _gpsTelemetry.value = current.copy(
            latitude = lat,
            longitude = lon,
            anchorLat = current.anchorLat ?: lat,
            anchorLon = current.anchorLon ?: lon,
            hasPermission = true,
            isTracking = true
        )
    }

    fun setGpsPermissionState(granted: Boolean) {
        _gpsTelemetry.value = _gpsTelemetry.value.copy(hasPermission = granted)
    }

    fun distanceToZoneMeters(zone: GeoHuntCheckpoint): Double? {
        val gps = _gpsTelemetry.value
        val curLat = gps.latitude ?: return null
        val curLon = gps.longitude ?: return null
        val anchorLat = gps.anchorLat ?: curLat
        val anchorLon = gps.anchorLon ?: curLon
        val (targetLat, targetLon) = VisionOracleRepository.offsetCoordinate(
            baseLat = anchorLat,
            baseLon = anchorLon,
            northMeters = zone.offsetNorthMeters,
            eastMeters = zone.offsetEastMeters
        )
        return VisionOracleRepository.haversineMeters(curLat, curLon, targetLat, targetLon)
    }

    fun prepareVerificationTarget(target: ActiveVerificationTarget) {
        _activeVerificationTarget.value = target
        _verificationState.value = VerificationUiState.Ready
        _payoutRedemptionTxSig.value = null
        rollRandomizedTargetForActive()
    }

    fun prepareVerificationFromStake(stake: StakeCommitment, defaultAlarmSound: String) {
        val builtIn = DefaultHabitCategories.firstOrNull {
            it.id.equals(stake.categoryId, ignoreCase = true)
        }
        val isCustom = builtIn == null && stake.categoryId.startsWith("custom", ignoreCase = true)
        val contextHint = stake.categoryContextHint.ifBlank {
            builtIn?.contextHint ?: stake.targetPrompt
        }
        prepareVerificationTarget(
            ActiveVerificationTarget(
                stakeId = stake.id,
                squadId = stake.squadId.ifBlank { _selectedSquadId.value.orEmpty() },
                categoryId = stake.categoryId.ifBlank { "WAKEUP" },
                categoryContextHint = contextHint,
                isCustomCategory = isCustom,
                title = stake.title,
                missionType = stake.missionType,
                targetPrompt = stake.targetPrompt,
                stakeAmount = stake.stakeAmount,
                tokenSymbol = stake.tokenSymbol,
                alarmSound = stake.alarmSound.ifBlank { defaultAlarmSound }
            )
        )
    }

    fun prepareVerificationFromGeoHunt(zone: GeoHuntCheckpoint, distanceMeters: Double?) {
        val profile = (profileState.value as? DataState.Success)?.data
        prepareVerificationTarget(
            ActiveVerificationTarget(
                stakeId = "",
                squadId = _selectedSquadId.value.orEmpty(),
                categoryId = "OUTDOORS",
                categoryContextHint = zone.targetPrompt,
                isCustomCategory = false,
                title = zone.title,
                missionType = "GEO_HUNT",
                targetPrompt = zone.targetPrompt,
                stakeAmount = 15.0,
                tokenSymbol = "USDC",
                alarmSound = profile?.alarmSound ?: "Siren",
                distanceMeters = distanceMeters,
                maxRadiusMeters = zone.radiusMeters
            )
        )
    }

    /**
     * Anti-Cheat AI Target Randomizer: rolls a fresh physical object from the stake's broad category
     * via `gemini-3.8-flash` (avoiding the user's last 7 verified items), and if it's a Custom Category
     * generates a clean studio reference image via `gemini-nano-banana-2.1`.
     */
    fun rollRandomizedTargetForActive() {
        val target = _activeVerificationTarget.value
        val recentObjects = (profileState.value as? DataState.Success)?.data?.recentVerifiedObjects.orEmpty()

        viewModelScope.launch {
            _randomizedTargetState.value = RandomizedTargetUiState.Loading
            try {
                val randomized = oracleRepository.generateRandomizedTarget(
                    categoryTitle = target.title,
                    categoryContextHint = target.categoryContextHint,
                    isCustomCategory = target.isCustomCategory,
                    recentVerifiedObjects = recentObjects
                )

                val needsCustomImage = target.isCustomCategory || randomized.presetAssetKey == "custom"
                _randomizedTargetState.value = RandomizedTargetUiState.Ready(
                    target = randomized,
                    generatedPreviewBitmap = null,
                    isGeneratingCustomPreview = needsCustomImage,
                    customPreviewError = null
                )

                if (needsCustomImage) {
                    launch {
                        try {
                            val bmp = oracleRepository.generateReferencePreviewBitmap(
                                randomized.referenceImagePrompt
                            )
                            val latest = _randomizedTargetState.value
                            if (latest is RandomizedTargetUiState.Ready &&
                                latest.target.targetObject == randomized.targetObject
                            ) {
                                _randomizedTargetState.value = latest.copy(
                                    generatedPreviewBitmap = bmp,
                                    isGeneratingCustomPreview = false,
                                    customPreviewError = null
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Custom reference preview generation failed", e)
                            val latest = _randomizedTargetState.value
                            if (latest is RandomizedTargetUiState.Ready &&
                                latest.target.targetObject == randomized.targetObject
                            ) {
                                _randomizedTargetState.value = latest.copy(
                                    generatedPreviewBitmap = null,
                                    isGeneratingCustomPreview = false,
                                    customPreviewError = e.localizedMessage ?: "Preview image unavailable"
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI target randomization failed", e)
                _randomizedTargetState.value = RandomizedTargetUiState.Error(
                    e.localizedMessage ?: "Could not roll randomized target. Tap Retry."
                )
            }
        }
    }

    fun resetVerificationState() {
        _verificationState.value = VerificationUiState.Ready
    }

    fun verifyCapturedFrame(
        bitmap: Bitmap,
        onVerifiedNavigateToCelebration: () -> Unit
    ) {
        val target = _activeVerificationTarget.value
        val randomized = (_randomizedTargetState.value as? RandomizedTargetUiState.Ready)?.target
        val activeTargetLabel = randomized?.targetObject ?: target.title
        val activeTargetPrompt = if (randomized != null) {
            "${randomized.targetObject}. ${randomized.visualCue}. Anti-cheat: ${randomized.antiCheatRule}"
        } else {
            target.targetPrompt
        }

        val profile = (profileState.value as? DataState.Success)?.data ?: UserProfile(userId = userId)
        val stakes = (stakesState.value as? DataState.Success)?.data.orEmpty()
        val matchedStake = stakes.firstOrNull { it.id == target.stakeId }
            ?: stakes.firstOrNull { it.status == "ACTIVE" }
        val squads = (squadsState.value as? DataState.Success)?.data.orEmpty()
        val matchedSquad = squads.firstOrNull { it.id == target.squadId } ?: squads.firstOrNull()
        val isSkrBoosted = target.tokenSymbol == "SKR" || matchedStake?.skrBoosted == true

        viewModelScope.launch {
            _verificationState.value = VerificationUiState.Scanning(
                if (isSkrBoosted) "Settling 0-fee SKR x402 rail & scanning..."
                else "Settling $0.005 USDC x402 voucher & scanning..."
            )
            try {
                // Step 1: Settle x402 HTTP 402 payment rail (0-fee SKR sponsor or signed micro-voucher)
                val x402Receipt = x402Client.settleVerifyCheckinRail(isSkrBoosted = isSkrBoosted)
                _lastX402Receipt.value = x402Receipt

                // Step 2: Gemini Vision AI real-world liveness & randomized-target verification
                val result = oracleRepository.verifyLivePhoto(
                    bitmap = bitmap,
                    missionType = target.missionType,
                    targetPrompt = activeTargetPrompt,
                    poolId = matchedSquad?.id?.ifBlank { "solo_pool" } ?: "solo_pool",
                    userId = userId,
                    distanceMeters = target.distanceMeters,
                    maxRadiusMeters = target.maxRadiusMeters
                )

                val confidencePct = (result.confidence * 100).toInt().coerceIn(0, 100)
                if (!result.verified) {
                    _verificationState.value = VerificationUiState.Rejected(
                        reason = result.reason,
                        confidencePct = confidencePct
                    )
                    return@launch
                }

                _verificationState.value = VerificationUiState.VerifyingOracle(
                    detectedLabel = result.detectedObject,
                    confidencePct = confidencePct
                )

                // Step 3: Award $ACT Points + unlock stake & split bonus
                val splitBonus = if (isSkrBoosted) 4.20 else 3.40
                val pointsAwarded = if (isSkrBoosted) 75 else 50
                val saveResult = repository.recordVerifiedCheckin(
                    profile = profile,
                    stake = matchedStake,
                    squad = matchedSquad,
                    missionTitle = target.title,
                    verifiedObjectLabel = activeTargetLabel,
                    confidence = result.confidence,
                    reason = result.reason,
                    proofHashHex = result.proofHashHex,
                    oracleSignatureHex = result.oracleSignatureHex,
                    payoutBonus = splitBonus,
                    pointsAwarded = pointsAwarded
                )

                val (updatedStreak, updatedBitmap) = saveResult.getOrElse { err ->
                    Log.e(TAG, "Failed to persist check-in", err)
                    _verificationState.value = VerificationUiState.Error(
                        err.localizedMessage ?: "Verified by AI, but failed to save check-in."
                    )
                    return@launch
                }

                val nowTimeLabel = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
                _celebrationReceipt.value = CelebrationReceipt(
                    verifiedTimeLabel = nowTimeLabel,
                    streakCount = updatedStreak,
                    weekBitmap = updatedBitmap,
                    stakeReturned = target.stakeAmount,
                    splitBonus = splitBonus,
                    totalEarned = target.stakeAmount + splitBonus,
                    tokenSymbol = target.tokenSymbol,
                    settlementMs = result.settlementMs,
                    missionTitle = target.title,
                    proofHashHex = result.proofHashHex,
                    oracleSignatureHex = result.oracleSignatureHex,
                    squadId = matchedSquad?.id.orEmpty(),
                    pointsEarned = pointsAwarded,
                    verifiedTargetObject = activeTargetLabel,
                    x402FeeUsdc = x402Receipt.feeUsdc,
                    x402Sponsored = x402Receipt.sponsoredBySkr,
                    x402Header = x402Receipt.xPaymentHeader
                )
                _verificationState.value = VerificationUiState.Ready
                onVerifiedNavigateToCelebration()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Verification error", e)
                _verificationState.value = VerificationUiState.Error(
                    e.localizedMessage ?: "Vision AI verification failed. Please retry."
                )
            }
        }
    }

    fun redeemPayoutToSeekerWallet() {
        val receipt = _celebrationReceipt.value ?: return
        val (walletPubkey, txSig) = walletManager.signPayoutClaim(
            missionTitle = receipt.missionTitle,
            amount = receipt.totalEarned,
            tokenSymbol = receipt.tokenSymbol,
            proofHashHex = receipt.proofHashHex
        )
        _siwsSession.value = walletManager.getSavedSession()
        _payoutRedemptionTxSig.value = txSig
        _bannerMessage.value = "Redeemed +$${"%.2f".format(Locale.US, receipt.totalEarned)} ${receipt.tokenSymbol} to ${SolanaSeekerWalletManager.shortenAddress(walletPubkey)}"
    }

    fun createCustomCategory(
        name: String,
        contextHint: String,
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createCustomCategory(name = name, contextHint = contextHint)
                .onSuccess {
                    _bannerMessage.value = "Created custom category \"$name\" for AI randomization"
                    onCreated()
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Could not create custom category."
                }
        }
    }

    fun lockNewStake(
        title: String,
        categoryId: String,
        categoryContextHint: String,
        missionType: String,
        mode: String,
        stakeAmount: Double,
        tokenSymbol: String,
        wakeHour: Int,
        wakeMinute: Int,
        durationDays: Int,
        onComplete: () -> Unit = {}
    ) {
        val profile = (profileState.value as? DataState.Success)?.data
        val alarmSound = profile?.alarmSound ?: "Siren"
        val linkedSquadId = if (mode == "SQUAD") _selectedSquadId.value.orEmpty() else ""

        viewModelScope.launch {
            repository.createStake(
                title = title,
                categoryId = categoryId,
                categoryContextHint = categoryContextHint,
                missionType = missionType,
                mode = mode,
                targetPrompt = "AI-randomized item from $title",
                stakeAmount = stakeAmount,
                tokenSymbol = tokenSymbol,
                wakeHour = wakeHour,
                wakeMinute = wakeMinute,
                durationDays = durationDays,
                alarmSound = alarmSound,
                squadId = linkedSquadId
            ).onSuccess {
                AlarmScheduler.scheduleDailyWakeAlarm(
                    context = appContext,
                    hour = wakeHour,
                    minute = wakeMinute,
                    missionTitle = title,
                    alarmSound = alarmSound
                )
                val wakeLabel = ActakeRepository.formatWakeTime(wakeHour, wakeMinute)
                _bannerMessage.value = "Locked $${"%.2f".format(Locale.US, stakeAmount)} $tokenSymbol for $title ($wakeLabel)"
                onComplete()
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Could not create stake."
            }
        }
    }

    fun saveAlarmSettings(
        alarmSound: String,
        strictnessEnabled: Boolean,
        onSaved: () -> Unit = {}
    ) {
        val stakes = (stakesState.value as? DataState.Success)?.data.orEmpty()
        val activeStake = stakes.firstOrNull { it.status == "ACTIVE" }

        viewModelScope.launch {
            repository.updateAlarmPreferences(
                alarmSound = alarmSound,
                strictnessEnabled = strictnessEnabled,
                activeStakeId = activeStake?.id
            ).onSuccess {
                AlarmScheduler.scheduleDailyWakeAlarm(
                    context = appContext,
                    hour = activeStake?.wakeHour ?: 6,
                    minute = activeStake?.wakeMinute ?: 0,
                    missionTitle = activeStake?.title ?: "Morning Wakeup",
                    alarmSound = alarmSound
                )
                _bannerMessage.value = "$alarmSound alarm armed (${if (strictnessEnabled) "Strict Mode ON" else "Standard Mode"})"
                onSaved()
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Failed to update alarm settings."
            }
        }
    }

    fun createSquad(
        name: String,
        wakeTimeLabel: String,
        missionTitle: String,
        stakeAmount: Double,
        tokenSymbol: String,
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createSquad(
                name = name,
                wakeTimeLabel = wakeTimeLabel,
                missionTitle = missionTitle,
                stakeAmount = stakeAmount,
                tokenSymbol = tokenSymbol
            ).onSuccess { newId ->
                _selectedSquadId.value = newId
                _bannerMessage.value = "Created squad \"$name\" • Share Blink to invite friends"
                onCreated()
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Could not create squad."
            }
        }
    }

    fun joinSquad(poolIdOrUrl: String, onJoined: () -> Unit = {}) {
        viewModelScope.launch {
            repository.joinSquadById(poolIdOrUrl)
                .onSuccess { squad ->
                    _selectedSquadId.value = squad.id
                    _bannerMessage.value = "Joined ${squad.name}!"
                    onJoined()
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Could not join squad pool."
                }
        }
    }

    fun reactToCheckin(squadId: String, checkinId: String, isFire: Boolean) {
        viewModelScope.launch {
            repository.reactToCheckin(squadId, checkinId, isFire)
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Failed to add reaction."
                }
        }
    }

    fun connectOrRefreshSeekerWallet() {
        val session = walletManager.signInWithSolana()
        _siwsSession.value = session
        saveSolanaWalletAddress(session.publicKeyBase58)
    }

    fun saveSolanaWalletAddress(address: String) {
        viewModelScope.launch {
            repository.updateWalletAddress(address)
                .onSuccess {
                    if (address.isNotBlank()) {
                        oracleRepository.fetchSolanaDevnetBalanceSol(address)
                            .onSuccess { sol ->
                                _solanaDevnetSol.value = sol
                                _bannerMessage.value = "Synced Solana Devnet (${"%.3f".format(Locale.US, sol)} SOL)"
                            }
                            .onFailure {
                                _bannerMessage.value = "Saved Seeker wallet (${SolanaSeekerWalletManager.shortenAddress(address)})"
                            }
                    } else {
                        _solanaDevnetSol.value = null
                        _bannerMessage.value = "Disconnected Solana wallet address."
                    }
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Could not save wallet address."
                }
        }
    }

    fun buildAgentAttestationJson(): String {
        val profile = (profileState.value as? DataState.Success)?.data
        val wallet = profile?.walletAddress?.ifBlank { _siwsSession.value?.publicKeyBase58.orEmpty() }
            ?: _siwsSession.value?.publicKeyBase58.orEmpty()
        return x402Client.buildAgentAttestationJson(
            walletAddress = wallet,
            streakCount = profile?.streakCount ?: 1,
            actPoints = profile?.actPoints ?: 150,
            recentVerifiedObject = profile?.recentVerifiedObjects?.lastOrNull().orEmpty()
        )
    }

    class Factory(
        private val context: Context,
        private val userId: String,
        private val isGuestMode: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            val dataSource: ActakeDataSource = if (isGuestMode) {
                LocalActakeStore(appCtx)
            } else {
                ActakeRepository(appCtx)
            }
            return ActakeViewModel(
                appContext = appCtx,
                userId = userId,
                isGuestMode = isGuestMode,
                repository = dataSource,
                oracleRepository = VisionOracleRepository(),
                walletManager = SolanaSeekerWalletManager(appCtx)
            ) as T
        }
    }
}
