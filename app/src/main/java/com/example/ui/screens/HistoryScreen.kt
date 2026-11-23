package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.StakeCommitment
import com.example.ui.components.ActakeMascotImage
import com.example.ui.components.ActakeWordmark
import com.example.ui.components.MissionArtIcon
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentDanger
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldSoft
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgCelebration
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgObsidianElevated
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.DividerSubtle
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.DataState
import java.text.SimpleDateFormat
import java.util.Locale

private enum class TokenFilter(val label: String) {
    ALL("All"),
    USDC("USDC"),
    SKR("SKR")
}

@Composable
fun HistoryScreen(
    completedStakesState: DataState<List<StakeCommitment>>,
    onCreateMissionClick: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(TokenFilter.ALL) }

    val allCompleted = (completedStakesState as? DataState.Success)?.data.orEmpty()
    val filteredStakes = remember(allCompleted, selectedFilter) {
        when (selectedFilter) {
            TokenFilter.ALL -> allCompleted
            TokenFilter.USDC -> allCompleted.filter { it.tokenSymbol.equals("USDC", ignoreCase = true) }
            TokenFilter.SKR -> allCompleted.filter { it.tokenSymbol.equals("SKR", ignoreCase = true) }
        }
    }

    val totalUsdcEarned = remember(allCompleted) {
        allCompleted
            .filter { it.tokenSymbol.equals("USDC", ignoreCase = true) }
            .sumOf { effectiveEarnedAmount(it) }
    }
    val totalSkrEarned = remember(allCompleted) {
        allCompleted
            .filter { it.tokenSymbol.equals("SKR", ignoreCase = true) }
            .sumOf { effectiveEarnedAmount(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCanvas),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .statusBarsPadding()
                .testTag("history_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 14.dp,
                bottom = 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        ActakeWordmark(fontSize = 28.sp)
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMuted
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AccentEmeraldSoft
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = BgCelebration,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.history_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = BgCelebration
                            )
                        }
                    }
                }
            }

            // Obsidian Total Earnings Summary Card
            item {
                HistoryEarningsSummaryCard(
                    completedCount = allCompleted.size,
                    totalUsdcEarned = totalUsdcEarned,
                    totalSkrEarned = totalSkrEarned
                )
            }

            // Token Filter Bar (All | USDC | SKR)
            item {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = BgCard,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TokenFilter.entries.forEach { filter ->
                            val active = filter == selectedFilter
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (active) BgObsidian else Color.Transparent)
                                    .clickable { selectedFilter = filter }
                                    .padding(vertical = 10.dp)
                                    .testTag("history_filter_${filter.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // State-driven Content
            when (completedStakesState) {
                is DataState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentCoral)
                        }
                    }
                }

                is DataState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = AccentDanger.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AccentDanger.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = AccentDanger
                                )
                                Text(
                                    text = completedStakesState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentDanger
                                )
                            }
                        }
                    }
                }

                is DataState.Success -> {
                    if (filteredStakes.isEmpty()) {
                        item {
                            HistoryEmptyStateCard(
                                isFilteredEmpty = allCompleted.isNotEmpty(),
                                activeFilterLabel = selectedFilter.label,
                                onCreateMissionClick = onCreateMissionClick
                            )
                        }
                    } else {
                        items(filteredStakes, key = { it.id }) { stake ->
                            CompletedStakeHistoryCard(stake = stake)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEarningsSummaryCard(
    completedCount: Int,
    totalUsdcEarned: Double,
    totalSkrEarned: Double
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = BgObsidian,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_summary_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(BgObsidian, BgObsidianElevated, BgObsidian)
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_total_earned),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AccentEmerald.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "$completedCount Completed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = String.format(Locale.US, "$%.2f", totalUsdcEarned),
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = Color.White
                        )
                        Text(
                            text = "USDC Earned",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2f", totalSkrEarned),
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = AccentCoral
                            )
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = AccentCoral,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "SKR Earned (0% Fee)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedStakeHistoryCard(
    stake: StakeCommitment
) {
    val earned = effectiveEarnedAmount(stake)
    val completedDateLabel = remember(stake.updatedAt, stake.createdAt) {
        val date = (stake.updatedAt ?: stake.createdAt)?.toDate()
        if (date != null) {
            SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.US).format(date)
        } else {
            stake.wakeTimeLabel
        }
    }

    val iconKey = remember(stake.title, stake.missionType) {
        val lower = stake.title.lowercase(Locale.US)
        when {
            "tooth" in lower -> "toothbrush"
            "gym" in lower -> "gym"
            "bed" in lower -> "bed"
            "study" in lower || "book" in lower -> "study"
            "grass" in lower || stake.missionType == "GEO_HUNT" -> "grass"
            "push" in lower || stake.missionType == "POSE_REP" -> "pushups"
            "sky" in lower -> "sky"
            else -> "water"
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = BgCard,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DividerSubtle.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .testTag("history_item_${stake.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    MissionArtIcon(iconKey = iconKey, size = 48.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stake.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = completedDateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Earned USDC/SKR Payout Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AccentEmeraldSoft
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (stake.tokenSymbol.equals("SKR", ignoreCase = true)) {
                                String.format(Locale.US, "+%.2f", earned)
                            } else {
                                String.format(Locale.US, "+$%.2f", earned)
                            },
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = BgCelebration
                        )
                        Text(
                            text = stake.tokenSymbol,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentEmerald
                        )
                    }
                }
            }

            HorizontalDivider(color = DividerSubtle.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Completed",
                        tint = AccentEmerald,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${stake.completedDays}/${stake.durationDays} days • ${stake.mode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }

                val proofLabel = if (stake.proofHash.isNotBlank()) {
                    "Proof #${stake.proofHash.take(8)}"
                } else {
                    String.format(Locale.US, "Stake $%.2f", stake.stakeAmount)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = BgSurfaceSoft
                ) {
                    Text(
                        text = proofLabel,
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyStateCard(
    isFilteredEmpty: Boolean,
    activeFilterLabel: String,
    onCreateMissionClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = BgCard,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_empty_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(AccentCoralSoft),
                contentAlignment = Alignment.Center
            ) {
                ActakeMascotImage(size = 76.dp)
            }

            Text(
                text = if (isFilteredEmpty) {
                    "No Completed $activeFilterLabel Stakes"
                } else {
                    stringResource(R.string.history_empty_title)
                },
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isFilteredEmpty) {
                    "Switch filters above or stake in $activeFilterLabel to track those payouts here."
                } else {
                    stringResource(R.string.history_empty_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            if (!isFilteredEmpty) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onCreateMissionClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("history_empty_cta")
                ) {
                    Text(
                        text = stringResource(R.string.history_empty_cta),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }
    }
}

private fun effectiveEarnedAmount(stake: StakeCommitment): Double {
    return if (stake.earnedAmount > 0.0) stake.earnedAmount else stake.stakeAmount
}
