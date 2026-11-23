package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SquadPool
import com.example.data.model.StakeCommitment
import com.example.data.model.UserProfile
import com.example.data.repository.SolanaChainSyncSnapshot
import com.example.solana.SiwsSession
import com.example.solana.SolanaSeekerWalletManager
import com.example.ui.components.ActakeWordmark
import com.example.ui.components.AvatarCluster
import com.example.ui.components.MetallicRadialOrb
import com.example.ui.components.TopStatusPills
import com.example.ui.components.WalletAccountSheet
import com.example.ui.components.activeHabitStakesDashboardItems
import com.example.ui.components.computeNextDeadlineInfo
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentOrbLight
import com.example.ui.theme.AccentOrbMid
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.DividerSubtle
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: UserProfile?,
    stakes: List<StakeCommitment>,
    squads: List<SquadPool>,
    solanaDevnetSol: Double?,
    siwsSession: SiwsSession?,
    isGuestMode: Boolean,
    isRefreshing: Boolean,
    lastChainSync: SolanaChainSyncSnapshot?,
    onRefresh: () -> Unit,
    onBuildAgentAttestationJson: () -> String,
    onInitiateNewStake: () -> Unit,
    onLaunchLiveVerification: (StakeCommitment) -> Unit,
    onOpenAlarmSettings: () -> Unit,
    onOpenSquads: () -> Unit,
    onConnectSeekerWallet: () -> Unit,
    onSaveWalletAddress: (String) -> Unit,
    onGoogleSignInFromGuest: () -> Unit,
    onExitGuestMode: () -> Unit,
    onSignOut: () -> Unit
) {
    val activeProfile = profile ?: UserProfile()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val activeStakes = remember(stakes, nowMillis) {
        stakes
            .filter { it.status == "ACTIVE" }
            .sortedBy { computeNextDeadlineInfo(it.wakeHour, it.wakeMinute, nowMillis).minutesUntilDeadline }
    }
    val nextStake = activeStakes.firstOrNull() ?: StakeCommitment(
        title = "Morning Wakeup",
        categoryId = "WAKEUP",
        stakeAmount = 15.0,
        wakeTimeLabel = "06:00 AM",
        alarmSound = activeProfile.alarmSound,
        durationDays = 7,
        completedDays = 1
    )
    val nextDeadline = remember(nextStake.wakeHour, nextStake.wakeMinute, nowMillis) {
        computeNextDeadlineInfo(nextStake.wakeHour, nextStake.wakeMinute, nowMillis)
    }
    val featuredSquad = squads.firstOrNull()
    var showWalletSheet by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier
            .fillMaxSize()
            .background(BgCanvas)
            .testTag("dashboard_pull_to_refresh"),
        contentAlignment = Alignment.TopCenter,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                containerColor = BgCard,
                color = AccentCoral,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 124.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar: Actake Wordmark + $ACT Points, Streak & USDC Pills
            item(key = "home_top_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ActakeWordmark(fontSize = 30.sp)
                        val walletShort = siwsSession?.shortAddress
                            ?: activeProfile.walletAddress.takeIf { it.length >= 12 }?.let {
                                SolanaSeekerWalletManager.shortenAddress(it)
                            }
                        if (!walletShort.isNullOrBlank()) {
                            Text(
                                text = "Seeker • $walletShort",
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    TopStatusPills(
                        streakCount = activeProfile.streakCount,
                        usdcBalance = activeProfile.usdcBalance,
                        actPoints = activeProfile.actPoints,
                        onWalletClick = { showWalletSheet = true }
                    )
                }
            }

            // Streaks & Deadlines 7-Day Strip
            item(key = "home_week_strip") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.section_streaks_deadlines),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    WeeklyCalendarStrip(weekBitmap = activeProfile.weekBitmap)
                }
            }

            // NEXT STAKE Hero Card (Soonest Deadline + AI-Randomized Proof + Loud Alarms)
            item(key = "home_next_stake_hero") {
                NextStakeCard(
                    stake = nextStake,
                    countdownLabel = nextDeadline.countdownLabel,
                    onVerifyNowClick = { onLaunchLiveVerification(nextStake) },
                    onAlarmSettingsClick = onOpenAlarmSettings
                )
            }

            // Modular MainDashboard Section: Active Habit Stakes with Live Deadlines, Sync Bar & + New Stake CTA
            activeHabitStakesDashboardItems(
                activeStakes = activeStakes,
                nowMillis = nowMillis,
                isRefreshing = isRefreshing,
                lastChainSync = lastChainSync,
                onRefresh = onRefresh,
                onInitiateNewStake = onInitiateNewStake,
                onVerifyStake = onLaunchLiveVerification
            )

            // Active Squad Metallic Orb Hero Card
            item(key = "home_squad_orb") {
                ActiveStakesOrbCard(
                    squadName = featuredSquad?.name ?: "7AM Campus Wake Club",
                    potAmount = featuredSquad?.potAmount ?: 185.0,
                    participantCount = featuredSquad?.participantCount ?: 42,
                    onJoinOrViewClick = onOpenSquads
                )
            }

            // Bottom 2-Column Progress Row (Duration & Balance Locked)
            item(key = "home_progress_metrics") {
                val durationProgress = if (nextStake.durationDays > 0) {
                    (nextStake.completedDays.toFloat() / nextStake.durationDays.toFloat()).coerceIn(0.15f, 1f)
                } else 0.5f
                val lockedProgress = (activeProfile.lockedBalance / (activeProfile.usdcBalance + activeProfile.lockedBalance).coerceAtLeast(1.0))
                    .toFloat().coerceIn(0.15f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CompactProgressMetricCard(
                        title = stringResource(R.string.label_duration),
                        valueText = "${nextStake.completedDays}/${nextStake.durationDays} days",
                        subtitle = "${(durationProgress * 100).toInt()}% progress",
                        progress = durationProgress,
                        modifier = Modifier.weight(1f)
                    )
                    CompactProgressMetricCard(
                        title = stringResource(R.string.label_balance_locked),
                        valueText = String.format(Locale.US, "$%.2f", activeProfile.lockedBalance),
                        subtitle = "${(lockedProgress * 100).toInt()}% locked",
                        progress = lockedProgress,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showWalletSheet) {
        WalletAccountSheet(
            profile = activeProfile,
            solanaDevnetSol = solanaDevnetSol,
            siwsSession = siwsSession,
            isGuestMode = isGuestMode,
            onBuildAgentAttestationJson = onBuildAgentAttestationJson,
            onDismiss = { showWalletSheet = false },
            onConnectSeekerWallet = onConnectSeekerWallet,
            onSaveWalletAddress = {
                onSaveWalletAddress(it)
                showWalletSheet = false
            },
            onGoogleSignInFromGuest = {
                showWalletSheet = false
                onGoogleSignInFromGuest()
            },
            onExitGuestMode = {
                showWalletSheet = false
                onExitGuestMode()
            },
            onSignOut = {
                showWalletSheet = false
                onSignOut()
            }
        )
    }
}

@Composable
private fun WeeklyCalendarStrip(weekBitmap: Int) {
    val cal = remember { Calendar.getInstance() }
    val todayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    val weekDates = remember {
        val base = Calendar.getInstance()
        val offsetToSun = base.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        base.add(Calendar.DAY_OF_YEAR, -offsetToSun)
        List(7) {
            val d = base.get(Calendar.DAY_OF_MONTH)
            base.add(Calendar.DAY_OF_YEAR, 1)
            d
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, label ->
            val isToday = (index + 1) == todayDayOfWeek
            val isVerified = ((weekBitmap shr index) and 1) == 1

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isToday) BgCard else Color.Transparent,
                shadowElevation = if (isToday) 4.dp else 0.dp,
                modifier = Modifier
                    .width(46.dp)
                    .padding(vertical = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) TextPrimary else TextMuted,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                    )
                    Text(
                        text = weekDates[index].toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isVerified) AccentCoralSoft else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isVerified) AccentCoral else DividerSubtle)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveStakesOrbCard(
    squadName: String,
    potAmount: Double,
    participantCount: Int,
    onJoinOrViewClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = BgCard,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onJoinOrViewClick)
            .testTag("active_stakes_orb_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF6F6FC),
                            AccentOrbLight.copy(alpha = 0.42f),
                            AccentOrbMid.copy(alpha = 0.28f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.section_active_stakes),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentCoral)
                        )
                        Text(
                            text = String.format(Locale.US, "$%.0f Pot", potAmount),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                MetallicRadialOrb(
                    orbSize = 148.dp,
                    mascotSize = 98.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = squadName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AvatarCluster(extraCount = participantCount.coerceAtLeast(1))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = BgObsidian
                        ) {
                            Text(
                                text = "JOIN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextStakeCard(
    stake: StakeCommitment,
    countdownLabel: String,
    onVerifyNowClick: () -> Unit,
    onAlarmSettingsClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = BgCard,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("next_stake_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "%s • $%.2f %s • %s",
                    stringResource(R.string.label_next_stake),
                    stake.stakeAmount,
                    stake.tokenSymbol,
                    countdownLabel
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = TextMuted,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stake.wakeTimeLabel,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                letterSpacing = (-1.0).sp,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                color = TextPrimary
            )

            Text(
                text = "AI rolls a fresh ${stake.title} target when you tap Snap Proof",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DividerSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AccentCoralSoft)
                        .clickable(onClick = onVerifyNowClick)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("home_verify_now_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = AccentCoral,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Snap Proof",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = stake.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgSurfaceSoft)
                        .clickable(onClick = onAlarmSettingsClick)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("home_alarm_settings_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = stake.alarmSound,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Loud Alarms",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactProgressMetricCard(
    title: String,
    valueText: String,
    subtitle: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = BgCard,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Text(
                text = "• $valueText",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "($subtitle)",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = BgObsidian,
                trackColor = BgSurfaceSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            )
        }
    }
}
