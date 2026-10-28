package com.focusmate.ai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.focusmate.ai.model.DailyStats
import com.focusmate.ai.model.DetectionDecision
import com.focusmate.ai.model.DetectionLogEvent
import com.focusmate.ai.model.GoalMode
import com.focusmate.ai.model.UserSettings

class FocusDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "focusmate.db"
        const val DATABASE_VERSION = 2

        const val TABLE_DAILY_STATS = "daily_stats"
        const val COL_DATE = "date"
        const val COL_REEL_COUNT = "reel_count"
        const val COL_SCROLL_SECONDS = "estimated_scroll_seconds"
        const val COL_LIMIT_REACHED = "limit_reached"
        const val COL_GOAL_MODE = "goal_mode"

        const val TABLE_SETTINGS = "user_settings"
        const val COL_SETTINGS_ID = "id"
        const val COL_DAILY_LIMIT = "daily_limit"
        const val COL_TIME_LIMIT_MIN = "time_limit_minutes"
        const val COL_SETTINGS_GOAL_MODE = "goal_mode"
        const val COL_CUSTOM_TITLE = "custom_goal_title"
        const val COL_CUSTOM_MESSAGE = "custom_goal_message"
        const val COL_OVERLAY_ENABLED = "overlay_enabled"
        const val COL_HAPTIC_ENABLED = "haptic_enabled"
        const val COL_SOUND_ENABLED = "sound_enabled"

        const val TABLE_LOGS = "detection_logs"
        const val COL_LOG_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_EVENT_TYPE = "event_type"
        const val COL_SIGNATURE = "signature"
        const val COL_DECISION = "decision"
        const val COL_REASON = "reason"
        const val COL_TOTAL_COUNT = "total_count"
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureTablesExist(db)
    }

    private fun ensureTablesExist(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_DAILY_STATS (
                $COL_DATE TEXT PRIMARY KEY,
                $COL_REEL_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_SCROLL_SECONDS INTEGER NOT NULL DEFAULT 0,
                $COL_LIMIT_REACHED INTEGER NOT NULL DEFAULT 0,
                $COL_GOAL_MODE TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (
                $COL_SETTINGS_ID INTEGER PRIMARY KEY,
                $COL_DAILY_LIMIT INTEGER NOT NULL DEFAULT 20,
                $COL_TIME_LIMIT_MIN INTEGER NOT NULL DEFAULT 30,
                $COL_SETTINGS_GOAL_MODE TEXT NOT NULL DEFAULT 'study',
                $COL_CUSTOM_TITLE TEXT NOT NULL DEFAULT 'My Focus Goal',
                $COL_CUSTOM_MESSAGE TEXT NOT NULL DEFAULT 'Stay focused!',
                $COL_OVERLAY_ENABLED INTEGER NOT NULL DEFAULT 1,
                $COL_HAPTIC_ENABLED INTEGER NOT NULL DEFAULT 1,
                $COL_SOUND_ENABLED INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_LOGS (
                $COL_LOG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_EVENT_TYPE TEXT NOT NULL,
                $COL_SIGNATURE TEXT NOT NULL,
                $COL_DECISION TEXT NOT NULL,
                $COL_REASON TEXT NOT NULL,
                $COL_TOTAL_COUNT INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onCreate(db: SQLiteDatabase) {
        ensureTablesExist(db)

        // Insert default settings
        val initialValues = ContentValues().apply {
            put(COL_SETTINGS_ID, 1)
            put(COL_DAILY_LIMIT, 20)
            put(COL_TIME_LIMIT_MIN, 30)
            put(COL_SETTINGS_GOAL_MODE, GoalMode.STUDY.id)
            put(COL_CUSTOM_TITLE, "My Focus Goal")
            put(COL_CUSTOM_MESSAGE, "You've reached your limit! Step away and focus on what matters most today.")
            put(COL_OVERLAY_ENABLED, 1)
            put(COL_HAPTIC_ENABLED, 1)
            put(COL_SOUND_ENABLED, 1)
        }
        db.insertWithOnConflict(TABLE_SETTINGS, null, initialValues, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureTablesExist(db)
    }

    fun getDailyStats(date: String): DailyStats? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DAILY_STATS,
            null,
            "$COL_DATE = ?",
            arrayOf(date),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                DailyStats(
                    date = it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                    reelCount = it.getInt(it.getColumnIndexOrThrow(COL_REEL_COUNT)),
                    estimatedScrollSeconds = it.getLong(it.getColumnIndexOrThrow(COL_SCROLL_SECONDS)),
                    limitReached = it.getInt(it.getColumnIndexOrThrow(COL_LIMIT_REACHED)) == 1,
                    goalMode = it.getString(it.getColumnIndexOrThrow(COL_GOAL_MODE))
                )
            } else null
        }
    }

    fun saveDailyStats(stats: DailyStats) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_DATE, stats.date)
            put(COL_REEL_COUNT, stats.reelCount)
            put(COL_SCROLL_SECONDS, stats.estimatedScrollSeconds)
            put(COL_LIMIT_REACHED, if (stats.limitReached) 1 else 0)
            put(COL_GOAL_MODE, stats.goalMode)
        }
        db.insertWithOnConflict(TABLE_DAILY_STATS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllDailyStats(limit: Int = 30): List<DailyStats> {
        val list = mutableListOf<DailyStats>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DAILY_STATS,
            null,
            null,
            null,
            null,
            null,
            "$COL_DATE DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    DailyStats(
                        date = it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                        reelCount = it.getInt(it.getColumnIndexOrThrow(COL_REEL_COUNT)),
                        estimatedScrollSeconds = it.getLong(it.getColumnIndexOrThrow(COL_SCROLL_SECONDS)),
                        limitReached = it.getInt(it.getColumnIndexOrThrow(COL_LIMIT_REACHED)) == 1,
                        goalMode = it.getString(it.getColumnIndexOrThrow(COL_GOAL_MODE))
                    )
                )
            }
        }
        return list
    }

    fun getSettings(): UserSettings {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SETTINGS,
            null,
            "$COL_SETTINGS_ID = ?",
            arrayOf("1"),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                UserSettings(
                    dailyReelLimit = it.getInt(it.getColumnIndexOrThrow(COL_DAILY_LIMIT)),
                    scrollTimeLimitMinutes = it.getInt(it.getColumnIndexOrThrow(COL_TIME_LIMIT_MIN)),
                    goalMode = GoalMode.fromId(it.getString(it.getColumnIndexOrThrow(COL_SETTINGS_GOAL_MODE))),
                    customGoalTitle = it.getString(it.getColumnIndexOrThrow(COL_CUSTOM_TITLE)),
                    customGoalMessage = it.getString(it.getColumnIndexOrThrow(COL_CUSTOM_MESSAGE)),
                    overlayEnabled = it.getInt(it.getColumnIndexOrThrow(COL_OVERLAY_ENABLED)) == 1,
                    hapticEnabled = it.getInt(it.getColumnIndexOrThrow(COL_HAPTIC_ENABLED)) == 1,
                    soundEnabled = it.getInt(it.getColumnIndexOrThrow(COL_SOUND_ENABLED)) == 1
                )
            } else {
                UserSettings()
            }
        }
    }

    fun saveSettings(settings: UserSettings) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SETTINGS_ID, 1)
            put(COL_DAILY_LIMIT, settings.dailyReelLimit)
            put(COL_TIME_LIMIT_MIN, settings.scrollTimeLimitMinutes)
            put(COL_SETTINGS_GOAL_MODE, settings.goalMode.id)
            put(COL_CUSTOM_TITLE, settings.customGoalTitle)
            put(COL_CUSTOM_MESSAGE, settings.customGoalMessage)
            put(COL_OVERLAY_ENABLED, if (settings.overlayEnabled) 1 else 0)
            put(COL_HAPTIC_ENABLED, if (settings.hapticEnabled) 1 else 0)
            put(COL_SOUND_ENABLED, if (settings.soundEnabled) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertLog(event: DetectionLogEvent) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, event.timestamp)
            put(COL_EVENT_TYPE, event.eventType)
            put(COL_SIGNATURE, event.signature)
            put(COL_DECISION, event.decision.name)
            put(COL_REASON, event.reason)
            put(COL_TOTAL_COUNT, event.currentTotalCount)
        }
        db.insert(TABLE_LOGS, null, values)
    }

    fun getRecentLogs(limit: Int = 100): List<DetectionLogEvent> {
        val list = mutableListOf<DetectionLogEvent>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_LOGS,
            null,
            null,
            null,
            null,
            null,
            "$COL_LOG_ID DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                val decisionName = it.getString(it.getColumnIndexOrThrow(COL_DECISION))
                val decision = runCatching { DetectionDecision.valueOf(decisionName) }.getOrDefault(DetectionDecision.IGNORED_EMPTY_CONTENT)
                list.add(
                    DetectionLogEvent(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_LOG_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                        eventType = it.getString(it.getColumnIndexOrThrow(COL_EVENT_TYPE)),
                        signature = it.getString(it.getColumnIndexOrThrow(COL_SIGNATURE)),
                        decision = decision,
                        reason = it.getString(it.getColumnIndexOrThrow(COL_REASON)),
                        currentTotalCount = it.getInt(it.getColumnIndexOrThrow(COL_TOTAL_COUNT))
                    )
                )
            }
        }
        return list
    }

    fun clearAllLogs() {
        val db = writableDatabase
        db.delete(TABLE_LOGS, null, null)
    }
}
