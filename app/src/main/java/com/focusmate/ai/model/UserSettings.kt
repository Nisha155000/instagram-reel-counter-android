package com.focusmate.ai.model

data class UserSettings(
    val dailyReelLimit: Int = 20,
    val scrollTimeLimitMinutes: Int = 30,
    val goalMode: GoalMode = GoalMode.STUDY,
    val customGoalTitle: String = "My Focus Goal",
    val customGoalMessage: String = "You've reached your limit! Step away and focus on what matters most today.",
    val overlayEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = true
) {
    fun getActiveReminderMessage(): String {
        return if (goalMode == GoalMode.CUSTOM && customGoalMessage.isNotBlank()) {
            customGoalMessage
        } else {
            goalMode.defaultReminder
        }
    }
}
