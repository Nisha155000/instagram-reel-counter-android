package com.focusmate.ai

import com.focusmate.ai.detector.ReelTransitionStateMachine
import com.focusmate.ai.model.DetectionDecision
import com.focusmate.ai.model.GoalMode
import com.focusmate.ai.model.ReelSignature
import com.focusmate.ai.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class ReelDetectionLogicTest {

    private fun swipeTo(
        sm: ReelTransitionStateMachine,
        signature: ReelSignature,
        scrollAtMs: Long,
        candidateAtMs: Long
    ) = run {
        sm.processScrollEvent(scrollAtMs)
        sm.processFrame(
            signature,
            "TYPE_VIEW_SCROLLED",
            candidateAtMs,
            com.focusmate.ai.detector.TransitionEvidence(isReelScroll = true)
        )
    }

    private fun calculateAccuracy(manual: Int, auto: Int): Float {
        if (manual <= 0) return 100f
        val diff = abs(auto - manual)
        val accuracy = 100f - (diff.toFloat() / manual.toFloat() * 100f)
        return max(0f, accuracy)
    }

    // --- Core State Machine & Duplicate Prevention Tests ---

    @Test
    fun testInitialReelCountedUponOpening() {
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L)
        val initialReel = ReelSignature(
            creatorHandle = "khaby00",
            audioTitle = "♪ Original sound - khaby00",
            captionSnippet = "Simple life hacks"
        )

        val result = sm.processFrame(initialReel, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L)
        assertEquals(DetectionDecision.COUNTED, result.decision)
        assertTrue(result.isNewReel)
        assertEquals(initialReel.key, sm.currentSignature?.key)
        assertTrue(sm.recentSignatures.contains(initialReel.key))
    }

    @Test
    fun testRepeatedEventsForSameReelIgnored_ZeroDuplicateCounts() {
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L)
        val reel1 = ReelSignature(
            creatorHandle = "natgeo",
            audioTitle = "♪ Hans Zimmer Theme",
            captionSnippet = "Amazon rainforest exploration"
        )

        // 1. Initial count
        val firstResult = sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L)
        assertEquals(DetectionDecision.COUNTED, firstResult.decision)
        assertTrue(firstResult.isNewReel)

        // 2. Simulate 20 subsequent frames over 15 seconds of video playback (subtitles, timers, heart animations)
        for (i in 1..20) {
            val playbackTime = 1000L + (i * 750L)
            val repeatedResult = sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = playbackTime)
            assertEquals(
                "Frame $i should be ignored as SAME_SIGNATURE",
                DetectionDecision.IGNORED_SAME_SIGNATURE,
                repeatedResult.decision
            )
            assertFalse(repeatedResult.isNewReel)
        }
    }

    @Test
    fun testNewReelTransitionAfterSwipe_CountedExactlyOnce() {
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L)
        val reel1 = ReelSignature(creatorHandle = "chef_john", audioTitle = "Cooking beats", captionSnippet = "Easy pasta")
        val reel2 = ReelSignature(creatorHandle = "travel_diaries", audioTitle = "Sunset lo-fi", captionSnippet = "Kyoto in autumn")

        // 1. Reel 1 opens at t = 1000ms
        sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L)

        // 2. User swipes up at t = 5000ms
        sm.processScrollEvent(nowMs = 5000L)

        // 3. Reel 2 settles at t = 5400ms (400ms after swipe, >180ms debounce)
        val result = sm.processFrame(
            reel2,
            "TYPE_VIEW_SCROLLED",
            nowMs = 5400L,
            evidence = com.focusmate.ai.detector.TransitionEvidence(isReelScroll = true)
        )
        assertEquals(DetectionDecision.COUNTED, result.decision)
        assertTrue(result.isNewReel)
        assertEquals(reel2.key, sm.currentSignature?.key)
    }

    @Test
    fun testRapidConsecutiveSkips_FlicksAt200ms_CountedSuccessfully() {
        // High-speed skipping: user flicks every ~200-220ms through multiple reels
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L)

        val reel1 = ReelSignature(creatorHandle = "creator1", audioTitle = "sound1")
        val reel2 = ReelSignature(creatorHandle = "creator2", audioTitle = "sound2")
        val reel3 = ReelSignature(creatorHandle = "creator3", audioTitle = "sound3")
        val reel4 = ReelSignature(creatorHandle = "creator4", audioTitle = "sound4")

        // Reel 1 at t = 1000ms
        val r1 = sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L)
        assertEquals(DetectionDecision.COUNTED, r1.decision)

        // Quick flick to Reel 2 at t = 1210ms (210ms later -> exceeds 180ms debounce)
        val r2 = swipeTo(sm, reel2, scrollAtMs = 1100L, candidateAtMs = 1200L)
        assertEquals(DetectionDecision.COUNTED, r2.decision)
        assertTrue(r2.isNewReel)

        // Quick flick to Reel 3 at t = 1420ms (210ms later)
        val r3 = swipeTo(sm, reel3, scrollAtMs = 1400L, candidateAtMs = 1500L)
        assertEquals(DetectionDecision.COUNTED, r3.decision)
        assertTrue(r3.isNewReel)

        // Quick flick to Reel 4 at t = 1630ms (210ms later)
        val r4 = swipeTo(sm, reel4, scrollAtMs = 1700L, candidateAtMs = 1800L)
        assertEquals(DetectionDecision.COUNTED, r4.decision)
        assertTrue(r4.isNewReel)
    }

    @Test
    fun testUltraFastFlickBelowDebounce_BufferedAndConfirmedOnNextFrame() {
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L)
        val reel1 = ReelSignature(creatorHandle = "user1", audioTitle = "sound1")
        val reel2 = ReelSignature(creatorHandle = "user2", audioTitle = "sound2")

        // Reel 1 counted at t = 1000ms
        sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L)

        // Ultra-fast transient frame arrives only 100ms later (< 180ms debounce)
        sm.processScrollEvent(nowMs = 1050L)
        val fastResult = sm.processFrame(reel2, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1100L)
        assertEquals(DetectionDecision.IGNORED_UNCERTAIN, fastResult.decision)
        assertFalse(fastResult.isNewReel)

        // Settling frame for reel2 arrives 70ms later (at t = 1170ms; 70ms since pending candidate)
        val settledResult = sm.processFrame(reel2, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1170L)
        assertEquals(DetectionDecision.COUNTED, settledResult.decision)
        assertTrue(settledResult.isNewReel)
        assertEquals(reel2.key, sm.currentSignature?.key)

        val repeatedSettledEvent = sm.processFrame(reel2, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1230L)
        assertEquals(DetectionDecision.IGNORED_SAME_SIGNATURE, repeatedSettledEvent.decision)
        assertFalse(repeatedSettledEvent.isNewReel)
    }

    @Test
    fun testReturningToPreviouslyViewedReelIgnored_NoDoubleCounting() {
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L)
        val reel1 = ReelSignature(creatorHandle = "creator1", audioTitle = "sound1")
        val reel2 = ReelSignature(creatorHandle = "creator2", audioTitle = "sound2")

        // Count Reel 1
        sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L)
        // Swipe to Reel 2
        swipeTo(sm, reel2, scrollAtMs = 1800L, candidateAtMs = 1900L)

        // User swipes back down to Reel 1 at t = 3500ms
        val backResult = swipeTo(sm, reel1, scrollAtMs = 3400L, candidateAtMs = 3500L)
        assertEquals(DetectionDecision.IGNORED_PREVIOUS_REEL, backResult.decision)
        assertFalse(backResult.isNewReel)
    }

    @Test
    fun testLRUBufferEviction_AllowsCountingAfterMaxHistory() {
        val maxHistory = 3
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L, maxRecentHistory = maxHistory)

        val reelA = ReelSignature(creatorHandle = "reelA")
        val reelB = ReelSignature(creatorHandle = "reelB")
        val reelC = ReelSignature(creatorHandle = "reelC")
        val reelD = ReelSignature(creatorHandle = "reelD")

        sm.processFrame(reelA, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L) // in buffer
        swipeTo(sm, reelB, 1200L, 1300L) // in buffer
        swipeTo(sm, reelC, 1700L, 1800L) // in buffer
        swipeTo(sm, reelD, 2200L, 2300L) // evicts reelA!

        assertFalse(sm.recentSignatures.contains(reelA.key))
        assertTrue(sm.recentSignatures.contains(reelB.key))
        assertTrue(sm.recentSignatures.contains(reelC.key))
        assertTrue(sm.recentSignatures.contains(reelD.key))

        // Returning to reelA after it was evicted can now be counted as a new view
        val returnResult = swipeTo(sm, reelA, 2700L, 2800L)
        assertEquals(DetectionDecision.COUNTED, returnResult.decision)
        assertTrue(returnResult.isNewReel)
    }

    @Test
    fun testLoadingDelayTransientFramesIgnored_DoesNotBreakCurrentSignature() {
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L)
        val reel1 = ReelSignature(creatorHandle = "creator1", audioTitle = "audio1")
        val emptyFrame = ReelSignature() // completely empty / invalid during loading

        sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L)

        // Loading frame during network lag or fast swipe
        val emptyResult = sm.processFrame(emptyFrame, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 2000L)
        assertEquals(DetectionDecision.IGNORED_EMPTY_CONTENT, emptyResult.decision)
        assertFalse(emptyResult.isNewReel)

        // Current signature is preserved
        assertEquals(reel1.key, sm.currentSignature?.key)
    }

    @Test
    fun testAppSwitchingLeavesAndResumesSession_PreservesSignature_NoDoubleCounting() {
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L)
        val reel1 = ReelSignature(creatorHandle = "creator1", audioTitle = "audio1")

        // Start session on Reel 1 at t = 1000ms
        val firstResult = sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L)
        assertEquals(DetectionDecision.COUNTED, firstResult.decision)
        assertTrue(sm.isCurrentlyInReels)

        // User switches away to WhatsApp at t = 10000ms (9 seconds active scrolling)
        val elapsed = sm.handleLeavingReels(nowMs = 10000L)
        assertEquals(9L, elapsed)
        assertFalse(sm.isCurrentlyInReels)

        // User returns to Instagram after 10 seconds, looking at the exact same Reel
        val resumeResult = sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 20000L)
        assertEquals(DetectionDecision.IGNORED_SAME_SIGNATURE, resumeResult.decision)
        assertFalse(resumeResult.isNewReel)
        assertTrue(sm.isCurrentlyInReels)
    }

    @Test
    fun testRestoredCurrentSignaturePreventsRecountAfterServiceRestart() {
        val reel = ReelSignature(creatorHandle = "creator", captionSnippet = "Previously counted reel")
        val sm = ReelTransitionStateMachine(restoredCurrentKey = reel.key)

        val resumed = sm.processFrame(reel, "TYPE_WINDOW_CONTENT_CHANGED", 10_000L)

        assertEquals(DetectionDecision.IGNORED_SAME_SIGNATURE, resumed.decision)
        assertFalse(resumed.isNewReel)
    }

    @Test
    fun testOpeningAndClosingCommentsSheet_PreservesSignature_NoDoubleCounting() {
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 180L)
        val reel1 = ReelSignature(creatorHandle = "creator1", captionSnippet = "Watch till end")

        // Reel 1 counted
        sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1000L)

        // User taps comment button at t = 3000ms
        // While comments sheet is open, comments thread events are ignored
        // User closes comments sheet at t = 8000ms and returns to Reel 1
        val returnResult = sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 8000L)
        assertEquals(DetectionDecision.IGNORED_SAME_SIGNATURE, returnResult.decision)
        assertFalse(returnResult.isNewReel)
    }

    @Test
    fun testScrollingOnSameReel_DoesNotCountAgain_ZeroInaccurateCounts() {
        val sm = ReelTransitionStateMachine(debounceThresholdMs = 250L)
        val reel1 = ReelSignature(creatorHandle = "natgeo", audioTitle = "rainforest_audio")

        // Initial Reel counted
        val r1 = sm.processFrame(reel1, "TYPE_VIEW_SCROLLED", nowMs = 1000L)
        assertEquals(DetectionDecision.COUNTED, r1.decision)

        // User drags / scrolls finger across the screen on the SAME reel
        sm.processScrollEvent(nowMs = 1100L)
        val r2 = sm.processFrame(reel1, "TYPE_VIEW_SCROLLED", nowMs = 1150L)
        assertEquals(DetectionDecision.IGNORED_SAME_SIGNATURE, r2.decision)

        // Another micro-scroll event while still on Reel 1
        sm.processScrollEvent(nowMs = 1300L)
        val r3 = sm.processFrame(reel1, "TYPE_WINDOW_CONTENT_CHANGED", nowMs = 1350L)
        assertEquals(DetectionDecision.IGNORED_SAME_SIGNATURE, r3.decision)
        assertFalse(r3.isNewReel)
    }

    @Test
    fun testMissingOrIncompleteSignatureHandling() {
        val onlyCreator = ReelSignature(creatorHandle = "photographer")
        assertTrue(onlyCreator.isValid)
        assertTrue(onlyCreator.key.contains("photographer"))

        val onlyAudio = ReelSignature(audioTitle = "Trending Beats vol. 1")
        assertTrue(onlyAudio.isValid)
        assertTrue(onlyAudio.key.contains("trending beats"))

        val onlyCaption = ReelSignature(captionSnippet = "5 simple habits for deep work")
        assertTrue(onlyCaption.isValid)
        assertTrue(onlyCaption.key.contains("5 simple habits"))

        val completelyEmpty = ReelSignature()
        assertFalse(completelyEmpty.isValid)
    }

    // --- Key Uniqueness & Accuracy Formula Tests ---

    @Test
    fun testAccuracyCalculationFormula_exactMatch() {
        val accuracy = calculateAccuracy(manual = 100, auto = 100)
        assertEquals(100f, accuracy, 0.01f)
    }

    @Test
    fun testAccuracyCalculationFormula_withMissedTransitions() {
        // Manual 100, Auto 92 (8 missed) -> 92.0%
        val accuracy = calculateAccuracy(manual = 100, auto = 92)
        assertEquals(92f, accuracy, 0.01f)
    }

    @Test
    fun testAccuracyCalculationFormula_withDuplicateOvercounts() {
        // Manual 100, Auto 105 (5 false positives / duplicates) -> 95.0%
        val accuracy = calculateAccuracy(manual = 100, auto = 105)
        assertEquals(95f, accuracy, 0.01f)
    }

    @Test
    fun testAccuracyCalculationFormula_zeroManualVerifiedReturnsHundred() {
        val accuracy = calculateAccuracy(manual = 0, auto = 0)
        assertEquals(100f, accuracy, 0.01f)
    }

    @Test
    fun testReelSignature_keyUniquenessAndMatching() {
        val reel1 = ReelSignature(
            creatorHandle = "@tech_creator",
            audioTitle = "Original audio - tech_creator",
            captionSnippet = "5 tips to ace system design"
        )

        val reel1Duplicate = ReelSignature(
            creatorHandle = "@tech_creator",
            audioTitle = "Original audio - tech_creator",
            captionSnippet = "5 tips to ace system design"
        )

        val reel2 = ReelSignature(
            creatorHandle = "@fitness_pro",
            audioTitle = "Workout Beats vol. 2",
            captionSnippet = "Morning mobility routine"
        )

        assertTrue(reel1.isValid)
        assertTrue(reel2.isValid)
        assertEquals(reel1.key, reel1Duplicate.key)
        assertFalse(reel1.key == reel2.key)
    }

    @Test
    fun testCaptionSeparatesReelsFromSameCreatorUsingSameAudio() {
        val reelA = ReelSignature(
            creatorHandle = "same_creator",
            audioTitle = "Original audio",
            captionSnippet = "First recipe with fresh tomatoes"
        )
        val reelB = reelA.copy(captionSnippet = "Second recipe with fresh basil")

        assertFalse(reelA.key == reelB.key)
    }

    @Test
    fun testRepeatedScrollEventsDoNotCountUnlessPagerPositionChanges() {
        val sm = ReelTransitionStateMachine()
        val sameVisibleSignature = ReelSignature(creatorHandle = "creator", audioTitle = "same audio")
        sm.processFrame(sameVisibleSignature, "TYPE_WINDOW_CONTENT_CHANGED", 1_000L)

        val baseline = sm.processFrame(
            sameVisibleSignature, "TYPE_VIEW_SCROLLED", 1_100L,
            com.focusmate.ai.detector.TransitionEvidence(isReelScroll = true, pageIndex = 8, viewportHeightPx = 1_000)
        )
        val pageAdvanced = sm.processFrame(
            sameVisibleSignature, "TYPE_VIEW_SCROLLED", 1_120L,
            com.focusmate.ai.detector.TransitionEvidence(isReelScroll = true, pageIndex = 9, viewportHeightPx = 1_000)
        )
        val repeatedEvent = sm.processFrame(
            sameVisibleSignature, "TYPE_VIEW_SCROLLED", 1_130L,
            com.focusmate.ai.detector.TransitionEvidence(isReelScroll = true, pageIndex = 9, viewportHeightPx = 1_000)
        )

        assertEquals(DetectionDecision.IGNORED_UNCERTAIN, baseline.decision)
        assertEquals(DetectionDecision.COUNTED, pageAdvanced.decision)
        assertEquals(1, pageAdvanced.countDelta)
        assertFalse(repeatedEvent.isNewReel)
    }

    @Test
    fun testRapidPagerUpdatesCountEachObservedTransitionAndSkippedPages() {
        val sm = ReelTransitionStateMachine()
        val a = ReelSignature(creatorHandle = "creator_a", captionSnippet = "First reel caption")
        val b = ReelSignature(creatorHandle = "creator_b", captionSnippet = "Second reel caption")
        val c = ReelSignature(creatorHandle = "creator_c", captionSnippet = "Third reel caption")
        sm.processFrame(a, "TYPE_WINDOW_CONTENT_CHANGED", 1_000L)

        val bResult = sm.processFrame(b, "TYPE_VIEW_SCROLLED", 1_025L,
            com.focusmate.ai.detector.TransitionEvidence(isReelScroll = true, pageIndex = 1))
        val cResult = sm.processFrame(c, "TYPE_VIEW_SCROLLED", 1_050L,
            com.focusmate.ai.detector.TransitionEvidence(isReelScroll = true, pageIndex = 2))

        assertEquals(DetectionDecision.COUNTED, bResult.decision)
        assertEquals(DetectionDecision.COUNTED, cResult.decision)
        assertEquals(2, bResult.countDelta + cResult.countDelta)
    }

    @Test
    fun testRapidContentOnlyTransitionsFinalizeStrongReelsWhenNextArrives() {
        val sm = ReelTransitionStateMachine()
        val a = ReelSignature(creatorHandle = "creator_a", captionSnippet = "First distinct reel caption")
        val b = ReelSignature(creatorHandle = "creator_b", captionSnippet = "Second distinct reel caption")
        val c = ReelSignature(creatorHandle = "creator_c", captionSnippet = "Third distinct reel caption")
        val d = ReelSignature(creatorHandle = "creator_d", captionSnippet = "Fourth distinct reel caption")
        sm.processFrame(a, "TYPE_WINDOW_CONTENT_CHANGED", 1_000L)

        val bFirst = sm.processFrame(b, "TYPE_WINDOW_CONTENT_CHANGED", 1_010L)
        val bFinalizedByC = sm.processFrame(c, "TYPE_WINDOW_CONTENT_CHANGED", 1_020L)
        val cConfirmed = sm.processFrame(c, "TYPE_WINDOW_CONTENT_CHANGED", 1_110L)
        val dFirst = sm.processFrame(d, "TYPE_WINDOW_CONTENT_CHANGED", 1_120L)
        val dConfirmed = sm.processFrame(d, "TYPE_WINDOW_CONTENT_CHANGED", 1_210L)

        assertEquals(DetectionDecision.IGNORED_UNCERTAIN, bFirst.decision)
        assertEquals(DetectionDecision.COUNTED, bFinalizedByC.decision)
        assertEquals(DetectionDecision.COUNTED, cConfirmed.decision)
        assertEquals(DetectionDecision.IGNORED_UNCERTAIN, dFirst.decision)
        assertEquals(DetectionDecision.COUNTED, dConfirmed.decision)
    }

    @Test
    fun testSameSignatureSkippedPageCountUsesPagerDeltaNotEventCount() {
        val sm = ReelTransitionStateMachine()
        val reel = ReelSignature(creatorHandle = "creator", audioTitle = "one audio")
        sm.processFrame(reel, "TYPE_WINDOW_CONTENT_CHANGED", 1_000L)
        sm.processFrame(reel, "TYPE_VIEW_SCROLLED", 1_100L,
            com.focusmate.ai.detector.TransitionEvidence(isReelScroll = true, pageIndex = 4))

        val result = sm.processFrame(reel, "TYPE_VIEW_SCROLLED", 1_120L,
            com.focusmate.ai.detector.TransitionEvidence(isReelScroll = true, pageIndex = 7))

        assertEquals(DetectionDecision.COUNTED, result.decision)
        assertEquals(3, result.countDelta)
    }

    @Test
    fun testStableSignatureTransitionCountsWhenInstagramOmitsScrollEvent() {
        val sm = ReelTransitionStateMachine()
        val first = ReelSignature(creatorHandle = "creator_a", captionSnippet = "First distinct reel caption")
        val next = ReelSignature(creatorHandle = "creator_b", captionSnippet = "Second distinct reel caption")
        sm.processFrame(first, "TYPE_WINDOW_CONTENT_CHANGED", 1_000L)

        val settlingFrame = sm.processFrame(next, "TYPE_WINDOW_CONTENT_CHANGED", 2_000L)
        val confirmedFrame = sm.processFrame(next, "TYPE_WINDOW_CONTENT_CHANGED", 2_130L)

        assertEquals(DetectionDecision.IGNORED_UNCERTAIN, settlingFrame.decision)
        assertEquals(DetectionDecision.COUNTED, confirmedFrame.decision)
        assertTrue(confirmedFrame.isNewReel)
    }

    @Test
    fun testGoalModeReminders() {
        val interviewSettings = UserSettings(goalMode = GoalMode.INTERVIEW_PREP)
        assertEquals(
            "You've reached your Reel limit. Take a break and practise your interview questions.",
            interviewSettings.getActiveReminderMessage()
        )

        val studySettings = UserSettings(goalMode = GoalMode.STUDY)
        assertEquals(
            "Your study goal is waiting. Take a break from scrolling and return to your studies.",
            studySettings.getActiveReminderMessage()
        )

        val codingSettings = UserSettings(goalMode = GoalMode.CODING)
        assertEquals(
            "Your coding goal is waiting. Spend some time building your project.",
            codingSettings.getActiveReminderMessage()
        )

        val customSettings = UserSettings(
            goalMode = GoalMode.CUSTOM,
            customGoalTitle = "Marathon Training",
            customGoalMessage = "Put your phone down and go for your evening run!"
        )
        assertEquals(
            "Put your phone down and go for your evening run!",
            customSettings.getActiveReminderMessage()
        )
    }
}
