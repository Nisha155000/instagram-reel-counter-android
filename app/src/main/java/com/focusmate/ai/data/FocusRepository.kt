package com.focusmate.ai.data

import android.content.Context
import com.focusmate.ai.model.DailyStats
import com.focusmate.ai.model.DetectionDecision
import com.focusmate.ai.model.DetectionLogEvent
import com.focusmate.ai.model.GoalMode
import com.focusmate.ai.model.ReelSignature
import com.focusmate.ai.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class FocusRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dbHelper = FocusDatabaseHelper(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences("focusmate_settings_prefs", Context.MODE_PRIVATE)
    private val statsWriteMutex = Mutex()
    private var initialDataLoaded = false
    private var pendingReelIncrements = 0

    // Current date
    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun loadSettingsFromPrefs(): UserSettings {
        val hasSaved = prefs.contains("daily_limit")
        if (!hasSaved) {
            // First run: try reading from SQLite or fallback to default
            val fromDb = try { dbHelper.getSettings() } catch (e: Exception) { UserSettings() }
            return fromDb
        }
        val modeId = prefs.getString("goal_mode", GoalMode.STUDY.id) ?: GoalMode.STUDY.id
        return UserSettings(
            dailyReelLimit = prefs.getInt("daily_limit", 20),
            scrollTimeLimitMinutes = prefs.getInt("time_limit_minutes", 30),
            goalMode = GoalMode.fromId(modeId),
            customGoalTitle = prefs.getString("custom_title", "My Focus Goal") ?: "My Focus Goal",
            customGoalMessage = prefs.getString("custom_message", "You've reached your limit! Step away and focus on what matters most today.") ?: "You've reached your limit!",
            overlayEnabled = prefs.getBoolean("overlay_enabled", true),
            hapticEnabled = prefs.getBoolean("haptic_enabled", true),
            soundEnabled = prefs.getBoolean("sound_enabled", true)
        )
    }

    // State flows
    private val _todayStats = MutableStateFlow(DailyStats(date = getTodayDate()))
    val todayStats: StateFlow<DailyStats> = _todayStats.asStateFlow()

    private val _historyStats = MutableStateFlow<List<DailyStats>>(emptyList())
    val historyStats: StateFlow<List<DailyStats>> = _historyStats.asStateFlow()

    private val _userSettings = MutableStateFlow(loadSettingsFromPrefs())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    private val _detectionLogs = MutableStateFlow<List<DetectionLogEvent>>(emptyList())
    val detectionLogs: StateFlow<List<DetectionLogEvent>> = _detectionLogs.asStateFlow()

    // Testing / Accuracy verification flows
    private val _manualVerifiedCount = MutableStateFlow(0)
    val manualVerifiedCount: StateFlow<Int> = _manualVerifiedCount.asStateFlow()

    private val _autoDetectedCount = MutableStateFlow(0)
    val autoDetectedCount: StateFlow<Int> = _autoDetectedCount.asStateFlow()

    private val _missedTransitions = MutableStateFlow(0)
    val missedTransitions: StateFlow<Int> = _missedTransitions.asStateFlow()

    private val _duplicateCounts = MutableStateFlow(0)
    val duplicateCounts: StateFlow<Int> = _duplicateCounts.asStateFlow()

    // Service status flags
    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    // Notification callback for when limit is hit
    var onLimitReachedCallback: ((UserSettings, DailyStats) -> Unit)? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        scope.launch {
            val today = getTodayDate()
            val currentSettings = _userSettings.value
            statsWriteMutex.withLock {
                val existingStats = dbHelper.getDailyStats(today) ?: DailyStats(
                    date = today,
                    goalMode = currentSettings.goalMode.id
                )
                val restored = synchronized(this@FocusRepository) {
                    val merged = existingStats.copy(reelCount = existingStats.reelCount + pendingReelIncrements)
                    pendingReelIncrements = 0
                    initialDataLoaded = true
                    _todayStats.value = merged
                    _autoDetectedCount.value = merged.reelCount
                    _manualVerifiedCount.value = merged.reelCount
                    merged
                }
                dbHelper.saveDailyStats(restored)
            }
            _historyStats.value = dbHelper.getAllDailyStats(30)
            _detectionLogs.value = dbHelper.getRecentLogs(100)
        }
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    fun setOverlayActive(active: Boolean) {
        _isOverlayActive.value = active
    }

    @Synchronized
    fun onReelCounted(signature: ReelSignature, eventType: String, reason: String) {
        if (!initialDataLoaded) pendingReelIncrements += 1
        val today = getTodayDate()
        val current = _todayStats.value
        val newReelCount = if (current.date == today) current.reelCount + 1 else 1

        val settings = _userSettings.value
        val limitReachedNow = newReelCount >= settings.dailyReelLimit && !current.limitReached

        val updated = current.copy(
            date = today,
            reelCount = newReelCount,
            limitReached = current.limitReached || limitReachedNow
        )
        _todayStats.value = updated
        _autoDetectedCount.value = newReelCount
        prefs.edit().putString(KEY_LAST_REEL_KEY, signature.key).apply()

        val log = DetectionLogEvent(
            eventType = eventType,
            signature = signature.key,
            decision = DetectionDecision.COUNTED,
            reason = reason,
            currentTotalCount = newReelCount
        )

        val updatedLogs = listOf(log) + _detectionLogs.value.take(199)
        _detectionLogs.value = updatedLogs

        scope.launch {
            statsWriteMutex.withLock { dbHelper.saveDailyStats(_todayStats.value) }
            dbHelper.insertLog(log)
            _historyStats.value = dbHelper.getAllDailyStats(30)
        }

        if (limitReachedNow) {
            onLimitReachedCallback?.invoke(settings, updated)
        }
    }

    @Synchronized
    fun onReelIgnored(signature: ReelSignature, eventType: String, decision: DetectionDecision, reason: String) {
        val current = _todayStats.value
        val log = DetectionLogEvent(
            eventType = eventType,
            signature = signature.key,
            decision = decision,
            reason = reason,
            currentTotalCount = current.reelCount
        )

        if (decision == DetectionDecision.IGNORED_SAME_SIGNATURE || decision == DetectionDecision.IGNORED_DEBOUNCE) {
            // Count as duplicate attempt if it occurred right on the same reel
            _duplicateCounts.value += 1
        }
        if (decision == DetectionDecision.IGNORED_SAME_SIGNATURE || decision == DetectionDecision.IGNORED_PREVIOUS_REEL) {
            prefs.edit().putString(KEY_LAST_REEL_KEY, signature.key).apply()
        }

        val updatedLogs = listOf(log) + _detectionLogs.value.take(199)
        _detectionLogs.value = updatedLogs

        scope.launch {
            dbHelper.insertLog(log)
        }
    }

    @Synchronized
    fun addScrollingSeconds(seconds: Long) {
        val today = getTodayDate()
        val current = _todayStats.value
        if (current.date == today) {
            val updated = current.copy(
                estimatedScrollSeconds = current.estimatedScrollSeconds + seconds
            )
            _todayStats.value = updated
            scope.launch {
                statsWriteMutex.withLock { dbHelper.saveDailyStats(_todayStats.value) }
            }
        }
    }

    fun updateSettings(newSettings: UserSettings) {
        _userSettings.value = newSettings
        prefs.edit().apply {
            putInt("daily_limit", newSettings.dailyReelLimit)
            putInt("time_limit_minutes", newSettings.scrollTimeLimitMinutes)
            putString("goal_mode", newSettings.goalMode.id)
            putString("custom_title", newSettings.customGoalTitle)
            putString("custom_message", newSettings.customGoalMessage)
            putBoolean("overlay_enabled", newSettings.overlayEnabled)
            putBoolean("haptic_enabled", newSettings.hapticEnabled)
            putBoolean("sound_enabled", newSettings.soundEnabled)
            apply()
        }
        scope.launch {
            dbHelper.saveSettings(newSettings)
        }
    }

    // Manual Testing Controls
    fun incrementManualCount() {
        val newManual = _manualVerifiedCount.value + 1
        _manualVerifiedCount.value = newManual
        if (newManual > _autoDetectedCount.value) {
            _missedTransitions.value = max(0, newManual - _autoDetectedCount.value)
        }
    }

    fun decrementManualCount() {
        if (_manualVerifiedCount.value > 0) {
            val newManual = _manualVerifiedCount.value - 1
            _manualVerifiedCount.value = newManual
            _missedTransitions.value = max(0, newManual - _autoDetectedCount.value)
        }
    }

    fun syncManualToAuto() {
        _manualVerifiedCount.value = _autoDetectedCount.value
        _missedTransitions.value = 0
        _duplicateCounts.value = 0
    }

    fun resetTestingCounters() {
        _manualVerifiedCount.value = 0
        _autoDetectedCount.value = 0
        _missedTransitions.value = 0
        _duplicateCounts.value = 0

        val log = DetectionLogEvent(
            eventType = "TESTING_RESET",
            signature = "MANUAL_RESET",
            decision = DetectionDecision.RESET_MANUAL,
            reason = "Developer reset testing counters",
            currentTotalCount = 0
        )
        _detectionLogs.value = listOf(log) + _detectionLogs.value.take(199)
    }

    fun resetTodayCount() {
        val today = getTodayDate()
        val reset = DailyStats(date = today, reelCount = 0, estimatedScrollSeconds = 0, limitReached = false)
        _todayStats.value = reset
        _autoDetectedCount.value = 0
        _manualVerifiedCount.value = 0
        _missedTransitions.value = 0
        _duplicateCounts.value = 0
        scope.launch {
            statsWriteMutex.withLock { dbHelper.saveDailyStats(_todayStats.value) }
        }
        prefs.edit().remove(KEY_LAST_REEL_KEY).apply()
    }

    fun getLastConfirmedReelKey(): String? = prefs.getString(KEY_LAST_REEL_KEY, null)

    fun clearLogs() {
        _detectionLogs.value = emptyList()
        scope.launch {
            dbHelper.clearAllLogs()
        }
    }

    fun calculateAccuracyPercentage(): Float {
        val manual = _manualVerifiedCount.value
        val auto = _autoDetectedCount.value
        if (manual <= 0) return 100f
        val diff = abs(auto - manual)
        val accuracy = 100f - (diff.toFloat() / manual.toFloat() * 100f)
        return max(0f, accuracy)
    }

    companion object {
        private const val KEY_LAST_REEL_KEY = "last_confirmed_reel_key"
        @Volatile
        private var instance: FocusRepository? = null

        fun getInstance(context: Context): FocusRepository {
            return instance ?: synchronized(this) {
                instance ?: FocusRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
