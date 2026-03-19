package com.beianlove.assistant

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast

class MainActivity : Activity() {


    private lateinit var prefs: SharedPreferences
    private lateinit var editInterval: EditText
    private lateinit var editDurationHour: EditText
    private lateinit var editCount: EditText
    private lateinit var rbUp: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(SettingsPrefs.PREFS_NAME, MODE_PRIVATE)

        editInterval = findViewById(R.id.editIntervalSec)
        editDurationHour = findViewById(R.id.editDurationHour)
        editCount = findViewById(R.id.editCount)

        val rgDirection = findViewById<RadioGroup>(R.id.rgDirection)
        rbUp = findViewById(R.id.rbUp)
        val rbDown = findViewById<RadioButton>(R.id.rbDown)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnFloating = findViewById<Button>(R.id.btnFloating)
        loadSettings()

        btnFloating?.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
                return@setOnClickListener
            }
            saveSettingsFromViews()
            startService(Intent(this, FloatingService::class.java))
            Toast.makeText(this, "悬浮球已开启，点击可开始/停止滑动", Toast.LENGTH_SHORT).show()
        }

        btnStart.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "请先在系统无障碍里开启本助手服务", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }

            val intervalSec = editInterval.text.toString().trim().toLongOrNull()
            val durationHour = editDurationHour.text.toString().trim().toDoubleOrNull()
            val count = editCount.text.toString().trim().toIntOrNull()

            if (intervalSec == null || durationHour == null || count == null) {
                Toast.makeText(this, "请输入合法数字：间隔/时长(小时)/次数", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (intervalSec <= 0) {
                Toast.makeText(this, "间隔必须大于 0（秒）", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val durationSec = (durationHour * 3600).toLong()
            if (durationSec <= 0 && count <= 0) {
                Toast.makeText(this, "请设置“时长>0 或 次数>0”至少一个限制", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val direction = when {
                rbUp.isChecked -> MyAccessibilityService.Direction.UP
                rbDown.isChecked -> MyAccessibilityService.Direction.DOWN
                else -> MyAccessibilityService.Direction.UP
            }

            saveSettings(intervalSec, durationHour, count, direction)
            MyAccessibilityService.startSwipe(
                direction = direction,
                intervalMs = intervalSec * 1000L,
                durationSec = durationSec,
                count = count
            )
            Toast.makeText(this, "已开始：到达时长或次数会自动停止", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            MyAccessibilityService.stopSwipe()
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }

    private fun loadSettings() {
        editInterval.setText(prefs.getLong(SettingsPrefs.KEY_INTERVAL_SEC, 10L).toString())
        editDurationHour.setText(prefs.getString(SettingsPrefs.KEY_DURATION_HOUR, "1") ?: "1")
        editCount.setText(prefs.getInt(SettingsPrefs.KEY_COUNT, 30).toString())
        rbUp.isChecked = prefs.getBoolean(SettingsPrefs.KEY_DIRECTION_UP, true)
    }

    private fun saveSettings(intervalSec: Long, durationHour: Double, count: Int, direction: MyAccessibilityService.Direction) {
        prefs.edit()
            .putLong(SettingsPrefs.KEY_INTERVAL_SEC, intervalSec)
            .putString(SettingsPrefs.KEY_DURATION_HOUR, durationHour.toString())
            .putInt(SettingsPrefs.KEY_COUNT, count)
            .putBoolean(SettingsPrefs.KEY_DIRECTION_UP, direction == MyAccessibilityService.Direction.UP)
            .apply()
    }

    private fun saveSettingsFromViews() {
        val intervalSec = editInterval.text.toString().trim().toLongOrNull() ?: 10L
        val durationHour = editDurationHour.text.toString().trim().toDoubleOrNull() ?: 1.0
        val count = editCount.text.toString().trim().toIntOrNull() ?: 30
        val direction = if (rbUp.isChecked) MyAccessibilityService.Direction.UP else MyAccessibilityService.Direction.DOWN
        saveSettings(intervalSec, durationHour, count, direction)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, MyAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}

