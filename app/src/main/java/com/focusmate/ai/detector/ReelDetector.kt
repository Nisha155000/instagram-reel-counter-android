package com.focusmate.ai.detector

import android.content.res.Resources
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusmate.ai.model.DetectionDecision
import com.focusmate.ai.model.ReelSignature
import com.focusmate.ai.detector.TransitionEvidence

class ReelDetector(
    private val listener: ReelDetectorListener,
    private val stateMachine: ReelTransitionStateMachine = ReelTransitionStateMachine()
) {

    companion object {
        private const val TAG = "ReelDetector"
        const val MAX_SEARCH_DEPTH = 30

        private val REEL_ACTION_BUTTONS = hashSetOf(
            "like", "comment", "comments", "share", "remix", "audio", "save", "send", "more", "follow", "following", "reels",
            "close", "cancel", "dismiss", "back", "all", "done", "skip", "hide", "reply", "profile", "options", "report",
            "search", "notifications", "messages", "direct", "camera", "reel", "home", "explore", "clips"
        )
    }

    fun onAppLeftInstagram() {
        val elapsedSec = stateMachine.handleLeavingReels(SystemClock.uptimeMillis())
        if (elapsedSec > 0) {
            listener.onActiveScrollingTime(elapsedSec)
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        val packageName = event.packageName?.toString() ?: ""
        val eventType = event.eventType
        val eventTypeString = AccessibilityEvent.eventTypeToString(eventType)

        if (!packageName.contains("instagram")) {
            onAppLeftInstagram()
            return
        }

        val now = SystemClock.uptimeMillis()

        if (rootNode == null) return

        // Step 1: Check if Comments bottom sheet or Share sheet is open
        if (isCommentsOrShareSheetOpen(rootNode)) {
            val dummySig = ReelSignature(captionSnippet = "Comments Sheet Active")
            listener.onReelIgnored(
                dummySig,
                eventTypeString,
                DetectionDecision.IGNORED_COMMENTS_OPEN,
                "Comments or share bottom-sheet is active; ignoring scroll events inside thread."
            )
            return
        }

        // Profiles, Stories and messages are Instagram surfaces but are not Reels.
        if (isProfileScreen(rootNode) || isStoriesOrDirectChat(rootNode)) {
            val elapsedSec = stateMachine.handleLeavingReels(now)
            if (elapsedSec > 0) {
                listener.onActiveScrollingTime(elapsedSec)
            }
            val dummySig = ReelSignature(captionSnippet = "Profile, Stories or DM Screen")
            listener.onReelIgnored(
                dummySig,
                eventTypeString,
                DetectionDecision.IGNORED_NOT_REELS_SURFACE,
                "Active screen is an Instagram profile, Story or DM; retaining the last Reel identity."
            )
            return
        }

        val reelsSurface = isReelsScreen(rootNode)
        if (!reelsSurface) {
            val elapsedSec = stateMachine.handleLeavingReels(now)
            if (elapsedSec > 0) listener.onActiveScrollingTime(elapsedSec)
            val dummySig = ReelSignature(captionSnippet = "Non-Reels Instagram screen")
            listener.onReelIgnored(
                dummySig,
                eventTypeString,
                DetectionDecision.IGNORED_NOT_REELS_SURFACE,
                "Instagram UI lacks Reels-viewer evidence; no content change or scroll is counted."
            )
            return
        }

        // Candidate identity is derived from on-screen author/audio/caption text;
        // Android/Instagram does not expose a guaranteed Reel ID here.
        val extracted = extractReelSignature(rootNode)
        val source = event.source
        val sourceId = source?.viewIdResourceName.orEmpty()
        val sourceClass = event.className?.toString().orEmpty()
        val sourceLooksLikePager = sourceId.contains("reel", true) || sourceId.contains("clips", true) ||
                sourceId.contains("viewer", true) || sourceId.contains("pager", true) ||
                sourceClass.contains("pager", true) ||
                (source?.isScrollable == true && sourceClass.contains("recycler", true))
        val isScroll = eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val evidence = TransitionEvidence(
            isReelScroll = isScroll && reelsSurface && sourceLooksLikePager,
            fromIndex = if (isScroll && sourceLooksLikePager && event.fromIndex >= 0) event.fromIndex else -1,
            pageIndex = if (isScroll && sourceLooksLikePager && event.toIndex >= 0) event.toIndex else -1,
            scrollY = if (isScroll && sourceLooksLikePager) event.scrollY else -1,
            viewportHeightPx = Resources.getSystem().displayMetrics.heightPixels
        )
        Log.d(
            TAG,
            "OBS type=$eventTypeString surface=REELS sigHash=${extracted.key.hashCode()} " +
                    "sourceId=$sourceId from=${event.fromIndex} to=${event.toIndex} y=${event.scrollY}"
        )

        val scrollFlush = stateMachine.handleEnteringReels(now)
        if (scrollFlush > 0) listener.onActiveScrollingTime(scrollFlush)

        val result = stateMachine.processFrame(extracted, eventTypeString, now, evidence)

        if (result.isNewReel) {
            repeat(result.countDelta) {
                Log.i(TAG, "REEL TRANSITION COUNTED +1 sigHash=${result.signature.key.hashCode()} reason=${result.reason}")
                listener.onReelCounted(result.signature, eventTypeString, result.reason)
            }
        } else {
            listener.onReelIgnored(result.signature, eventTypeString, result.decision, result.reason)
        }

    }

    private fun isReelsScreen(root: AccessibilityNodeInfo): Boolean {
        var hasExplicitViewer = false
        var selectedReelsTab = false
        var hasCommentAction = false
        var hasLikeAction = false
        var hasShareAction = false
        var hasAudioIndicator = false
        val displayMetrics = Resources.getSystem().displayMetrics
        val rightRailActionY = mutableListOf<Int>()

        traverseNodes(root, maxDepth = 20) { node ->
            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            val text = (node.text?.toString() ?: "").lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val isRightRail = bounds.centerX() >= displayMetrics.widthPixels * 0.72f

            if (desc.contains("like") || text.contains("like") || viewId.contains("like")) {
                hasLikeAction = true
                if (isRightRail) rightRailActionY += bounds.centerY()
            }
            if (desc.contains("comment") || text.contains("comment") || viewId.contains("comment")) {
                hasCommentAction = true
                if (isRightRail) rightRailActionY += bounds.centerY()
            }
            if (desc.contains("share") || desc.contains("send") || viewId.contains("share")) {
                hasShareAction = true
                if (isRightRail) rightRailActionY += bounds.centerY()
            }
            if (viewId.contains("reel_viewer") || viewId.contains("clips_viewer") ||
                viewId.contains("clips_video") || viewId.contains("reel_pager") ||
                (viewId.contains("reel") && (viewId.contains("viewer") || viewId.contains("pager") ||
                        viewId.contains("player") || viewId.contains("container")))) {
                hasExplicitViewer = true
            }
            if (node.isSelected && (desc.contains("reels") || text == "reels")) {
                selectedReelsTab = true
            }
            if (desc.contains("original audio") || desc.contains("audio -") || text.contains("original audio") ||
                text.contains("audio -") || text.contains("♪") || desc.contains("♪")) {
                hasAudioIndicator = true
            }

            false // keep scanning
        }

        // Like/comment buttons also exist in the Home feed. Require a Reel viewer
        // structure or the full-screen action rail plus an audio indicator.
        val hasVerticalActionRail = hasLikeAction && hasCommentAction && hasShareAction &&
                rightRailActionY.size >= 3 &&
                (rightRailActionY.maxOrNull()!! - rightRailActionY.minOrNull()!!) >= displayMetrics.heightPixels * 0.12f
        val hasFullScreenReelControls = hasLikeAction && hasCommentAction && hasShareAction && hasAudioIndicator
        return hasExplicitViewer || selectedReelsTab || hasVerticalActionRail || hasFullScreenReelControls
    }

    private fun isProfileScreen(root: AccessibilityNodeInfo): Boolean {
        var hasProfileHeaderId = false
        var hasProfileManagementAction = false
        var hasFollowerLabel = false
        var hasPostsTab = false
        traverseNodes(root, maxDepth = 18) { node ->
            val text = (node.text?.toString() ?: "").trim().lowercase()
            val desc = (node.contentDescription?.toString() ?: "").trim().lowercase()
            val id = (node.viewIdResourceName ?: "").lowercase()
            if (id.contains("user_profile") || id.contains("profile_header") || id.contains("profile_tab_layout")) {
                hasProfileHeaderId = true
            }
            if (text == "edit profile" || text == "share profile" || desc == "edit profile" || desc == "share profile") {
                hasProfileManagementAction = true
            }
            if (text.contains("followers") || text.contains("following") || desc.contains("followers") || desc.contains("following")) {
                hasFollowerLabel = true
            }
            if (text == "posts" || desc == "posts, tab" || id.contains("profile_tab_layout")) {
                hasPostsTab = true
            }
            false
        }
        return hasProfileHeaderId || (hasProfileManagementAction && hasFollowerLabel) ||
                (hasFollowerLabel && hasPostsTab)
    }

    private fun isCommentsOrShareSheetOpen(root: AccessibilityNodeInfo): Boolean {
        var isCommentOrShare = false
        traverseNodes(root, maxDepth = 15) { node ->
            val text = (node.text?.toString() ?: "").lowercase()
            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()

            // Comment input field is active and editable
            if (node.isEditable && (text.contains("comment") || desc.contains("comment") || text.startsWith("add a") || desc.startsWith("add a"))) {
                isCommentOrShare = true
                return@traverseNodes true
            }
            if (viewId.contains("layout_comment_thread_edittext") || viewId.contains("comment_composer") || viewId.contains("comments_recycler_view")) {
                isCommentOrShare = true
                return@traverseNodes true
            }
            if (viewId.contains("direct_share_sheet") || viewId.contains("share_sheet") || viewId.contains("direct_share_container")) {
                isCommentOrShare = true
                return@traverseNodes true
            }
            if (text == "copy link" || desc == "copy link" || text.startsWith("send to ") ||
                desc.startsWith("send to ") || text == "share to" || desc == "share to") {
                isCommentOrShare = true
                return@traverseNodes true
            }
            false
        }
        return isCommentOrShare
    }

    private fun isStoriesOrDirectChat(root: AccessibilityNodeInfo): Boolean {
        var isStories = false
        var isDirectChat = false

        traverseNodes(root, maxDepth = 10) { node ->
            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            val viewId = (node.viewIdResourceName ?: "").lowercase()

            if (desc.contains("story progress") || viewId.contains("story_viewer") || viewId.contains("stories_tray") || viewId.contains("stories_reel_viewer")) {
                isStories = true
                return@traverseNodes true
            }
            if (desc.contains("message...") || viewId.contains("direct_thread") || viewId.contains("row_thread")) {
                isDirectChat = true
                return@traverseNodes true
            }
            false
        }
        return isStories || isDirectChat
    }

    fun extractUsernameFromDescription(desc: String): String {
        if (desc.isBlank()) return ""
        val lower = desc.lowercase()
        if (lower.startsWith("profile picture of ")) {
            return desc.substring("profile picture of ".length).trim().take(35)
        }
        if (lower.endsWith("'s profile picture")) {
            return desc.substring(0, desc.length - "'s profile picture".length).trim().take(35)
        }
        if (lower.startsWith("photo by ") || lower.startsWith("reel by ")) {
            val prefixLen = if (lower.startsWith("photo by ")) "photo by ".length else "reel by ".length
            return desc.substring(prefixLen).trim().take(35)
        }
        if (lower.startsWith("like reel by ")) {
            return desc.substring("like reel by ".length).trim().take(35)
        }
        if (lower.startsWith("like by ")) {
            return desc.substring("like by ".length).trim().take(35)
        }
        return ""
    }

    private fun extractReelSignature(root: AccessibilityNodeInfo): ReelSignature {
        val displayMetrics = try {
            Resources.getSystem().displayMetrics
        } catch (e: Exception) {
            null
        }
        val screenHeight = displayMetrics?.heightPixels ?: 2400
        val screenWidth = displayMetrics?.widthPixels ?: 1080

        val bottomNavThreshold = (screenHeight * 0.88f).toInt()
        val topStatusThreshold = (screenHeight * 0.06f).toInt()
        val reelInfoMinTop = (screenHeight * 0.40f).toInt()

        var creator = ""
        var audio = ""
        var caption = ""

        traverseNodes(root, maxDepth = MAX_SEARCH_DEPTH) { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Exclude bottom navigation bar and top status bar
            val isBottomNav = rect.top >= bottomNavThreshold
            val isTopBar = rect.bottom <= topStatusThreshold

            val viewId = (node.viewIdResourceName ?: "").lowercase()
            val isExplicitTab = viewId.contains("tab_bar") || viewId.contains("tablayout") ||
                    (node.contentDescription?.toString()?.lowercase()?.contains("tab,") == true)

            if (isBottomNav || isExplicitTab) {
                return@traverseNodes false
            }

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val captionCandidate = text.ifEmpty { desc }

            // Exclude video progress indicators e.g. "0:15", "0:05 / 0:30"
            if (text.matches(Regex("^\\d{1,2}:\\d{2}$")) || desc.matches(Regex("^\\d{1,2}:\\d{2}$"))) {
                return@traverseNodes false
            }

            // Exclude centered auto-generated speech subtitles
            val isSubtitle = rect.top < (screenHeight * 0.65) && rect.left > (screenWidth * 0.12) && rect.right < (screenWidth * 0.88) && captionCandidate.length > 3
            if (isSubtitle && !captionCandidate.contains("♪") && !captionCandidate.contains("♫") && !desc.contains("profile")) {
                return@traverseNodes false
            }

            // 1. Creator detection (Strict matching only: no arbitrary single-word fallbacks)
            if (creator.isEmpty() && !isTopBar) {
                val profileFromDesc = extractUsernameFromDescription(desc)
                if (profileFromDesc.isNotBlank() && !REEL_ACTION_BUTTONS.contains(profileFromDesc.lowercase())) {
                    creator = profileFromDesc
                } else if (text.startsWith("@") && text.length in 3..35) {
                    creator = text
                } else if (desc.startsWith("@") && desc.length in 3..35) {
                    creator = desc
                } else if (viewId.contains("profile_name") || viewId.contains("author") || viewId.contains("clips_video_author")) {
                    val candidate = text.ifEmpty { desc }
                    if (candidate.isNotBlank() && !REEL_ACTION_BUTTONS.contains(candidate.lowercase())) {
                        creator = candidate
                    }
                }
            }

            // 2. Audio track detection (Strict: must have musical note or audio keyword)
            if (audio.isEmpty()) {
                val lowerText = text.lowercase()
                val lowerDesc = desc.lowercase()
                if (lowerText.contains("original audio") || lowerText.contains("audio -") ||
                    lowerDesc.contains("original audio") || lowerDesc.contains("audio -") ||
                    text.contains("♪") || text.contains("♫") || desc.contains("♪") || desc.contains("♫") ||
                    viewId.contains("music_title") || viewId.contains("audio_track") || viewId.contains("music_song_title")) {
                    val candidate = text.ifEmpty { desc }
                    if (candidate.length >= 3) {
                        audio = candidate
                    }
                }
            }

            // 3. Caption snippet detection (Must be docked in lower-left card)
            if (caption.isEmpty() && captionCandidate.length >= 10 && !isTopBar && rect.top >= reelInfoMinTop && rect.left < (screenWidth * 0.80)) {
                val lower = captionCandidate.lowercase()
                val isStandardAction = REEL_ACTION_BUTTONS.any { lower == it || lower.startsWith(it) }
                val isProfileDescription = lower.startsWith("profile picture of ") || lower.endsWith("'s profile picture")
                if (!isStandardAction && !isProfileDescription && !captionCandidate.contains("♪") && !captionCandidate.contains("♫") && captionCandidate != creator) {
                    caption = captionCandidate
                }
            }

            false // keep scanning to build complete canonical signature
        }

        return ReelSignature(
            creatorHandle = creator,
            audioTitle = audio,
            captionSnippet = caption.take(30)
        )
    }

    private fun traverseNodes(
        node: AccessibilityNodeInfo?,
        depth: Int = 0,
        maxDepth: Int,
        action: (AccessibilityNodeInfo) -> Boolean
    ) {
        if (node == null || depth > maxDepth) return

        if (action(node)) {
            return
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            traverseNodes(child, depth + 1, maxDepth, action)
        }
    }

    fun resetState() {
        stateMachine.resetState()
    }
}
