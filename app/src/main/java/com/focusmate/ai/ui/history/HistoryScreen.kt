package com.focusmate.ai.ui.history

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusmate.ai.data.FocusRepository
import com.focusmate.ai.model.DailyStats
import com.focusmate.ai.theme.FocusAmber
import com.focusmate.ai.theme.FocusCyan
import com.focusmate.ai.theme.FocusGreen
import com.focusmate.ai.theme.FocusRed
import com.focusmate.ai.ui.components.MetricCard

@Composable
fun HistoryScreen(
    repository: FocusRepository,
    modifier: Modifier = Modifier
) {
    val history by repository.historyStats.collectAsState()
    val settings by repository.userSettings.collectAsState()

    val totalReels = history.sumOf { it.reelCount }
    val totalSeconds = history.sumOf { it.estimatedScrollSeconds }
    val totalHours = totalSeconds / 3600
    val totalMinutes = (totalSeconds % 3600) / 60

    val adherenceRate = if (history.isNotEmpty()) {
        val keptUnderLimit = history.count { it.reelCount <= settings.dailyReelLimit }
        (keptUnderLimit.toFloat() / history.size.toFloat() * 100f).toInt()
    } else 100

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "History & Trends",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Track your long-term Instagram Reel mindfulness",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "TOTAL REELS",
                    value = "$totalReels",
                    subtitle = "Past ${history.size} tracked days",
                    badgeColor = FocusCyan,
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "TOTAL SCROLL TIME",
                    value = "${totalHours}h ${totalMinutes}m",
                    subtitle = "Estimated active time",
                    badgeColor = FocusAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            MetricCard(
                title = "GOAL ADHERENCE RATE",
                value = "$adherenceRate%",
                subtitle = "Days kept within daily limits",
                badgeColor = if (adherenceRate >= 80) FocusGreen else FocusAmber,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "DAILY BREAKDOWN",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (history.isEmpty()) {
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
                            text = "No history recorded yet",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Daily statistics will automatically appear here as you browse Instagram.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(history, key = { it.date }) { stats ->
                DailyHistoryCard(stats, settings.dailyReelLimit)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DailyHistoryCard(stats: DailyStats, limit: Int) {
    val isUnder = stats.reelCount <= limit
    val minutes = stats.estimatedScrollSeconds / 60
    val seconds = stats.estimatedScrollSeconds % 60
    val progress = if (limit > 0) (stats.reelCount.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stats.date,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isUnder) FocusGreen else FocusRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isUnder) "Within Limit" else "Limit Exceeded",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUnder) FocusGreen else FocusRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Reels: ${stats.reelCount} / $limit",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Est. Time: ${minutes}m ${seconds}s",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (stats.reelCount >= limit) FocusRed else FocusCyan,
                trackColor = Color(0xFF1E293B)
            )
        }
    }
}
