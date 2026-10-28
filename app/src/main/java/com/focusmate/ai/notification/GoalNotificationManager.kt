package com.focusmate.ai.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.focusmate.ai.MainActivity
import com.focusmate.ai.R
import com.focusmate.ai.model.DailyStats
import com.focusmate.ai.model.UserSettings

class GoalNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "focusmate_limits_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISMISS = "com.focusmate.ai.ACTION_DISMISS_ALERT"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Prevents sending repeated alerts for the same threshold in the same session
    private var lastAlertedCount: Int = -1

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notifyLimitReached(settings: UserSettings, stats: DailyStats) {
        if (lastAlertedCount == stats.reelCount) {
            // Already notified for this exact count
            return
        }
        lastAlertedCount = stats.reelCount

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (settings.goalMode) {
            com.focusmate.ai.model.GoalMode.CUSTOM -> settings.customGoalTitle
            else -> "${settings.goalMode.title} Limit Reached!"
        }

        val message = settings.getActiveReminderMessage()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Reels: ${stats.reelCount} / ${settings.dailyReelLimit}. $message")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("You have watched ${stats.reelCount} Reels today (Daily Limit: ${settings.dailyReelLimit}).\n\n$message")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open FocusMate", pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun resetSessionAlert() {
        lastAlertedCount = -1
    }
}
