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
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
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
    private var floatingBackgroundView: View? = null
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
        val iconView = floatingImageView ?: return
        val bgView = floatingBackgroundView ?: return
        val running = prefs?.getBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, false) == true
        if (running) {
            bgView.setBackgroundResource(R.drawable.bg_floating_frosted_running)
            iconView.setImageResource(R.drawable.ic_floating_pause)
            // 白色毛玻璃按钮：中间改用灰色图标（反色）
            iconView.setColorFilter(Color.parseColor("#B0B0B0"))
            iconView.contentDescription = "运行中-点击暂停"
        } else {
            bgView.setBackgroundResource(R.drawable.bg_floating_frosted_paused)
            iconView.setImageResource(R.drawable.ic_floating_play)
            // 灰色毛玻璃按钮：中间改用白色图标（反色）
            iconView.setColorFilter(Color.parseColor("#FFFFFF"))
            iconView.contentDescription = "已暂停-点击开始"
        }
        iconView.invalidate()
        bgView.invalidate()
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "抖音助手",
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
            .setContentTitle("抖音助手")
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
        // 按你的要求：从原本 48dp 缩到 2/3 => 32dp
        val size = (32 * resources.displayMetrics.density).toInt()
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

        val container = FrameLayout(this).apply {
            isClickable = true
            isFocusable = false
        }

        val bgView = View(this).apply {
            setBackgroundResource(R.drawable.bg_floating_frosted_paused)
            // 轻微模糊（仅模糊该 View 自己的内容），叠加半透明背景产生“毛玻璃”观感。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(14f, 14f, Shader.TileMode.CLAMP))
            }
        }

        val iconView = ImageView(this).apply {
            setImageResource(R.drawable.ic_floating_play)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.CENTER
            setColorFilter(Color.parseColor("#666666"))
        }

        container.addView(bgView, FrameLayout.LayoutParams(size, size))
        container.addView(iconView, FrameLayout.LayoutParams(size, size, Gravity.CENTER))

        var hasMoved = false
        val touchSlop = (4 * resources.displayMetrics.density).toInt()
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                    // 吸收事件：避免子 View / Click 分发导致状态切换不稳定
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) hasMoved = true
                    params!!.x = initialX + dx
                    params!!.y = initialY + dy
                    windowManager?.updateViewLayout(container, params)
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    // 未拖动才当作“点击”，触发开始/暂停
                    if (!hasMoved) toggleSwipe()
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_CANCEL -> {
                    return@setOnTouchListener true
                }
            }
            true
        }

        floatingView = container
        floatingImageView = iconView
        floatingBackgroundView = bgView
        try {
            windowManager?.addView(container, params)
            container.post {
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
        floatingBackgroundView = null
    }

    private fun toggleSwipe() {
        val p = prefs ?: return
        if (!isAccessibilityServiceEnabled()) {
            android.util.Log.e("FloatingService", "toggleSwipe：无障碍服务未启用，blocked")
            Toast.makeText(this, "请先在系统无障碍里开启本助手服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val running = p.getBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, false)
        if (running) {
            p.edit().putBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, false).apply()
            // 取消“待启动”的参数（如果无障碍服务尚未连接/在另一个进程里，还能阻止 onServiceConnected 启动）
            p.edit().putBoolean(SettingsPrefs.KEY_PENDING_START, false).apply()
            android.util.Log.e("FloatingService", "toggleSwipe：停止（KEY_SWIPE_RUNNING=false, KEY_PENDING_START=false）")
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

        // 写入待启动参数：即使当前 moment `instance` 还没连接上，onServiceConnected 也能读到并启动。
        p.edit()
            .putBoolean(SettingsPrefs.KEY_PENDING_START, true)
            .putString(SettingsPrefs.KEY_PENDING_DIRECTION, direction.name)
            .putLong(SettingsPrefs.KEY_PENDING_INTERVAL_MS, intervalSec * 1000L)
            .putLong(SettingsPrefs.KEY_PENDING_DURATION_SEC, durationSec)
            .putInt(SettingsPrefs.KEY_PENDING_COUNT, count)
            .apply()
        android.util.Log.e(
            "FloatingService",
            "toggleSwipe：开始（KEY_SWIPE_RUNNING=true, pendingStart=true, direction=${direction.name}, intervalMs=${intervalSec * 1000L}, durationSec=$durationSec, count=$count）"
        )

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
        val componentName = ComponentName(this, MyAccessibilityService::class.java)
        val expected = componentName.flattenToString()
        val className = MyAccessibilityService::class.java.name
        val simpleName = MyAccessibilityService::class.java.simpleName
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        android.util.Log.e(
            "FloatingService",
            "isAccessibilityServiceEnabled：enabledServices=$enabledServices expected=$expected className=$className simpleName=$simpleName"
        )
        return enabledServices.split(':').any { entry ->
            val e = entry.trim()
            e.equals(expected, ignoreCase = true) ||
                // 常见简写：com.xxx/.MyAccessibilityService
                e.equals("${componentName.packageName}/.${simpleName}", ignoreCase = true) ||
                // 另一种形式：com.xxx/com.xxx.MyAccessibilityService
                e.equals("${componentName.packageName}/${className}", ignoreCase = true) ||
                // 兜底：包含类名
                e.contains(className, ignoreCase = true) ||
                e.contains(simpleName, ignoreCase = true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "floating_service"
        private const val NOTIFICATION_ID = 1001
    }
}
