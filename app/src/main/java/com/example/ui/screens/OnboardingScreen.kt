package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.solana.LocalSolanaWalletProvider
import com.example.solana.SiwsSession
import kotlinx.coroutines.launch
import com.example.ui.components.ActakeMascotImage
import com.example.ui.components.ActakeWordmark
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralDeep
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldSoft
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgWarmTint
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * Solana Seeker Built-In Wallet First Onboarding (SIWS).
 * Users swipe or tap to authorize via Sign-In With Solana (SIWS) with zero email/password friction.
 * Optional Google Cloud Backup is cleanly tucked inside Settings -> Account Linking.
 */
@Composable
fun OnboardingScreen(
    onSeekerWalletConnected: (SiwsSession) -> Unit
) {
    val walletProvider = LocalSolanaWalletProvider.current
    val walletState by walletProvider.walletState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val mwaDetected = walletState.mwaWalletInstalled
    val isConnecting = walletState.isConnecting

    fun performSiwsConnect() {
        if (isConnecting) return
        scope.launch {
            walletProvider.connectAndSignIn()
                .onSuccess { session ->
                    onSeekerWalletConnected(session)
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgWarmTint, BgCanvas, BgWarmTint)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Brand Lockup
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActakeMascotImage(size = 44.dp)
                    Column {
                        ActakeWordmark(fontSize = 30.sp)
                        Text(
                            text = stringResource(R.string.tagline_onchain),
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.4.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Two-tone Hero Headline
                Text(
                    text = stringResource(R.string.onboarding_headline_top),
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 42.sp,
                    lineHeight = 46.sp,
                    letterSpacing = (-1.2).sp,
                    color = Color(0xFFB5B7C2),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.onboarding_headline_bottom),
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 46.sp,
                    lineHeight = 50.sp,
                    letterSpacing = (-1.2).sp,
                    color = AccentCoral,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Elevated Alabaster Mascot Showcase Card
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = BgCard,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AccentCoralSoft.copy(alpha = 0.65f),
                                    BgCard
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    ActakeMascotImage(size = 196.dp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Hardware Seed Vault / SIWS Detection Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = AccentEmeraldSoft
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (mwaDetected) {
                            "Solana Seeker Seed Vault / MWA Detected"
                        } else {
                            "Sign-In With Solana (SIWS) • Zero Email Needed"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Value Prop Copy
            Text(
                text = buildAnnotatedString {
                    append("Stake USDC or SKR • Snap an ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                        append("AI-randomized target")
                    }
                    append(" at ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                        append("06:00 AM")
                    }
                    append(" • Earn ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AccentCoral)) {
                        append("\$ACT Points")
                    }
                    append(" & split the losers' pot.")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Interactive Coral Swipe-to-Connect Seeker Pill CTA
            SwipeToConnectPill(
                isLoading = isConnecting,
                onSwipeCompleted = { performSiwsConnect() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Direct Tap: Connect Solana Seeker Wallet (SIWS)
            Button(
                onClick = { performSiwsConnect() },
                enabled = !isConnecting,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BgObsidian,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("connect_seeker_wallet_button")
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Signing SIWS Message...",
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_guest_cta),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Optional Google Cloud Backup is available anytime in Settings → Account Linking.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SwipeToConnectPill(
    isLoading: Boolean,
    onSwipeCompleted: () -> Unit
) {
    val density = LocalDensity.current
    var rawOffsetPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(AccentCoral, AccentCoralDeep)
                )
            )
            .testTag("swipe_connect_pill")
    ) {
        val thumbSize = 52.dp
        val paddingPx = with(density) { 5.dp.toPx() }
        val maxDragPx = with(density) { (maxWidth - thumbSize - 10.dp).toPx() }.coerceAtLeast(1f)
        val animatedOffset by animateFloatAsState(
            targetValue = rawOffsetPx,
            animationSpec = spring(),
            label = "swipe_thumb_offset"
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.width(34.dp))
            Text(
                text = if (isLoading) "Authorizing Seeker..." else stringResource(R.string.onboarding_swipe_cta),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row {
                repeat(3) { idx ->
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f + idx * 0.15f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((paddingPx + animatedOffset).roundToInt(), 0) }
                .size(thumbSize)
                .draggable(
                    enabled = !isLoading,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        rawOffsetPx = (rawOffsetPx + delta).coerceIn(0f, maxDragPx)
                    },
                    onDragStopped = {
                        if (rawOffsetPx >= maxDragPx * 0.65f) {
                            onSwipeCompleted()
                        }
                        rawOffsetPx = 0f
                    }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = AccentCoral,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Swipe to connect Solana Seeker",
                        tint = AccentCoral,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
