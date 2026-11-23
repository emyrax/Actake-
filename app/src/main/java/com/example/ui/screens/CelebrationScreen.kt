package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CelebrationReceipt
import com.example.ui.components.ActakeMascotImage
import com.example.ui.components.ActakeWordmark
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldSoft
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgCelebration
import com.example.ui.theme.BgCelebrationDark
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.util.Locale

@Composable
fun CelebrationScreen(
    receipt: CelebrationReceipt,
    payoutRedemptionTxSig: String?,
    onRedeemToWallet: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun shareProofBlink() {
        val poolSegment = receipt.squadId.ifBlank { "wake-club" }
        val verifiedItem = receipt.verifiedTargetObject.ifBlank { receipt.missionTitle }
        val blinkUrl = com.example.solana.ActakeEscrowProgram.buildSquadBlinkUrl(poolSegment)
        val shareText = buildString {
            append("✅ Verified $verifiedItem (${receipt.missionTitle}) at ${receipt.verifiedTimeLabel} on Actake!\n")
            append("🔥 ${receipt.streakCount} day streak • +${receipt.pointsEarned} \$ACT • +$${"%.2f".format(Locale.US, receipt.totalEarned)} ${receipt.tokenSymbol}\n")
            append("Proof SHA-256: ${receipt.proofHashHex.take(16)}...\n")
            append("Join my squad Blink: $blinkUrl")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Actake Verified Proof")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share Proof Blink"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgCelebrationDark, BgCelebration, BgCelebrationDark)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActakeWordmark(fontSize = 30.sp, lightOnDark = true)

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckBox,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.celebration_verified_wake),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = receipt.verifiedTimeLabel,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    letterSpacing = (-1.2).sp,
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFeatureSettings = "tnum"
                    )
                )

                if (receipt.verifiedTargetObject.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "AI Verified Target: ${receipt.verifiedTargetObject}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Floating White Streak + $ACT Points Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = BgCard,
                shadowElevation = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🔥", fontSize = 20.sp)
                            Text(
                                text = "${receipt.streakCount} day streak",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = TextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AccentEmeraldSoft
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "+${receipt.pointsEarned} \$ACT",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dayLabels.forEachIndexed { idx, day ->
                            val verified = ((receipt.weekBitmap shr idx) and 1) == 1
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (verified) AccentEmerald else BgSurfaceSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (verified) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "$day verified",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = day,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Translucent Glass Anchor Escrow & SPL Token Settlement Receipt Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.34f),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "+$%.2f %s Earned",
                            receipt.totalEarned,
                            receipt.tokenSymbol
                        ),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "$%.2f Stake Returned + $%.2f Split • Verified in %dms",
                            receipt.stakeReturned,
                            receipt.splitBonus,
                            receipt.settlementMs
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = Color.White.copy(alpha = 0.90f),
                        textAlign = TextAlign.Center
                    )

                    // Anchor Escrow & SPL Token / Token-2022 Pill
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.24f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (receipt.skrZeroFee) {
                                    "actake_escrow • SKR Token-2022 (0% Fee)"
                                } else {
                                    "actake_escrow • ${receipt.tokenStandardLabel} (${receipt.tokenSymbol})"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (receipt.escrowTxSignature.isNotBlank()) {
                        Text(
                            text = "submit_checkin Tx: ${receipt.escrowTxSignature.take(18)}...",
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.80f)
                        )
                    }

                    if (receipt.proofHashHex.isNotBlank()) {
                        Text(
                            text = "Ed25519 Proof: ${receipt.proofHashHex.take(20)}...",
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.74f)
                        )
                    }
                }
            }

            // Actake Shield Mascot
            ActakeMascotImage(size = 96.dp)

            // Action Buttons: Redeem to Seeker Wallet + Share Proof Blink + Done
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (payoutRedemptionTxSig == null) {
                    Button(
                        onClick = onRedeemToWallet,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCoral,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("celebration_redeem_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.celebration_redeem_wallet),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AccentEmerald.copy(alpha = 0.22f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AccentEmerald, RoundedCornerShape(50))
                            .testTag("celebration_redeemed_badge")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Redeemed • Tx ${payoutRedemptionTxSig.take(18)}...",
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Button(
                    onClick = { shareProofBlink() },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BgCard,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("celebration_share_blink_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.celebration_share_blink),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BgObsidian,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("celebration_done_button")
                ) {
                    Text(
                        text = stringResource(R.string.celebration_done),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
