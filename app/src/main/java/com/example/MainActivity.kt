package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.alarm.EXTRA_ALARM_MISSION_TITLE
import com.example.alarm.EXTRA_ALARM_SOUND
import com.example.alarm.EXTRA_ALARM_TRIGGERED
import com.example.data.model.CelebrationReceipt
import com.example.data.repository.LocalActakeStore
import com.example.firebase.configureAppCheck
import com.example.firebase.onGoogleSignInClicked
import com.example.firebase.signOutFromActake
import com.example.solana.SolanaWalletProvider
import com.example.solana.SolanaWalletProviderHost
import com.example.ui.components.ActakeFloatingNavBar
import com.example.ui.components.ActakeTab
import com.example.ui.components.AlarmChallengePopupDialog
import com.example.ui.screens.ActiveAlarmScreen
import com.example.ui.screens.AlarmSettingsScreen
import com.example.ui.screens.CelebrationScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HuntsScreen
import com.example.ui.screens.MissionsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SquadsScreen
import com.example.ui.theme.ActakeTheme
import com.example.ui.theme.BgObsidian
import com.example.viewmodel.ActakeViewModel
import com.example.viewmodel.ActiveVerificationTarget
import com.example.viewmodel.DataState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private const val ROUTE_HOME = "home"
private const val ROUTE_MISSIONS = "missions"
private const val ROUTE_HUNTS = "hunts"
private const val ROUTE_SQUADS = "squads"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_ALARM_SETTINGS = "alarm_settings"
private const val ROUTE_ACTIVE_ALARM = "active_alarm"
private const val ROUTE_CELEBRATION = "celebration"

private data class AlarmLaunchEvent(
    val eventId: Long,
    val missionTitle: String,
    val alarmSound: String
)

class MainActivity : ComponentActivity() {
    private var alarmLaunchEvent by mutableStateOf<AlarmLaunchEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI/ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        captureAlarmIntent(intent)

        setContent {
            ActakeTheme {
                ActakeRoot(
                    alarmLaunchEvent = alarmLaunchEvent
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureAlarmIntent(intent)
    }

    private fun captureAlarmIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ALARM_TRIGGERED, false) == true) {
            alarmLaunchEvent = AlarmLaunchEvent(
                eventId = System.currentTimeMillis(),
                missionTitle = intent.getStringExtra(EXTRA_ALARM_MISSION_TITLE) ?: "Morning Wakeup",
                alarmSound = intent.getStringExtra(EXTRA_ALARM_SOUND) ?: "Siren"
            )
        }
    }
}

private const val PREFS_SESSION = "actake_session_prefs"
private const val KEY_GUEST_MODE_ACTIVE = "guest_mode_active"

@Composable
private fun ActakeRoot(
    alarmLaunchEvent: AlarmLaunchEvent?
) {
    val context = LocalContext.current
    val sessionPrefs = remember(context) {
        context.applicationContext.getSharedPreferences(PREFS_SESSION, Context.MODE_PRIVATE)
    }
    val walletProvider = remember(context) { SolanaWalletProvider.getInstance(context) }
    val walletPublicKey by walletProvider.publicKey.collectAsStateWithLifecycle()

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    // Silent auto-reconnect for Seeker SIWS wallet sessions (zero mandatory Google sign-in)
    var seekerModeActive by remember {
        mutableStateOf(
            !walletPublicKey.isNullOrBlank() ||
                sessionPrefs.getBoolean(KEY_GUEST_MODE_ACTIVE, false) ||
                alarmLaunchEvent != null
        )
    }

    LaunchedEffect(alarmLaunchEvent?.eventId) {
        if (alarmLaunchEvent != null && currentUser == null) {
            seekerModeActive = true
        }
    }

    fun setSeekerMode(active: Boolean) {
        seekerModeActive = active
        sessionPrefs.edit().putBoolean(KEY_GUEST_MODE_ACTIVE, active).apply()
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                setSeekerMode(false)
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    SolanaWalletProviderHost(provider = walletProvider) {
        if (currentUser == null && !seekerModeActive) {
            OnboardingScreen(
                onSeekerWalletConnected = {
                    setSeekerMode(true)
                }
            )
        } else {
            val isLocalSeekerMode = currentUser == null
            val sessionKey = currentUser?.uid
                ?: walletPublicKey
                ?: LocalActakeStore.GUEST_UID
            key(sessionKey, isLocalSeekerMode) {
                AuthenticatedActakeShell(
                    userId = sessionKey,
                    isGuestMode = isLocalSeekerMode,
                    alarmLaunchEvent = alarmLaunchEvent,
                    onSignedInFromGuest = {
                        currentUser = Firebase.auth.currentUser
                        setSeekerMode(false)
                    },
                    onExitGuestMode = {
                        walletProvider.disconnect()
                        setSeekerMode(false)
                    },
                    onSignedOut = {
                        currentUser = null
                        setSeekerMode(!walletProvider.currentPublicKeyOrNull().isNullOrBlank())
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedActakeShell(
    userId: String,
    isGuestMode: Boolean,
    alarmLaunchEvent: AlarmLaunchEvent?,
    onSignedInFromGuest: () -> Unit,
    onExitGuestMode: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ActakeViewModel = viewModel(
        key = "${userId}_$isGuestMode",
        factory = ActakeViewModel.Factory(context, userId, isGuestMode)
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val stakesState by viewModel.stakesState.collectAsStateWithLifecycle()
    val completedStakesState by viewModel.completedStakesState.collectAsStateWithLifecycle()
    val customCategoriesState by viewModel.customCategoriesState.collectAsStateWithLifecycle()
    val squadsState by viewModel.squadsState.collectAsStateWithLifecycle()
    val selectedSquadId by viewModel.selectedSquadId.collectAsStateWithLifecycle()
    val checkinsState by viewModel.checkinsState.collectAsStateWithLifecycle()
    val siwsSession by viewModel.siwsSession.collectAsStateWithLifecycle()
    val activeTarget by viewModel.activeVerificationTarget.collectAsStateWithLifecycle()
    val ringingAlarmTarget by viewModel.ringingAlarmTarget.collectAsStateWithLifecycle()
    val randomizedTargetState by viewModel.randomizedTargetState.collectAsStateWithLifecycle()
    val verificationState by viewModel.verificationState.collectAsStateWithLifecycle()
    val celebrationReceipt by viewModel.celebrationReceipt.collectAsStateWithLifecycle()
    val payoutRedemptionTxSig by viewModel.payoutRedemptionTxSig.collectAsStateWithLifecycle()
    val gpsTelemetry by viewModel.gpsTelemetry.collectAsStateWithLifecycle()
    val selectedHuntZone by viewModel.selectedHuntZone.collectAsStateWithLifecycle()
    val solanaDevnetSol by viewModel.solanaDevnetSol.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val lastChainSync by viewModel.lastChainSync.collectAsStateWithLifecycle()
    val isAuthorizingPayment by viewModel.isAuthorizingPayment.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val profile = (profileState as? DataState.Success)?.data
    val stakes = (stakesState as? DataState.Success)?.data.orEmpty()
    val customCategories = (customCategoriesState as? DataState.Success)?.data.orEmpty()
    val squads = (squadsState as? DataState.Success)?.data.orEmpty()
    val checkins = (checkinsState as? DataState.Success)?.data.orEmpty()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBannerMessage()
    }

    // System AlarmManager / AlarmReceiver intent opens the ringing Alarm Challenge Pop-up first
    LaunchedEffect(alarmLaunchEvent?.eventId) {
        val event = alarmLaunchEvent ?: return@LaunchedEffect
        val matchedStake = stakes.firstOrNull {
            it.status == "ACTIVE" && it.title.equals(event.missionTitle, ignoreCase = true)
        }
        if (matchedStake != null) {
            viewModel.triggerAlarmPopupForStake(
                stake = matchedStake,
                defaultAlarmSound = event.alarmSound
            )
        } else {
            viewModel.triggerAlarmPopupForTarget(
                ActiveVerificationTarget(
                    categoryId = "WAKEUP",
                    categoryContextHint = "Everyday morning hygiene and kitchen objects such as a ceramic coffee mug, toothbrush, sink faucet, or curtains",
                    title = event.missionTitle,
                    missionType = "OBJECT_SNAP",
                    targetPrompt = "AI-randomized item from ${event.missionTitle}",
                    stakeAmount = 15.0,
                    tokenSymbol = "USDC",
                    alarmSound = event.alarmSound
                )
            )
        }
    }

    // Real-time foreground alarm watcher: triggers the AI image + description pop-up automatically
    // the exact minute any active habit stake's scheduled alarm time is reached.
    LaunchedEffect(stakes, currentRoute, ringingAlarmTarget) {
        if (currentRoute == ROUTE_ACTIVE_ALARM || ringingAlarmTarget != null) return@LaunchedEffect
        val activeStakes = stakes.filter { it.status == "ACTIVE" }
        if (activeStakes.isEmpty()) return@LaunchedEffect

        while (true) {
            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val minute = now.get(Calendar.MINUTE)
            val year = now.get(Calendar.YEAR)
            val dayOfYear = now.get(Calendar.DAY_OF_YEAR)

            val dueStake = activeStakes.firstOrNull { stake ->
                stake.wakeHour == hour && stake.wakeMinute == minute
            }
            if (dueStake != null) {
                val occurrenceKey = "$year-$dayOfYear-$hour-$minute-${dueStake.id.ifBlank { dueStake.title }}"
                if (viewModel.markAlarmOccurrenceTriggered(occurrenceKey)) {
                    viewModel.triggerAlarmPopupForStake(
                        stake = dueStake,
                        defaultAlarmSound = profile?.alarmSound ?: "Siren"
                    )
                    break
                }
            }
            delay(1_000L)
        }
    }

    val showFloatingBottomBar = currentRoute in setOf(
        ROUTE_HOME, ROUTE_MISSIONS, ROUTE_HUNTS, ROUTE_SQUADS, ROUTE_HISTORY
    )
    val selectedTab = when (currentRoute) {
        ROUTE_MISSIONS -> ActakeTab.MISSIONS
        ROUTE_HUNTS -> ActakeTab.HUNTS
        ROUTE_SQUADS -> ActakeTab.SQUADS
        ROUTE_HISTORY -> ActakeTab.HISTORY
        else -> ActakeTab.HOME
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    profile = profile,
                    stakes = stakes,
                    squads = squads,
                    solanaDevnetSol = solanaDevnetSol,
                    siwsSession = siwsSession,
                    isGuestMode = isGuestMode,
                    isRefreshing = isRefreshing,
                    lastChainSync = lastChainSync,
                    onRefresh = { viewModel.refreshDashboardFromBlockchain() },
                    onInitiateNewStake = {
                        navController.navigate(ROUTE_MISSIONS) {
                            popUpTo(ROUTE_HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onLaunchLiveVerification = { stake ->
                        viewModel.prepareVerificationFromStake(stake, profile?.alarmSound ?: "Siren")
                        navController.navigate(ROUTE_ACTIVE_ALARM)
                    },
                    onOpenAlarmSettings = {
                        navController.navigate(ROUTE_ALARM_SETTINGS)
                    },
                    onOpenSquads = {
                        navController.navigate(ROUTE_SQUADS) {
                            popUpTo(ROUTE_HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onConnectSeekerWallet = {
                        viewModel.connectOrRefreshSeekerWallet()
                    },
                    onSaveWalletAddress = { address ->
                        viewModel.saveSolanaWalletAddress(address)
                    },
                    onGoogleSignInFromGuest = {
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = onSignedInFromGuest,
                            onAuthError = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            scope = scope
                        )
                    },
                    onExitGuestMode = onExitGuestMode,
                    onSignOut = {
                        signOutFromActake(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = onSignedOut
                        )
                    }
                )
            }

            composable(ROUTE_MISSIONS) {
                MissionsScreen(
                    customCategories = customCategories,
                    siwsSession = siwsSession,
                    isAuthorizingPayment = isAuthorizingPayment,
                    onOpenInstalledMwaWallet = { viewModel.launchInstalledSeekerWallet(context) },
                    onCreateCustomCategory = { name, contextHint ->
                        viewModel.createCustomCategory(name, contextHint)
                    },
                    onLockStake = { title, catId, contextHint, type, mode, amt, tok, h, m, days ->
                        viewModel.lockNewStake(
                            title = title,
                            categoryId = catId,
                            categoryContextHint = contextHint,
                            missionType = type,
                            mode = mode,
                            stakeAmount = amt,
                            tokenSymbol = tok,
                            wakeHour = h,
                            wakeMinute = m,
                            durationDays = days,
                            onComplete = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_HOME) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                )
            }

            composable(ROUTE_HUNTS) {
                val dist = viewModel.distanceToZoneMeters(selectedHuntZone)
                HuntsScreen(
                    profile = profile,
                    selectedZone = selectedHuntZone,
                    gpsTelemetry = gpsTelemetry,
                    distanceMeters = dist,
                    onSelectZone = { viewModel.selectHuntZone(it) },
                    onUpdateGpsLocation = { lat, lon -> viewModel.updateGpsLocation(lat, lon) },
                    onGpsPermissionResult = { viewModel.setGpsPermissionState(it) },
                    onSnapGeoProof = { zone, meters ->
                        viewModel.prepareVerificationFromGeoHunt(zone, meters)
                        navController.navigate(ROUTE_ACTIVE_ALARM)
                    }
                )
            }

            composable(ROUTE_SQUADS) {
                SquadsScreen(
                    squads = squads,
                    selectedSquadId = selectedSquadId,
                    checkins = checkins,
                    onSelectSquad = { viewModel.selectSquad(it) },
                    onCreateSquad = { name, wake, mission, amt, tok ->
                        viewModel.createSquad(name, wake, mission, amt, tok)
                    },
                    onJoinSquad = { poolId ->
                        viewModel.joinSquad(poolId)
                    },
                    onReactCheckin = { squadId, checkinId, isFire ->
                        viewModel.reactToCheckin(squadId, checkinId, isFire)
                    },
                    onCheckInForSquad = { squad ->
                        viewModel.prepareVerificationTarget(
                            ActiveVerificationTarget(
                                squadId = squad.id,
                                categoryId = "WAKEUP",
                                categoryContextHint = "Everyday morning or habit proof item for ${squad.missionTitle}",
                                title = squad.missionTitle,
                                missionType = "OBJECT_SNAP",
                                targetPrompt = "Live real-world photograph proving ${squad.missionTitle}",
                                stakeAmount = squad.stakeAmount,
                                tokenSymbol = squad.tokenSymbol,
                                alarmSound = profile?.alarmSound ?: "Siren"
                            )
                        )
                        navController.navigate(ROUTE_ACTIVE_ALARM)
                    }
                )
            }

            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    completedStakesState = completedStakesState,
                    onCreateMissionClick = {
                        navController.navigate(ROUTE_MISSIONS) {
                            popUpTo(ROUTE_HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(ROUTE_ALARM_SETTINGS) {
                AlarmSettingsScreen(
                    profile = profile,
                    onBack = { navController.popBackStack() },
                    onSaveAlarm = { sound, strictness ->
                        viewModel.saveAlarmSettings(sound, strictness) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(ROUTE_ACTIVE_ALARM) {
                ActiveAlarmScreen(
                    target = activeTarget,
                    randomizedTargetState = randomizedTargetState,
                    verificationState = verificationState,
                    strictnessEnabled = profile?.strictnessEnabled ?: false,
                    onRerollTarget = { viewModel.rollRandomizedTargetForActive() },
                    onRetryPreviewImage = { viewModel.retryReferenceImageGeneration() },
                    onCaptureFrame = { bitmap ->
                        viewModel.verifyCapturedFrame(bitmap) {
                            navController.navigate(ROUTE_CELEBRATION) {
                                popUpTo(ROUTE_ACTIVE_ALARM) { inclusive = true }
                            }
                        }
                    },
                    onRetryReset = { viewModel.resetVerificationState() },
                    onExitCancelled = { navController.popBackStack() }
                )
            }

            composable(ROUTE_CELEBRATION) {
                val receipt = celebrationReceipt ?: CelebrationReceipt(
                    verifiedTimeLabel = "06:00 AM",
                    streakCount = profile?.streakCount ?: 1,
                    weekBitmap = profile?.weekBitmap ?: 127,
                    stakeReturned = 15.0,
                    splitBonus = 3.40,
                    totalEarned = 18.40,
                    tokenSymbol = "USDC",
                    settlementMs = 150L,
                    missionTitle = activeTarget.title,
                    proofHashHex = "",
                    oracleSignatureHex = "",
                    squadId = selectedSquadId.orEmpty(),
                    pointsEarned = 50,
                    verifiedTargetObject = activeTarget.title
                )
                CelebrationScreen(
                    receipt = receipt,
                    payoutRedemptionTxSig = payoutRedemptionTxSig,
                    onRedeemToWallet = { viewModel.redeemPayoutToSeekerWallet() },
                    onDone = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        if (showFloatingBottomBar) {
            ActakeFloatingNavBar(
                currentTab = selectedTab,
                onSelectTab = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(ROUTE_HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = BgObsidian,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Ringing Alarm Pop-up: displays the AI-generated reference image & description first,
        // then redirects to the live camera when the user clicks "Verify Proof".
        val activePopupTarget = ringingAlarmTarget
        if (activePopupTarget != null) {
            AlarmChallengePopupDialog(
                target = activePopupTarget,
                randomizedTargetState = randomizedTargetState,
                onRetryTarget = { viewModel.rollRandomizedTargetForActive() },
                onRetryPreviewImage = { viewModel.retryReferenceImageGeneration() },
                onVerifyProofClick = {
                    viewModel.proceedFromAlarmPopupToLiveCam()
                    navController.navigate(ROUTE_ACTIVE_ALARM) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
