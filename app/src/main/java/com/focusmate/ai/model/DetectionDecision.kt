package com.focusmate.ai.model

enum class DetectionDecision(val label: String, val isCounted: Boolean) {
    COUNTED("Counted (+1)", true),
    IGNORED_SAME_SIGNATURE("Ignored: Identical Reel", false),
    IGNORED_PREVIOUS_REEL("Ignored: Re-viewed Previous Reel", false),
    IGNORED_DEBOUNCE("Ignored: Debounce Filter", false),
    IGNORED_COMMENTS_OPEN("Ignored: Comments Open", false),
    IGNORED_NOT_REELS_SURFACE("Ignored: Non-Reels Surface", false),
    IGNORED_OUTSIDE_INSTAGRAM("Ignored: App Not in Foreground", false),
    IGNORED_EMPTY_CONTENT("Ignored: Loading / No Reel Content", false),
    IGNORED_UNCERTAIN("Ignored: Transition Not Confirmed", false),
    RESET_MANUAL("Manual Reset", false),
    MANUAL_ADJUSTMENT("Manual Adjustment", true);
}
