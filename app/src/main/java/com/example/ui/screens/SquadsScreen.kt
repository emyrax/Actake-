package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SquadCheckin
import com.example.data.model.SquadPool
import com.example.data.repository.ActakeRepository
import com.example.ui.components.ActakeMascotImage
import com.example.ui.components.ActakeWordmark
import com.example.ui.components.AvatarCluster
import com.example.ui.components.DeadlineClockPickerDialog
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentDanger
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadsScreen(
    squads: List<SquadPool>,
    selectedSquadId: String?,
    checkins: List<SquadCheckin>,
    onSelectSquad: (String) -> Unit,
    onCreateSquad: (name: String, wakeTime: String, mission: String, stakeAmount: Double, token: String) -> Unit,
    onJoinSquad: (poolIdOrBlinkUrl: String) -> Unit,
    onReactCheckin: (squadId: String, checkinId: String, isFire: Boolean) -> Unit,
    onCheckInForSquad: (SquadPool) -> Unit
) {
    val context = LocalContext.current
    val activeSquad = squads.firstOrNull { it.id == selectedSquadId } ?: squads.firstOrNull()

    var showCreateSheet by remember { mutableStateOf(false) }
    var showJoinSheet by remember { mutableStateOf(false) }

    fun shareBlinkLink(squad: SquadPool) {
        val blinkUrl = "https://actake.app/api/actions/join/${squad.id}"
        val shareText = "Join my Actake accountability squad \"${squad.name}\" (${squad.wakeTimeLabel} • $${"%.0f".format(Locale.US, squad.stakeAmount)} ${squad.tokenSymbol} stake)! Snap live proof or lose your stake: $blinkUrl"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join ${squad.name} on Actake")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share Actake Squad Blink"))
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 14.dp,
                bottom = 118.dp + navBarBottom
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar: actake + Squads (N) + Coral + Create Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActakeWordmark(fontSize = 28.sp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.squads_title)} (${squads.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BgCard,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showJoinSheet = true }
                                .testTag("join_squad_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.GroupAdd,
                                    contentDescription = "Join squad by pool ID",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AccentCoral,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showCreateSheet = true }
                                .testTag("create_squad_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.cd_create_squad),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Squad switcher chips when user belongs to multiple squads
            if (squads.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(squads, key = { it.id }) { squad ->
                            val selected = squad.id == activeSquad?.id
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) BgObsidian else BgCard,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onSelectSquad(squad.id) }
                            ) {
                                Text(
                                    text = squad.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Color.White else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Daily Progress & Live Pot Card
            if (activeSquad != null) {
                item {
                    val completionPct = if (activeSquad.participantCount > 0) {
                        ((activeSquad.checkedInCount.toFloat() / activeSquad.participantCount.toFloat()) * 100)
                            .toInt().coerceIn(0, 100)
                    } else 0

                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = BgCard,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.squads_daily_progress),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                ActakeMascotImage(size = 42.dp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.squads_group_completion),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted
                                )
                                Text(
                                    text = "$completionPct% checked in",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            LinearProgressIndicator(
                                progress = { (completionPct / 100f).coerceAtLeast(0.08f) },
                                color = AccentEmerald,
                                trackColor = BgSurfaceSoft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50))
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarCluster(extraCount = activeSquad.participantCount)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(R.string.squads_live_pot),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "$%.0f Total",
                                                activeSquad.potAmount
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = AccentEmerald
                                        )
                                        Text(
                                            text = "•",
                                            color = TextMuted
                                        )
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "$%.0f Slashed",
                                                activeSquad.slashedAmount
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentDanger
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Squad Card + Share Solana Blink + Check-in Proof Feed
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = BgCard,
                        shadowElevation = 5.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Active Squad:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = activeSquad.name,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontSize = 21.sp,
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = AccentCoralSoft,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { shareBlinkLink(activeSquad) }
                                        .testTag("share_squad_blink_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = stringResource(R.string.cd_share_blink),
                                            tint = AccentCoral,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Share Blink",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = AccentCoral,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Stake summary bar
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = BgSurfaceSoft,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "STAKING $%.0f %s • %.0f %s Pot",
                                            activeSquad.stakeAmount,
                                            activeSquad.tokenSymbol,
                                            activeSquad.potAmount,
                                            activeSquad.tokenSymbol
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = activeSquad.wakeTimeLabel,
                                        fontFamily = MonoFontFamily,
                                        fontSize = 12.sp,
                                        color = AccentCoral,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Quick Verify Proof CTA for this Squad
                            Button(
                                onClick = { onCheckInForSquad(activeSquad) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = BgObsidian),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("squad_verify_now_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Post Verified Check-In Proof",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(color = DividerSubtle)

                            // Live Verified Proof Feed
                            if (checkins.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Text(
                                        text = "No verified check-ins yet today",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Snap live Vision AI proof above to be first on the board.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextMuted
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    checkins.forEach { checkin ->
                                        SquadCheckinFeedCard(
                                            checkin = checkin,
                                            onThumbsClick = {
                                                onReactCheckin(activeSquad.id, checkin.id, false)
                                            },
                                            onFireClick = {
                                                onReactCheckin(activeSquad.id, checkin.id, true)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = BgCard,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActakeMascotImage(size = 76.dp)
                            Text(
                                text = "No Accountability Squads Yet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Launch a shared stake pool with a custom deadline or join a friend's Solana Blink.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showJoinSheet = true },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Join Blink", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { showCreateSheet = true },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Create Squad", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateSquadBottomSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, wake, mission, amt, tok ->
                showCreateSheet = false
                onCreateSquad(name, wake, mission, amt, tok)
            }
        )
    }

    if (showJoinSheet) {
        JoinSquadBottomSheet(
            onDismiss = { showJoinSheet = false },
            onJoin = { poolId ->
                showJoinSheet = false
                onJoinSquad(poolId)
            }
        )
    }
}

@Composable
private fun SquadCheckinFeedCard(
    checkin: SquadCheckin,
    onThumbsClick: () -> Unit,
    onFireClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BgObsidian),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = checkin.userName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = "${checkin.userName} • ${checkin.verifiedTimeLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "🔥 ${checkin.streakCount} Streak • ${(checkin.aiConfidence * 100).toInt()}% Vision AI",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentCoral
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Verified check-in",
                tint = AccentEmerald,
                modifier = Modifier.size(26.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgSurfaceSoft,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = checkin.missionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (checkin.aiReason.isNotBlank()) {
                    Text(
                        text = checkin.aiReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Proof #${checkin.proofHash.take(10)}",
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = BgCard,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onThumbsClick)
                        ) {
                            Text(
                                text = "👍 ${checkin.thumbsCount}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = BgCard,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onFireClick)
                        ) {
                            Text(
                                text = "🔥 ${checkin.fireCount}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSquadBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, wake: String, mission: String, stake: Double, token: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var wakeHour by remember { mutableIntStateOf(6) }
    var wakeMinute by remember { mutableIntStateOf(30) }
    var showClockPicker by remember { mutableStateOf(false) }
    var mission by remember { mutableStateOf("Morning Wakeup") }
    var stakeAmount by remember { mutableStateOf("15") }
    val formattedWakeTime = ActakeRepository.formatWakeTime(wakeHour, wakeMinute)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Create Accountability Squad",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                label = { Text("Squad Name") },
                placeholder = { Text("e.g. 6AM Solana Builders Club") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgSurfaceSoft,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showClockPicker = true }
                        .testTag("squad_deadline_clock_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Deadline",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = formattedWakeTime,
                                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Pick squad deadline from clock",
                            tint = AccentCoral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = stakeAmount,
                    onValueChange = { stakeAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Stake (USDC)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = mission,
                onValueChange = { mission = it.take(80) },
                label = { Text("Daily Proof Mission") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val amount = stakeAmount.toDoubleOrNull()?.coerceIn(1.0, 1000.0) ?: 15.0
                    onCreate(
                        name.ifBlank { "6:30AM Wake Squad" },
                        formattedWakeTime,
                        mission.ifBlank { "Morning Wakeup" },
                        amount,
                        "USDC"
                    )
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_create_squad_button")
            ) {
                Text("Launch Squad & Generate Blink", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showClockPicker) {
        DeadlineClockPickerDialog(
            initialHour = wakeHour,
            initialMinute = wakeMinute,
            onDismiss = { showClockPicker = false },
            onTimeSelected = { h, m ->
                wakeHour = h
                wakeMinute = m
                showClockPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinSquadBottomSheet(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Join Squad via Blink or Pool ID",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "Paste a friend's Actake Blink link or pool ID to join their stake pool.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Pool ID or Blink URL") },
                placeholder = { Text("https://actake.app/api/actions/join/...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onJoin(input) },
                    enabled = input.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = BgObsidian),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Join Pool")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
