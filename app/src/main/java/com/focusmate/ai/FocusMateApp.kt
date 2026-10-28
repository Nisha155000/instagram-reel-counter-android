package com.focusmate.ai

import android.app.Application
import com.focusmate.ai.data.FocusRepository
import com.focusmate.ai.notification.GoalNotificationManager

class FocusMateApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FocusRepository.getInstance(this)
        GoalNotificationManager(this)
    }
}
