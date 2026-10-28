package com.focusmate.ai.model

data class DailyStats(
    val date: String, // YYYY-MM-DD
    val reelCount: Int = 0,
    val estimatedScrollSeconds: Long = 0L,
    val limitReached: Boolean = false,
    val goalMode: String = GoalMode.STUDY.id
)
