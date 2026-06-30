package com.leowalk.LyricFocus

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.leowalk.LyricFocus.service.LyricService
import com.leowalk.LyricFocus.service.MusicMonitorService
import com.leowalk.LyricFocus.util.RootHelper
import com.leowalk.LyricFocus.AboutActivity

class MainActivity : AppCompatActivity() {

    private lateinit var switchFocusLyric: MaterialSwitch
    private lateinit var switchAppWhitelist: MaterialSwitch
    private lateinit var btnManageWhitelist: MaterialButton
    private lateinit var btnSwitchLyricSource: MaterialButton
    private lateinit var sliderSyncAdvance: Slider
    private lateinit var tvSyncAdvanceValue: TextView
    private lateinit var tvLyricSourceMode: TextView
    private lateinit var tvLyricSourceHit: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvNotificationPermission: TextView
    private lateinit var tvPostNotificationPermission: TextView
    private lateinit var btnGrantNotification: MaterialButton
    private lateinit var btnGrantPostNotification: MaterialButton
    private lateinit var tvRootPermission: TextView
    private lateinit var btnRestartSystemUi: MaterialButton
    private lateinit var tvLsposedStatus: TextView
    private lateinit var btnOpenLsposed: MaterialButton
    private lateinit var btnAbout: MaterialButton
    private lateinit var btnStyleSettings: MaterialButton
    private var isSyncAdvanceSliderUpdating = false
    private var isCheckingRoot = false

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updateStatus()
        if (!granted) maybeShowPostNotificationSettingsDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        setupWindowInsets()
        initViews()
        setupListeners()
        updateStatus()
        ensurePostNotificationsPermission()
    }

    override fun onResume() {
        super.onResume()
        bindSyncAdvanceSlider(FocusPreferences.getSyncAdvanceMs(this))
        updateWhitelistUi()
        updateLyricSourceUi()
        updateStatus()
        updateLsposedStatus()
        checkRootAccessAsync()
    }

    private fun setupWindowInsets() {
        val appBar = findViewById<android.view.View>(R.id.app_bar)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)
        val content = findViewById<android.view.View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
    }

    private fun initViews() {
        switchFocusLyric = findViewById(R.id.switch_focus_lyric)
        switchAppWhitelist = findViewById(R.id.switch_app_whitelist)
        btnManageWhitelist = findViewById(R.id.btn_manage_whitelist)
        btnSwitchLyricSource = findViewById(R.id.btn_switch_lyric_source)
        sliderSyncAdvance = findViewById(R.id.slider_sync_advance)
        tvSyncAdvanceValue = findViewById(R.id.tv_sync_advance_value)
        tvLyricSourceMode = findViewById(R.id.tv_lyric_source_mode)
        tvLyricSourceHit = findViewById(R.id.tv_lyric_source_hit)
        tvServiceStatus = findViewById(R.id.tv_service_status)
        tvNotificationPermission = findViewById(R.id.tv_notification_permission_status)
        tvPostNotificationPermission = findViewById(R.id.tv_post_notification_permission_status)
        btnGrantNotification = findViewById(R.id.btn_grant_notification)
        btnGrantPostNotification = findViewById(R.id.btn_grant_post_notification)
        tvRootPermission = findViewById(R.id.tv_root_permission_status)
        btnRestartSystemUi = findViewById(R.id.btn_restart_systemui)
        tvLsposedStatus = findViewById(R.id.tv_lsposed_status)
        btnOpenLsposed = findViewById(R.id.btn_open_lsposed)
        btnAbout = findViewById(R.id.btn_about)
        btnStyleSettings = findViewById(R.id.btn_style_settings)

        switchFocusLyric.isChecked = FocusPreferences.isFocusEnabled(this)
        switchAppWhitelist.isChecked = FocusPreferences.isAppWhitelistEnabled(this)
        updateLsposedStatus()
        updateWhitelistUi()
        bindSyncAdvanceSlider(FocusPreferences.getSyncAdvanceMs(this))
        updateLyricSourceUi()
    }

    private fun bindSyncAdvanceSlider(advanceMs: Long) {
        isSyncAdvanceSliderUpdating = true
        sliderSyncAdvance.value = advanceMs.toFloat()
        tvSyncAdvanceValue.text = FocusPreferences.formatSyncAdvanceLabel(advanceMs)
        isSyncAdvanceSliderUpdating = false
    }

    private fun setupListeners() {
        switchFocusLyric.setOnCheckedChangeListener { _, checked ->
            FocusPreferences.setFocusEnabled(this, checked)
            broadcastSettingsChanged()
        }
        switchAppWhitelist.setOnCheckedChangeListener { _, checked ->
            FocusPreferences.setAppWhitelistEnabled(this, checked)
            if (checked && FocusPreferences.getWhitelistedPackages(this).isEmpty()) {
                FocusPreferences.setWhitelistedPackages(this, FocusPreferences.defaultMusicPackages())
            }
            updateWhitelistUi()
            broadcastSettingsChanged()
        }
        btnManageWhitelist.setOnClickListener {
            startActivity(Intent(this, AppWhitelistActivity::class.java))
        }
        btnSwitchLyricSource.setOnClickListener {
            showLyricSourcePicker()
        }
        btnGrantNotification.setOnClickListener {
            openNotificationAccessSettings()
        }
        btnGrantPostNotification.setOnClickListener {
            if (NotificationPermissionHelper.needsPostNotificationsPermission()) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    maybeShowPostNotificationSettingsDialog()
                } else {
                    requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                NotificationPermissionHelper.openAppNotificationSettings(this)
            }
        }
        btnRestartSystemUi.setOnClickListener {
            confirmRestartSystemUi()
        }
        btnOpenLsposed.setOnClickListener {
            openLsposedManager()
        }
        btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        btnStyleSettings.setOnClickListener {
            startActivity(Intent(this, StyleSettingsActivity::class.java))
        }
        sliderSyncAdvance.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isSyncAdvanceSliderUpdating) return@addOnChangeListener
            tvSyncAdvanceValue.text = FocusPreferences.formatSyncAdvanceLabel(value.toLong())
        }
        sliderSyncAdvance.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                val normalized = slider.value.toLong().coerceIn(
                    FocusPreferences.MIN_SYNC_ADVANCE_MS,
                    FocusPreferences.MAX_SYNC_ADVANCE_MS
                )
                FocusPreferences.setSyncAdvanceMs(this@MainActivity, normalized)
                bindSyncAdvanceSlider(normalized)
                broadcastSettingsChanged()
            }
        })
    }

    private fun updateLyricSourceUi() {
        tvLyricSourceMode.text = FocusPreferences.formatLyricSourceLabel(
            FocusPreferences.getLyricSource(this)
        )
        val hit = LyricService.currentLyricSourceHit
        val song = LyricService.currentLyricSongLabel
        tvLyricSourceHit.text = when {
            hit.isNotBlank() && song.isNotBlank() -> "$song · $hit"
            hit.isNotBlank() -> hit
            song.isNotBlank() -> "$song · 未命中"
            LyricService.isServiceRunning -> "等待播放…"
            else -> "未播放"
        }
    }

    private fun showLyricSourcePicker() {
        val options = FocusPreferences.lyricSourceOptions()
        val current = FocusPreferences.getLyricSource(this)
        val labels = options.map { it.second }.toTypedArray()
        val checked = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("歌词获取源")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val selected = options[which].first
                if (selected != current) {
                    FocusPreferences.setLyricSource(this, selected)
                    updateLyricSourceUi()
                    broadcastSettingsChanged(includeLyricSource = true)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateWhitelistUi() {
        val enabled = FocusPreferences.isAppWhitelistEnabled(this)
        btnManageWhitelist.isEnabled = enabled
        btnManageWhitelist.alpha = if (enabled) 1f else 0.5f
    }

    private fun broadcastSettingsChanged(includeLyricSource: Boolean = false) {
        try {
            val base = Intent(FocusPreferences.ACTION_SETTINGS_CHANGED).apply {
                putExtra(FocusPreferences.EXTRA_FOCUS_ENABLED, FocusPreferences.isFocusEnabled(this@MainActivity))
                putExtra(FocusPreferences.EXTRA_SHOW_IN_SHADE, FocusPreferences.isShowInShade(this@MainActivity))
                putExtra(FocusPreferences.EXTRA_SHOW_ON_ISLAND, FocusPreferences.isShowOnIsland(this@MainActivity))
                putExtra(FocusPreferences.EXTRA_SYNC_ADVANCE_MS, FocusPreferences.getSyncAdvanceMs(this@MainActivity))
                putExtra(FocusPreferences.EXTRA_APP_WHITELIST_ENABLED, FocusPreferences.isAppWhitelistEnabled(this@MainActivity))
                if (includeLyricSource) {
                    putExtra(FocusPreferences.EXTRA_LYRIC_SOURCE, FocusPreferences.getLyricSource(this@MainActivity))
                }
            }
            sendBroadcast(Intent(base).setPackage("com.android.systemui"))
            sendBroadcast(Intent(base).setPackage(packageName))
        } catch (_: Exception) {
        }
        startMusicMonitorService()
    }

    private fun updateStatus() {
        val hasNotificationPermission = isNotificationServiceEnabled()
        val hasPostNotificationPermission =
            NotificationPermissionHelper.hasPostNotificationsPermission(this)
        val running = LyricService.isServiceRunning

        if (hasNotificationPermission) {
            tvNotificationPermission.text = "已授权"
            tvNotificationPermission.setTextColor(getColor(R.color.green))
        } else {
            tvNotificationPermission.text = "未授权"
            tvNotificationPermission.setTextColor(getColor(R.color.red))
        }

        if (!NotificationPermissionHelper.needsPostNotificationsPermission()) {
            tvPostNotificationPermission.text = "当前系统无需单独授权"
            tvPostNotificationPermission.setTextColor(getColor(R.color.grey))
        } else if (hasPostNotificationPermission) {
            tvPostNotificationPermission.text = "已允许"
            tvPostNotificationPermission.setTextColor(getColor(R.color.green))
        } else {
            tvPostNotificationPermission.text = "未允许"
            tvPostNotificationPermission.setTextColor(getColor(R.color.red))
        }

        tvServiceStatus.text = if (running) "运行中" else "未运行"
        tvServiceStatus.setTextColor(getColor(if (running) R.color.green else R.color.grey))
        updateLyricSourceUi()

        if (hasNotificationPermission && !running) {
            LyricService.start(this)
            startMusicMonitorService()
        }
    }

    private fun startMusicMonitorService() {
        val intent = Intent(this, MusicMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun ensurePostNotificationsPermission() {
        if (!NotificationPermissionHelper.needsPostNotificationsPermission()) return
        if (NotificationPermissionHelper.hasPostNotificationsPermission(this)) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) return
        requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun maybeShowPostNotificationSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("允许发送通知")
            .setMessage("拉取歌词与前台服务需要「发送通知」权限。")
            .setPositiveButton("去开启") { _, _ ->
                NotificationPermissionHelper.openAppNotificationSettings(this)
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return try {
            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
            flat.split(":").any { part ->
                ComponentName.unflattenFromString(part)?.packageName == packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun checkRootAccessAsync() {
        if (isCheckingRoot) return
        isCheckingRoot = true
        tvRootPermission.text = "检测中…"
        tvRootPermission.setTextColor(getColor(R.color.grey))
        btnRestartSystemUi.isEnabled = false
        Thread {
            val granted = RootHelper.checkRootAccess()
            runOnUiThread {
                isCheckingRoot = false
                if (granted) {
                    tvRootPermission.text = "已授权"
                    tvRootPermission.setTextColor(getColor(R.color.green))
                } else {
                    tvRootPermission.text = "未授权"
                    tvRootPermission.setTextColor(getColor(R.color.red))
                }
                btnRestartSystemUi.isEnabled = true
            }
        }.start()
    }

    private fun confirmRestartSystemUi() {
        MaterialAlertDialogBuilder(this)
            .setTitle("重启系统界面")
            .setMessage("将结束 SystemUI 进程并自动恢复，屏幕可能短暂黑屏或闪烁。需要 Root 权限。")
            .setPositiveButton("重启") { _, _ -> restartSystemUi() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun restartSystemUi() {
        btnRestartSystemUi.isEnabled = false
        btnRestartSystemUi.text = "重启中…"
        RootHelper.restartSystemUiAsync { success, message ->
            runOnUiThread {
                btnRestartSystemUi.isEnabled = true
                btnRestartSystemUi.text = "重启"
                if (success) {
                    Toast.makeText(this, "已发送重启指令，系统界面即将恢复", Toast.LENGTH_SHORT).show()
                    checkRootAccessAsync()
                } else {
                    Toast.makeText(
                        this,
                        message ?: "重启失败",
                        Toast.LENGTH_LONG
                    ).show()
                    checkRootAccessAsync()
                }
            }
        }
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun openLsposedManager() {
        val lsposedPackages = listOf(
            "org.lsposed.manager",
            "org.lsposed.lspmanager",
            "com.lsposed.lspmanager"
        )
        for (pkg in lsposedPackages) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(pkg, "$pkg.ui.activity.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            } catch (_: Exception) {
                try {
                    val intent = Intent().apply {
                        component = ComponentName(pkg, "$pkg.ui.MainActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    return
                } catch (_: Exception) {
                    try {
                        val intent = packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) {
                            startActivity(intent)
                            return
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
        Toast.makeText(this, "未检测到 LSPosed Manager，请先安装", Toast.LENGTH_LONG).show()
    }

    private fun updateLsposedStatus() {
        val lsposedPackages = listOf(
            "org.lsposed.manager",
            "org.lsposed.lspmanager",
            "com.lsposed.lspmanager"
        )
        for (pkg in lsposedPackages) {
            try {
                packageManager.getApplicationInfo(pkg, 0)
                tvLsposedStatus.text = "已安装"
                tvLsposedStatus.setTextColor(getColor(R.color.green))
                return
            } catch (_: Exception) {
            }
        }
        tvLsposedStatus.text = "未安装"
        tvLsposedStatus.setTextColor(getColor(R.color.red))
    }
}
