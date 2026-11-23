package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

/**
 * Material 3 Clock Time Picker Dialog for selecting any custom daily habit deadline.
 * Supports both interactive analog dial clock ([TimePicker]) and direct numeric input ([TimeInput]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineClockPickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = false
    )
    var useKeyboardInput by remember { mutableStateOf(false) }

    val pickerColors = TimePickerDefaults.colors(
        clockDialColor = BgSurfaceSoft,
        clockDialSelectedContentColor = Color.White,
        clockDialUnselectedContentColor = TextPrimary,
        selectorColor = AccentCoral,
        containerColor = BgCard,
        periodSelectorBorderColor = AccentCoral.copy(alpha = 0.45f),
        periodSelectorSelectedContainerColor = AccentCoralSoft,
        periodSelectorUnselectedContainerColor = BgSurfaceSoft,
        periodSelectorSelectedContentColor = AccentCoral,
        periodSelectorUnselectedContentColor = TextMuted,
        timeSelectorSelectedContainerColor = AccentCoralSoft,
        timeSelectorUnselectedContainerColor = BgSurfaceSoft,
        timeSelectorSelectedContentColor = AccentCoral,
        timeSelectorUnselectedContentColor = TextPrimary
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = BgCard,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("deadline_clock_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pick Daily Deadline",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Choose any custom time from the clock",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                    IconButton(
                        onClick = { useKeyboardInput = !useKeyboardInput },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("deadline_clock_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (useKeyboardInput) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                            contentDescription = if (useKeyboardInput) "Switch to dial clock" else "Switch to keyboard time input",
                            tint = AccentCoral
                        )
                    }
                }

                if (useKeyboardInput) {
                    TimeInput(
                        state = timePickerState,
                        colors = pickerColors
                    )
                } else {
                    TimePicker(
                        state = timePickerState,
                        colors = pickerColors
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("deadline_clock_cancel_button")
                    ) {
                        Text(
                            text = "Cancel",
                            color = TextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onTimeSelected(timePickerState.hour, timePickerState.minute)
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BgObsidian,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("deadline_clock_confirm_button")
                    ) {
                        Text(
                            text = "Set Deadline",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
