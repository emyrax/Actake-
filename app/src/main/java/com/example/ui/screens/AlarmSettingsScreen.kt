package com.example.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.alarm.AlarmAudioPlayer
import com.example.data.model.AlarmSoundOption
import com.example.data.model.UserProfile
import com.example.ui.components.ActakeWordmark
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.DividerSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.util.Locale

@Composable
fun AlarmSettingsScreen(
    profile: UserProfile?,
    onBack: () -> Unit,
    onSaveAlarm: (alarmSound: String, strictnessEnabled: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioPlayer = remember(context) { AlarmAudioPlayer(context) }

    DisposableEffect(Unit) {
        onDispose { audioPlayer.stop() }
    }

    var selectedSound by remember(profile?.alarmSound) {
        mutableStateOf(AlarmSoundOption.fromLabel(profile?.alarmSound ?: "Siren"))
    }
    var strictnessEnabled by remember(profile?.strictnessEnabled) {
        mutableStateOf(profile?.strictnessEnabled ?: true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar with right-aligned Actake brand mark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ActakeWordmark(fontSize = 20.sp)
            }

            // Navigation row: Back circle + Loud Alarms + Streak/USDC summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BgCard,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(44.dp)
                    ) {
                        IconButton(
                            onClick = {
                                audioPlayer.stop()
                                onBack()
                            },
                            modifier = Modifier.testTag("alarm_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = TextPrimary
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.alarm_settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "🔥 ${profile?.streakCount ?: 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = String.format(Locale.US, "$%.2f USDC", profile?.usdcBalance ?: 150.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Text(
                text = stringResource(R.string.alarm_settings_subtitle),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Sound Picker List — Selected sound pops out as elevated pure white card with emerald check
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AlarmSoundOption.entries.forEach { option ->
                    val isSelected = option == selectedSound
                    val horizontalInset by animateDpAsState(
                        targetValue = if (isSelected) 0.dp else 8.dp,
                        animationSpec = spring(),
                        label = "sound_card_inset"
                    )
                    val elevation by animateDpAsState(
                        targetValue = if (isSelected) 12.dp else 0.dp,
                        animationSpec = spring(),
                        label = "sound_card_elevation"
                    )

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = if (isSelected) BgCard else BgSurfaceSoft,
                        shadowElevation = elevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalInset)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.White else DividerSubtle,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .clickable {
                                selectedSound = option
                                audioPlayer.playPreview(option, scope)
                            }
                            .testTag("alarm_sound_${option.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 20.dp,
                                    vertical = if (isSelected) 22.dp else 15.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = option.label,
                                    style = if (isSelected) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = TextPrimary
                                )
                                if (isSelected) {
                                    Text(
                                        text = option.subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected alarm sound",
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(34.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircle,
                                    contentDescription = "Preview ${option.label}",
                                    tint = TextMuted,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Strictness Toggle Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = BgSurfaceSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerSubtle, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.alarm_strictness_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Switch(
                            checked = strictnessEnabled,
                            onCheckedChange = { strictnessEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentCoral
                            ),
                            modifier = Modifier.testTag("alarm_strictness_switch")
                        )
                    }
                    Text(
                        text = stringResource(R.string.alarm_strictness_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Primary Coral CTA: Set as Alarm
            Button(
                onClick = {
                    audioPlayer.stop()
                    onSaveAlarm(selectedSound.label, strictnessEnabled)
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCoral,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("set_alarm_cta_button")
            ) {
                Text(
                    text = stringResource(R.string.alarm_set_cta),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
