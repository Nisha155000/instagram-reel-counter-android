package com.focusmate.ai.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.focusmate.ai.data.FocusRepository
import com.focusmate.ai.detector.ReelDetector
import com.focusmate.ai.detector.ReelDetectorListener
import com.focusmate.ai.detector.ReelTransitionStateMachine
import com.focusmate.ai.model.DetectionDecision
import com.focusmate.ai.model.ReelSignature
import com.focusmate.ai.notification.GoalNotificationManager

class FocusAccessibilityService : AccessibilityService(), ReelDetectorListener {

    companion object {
        private const val TAG = "FocusAccessibility"
    }

    private lateinit var repository: FocusRepository
    private lateinit var detector: ReelDetector
    private lateinit var notificationManager: GoalNotificationManager
    private var isInstagramInForeground = false
    private val ignoredLogTimes = LinkedHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate() initialized")
        repository = FocusRepository.getInstance(applicationContext)
        detector = ReelDetector(
            this,
            ReelTransitionStateMachine(restoredCurrentKey = repository.getLastConfirmedReelKey())
        )
        notificationManager = GoalNotificationManager(applicationContext)

        repository.onLimitReachedCallback = { settings, stats ->
            notificationManager.notifyLimitReached(settings, stats)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected() - Accessibility Service Connected & Active")
        repository.setAccessibilityConnected(true)

        val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
        // Scope to Instagram packages so OEM ROMs (ColorOS/MIUI) allow full accessibility access
        info.packageNames = arrayOf("com.instagram.android", "com.instagram.lite")
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        // Let the detector compare actual successive UI states; Android can
        // otherwise coalesce rapid Reel-page updates into one notification.
        info.notificationTimeout = 0
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        val eventType = event.eventType
        val isInstagram = packageName.contains("instagram")

        if (isInstagram) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (!isInstagramInForeground) {
                    isInstagramInForeground = true
                    FloatingCounterService.isDismissedByUser = false
                    Log.d(TAG, "Instagram entered foreground ($packageName)")
                }
            }
            // Ensure overlay counter is running over Instagram
            if (!FloatingCounterService.isDismissedByUser) {
                checkAndStartOverlay()
            }

            val rootNode = getInstagramRootNode(event)
            if (rootNode != null) {
                detector.onAccessibilityEvent(event, rootNode)
            }
        } else if (packageName.isNotBlank() && !packageName.contains("focusmate")) {
            if (isInstagramInForeground && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                isInstagramInForeground = false
                Log.d(TAG, "Left Instagram to $packageName")
                detector.onAppLeftInstagram()
            }
        }
    }

    private fun getInstagramRootNode(event: AccessibilityEvent): AccessibilityNodeInfo? {
        // Priority 1: event.source (Directly attached to the event, always granted by OEM security)
        val source = event.source
        if (source != null) {
            var curr: AccessibilityNodeInfo = source
            while (true) {
                val parentNode: AccessibilityNodeInfo = curr.parent ?: break
                curr = parentNode
            }
            return curr
        }

        // Priority 2: rootInActiveWindow
        val activeRoot = try { rootInActiveWindow } catch (e: Exception) { null }
        if (activeRoot != null && (activeRoot.packageName?.toString()?.contains("instagram") == true)) {
            return activeRoot
        }

        // Priority 3: Check interactive windows
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    val windowRoot = window.root
                    if (windowRoot != null && (windowRoot.packageName?.toString()?.contains("instagram") == true)) {
                        return windowRoot
                    }
                }
            } catch (e: Exception) {
                // Window access fallback
            }
        }

        Log.d(TAG, "Instagram event had no current accessible root; stale tree will not be reused")
        return null
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt() called by system")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() called")
        repository.setAccessibilityConnected(false)
        detector.resetState()
    }

    // ReelDetectorListener Callbacks
    override fun onReelCounted(signature: ReelSignature, eventType: String, reason: String) {
        Log.i(TAG, "REEL COUNTED: ${signature.key} | $reason")
        repository.onReelCounted(signature, eventType, reason)
        if (!FloatingCounterService.isDismissedByUser) {
            checkAndStartOverlay()
        }
    }

    override fun onReelIgnored(signature: ReelSignature, eventType: String, decision: DetectionDecision, reason: String) {
        val repetitive = decision == DetectionDecision.IGNORED_SAME_SIGNATURE ||
                decision == DetectionDecision.IGNORED_EMPTY_CONTENT ||
                decision == DetectionDecision.IGNORED_NOT_REELS_SURFACE
        if (repetitive) {
            val key = "${decision.name}:${signature.key.hashCode()}"
            val now = android.os.SystemClock.uptimeMillis()
            synchronized(ignoredLogTimes) {
                val last = ignoredLogTimes[key]
                if (last != null && now - last < 1_000L) return
                ignoredLogTimes[key] = now
                while (ignoredLogTimes.size > 64) {
                    ignoredLogTimes.remove(ignoredLogTimes.keys.first())
                }
            }
        }
        Log.d(TAG, "REEL DECISION [${decision.name}] event=$eventType sigHash=${signature.key.hashCode()} reason=$reason")
        repository.onReelIgnored(signature, eventType, decision, reason)
    }

    override fun onActiveScrollingTime(seconds: Long) {
        repository.addScrollingSeconds(seconds)
    }

    private fun checkAndStartOverlay() {
        val settings = repository.userSettings.value
        if (settings.overlayEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                val intent = Intent(this, FloatingCounterService::class.java)
                try {
                    ContextCompat.startForegroundService(this, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start FloatingCounterService", e)
                }
            }
        }
    }
}
