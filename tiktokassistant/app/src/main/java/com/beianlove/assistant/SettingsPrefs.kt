package com.beianlove.assistant

object SettingsPrefs {
    const val PREFS_NAME = "assistant_settings"
    const val KEY_INTERVAL_SEC = "interval_sec"
    const val KEY_DURATION_HOUR = "duration_hour"
    const val KEY_COUNT = "count"
    const val KEY_DIRECTION_UP = "direction_up"
    /** 跨进程：无障碍滑动是否正在运行（由 MyAccessibilityService 写入，FloatingService 读取） */
    const val KEY_SWIPE_RUNNING = "swipe_running"

    /**
     * 跨进程：无障碍服务尚未连接时，预先保存“启动滑动”的参数。
     * FloatingService 写入，MyAccessibilityService 在 onServiceConnected() 读取执行。
     */
    const val KEY_PENDING_START = "pending_start"
    const val KEY_PENDING_DIRECTION = "pending_start_direction"
    const val KEY_PENDING_INTERVAL_MS = "pending_start_interval_ms"
    const val KEY_PENDING_DURATION_SEC = "pending_start_duration_sec"
    const val KEY_PENDING_COUNT = "pending_start_count"
}
