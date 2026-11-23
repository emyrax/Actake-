package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomCategory
import com.example.data.model.DefaultHabitCategories
import com.example.data.model.HabitCategory
import com.example.data.repository.ActakeRepository
import com.example.solana.SiwsSession
import com.example.ui.components.ActakeMascotImage
import com.example.ui.components.ActakeWordmark
import com.example.ui.components.DeadlineClockPickerDialog
import com.example.ui.components.MissionArtIcon
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralBorder
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldSoft
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.DividerSubtle
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Anti-Cheat Category-Only Mission Selection Screen.
 *
 * Fully scrollable vertical layout (`verticalScroll`) with symmetric 2-column category cards,
 * Custom Category creation, live stake preview controls, and Solana Seeker MWA + Anchor Escrow checkout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionsScreen(
    customCategories: List<CustomCategory>,
    siwsSession: SiwsSession? = null,
    isAuthorizingPayment: Boolean = false,
    onOpenInstalledMwaWallet: () -> Unit = {},
    onCreateCustomCategory: (name: String, contextHint: String) -> Unit,
    onLockStake: (
        title: String,
        categoryId: String,
        categoryContextHint: String,
        missionType: String,
        mode: String,
        stakeAmount: Double,
        tokenSymbol: String,
        wakeHour: Int,
        wakeMinute: Int,
        durationDays: Int
    ) -> Unit
) {
    val customHabitCategories = remember(customCategories) {
        customCategories.map { custom ->
            HabitCategory(
                id = custom.id.ifBlank { "custom_${custom.name.lowercase().replace(" ", "_")}" },
                title = custom.name,
                subtitle = custom.contextHint,
                contextHint = custom.contextHint,
                missionType = "OBJECT_SNAP",
                defaultMode = "SOLO",
                defaultStake = 10.0,
                iconKey = "custom",
                isCustom = true
            )
        }
    }
    val allCategories = remember(customHabitCategories) {
        DefaultHabitCategories + customHabitCategories
    }
    val categoryRows = remember(allCategories) {
        allCategories.chunked(2)
    }

    val modes = listOf("SOLO" to "Solo Stake", "GPS_HUNT" to "GPS Hunt", "SQUAD" to "Squads")
    var selectedMode by remember { mutableStateOf("SOLO") }
    var selectedCategory by remember { mutableStateOf(DefaultHabitCategories.first()) }
    var quickStakeAmount by remember { mutableFloatStateOf(15f) }
    var useSkrToken by remember { mutableStateOf(false) }
    var showConfigureSheet by remember { mutableStateOf(false) }
    var showCustomCategorySheet by remember { mutableStateOf(false) }

    // Pre-warm CAMERA + POST_NOTIFICATIONS permissions during stake creation so 6:00 AM alarms never block
    var pendingStakeLockAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val preWarmPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingStakeLockAction?.invoke()
        pendingStakeLockAction = null
    }

    fun preWarmPermissionsThen(action: () -> Unit) {
        val perms = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        pendingStakeLockAction = action
        preWarmPermissionsLauncher.launch(perms)
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()

    val modeDescription = when (selectedMode) {
        "GPS_HUNT" -> "GPS geofence + live AI photo verification before deadline."
        "SQUAD" -> "Stake into a shared pool and split slashed stakes from late members."
        else -> "Beat your daily alarm with an AI-randomized physical item photo."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCanvas),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 118.dp + navBarBottom
                )
                .testTag("missions_scroll_view"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar: Wordmark + Seeker status pill + Mascot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ActakeWordmark(fontSize = 28.sp)
                    Text(
                        text = if (siwsSession != null) {
                            "Seeker • ${siwsSession.shortAddress} • Escrow Ready"
                        } else {
                            "Anti-Cheat AI Habit Staking"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = BgCard,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ActakeMascotImage(size = 36.dp)
                    }
                }
            }

            // Hero Headline + Anti-Cheat Banner
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextPrimary)) {
                            append("Pick a ")
                        }
                        withStyle(SpanStyle(color = AccentCoral)) {
                            append("Habit Category")
                        }
                    },
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 29.sp,
                    letterSpacing = (-0.8).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AccentEmeraldSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Anti-Cheat Active: AI randomizes a fresh object & generates a reference photo when your alarm rings so nothing can be pre-staged.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Segmented Mode Selector + Contextual Description
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        modes.forEach { (modeKey, modeLabel) ->
                            val active = selectedMode == modeKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (active) BgObsidian else Color.Transparent)
                                    .clickable { selectedMode = modeKey }
                                    .padding(vertical = 11.dp)
                                    .testTag("mission_mode_${modeKey.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else TextMuted
                                )
                            }
                        }
                    }
                }
                Text(
                    text = modeDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }

            // Section Header: Habit Categories
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Habit Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = BgSurfaceSoft
                ) {
                    Text(
                        text = "${allCategories.size} Available",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Symmetric 2-Column Category Grid inside ScrollView
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                categoryRows.forEach { rowCategories ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowCategories.forEach { category ->
                            MissionCategoryCard(
                                category = category,
                                isSelected = selectedCategory.id == category.id,
                                onClick = {
                                    selectedCategory = category
                                    quickStakeAmount = category.defaultStake.toFloat().coerceIn(5f, 50f)
                                    showConfigureSheet = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        if (rowCategories.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Dedicated Full-Width "+ Custom Habit Category" Banner Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = AccentCoralSoft.copy(alpha = 0.72f),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = AccentCoralBorder,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { showCustomCategorySheet = true }
                    .testTag("add_custom_category_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(AccentCoral),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Create custom category",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "+ Create Custom Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentCoral
                        )
                        Text(
                            text = "Add your own space (e.g., Electronics Lab, Music Studio) for custom AI targets & images.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Quick Stake Summary & Direct Configure Card
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = BgCard,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerSubtle.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Selected: ${selectedCategory.title}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "$%.0f %s",
                                    quickStakeAmount,
                                    if (useSkrToken) "SKR" else "USDC"
                                ),
                                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "0% Protocol Fee",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "with \$SKR Token-2022 ⚡",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentCoral
                                )
                            }
                            Switch(
                                checked = useSkrToken,
                                onCheckedChange = { useSkrToken = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentCoral
                                ),
                                modifier = Modifier.testTag("skr_boost_switch")
                            )
                        }
                    }

                    // Quick inline stake amount slider ($5 - $50)
                    Slider(
                        value = quickStakeAmount,
                        onValueChange = { quickStakeAmount = (it / 5f).roundToInt() * 5f },
                        valueRange = 5f..50f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCoral,
                            activeTrackColor = AccentCoral,
                            inactiveTrackColor = BgSurfaceSoft
                        )
                    )

                    Button(
                        onClick = { showConfigureSheet = true },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BgObsidian,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("configure_stake_cta")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "Configure & Stake $%.0f %s",
                                quickStakeAmount,
                                if (useSkrToken) "SKR" else "USDC"
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showConfigureSheet) {
        CategoryStakeConfigSheet(
            category = selectedCategory,
            initialMode = selectedMode,
            initialStake = quickStakeAmount,
            initialUseSkr = useSkrToken,
            siwsSession = siwsSession,
            isAuthorizingPayment = isAuthorizingPayment,
            onOpenInstalledMwaWallet = onOpenInstalledMwaWallet,
            onDismiss = { showConfigureSheet = false },
            onConfirmLock = { stakeAmt, tokenSym, wakeHour, wakeMin, days ->
                showConfigureSheet = false
                preWarmPermissionsThen {
                    onLockStake(
                        selectedCategory.title,
                        selectedCategory.id,
                        selectedCategory.contextHint,
                        selectedCategory.missionType,
                        selectedMode,
                        stakeAmt,
                        tokenSym,
                        wakeHour,
                        wakeMin,
                        days
                    )
                }
            }
        )
    }

    if (showCustomCategorySheet) {
        CreateCustomCategorySheet(
            onDismiss = { showCustomCategorySheet = false },
            onCreate = { name, hint ->
                showCustomCategorySheet = false
                onCreateCustomCategory(name, hint)
            }
        )
    }
}

@Composable
private fun MissionCategoryCard(
    category: HabitCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = BgCard,
        shadowElevation = if (isSelected) 8.dp else 3.dp,
        modifier = modifier
            .heightIn(min = 166.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AccentCoral else DividerSubtle.copy(alpha = 0.65f),
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("mission_card_${category.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (category.isCustom) AccentCoralSoft else BgSurfaceSoft
                ) {
                    Text(
                        text = String.format(Locale.US, "$%.0f/d", category.defaultStake),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = if (category.isCustom) AccentCoral else TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected category",
                        tint = AccentCoral,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                MissionArtIcon(iconKey = category.iconKey, size = 50.dp)
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (category.isCustom) "Custom • AI Preview" else category.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (category.isCustom) AccentCoral else TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryStakeConfigSheet(
    category: HabitCategory,
    initialMode: String,
    initialStake: Float,
    initialUseSkr: Boolean,
    siwsSession: SiwsSession?,
    isAuthorizingPayment: Boolean,
    onOpenInstalledMwaWallet: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmLock: (
        stakeAmount: Double,
        tokenSymbol: String,
        wakeHour: Int,
        wakeMinute: Int,
        durationDays: Int
    ) -> Unit
) {
    var stakeAmount by remember(category.id) { mutableFloatStateOf(initialStake.coerceIn(5f, 50f)) }
    var useSkr by remember { mutableStateOf(initialUseSkr) }
    var wakeHour by remember { mutableIntStateOf(6) }
    var wakeMinute by remember { mutableIntStateOf(0) }
    var durationDays by remember { mutableIntStateOf(7) }
    var showClockPicker by remember { mutableStateOf(false) }

    val wakePresets = listOf(
        Triple(5, 30, "05:30 AM"),
        Triple(6, 0, "06:00 AM"),
        Triple(6, 30, "06:30 AM"),
        Triple(7, 0, "07:00 AM")
    )
    val isCustomTime = wakePresets.none { (h, m, _) -> h == wakeHour && m == wakeMinute }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MissionArtIcon(iconKey = category.iconKey, size = 48.dp)
                Column {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "$initialMode • Anti-Cheat AI Randomized Target",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentCoral
                    )
                }
            }

            // Anti-Cheat Explanation Box (NO manual object input allowed!)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BgSurfaceSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = AccentCoral,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Specific target item is locked until alarm time. AI will pick a fresh object matching \"${category.title}\" and render a reference photo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }

            // Stake Amount Slider ($5 - $50)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Stake Amount",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "$%.0f %s",
                            stakeAmount,
                            if (useSkr) "SKR (0% Fee)" else "USDC"
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCoral
                    )
                }
                Slider(
                    value = stakeAmount,
                    onValueChange = { stakeAmount = (it / 5f).roundToInt() * 5f },
                    valueRange = 5f..50f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCoral,
                        activeTrackColor = AccentCoral
                    )
                )
            }

            // Daily Deadline: Custom Clock Picker + Quick Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = AccentCoral,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Daily Deadline",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    if (isCustomTime) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AccentCoralSoft
                        ) {
                            Text(
                                text = "Custom Clock",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentCoral,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Tappable Clock Card to pick any custom time
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isCustomTime) AccentCoralSoft else BgSurfaceSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isCustomTime) 1.5.dp else 1.dp,
                            color = if (isCustomTime) AccentCoral else DividerSubtle,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showClockPicker = true }
                        .testTag("custom_deadline_clock_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = ActakeRepository.formatWakeTime(wakeHour, wakeMinute),
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = "Tap to pick any custom time from clock",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = BgObsidian
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = "Open clock time picker",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Pick Clock",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    wakePresets.forEach { (h, m, label) ->
                        val active = wakeHour == h && wakeMinute == m
                        FilterChip(
                            selected = active,
                            onClick = {
                                wakeHour = h
                                wakeMinute = m
                            },
                            label = {
                                Text(
                                    text = label.removeSuffix(" AM"),
                                    fontSize = 11.sp,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Duration (1d to 30d) & $SKR Boost Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 7, 14, 30).forEach { days ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (durationDays == days) BgObsidian else BgSurfaceSoft,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { durationDays = days }
                        ) {
                            Text(
                                text = "${days}d",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = if (durationDays == days) Color.White else TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = if (useSkr) AccentEmerald else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "\$SKR",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Switch(
                        checked = useSkr,
                        onCheckedChange = { useSkr = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = AccentEmerald,
                            checkedThumbColor = Color.White
                        )
                    )
                }
            }

            // Native Solana Mobile Stack (MWA 2.0 + Anchor Escrow + SPL Token / Token-2022) Breakdown
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BgObsidian,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("seeker_escrow_payment_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = AccentCoral,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = siwsSession?.walletLabel ?: "Solana Seeker MWA 2.0",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AccentEmerald.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = siwsSession?.shortAddress ?: "Seed Vault",
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Anchor Escrow (actake_escrow)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        Text(
                            text = if (useSkr) "0% Fee (SKR Token-2022)" else "SPL Token USDC • join_pool",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = if (useSkr) AccentEmerald else Color.White
                        )
                    }

                    if (siwsSession?.mwaWalletInstalled == true) {
                        OutlinedButton(
                            onClick = onOpenInstalledMwaWallet,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("open_native_mwa_wallet_button")
                        ) {
                            Text(
                                text = "Open Native Solana Seeker MWA Wallet",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onConfirmLock(
                        stakeAmount.toDouble(),
                        if (useSkr) "SKR" else "USDC",
                        wakeHour,
                        wakeMinute,
                        durationDays
                    )
                },
                enabled = !isAuthorizingPayment,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_lock_stake_button")
            ) {
                if (isAuthorizingPayment) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Signing actake_escrow::join_pool…",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Pay & Lock $%.0f %s via Seeker Wallet",
                            stakeAmount,
                            if (useSkr) "SKR" else "USDC"
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
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
private fun CreateCustomCategorySheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, contextHint: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var contextHint by remember { mutableStateOf("") }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Create Custom Habit Category",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "Describe the space or routine. Actake AI will randomize a real physical object from this category and generate a custom visual reference preview.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Category Name") },
                placeholder = { Text("e.g. Electronics Lab or Music Practice") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_category_name_input")
            )

            OutlinedTextField(
                value = contextHint,
                onValueChange = { contextHint = it.take(180) },
                label = { Text("Context Hint for AI Randomizer") },
                placeholder = { Text("e.g. Hardware tools, breadboard, cables on my workbench") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_category_hint_input")
            )

            Button(
                onClick = {
                    onCreate(
                        name.trim().ifBlank { "Workshop Lab" },
                        contextHint.trim().ifBlank { "Everyday physical tools and gear for ${name.trim()}" }
                    )
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = BgObsidian),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_custom_category_button")
            ) {
                Text("Save Custom Category", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
