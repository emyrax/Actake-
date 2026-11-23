package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.StakeCommitment
import com.example.data.repository.SolanaChainSyncSnapshot
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralBorder
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldSoft
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.DividerSubtle
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DeadlineInfo(
    val minutesUntilDeadline: Long,
    val countdownLabel: String,
    val isUrgent: Boolean
)

/**
 * Computes the remaining time until the next daily occurrence of [wakeHour]:[wakeMinute].
 */
fun computeNextDeadlineInfo(
    wakeHour: Int,
    wakeMinute: Int,
    nowMillis: Long = System.currentTimeMillis()
): DeadlineInfo {
    val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val targetCal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, wakeHour.coerceIn(0, 23))
        set(Calendar.MINUTE, wakeMinute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (!after(nowCal)) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    val diffMillis = (targetCal.timeInMillis - nowMillis).coerceAtLeast(60_000L)
    val totalMinutes = diffMillis / 60_000L
    val hours = totalMinutes / 60L
    val mins = totalMinutes % 60L
    val label = when {
        hours >= 1L -> String.format(Locale.US, "Due in %dh %02dm", hours, mins)
        else -> String.format(Locale.US, "Due in %dm", mins.coerceAtLeast(1L))
    }
    return DeadlineInfo(
        minutesUntilDeadline = totalMinutes,
        countdownLabel = label,
        isUrgent = totalMinutes <= 120L
    )
}

/**
 * Maps a StakeCommitment categoryId to its visual icon key.
 */
fun iconKeyForCategory(categoryId: String): String = when (categoryId.uppercase(Locale.US)) {
    "WAKEUP" -> "wakeup"
    "READING" -> "study"
    "GYM" -> "gym"
    "OUTDOORS" -> "grass"
    "HYDRATION" -> "water"
    else -> "custom"
}

/**
 * Standalone Main Dashboard Jetpack Compose component that displays all active habit stakes,
 * their live daily deadlines, progress, pull-to-refresh blockchain sync, and accessible CTAs
 * to initiate a new stake.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    stakes: List<StakeCommitment>,
    isRefreshing: Boolean = false,
    lastChainSync: SolanaChainSyncSnapshot? = null,
    onRefresh: () -> Unit = {},
    onInitiateNewStake: () -> Unit,
    onVerifyStake: (StakeCommitment) -> Unit,
    modifier: Modifier = Modifier
) {
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
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier
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
                .statusBarsPadding()
                .testTag("main_dashboard_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 124.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            activeHabitStakesDashboardItems(
                activeStakes = activeStakes,
                nowMillis = nowMillis,
                isRefreshing = isRefreshing,
                lastChainSync = lastChainSync,
                onRefresh = onRefresh,
                onInitiateNewStake = onInitiateNewStake,
                onVerifyStake = onVerifyStake
            )
        }
    }
}

/**
 * Embeddable LazyListScope section for rendering the Main Dashboard inside a parent LazyColumn
 * without nested scrolling.
 */
fun LazyListScope.activeHabitStakesDashboardItems(
    activeStakes: List<StakeCommitment>,
    nowMillis: Long,
    isRefreshing: Boolean = false,
    lastChainSync: SolanaChainSyncSnapshot? = null,
    onRefresh: () -> Unit = {},
    onInitiateNewStake: () -> Unit,
    onVerifyStake: (StakeCommitment) -> Unit
) {
    val totalLocked = activeStakes.sumOf { it.stakeAmount }

    item(key = "main_dashboard_header") {
        MainDashboardHeader(
            activeCount = activeStakes.size,
            totalLockedAmount = totalLocked,
            onInitiateNewStake = onInitiateNewStake
        )
    }

    item(key = "main_dashboard_chain_sync") {
        BlockchainSyncStatusBar(
            isRefreshing = isRefreshing,
            lastChainSync = lastChainSync,
            onSyncClick = onRefresh
        )
    }

    if (activeStakes.isEmpty()) {
        item(key = "main_dashboard_empty") {
            EmptyActiveStakesCard(onInitiateNewStake = onInitiateNewStake)
        }
    } else {
        items(
            items = activeStakes,
            key = { stake ->
                stake.id.ifBlank { "${stake.title}_${stake.wakeHour}_${stake.wakeMinute}" }
            }
        ) { stake ->
            val deadlineInfo = remember(stake.wakeHour, stake.wakeMinute, nowMillis) {
                computeNextDeadlineInfo(stake.wakeHour, stake.wakeMinute, nowMillis)
            }
            ActiveHabitStakeCard(
                stake = stake,
                deadlineInfo = deadlineInfo,
                onVerifyClick = { onVerifyStake(stake) }
            )
        }

        item(key = "main_dashboard_add_cta") {
            InitiateNewStakeFooterButton(onInitiateNewStake = onInitiateNewStake)
        }
    }
}

@Composable
fun MainDashboardHeader(
    activeCount: Int,
    totalLockedAmount: Double,
    onInitiateNewStake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_active_stakes_list),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AccentCoralSoft
                ) {
                    Text(
                        text = "$activeCount Active",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = AccentCoral,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = String.format(Locale.US, "$%.2f locked across habits", totalLockedAmount),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Button(
            onClick = onInitiateNewStake,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCoral,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .height(48.dp)
                .testTag("dashboard_new_stake_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.dashboard_new_stake_cta),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BlockchainSyncStatusBar(
    isRefreshing: Boolean,
    lastChainSync: SolanaChainSyncSnapshot?,
    onSyncClick: () -> Unit
) {
    val timeLabel = remember(lastChainSync?.syncedAtMillis) {
        lastChainSync?.syncedAtMillis?.let {
            SimpleDateFormat("h:mm a", Locale.US).format(Date(it))
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (lastChainSync != null) AccentEmeraldSoft.copy(alpha = 0.72f) else BgSurfaceSoft,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isRefreshing, onClick = onSyncClick)
            .testTag("dashboard_chain_sync_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRefreshing -> AccentCoral
                                lastChainSync != null -> AccentEmerald
                                else -> TextMuted.copy(alpha = 0.5f)
                            }
                        )
                )
                Text(
                    text = when {
                        isRefreshing -> "Syncing stake status from Solana Devnet..."
                        lastChainSync != null -> String.format(
                            Locale.US,
                            "Solana Slot #%,d • Synced %s",
                            lastChainSync.confirmedSlot,
                            timeLabel.orEmpty()
                        )
                        else -> "Pull down to sync latest Solana stake status"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = "Sync blockchain status",
                    tint = if (isRefreshing) AccentCoral else TextMuted,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (isRefreshing) "Syncing" else "Sync",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isRefreshing) AccentCoral else TextMuted
                )
            }
        }
    }
}

@Composable
fun ActiveHabitStakeCard(
    stake: StakeCommitment,
    deadlineInfo: DeadlineInfo,
    onVerifyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (stake.durationDays > 0) {
        (stake.completedDays.toFloat() / stake.durationDays.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = BgCard,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (deadlineInfo.isUrgent) 1.5.dp else 1.dp,
                color = if (deadlineInfo.isUrgent) AccentCoralBorder else DividerSubtle.copy(alpha = 0.65f),
                shape = RoundedCornerShape(26.dp)
            )
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onVerifyClick)
            .testTag("active_stake_row_${stake.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Category Icon + Habit Title + Stake Amount Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MissionArtIcon(
                        iconKey = iconKeyForCategory(stake.categoryId),
                        size = 46.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stake.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (stake.skrBoosted) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = AccentEmeraldSoft
                                ) {
                                    Text(
                                        text = "0% FEE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AccentEmerald,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BgSurfaceSoft
                            ) {
                                Text(
                                    text = stake.mode.uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "Day ${stake.completedDays}/${stake.durationDays}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AccentCoralSoft
                ) {
                    Text(
                        text = String.format(Locale.US, "$%.0f %s", stake.stakeAmount, stake.tokenSymbol),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCoral,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Explicit Deadline Highlight Strip (Clock + Live Relative Countdown)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (deadlineInfo.isUrgent) AccentCoralSoft else BgSurfaceSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Daily deadline",
                            tint = if (deadlineInfo.isUrgent) AccentCoral else TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Deadline",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                        Text(
                            text = stake.wakeTimeLabel,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = if (deadlineInfo.isUrgent) AccentCoral else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = deadlineInfo.countdownLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = if (deadlineInfo.isUrgent) AccentCoral else TextPrimary
                        )
                    }
                }
            }

            // Completion Progress Bar
            LinearProgressIndicator(
                progress = { progress.coerceAtLeast(0.08f) },
                color = AccentCoral,
                trackColor = BgSurfaceSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            )

            // Bottom Footer: AI Anti-Cheat Metadata + Verify Proof Action Button (48dp touch target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "AI Random Target • ${stake.alarmSound}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onVerifyClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BgObsidian,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .height(44.dp)
                        .testTag("verify_stake_button_${stake.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Verify Proof",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyActiveStakesCard(
    onInitiateNewStake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = BgCard,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DividerSubtle.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(AccentCoralSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = AccentCoral,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "No Active Habit Stakes Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Pick a habit category, set your daily deadline, and lock USDC or SKR to activate AI-verified accountability.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onInitiateNewStake,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BgObsidian,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("dashboard_empty_new_stake_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Initiate New Habit Stake",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun InitiateNewStakeFooterButton(
    onInitiateNewStake: () -> Unit
) {
    OutlinedButton(
        onClick = onInitiateNewStake,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.2.dp, AccentCoralBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AccentCoralSoft.copy(alpha = 0.55f),
            contentColor = AccentCoral
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("dashboard_initiate_new_stake_cta")
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Initiate Another Habit Stake",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
