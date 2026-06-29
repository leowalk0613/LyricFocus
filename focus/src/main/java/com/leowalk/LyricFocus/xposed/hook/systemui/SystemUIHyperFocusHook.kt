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
 * HyperOS 焦点通知歌词（miui.focus.param），锁屏/AOD/超级岛通过 updatable 焦点通知刷新。
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
        private var pinAboveMedia = false
        private var showOnIsland = false
        private var aodKeepaliveSec = FocusPreferences.DEFAULT_AOD_KEEPALIVE_SEC
        private var lastFocusNotifyTime = 0L
        private var lastNotifiedLyric = ""
        private var lastNotifiedSecond = ""
        private var lastNotifiedTitle = ""
        private var lastNotifiedArtist = ""

        private const val MIN_TICK_MS = 500L
        private const val LAYOUT_REFLOW_DEBOUNCE_MS = 5_000L

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
            val translation: String? = null
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        log("Starting SystemUI Hyper Focus hook")
        hookSystemUIContext(lpparam)
        hookFocusPermissionBypass(lpparam.classLoader)
        hookAntiFlicker(lpparam.classLoader)
        hookHideFromShadeIfNeeded(lpparam.classLoader)
        hookPinAboveMediaCompat(lpparam.classLoader)
        hookSuppressIslandIfNeeded(lpparam.classLoader)
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

    private fun hookAntiFlicker(classLoader: ClassLoader) {
        FocusAntiFlickerHook.install(classLoader, tag)
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
        hookMoveMediaBelowFocus(classLoader)
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

    private fun hookMoveMediaBelowFocus(classLoader: ClassLoader) {
        try {
            val stackClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.stack.NotificationStackScrollLayout",
                classLoader
            )
            XposedBridge.hookAllMethods(stackClass, "addView", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!pinAboveMedia || !isPlaying || currentLyricText.isBlank()) return
                    val child = param.args[0] as? View ?: return
                    val name = child.javaClass.name
                    if (!name.contains("Media", ignoreCase = true) &&
                        !name.contains("media", ignoreCase = true)
                    ) {
                        return
                    }
                    val stack = param.thisObject as? ViewGroup ?: return
                    stack.post {
                        if (child.parent != stack) return@post
                        val index = stack.indexOfChild(child)
                        if (index >= 0 && index < stack.childCount - 1) {
                            stack.removeView(child)
                            stack.addView(child, stack.childCount)
                        }
                    }
                }
            })
            log("Media below focus hook ready")
        } catch (e: Throwable) {
            log("Media reorder hook skipped: ${e.message}")
        }
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
        lastNotifiedLyric = ""
        lastNotifiedSecond = ""
        lastNotifiedTitle = ""
        lastNotifiedArtist = ""
        invalidateLayoutCache()
        HyperFocusLyricStyle.resetPostedCache()
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
                    "焦点歌词",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "HyperOS 焦点通知歌词（锁屏 / AOD，可选超级岛）"
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

    private fun registerScreenReceiver() {
        unregisterReceiverSafe(screenReceiver)
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> handler.postDelayed({ repostFocusIfNeeded() }, 400L)
                    Intent.ACTION_USER_PRESENT -> handler.postDelayed({
                        hideFocusRowsInUnlockedShade()
                        repostFocusIfNeeded()
                    }, 400L)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiverSafe(screenReceiver!!, filter)
    }

    private fun repostFocusIfNeeded() {
        if (!focusEnabled || !isPlaying || currentLyricText.isBlank()) return
        postFocusUpdate(FocusRefreshMode.LINE_CHANGE, force = true)
        scheduleNextUpdate()
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
        val prevColorSet = prevColor != null
        FocusStyleSnapshot.applyFromLyricIntent(intent)
        val newColorSet = FocusStyleSnapshot.extractedTextColor != null
        return prevMonet != FocusStyleSnapshot.monetDynamicColorEnabled ||
            prevTextExtraction != FocusStyleSnapshot.textColorExtractionEnabled ||
            prevEnabled != FocusStyleSnapshot.colorExtractionEnabled ||
            prevColor != FocusStyleSnapshot.extractedTextColor ||
            prevBgColor != FocusStyleSnapshot.extractedBgColor ||
            prevColorSet != newColorSet
    }

    private fun handleSettingsChanged(intent: Intent) {
        val styleChanged = intent.getBooleanExtra(FocusStyleSnapshot.EXTRA_STYLE_CHANGED, false)
        FocusStyleSnapshot.applyFromIntent(intent)
        if (styleChanged) {
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
        if (intent.hasExtra(FocusPreferences.EXTRA_PIN_ABOVE_MEDIA)) {
            pinAboveMedia = intent.getBooleanExtra(FocusPreferences.EXTRA_PIN_ABOVE_MEDIA, false)
            if (!pinAboveMedia) {
                invalidateLayoutCache()
            }
        } else {
            systemUIContext?.let { pinAboveMedia = FocusPreferences.readPinAboveMedia(it) }
        }
        if (intent.hasExtra(FocusPreferences.EXTRA_SHOW_ON_ISLAND)) {
            val newShowOnIsland = intent.getBooleanExtra(FocusPreferences.EXTRA_SHOW_ON_ISLAND, false)
            if (newShowOnIsland != showOnIsland) {
                notificationManager?.let { HyperFocusLyricStyle.cancelFocusNotification(it) }
                lastNotifiedLyric = ""
                lastNotifiedSecond = ""
                lastNotifiedTitle = ""
                lastNotifiedArtist = ""
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
                    val needsPost = forceResync || songChanged || leavingPlaceholder ||
                        lastNotifiedLyric.isBlank() || styleChanged
                    if (needsPost) {
                        prepareFocusSessionRecreate(
                            songChanged = songChanged,
                            leavingPlaceholder = leavingPlaceholder,
                            force = forceResync || styleChanged
                        )
                        postFocusUpdate(
                            FocusRefreshMode.LINE_CHANGE,
                            force = forceResync || lastNotifiedLyric.isBlank() ||
                                songChanged || leavingPlaceholder || styleChanged
                        )
                    }
                }
                scheduleNextUpdate()
            } else {
                preferAppLyric = false
                cancelAlarmOnly()
            }
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
            val prevSecond = currentSecondLine
            val leavingPlaceholder = isPlaceholderLyric(prevLyric) &&
                lyric.isNotBlank() && !isPlaceholderLyric(lyric)
            currentTitle = title
            currentArtist = artist
            preferAppLyric = true

            if (lyric.isNotBlank()) {
                currentLyricText = lyric
                currentSecondLine = secondLine
            }

            val contentChanged = lyric.isNotBlank() && isPlaying &&
                (lyric != prevLyric || secondLine != prevSecond || songChanged)
            val needsPost = contentChanged || forceResync || leavingPlaceholder ||
                lastNotifiedLyric.isBlank() || styleChanged

            if (needsPost && lyric.isNotBlank() && isPlaying) {
                prepareFocusSessionRecreate(
                    songChanged = songChanged,
                    leavingPlaceholder = leavingPlaceholder,
                    force = forceResync || songChanged || leavingPlaceholder || styleChanged
                )
                postFocusUpdate(
                    FocusRefreshMode.LINE_CHANGE,
                    force = forceResync || lastNotifiedLyric.isBlank() ||
                        songChanged || leavingPlaceholder || contentChanged || styleChanged
                )
            } else if (!isPlaying) {
                cancelFocusNotification()
            }
            scheduleNextUpdate()
        } catch (e: Throwable) {
            logE("Failed to handle simple update", e)
        }
    }

    private fun handlePlaybackState(intent: Intent) {
        isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
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

    private fun resolveSecondLine(current: LyricLineData?, next: LyricLineData?): String {
        val translation = current?.translation
        if (!translation.isNullOrBlank()) return translation
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

    /** 息屏 AOD（非亮屏锁屏）：rvAod 需 cancel+notify 才能刷新 */
    private fun isAodActive(): Boolean = !isScreenInteractive()

    private fun isSourcePackageAllowed(packageName: String): Boolean {
        val context = systemUIContext ?: return true
        return FocusPreferences.readIsPackageAllowed(context, packageName)
    }

    private fun prepareFocusSessionRecreate(
        songChanged: Boolean,
        leavingPlaceholder: Boolean,
        force: Boolean
    ) {
        if (!songChanged && !leavingPlaceholder && !force) return
        if (leavingPlaceholder || songChanged || force) {
            notificationManager?.let { HyperFocusLyricStyle.cancelFocusNotification(it) }
        }
        lastNotifiedLyric = ""
        lastNotifiedSecond = ""
        lastNotifiedTitle = ""
        lastNotifiedArtist = ""
        HyperFocusLyricStyle.resetPostedCache()
    }

    private fun postFocusUpdate(mode: FocusRefreshMode, force: Boolean = false) {
        if (currentLyricText.isBlank()) return
        val now = System.currentTimeMillis()
        when (mode) {
            FocusRefreshMode.LINE_CHANGE -> {
                val metaChanged = currentTitle != lastNotifiedTitle ||
                    currentArtist != lastNotifiedArtist
                val contentChanged = currentLyricText != lastNotifiedLyric ||
                    currentSecondLine != lastNotifiedSecond
                if (!force && !contentChanged && !metaChanged) return
                sendFocusNotification(
                    HyperFocusLyricStyle.RefreshKind.LINE_CHANGE,
                    forceRefresh = force
                )
                lastNotifiedLyric = currentLyricText
                lastNotifiedSecond = currentSecondLine
                lastNotifiedTitle = currentTitle
                lastNotifiedArtist = currentArtist
            }
            FocusRefreshMode.KEEPALIVE -> {
                if (currentLyricText != lastNotifiedLyric ||
                    currentSecondLine != lastNotifiedSecond
                ) {
                    return
                }
                if (now - lastFocusNotifyTime < effectiveKeepaliveMs()) return
                sendFocusNotification(HyperFocusLyricStyle.RefreshKind.KEEPALIVE)
            }
        }
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
            HyperFocusLyricStyle.postFocusNotification(
                systemContext = context,
                notificationManager = nm,
                content = HyperFocusLyricStyle.FocusContent(
                    songTitle = currentTitle,
                    artist = currentArtist,
                    lyricText = currentLyricText,
                    secondLineText = currentSecondLine.ifBlank { currentArtist },
                    musicPackage = musicPackage
                ),
                showInShade = showInShade,
                pinAboveMedia = pinAboveMedia,
                showOnIsland = showOnIsland,
                refreshKind = refreshKind,
                forceRefresh = forceRefresh,
                recreateForAod = refreshKind == HyperFocusLyricStyle.RefreshKind.LINE_CHANGE &&
                    isAodActive()
            )
            if (refreshKind == HyperFocusLyricStyle.RefreshKind.LINE_CHANGE) {
                markLayoutReflowAllowed(true)
            }
            lastFocusNotifyTime = System.currentTimeMillis()
        } catch (e: Throwable) {
            logE("Failed to send focus notification", e)
        }
    }

    private fun cancelFocusNotification() {
        try {
            lastFocusNotifyTime = 0L
            lastNotifiedLyric = ""
            lastNotifiedSecond = ""
            lastNotifiedTitle = ""
            lastNotifiedArtist = ""
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
                lines.add(LyricLineData(obj.getLong("time"), obj.getString("text"), translation))
            }
            lines.sortBy { it.time }
            lines
        } catch (e: Throwable) {
            logE("Failed to parse lyric json", e)
            emptyList()
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
