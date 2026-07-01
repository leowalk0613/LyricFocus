package com.leowalk.LyricFocus.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.NotificationPermissionHelper
import com.leowalk.LyricFocus.R
import com.leowalk.LyricFocus.service.LyricService
import com.leowalk.LyricFocus.service.MusicMonitorService

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var switchFocusLyric: MaterialSwitch
    private lateinit var switchCustomAodLayout: MaterialSwitch
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
    private var isSyncAdvanceSliderUpdating = false

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updateStatus()
        if (!granted) maybeShowPostNotificationSettingsDialog()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        updateStatus()
        ensurePostNotificationsPermission()
    }

    override fun onResume() {
        super.onResume()
        bindSyncAdvanceSlider(FocusPreferences.getSyncAdvanceMs(requireContext()))
        updateWhitelistUi()
        updateLyricSourceUi()
        updateStatus()
    }

    private fun initViews(view: View) {
        switchFocusLyric = view.findViewById(R.id.switch_focus_lyric)
        switchCustomAodLayout = view.findViewById(R.id.switch_custom_aod_layout)
        switchAppWhitelist = view.findViewById(R.id.switch_app_whitelist)
        btnManageWhitelist = view.findViewById(R.id.btn_manage_whitelist)
        btnSwitchLyricSource = view.findViewById(R.id.btn_switch_lyric_source)
        sliderSyncAdvance = view.findViewById(R.id.slider_sync_advance)
        tvSyncAdvanceValue = view.findViewById(R.id.tv_sync_advance_value)
        tvLyricSourceMode = view.findViewById(R.id.tv_lyric_source_mode)
        tvLyricSourceHit = view.findViewById(R.id.tv_lyric_source_hit)
        tvServiceStatus = view.findViewById(R.id.tv_service_status)
        tvNotificationPermission = view.findViewById(R.id.tv_notification_permission_status)
        tvPostNotificationPermission = view.findViewById(R.id.tv_post_notification_permission_status)
        btnGrantNotification = view.findViewById(R.id.btn_grant_notification)
        btnGrantPostNotification = view.findViewById(R.id.btn_grant_post_notification)
        tvRootPermission = view.findViewById(R.id.tv_root_permission_status)

        val ctx = requireContext()
        switchFocusLyric.isChecked = FocusPreferences.isFocusEnabled(ctx)
        switchCustomAodLayout.isChecked = FocusPreferences.isCustomAodLayout(ctx)
        switchAppWhitelist.isChecked = FocusPreferences.isAppWhitelistEnabled(ctx)
        updateWhitelistUi()
        bindSyncAdvanceSlider(FocusPreferences.getSyncAdvanceMs(ctx))
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
            FocusPreferences.setFocusEnabled(requireContext(), checked)
            broadcastSettingsChanged()
        }
        switchCustomAodLayout.setOnCheckedChangeListener { _, checked ->
            FocusPreferences.setCustomAodLayout(requireContext(), checked)
            FocusPreferences.notifyStyleSettingsChanged(requireContext())
        }
        switchAppWhitelist.setOnCheckedChangeListener { _, checked ->
            val ctx = requireContext()
            FocusPreferences.setAppWhitelistEnabled(ctx, checked)
            if (checked && FocusPreferences.getWhitelistedPackages(ctx).isEmpty()) {
                FocusPreferences.setWhitelistedPackages(ctx, FocusPreferences.defaultMusicPackages())
            }
            updateWhitelistUi()
            broadcastSettingsChanged()
        }
        btnManageWhitelist.setOnClickListener {
            startActivity(Intent(requireContext(), com.leowalk.LyricFocus.AppWhitelistActivity::class.java))
        }
        btnSwitchLyricSource.setOnClickListener {
            showLyricSourcePicker()
        }
        btnGrantNotification.setOnClickListener {
            openNotificationAccessSettings()
        }
        btnGrantPostNotification.setOnClickListener {
            val ctx = requireContext()
            if (NotificationPermissionHelper.needsPostNotificationsPermission()) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    maybeShowPostNotificationSettingsDialog()
                } else {
                    requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                NotificationPermissionHelper.openAppNotificationSettings(ctx)
            }
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
                FocusPreferences.setSyncAdvanceMs(requireContext(), normalized)
                bindSyncAdvanceSlider(normalized)
                broadcastSettingsChanged()
            }
        })
    }

    private fun updateLyricSourceUi() {
        val ctx = requireContext()
        tvLyricSourceMode.text = FocusPreferences.formatLyricSourceLabel(
            FocusPreferences.getLyricSource(ctx)
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
        val ctx = requireContext()
        val options = FocusPreferences.lyricSourceOptions()
        val current = FocusPreferences.getLyricSource(ctx)
        val labels = options.map { it.second }.toTypedArray()
        val checked = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(ctx)
            .setTitle("歌词获取源")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val selected = options[which].first
                if (selected != current) {
                    FocusPreferences.setLyricSource(ctx, selected)
                    updateLyricSourceUi()
                    broadcastSettingsChanged(includeLyricSource = true)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateWhitelistUi() {
        val enabled = FocusPreferences.isAppWhitelistEnabled(requireContext())
        btnManageWhitelist.isEnabled = enabled
        btnManageWhitelist.alpha = if (enabled) 1f else 0.5f
    }

    private fun broadcastSettingsChanged(includeLyricSource: Boolean = false) {
        val ctx = requireContext()
        try {
            val base = Intent(FocusPreferences.ACTION_SETTINGS_CHANGED).apply {
                putExtra(FocusPreferences.EXTRA_FOCUS_ENABLED, FocusPreferences.isFocusEnabled(ctx))
                putExtra(FocusPreferences.EXTRA_SHOW_IN_SHADE, FocusPreferences.isShowInShade(ctx))
                putExtra(FocusPreferences.EXTRA_SHOW_ON_ISLAND, FocusPreferences.isShowOnIsland(ctx))
                putExtra(FocusPreferences.EXTRA_SYNC_ADVANCE_MS, FocusPreferences.getSyncAdvanceMs(ctx))
                putExtra(FocusPreferences.EXTRA_APP_WHITELIST_ENABLED, FocusPreferences.isAppWhitelistEnabled(ctx))
                if (includeLyricSource) {
                    putExtra(FocusPreferences.EXTRA_LYRIC_SOURCE, FocusPreferences.getLyricSource(ctx))
                }
            }
            ctx.sendBroadcast(Intent(base).setPackage("com.android.systemui"))
            ctx.sendBroadcast(Intent(base).setPackage(ctx.packageName))
        } catch (_: Exception) {
        }
        startMusicMonitorService()
    }

    private fun updateStatus() {
        val ctx = requireContext()
        val hasNotificationPermission = isNotificationServiceEnabled()
        val hasPostNotificationPermission =
            NotificationPermissionHelper.hasPostNotificationsPermission(ctx)
        val running = LyricService.isServiceRunning

        if (hasNotificationPermission) {
            tvNotificationPermission.text = "已授权"
            tvNotificationPermission.setTextColor(ctx.getColor(R.color.green))
        } else {
            tvNotificationPermission.text = "未授权"
            tvNotificationPermission.setTextColor(ctx.getColor(R.color.red))
        }

        if (!NotificationPermissionHelper.needsPostNotificationsPermission()) {
            tvPostNotificationPermission.text = "当前系统无需单独授权"
            tvPostNotificationPermission.setTextColor(ctx.getColor(R.color.grey))
        } else if (hasPostNotificationPermission) {
            tvPostNotificationPermission.text = "已允许"
            tvPostNotificationPermission.setTextColor(ctx.getColor(R.color.green))
        } else {
            tvPostNotificationPermission.text = "未允许"
            tvPostNotificationPermission.setTextColor(ctx.getColor(R.color.red))
        }

        tvRootPermission.text = "请在工具栏使用重启 SystemUI"
        tvRootPermission.setTextColor(ctx.getColor(R.color.grey))

        tvServiceStatus.text = if (running) "运行中" else "未运行"
        tvServiceStatus.setTextColor(ctx.getColor(if (running) R.color.green else R.color.grey))
        updateLyricSourceUi()

        if (hasNotificationPermission && !running) {
            LyricService.start(ctx)
            startMusicMonitorService()
        }
    }

    private fun startMusicMonitorService() {
        val ctx = requireContext()
        val intent = Intent(ctx, MusicMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    private fun ensurePostNotificationsPermission() {
        val ctx = requireContext()
        if (!NotificationPermissionHelper.needsPostNotificationsPermission()) return
        if (NotificationPermissionHelper.hasPostNotificationsPermission(ctx)) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) return
        requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun maybeShowPostNotificationSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("允许发送通知")
            .setMessage("拉取歌词与前台服务需要「发送通知」权限。")
            .setPositiveButton("去开启") { _, _ ->
                NotificationPermissionHelper.openAppNotificationSettings(requireContext())
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return try {
            val ctx = requireContext()
            val flat = ctx.contentResolver.let {
                Settings.Secure.getString(it, "enabled_notification_listeners")
            } ?: return false
            flat.split(":").any { part ->
                ComponentName.unflattenFromString(part)?.packageName == ctx.packageName
            }
        } catch (_: Exception) {
            false
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
}
