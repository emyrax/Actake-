package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.solana.SiwsSession
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldSoft
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
fun WalletAccountSheet(
    profile: UserProfile,
    solanaDevnetSol: Double?,
    siwsSession: SiwsSession?,
    isGuestMode: Boolean,
    onDismiss: () -> Unit,
    onConnectSeekerWallet: () -> Unit,
    onSaveWalletAddress: (String) -> Unit,
    onGoogleSignInFromGuest: () -> Unit,
    onExitGuestMode: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var walletInput by remember(profile.walletAddress, siwsSession?.publicKeyBase58) {
        mutableStateOf(profile.walletAddress.ifBlank { siwsSession?.publicKeyBase58.orEmpty() })
    }

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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = AccentCoral
                )
                Column {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "$%.2f USDC • %.0f SKR • %d \$ACT",
                            profile.usdcBalance,
                            profile.skrBalance,
                            profile.actPoints
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = TextMuted
                    )
                }
            }

            // Primary Identity Card: Solana Seeker SIWS Wallet
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BgObsidian,
                modifier = Modifier.fillMaxWidth()
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
                        Text(
                            text = siwsSession?.walletLabel ?: "Solana Seeker SIWS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AccentEmerald.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = if (siwsSession != null) "SIWS ACTIVE" else "CONNECT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (siwsSession != null) {
                        Text(
                            text = siwsSession.publicKeyBase58,
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Nonce: ${siwsSession.nonce} • Auto-reconnect active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    } else {
                        Button(
                            onClick = onConnectSeekerWallet,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Authorize Seeker Wallet (SIWS)")
                        }
                    }
                }
            }

            if (solanaDevnetSol != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AccentEmerald.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Solana Devnet Live Balance: %.4f SOL",
                                solanaDevnetSol
                            ),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = TextPrimary
                        )
                    }
                }
            }

            // Custom Anchor Escrow & Solana Actions / Blinks Card
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
                        text = "Anchor Escrow & Solana Blinks",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Stakes lock in actake_escrow (SPL Token USDC / Token-2022 SKR 0% fee). Share your Blink link to invite friends to stake in one tap.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    OutlinedButton(
                        onClick = {
                            val blinkUrl = com.example.solana.ActakeEscrowProgram.buildSquadBlinkUrl("wake-club")
                            val shareText = "Join my Actake accountability stake on Solana Seeker!\n🔥 Streak: ${profile.streakCount} days • ${profile.actPoints} \$ACT\nTap Blink to stake: $blinkUrl"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Actake Solana Blink Invite")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Solana Blink"))
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("share_solana_blink_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Squad Solana Blink", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            OutlinedTextField(
                value = walletInput,
                onValueChange = { walletInput = it },
                label = { Text("Solana Seeker / Devnet Public Key") },
                placeholder = { Text("Paste Base58 pubkey to sync Devnet SOL") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("solana_wallet_input")
            )

            Button(
                onClick = { onSaveWalletAddress(walletInput) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = BgObsidian),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_wallet_button")
            ) {
                Text("Save & Sync Solana Devnet")
            }

            HorizontalDivider(color = DividerSubtle)

            // Settings -> Account Linking (Strictly Optional Google Cloud Backup)
            Text(
                text = "Account Linking (Optional Backup)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TextMuted
            )

            if (isGuestMode) {
                Button(
                    onClick = onGoogleSignInFromGuest,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("guest_google_sign_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.guest_sign_in_cta))
                }

                OutlinedButton(
                    onClick = onExitGuestMode,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("exit_guest_mode_button")
                ) {
                    Text(stringResource(R.string.guest_exit_cta))
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AccentEmeraldSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Google Cloud Backup Linked • Stakes & Categories synced across devices",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlink Google & Sign Out")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
