package com.focusmate.ai.model

data class DetectionLogEvent(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val signature: String,
    val decision: DetectionDecision,
    val reason: String,
    val currentTotalCount: Int
)
