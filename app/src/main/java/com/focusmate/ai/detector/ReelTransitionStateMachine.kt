package com.focusmate.ai.detector

import com.focusmate.ai.model.DetectionDecision
import com.focusmate.ai.model.ReelSignature
import java.util.Collections
import java.util.LinkedHashSet
import kotlin.math.roundToInt

data class TransitionResult(
    val decision: DetectionDecision,
    val signature: ReelSignature,
    val reason: String,
    val isNewReel: Boolean,
    val countDelta: Int = if (isNewReel) 1 else 0
)

data class TransitionEvidence(
    val isReelScroll: Boolean = false,
    val fromIndex: Int = -1,
    val pageIndex: Int = -1,
    val scrollY: Int = -1,
    val viewportHeightPx: Int = 0
)

class ReelTransitionStateMachine(
    private val debounceThresholdMs: Long = 250L,
    private val maxRecentHistory: Int = 30,
    restoredCurrentKey: String? = null
) {
    var currentSignature: ReelSignature? = null
        private set
    var lastTransitionTimeMs: Long = 0L
        private set
    private var pendingCandidate: ReelSignature? = null
    private var pendingCandidateTimeMs: Long = 0L
    private var pendingObservations: Int = 0
    val recentSignatures: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet<String>())
    var hasRecentScroll: Boolean = false
        private set
    var lastScrollTimeMs: Long = 0L
        private set
    private var currentKey: String? = restoredCurrentKey?.takeIf { it.isNotBlank() }
    private var currentPageIndex: Int = -1
    private var lastReelScrollY: Int = -1
    var isCurrentlyInReels: Boolean = false
        private set
    var sessionStartTimeMs: Long = 0L
        private set
    var lastTimeFlushMs: Long = 0L
        private set

    init {
        currentKey?.let { recentSignatures.add(it) }
    }

    fun processScrollEvent(nowMs: Long) {
        hasRecentScroll = true
        lastScrollTimeMs = nowMs
    }

    fun processFrame(
        signature: ReelSignature,
        eventType: String,
        nowMs: Long,
        evidence: TransitionEvidence = TransitionEvidence()
    ): TransitionResult {
        if (!signature.isValid) {
            return TransitionResult(
                DetectionDecision.IGNORED_EMPTY_CONTENT,
                ReelSignature(nodeFingerprint = "empty_frame"),
                "Loading or incomplete accessibility content; retaining the last confirmed Reel.",
                false
            )
        }
        handleEnteringReels(nowMs)
        val confirmedKey = currentKey
        if (confirmedKey == null) {
            confirm(signature, nowMs, evidence)
            return TransitionResult(
                DetectionDecision.COUNTED, signature,
                "Initial Reel detected upon opening Instagram Reels. Creator: ${signature.creatorHandle.ifEmpty { "Active Reel" }}",
                true
            )
        }
        if (confirmedKey == signature.key) {
            currentSignature = signature
            pendingCandidate = null
            pendingObservations = 0
            val pageDelta = pageDelta(evidence)
            if (pageDelta > 0) {
                currentPageIndex = evidence.pageIndex
                lastReelScrollY = evidence.scrollY
                lastTransitionTimeMs = nowMs
                return TransitionResult(
                    DetectionDecision.COUNTED,
                    signature,
                    "Reels pager index advanced by $pageDelta while the accessible text identity stayed the same. ${describeEvidence(eventType, evidence)}",
                    true,
                    pageDelta
                )
            }
            if (evidence.pageIndex >= 0 && currentPageIndex < 0) currentPageIndex = evidence.pageIndex
            if (evidence.scrollY >= 0 && lastReelScrollY < 0) lastReelScrollY = evidence.scrollY
            val pixelDelta = pixelPageDelta(evidence)
            if (pixelDelta > 0) {
                lastReelScrollY = evidence.scrollY
                lastTransitionTimeMs = nowMs
                return TransitionResult(
                    DetectionDecision.COUNTED,
                    signature,
                    "Reels viewer scroll position crossed $pixelDelta page boundary/boundaries. ${describeEvidence(eventType, evidence)}",
                    true,
                    pixelDelta
                )
            }
            if (evidence.isReelScroll) {
                return TransitionResult(
                    DetectionDecision.IGNORED_UNCERTAIN,
                    signature,
                    "Scroll event occurred, but no changed Reel identity or pager position was exposed; not counting an unverified gesture. ${describeEvidence(eventType, evidence)}",
                    false
                )
            }
            return TransitionResult(
                DetectionDecision.IGNORED_SAME_SIGNATURE,
                signature,
                "Repeated event for the currently confirmed Reel. ${describeEvidence(eventType, evidence)}",
                false
            )
        }

        // A confirmed pager event plus a different signature is enough evidence;
        // never use a time window to suppress a legitimate rapid transition.
        if (evidence.isReelScroll) {
            if (recentSignatures.contains(signature.key)) {
                confirm(signature, nowMs, evidence)
                return TransitionResult(
                    DetectionDecision.IGNORED_PREVIOUS_REEL,
                    signature,
                    "Pager changed to a Reel in recent history; treated as a re-view, not a new distinct Reel.",
                    false
                )
            }
            val pagerDelta = pageDelta(evidence).takeIf { it > 0 } ?: 1
            confirm(signature, nowMs, evidence)
            return TransitionResult(
                DetectionDecision.COUNTED,
                signature,
                "Reels pager event and changed stable content identity confirmed a new Reel. ${describeEvidence(eventType, evidence)}",
                true,
                pagerDelta
            )
        }

        // Instagram versions/OEMs may omit TYPE_VIEW_SCROLLED. In that case,
        // require repeated identical content observations; elapsed time alone
        // is never sufficient. This preserves delayed updates without counting
        // a one-off caption/subtitle mutation.
        if (pendingCandidate?.key != signature.key) {
            val superseded = pendingCandidate
            if (superseded != null &&
                superseded.key != confirmedKey &&
                !recentSignatures.contains(superseded.key) &&
                (pendingObservations >= 2 || superseded.hasStrongContentIdentity)
            ) {
                // Rapid transitions may replace a fully-described Reel before
                // the UI emits a second frame. The next distinct identity
                // confirms that the superseded candidate was actually visible.
                confirm(superseded, nowMs, evidence)
                pendingCandidate = signature
                pendingCandidateTimeMs = nowMs
                pendingObservations = 1
                return TransitionResult(
                    DetectionDecision.COUNTED,
                    superseded,
                    "Rapid content change confirmed the preceding Reel candidate before the next Reel arrived.",
                    true
                )
            }
            pendingCandidate = signature
            pendingCandidateTimeMs = nowMs
            pendingObservations = 1
            return TransitionResult(
                DetectionDecision.IGNORED_UNCERTAIN,
                signature,
                "Different content observed once; waiting for a second matching Reel observation. ${describeEvidence(eventType, evidence)}",
                false
            )
        }
        pendingObservations += 1
        if (pendingObservations < 2 || nowMs - pendingCandidateTimeMs < debounceThresholdMs / 3L) {
            return TransitionResult(
                DetectionDecision.IGNORED_UNCERTAIN,
                signature,
                "Candidate has not yet been independently confirmed. ${describeEvidence(eventType, evidence)}",
                false
            )
        }
        if (recentSignatures.contains(signature.key)) {
            confirm(signature, nowMs, evidence)
            return TransitionResult(DetectionDecision.IGNORED_PREVIOUS_REEL, signature, "Stable content matches a recently viewed Reel; re-view not counted.", false)
        }
        confirm(signature, nowMs, evidence)
        val reason = buildString {
            append("Repeated accessibility observations confirmed a Reel transition without a scroll event. ${describeEvidence(eventType, evidence)} ")
            if (signature.creatorHandle.isNotBlank()) append("Creator: ${signature.creatorHandle}. ")
            if (signature.audioTitle.isNotBlank()) append("Audio: ${signature.audioTitle}. ")
            if (signature.captionSnippet.isNotBlank()) append("Caption: ${signature.captionSnippet.take(25)}")
        }.trim()
        return TransitionResult(DetectionDecision.COUNTED, signature, reason, true)
    }

    private fun confirm(signature: ReelSignature, nowMs: Long, evidence: TransitionEvidence) {
        currentSignature = signature
        currentKey = signature.key
        lastTransitionTimeMs = nowMs
        pendingCandidate = null
        pendingObservations = 0
        hasRecentScroll = false
        if (evidence.pageIndex >= 0) currentPageIndex = evidence.pageIndex
        if (evidence.scrollY >= 0) lastReelScrollY = evidence.scrollY
        remember(signature.key)
    }

    private fun pageDelta(evidence: TransitionEvidence): Int {
        if (!evidence.isReelScroll || evidence.pageIndex < 0) return 0
        val previousPage = if (currentPageIndex >= 0) currentPageIndex else evidence.fromIndex
        if (previousPage < 0) return 0
        return kotlin.math.abs(evidence.pageIndex - previousPage)
    }

    private fun pixelPageDelta(evidence: TransitionEvidence): Int {
        if (!evidence.isReelScroll || evidence.scrollY < 0 || evidence.viewportHeightPx <= 0 || lastReelScrollY < 0) return 0
        val distance = kotlin.math.abs(evidence.scrollY - lastReelScrollY)
        val pageThreshold = evidence.viewportHeightPx * 0.65f
        if (distance < pageThreshold) return 0
        return kotlin.math.max(1, (distance / evidence.viewportHeightPx.toFloat()).roundToInt()).also {
            lastReelScrollY = evidence.scrollY
        }
    }

    private fun remember(key: String) {
        synchronized(recentSignatures) {
            if (recentSignatures.size >= maxRecentHistory) recentSignatures.remove(recentSignatures.iterator().next())
            recentSignatures.add(key)
        }
    }

    private fun describeEvidence(eventType: String, evidence: TransitionEvidence): String =
        "event=$eventType pager=${evidence.isReelScroll} index=${evidence.fromIndex}->${evidence.pageIndex} scrollY=${evidence.scrollY}"

    fun handleEnteringReels(nowMs: Long): Long {
        if (!isCurrentlyInReels) {
            isCurrentlyInReels = true
            sessionStartTimeMs = nowMs
            lastTimeFlushMs = nowMs
            return 0L
        }
        val elapsedSec = (nowMs - lastTimeFlushMs) / 1000
        if (elapsedSec >= 5) {
            lastTimeFlushMs = nowMs
            return elapsedSec
        }
        return 0L
    }

    fun handleLeavingReels(nowMs: Long): Long {
        if (!isCurrentlyInReels) return 0L
        val elapsedSec = (nowMs - lastTimeFlushMs) / 1000
        isCurrentlyInReels = false
        hasRecentScroll = false
        pendingCandidate = null
        return if (elapsedSec > 0) elapsedSec else 0L
    }

    fun resetState() {
        pendingCandidate = null
        pendingCandidateTimeMs = 0L
        pendingObservations = 0
        isCurrentlyInReels = false
        hasRecentScroll = false
        lastScrollTimeMs = 0L
    }
}
