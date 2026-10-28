package com.focusmate.ai.ui.testing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusmate.ai.data.FocusRepository
import com.focusmate.ai.model.DetectionDecision
import com.focusmate.ai.model.DetectionLogEvent
import com.focusmate.ai.theme.FocusAmber
import com.focusmate.ai.theme.FocusCyan
import com.focusmate.ai.theme.FocusGreen
import com.focusmate.ai.theme.FocusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccuracyTestingScreen(
    repository: FocusRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manualCount by repository.manualVerifiedCount.collectAsState()
    val autoCount by repository.autoDetectedCount.collectAsState()
    val missedCount by repository.missedTransitions.collectAsState()
    val duplicateCount by repository.duplicateCounts.collectAsState()
    val logs by repository.detectionLogs.collectAsState()

    val accuracy = repository.calculateAccuracyPercentage()

    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "COUNTED" -> logs.filter { it.decision == DetectionDecision.COUNTED }
            "IGNORED" -> logs.filter { it.decision != DetectionDecision.COUNTED }
            else -> logs
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Accuracy Testing Screen",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Real-time verification against manual Reel observations",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dual Counter Comparison Card
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Auto Detected
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "AUTOMATIC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FocusCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$autoCount",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Detected Reels",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(80.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )

                        // Manually Verified
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "MANUAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = FocusGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$manualCount",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Verified Swipes",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Manual Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { repository.incrementManualCount() },
                            colors = ButtonDefaults.buttonColors(containerColor = FocusGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("+1 Manual Reel", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { repository.decrementManualCount() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("-1", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { repository.syncManualToAuto() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("Sync", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Accuracy Metrics Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Accuracy %
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .weight(1.2f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "ACCURACY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FocusCyan)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f%%", accuracy),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = if (accuracy >= 90f) FocusGreen else if (accuracy >= 70f) FocusAmber else FocusRed
                        )
                        Text(text = "vs Manual Target", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Missed Transitions
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "MISSED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FocusRed)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$missedCount",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = if (missedCount > 0) FocusRed else MaterialTheme.colorScheme.onSurface
                        )
                        Text(text = "Transitions", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Duplicate Counts
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "DUPLICATES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FocusAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$duplicateCount",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = if (duplicateCount > 0) FocusAmber else MaterialTheme.colorScheme.onSurface
                        )
                        Text(text = "Suppressed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DETECTION EVENT LOGS (${filteredLogs.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val report = buildDiagnosticReport(autoCount, manualCount, missedCount, duplicateCount, accuracy, logs)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("FocusMate Diagnostic Report", report))
                            Toast.makeText(context, "Diagnostic report copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Export Logs", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { repository.clearLogs() },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clear", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${logs.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "COUNTED",
                    onClick = { selectedFilter = "COUNTED" },
                    label = { Text("Counted", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "IGNORED",
                    onClick = { selectedFilter = "IGNORED" },
                    label = { Text("Ignored", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filteredLogs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No detection events logged yet",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Open Instagram and swipe through Reels to see real-time detection rationale.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { log ->
                DetectionLogCard(log)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DetectionLogCard(log: DetectionLogEvent) {
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(log.timestamp))

    val (badgeBg, badgeText, badgeLabel) = when (log.decision) {
        DetectionDecision.COUNTED -> Triple(Color(0xFF0F382A), FocusGreen, "COUNTED +1")
        DetectionDecision.IGNORED_SAME_SIGNATURE -> Triple(Color(0xFF382D0F), FocusAmber, "SAME REEL")
        DetectionDecision.IGNORED_PREVIOUS_REEL -> Triple(Color(0xFF281F38), Color(0xFFCE93D8), "PREVIOUS REEL")
        DetectionDecision.IGNORED_DEBOUNCE -> Triple(Color(0xFF102838), FocusCyan, "DEBOUNCE")
        DetectionDecision.IGNORED_COMMENTS_OPEN -> Triple(Color(0xFF381028), Color(0xFFF48FB1), "COMMENTS OPEN")
        DetectionDecision.IGNORED_NOT_REELS_SURFACE -> Triple(Color(0xFF252525), Color(0xFFB0BEC5), "NOT REEL")
        else -> Triple(Color(0xFF252525), Color(0xFFB0BEC5), log.decision.name)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(badgeText)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Event: ${log.eventType} · Total: ${log.currentTotalCount}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.reason,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (log.signature.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sig: ${log.signature}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun buildDiagnosticReport(
    auto: Int,
    manual: Int,
    missed: Int,
    duplicates: Int,
    accuracy: Float,
    logs: List<DetectionLogEvent>
): String {
    val sb = StringBuilder()
    sb.appendLine("=== FOCUSMATE AI REEL DETECTION DIAGNOSTIC REPORT ===")
    sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
    sb.appendLine("Automatically Detected Count: $auto")
    sb.appendLine("Manually Verified Count:     $manual")
    sb.appendLine("Missed Transitions:          $missed")
    sb.appendLine("Duplicate Events Suppressed: $duplicates")
    sb.appendLine("Accuracy Percentage:         ${String.format(Locale.getDefault(), "%.2f%%", accuracy)}")
    sb.appendLine("Total Events In Log:         ${logs.size}")
    sb.appendLine()
    sb.appendLine("--- RECENT EVENT LOGS ---")
    logs.take(50).forEach { log ->
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(log.timestamp))
        sb.appendLine("[$time] [${log.decision.name}] ${log.reason} | Sig: ${log.signature}")
    }
    return sb.toString()
}
