package com.focusmate.ai.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusmate.ai.data.FocusRepository
import com.focusmate.ai.service.FloatingCounterService
import com.focusmate.ai.theme.FocusAmber
import com.focusmate.ai.theme.FocusCyan
import com.focusmate.ai.theme.FocusGreen
import com.focusmate.ai.theme.FocusRed
import com.focusmate.ai.ui.components.MetricCard
import com.focusmate.ai.ui.components.ProgressRing
import com.focusmate.ai.ui.components.StatusBadge

@Composable
fun DashboardScreen(
    repository: FocusRepository,
    onNavigateToGoals: () -> Unit,
    onNavigateToTesting: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val todayStats by repository.todayStats.collectAsState()
    val settings by repository.userSettings.collectAsState()
    val isAccessibilityConnected by repository.isAccessibilityConnected.collectAsState()
    val isOverlayActive by repository.isOverlayActive.collectAsState()

    val scrollMinutes = todayStats.estimatedScrollSeconds / 60
    val scrollRemainingSeconds = todayStats.estimatedScrollSeconds % 60
    val formattedTime = "${scrollMinutes}m ${scrollRemainingSeconds}s"

    val remainingReels = kotlin.math.max(0, settings.dailyReelLimit - todayStats.reelCount)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FocusMate AI",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Instagram Reel Mindfulness",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatusBadge(
                label = if (isAccessibilityConnected) "Monitoring ON" else "Service OFF",
                isActive = isAccessibilityConnected
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dadi Mascot Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF131D2E)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FocusCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👵👓🦯",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "DADI FOCUSMATE MASCOT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FocusCyan,
                        letterSpacing = 0.08.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (todayStats.reelCount >= settings.dailyReelLimit) {
                            "\"Beta! Stop scrolling! Dadi's stick is angry! Go focus on your ${settings.goalMode.title}!\""
                        } else if (todayStats.reelCount > 0) {
                            "\"Beta, Dadi is watching from the top of the screen! ${remainingReels} reels left.\""
                        } else {
                            "\"Whenever you open Instagram, Dadi pops up on top with spectacles & stick to count reels & time!\""
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Center Hero Progress Ring
        ProgressRing(
            current = todayStats.reelCount,
            max = settings.dailyReelLimit
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "ESTIMATED SCROLL TIME",
                value = formattedTime,
                subtitle = "Active reel viewing time",
                badgeColor = FocusCyan,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "REMAINING TODAY",
                value = "$remainingReels",
                subtitle = "Until daily limit",
                badgeColor = if (remainingReels == 0) FocusRed else FocusGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Goal Mode Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE GOAL MODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FocusAmber
                    )
                    OutlinedButton(
                        onClick = onNavigateToGoals,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Change", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (settings.goalMode == com.focusmate.ai.model.GoalMode.CUSTOM) settings.customGoalTitle else settings.goalMode.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${settings.getActiveReminderMessage()}\"",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Service & Floating Counter Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DADI FLOATING COUNTER (👵👓🦯)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Opens Over Instagram",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Opens on top of screen when Instagram opens, counting reels & scroll time",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isOverlayActive,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(context, FloatingCounterService::class.java)
                                    FloatingCounterService.isDismissedByUser = false
                                    ContextCompat.startForegroundService(context, intent)
                                }
                            } else {
                                context.stopService(Intent(context, FloatingCounterService::class.java))
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(context, FloatingCounterService::class.java)
                            FloatingCounterService.isDismissedByUser = false
                            ContextCompat.startForegroundService(context, intent)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("👀 Preview Dadi Floater (👵👓🦯) Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (!isAccessibilityConnected) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FocusAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Accessibility Monitoring in Settings", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    launchInstagram(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Open Instagram",
                    color = androidx.compose.ui.graphics.Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onNavigateToTesting,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Testing Screen", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { repository.resetTodayCount() },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Reset Today's Count (Dev)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun launchInstagram(context: Context) {
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage("com.instagram.android")
    if (launchIntent != null) {
        context.startActivity(launchIntent)
    } else {
        // Fallback to browser or store
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com"))
        context.startActivity(webIntent)
    }
}
