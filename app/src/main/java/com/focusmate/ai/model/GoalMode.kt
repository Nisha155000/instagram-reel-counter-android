package com.focusmate.ai.model

enum class GoalMode(
    val id: String,
    val title: String,
    val defaultLimit: Int,
    val defaultReminder: String,
    val description: String
) {
    INTERVIEW_PREP(
        id = "interview_prep",
        title = "Interview Preparation",
        defaultLimit = 15,
        defaultReminder = "You've reached your Reel limit. Take a break and practise your interview questions.",
        description = "Prepare for technical & behavioural rounds without doomscrolling."
    ),
    STUDY(
        id = "study",
        title = "Study",
        defaultLimit = 20,
        defaultReminder = "Your study goal is waiting. Take a break from scrolling and return to your studies.",
        description = "Keep your study sessions sharp and distraction-free."
    ),
    CODING(
        id = "coding",
        title = "Coding",
        defaultLimit = 20,
        defaultReminder = "Your coding goal is waiting. Spend some time building your project.",
        description = "Spend more time writing code and solving problems."
    ),
    CUSTOM(
        id = "custom",
        title = "Custom Goal",
        defaultLimit = 25,
        defaultReminder = "You've reached your limit! Step away and focus on what matters most today.",
        description = "Design your own focus goal and personalized reminder."
    );

    companion object {
        fun fromId(id: String): GoalMode = entries.firstOrNull { it.id == id } ?: STUDY
    }
}
