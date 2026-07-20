package com.leowalk.LyricFocus.xposed.hook.systemui

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.FocusStyleSnapshot
import com.leowalk.LyricFocus.notification.HyperFocusLyricStyle
import com.leowalk.LyricFocus.xposed.hook.BaseHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray

/**
 * HyperOS 焦点通知歌词（miui.focus.param），锁屏/AOD 通过 updatable 焦点通知刷新。
 * 参考 [HyperCeiler FocusNotifLyric](https://github.com/ReChronoRain/HyperCeiler)。
 */
class SystemUIHyperFocusHook : BaseHook() {

    override val tag: String = "SystemUIHyperFocusHook"

    companion object {
        private const val ACTION_LYRIC_DATA = "com.leowalk.LyricFocus.action.LYRIC_DATA"
        private const val ACTION_UPDATE_LYRIC = "com.leowalk.LyricFocus.action.UPDATE_LYRIC"
        private const val ACTION_ALARM_TICK = "com.leowalk.LyricFocus.systemui.action.ALARM_TICK"
        private const val ACTION_PLAYBACK_STATE = "com.leowalk.LyricFocus.action.PLAYBACK_STATE"
        const val ACTION_SETTINGS_CHANGED = FocusPreferences.ACTION_SETTINGS_CHANGED
        private const val ACTION_REQUEST_RESYNC = FocusPreferences.ACTION_REQUEST_RESYNC

        private const val EXTRA_LYRIC_TEXT = "lyric_text"
        private const val EXTRA_SECOND_LINE = "second_line"
        private const val EXTRA_LINE_TRANSLATION = "line_translation"
        private const val EXTRA_IS_PLAYING = "is_playing"
        private const val EXTRA_PLAYING = "playing"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_LYRIC_JSON = "lyric_json"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_OFFSET = "offset"
        private const val EXTRA_SYNC_ADVANCE = "sync_advance"
        private const val EXTRA_MUSIC_PACKAGE = "music_package"
        private const val EXTRA_FORCE_RESYNC = "force_resync"

        private var cachedFocusRow: View? = null

        private var musicPackage = ""

        private var currentLyricText = ""
        private var currentSecondLine = ""
        private var currentLineTranslation: String? = null
        private var currentTitle = ""
        private var currentArtist = ""
        private var isPlaying = false
        private var currentPosition = 0L
        private var lastUpdateTime = 0L
        private var lyricOffset = 0L
        private var syncAdvanceMs = FocusPreferences.DEFAULT_SYNC_ADVANCE_MS
        private var lyricLines: List<LyricLineData> = emptyList()
        private var preferAppLyric = false
        private var focusEnabled = true
        private var showInShade = false
        private var pinAboveMedia = true
        private var showOnIsland = false
        private var aodKeepaliveSec = FocusPreferences.DEFAULT_AOD_KEEPALIVE_SEC
        private var lastFocusNotifyTime = 0L
        private var lastNotifiedLyric = ""
        private var lastNotifiedSecond = ""
        private var lastNotifiedTitle = ""
        private var lastNotifiedArtist = ""
        private var lastNotifiedMultiLineKey = ""

        private const val MIN_TICK_MS = 500L
        private const val LAYOUT_REFLOW_DEBOUNCE_MS = 5_000L
        /** 亮屏/解锁后重发焦点通知，等待 Keyguard 与 SystemUI 就绪 */
        private const val SCREEN_REPOST_DELAY_MS = 400L
        /**
         * 息屏进 AOD：先让系统完成一次 DiffDispatch 切换动画，再决定是否 cancel+notify 重绑 rvAod。
         * 原先 550ms 正好落在系统切换动画中途，表现为「亮屏锁屏正常、息屏后概率抽搐」。
         */
        private const val SCREEN_OFF_REPOST_DELAY_MS = 1100L
        /**
         * AOD DiffDispatch（alpha delay 150ms + scale）未结束前再 cancel+notify
         * 会截断动画；息屏重绑与换行/forceResync 叠在一起时概率复现。
         */
        private const val AOD_SWITCH_SETTLE_MS = 900L

        @Volatile
        private var needsAodRebind = false
        /** 最近一次会触发 cancel+notify 的焦点会话重建时间 */
        private var lastFocusRecreateAt = 0L
        private var pendingDisplayRepost: Runnable? = null
        private var pendingCoalescedFocusPost: Runnable? = null

        private enum class FocusRefreshMode {
            LINE_CHANGE,
            KEEPALIVE
        }

        private fun aodKeepaliveMs(): Long = aodKeepaliveSec * 1000L

        private fun effectiveKeepaliveMs(): Long {
            return (aodKeepaliveSec.coerceAtMost(FocusPreferences.SYSTEM_FOCUS_MAX_KEEPALIVE_SEC) * 1000L)
        }

        private var allowLayoutReflow = false
        private var lastLayoutReflowTime = 0L
        private var cachedClockBottom: Int? = null
        private var cachedClockBottomLyric = ""
        private var cachedStackContentHeight: Int? = null
        private var cachedStackContentLyric = ""

        private var systemUIContext: Context? = null
        private var lyricReceiver: BroadcastReceiver? = null
        private var alarmReceiver: BroadcastReceiver? = null
        private var screenReceiver: BroadcastReceiver? = null
        private var notificationManager: NotificationManager? = null
        private var alarmManager: AlarmManager? = null
        private var alarmIntent: PendingIntent? = null
        private val handler = Handler(Looper.getMainLooper())

        private data class LyricLineData(
            val time: Long,
            val text: String,
            val translation: String? = null,
            val reading: String? = null
        ) {
            fun secondaryText(): String? {
                return listOfNotNull(
                    translation?.takeIf { it.isNotBlank() },
                    reading?.takeIf { it.isNotBlank() }
                ).joinToString("\n").takeIf { it.isNotBlank() }
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        log("Starting SystemUI Hyper Focus hook")
        hookSystemUIContext(lpparam)
        hookFocusPermissionBypass(lpparam.classLoader)
        hookHideFromShadeIfNeeded(lpparam.classLoader)
        hookPinAboveMediaCompat(lpparam.classLoader)
        FocusPinAboveHook.install(lpparam.classLoader, tag)
        hookSuppressIslandIfNeeded(lpparam.classLoader)
        hookKeyguardRepost(lpparam.classLoader)
        hookForceAodUpdate(lpparam.classLoader)
    }

    private fun hookSuppressIslandIfNeeded(classLoader: ClassLoader) {
        FocusIslandSuppressHook.install(classLoader, tag) { systemUIContext }
    }

    private fun hookSystemUIContext(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.SystemUIApplication",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as android.app.Application
                        systemUIContext = app.applicationContext
                        notificationManager = systemUIContext?.getSystemService(
                            Context.NOTIFICATION_SERVICE
                        ) as NotificationManager?
                        alarmManager = systemUIContext?.getSystemService(
                            Context.ALARM_SERVICE
                        ) as AlarmManager?
                        resetSessionState()
                        createNotificationChannel()
                        createAlarmIntent()
                        registerLyricReceiver()
                        registerAlarmReceiver()
                        registerScreenReceiver()
                        refreshSettings()
                        scheduleResyncRequests()
                        log("SystemUI context ready for focus lyrics")
                    }
                }
            )
        } catch (e: Throwable) {
            logE("Error hooking SystemUI context", e)
        }
    }

    private fun hookFocusPermissionBypass(classLoader: ClassLoader) {
        bypassBooleanMethod(classLoader, "miui.systemui.notification.NotificationSettingsManager", "canShowFocus")
        bypassBooleanMethod(classLoader, "miui.systemui.notification.NotificationSettingsManager", "canCustomFocus")
        tryHookAuthBypass(classLoader)
    }

    private fun bypassBooleanMethod(classLoader: ClassLoader, className: String, methodName: String) {
        try {
            XposedHelpers.findAndHookMethod(
                className,
                classLoader,
                methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
            log("Bypassed $className.$methodName")
        } catch (e: Throwable) {
            log("Skip bypass $className.$methodName: ${e.message}")
        }
    }

    private fun tryHookAuthBypass(classLoader: ClassLoader) {
        try {
            val authClass = classLoader.loadClass(
                "miui.systemui.notification.auth.AuthManager\$AuthServiceCallback\$onAuthResult\$1"
            )
            XposedHelpers.findAndHookMethod(
                authClass,
                "invokeSuspend",
                Object::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val bundle = XposedHelpers.getObjectField(param.thisObject, "\$authBundle") as? Bundle
                        bundle?.putInt("result_code", 0)
                    }
                }
            )
            log("Auth bypass hooked")
        } catch (_: Throwable) {
        }
    }

    private fun hookHideFromShadeIfNeeded(classLoader: ClassLoader) {
        hookHideFocusRowInShadeStack(classLoader)
    }

    /** 仅在下拉通知栏隐藏焦点行，不阻断 bind pipeline（锁屏/AOD/岛仍正常绑定） */
    private fun hookHideFocusRowInShadeStack(classLoader: ClassLoader) {
        try {
            val stackClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.stack.NotificationStackScrollLayout",
                classLoader
            )
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    applyFocusRowShadeVisibility(param.args[0] as? View)
                }
            }
            XposedBridge.hookAllMethods(stackClass, "addView", hook)
            XposedBridge.hookAllMethods(stackClass, "addViewInLayout", hook)
            log("Shade-only focus row hide hook ready")
        } catch (e: Throwable) {
            log("Shade hide hook skipped: ${e.message}")
        }
    }

    private fun applyFocusRowShadeVisibility(view: View?) {
        if (showInShade || view == null) return
        val row = findFocusNotificationRow(view) ?: return
        cachedFocusRow = row
        if (shouldShowFocusOnLockScreen()) {
            row.visibility = View.VISIBLE
            row.layoutParams?.let { lp ->
                if (lp.height == 0) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    row.layoutParams = lp
                }
            }
            return
        }
        try {
            row.visibility = View.GONE
            row.layoutParams?.let { lp ->
                lp.height = 0
                row.layoutParams = lp
            }
        } catch (_: Throwable) {
        }
    }

    private fun findFocusNotificationRow(view: View): View? {
        val rowClass = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"
        var row: View? = if (view.javaClass.name == rowClass) view else null
        if (row == null) {
            var current: View? = view
            while (current != null) {
                if (current.javaClass.name == rowClass) {
                    row = current
                    break
                }
                current = current.parent as? View
            }
        }
        row ?: return null
        val entry = XposedHelpers.callMethod(row, "getEntry") ?: return null
        val sbn = XposedHelpers.getObjectField(entry, "mSbn") as? StatusBarNotification ?: return null
        return if (sbn.notification?.channelId == HyperFocusLyricStyle.CHANNEL_ID) row else null
    }

    /** 锁屏或息屏 AOD：焦点通知应可见 */
    private fun shouldShowFocusOnLockScreen(): Boolean {
        return isKeyguardLocked() || !isScreenInteractive()
    }

    private fun isKeyguardLocked(): Boolean {
        val km = systemUIContext?.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isKeyguardLocked == true
    }

    private fun hideFocusRowsInUnlockedShade() {
        if (showInShade) return
        cachedFocusRow?.let { applyFocusRowShadeVisibility(it) }
    }

    private fun isPlaceholderLyric(text: String): Boolean {
        return text.isBlank() ||
            text == "\u6682\u65e0\u6b4c\u8bcd" ||
            text == "\u52a0\u8f7d\u6b4c\u8bcd\u4e2d..."
    }

    private fun hookPinAboveMediaCompat(classLoader: ClassLoader) {
        hookSuppressFocusRowHeightReflow(classLoader)
        hookStabilizeKeyguardSinking(classLoader)
        hookDebouncePanelReflow(classLoader)
    }

    private fun hookSuppressFocusRowHeightReflow(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
                classLoader,
                "notifyHeightChanged",
                Boolean::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!pinAboveMedia || allowLayoutReflow) return
                        if (param.args[0] != true) return
                        val entry = XposedHelpers.callMethod(param.thisObject, "getEntry") ?: return
                        val sbn = XposedHelpers.getObjectField(entry, "mSbn") as? StatusBarNotification
                            ?: return
                        if (sbn.notification?.channelId == HyperFocusLyricStyle.CHANNEL_ID) {
                            param.args[0] = false
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log("ExpandableNotificationRow height hook skipped: ${e.message}")
        }
    }

    private fun hookStabilizeKeyguardSinking(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.keyguard.clock.KeyguardClockContainer",
                classLoader,
                "getClockBottom",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!pinAboveMedia || currentLyricText.isBlank()) return
                        val bottom = param.result as? Int ?: return
                        if (currentLyricText == cachedClockBottomLyric && cachedClockBottom != null) {
                            param.result = cachedClockBottom
                            return
                        }
                        cachedClockBottom = bottom
                        cachedClockBottomLyric = currentLyricText
                    }
                }
            )
        } catch (e: Throwable) {
            log("KeyguardClockContainer hook skipped: ${e.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.stack.NotificationStackScrollLayout",
                classLoader,
                "getIntrinsicContentHeight",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!pinAboveMedia || currentLyricText.isBlank()) return
                        val height = param.result as? Int ?: return
                        if (currentLyricText == cachedStackContentLyric && cachedStackContentHeight != null) {
                            param.result = cachedStackContentHeight
                            return
                        }
                        cachedStackContentHeight = height
                        cachedStackContentLyric = currentLyricText
                    }
                }
            )
        } catch (e: Throwable) {
            log("NotificationStackScrollLayout height hook skipped: ${e.message}")
        }
    }

    private fun hookDebouncePanelReflow(classLoader: ClassLoader) {
        val targets = listOf(
            "com.android.systemui.shade.MiuiNotificationPanelViewController",
            "com.android.systemui.shade.NotificationPanelViewController"
        )
        for (target in targets) {
            try {
                XposedHelpers.findAndHookMethod(
                    target,
                    classLoader,
                    "positionClockAndNotifications",
                    Boolean::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!pinAboveMedia) return
                            val now = System.currentTimeMillis()
                            if (!allowLayoutReflow &&
                                now - lastLayoutReflowTime < LAYOUT_REFLOW_DEBOUNCE_MS
                            ) {
                                param.result = null
                            }
                        }
                    }
                )
                log("Debounced positionClockAndNotifications on $target")
                break
            } catch (_: Throwable) {
            }
        }
    }

    private fun syncFocusPinState() {
        FocusPinState.pinAboveMedia = pinAboveMedia
        FocusPinState.isPlaying = isPlaying
        FocusPinState.lyricActive = currentLyricText.isNotBlank()
    }

    private fun scheduleLyricFocusReorder() {
        syncFocusPinState()
        FocusPinAboveHook.scheduleViewReorder(cachedFocusRow)
    }

    private fun invalidateLayoutCache() {
        cachedClockBottom = null
        cachedClockBottomLyric = ""
        cachedStackContentHeight = null
        cachedStackContentLyric = ""
    }

    private fun markLayoutReflowAllowed(forceRecreate: Boolean) {
        if (!pinAboveMedia) return
        if (forceRecreate) {
            invalidateLayoutCache()
            allowLayoutReflow = true
            lastLayoutReflowTime = System.currentTimeMillis()
            handler.postDelayed({ allowLayoutReflow = false }, 800L)
        }
    }

    private fun resetSessionState() {
        currentLyricText = ""
        currentSecondLine = ""
        currentLineTranslation = null
        currentTitle = ""
        currentArtist = ""
        isPlaying = false
        currentPosition = 0L
        lastUpdateTime = 0L
        lyricOffset = 0L
        lyricLines = emptyList()
        preferAppLyric = false
        musicPackage = ""
        lastFocusNotifyTime = 0L
        lastFocusRecreateAt = 0L
        clearNotifiedLyricContent()
        needsAodRebind = false
        cancelPendingDisplayRepost()
        cancelPendingCoalescedFocusPost()
        invalidateLayoutCache()
        HyperFocusLyricStyle.resetPostedCache()
        syncFocusPinState()
    }

    private fun scheduleResyncRequests() {
        listOf(500L, 1500L, 4000L).forEach { delayMs ->
            handler.postDelayed({ requestResyncFromApp() }, delayMs)
        }
    }

    private fun requestResyncFromApp() {
        try {
            val intent = Intent(ACTION_REQUEST_RESYNC).setPackage(HyperFocusLyricStyle.MODULE_PACKAGE)
            systemUIContext?.sendBroadcast(intent)
            log("Requested focus state resync from app")
        } catch (e: Throwable) {
            logE("Failed to request resync from app", e)
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    HyperFocusLyricStyle.CHANNEL_ID,
                    "LyricFocus 焦点歌词",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description =
                        "LyricFocus 模块使用的 HyperOS 焦点通知：在锁屏与息屏（AOD）显示歌词"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    enableLights(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (e: Throwable) {
            logE("Failed to create channel", e)
        }
    }

    private fun createAlarmIntent() {
        try {
            val intent = Intent(ACTION_ALARM_TICK).setPackage("com.android.systemui")
            alarmIntent = PendingIntent.getBroadcast(
                systemUIContext,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Throwable) {
            logE("Failed to create alarm intent", e)
        }
    }

    private fun registerLyricReceiver() {
        unregisterReceiverSafe(lyricReceiver)
        lyricReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_LYRIC_DATA -> handleLyricData(intent)
                    ACTION_UPDATE_LYRIC -> handleSimpleUpdate(intent)
                    ACTION_PLAYBACK_STATE -> handlePlaybackState(intent)
                    ACTION_SETTINGS_CHANGED -> handleSettingsChanged(intent)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_LYRIC_DATA)
            addAction(ACTION_UPDATE_LYRIC)
            addAction(ACTION_PLAYBACK_STATE)
            addAction(ACTION_SETTINGS_CHANGED)
        }
        registerReceiverSafe(lyricReceiver!!, filter)
    }

    private fun registerAlarmReceiver() {
        unregisterReceiverSafe(alarmReceiver)
        alarmReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_ALARM_TICK) {
                    updateLyricProgress()
                }
            }
        }
        registerReceiverSafe(alarmReceiver!!, IntentFilter(ACTION_ALARM_TICK))
    }

    /** 锁屏显示时重发，避免亮屏锁屏下 rv 未绑定 */
    private fun hookKeyguardRepost(classLoader: ClassLoader) {
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!isKeyguardLocked()) return
                scheduleDisplayRepost(SCREEN_REPOST_DELAY_MS, forScreenOff = false)
            }
        }
        val keyguardShowingHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val showing = param.args.getOrNull(0) as? Boolean ?: return
                if (!showing) return
                scheduleDisplayRepost(SCREEN_REPOST_DELAY_MS, forScreenOff = false)
            }
        }
        val methodNames = listOf(
            "notifyKeyguardStateChanged",
            "handleKeyguardChanged",
            "updateKeyguardState"
        )
        for (name in methodNames) {
            try {
                if (name == "notifyKeyguardStateChanged") {
                    XposedHelpers.findAndHookMethod(
                        "com.android.keyguard.KeyguardUpdateMonitor",
                        classLoader,
                        name,
                        Boolean::class.java,
                        Boolean::class.java,
                        keyguardShowingHook
                    )
                } else {
                    XposedHelpers.findAndHookMethod(
                        "com.android.keyguard.KeyguardUpdateMonitor",
                        classLoader,
                        name,
                        hook
                    )
                }
                log("Keyguard repost hook: KeyguardUpdateMonitor.$name")
                return
            } catch (_: Throwable) {
            }
        }
        log("Keyguard repost hook skipped")
    }

    private fun registerScreenReceiver() {
        unregisterReceiverSafe(screenReceiver)
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        needsAodRebind = true
                        // 系统从息屏起播 DiffDispatch；静默至重绑时刻，期间只合并不 cancel
                        lastFocusRecreateAt = System.currentTimeMillis() -
                            AOD_SWITCH_SETTLE_MS + SCREEN_OFF_REPOST_DELAY_MS
                        scheduleDisplayRepost(SCREEN_OFF_REPOST_DELAY_MS, forScreenOff = true)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        needsAodRebind = false
                        scheduleDisplayRepost(SCREEN_REPOST_DELAY_MS, forScreenOff = false)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        cancelPendingDisplayRepost()
                        handler.postDelayed({
                            hideFocusRowsInUnlockedShade()
                            needsAodRebind = false
                            repostFocusForDisplayChange(forScreenOff = false)
                        }, SCREEN_REPOST_DELAY_MS)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiverSafe(screenReceiver!!, filter)
    }

    private fun cancelPendingDisplayRepost() {
        pendingDisplayRepost?.let { handler.removeCallbacks(it) }
        pendingDisplayRepost = null
    }

    private fun cancelPendingCoalescedFocusPost() {
        pendingCoalescedFocusPost?.let { handler.removeCallbacks(it) }
        pendingCoalescedFocusPost = null
    }

    private fun scheduleDisplayRepost(delayMs: Long, forScreenOff: Boolean) {
        cancelPendingDisplayRepost()
        val task = Runnable {
            pendingDisplayRepost = null
            repostFocusForDisplayChange(forScreenOff = forScreenOff)
        }
        pendingDisplayRepost = task
        handler.postDelayed(task, delayMs.coerceAtLeast(0L))
    }

    private fun isFocusContentAlreadyBound(): Boolean {
        return lastFocusNotifyTime > 0L &&
            currentLyricText.isNotBlank() &&
            isLyricDisplayContentSame() &&
            currentTitle == lastNotifiedTitle &&
            currentArtist == lastNotifiedArtist
    }

    private fun millisSinceLastFocusRecreate(): Long {
        if (lastFocusRecreateAt <= 0L) return Long.MAX_VALUE
        return System.currentTimeMillis() - lastFocusRecreateAt
    }

    private fun remainingAodSwitchSettleMs(): Long {
        val elapsed = millisSinceLastFocusRecreate()
        if (elapsed >= AOD_SWITCH_SETTLE_MS) return 0L
        return AOD_SWITCH_SETTLE_MS - elapsed
    }

    /**
     * 显示路径变化时重发焦点通知。息屏路径会与冷启动 forceResync 合并，
     * 避免连续 cancel+notify 截断 AOD DiffDispatch 切换动画。
     */
    private fun repostFocusForDisplayChange(forScreenOff: Boolean = false) {
        if (!focusEnabled || !isPlaying || currentLyricText.isBlank()) return
        val shouldForceRecreate = forScreenOff && needsAodRebind
        if (forScreenOff) {
            // 歌词侧已在息屏后完成 rvAod 重绑：无需再撕会话
            if (!needsAodRebind && isFocusContentAlreadyBound()) {
                return
            }
            val settleLeft = remainingAodSwitchSettleMs()
            if (settleLeft > 0L) {
                if (!needsAodRebind && isFocusContentAlreadyBound()) return
                scheduleDisplayRepost(settleLeft, forScreenOff = true)
                return
            }
        } else if (isFocusContentAlreadyBound() && remainingAodSwitchSettleMs() > 0L) {
            // 亮屏/锁屏：内容已绑定且动画窗口内，跳过重复重发
            return
        }
        postFocusWithOptionalRecreate(
            songChanged = false,
            leavingPlaceholder = false,
            forceRecreate = shouldForceRecreate || !forScreenOff,
            forcePost = true
        )
        scheduleNextUpdate()
    }

    private fun repostFocusIfNeeded() {
        repostFocusForDisplayChange(forScreenOff = false)
    }

    /**
     * 统一出口：先准备会话缓存，真正的 AOD cancel+notify 节流在 [sendFocusNotification]。
     */
    private fun postFocusWithOptionalRecreate(
        songChanged: Boolean,
        leavingPlaceholder: Boolean,
        forceRecreate: Boolean,
        forcePost: Boolean
    ) {
        val needRecreate = songChanged || leavingPlaceholder || forceRecreate
        if (!needRecreate && !forcePost) return
        if (needRecreate) {
            prepareFocusSessionRecreate(
                songChanged = songChanged,
                leavingPlaceholder = leavingPlaceholder,
                force = forceRecreate
            )
        }
        postFocusUpdate(FocusRefreshMode.LINE_CHANGE, force = forcePost || needRecreate)
    }

    private fun unregisterReceiverSafe(receiver: BroadcastReceiver?) {
        if (receiver == null) return
        try {
            systemUIContext?.unregisterReceiver(receiver)
        } catch (_: Throwable) {
        }
    }

    private fun registerReceiverSafe(receiver: BroadcastReceiver, filter: IntentFilter) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                systemUIContext?.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                systemUIContext?.registerReceiver(receiver, filter)
            }
        } catch (e: Throwable) {
            logE("Failed to register receiver", e)
        }
    }

    private fun refreshSettings() {
        val context = systemUIContext ?: return
        focusEnabled = FocusPreferences.readFocusEnabled(context)
        showInShade = FocusPreferences.readShowInShade(context)
        pinAboveMedia = FocusPreferences.readPinAboveMedia(context)
        syncFocusPinState()
        showOnIsland = FocusPreferences.readShowOnIsland(context)
        aodKeepaliveSec = FocusPreferences.readAodKeepaliveSec(context)
        syncAdvanceMs = FocusPreferences.readSyncAdvanceMs(context)
        FocusStyleSnapshot.reloadFromDisk()
    }

    private fun applyIncomingStyleExtras(intent: Intent): Boolean {
        val prevMonet = FocusStyleSnapshot.monetDynamicColorEnabled
        val prevTextExtraction = FocusStyleSnapshot.textColorExtractionEnabled
        val prevEnabled = FocusStyleSnapshot.colorExtractionEnabled
        val prevColor = FocusStyleSnapshot.extractedTextColor
        val prevBgColor = FocusStyleSnapshot.extractedBgColor
        val prevAccent = FocusStyleSnapshot.extractedAccentColor
        val prevColorSet = prevColor != null
        FocusStyleSnapshot.applyFromLyricIntent(intent)
        val newColorSet = FocusStyleSnapshot.extractedTextColor != null
        return prevMonet != FocusStyleSnapshot.monetDynamicColorEnabled ||
            prevTextExtraction != FocusStyleSnapshot.textColorExtractionEnabled ||
            prevEnabled != FocusStyleSnapshot.colorExtractionEnabled ||
            prevColor != FocusStyleSnapshot.extractedTextColor ||
            prevBgColor != FocusStyleSnapshot.extractedBgColor ||
            prevAccent != FocusStyleSnapshot.extractedAccentColor ||
            prevColorSet != newColorSet
    }

    private fun handleSettingsChanged(intent: Intent) {
        val styleChanged = intent.getBooleanExtra(FocusStyleSnapshot.EXTRA_STYLE_CHANGED, false)
        FocusStyleSnapshot.applyFromIntent(intent)
        if (styleChanged) {
            // 仅清空已通知缓存；真正的 cancel+notify 交给随后的 repost
            invalidateLayoutCache()
            markLayoutReflowAllowed(forceRecreate = true)
            prepareFocusSessionRecreate(
                songChanged = false,
                leavingPlaceholder = false,
                force = true
            )
        }
        if (intent.hasExtra(FocusPreferences.EXTRA_FOCUS_ENABLED)) {
            focusEnabled = intent.getBooleanExtra(FocusPreferences.EXTRA_FOCUS_ENABLED, true)
        } else if (!styleChanged) {
            refreshSettings()
        }
        if (intent.hasExtra(FocusPreferences.EXTRA_SHOW_IN_SHADE)) {
            showInShade = intent.getBooleanExtra(FocusPreferences.EXTRA_SHOW_IN_SHADE, false)
        }
        pinAboveMedia = true
        syncFocusPinState()
        if (intent.hasExtra(FocusPreferences.EXTRA_SHOW_ON_ISLAND)) {
            val newShowOnIsland = intent.getBooleanExtra(FocusPreferences.EXTRA_SHOW_ON_ISLAND, false)
            if (newShowOnIsland != showOnIsland) {
                notificationManager?.let { HyperFocusLyricStyle.cancelFocusNotification(it) }
                clearNotifiedLyricContent()
                HyperFocusLyricStyle.resetPostedCache()
            }
            showOnIsland = newShowOnIsland
        } else {
            systemUIContext?.let { showOnIsland = FocusPreferences.readShowOnIsland(it) }
        }
        if (intent.hasExtra(FocusPreferences.EXTRA_AOD_KEEPALIVE_SEC)) {
            aodKeepaliveSec = intent.getIntExtra(
                FocusPreferences.EXTRA_AOD_KEEPALIVE_SEC,
                aodKeepaliveSec
            ).coerceIn(
                FocusPreferences.MIN_AOD_KEEPALIVE_SEC,
                FocusPreferences.MAX_AOD_KEEPALIVE_SEC
            )
        } else {
            systemUIContext?.let { aodKeepaliveSec = FocusPreferences.readAodKeepaliveSec(it) }
        }
        if (intent.hasExtra(FocusPreferences.EXTRA_SYNC_ADVANCE_MS)) {
            syncAdvanceMs = intent.getLongExtra(
                FocusPreferences.EXTRA_SYNC_ADVANCE_MS,
                syncAdvanceMs
            ).coerceIn(FocusPreferences.MIN_SYNC_ADVANCE_MS, FocusPreferences.MAX_SYNC_ADVANCE_MS)
        } else {
            systemUIContext?.let { syncAdvanceMs = FocusPreferences.readSyncAdvanceMs(it) }
        }
        if (!focusEnabled) {
            cancelFocusNotification()
            return
        }
        HyperFocusLyricStyle.resetPostedCache()
        repostFocusIfNeeded()
    }

    private fun handleLyricData(intent: Intent) {
        if (!focusEnabled) {
            cancelFocusNotification()
            return
        }
        try {
            val styleChanged = applyIncomingStyleExtras(intent)
            val lyricJson = intent.getStringExtra(EXTRA_LYRIC_JSON)
            val newTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
            val newArtist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
            val lyricText = intent.getStringExtra(EXTRA_LYRIC_TEXT) ?: ""
            val secondLine = intent.getStringExtra(EXTRA_SECOND_LINE) ?: ""
            val lineTranslation = intent.getStringExtra(EXTRA_LINE_TRANSLATION)?.takeIf { it.isNotBlank() }
            val prevLyric = currentLyricText
            val prevTitle = currentTitle
            val prevArtist = currentArtist
            val songChanged = newTitle != prevTitle || newArtist != prevArtist
            val lyricChanged = lyricText != prevLyric
            val forceResync = intent.getBooleanExtra(EXTRA_FORCE_RESYNC, false)
            val leavingPlaceholder = isPlaceholderLyric(prevLyric) &&
                lyricText.isNotBlank() && !isPlaceholderLyric(lyricText)
            val isPlaceholder = isPlaceholderLyric(lyricText)

            currentTitle = newTitle
            currentArtist = newArtist
            currentPosition = intent.getLongExtra(EXTRA_POSITION, 0L)
            lastUpdateTime = System.currentTimeMillis()
            if (intent.hasExtra(EXTRA_IS_PLAYING)) {
                val incomingPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                isPlaying = if (incomingPlaying) {
                    true
                } else if (isPlaceholderLyric(lyricText) && (forceResync || isPlaying)) {
                    // 占位歌词推送时避免 isPlaying 竞态导致锁屏不显示
                    isPlaying
                } else {
                    incomingPlaying
                }
            }
            lyricOffset = intent.getLongExtra(EXTRA_OFFSET, 0L)
            syncAdvanceMs = intent.getLongExtra(EXTRA_SYNC_ADVANCE, syncAdvanceMs)
            musicPackage = intent.getStringExtra(EXTRA_MUSIC_PACKAGE) ?: musicPackage
            if (!isSourcePackageAllowed(musicPackage)) {
                cancelFocusNotification()
                cancelAlarmOnly()
                preferAppLyric = false
                return
            }
            lyricLines = parseLyricJson(lyricJson)
            val hasAppLyricSource = lyricLines.isNotEmpty() || isPlaceholder

            if (hasAppLyricSource || lyricText.isNotBlank()) {
                preferAppLyric = true
                if (lyricText.isNotBlank()) {
                    currentLyricText = lyricText
                    currentSecondLine = secondLine
                    currentLineTranslation = lineTranslation
                    val needsPost = forceResync || songChanged || leavingPlaceholder ||
                        lastNotifiedLyric.isBlank() || styleChanged
                    if (needsPost) {
                        postFocusWithOptionalRecreate(
                            songChanged = songChanged,
                            leavingPlaceholder = leavingPlaceholder,
                            forceRecreate = forceResync || styleChanged,
                            forcePost = forceResync || lastNotifiedLyric.isBlank() ||
                                songChanged || leavingPlaceholder || styleChanged
                        )
                    }
                }
                scheduleNextUpdate()
            } else {
                preferAppLyric = false
                cancelAlarmOnly()
            }
            syncFocusPinState()
            scheduleLyricFocusReorder()
        } catch (e: Throwable) {
            logE("Failed to handle lyric data", e)
        }
    }

    private fun handleSimpleUpdate(intent: Intent) {
        if (!focusEnabled) {
            cancelFocusNotification()
            return
        }
        try {
            val styleChanged = applyIncomingStyleExtras(intent)
            val lyric = intent.getStringExtra(EXTRA_LYRIC_TEXT) ?: ""
            val secondLine = intent.getStringExtra(EXTRA_SECOND_LINE) ?: ""
            val lineTranslation = intent.getStringExtra(EXTRA_LINE_TRANSLATION)?.takeIf { it.isNotBlank() }
            isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
            val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
            val artist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
            if (intent.hasExtra(EXTRA_POSITION)) {
                currentPosition = intent.getLongExtra(EXTRA_POSITION, currentPosition)
                lastUpdateTime = System.currentTimeMillis()
            }
            if (intent.hasExtra(EXTRA_SYNC_ADVANCE)) {
                syncAdvanceMs = intent.getLongExtra(EXTRA_SYNC_ADVANCE, syncAdvanceMs)
            }
            if (intent.hasExtra(EXTRA_MUSIC_PACKAGE)) {
                musicPackage = intent.getStringExtra(EXTRA_MUSIC_PACKAGE) ?: musicPackage
            }
            if (!isSourcePackageAllowed(musicPackage)) {
                cancelFocusNotification()
                cancelAlarmOnly()
                preferAppLyric = false
                return
            }
            val songChanged = title != currentTitle || artist != currentArtist
            val forceResync = intent.getBooleanExtra(EXTRA_FORCE_RESYNC, false)
            val prevLyric = currentLyricText
            val leavingPlaceholder = isPlaceholderLyric(prevLyric) &&
                lyric.isNotBlank() && !isPlaceholderLyric(lyric)
            currentTitle = title
            currentArtist = artist
            preferAppLyric = true

            if (lyric.isNotBlank()) {
                currentLyricText = lyric
                currentSecondLine = secondLine
                currentLineTranslation = lineTranslation
            }

            val contentChanged = lyric.isNotBlank() && isPlaying && (
                songChanged || isLyricDisplayContentChanged()
            )
            val needsPost = contentChanged || forceResync || leavingPlaceholder ||
                lastNotifiedLyric.isBlank() || styleChanged

            if (needsPost && lyric.isNotBlank() && isPlaying) {
                postFocusWithOptionalRecreate(
                    songChanged = songChanged,
                    leavingPlaceholder = leavingPlaceholder,
                    forceRecreate = forceResync || songChanged || leavingPlaceholder || styleChanged,
                    forcePost = forceResync || lastNotifiedLyric.isBlank() ||
                        songChanged || leavingPlaceholder || contentChanged || styleChanged
                )
            } else if (!isPlaying) {
                cancelFocusNotification()
            }
            scheduleNextUpdate()
            syncFocusPinState()
            scheduleLyricFocusReorder()
        } catch (e: Throwable) {
            logE("Failed to handle simple update", e)
        }
    }

    private fun handlePlaybackState(intent: Intent) {
        isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
        syncFocusPinState()
        if (isPlaying) {
            lastUpdateTime = System.currentTimeMillis()
            if (currentLyricText.isNotBlank() && lastNotifiedLyric.isBlank()) {
                postFocusUpdate(FocusRefreshMode.LINE_CHANGE, force = true)
            }
            scheduleNextUpdate()
        } else {
            cancelFocusNotification()
        }
    }

    private fun updateLyricProgress() {
        try {
            if (!isPlaying) {
                cancelFocusNotification()
                return
            }

            // 换行仅跟 App UPDATE_LYRIC；此处只做 AOD 保活
            if (preferAppLyric) {
                maybeAodKeepalive()
                scheduleNextUpdate()
                return
            }

            maybeAodKeepalive()
            scheduleNextUpdate()
        } catch (e: Throwable) {
            logE("Failed to update lyric progress", e)
        }
    }

    private fun cancelAlarmOnly() {
        try {
            val intent = alarmIntent ?: return
            alarmManager?.cancel(intent)
        } catch (_: Throwable) {
        }
    }

    private fun maybeAodKeepalive() {
        if (!isAodActive() || currentLyricText.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastFocusNotifyTime >= effectiveKeepaliveMs()) {
            postFocusUpdate(FocusRefreshMode.KEEPALIVE)
        }
    }

    private fun scheduleNextUpdate() {
        try {
            if (!isPlaying) return
            if (preferAppLyric) {
                scheduleAodKeepaliveOnly()
                return
            }
            if (isAodActive() && currentLyricText.isNotBlank()) {
                scheduleAodKeepaliveOnly()
            }
        } catch (e: Throwable) {
            handler.postDelayed({ updateLyricProgress() }, effectiveKeepaliveMs())
        }
    }

    /** App 驱动换行时，息屏仅按保活间隔唤醒 */
    private fun scheduleAodKeepaliveOnly() {
        if (!isAodActive() || currentLyricText.isBlank()) return
        val keepaliveMs = effectiveKeepaliveMs()
        val keepaliveLeft = if (lastFocusNotifyTime > 0) {
            (keepaliveMs - (System.currentTimeMillis() - lastFocusNotifyTime))
                .coerceAtLeast(MIN_TICK_MS)
        } else {
            keepaliveMs
        }
        scheduleAlarmAfter(keepaliveLeft)
    }

    private fun scheduleAlarmAfter(delayMs: Long) {
        try {
            val intent = alarmIntent ?: return
            val triggerTime = SystemClock.elapsedRealtime() + delayMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    intent
                )
            } else {
                alarmManager?.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, intent)
            }
        } catch (e: Throwable) {
            handler.postDelayed({ updateLyricProgress() }, effectiveKeepaliveMs())
        }
    }

    private fun getAdjustedPosition(position: Long): Long = position + lyricOffset + syncAdvanceMs

    private fun getCurrentLineIndex(position: Long): Int {
        if (lyricLines.isEmpty()) return -1
        val adjusted = getAdjustedPosition(position)
        var result = -1
        for (i in lyricLines.indices) {
            if (lyricLines[i].time <= adjusted) result = i else break
        }
        return result
    }

    private fun hasRealTimedLyrics(): Boolean {
        if (lyricLines.size < 2) return false
        if (isPlaceholderLyric(currentLyricText)) return false
        return true
    }

    private fun buildMultiLineWindow(): HyperFocusLyricStyle.MultiLineWindow? {
        if (!FocusStyleSnapshot.multiLineLyrics) return null
        if (!hasRealTimedLyrics()) return null
        // 未到首句也展示第一页，进入多行模式即见完整歌词页
        val currentIndex = getCurrentLineIndex(currentPosition).coerceAtLeast(0)
        val visibleCount = FocusPreferences.coerceMultiLineLineCount(
            FocusStyleSnapshot.multiLineLineCount
        )
        val maxSlots = HyperFocusLyricStyle.MULTI_LINE_MAX_SLOTS

        if (FocusStyleSnapshot.multiLineShowTranslation) {
            // 有翻译：(原文+翻译) 交错填满所选行数，例如 8 行 = 4 对
            val pairCount = visibleCount / 2
            val pageStart = (currentIndex / pairCount) * pairCount
            val originals = ArrayList<String>(pairCount)
            val translations = ArrayList<String>(pairCount)
            var hasAnyTranslation = false
            for (i in 0 until pairCount) {
                val line = lyricLines.getOrNull(pageStart + i)
                val text = line?.text?.trim().orEmpty()
                val secondary = line?.translation
                    ?.replace('\n', ' ')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    .orEmpty()
                originals += text
                translations += secondary
                if (secondary.isNotBlank()) hasAnyTranslation = true
            }
            // 当前行可能只有单独下发的翻译（无逐行 translation 字段）
            if (!hasAnyTranslation) {
                val offsetInPage = currentIndex - pageStart
                val fallback = currentLineTranslation
                    ?.replace('\n', ' ')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                if (fallback != null && offsetInPage in 0 until pairCount) {
                    translations[offsetInPage] = fallback
                    hasAnyTranslation = true
                }
            }
            if (hasAnyTranslation) {
                val interleaved = ArrayList<String>(maxSlots)
                for (i in 0 until pairCount) {
                    interleaved += originals[i]
                    interleaved += translations[i]
                }
                while (interleaved.size < maxSlots) {
                    interleaved += ""
                }
                val currentSlot = if (currentIndex >= pageStart && currentIndex < pageStart + pairCount) {
                    (currentIndex - pageStart) * 2
                } else -1
                return HyperFocusLyricStyle.MultiLineWindow(
                    lines = interleaved,
                    interleavedTranslations = true,
                    visibleCount = visibleCount,
                    currentLineSlot = currentSlot
                )
            }
        }

        val pageStart = (currentIndex / visibleCount) * visibleCount
        val lines = ArrayList<String>(maxSlots)
        for (i in 0 until visibleCount) {
            val text = lyricLines.getOrNull(pageStart + i)?.text?.trim().orEmpty()
            lines += text
        }
        while (lines.size < maxSlots) {
            lines += ""
        }
        val currentSlot = if (currentIndex >= pageStart && currentIndex < pageStart + visibleCount) {
            currentIndex - pageStart
        } else -1
        return HyperFocusLyricStyle.MultiLineWindow(
            lines = lines,
            visibleCount = visibleCount,
            currentLineSlot = currentSlot
        )
    }

    private fun multiLineContentKey(): String {
        return buildMultiLineWindow()?.contentKey().orEmpty()
    }

    /** 多行模式按页判定内容变化；否则按单句+次行判定 */
    private fun isLyricDisplayContentChanged(): Boolean {
        val multiKey = multiLineContentKey()
        if (multiKey.isNotEmpty()) {
            return multiKey != lastNotifiedMultiLineKey
        }
        return currentLyricText != lastNotifiedLyric ||
            currentSecondLine != lastNotifiedSecond
    }

    private fun isLyricDisplayContentSame(): Boolean {
        val multiKey = multiLineContentKey()
        if (multiKey.isNotEmpty()) {
            return lastNotifiedMultiLineKey.isNotEmpty() && multiKey == lastNotifiedMultiLineKey
        }
        return currentLyricText == lastNotifiedLyric &&
            currentSecondLine == lastNotifiedSecond
    }

    private fun rememberNotifiedLyricContent() {
        lastNotifiedLyric = currentLyricText
        lastNotifiedSecond = currentSecondLine
        lastNotifiedTitle = currentTitle
        lastNotifiedArtist = currentArtist
        lastNotifiedMultiLineKey = multiLineContentKey()
    }

    private fun clearNotifiedLyricContent() {
        lastNotifiedLyric = ""
        lastNotifiedSecond = ""
        lastNotifiedTitle = ""
        lastNotifiedArtist = ""
        lastNotifiedMultiLineKey = ""
    }

    private fun resolveSecondLine(current: LyricLineData?, next: LyricLineData?): String {
        val secondary = current?.secondaryText()
        if (!secondary.isNullOrBlank()) return secondary
        return next?.text ?: ""
    }

    private fun getCurrentLine(position: Long): LyricLineData? {
        if (lyricLines.isEmpty()) return null
        val adjusted = getAdjustedPosition(position)
        var result: LyricLineData? = null
        for (line in lyricLines) {
            if (line.time <= adjusted) result = line else break
        }
        return result
    }

    private fun getNextLine(position: Long): LyricLineData? {
        if (lyricLines.isEmpty()) return null
        val adjusted = getAdjustedPosition(position)
        for (line in lyricLines) {
            if (line.time > adjusted) return line
        }
        return null
    }

    private fun getNextLineSwitchDelay(): Long {
        val nextLine = getNextLine(currentPosition) ?: return 5000L
        val switchAt = nextLine.time - lyricOffset - syncAdvanceMs
        return (switchAt - currentPosition).coerceIn(16L, 30_000L)
    }

    private fun isScreenInteractive(): Boolean {
        val pm = systemUIContext?.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive != false
    }

    /** 息屏 AOD：仅首次绑定（needsAodRebind）时 cancel+notify；后续换行靠 notify+enableAlert=true 驱动 AOD 刷新。 */
    private fun isAodActive(): Boolean = !isScreenInteractive()

    private fun isSourcePackageAllowed(packageName: String): Boolean {
        val context = systemUIContext ?: return true
        return FocusPreferences.readIsPackageAllowed(context, packageName)
    }

    /**
     * 仅重置「已通知」缓存，让后续 post 走完整刷新。
     * 不在这里 cancel：与 [HyperFocusLyricStyle.postFocusNotification] 内 cancel 叠在一起
     * 会让 AOD DiffDispatch 切换动画被撕两次，表现为抽搐/播不完整。
     */
    private fun prepareFocusSessionRecreate(
        songChanged: Boolean,
        leavingPlaceholder: Boolean,
        force: Boolean
    ) {
        if (!songChanged && !leavingPlaceholder && !force) return
        clearNotifiedLyricContent()
        HyperFocusLyricStyle.resetPostedCache()
    }

    private fun postFocusUpdate(mode: FocusRefreshMode, force: Boolean = false) {
        if (currentLyricText.isBlank()) return
        val now = System.currentTimeMillis()
        when (mode) {
            FocusRefreshMode.LINE_CHANGE -> {
                val metaChanged = currentTitle != lastNotifiedTitle ||
                    currentArtist != lastNotifiedArtist
                val contentChanged = isLyricDisplayContentChanged()
                if (!force && !contentChanged && !metaChanged) return
                sendFocusNotification(
                    HyperFocusLyricStyle.RefreshKind.LINE_CHANGE,
                    forceRefresh = force
                )
            }
            FocusRefreshMode.KEEPALIVE -> {
                if (!isLyricDisplayContentSame()) {
                    return
                }
                if (now - lastFocusNotifyTime < effectiveKeepaliveMs()) return
                sendFocusNotification(HyperFocusLyricStyle.RefreshKind.KEEPALIVE)
            }
        }
    }

    /**
     * AOD 上任何会 cancel 的推送都必须避开 DiffDispatch 播放窗口；
     * 窗口内多次换行/重绑合并为一次，带上最新歌词。
     * 合并任务可代替排队中的息屏重绑，故一并取消 pending display repost。
     */
    private fun scheduleCoalescedAodFocusSend(
        refreshKind: HyperFocusLyricStyle.RefreshKind,
        forceRefresh: Boolean,
        delayMs: Long
    ) {
        cancelPendingCoalescedFocusPost()
        cancelPendingDisplayRepost()
        val task = Runnable {
            pendingCoalescedFocusPost = null
            if (!focusEnabled || !isPlaying || currentLyricText.isBlank()) return@Runnable
            // 合并后的一次发送同时完成 AOD 重绑
            sendFocusNotification(
                refreshKind,
                forceRefresh = forceRefresh || needsAodRebind
            )
            scheduleNextUpdate()
        }
        pendingCoalescedFocusPost = task
        handler.postDelayed(task, delayMs.coerceAtLeast(0L))
    }

    @SuppressLint("NotificationPermission")
    private fun sendFocusNotification(
        refreshKind: HyperFocusLyricStyle.RefreshKind,
        forceRefresh: Boolean = false
    ) {
        try {
            if (!focusEnabled) {
                cancelFocusNotification()
                return
            }
            val context = systemUIContext ?: return
            val nm = notificationManager ?: return
            if (!isPlaying || currentLyricText.isBlank()) {
                cancelFocusNotification()
                return
            }
            val aodActive = isAodActive()
            // 仅屏幕状态切换进入 AOD 时 cancel+notify 绑定 rvAod；
            // 其余全部仅 notify（锁屏 / AOD 换行 / 保活），靠 updatable=true 驱动 SystemUI 重读 RemoteViews。
            val recreateForAod = aodActive && needsAodRebind
            val effectiveForceRefresh = forceRefresh ||
                (aodActive && needsAodRebind &&
                    refreshKind == HyperFocusLyricStyle.RefreshKind.KEEPALIVE)
            val willRecreate = recreateForAod
            if (aodActive && willRecreate) {
                val settleLeft = remainingAodSwitchSettleMs()
                if (settleLeft > 0L) {
                    // 认领当前内容，避免窗口内重复排队；真正 notify 仍走合并任务
                    rememberNotifiedLyricContent()
                    scheduleCoalescedAodFocusSend(
                        refreshKind = HyperFocusLyricStyle.RefreshKind.LINE_CHANGE,
                        forceRefresh = true,
                        delayMs = settleLeft
                    )
                    return
                }
            }

            // 即将实发：丢掉排队中的合并任务，防止 settle 结束后再发第二次
            cancelPendingCoalescedFocusPost()

            val multiLine = buildMultiLineWindow()
            HyperFocusLyricStyle.postFocusNotification(
                systemContext = context,
                notificationManager = nm,
                content = HyperFocusLyricStyle.FocusContent(
                    songTitle = currentTitle,
                    artist = currentArtist,
                    lyricText = currentLyricText,
                    // 多行模式只展示当前页，不附带下一句/翻译次行
                    secondLineText = if (multiLine != null) {
                        ""
                    } else {
                        currentSecondLine.ifBlank { currentArtist }
                    },
                    lineTranslation = if (multiLine != null) null else currentLineTranslation,
                    musicPackage = musicPackage,
                    multiLine = multiLine
                ),
                showInShade = showInShade,
                pinAboveMedia = pinAboveMedia,
                showOnIsland = showOnIsland,
                refreshKind = refreshKind,
                forceRefresh = effectiveForceRefresh,
                recreateForAod = recreateForAod
            )
            if (willRecreate) {
                lastFocusRecreateAt = System.currentTimeMillis()
            }
            if (aodActive && recreateForAod) {
                needsAodRebind = false
            }
            if (refreshKind == HyperFocusLyricStyle.RefreshKind.LINE_CHANGE) {
                markLayoutReflowAllowed(willRecreate)
            }
            lastFocusNotifyTime = System.currentTimeMillis()
            rememberNotifiedLyricContent()
            // 息屏切换动画窗口内不要反复挪 View，避免与 DiffDispatch 抢布局
            if (!aodActive || remainingAodSwitchSettleMs() <= 0L) {
                scheduleLyricFocusReorder()
            } else {
                handler.postDelayed(
                    { scheduleLyricFocusReorder() },
                    remainingAodSwitchSettleMs()
                )
            }
        } catch (e: Throwable) {
            logE("Failed to send focus notification", e)
        }
    }

    private fun cancelFocusNotification() {
        try {
            lastFocusNotifyTime = 0L
            lastFocusRecreateAt = 0L
            needsAodRebind = false
            cancelPendingDisplayRepost()
            cancelPendingCoalescedFocusPost()
            clearNotifiedLyricContent()
            invalidateLayoutCache()
            notificationManager?.let { HyperFocusLyricStyle.cancelFocusNotification(it) }
        } catch (_: Throwable) {
        }
    }

    private fun parseLyricJson(json: String?): List<LyricLineData> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val lines = mutableListOf<LyricLineData>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val translation = if (obj.has("translation")) obj.getString("translation") else null
                val reading = if (obj.has("reading")) obj.getString("reading") else null
                lines.add(LyricLineData(obj.getLong("time"), obj.getString("text"), translation, reading))
            }
            lines.sortBy { it.time }
            lines
        } catch (e: Throwable) {
            logE("Failed to parse lyric json", e)
            emptyList()
        }
    }

    /**
     * Hook AodFocusControllerV2$3.onAdd() 在调用前为我们的焦点通知强制设置 enableAlert=true。
     * 迫使 MIUI 走 NON-UPDATABLE 路径 (addAodView → DozeService → AOD 进程)，
     * 解决 updatable=true 时 AOD 只更新本地 ViewGroup 不通知 DozeService 导致息屏不刷新的问题。
     * 参考：AodFocusControllerV2$3.onAdd() 中决策逻辑。
     */
    private fun hookForceAodUpdate(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.notification.focus.AodFocusControllerV2\$3",
                classLoader,
                "onAdd",
                "com.android.systemui.statusbar.notification.collection.NotificationEntry",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val entry = param.args[0]
                            val sbn = XposedHelpers.getObjectField(entry, "mSbn")
                            val notification = XposedHelpers.callMethod(
                                sbn,
                                "getNotification"
                            ) as? Notification
                            if (notification?.channelId == HyperFocusLyricStyle.CHANNEL_ID) {
                                notification.extras.putBoolean(
                                    "miui.focus.enableAlert",
                                    true
                                )
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
            log("AodFocusControllerV2 onAdd hook installed for force AOD update")
        } catch (e: Throwable) {
            log("AodFocusControllerV2 hook skipped (non-critical): ${e.message}")
        }
    }

    override fun log(msg: String) {
        XposedBridge.log("$tag: $msg")
    }

    override fun logE(msg: String, throwable: Throwable?) {
        XposedBridge.log("$tag: $msg")
        throwable?.let { XposedBridge.log(it) }
    }
}
