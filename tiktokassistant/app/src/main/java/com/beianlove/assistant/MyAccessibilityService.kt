package com.beianlove.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
                    stopInternal()
                    return
                }
                if (remainingCount <= 0) {
                    stopInternal()
                    return
                }

                val p = params ?: return

                // 先尝试节点滚动，再用手势模拟手指滑动
                performSwipe(p.direction)

                if (remainingCount != Int.MAX_VALUE) remainingCount--

                // 下次执行间隔：10～20 秒随机（使用 ThreadLocalRandom 兼容所有版本）
                val nextDelayMs = ThreadLocalRandom.current().nextLong(10_000L, 20_001L)
                mainHandler.postDelayed(this, nextDelayMs)
            } catch (e: Throwable) {
                // 防止异常导致后续不再调度，用原间隔继续
                val p = params ?: return
                mainHandler.postDelayed(this, p.intervalMs)
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
        val startY = height * 0.85f
        val endY = height * 0.15f

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
        val durationMs = 350L
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val builder = GestureDescription.Builder().addStroke(stroke)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                builder.setDisplayId(android.view.Display.DEFAULT_DISPLAY)
            } catch (_: Exception) { }
        }
        val gesture = builder.build()
        dispatchGesture(gesture, null, mainHandler)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // 如果用户在服务连接之前就点了开始，则这里补发启动。
        pendingStart?.let { startInternal(it) }
        pendingStart = null

        if (pendingStopRequested) {
            pendingStopRequested = false
            stopInternal()
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // 不读取/分析内容：仅执行全局手势。
    }

    override fun onInterrupt() {
        stopInternal()
    }

    override fun onDestroy() {
        stopInternal()
        if (instance === this) instance = null
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
        // 先执行一次，再按照间隔循环
        mainHandler.removeCallbacks(swipeRunnable)
        swipeRunnable.run()
    }

    private fun stopInternal() {
        running = false
        applicationContext.getSharedPreferences(SettingsPrefs.PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(SettingsPrefs.KEY_SWIPE_RUNNING, false).apply()
        mainHandler.removeCallbacks(swipeRunnable)
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
            instance?.let { it.startInternal(p) }
        }

        fun stopSwipe() {
            pendingStart = null
            pendingStopRequested = true
            instance?.stopInternal()
        }

        fun isRunning(): Boolean = instance?.isSwipeRunning() == true
    }

    fun isSwipeRunning(): Boolean = running
}

