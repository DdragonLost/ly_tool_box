package com.beianlove.assistant

object SettingsPrefs {
    const val PREFS_NAME = "assistant_settings"
    const val KEY_INTERVAL_SEC = "interval_sec"
    const val KEY_DURATION_HOUR = "duration_hour"
    const val KEY_COUNT = "count"
    const val KEY_DIRECTION_UP = "direction_up"
    /** 跨进程：无障碍滑动是否正在运行（由 MyAccessibilityService 写入，FloatingService 读取） */
    const val KEY_SWIPE_RUNNING = "swipe_running"
}
