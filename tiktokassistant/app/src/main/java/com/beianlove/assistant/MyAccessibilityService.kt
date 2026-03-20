package com.beianlove.assistant

import android.content.SharedPreferences
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ThreadLocalRandom

/**
 * 无障碍服务：模拟手指滑动，使前台应用页面产生上下滚动。
 * 优先对可滚动节点执行 ACTION_SCROLL，失败时用 dispatchGesture 模拟触摸滑动。
 */
class MyAccessibilityService : AccessibilityService() {

    enum class Direction { UP, DOWN }

    data class StartParams(
        val direction: Direction,
        val intervalMs: Long,
        val durationSec: Long, // <=0 表示不限制
        val count: Int, // <=0 表示不限制
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    private var params: StartParams? = null
    private var endUptimeMs: Long = Long.MAX_VALUE
    private var remainingCount: Int = Int.MAX_VALUE

    private val swipeRunnable = object : Runnable {
        override fun run() {
            try {
                if (!running) return

                val now = SystemClock.uptimeMillis()

                if (now >= endUptimeMs) {
                    Log.d("MyAccessibilityService", "滑动已到达指定时长，自动停止")
                    stopInternal()
                    return
                }
                if (remainingCount <= 0) {
                    Log.d("MyAccessibilityService", "滑动已到达指定次数，自动停止")
                    stopInternal()
                    return
                }

                val p = params ?: return

                // 先尝试节点滚动，再用手势模拟手指滑动
                Log.d("MyAccessibilityService", "执行滑动：方向=${p.direction}, 剩余次数=$remainingCount")
                performSwipe(p.direction)

                if (remainingCount != Int.MAX_VALUE) remainingCount--

                // 下次执行间隔：10～20 秒随机（使用 ThreadLocalRandom 兼容所有版本）
                val nextDelayMs = ThreadLocalRandom.current().nextLong(10_000L, 20_001L)
                mainHandler.postDelayed(this, nextDelayMs)
            } catch (e: Throwable) {
                Log.e("MyAccessibilityService", "滑动过程发生异常：${e.message}", e)
                // 防止异常导致后续不再调度，用原间隔继续
                val p = params ?: return
                mainHandler.postDelayed(this, p.intervalMs)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.e("MyAccessibilityService", "onCreate(pid=${Process.myPid()})")
    }

    /**
     * 跨进程/跨实例通信兜底：
     * FloatingService 可能与无障碍服务不是同一进程，因此 companion object 的静态变量/引用不可共享。
     * 这里轮询 SharedPreferences：当用户点“开始/暂停”时，服务端就会启动/停止滑动。
     */
    private val prefsPoller = object : Runnable {
        override fun run() {
            try {
                syncFromSharedPrefs()
            } catch (t: Throwable) {
                // 避免轮询异常导致后续不再触发
                Log.e("MyAccessibilityService", "prefsPoller 异常：${t.message}", t)
            }
            mainHandler.postDelayed(this, 500L)
        }
    }

    private fun syncFromSharedPrefs() {
        val sp: SharedPreferences = applicationContext.getSharedPreferences(SettingsPrefs.PREFS_NAME, MODE_PRIVATE)
        val wantRunning = sp.getBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, false)
        val pendingStart = sp.getBoolean(SettingsPrefs.KEY_PENDING_START, false)

        if (wantRunning) {
            if (!running) {
                val directionStr = sp.getString(SettingsPrefs.KEY_PENDING_DIRECTION, Direction.UP.name) ?: Direction.UP.name
                val direction = try {
                    Direction.valueOf(directionStr)
                } catch (_: Exception) {
                    Direction.UP
                }
                val intervalMs = sp.getLong(SettingsPrefs.KEY_PENDING_INTERVAL_MS, 10_000L)
                val durationSec = sp.getLong(SettingsPrefs.KEY_PENDING_DURATION_SEC, 1L)
                val count = sp.getInt(SettingsPrefs.KEY_PENDING_COUNT, 30)

                // 清掉标记，避免轮询重复触发（即使我们不再依赖它）
                if (pendingStart) sp.edit().putBoolean(SettingsPrefs.KEY_PENDING_START, false).apply()
                Log.e(
                    "MyAccessibilityService",
                    "syncFromSharedPrefs startInternal(pid=${Process.myPid()}): direction=$direction intervalMs=$intervalMs durationSec=$durationSec count=$count (pendingStart=$pendingStart)"
                )
                startInternal(StartParams(direction = direction, intervalMs = intervalMs, durationSec = durationSec, count = count))
            }
        } else {
            if (running) {
                sp.edit().putBoolean(SettingsPrefs.KEY_PENDING_START, false).apply()
                Log.e("MyAccessibilityService", "轮询触发 stopInternal: wantRunning=false")
                stopInternal()
            } else {
                // 未运行时顺便清理 pending，避免遗留
                if (pendingStart) sp.edit().putBoolean(SettingsPrefs.KEY_PENDING_START, false).apply()
            }
        }
    }

    /** 上滑/下滑：直接使用 dispatchGesture 模拟手指滑动，确保有滑动效果 */
    private fun performSwipe(direction: Direction) {
        performSwipeGesture(direction)
    }

    /** 查找可滚动节点并执行 ACTION_SCROLL（适用于列表/ViewPager 等） */
    private fun tryScrollByNode(direction: Direction): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = when (direction) {
            Direction.UP -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD  // 上滑 -> 内容向上，下一项
            Direction.DOWN -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return findScrollableAndPerform(root, action)
    }

    private fun findScrollableAndPerform(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (!node.isScrollable) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (findScrollableAndPerform(child, action)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
            return false
        }
        val ok = node.performAction(action)
        return ok
    }

    /** 模拟手指在屏幕中央上下滑动（dispatchGesture），触发窗口滚动 */
    private fun performSwipeGesture(direction: Direction) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val centerX = width / 2f
        val startY = height * 0.75f  // 从偏下位置开始
        val endY = height * 0.25f    // 到偏上位置结束
        
        // 增加滑动距离，确保能触发滚动
        val path = Path().apply {
            when (direction) {
                Direction.UP -> {
                    moveTo(centerX, startY)
                    lineTo(centerX, endY)
                }
                Direction.DOWN -> {
                    moveTo(centerX, endY)
                    lineTo(centerX, startY)
                }
            }
        }
        
        val durationMs = 400L  // 稍微延长滑动时间，更自然
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val builder = GestureDescription.Builder().addStroke(stroke)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                builder.setDisplayId(android.view.Display.DEFAULT_DISPLAY)
            } catch (_: Exception) { }
        }
        
        val gesture = builder.build()
        
        // 执行手势
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                // 手势完成，无需额外处理
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                // 手势被取消，可能是权限问题或窗口不可用
            }
        }, mainHandler)
        
        if (!result) {
            // 如果直接发送失败，尝试短暂延迟后重试
            mainHandler.postDelayed({
                dispatchGesture(gesture, null, mainHandler)
            }, 100)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.e("MyAccessibilityService", "onServiceConnected(pid=${Process.myPid()}): 无障碍服务已连接")

        // 立即同步一次，然后开启轮询。
        mainHandler.removeCallbacks(prefsPoller)
        mainHandler.post {
            syncFromSharedPrefs()
            mainHandler.post(prefsPoller)
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // 每次收到事件时都同步一次，减少因为连接/轮询时序导致的丢失。
        try {
            syncFromSharedPrefs()
        } catch (_: Throwable) {
        }
    }

    override fun onInterrupt() {
        stopInternal()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(prefsPoller)
        stopInternal()
        if (instance === this) instance = null
        Log.e("MyAccessibilityService", "onDestroy(pid=${Process.myPid()}): 无障碍服务已销毁")
        super.onDestroy()
    }

    private fun startInternal(p: StartParams) {
        stopInternal()

        // 安全限制：避免过于频繁导致误触/卡顿
        val safeIntervalMs = p.intervalMs.coerceIn(500L, 60_000L)
        val safeDurationSec = p.durationSec.coerceAtLeast(0L)
        val safeCount = if (p.count <= 0) Int.MAX_VALUE else p.count.coerceIn(1, 50_000)

        params = p.copy(intervalMs = safeIntervalMs, durationSec = safeDurationSec, count = safeCount)
        endUptimeMs = if (safeDurationSec > 0) SystemClock.uptimeMillis() + safeDurationSec * 1000L else Long.MAX_VALUE
        remainingCount = safeCount

        running = true
        applicationContext.getSharedPreferences(SettingsPrefs.PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, true).apply()
        Log.e(
            "MyAccessibilityService",
            "startInternal(pid=${Process.myPid()}): direction=${p.direction} intervalMs=${p.intervalMs} durationSec=${p.durationSec} count=${p.count}"
        )
        // 先执行一次，再按照间隔循环
        mainHandler.removeCallbacks(swipeRunnable)
        swipeRunnable.run()
    }

    private fun stopInternal() {
        running = false
        applicationContext.getSharedPreferences(SettingsPrefs.PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, false).apply()
        mainHandler.removeCallbacks(swipeRunnable)
        Log.d("MyAccessibilityService", "滑动服务已停止")
        Log.e("MyAccessibilityService", "stopInternal：滑动服务已停止")
    }

    companion object {
        @Volatile
        private var instance: MyAccessibilityService? = null

        @Volatile
        private var pendingStart: StartParams? = null

        @Volatile
        private var pendingStopRequested: Boolean = false

        fun startSwipe(direction: Direction, intervalMs: Long, durationSec: Long, count: Int) {
            val p = StartParams(
                direction = direction,
                intervalMs = intervalMs,
                durationSec = durationSec,
                count = count
            )

            pendingStopRequested = false
            pendingStart = p
            Log.e(
                "MyAccessibilityService",
                "companion.startSwipe(pid=${Process.myPid()}): called direction=${direction.name} intervalMs=$intervalMs durationSec=$durationSec count=$count, instanceIsNull=${instance == null}"
            )
            instance?.let { it.startInternal(p) }
        }

        fun stopSwipe() {
            pendingStart = null
            pendingStopRequested = true
            Log.e(
                "MyAccessibilityService",
                "companion.stopSwipe(pid=${Process.myPid()}): called instanceIsNull=${instance == null}"
            )
            instance?.stopInternal()
        }

        fun isRunning(): Boolean = instance?.isSwipeRunning() == true
    }

    fun isSwipeRunning(): Boolean = running
}

