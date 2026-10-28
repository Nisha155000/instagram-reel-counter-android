package com.focusmate.ai.detector

import com.focusmate.ai.model.DetectionDecision
import com.focusmate.ai.model.ReelSignature

interface ReelDetectorListener {
    fun onReelCounted(signature: ReelSignature, eventType: String, reason: String)
    fun onReelIgnored(signature: ReelSignature, eventType: String, decision: DetectionDecision, reason: String)
    fun onActiveScrollingTime(seconds: Long)
}
