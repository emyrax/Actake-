package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.alarm.AlarmAudioPlayer
import com.example.data.model.AlarmSoundOption
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralBorder
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentDanger
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldSoft
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.DividerSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.ActiveVerificationTarget
import com.example.viewmodel.RandomizedTargetUiState
import java.util.Locale

/**
 * Non-dismissible pop-up displayed the moment an active habit alarm goes off.
 *
 * Shows the AI-generated reference image and description of the randomized target item,
 * plays the configured loud alarm sound, and requires the user to click **Verify Proof**
 * to redirect straight to the live CameraX scanner to prove they are pointing at that exact item.
 */
@Composable
fun AlarmChallengePopupDialog(
    target: ActiveVerificationTarget,
    randomizedTargetState: RandomizedTargetUiState,
    onRetryTarget: () -> Unit,
    onRetryPreviewImage: () -> Unit,
    onVerifyProofClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioPlayer = remember(context) { AlarmAudioPlayer(context) }
    var isMuted by remember { mutableStateOf(false) }

    LaunchedEffect(target.alarmSound, isMuted) {
        val option = AlarmSoundOption.fromLabel(target.alarmSound)
        if (!isMuted) {
            audioPlayer.startAlarmLoop(option, scope)
        } else {
            audioPlayer.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "alarm_popup_pulse")
    val bellScale by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarm_bell_scale"
    )

    val readyState = randomizedTargetState as? RandomizedTargetUiState.Ready
    val canProceedToLiveCam = readyState != null &&
        (!readyState.isGeneratingCustomPreview || readyState.customPreviewError != null)

    Dialog(
        onDismissRequest = {
            // Intentionally non-dismissible: user must click Verify Proof to open the live cam
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = BgCard,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .border(1.5.dp, AccentCoralBorder, RoundedCornerShape(30.dp))
                .testTag("alarm_challenge_popup_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Pulsing Alarm Badge + Stake at Risk + Mute Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AccentCoralSoft
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = AccentCoral,
                                modifier = Modifier
                                    .size(16.dp)
                                    .scale(bellScale)
                            )
                            Text(
                                text = stringResource(R.string.alarm_popup_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentCoral,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = BgSurfaceSoft
                        ) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "$%.2f %s at risk",
                                    target.stakeAmount,
                                    target.tokenSymbol
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(40.dp)
                                .testTag("alarm_popup_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                                else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute alarm" else "Mute alarm",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Mission Title + Subtitle
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.alarm_popup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                // Scrollable Body: AI-Generated Image + Target Description
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (randomizedTargetState) {
                        is RandomizedTargetUiState.Loading -> {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = BgSurfaceSoft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = AccentCoral,
                                        strokeWidth = 3.5.dp,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = stringResource(R.string.alarm_popup_preparing),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Picking a randomized everyday item via Gemini AI",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        is RandomizedTargetUiState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = AccentCoralSoft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, AccentDanger.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = AccentDanger,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = randomizedTargetState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    OutlinedButton(
                                        onClick = onRetryTarget,
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.2.dp, AccentCoral),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("alarm_popup_retry_target_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = null,
                                            tint = AccentCoral,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.alarm_popup_retry),
                                            color = AccentCoral,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        is RandomizedTargetUiState.Ready -> {
                            // AI-Generated Visual Reference Image
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = BgSurfaceSoft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(216.dp)
                                    .border(1.dp, DividerSubtle, RoundedCornerShape(22.dp))
                                    .testTag("alarm_popup_reference_image")
                            ) {
                                val bmp = randomizedTargetState.generatedPreviewBitmap
                                when {
                                    bmp != null -> {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "AI generated visual reference for ${randomizedTargetState.target.targetObject}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    randomizedTargetState.isGeneratingCustomPreview -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = AccentCoral,
                                                strokeWidth = 3.dp,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Generating studio photo of ${randomizedTargetState.target.targetObject}…",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    else -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                tint = AccentDanger,
                                                modifier = Modifier.size(30.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = randomizedTargetState.customPreviewError
                                                    ?: "Reference image unavailable",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedButton(
                                                onClick = onRetryPreviewImage,
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier.height(44.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Retry Image", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Target Item Title + Full Capture Description Card
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = AccentCoralSoft.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, AccentCoralBorder, RoundedCornerShape(18.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "TARGET ITEM: ${randomizedTargetState.target.targetObject}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = randomizedTargetState.target.visualCue,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary.copy(alpha = 0.9f)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VerifiedUser,
                                            contentDescription = null,
                                            tint = AccentEmerald,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = randomizedTargetState.target.antiCheatRule,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Primary CTA: Verify Proof -> Redirects to Live Camera Verification
                Button(
                    onClick = {
                        audioPlayer.stop()
                        onVerifyProofClick()
                    },
                    enabled = canProceedToLiveCam,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BgObsidian,
                        contentColor = Color.White,
                        disabledContainerColor = BgSurfaceSoft,
                        disabledContentColor = TextMuted
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("alarm_popup_verify_proof_button")
                ) {
                    if (readyState?.isGeneratingCustomPreview == true ||
                        randomizedTargetState is RandomizedTargetUiState.Loading
                    ) {
                        CircularProgressIndicator(
                            color = AccentCoral,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Preparing Item Preview…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.alarm_popup_verify_cta),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
