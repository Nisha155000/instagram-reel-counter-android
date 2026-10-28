package com.focusmate.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.focusmate.ai.data.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FloatingCounterService : Service() {

    companion object {
        const val OVERLAY_CHANNEL_ID = "focusmate_overlay_service_channel"
        const val FOREGROUND_NOTIFICATION_ID = 2001
        
        @Volatile
        var isDismissedByUser: Boolean = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var repository: FocusRepository
    private var overlayView: View? = null
    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var cardContainer: LinearLayout
    private lateinit var headerRow: LinearLayout
    private lateinit var avatarTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var closeButton: ImageView
    private lateinit var counterRow: LinearLayout
    private lateinit var counterTextView: TextView
    private lateinit var timeTextView: TextView
    private lateinit var speechBubbleLayout: LinearLayout
    private lateinit var dadiSpeechTextView: TextView

    private var isCompact = false
    private var lastReelCount = 0
    private var lastLimit = 20
    private var lastScrollSeconds = 0L

    override fun onCreate() {
        super.onCreate()
        repository = FocusRepository.getInstance(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        buildOverlayView()
        observeStats()
        repository.setOverlayActive(true)
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                "Dadi FocusMate Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows grandmother reel counter over Instagram"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Dadi FocusMate is Watching 👵👓🦯")
            .setContentText("Counting Instagram Reels & scrolling time")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun buildOverlayView() {
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16f)
            y = dp(75f) // Opens right at the top of the screen by default
        }

        // Outer Container
        cardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            elevation = dp(10f).toFloat()

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121824"))
                cornerRadius = dp(18f).toFloat()
                setStroke(dp(1.8f), Color.parseColor("#00E5FF"))
            }
        }

        // Header Row: Avatar 👵👓🦯 + Title + Close Button
        headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4f))
        }

        avatarTextView = TextView(this).apply {
            text = "👵👓🦯"
            textSize = 18f
            setPadding(0, 0, dp(6f), 0)
        }

        titleTextView = TextView(this).apply {
            text = "DADI FOCUSMATE"
            textSize = 11f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        closeButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#94A3B8"))
            val iconSize = dp(16f)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginStart = dp(8f)
            }
            setOnClickListener {
                isDismissedByUser = true
                stopSelf()
            }
        }

        headerRow.addView(avatarTextView)
        headerRow.addView(titleTextView)
        headerRow.addView(closeButton)

        // Counter Row: Reels Count + Scroll Time
        counterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2f), 0, dp(4f))
        }

        counterTextView = TextView(this).apply {
            text = "REELS: 0 / 20"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        timeTextView = TextView(this).apply {
            text = "⏱️ 0m"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = Typeface.DEFAULT_BOLD
        }

        counterRow.addView(counterTextView)
        counterRow.addView(timeTextView)

        // Speech Bubble: What Dadi says to you
        speechBubbleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(8f).toFloat()
            }
        }

        dadiSpeechTextView = TextView(this).apply {
            text = "Beta, Dadi is watching your reels! 🦯"
            textSize = 11f
            setTextColor(Color.parseColor("#E2E8F0"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        speechBubbleLayout.addView(dadiSpeechTextView)

        // Assemble Container
        cardContainer.addView(headerRow)
        cardContainer.addView(counterRow)
        cardContainer.addView(speechBubbleLayout)

        setupTouchListener(cardContainer)

        overlayView = cardContainer
        windowManager.addView(overlayView, layoutParams)
    }

    private fun setupTouchListener(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMove = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMove = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isMove = true
                    }
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    if (overlayView?.isAttachedToWindow == true) {
                        windowManager.updateViewLayout(overlayView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMove) {
                        toggleCompactMode()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleCompactMode() {
        isCompact = !isCompact
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        if (isCompact) {
            headerRow.visibility = View.GONE
            timeTextView.visibility = View.GONE
            speechBubbleLayout.visibility = View.GONE
            counterTextView.text = "👵🦯 $lastReelCount / $lastLimit"
            counterTextView.textSize = 13f
            cardContainer.setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
        } else {
            headerRow.visibility = View.VISIBLE
            timeTextView.visibility = View.VISIBLE
            speechBubbleLayout.visibility = View.VISIBLE
            counterTextView.text = "REELS: $lastReelCount / $lastLimit"
            counterTextView.textSize = 15f
            cardContainer.setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        }
    }

    private fun observeStats() {
        serviceJob = scope.launch {
            combine(repository.todayStats, repository.userSettings) { stats, settings ->
                Pair(stats, settings)
            }.collect { (stats, settings) ->
                lastReelCount = stats.reelCount
                lastLimit = settings.dailyReelLimit
                lastScrollSeconds = stats.estimatedScrollSeconds
                updateCounterUI(stats.reelCount, settings.dailyReelLimit, stats.estimatedScrollSeconds, settings)
            }
        }
    }

    private fun updateCounterUI(
        currentCount: Int,
        limit: Int,
        scrollSeconds: Long,
        settings: com.focusmate.ai.model.UserSettings
    ) {
        val minutes = scrollSeconds / 60
        val remaining = kotlin.math.max(0, limit - currentCount)

        val isOverLimit = currentCount >= limit || (settings.scrollTimeLimitMinutes > 0 && minutes >= settings.scrollTimeLimitMinutes)
        val isWarning = currentCount >= (limit * 0.8f) || (settings.scrollTimeLimitMinutes > 0 && minutes >= (settings.scrollTimeLimitMinutes * 0.8f))

        if (isCompact) {
            counterTextView.text = "👵🦯 $currentCount / $limit"
        } else {
            counterTextView.text = "REELS: $currentCount / $limit"
            timeTextView.text = "⏱️ ${minutes}m"

            // Dynamic Grandmother speech
            when {
                isOverLimit -> {
                    if (currentCount >= limit) {
                        dadiSpeechTextView.text = "👵🦯 BETAA STOP! Reel limit ($limit) reached! Dadi's stick is angry! Go focus on ${settings.goalMode.title}!"
                    } else {
                        dadiSpeechTextView.text = "👵🦯 BETAA STOP! Time limit (${settings.scrollTimeLimitMinutes}m) reached! Dadi says put phone down now!"
                    }
                }
                isWarning -> {
                    dadiSpeechTextView.text = "👵👓 Beta, only $remaining reels left! Dadi's stick is getting ready! 🦯"
                }
                currentCount > 0 -> {
                    dadiSpeechTextView.text = "👵 Beta, Dadi is watching you scroll! $remaining reels left."
                }
                else -> {
                    dadiSpeechTextView.text = "👵 Dadi is counting with spectacles & stick! 👓🦯"
                }
            }
        }

        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        val backgroundDrawable = GradientDrawable().apply {
            cornerRadius = dp(18f).toFloat()
            when {
                isOverLimit -> {
                    setColor(Color.parseColor("#3B0D11"))
                    setStroke(dp(2f), Color.parseColor("#FF1744"))
                    counterTextView.setTextColor(Color.parseColor("#FF5252"))
                    titleTextView.setTextColor(Color.parseColor("#FF5252"))
                }
                isWarning -> {
                    setColor(Color.parseColor("#2B1E08"))
                    setStroke(dp(1.8f), Color.parseColor("#FFC400"))
                    counterTextView.setTextColor(Color.parseColor("#FFD54F"))
                    titleTextView.setTextColor(Color.parseColor("#FFD54F"))
                }
                else -> {
                    setColor(Color.parseColor("#121824"))
                    setStroke(dp(1.8f), Color.parseColor("#00E5FF"))
                    counterTextView.setTextColor(Color.WHITE)
                    titleTextView.setTextColor(Color.parseColor("#00E5FF"))
                }
            }
        }
        cardContainer.background = backgroundDrawable
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        repository.setOverlayActive(false)
        overlayView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
