package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldSoft
import com.example.ui.theme.AccentOrbLight
import com.example.ui.theme.AccentOrbMid
import com.example.ui.theme.AccentOrbSilver
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.DividerSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.util.Locale

enum class ActakeTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    MISSIONS("missions", "Missions", Icons.Filled.AssignmentTurnedIn, Icons.Outlined.AssignmentTurnedIn),
    HUNTS("hunts", "Hunts", Icons.Filled.Explore, Icons.Outlined.Explore),
    SQUADS("squads", "Squads", Icons.Filled.Groups, Icons.Outlined.Groups),
    HISTORY("history", "History", Icons.Filled.History, Icons.Outlined.History)
}

/**
 * Brand geometric lowercase wordmark: "ac" + "t" with integrated coral checkmark + "ake".
 */
@Composable
fun ActakeWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 26.sp,
    lightOnDark: Boolean = false
) {
    val leftColor = if (lightOnDark) Color.White else TextPrimary
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ac",
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = (-1.2).sp,
            color = leftColor
        )
        Box(contentAlignment = Alignment.TopCenter) {
            Text(
                text = "t",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                letterSpacing = (-1.0).sp,
                color = leftColor
            )
            // Integrated coral checkmark accent over the 't'
            Canvas(
                modifier = Modifier
                    .size((fontSize.value * 0.56f).dp)
                    .offset(x = 2.dp, y = (-1).dp)
            ) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = AccentCoral,
                    start = Offset(w * 0.12f, h * 0.55f),
                    end = Offset(w * 0.42f, h * 0.82f),
                    strokeWidth = w * 0.22f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = AccentCoral,
                    start = Offset(w * 0.42f, h * 0.82f),
                    end = Offset(w * 0.92f, h * 0.14f),
                    strokeWidth = w * 0.22f,
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            text = "ake",
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = (-1.0).sp,
            color = AccentCoral
        )
    }
}

@Composable
fun ActakeMascotImage(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    Image(
        painter = painterResource(id = R.drawable.img_actake_mascot),
        contentDescription = stringResource(R.string.cd_actake_mascot),
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

/**
 * Animated radial metallic orb with brushed sheen and centered Actake mascot.
 */
@Composable
fun MetallicRadialOrb(
    modifier: Modifier = Modifier,
    orbSize: Dp = 156.dp,
    mascotSize: Dp = 104.dp
) {
    val transition = rememberInfiniteTransition(label = "orb_shimmer")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_angle"
    )

    Box(
        modifier = modifier.size(orbSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            // Outer atmospheric glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentOrbMid.copy(alpha = 0.45f),
                        AccentOrbLight.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.12f
                ),
                radius = radius * 1.08f
            )
            // Brushed metallic sweep disc
            rotate(degrees = angle, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            AccentOrbSilver,
                            Color.White,
                            AccentOrbLight,
                            AccentOrbMid.copy(alpha = 0.72f),
                            Color.White,
                            AccentOrbSilver
                        ),
                        center = center
                    ),
                    radius = radius * 0.90f
                )
            }
            // Soft inner depth sheen
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        AccentOrbLight.copy(alpha = 0.35f),
                        AccentOrbMid.copy(alpha = 0.25f)
                    ),
                    center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f),
                    radius = radius * 0.85f
                ),
                radius = radius * 0.86f
            )
            // Crisp specular rim
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = radius * 0.90f,
                style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round)
            )
        }
        ActakeMascotImage(size = mascotSize)
    }
}

@Composable
fun TopStatusPills(
    streakCount: Int,
    usdcBalance: Double,
    onWalletClick: () -> Unit,
    modifier: Modifier = Modifier,
    actPoints: Int? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (actPoints != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AccentCoralSoft,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onWalletClick)
                        .testTag("points_pill")
                ) {
                    Text(
                        text = "$actPoints \$ACT",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCoral,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = BgCard,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onWalletClick)
                    .testTag("streak_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "🔥", fontSize = 13.sp)
                    Text(
                        text = "$streakCount",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = BgCard,
            shadowElevation = 2.dp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onWalletClick)
                .testTag("balance_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(AccentAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = String.format(Locale.US, "$%.2f USDC", usdcBalance),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
fun AvatarCluster(
    extraCount: Int,
    modifier: Modifier = Modifier
) {
    val badges = listOf(
        "A" to Color(0xFF4F46E5),
        "S" to Color(0xFFE86F48),
        "M" to Color(0xFF0EA5E9)
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.forEachIndexed { index, (initial, color) ->
            Box(
                modifier = Modifier
                    .offset(x = (-8 * index).dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Box(
            modifier = Modifier
                .offset(x = (-24).dp)
                .height(30.dp)
                .clip(RoundedCornerShape(50))
                .background(BgSurfaceSoft)
                .border(2.dp, Color.White, RoundedCornerShape(50))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+$extraCount",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun MissionArtIcon(
    iconKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp
) {
    val (icon, bgColor, tintColor) = when (iconKey) {
        "wakeup", "toothbrush" -> Triple(Icons.Outlined.WbSunny, Color(0xFFFFF7ED), AccentCoral)
        "gym" -> Triple(Icons.Outlined.FitnessCenter, Color(0xFFFFF1EB), AccentCoral)
        "bed" -> Triple(Icons.Outlined.Bed, Color(0xFFEEF2FF), Color(0xFF4F46E5))
        "study" -> Triple(Icons.Outlined.MenuBook, Color(0xFFF3E8FF), Color(0xFF7C3AED))
        "grass" -> Triple(Icons.Outlined.Park, AccentEmeraldSoft, AccentEmerald)
        "pushups" -> Triple(Icons.Outlined.SelfImprovement, Color(0xFFFFF7ED), Color(0xFFEA580C))
        "sky" -> Triple(Icons.Outlined.WbSunny, Color(0xFFFEF9C3), Color(0xFFCA8A04))
        "custom" -> Triple(Icons.Outlined.AutoAwesome, Color(0xFFEEF2FF), Color(0xFF4F46E5))
        else -> Triple(Icons.Outlined.WaterDrop, Color(0xFFE0F2FE), Color(0xFF0369A1))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

/**
 * Floating pill navigation bar with mandatory navigationBarsPadding() for gesture/3-button safety.
 */
@Composable
fun ActakeFloatingNavBar(
    currentTab: ActakeTab,
    onSelectTab: (ActakeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = BgCard.copy(alpha = 0.96f),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DividerSubtle.copy(alpha = 0.8f), RoundedCornerShape(32.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActakeTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val containerColor = if (selected) BgObsidian else Color.Transparent
                    val contentColor = if (selected) Color.White else TextMuted

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(containerColor)
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 8.dp)
                            .testTag("nav_tab_${tab.route}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                tint = contentColor,
                                modifier = Modifier.size(21.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
