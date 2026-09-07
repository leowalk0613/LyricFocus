package com.leowalk.LyricFocus.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.ImageViewCompat
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
import com.leowalk.LyricFocus.util.AutostartHelper
import com.leowalk.LyricFocus.util.InstalledAppsHelper
import com.leowalk.LyricFocus.util.RootHelper
import com.leowalk.LyricFocus.util.UpdateChecker

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var switchFocusLyric: MaterialSwitch
    private lateinit var switchCustomAodLayout: MaterialSwitch
    private lateinit var switchAodchange: MaterialSwitch
    private lateinit var switchAppWhitelist: MaterialSwitch
    private lateinit var switchHideDesktopIcon: MaterialSwitch
    private lateinit var btnManageWhitelist: MaterialButton
    private lateinit var btnManageLyricSource: MaterialButton
    private lateinit var sliderSyncAdvance: Slider
    private lateinit var tvSyncAdvanceValue: TextView
    private lateinit var tvLyricSourceMode: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var cardServiceStatus: com.google.android.material.card.MaterialCardView
    private lateinit var btnUsageHelp: ImageButton
    private lateinit var tvStatusAodMode: TextView
    private lateinit var tvStatusSongInfo: TextView
    private lateinit var tvStatusLyricSource: TextView
    private lateinit var ivNotificationPermission: ImageView
    private lateinit var ivPostNotificationPermission: ImageView
    private lateinit var btnGrantNotification: MaterialButton
    private lateinit var btnGrantPostNotification: MaterialButton
    private lateinit var ivRootPermission: ImageView
    private lateinit var ivAppListPermission: ImageView
    private lateinit var ivAutostartPermission: ImageView
    private lateinit var ivBatteryPermission: ImageView
    private lateinit var btnGrantAppList: MaterialButton
    private lateinit var btnGrantAutostart: MaterialButton
    private lateinit var btnGrantBattery: MaterialButton
    private var isSyncAdvanceSliderUpdating = false
    private var pendingUpdateInfo: UpdateChecker.UpdateInfo? = null
    private var hasCheckedForUpdates = false
    private var isCheckingRoot = false
    private var aodchangeDimmed = false
    private val statusHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            if (isAdded) updateStatus()
            statusHandler.postDelayed(this, 2000L)
        }
    }

    interface UpdateCallback {
        fun onUpdateChecked(info: UpdateChecker.UpdateInfo)
    }

    private var updateCallback: UpdateCallback? = null

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        if (context is UpdateCallback) {
            updateCallback = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        updateCallback = null
    }

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
        statusHandler.postDelayed(statusRefreshRunnable, 2000L)
        if (!hasCheckedForUpdates) {
            hasCheckedForUpdates = true
            checkForUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        statusHandler.removeCallbacks(statusRefreshRunnable)
    }

    private fun initViews(view: View) {
        switchFocusLyric = view.findViewById(R.id.switch_focus_lyric)
        switchCustomAodLayout = view.findViewById(R.id.switch_custom_aod_layout)
        switchAodchange = view.findViewById(R.id.switch_aodchange)
        switchAppWhitelist = view.findViewById(R.id.switch_app_whitelist)
        switchHideDesktopIcon = view.findViewById(R.id.switch_hide_desktop_icon)
        btnManageWhitelist = view.findViewById(R.id.btn_manage_whitelist)
        btnManageLyricSource = view.findViewById(R.id.btn_manage_lyric_source)
        sliderSyncAdvance = view.findViewById(R.id.slider_sync_advance)
        tvSyncAdvanceValue = view.findViewById(R.id.tv_sync_advance_value)
        tvLyricSourceMode = view.findViewById(R.id.tv_lyric_source_mode)
        tvServiceStatus = view.findViewById(R.id.tv_service_status)
        cardServiceStatus = view.findViewById(R.id.card_service_status)
        tvStatusAodMode = view.findViewById(R.id.tv_status_aod_mode)
        tvStatusSongInfo = view.findViewById(R.id.tv_status_song_info)
        tvStatusLyricSource = view.findViewById(R.id.tv_status_lyric_source)
        ivNotificationPermission = view.findViewById(R.id.iv_notification_permission_status)
        ivPostNotificationPermission = view.findViewById(R.id.iv_post_notification_permission_status)
        btnGrantNotification = view.findViewById(R.id.btn_grant_notification)
        btnGrantPostNotification = view.findViewById(R.id.btn_grant_post_notification)
        ivRootPermission = view.findViewById(R.id.iv_root_permission_status)
        ivAppListPermission = view.findViewById(R.id.iv_app_list_permission_status)
        ivAutostartPermission = view.findViewById(R.id.iv_autostart_permission_status)
        ivBatteryPermission = view.findViewById(R.id.iv_battery_permission_status)
        btnGrantAppList = view.findViewById(R.id.btn_grant_app_list)
        btnGrantAutostart = view.findViewById(R.id.btn_grant_autostart)
        btnGrantBattery = view.findViewById(R.id.btn_grant_battery)
        view.findViewById<ImageButton>(R.id.btn_usage_help).setOnClickListener {
            showUsageHelpDialog()
        }
        btnUsageHelp = view.findViewById(R.id.btn_usage_help)

        val ctx = requireContext()
        switchFocusLyric.isChecked = FocusPreferences.isFocusEnabled(ctx)
        switchCustomAodLayout.isChecked = FocusPreferences.isCustomAodLayout(ctx)
        switchAodchange.isChecked = FocusPreferences.isAodchangeEnabled(ctx)
        switchAppWhitelist.isChecked = FocusPreferences.isAppWhitelistEnabled(ctx)
        switchHideDesktopIcon.isChecked = FocusPreferences.isHideDesktopIcon(ctx)
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
            broadcastSettingsChanged()
        }
        switchAodchange.setOnCheckedChangeListener { _, checked ->
            FocusPreferences.setAodchangeEnabled(requireContext(), checked)
            FocusPreferences.notifyStyleSettingsChanged(requireContext())
            broadcastSettingsChanged()
            updateStatus()
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
        switchHideDesktopIcon.setOnCheckedChangeListener { _, checked ->
            FocusPreferences.setHideDesktopIcon(requireContext(), checked)
            broadcastSettingsChanged()
        }
        btnManageWhitelist.setOnClickListener {
            startActivity(Intent(requireContext(), com.leowalk.LyricFocus.AppWhitelistActivity::class.java))
        }
        btnManageLyricSource.setOnClickListener {
            startActivity(Intent(requireContext(), com.leowalk.LyricFocus.LyricSourceActivity::class.java))
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
        btnGrantAppList.setOnClickListener {
            InstalledAppsHelper.openAppListPermissionSettings(requireContext())
        }
        btnGrantAutostart.setOnClickListener {
            AutostartHelper.openAutostartSettings(requireContext())
        }
        btnGrantBattery.setOnClickListener {
            openBatteryOptimizationSettings()
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
        val source = FocusPreferences.getLyricSource(ctx)
        tvLyricSourceMode.text = FocusPreferences.formatLyricSourceLabel(source)
    }

    private fun buildAodModeLabel(ctx: android.content.Context): String {
        return if (FocusPreferences.isAodchangeEnabled(ctx)) {
            "外部渲染（aodchange）"
        } else if (FocusPreferences.isCustomAodLayout(ctx)) {
            "万象息屏（自定义）"
        } else if (FocusPreferences.isMultiLineLyrics(ctx)) {
            val height = FocusPreferences.getMultiLineHeightDp(ctx)
            "锁屏样式（多行·${height}dp）"
        } else {
            "锁屏样式"
        }
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
                putExtra(FocusPreferences.EXTRA_AODCHANGE_MODE, FocusPreferences.isAodchangeEnabled(ctx))
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

    /**
     * aodchange 外部渲染开启：禁用依赖 hook 的设置项（焦点通知歌词、万象息屏、
     * 白名单、同步提前），仅保留"aodchange 外部渲染"开关本身可操作。
     */
    private fun applyAodchangeDim() {
        try {
            val dimmed = FocusPreferences.isAodchangeEnabled(requireContext())
            if (dimmed == aodchangeDimmed) return
            aodchangeDimmed = dimmed
            val views = listOf(
                switchFocusLyric, switchCustomAodLayout, switchAppWhitelist,
                btnManageWhitelist, sliderSyncAdvance
            )
            for (v in views) {
                v.isEnabled = !dimmed
                v.alpha = if (dimmed) 0.4f else 1f
            }
        } catch (_: Throwable) {
        }
    }

    private fun updateStatus() {
        val ctx = requireContext()
        val hasNotificationPermission = isNotificationServiceEnabled()
        val hasPostNotificationPermission =
            NotificationPermissionHelper.hasPostNotificationsPermission(ctx)
        val running = LyricService.isServiceRunning

        applyAodchangeDim()

        setPermissionStatusIcon(ivNotificationPermission, granted = hasNotificationPermission)
        setPermissionStatusIcon(
            ivPostNotificationPermission,
            granted = !NotificationPermissionHelper.needsPostNotificationsPermission() ||
                hasPostNotificationPermission
        )

        refreshRootPermissionStatus()

        setPermissionStatusIcon(
            ivAppListPermission,
            granted = !InstalledAppsHelper.hasLimitedPackageVisibility(ctx)
        )
        setPermissionStatusIcon(
            ivAutostartPermission,
            granted = AutostartHelper.isAutostartEnabled(ctx)
        )
        setPermissionStatusIcon(
            ivBatteryPermission,
            granted = isIgnoringBatteryOptimizations(ctx)
        )

        tvServiceStatus.text = if (running) "运行中" else "未运行"
        if (running) {
            val accent = com.google.android.material.color.MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorPrimary, "LyricFocus")
            cardServiceStatus.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(accent))
            val onAccent = com.google.android.material.color.MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorOnPrimary, "LyricFocus")
            tvServiceStatus.setTextColor(onAccent)
            tvServiceStatus.setTypeface(tvServiceStatus.typeface, android.graphics.Typeface.BOLD)
            btnUsageHelp.setColorFilter(onAccent)
            tvStatusAodMode.setTextColor(onAccent)
            tvStatusSongInfo.setTextColor(onAccent)
            tvStatusLyricSource.setTextColor(onAccent)
        } else {
            cardServiceStatus.setCardBackgroundColor(
                android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(ctx,
                        com.google.android.material.R.attr.colorSurfaceContainerLow, "LyricFocus")
                )
            )
            val primary = com.google.android.material.color.MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorPrimary, "LyricFocus")
            val onSurface = com.google.android.material.color.MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorOnSurfaceVariant, "LyricFocus")
            tvServiceStatus.setTextColor(primary)
            tvServiceStatus.setTypeface(tvServiceStatus.typeface, android.graphics.Typeface.NORMAL)
            btnUsageHelp.setColorFilter(onSurface)
            tvStatusAodMode.setTextColor(onSurface)
            tvStatusSongInfo.setTextColor(onSurface)
            tvStatusLyricSource.setTextColor(onSurface)
        }

        if (running) {
            val aodMode = buildAodModeLabel(ctx)
            tvStatusAodMode.text = "AOD 模式: $aodMode"
            tvStatusAodMode.visibility = View.VISIBLE

            val song = LyricService.currentLyricSongLabel
            tvStatusSongInfo.visibility = if (song.isNotBlank()) {
                tvStatusSongInfo.text = "当前播放: $song"
                View.VISIBLE
            } else View.GONE

            val source = LyricService.currentLyricSourceHit
            tvStatusLyricSource.visibility = if (source.isNotBlank()) {
                tvStatusLyricSource.text = "歌词来源: $source"
                View.VISIBLE
            } else View.GONE
        } else {
            tvStatusAodMode.visibility = View.GONE
            tvStatusSongInfo.visibility = View.GONE
            tvStatusLyricSource.visibility = View.GONE
        }
        updateLyricSourceUi()

        if (hasNotificationPermission && !running) {
            LyricService.start(ctx)
            startMusicMonitorService()
        }
    }

    private fun refreshRootPermissionStatus() {
        if (!::ivRootPermission.isInitialized) return
        if (isCheckingRoot) return
        isCheckingRoot = true
        setPermissionStatusIcon(ivRootPermission, granted = null)
        Thread {
            val granted = RootHelper.checkRootAccess()
            activity?.runOnUiThread {
                isCheckingRoot = false
                if (!isAdded) return@runOnUiThread
                setPermissionStatusIcon(ivRootPermission, granted = granted)
            }
        }.start()
    }

    private fun setPermissionStatusIcon(view: ImageView, granted: Boolean?) {
        val ctx = view.context
        when (granted) {
            true -> {
                view.setImageResource(R.drawable.ic_status_ok)
                ImageViewCompat.setImageTintList(
                    view,
                    android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.green))
                )
            }
            false -> {
                view.setImageResource(R.drawable.ic_status_fail)
                ImageViewCompat.setImageTintList(
                    view,
                    android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.red))
                )
            }
            null -> {
                view.setImageResource(R.drawable.ic_status_ok)
                ImageViewCompat.setImageTintList(
                    view,
                    android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.grey))
                )
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(ctx: android.content.Context): Boolean {
        return try {
            val pm = ctx.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun openBatteryOptimizationSettings() {
        val ctx = requireContext()
        val candidates = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${ctx.packageName}")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", ctx.packageName, null)
            }
        )
        for (intent in candidates) {
            try {
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    startActivity(intent)
                    return
                }
            } catch (_: Exception) {
            }
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

    private fun showUsageHelpDialog() {
        val textView = TextView(requireContext()).apply {
            text = "【基础步骤】\n" +
                "1. 授予通知访问与发送通知权限\n" +
                "2. LSPosed 勾选「系统界面」和「息屏与锁屏编辑(com.miui.aod)」\n" +
                "3. 使用 Root 重启系统界面使 Hook 生效\n" +
                "4. 播放音乐后锁屏/AOD 显示歌词\n\n" +
                "【后台保活】\n" +
                "5. 设置 → 应用 → LyricFocus → 省电策略设为「无限制」，避免后台被杀\n" +
                "6. 设置 → 通知与状态栏 → 通知管理 → LyricFocus：允许通知，关闭「静默」\n\n" +
                "【常见问题】\n" +
                "7. 若使用白名单，确认目标音乐 App 未被「应用双开」隔离到不同用户空间\n" +
                "8. 焦点通知白名单：系统级焦点通知白名单限制会导致 LyricFocus 无法正常显示歌词，需通过 LSPosed 模块（如 HyperCeiler）移除该限制"
            setPadding(48, 24, 48, 0)
            setTextIsSelectable(true)
        }
        val scrollView = ScrollView(requireContext()).apply {
            addView(textView)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("使用说明")
            .setView(scrollView)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun checkForUpdates() {
        Thread {
            val info = try {
                UpdateChecker(requireContext()).checkForUpdates()
            } catch (_: Exception) {
                null
            }
            activity?.runOnUiThread {
                info?.let {
                    pendingUpdateInfo = it
                    updateCallback?.onUpdateChecked(it)
                }
            }
        }.start()
    }

    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        val checker = UpdateChecker(requireContext())
        val currentVersion = checker.getCurrentVersion(requireContext())
        val versionBlock = "当前版本：$currentVersion\n最新版本：${info.latestVersion}"
        val textView = TextView(requireContext()).apply {
            text = "$versionBlock\n\n正在加载更新日志…"
            setPadding(48, 24, 48, 0)
            setTextIsSelectable(true)
        }
        val scrollView = ScrollView(requireContext()).apply {
            addView(textView)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("发现新版本")
            .setView(scrollView)
            .setNeutralButton("下载渠道") { _, _ ->
                view?.post { showDownloadChannels(info) }
            }
            .setNegativeButton("关闭", null)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            val buttons = listOf(
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE),
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            )
            buttons.forEach { it?.isClickable = false }
            view?.postDelayed({
                if (dialog.isShowing) buttons.forEach { it?.isClickable = true }
            }, 450L)
        }
        dialog.show()
        Thread {
            val notes = try {
                checker.fetchReleaseNotes(info)
            } catch (_: Exception) {
                "加载更新日志失败"
            }
            activity?.runOnUiThread {
                if (!isAdded || !dialog.isShowing) return@runOnUiThread
                textView.text = "$versionBlock\n\n$notes"
            }
        }.start()
    }

    private fun showDownloadChannels(info: UpdateChecker.UpdateInfo) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("下载渠道")
            .setItems(arrayOf("GitHub", "Gitee", "123网盘")) { _, which ->
                when (which) {
                    0 -> openUrl(
                        info.githubUrl
                            ?: "https://github.com/leowalk0613/LyricFocus/releases"
                    )
                    1 -> openUrl(
                        info.giteeUrl
                            ?: "https://gitee.com/leowalk0613/LyricFocus/releases"
                    )
                    2 -> openUrl("https://1825191091.share.123pan.cn/123pan/jNBsjv-vZrV?pwd=Ifn3")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }
}
