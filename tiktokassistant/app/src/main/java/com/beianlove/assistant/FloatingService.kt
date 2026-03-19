package com.beianlove.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * 前台服务：显示悬浮小图标，点击可开启/停止上下滑动小助手。
 */
class FloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingImageView: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var prefs: SharedPreferences? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusUpdater = object : Runnable {
        override fun run() {
            updateFloatingIconState()
            statusHandler.postDelayed(this, 500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(SettingsPrefs.PREFS_NAME, MODE_PRIVATE)
        createFloatingView()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        statusHandler.removeCallbacks(statusUpdater)
        removeFloatingView()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun updateFloatingIconState() {
        val view = floatingImageView ?: return
        val running = prefs?.getBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, false) == true
        if (running) {
            view.setBackgroundColor(Color.parseColor("#4CAF50"))
            view.setImageResource(android.R.drawable.ic_media_play)
            view.setColorFilter(Color.WHITE)
            view.contentDescription = "运行中-点击停止"
        } else {
            view.setBackgroundResource(android.R.drawable.btn_default)
            view.setImageResource(android.R.drawable.ic_menu_compass)
            view.setColorFilter(null)
            view.contentDescription = "已停止-点击开始"
        }
        view.invalidate()
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮球服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("滑动小助手")
            .setContentText("悬浮球已开启，点击可开始/停止")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = (48 * resources.displayMetrics.density).toInt()
        params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        val view = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(4, 4, 4, 4)
            setOnClickListener {
                toggleSwipe()
            }
        }

        var hasMoved = false
        val touchSlop = (4 * resources.displayMetrics.density).toInt()
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) hasMoved = true
                    params!!.x = initialX + dx
                    params!!.y = initialY + dy
                    windowManager?.updateViewLayout(view, params)
                }
            }
            hasMoved
        }

        floatingView = view
        floatingImageView = view
        try {
            windowManager?.addView(view, params)
            view.post {
                updateFloatingIconState()
                statusHandler.post(statusUpdater)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法显示悬浮窗", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeFloatingView() {
        statusHandler.removeCallbacks(statusUpdater)
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) { }
        }
        floatingView = null
        floatingImageView = null
    }

    private fun toggleSwipe() {
        val p = prefs ?: return
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先在系统无障碍里开启本助手服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val running = p.getBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, false)
        if (running) {
            p.edit().putBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, false).apply()
            MyAccessibilityService.stopSwipe()
            updateFloatingIconState()
            Toast.makeText(this, "已停止滑动", Toast.LENGTH_SHORT).show()
            return
        }
        p.edit().putBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, true).apply()
        val intervalSec = p.getLong(SettingsPrefs.KEY_INTERVAL_SEC, 10L)
        val durationHourStr = p.getString(SettingsPrefs.KEY_DURATION_HOUR, "1") ?: "1"
        val durationHour = durationHourStr.toDoubleOrNull() ?: 1.0
        val durationSec = (durationHour * 3600).toLong()
        val count = p.getInt(SettingsPrefs.KEY_COUNT, 30)
        val direction = if (p.getBoolean(SettingsPrefs.KEY_DIRECTION_UP, true)) {
            MyAccessibilityService.Direction.UP
        } else {
            MyAccessibilityService.Direction.DOWN
        }
        MyAccessibilityService.startSwipe(
            direction = direction,
            intervalMs = intervalSec * 1000L,
            durationSec = durationSec,
            count = count
        )
        updateFloatingIconState()
        Toast.makeText(this, "已开始滑动", Toast.LENGTH_SHORT).show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, MyAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    companion object {
        private const val CHANNEL_ID = "floating_service"
        private const val NOTIFICATION_ID = 1001
    }
}
