package com.focusmate.ai.ui.goals

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusmate.ai.data.FocusRepository
import com.focusmate.ai.model.GoalMode
import com.focusmate.ai.notification.GoalNotificationManager
import com.focusmate.ai.theme.FocusAmber
import com.focusmate.ai.theme.FocusCyan
import com.focusmate.ai.theme.FocusGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GoalsScreen(
    repository: FocusRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by repository.userSettings.collectAsState()
    val todayStats by repository.todayStats.collectAsState()

    var selectedMode by remember { mutableStateOf(settings.goalMode) }
    var reelLimit by remember { mutableFloatStateOf(settings.dailyReelLimit.toFloat()) }
    var timeLimitMin by remember { mutableFloatStateOf(settings.scrollTimeLimitMinutes.toFloat()) }
    var customTitle by remember { mutableStateOf(settings.customGoalTitle) }
    var customMessage by remember { mutableStateOf(settings.customGoalMessage) }
    var overlayToggle by remember { mutableStateOf(settings.overlayEnabled) }

    var isSavedNotificationVisible by remember { mutableStateOf(false) }

    // Sync from settings on initial load or repository update
    LaunchedEffect(settings) {
        selectedMode = settings.goalMode
        reelLimit = settings.dailyReelLimit.toFloat()
        timeLimitMin = settings.scrollTimeLimitMinutes.toFloat()
        customTitle = settings.customGoalTitle
        customMessage = settings.customGoalMessage
        overlayToggle = settings.overlayEnabled
    }

    fun saveCurrentSettings(showToast: Boolean = true) {
        val updated = settings.copy(
            goalMode = selectedMode,
            dailyReelLimit = reelLimit.toInt().coerceAtLeast(1),
            scrollTimeLimitMinutes = timeLimitMin.toInt().coerceAtLeast(1),
            customGoalTitle = customTitle.ifBlank { "My Focus Goal" },
            customGoalMessage = customMessage.ifBlank { selectedMode.defaultReminder },
            overlayEnabled = overlayToggle
        )
        repository.updateSettings(updated)

        if (showToast) {
            Toast.makeText(
                context,
                "Saved: ${updated.dailyReelLimit} Reels Limit, ${updated.scrollTimeLimitMinutes}m Scroll Limit",
                Toast.LENGTH_SHORT
            ).show()
            coroutineScope.launch {
                isSavedNotificationVisible = true
                delay(2500)
                isSavedNotificationVisible = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Focus Goal & Limits",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Set your daily boundaries and keep your goals on track",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AnimatedVisibility(
            visible = isSavedNotificationVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(Color(0xFF0F382A), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Settings Saved Successfully!",
                    color = FocusGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "CHOOSE YOUR MODE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))

        // 4 Mode Selection Cards
        GoalMode.entries.forEach { mode ->
            val isSelected = selectedMode == mode
            val borderColor = if (isSelected) FocusCyan else MaterialTheme.colorScheme.outline
            val containerColor = if (isSelected) Color(0xFF102838) else MaterialTheme.colorScheme.surfaceVariant

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                    .clickable {
                        selectedMode = mode
                        saveCurrentSettings(showToast = false)
                    }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mode.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) FocusCyan else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Text(text = "Selected", fontSize = 11.sp, color = FocusGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mode.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Custom Goal inputs
        if (selectedMode == GoalMode.CUSTOM) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = customTitle,
                onValueChange = { customTitle = it },
                label = { Text("Custom Goal Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customMessage,
                onValueChange = { customMessage = it },
                label = { Text("Personal Reminder Message") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Daily Reel Limit Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DAILY REEL LIMIT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${reelLimit.toInt()} REELS",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FocusCyan
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Slider(
            value = reelLimit,
            onValueChange = { reelLimit = it },
            onValueChangeFinished = { saveCurrentSettings(showToast = true) },
            valueRange = 5f..100f,
            modifier = Modifier.fillMaxWidth()
        )

        // Quick Stepper Chips for Reel Limit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(-5, -1, 1, 5).forEach { step ->
                OutlinedButton(
                    onClick = {
                        val newLimit = (reelLimit + step).coerceIn(1f, 200f)
                        reelLimit = newLimit
                        saveCurrentSettings(showToast = true)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (step > 0) "+$step" else "$step",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Scrolling Time Limit Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SCROLLING TIME LIMIT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FocusAmber
            )
            Text(
                text = "${timeLimitMin.toInt()} MINS",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FocusAmber
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Slider(
            value = timeLimitMin,
            onValueChange = { timeLimitMin = it },
            onValueChangeFinished = { saveCurrentSettings(showToast = true) },
            valueRange = 5f..180f,
            modifier = Modifier.fillMaxWidth()
        )

        // Quick Stepper Chips for Time Limit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(-15, -5, 5, 15).forEach { step ->
                OutlinedButton(
                    onClick = {
                        val newTime = (timeLimitMin + step).coerceIn(1f, 300f)
                        timeLimitMin = newTime
                        saveCurrentSettings(showToast = true)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (step > 0) "+${step}m" else "${step}m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Overlay Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Floating Counter Overlay",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Show live movable counter over Instagram",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = overlayToggle,
                onCheckedChange = {
                    overlayToggle = it
                    saveCurrentSettings(showToast = true)
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Explicit Save Settings button with confirmation
        Button(
            onClick = {
                saveCurrentSettings(showToast = true)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSavedNotificationVisible) FocusGreen else MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                text = if (isSavedNotificationVisible) "Settings Saved!" else "Save Settings",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                val notificationManager = GoalNotificationManager(context)
                val testSettings = settings.copy(
                    goalMode = selectedMode,
                    customGoalTitle = customTitle,
                    customGoalMessage = customMessage
                )
                notificationManager.notifyLimitReached(testSettings, todayStats)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(text = "Trigger Test Limit Alert", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
