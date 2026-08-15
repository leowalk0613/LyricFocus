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
import com.leowalk.LyricFocus.xposed.ReflectUtil
import com.leowalk.LyricFocus.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import org.json.JSONArray
import java.lang.ref.WeakReference

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
        private const val EXTRA_AODCHANGE_MODE = "aodchange_mode"

        private const val SETTINGS_URI = "content://com.leowalk.LyricFocus.settings"

        /** 通过本应用 ContentProvider 查询开关状态（systemui 进程读不了 app 私有 prefs）。 */
        fun queryMode(ctx: Context?, method: String): Boolean? {
            if (ctx == null) return null
            return try {
                val r = ctx.contentResolver.call(
                    android.net.Uri.parse(SETTINGS_URI), method, null, null
                )
                if (r != null && r.containsKey("enabled")) r.getBoolean("enabled", false) else null
            } catch (e: Throwable) {
                null
            }
        }

        private var cachedFocusRow: WeakReference<View>? = null

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
        private var lyricLinesStale = false
        /** 缓存最近一次解析的歌词 JSON 哈希，相同 JSON 不重复解析 */
        private var lastLyricJsonHash: Int = 0
        /** 缓存 buildMultiLineWindow 结果，避免重复计算 */
        private var cachedMultiLineWindow: HyperFocusLyricStyle.MultiLineWindow? = null
        private var cachedMultiLinePosition: Long = -1L
        private var cachedMultiLineOffset: Long = 0L
        private var cachedMultiLineSyncAdvance: Long = 0L
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
        /** 用于 AOD↔锁屏过渡时检测多行状态切换 */
        private var lastMultiLineWasActive = false
        private var lastCancelAndRepostTime = 0L
        /** 切歌标记：true 时下一次 AOD LINE_CHANGE 走 cancel+notify 重建 */
        private var aodNeedsRecreate = false
        /** 用于边沿检测：上一次检测到 AOD 活跃，避免重复 cancel/repost */
        private var wasAodActive = false
        /** HyperOS4 上 PowerManager.isInteractive() 在 AOD 下返回 true，改由 MiuiDozeService 回调跟踪 AOD 状态 */
        @Volatile
        private var aodState = false
        /** AOD 状态 hook 是否安装成功；失败时回退到旧的 isInteractive 判断 */
        private var aodStateTrackingInstalled = false
        /** 歌曲身份 key：只有 key 改变才允许 AOD cancel+notify */
        private var lastSongKey = ""
        private const val MIN_TICK_MS = 500L
        private const val LAYOUT_REFLOW_DEBOUNCE_MS = 2_000L
        /** 亮屏/解锁后重发焦点通知，等待 Keyguard 与 SystemUI 就绪 */
        private const val SCREEN_REPOST_DELAY_MS = 100L
        /** 切歌/屏状态变化时 cancel+repost 的最小间隔 */
        private const val CANCEL_REPOST_DEBOUNCE_MS = 600L

        private enum class FocusRefreshMode {
            LINE_CHANGE,
            KEEPALIVE
        }

        private enum class AodRecreateReason {
            SCREEN_ENTER_AOD,
            SONG_CHANGED
        }

        private fun effectiveKeepaliveMs(): Long {
            return (aodKeepaliveSec.coerceAtMost(FocusPreferences.SYSTEM_FOCUS_MAX_KEEPALIVE_SEC) * 1000L)
        }

        private fun currentSongKey(): String {
            return "$musicPackage|$currentTitle|$currentArtist"
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

        /** aodchange 外部渲染开启：所有 hook 变为 no-op，不注册任何 receiver/闹钟 */
        @Volatile
        var aodchangeMode: Boolean = false
            private set

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

    override fun install(classLoader: ClassLoader, module: XposedModule) {
        log("Starting SystemUI Hyper Focus hook")
        FocusStyleSnapshot.attachModule(module)
        hookSystemUIContext(classLoader, module)
        hookFocusPermissionBypass(classLoader, module)
        hookHideFromShadeIfNeeded(classLoader, module)
        hookPinAboveMediaCompat(classLoader, module)
        hookSuppressIslandIfNeeded(classLoader, module)
        FocusAntiFlickerHook.install(classLoader, module, tag)
        hookKeyguardRepost(classLoader, module)
        hookForceAodUpdate(classLoader, module)
        hookAodStateTracking(classLoader, module)
    }

    private fun hookSuppressIslandIfNeeded(classLoader: ClassLoader, module: XposedModule) {
        FocusIslandSuppressHook.install(classLoader, module, tag) { systemUIContext }
    }

    private fun hookSystemUIContext(classLoader: ClassLoader, module: XposedModule) {
        try {
            // HyperOS4/Android17: SystemUIApplication 更名为 SystemUIApplicationImpl
            val method = ReflectUtil.findMethod(
                "com.android.systemui.application.impl.SystemUIApplicationImpl",
                classLoader,
                "onCreate"
            )
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as android.app.Application
                systemUIContext = app.applicationContext
                notificationManager = systemUIContext?.getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager?
                alarmManager = systemUIContext?.getSystemService(
                    Context.ALARM_SERVICE
                ) as AlarmManager?

                // aodchange 外部渲染：仅保留歌词数据推送（App 侧），SystemUI 侧全部功能停止。
                // onCreate 阶段应用 Context 可用，优先通过本应用 ContentProvider 查询开关。
                aodchangeMode = queryMode(systemUIContext, "aodchange_mode") == true
                val focusLyricEnabled = queryMode(systemUIContext, "focus_mode")
                log("aodchangeMode initial = $aodchangeMode, focusLyricEnabled = $focusLyricEnabled")
                if (aodchangeMode || focusLyricEnabled == false) {
                    resetSessionState()
                    clearNotifiedLyricContent()
                    HyperFocusLyricStyle.resetPostedCache()
                    // 保留 lyricReceiver（含 ACTION_SETTINGS_CHANGED），接收开关切换
                    registerLyricReceiver()
                    log("aodchange external rendering ON / focus lyric OFF, focus lyric output disabled")
                    return@intercept result
                }

                resetSessionState()
                createNotificationChannel()
                createAlarmIntent()
                registerLyricReceiver()
                registerAlarmReceiver()
                registerScreenReceiver()
                refreshSettings()
                scheduleResyncRequests()
                log("SystemUI context ready for focus lyrics")

                // LyricService 可能先于 SystemUI 启动（其同步广播丢失），延迟重读一次
                handler.postDelayed({
                    try {
                        val recheck = queryMode(systemUIContext, "aodchange_mode") == true
                        if (recheck && !aodchangeMode) {
                            aodchangeMode = true
                            stopAllFocusOutputs()
                            log("aodchangeMode recheck = true, focus lyric output disabled")
                        }
                    } catch (e: Throwable) {
                        logE("aodchangeMode recheck failed", e)
                    }
                }, 3000L)
                result
            }
        } catch (e: Throwable) {
            logE("Error hooking SystemUI context", e)
        }
    }

    private fun hookFocusPermissionBypass(classLoader: ClassLoader, module: XposedModule) {
        // HyperOS4/Android17: canShowFocus/canCustomFocus 更名为 canShowFocusState/canShowFocusStateApp，
        // 返回类型由 boolean 改为 int（-1 不支持 / 0 关闭 / 1 允许）
        bypassFocusStateMethod(classLoader, module, "com.miui.systemui.notification.NotificationSettingsManager", "canShowFocusState")
        bypassFocusStateMethod(classLoader, module, "com.miui.systemui.notification.NotificationSettingsManager", "canShowFocusStateApp")
        tryHookAuthBypass(classLoader, module)
        // HyperOS4: 焦点通知插件新增 SignatureChecker.checkSignatures 签名检查，
        // 即使 canShowFocus 通过也会触发 onAuthFailed，必须强制返回 true
        hookSignatureChecker(classLoader, module)
        hookPluginClassLoader(classLoader, module)
    }

    /** HyperOS4 焦点通知插件类加载器：NotificationSettingsManager/SignatureChecker 从插件加载 */
    private fun hookPluginClassLoader(classLoader: ClassLoader, module: XposedModule) {
        try {
            val factoryClass = classLoader.loadClass(
                "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory"
            )
            val methods = factoryClass.declaredMethods.filter { it.name == "createPluginContext" }
            if (methods.isEmpty()) {
                log("PluginFactory.createPluginContext not found")
                return
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val pluginClassLoader = (result as? Context)?.classLoader
                    if (pluginClassLoader != null) {
                        hookSignatureChecker(pluginClassLoader, module)
                        hookPluginFocusWhitelist(pluginClassLoader, module)
                    }
                    result
                }
            }
            log("Waiting for focus notification plugin class loader")
        } catch (e: Throwable) {
            log("Plugin class loader hook skipped: ${e.message}")
        }
    }

    /** 插件类加载器中的白名单方法（HyperOS4 部分类从插件加载） */
    private fun hookPluginFocusWhitelist(classLoader: ClassLoader, module: XposedModule) {
        try {
            val clazz = classLoader.loadClass(
                "miui.systemui.notification.NotificationSettingsManager"
            )
            clazz.declaredMethods
                .filter {
                    (it.name == "canShowFocus" || it.name == "canCustomFocus" ||
                        it.name == "canShowFocusState" || it.name == "canShowFocusStateApp") &&
                        it.returnType == Boolean::class.javaPrimitiveType
                }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        if (aodchangeMode) return@intercept chain.proceed()
                        true
                    }
                    log("Plugin bypassed ${method.name}")
                }
        } catch (e: Throwable) {
            log("Plugin focus whitelist skip: ${e.message}")
        }
    }

    /** HyperOS4 焦点通知插件签名检查：强制返回 true 绕过 onAuthFailed */
    private fun hookSignatureChecker(classLoader: ClassLoader, module: XposedModule) {
        try {
            val clazz = classLoader.loadClass(
                "miui.systemui.notification.focus.SignatureChecker"
            )
            clazz.declaredMethods
                .filter {
                    it.name == "checkSignatures" &&
                        it.returnType == Boolean::class.javaPrimitiveType
                }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        if (aodchangeMode) return@intercept chain.proceed()
                        true
                    }
                    log("Hooked SignatureChecker.${method.name}")
                }
        } catch (e: Throwable) {
            log("SignatureChecker hook skip: ${e.message}")
        }
    }

    private fun bypassFocusStateMethod(classLoader: ClassLoader, module: XposedModule, className: String, methodName: String) {
        try {
            val method = ReflectUtil.findMethod(
                className, classLoader, methodName,
                Context::class.java, String::class.java
            )
            module.hook(method).intercept { chain ->
                if (aodchangeMode) return@intercept chain.proceed()
                1
            }
            log("Bypassed $className.$methodName")
        } catch (e: Throwable) {
            log("Skip bypass $className.$methodName: ${e.message}")
        }
    }

    private fun tryHookAuthBypass(classLoader: ClassLoader, module: XposedModule) {
        try {
            val authClass = classLoader.loadClass(
                "miui.systemui.notification.auth.AuthManager\$AuthServiceCallback\$onAuthResult\$1"
            )
            val method = authClass.getDeclaredMethod("invokeSuspend", Object::class.java)
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                if (aodchangeMode) return@intercept chain.proceed()
                val bundle = ReflectUtil.getField(chain.thisObject, "\$authBundle") as? Bundle
                bundle?.putInt("result_code", 0)
                chain.proceed()
            }
            log("Auth bypass hooked")
        } catch (_: Throwable) {
        }
    }

    private fun hookHideFromShadeIfNeeded(classLoader: ClassLoader, module: XposedModule) {
        hookHideFocusRowInShadeStack(classLoader, module)
    }

    /** 仅在下拉通知栏隐藏焦点行，不阻断 bind pipeline（锁屏/AOD/岛仍正常绑定） */
    private fun hookHideFocusRowInShadeStack(classLoader: ClassLoader, module: XposedModule) {
        try {
            val stackClass = ReflectUtil.findClass(
                "com.android.systemui.statusbar.stack.NotificationStackScrollLayout",
                classLoader
            )
            val addViewMethods = ReflectUtil.findMethodsByName(stackClass, "addView")
            val addViewInLayoutMethods = ReflectUtil.findMethodsByName(stackClass, "addViewInLayout")
            val allMethods = addViewMethods + addViewInLayoutMethods
            for (method in allMethods) {
                module.hook(method).intercept { chain ->
                    if (aodchangeMode) return@intercept chain.proceed()
                    val result = chain.proceed()
                    applyFocusRowShadeVisibility(chain.args.getOrNull(0) as? View)
                    result
                }
            }
            log("Shade-only focus row hide hook ready")
        } catch (e: Throwable) {
            log("Shade hide hook skipped: ${e.message}")
        }
    }

    private fun applyFocusRowShadeVisibility(view: View?) {
        if (showInShade || view == null) return
        val row = findFocusNotificationRow(view) ?: return
        cachedFocusRow = WeakReference(row)
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
        val entry = ReflectUtil.callMethod(row, "getEntry") ?: return null
        val sbn = ReflectUtil.getField(entry, "mSbn") as? StatusBarNotification ?: return null
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
        cachedFocusRow?.get()?.let { applyFocusRowShadeVisibility(it) }
    }

    private fun isPlaceholderLyric(text: String): Boolean {
        return text.isBlank() ||
            text == "\u266A" ||
            text == "\u6682\u65e0\u6b4c\u8bcd" ||
            text == "\u52a0\u8f7d\u6b4c\u8bcd\u4e2d..."
    }

    private fun hookPinAboveMediaCompat(classLoader: ClassLoader, module: XposedModule) {
        hookSuppressFocusRowHeightReflow(classLoader, module)
        hookStabilizeKeyguardSinking(classLoader, module)
        hookDebouncePanelReflow(classLoader, module)
    }

    private fun hookSuppressFocusRowHeightReflow(classLoader: ClassLoader, module: XposedModule) {
        try {
            // HyperOS4/Android17: notifyHeightChanged(Boolean) 签名变更为 notifyHeightChanged(String, Boolean)
            val method = ReflectUtil.findMethod(
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
                classLoader,
                "notifyHeightChanged",
                String::class.java,
                Boolean::class.java
            )
            module.hook(method).intercept { chain ->
                if (aodchangeMode) return@intercept chain.proceed()
                if (!pinAboveMedia || allowLayoutReflow) return@intercept chain.proceed()
                if (chain.args.getOrNull(1) != true) return@intercept chain.proceed()
                val entry = ReflectUtil.callMethod(chain.thisObject, "getEntry") ?: return@intercept chain.proceed()
                val sbn = ReflectUtil.getField(entry, "mSbn") as? StatusBarNotification
                    ?: return@intercept chain.proceed()
                if (sbn.notification?.channelId == HyperFocusLyricStyle.CHANNEL_ID) {
                    return@intercept null
                }
                chain.proceed()
            }
        } catch (e: Throwable) {
            log("ExpandableNotificationRow height hook skipped: ${e.message}")
        }
    }

    private fun hookStabilizeKeyguardSinking(classLoader: ClassLoader, module: XposedModule) {
        try {
            val clockMethod = ReflectUtil.findMethod(
                "com.android.keyguard.clock.KeyguardClockContainer",
                classLoader,
                "getClockBottom"
            )
            module.hook(clockMethod).intercept { chain ->
                if (aodchangeMode) return@intercept chain.proceed()
                val result = chain.proceed()
                if (!pinAboveMedia || currentLyricText.isBlank()) return@intercept result
                val bottom = result as? Int ?: return@intercept result
                if (currentLyricText == cachedClockBottomLyric && cachedClockBottom != null) {
                    return@intercept cachedClockBottom
                }
                cachedClockBottom = bottom
                cachedClockBottomLyric = currentLyricText
                result
            }
        } catch (e: Throwable) {
            log("KeyguardClockContainer hook skipped: ${e.message}")
        }

        try {
            val contentMethod = ReflectUtil.findMethod(
                "com.android.systemui.statusbar.stack.NotificationStackScrollLayout",
                classLoader,
                "getIntrinsicContentHeight"
            )
            module.hook(contentMethod).intercept { chain ->
                if (aodchangeMode) return@intercept chain.proceed()
                val result = chain.proceed()
                if (!pinAboveMedia || currentLyricText.isBlank()) return@intercept result
                val height = result as? Int ?: return@intercept result
                if (currentLyricText == cachedStackContentLyric && cachedStackContentHeight != null) {
                    return@intercept cachedStackContentHeight
                }
                cachedStackContentHeight = height
                cachedStackContentLyric = currentLyricText
                result
            }
        } catch (e: Throwable) {
            log("NotificationStackScrollLayout height hook skipped: ${e.message}")
        }
    }

    private fun hookDebouncePanelReflow(classLoader: ClassLoader, module: XposedModule) {
        val targets = listOf(
            "com.android.systemui.shade.MiuiNotificationPanelViewController",
            "com.android.systemui.shade.NotificationPanelViewController"
        )
        for (target in targets) {
            try {
                val method = ReflectUtil.findMethod(
                    target,
                    classLoader,
                    "positionClockAndNotifications",
                    Boolean::class.java
                )
                module.hook(method).intercept { chain ->
                    if (aodchangeMode) return@intercept chain.proceed()
                    if (!pinAboveMedia) return@intercept chain.proceed()
                    val now = System.currentTimeMillis()
                    if (!allowLayoutReflow &&
                        now - lastLayoutReflowTime < LAYOUT_REFLOW_DEBOUNCE_MS
                    ) {
                        return@intercept null
                    }
                    chain.proceed()
                }
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
    }

    private fun invalidateLayoutCache() {
        cachedClockBottom = null
        cachedClockBottomLyric = ""
        cachedStackContentHeight = null
        cachedStackContentLyric = ""
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
        clearNotifiedLyricContent()
        invalidateLayoutCache()
        HyperFocusLyricStyle.resetPostedCache()
        syncFocusPinState()
    }

    /** aodchange 外部渲染：停止焦点通知输出、闹钟、receiver 注册。
     *  保留 lyricReceiver（含 ACTION_SETTINGS_CHANGED），以便接收开关切换广播。 */
    private fun stopAllFocusOutputs() {
        try {
            unregisterReceiverSafe(alarmReceiver)
            unregisterReceiverSafe(screenReceiver)
            alarmReceiver = null
            screenReceiver = null
            cancelAlarmOnly()
            notificationManager?.let { HyperFocusLyricStyle.cancelFocusNotification(it) }
            resetSessionState()
            invalidateLayoutCache()
        } catch (e: Throwable) {
            logE("Failed to stop focus outputs", e)
        }
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
    private fun hookKeyguardRepost(classLoader: ClassLoader, module: XposedModule) {
        // HyperOS4/Android17: KeyguardUpdateMonitor.notifyKeyguardStateChanged 已移除，
        // 改用 KeyguardStateControllerImpl.notifyKeyguardState(boolean showing, boolean occluded)
        try {
            val method = ReflectUtil.findMethod(
                "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl",
                classLoader,
                "notifyKeyguardState",
                Boolean::class.java,
                Boolean::class.java
            )
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (aodchangeMode) return@intercept result
                val showing = chain.args.getOrNull(0) as? Boolean ?: return@intercept result
                if (!showing) return@intercept result
                handler.postDelayed({ repostFocusIfNeeded() }, SCREEN_REPOST_DELAY_MS)
                result
            }
            log("Keyguard repost hook: KeyguardStateControllerImpl.notifyKeyguardState")
            return
        } catch (_: Throwable) {
        }

        // 旧版回退：KeyguardUpdateMonitor
        val methodNames = listOf(
            "notifyKeyguardStateChanged",
            "handleKeyguardChanged",
            "updateKeyguardState"
        )
        for (name in methodNames) {
            try {
                if (name == "notifyKeyguardStateChanged") {
                    val method = ReflectUtil.findMethod(
                        "com.android.keyguard.KeyguardUpdateMonitor",
                        classLoader,
                        name,
                        Boolean::class.java,
                        Boolean::class.java
                    )
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (aodchangeMode) return@intercept result
                        val showing = chain.args.getOrNull(0) as? Boolean ?: return@intercept result
                        if (!showing) return@intercept result
                        handler.postDelayed({ repostFocusIfNeeded() }, SCREEN_REPOST_DELAY_MS)
                        result
                    }
                } else {
                    val method = ReflectUtil.findMethod(
                        "com.android.keyguard.KeyguardUpdateMonitor",
                        classLoader,
                        name
                    )
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (aodchangeMode) return@intercept result
                        handler.postDelayed({ handlePossibleAodStateChange() }, SCREEN_REPOST_DELAY_MS)
                        result
                    }
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
                        // 进入 AOD：边沿检测，只在 false→true 时触发一次 cancel+repost
                        handler.postDelayed({ handlePossibleAodStateChange() }, 600L)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        handler.postDelayed({
                            if (!isKeyguardLocked()) return@postDelayed
                            handlePossibleAodStateChange()
                        }, SCREEN_REPOST_DELAY_MS)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        handler.postDelayed({
                            hideFocusRowsInUnlockedShade()
                            cancelFocusNotification()
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

    private fun repostFocusIfNeeded() {
        if (!focusEnabled || !isPlaying || currentLyricText.isBlank()) return
        if (currentLyricText == lastNotifiedLyric && currentSecondLine == lastNotifiedSecond) return
        postFocusUpdate(FocusRefreshMode.LINE_CHANGE, force = true)
        scheduleNextUpdate()
    }

    private fun postFocusWithOptionalRecreate(
        songChanged: Boolean,
        leavingPlaceholder: Boolean,
        forceRecreate: Boolean,
        forcePost: Boolean
    ) {
        val needPost = songChanged || leavingPlaceholder || forceRecreate || forcePost
        if (!needPost) return
        if (songChanged || leavingPlaceholder || forceRecreate) {
            // AOD 下内容未变时不清空去重状态，避免 forceRecreate 循环破坏去重
            val aodSameContent = isAodActive() && !songChanged && isLyricDisplayContentSame()
            if (!aodSameContent) {
                clearNotifiedLyricContent()
                HyperFocusLyricStyle.resetPostedCache()
            }
        }
        postFocusUpdate(FocusRefreshMode.LINE_CHANGE, force = forcePost || needPost)
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
        // SystemUI 进程（uid=1000）无法直接读取应用私有 prefs，
        // 样式通过广播（notifyStyleSettingsChanged）实时同步，这里不 reloadFromDisk，
        // 避免把广播设置的样式覆盖回默认值。
        val context = systemUIContext ?: return
        queryMode(context, "focus_mode")?.let { focusEnabled = it }
    }

    private fun applyIncomingStyleExtras(intent: Intent): Boolean {
        val prevMonet = FocusStyleSnapshot.monetDynamicColorEnabled
        val prevTextExtraction = FocusStyleSnapshot.textColorExtractionEnabled
        val prevColorMode = FocusStyleSnapshot.colorModeEnabled
        val prevEnabled = FocusStyleSnapshot.colorExtractionEnabled
        val prevColor = FocusStyleSnapshot.extractedTextColor
        val prevBgColor = FocusStyleSnapshot.extractedBgColor
        val prevAccent = FocusStyleSnapshot.extractedAccentColor
        val prevColorSet = prevColor != null
        FocusStyleSnapshot.applyFromLyricIntent(intent)
        val newColorSet = FocusStyleSnapshot.extractedTextColor != null
        return prevMonet != FocusStyleSnapshot.monetDynamicColorEnabled ||
            prevTextExtraction != FocusStyleSnapshot.textColorExtractionEnabled ||
            prevColorMode != FocusStyleSnapshot.colorModeEnabled ||
            prevEnabled != FocusStyleSnapshot.colorExtractionEnabled ||
            prevColor != FocusStyleSnapshot.extractedTextColor ||
            prevBgColor != FocusStyleSnapshot.extractedBgColor ||
            prevAccent != FocusStyleSnapshot.extractedAccentColor ||
            prevColorSet != newColorSet
    }

    private fun handleSettingsChanged(intent: Intent) {
        // aodchange 外部渲染：通过广播同步状态（不依赖跨进程 prefs 读取）。
        // 纯 aodchange 状态同步广播（App 每 30 秒重发）只同步 mode，
        // 不继续走下方设置重置逻辑，避免 SystemUI 进程读不到 app prefs 把设置/样式覆盖回默认。
        if (intent.hasExtra(EXTRA_AODCHANGE_MODE) &&
            !intent.hasExtra(FocusPreferences.EXTRA_FOCUS_ENABLED) &&
            !intent.getBooleanExtra(FocusStyleSnapshot.EXTRA_STYLE_CHANGED, false)
        ) {
            val newMode = intent.getBooleanExtra(EXTRA_AODCHANGE_MODE, false)
            if (newMode != aodchangeMode) {
                aodchangeMode = newMode
                if (aodchangeMode) {
                    stopAllFocusOutputs()
                } else {
                    // 关闭 aodchange 渲染：恢复焦点歌词输出
                    createNotificationChannel()
                    createAlarmIntent()
                    registerAlarmReceiver()
                    registerScreenReceiver()
                    refreshSettings()
                    scheduleResyncRequests()
                }
                log("aodchange mode changed via broadcast: $newMode")
            }
            return
        }
        val styleChanged = intent.getBooleanExtra(FocusStyleSnapshot.EXTRA_STYLE_CHANGED, false)
        FocusStyleSnapshot.applyFromIntent(intent)
        if (styleChanged) {
            invalidateLayoutCache()
            clearNotifiedLyricContent()
            HyperFocusLyricStyle.resetPostedCache()
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
        if (aodchangeMode) {
            return
        }
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
            val songChanged = currentSongKey() != "$musicPackage|$newTitle|$newArtist"
            if (songChanged) aodNeedsRecreate = true
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
                } else if (isPlaceholderLyric(lyricText) || songChanged || isPlaying) {
                    // 占位歌词、切歌或正在播放时不改变播放状态
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
            // 相同 JSON 不重复解析，减少 CPU 占用（多行模式下每句歌词都发完整 JSON）
            val jsonHash = lyricJson?.hashCode() ?: 0
            if (jsonHash != lastLyricJsonHash || lyricLinesStale) {
                lyricLines = parseLyricJson(lyricJson)
                lyricLinesStale = false
                lastLyricJsonHash = jsonHash
                // 歌词变化时清空多行缓存
                cachedMultiLineWindow = null
                cachedMultiLinePosition = -1L
            }
            val hasAppLyricSource = lyricLines.isNotEmpty() || isPlaceholder

            if (hasAppLyricSource || lyricText.isNotBlank()) {
                preferAppLyric = true
                if (lyricText.isNotBlank()) {
                    currentLyricText = lyricText
                    currentSecondLine = secondLine
                    currentLineTranslation = lineTranslation
                    val contentChanged = isLyricDisplayContentChanged()
                    val needsPost = forceResync || songChanged || leavingPlaceholder ||
                        lastNotifiedLyric.isBlank() || styleChanged || contentChanged
                    if (needsPost) {
                        if (songChanged) {
                            forceCancelAndRepostForAod(AodRecreateReason.SONG_CHANGED)
                        } else {
                            postFocusWithOptionalRecreate(
                                songChanged = false,
                                leavingPlaceholder = leavingPlaceholder,
                                forceRecreate = forceResync || styleChanged,
                                forcePost = forceResync || lastNotifiedLyric.isBlank() ||
                                    leavingPlaceholder || styleChanged || contentChanged
                            )
                        }
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
        if (aodchangeMode) {
            return
        }
        if (!focusEnabled) {
            cancelFocusNotification()
            return
        }
        try {
            val styleChanged = applyIncomingStyleExtras(intent)
            val lyric = intent.getStringExtra(EXTRA_LYRIC_TEXT) ?: ""
            val secondLine = intent.getStringExtra(EXTRA_SECOND_LINE) ?: ""
            val lineTranslation = intent.getStringExtra(EXTRA_LINE_TRANSLATION)?.takeIf { it.isNotBlank() }
            val incomingPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
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
            val songChanged = currentSongKey() != "$musicPackage|$title|$artist"
            if (songChanged) {
                lyricLinesStale = true
                lastLyricJsonHash = 0
                cachedMultiLineWindow = null
                cachedMultiLinePosition = -1L
            }
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
            isPlaying = incomingPlaying

            // 切歌时不取消通知，避免出现空白；占位歌词也不取消
            if (!incomingPlaying && !songChanged && !isPlaceholderLyric(currentLyricText)) {
                cancelFocusNotification()
                return
            }

            val contentChanged = lyric.isNotBlank() && isPlaying && (
                songChanged || isLyricDisplayContentChanged()
            )
            val needsPost = contentChanged || forceResync || leavingPlaceholder ||
                lastNotifiedLyric.isBlank() || styleChanged

            if (needsPost && lyric.isNotBlank() && isPlaying) {
                if (songChanged) {
                    forceCancelAndRepostForAod(AodRecreateReason.SONG_CHANGED)
                } else {
                    postFocusWithOptionalRecreate(
                        songChanged = false,
                        leavingPlaceholder = leavingPlaceholder,
                        forceRecreate = forceResync || leavingPlaceholder || styleChanged,
                        forcePost = forceResync || lastNotifiedLyric.isBlank() ||
                            leavingPlaceholder || contentChanged || styleChanged
                    )
                }
            }
            scheduleNextUpdate()
            syncFocusPinState()
            scheduleLyricFocusReorder()
        } catch (e: Throwable) {
            logE("Failed to handle simple update", e)
        }
    }

    private fun handlePlaybackState(intent: Intent) {
        if (aodchangeMode) {
            return
        }
        isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
        syncFocusPinState()
        if (isPlaying) {
            lastUpdateTime = System.currentTimeMillis()
            if (currentLyricText.isNotBlank()) {
                postFocusUpdate(FocusRefreshMode.LINE_CHANGE, force = true)
            }
            scheduleNextUpdate()
        } else {
            if (lastFocusNotifyTime > 0L) cancelFocusNotification()
        }
    }

    private fun updateLyricProgress() {
        try {
            if (!isPlaying || currentLyricText.isBlank()) {
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
        val elapsed = now - lastFocusNotifyTime
        if (elapsed >= effectiveKeepaliveMs()) {
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
        if (lyricLinesStale) return false
        if (lyricLines.size < 2) return false
        if (isPlaceholderLyric(currentLyricText)) return false
        return true
    }

    private fun buildMultiLineWindow(): HyperFocusLyricStyle.MultiLineWindow? {
        if (!FocusStyleSnapshot.multiLineLyrics) {
            log("buildMultiLine: multiLineLyrics=false")
            return null
        }
        if (!hasRealTimedLyrics()) {
            log("buildMultiLine: stale=$lyricLinesStale size=${lyricLines.size} placeholder=${isPlaceholderLyric(currentLyricText)}")
            return null
        }
        val currentIndex = getCurrentLineIndex(currentPosition).coerceAtLeast(0)
        val maxSlots = HyperFocusLyricStyle.MULTI_LINE_MAX_SLOTS
        val pageSlots = maxSlots

        // 缓存命中：位置、偏移、预读量都不变时直接返回缓存
        if (cachedMultiLineWindow != null &&
            cachedMultiLinePosition == currentPosition &&
            cachedMultiLineOffset == lyricOffset &&
            cachedMultiLineSyncAdvance == syncAdvanceMs
        ) {
            return cachedMultiLineWindow
        }

        val result: HyperFocusLyricStyle.MultiLineWindow? = if (FocusStyleSnapshot.multiLineShowTranslation) {
            val interleaved = ArrayList<String>(maxSlots)
            var hasAnyTranslation = false
            var fwdIdx = currentIndex
            while (interleaved.size < pageSlots && fwdIdx < lyricLines.size) {
                val line = lyricLines.getOrNull(fwdIdx)
                val orig = line?.text?.trim()?.takeIf { it.isNotBlank() } ?: ""
                if (orig.isNotEmpty()) {
                    interleaved += orig
                    if (interleaved.size >= pageSlots) break
                }
                val trans = line?.translation?.replace('\n', ' ')?.trim()?.takeIf { it.isNotBlank() } ?: ""
                if (trans.isNotEmpty()) {
                    interleaved += trans
                    hasAnyTranslation = true
                    if (interleaved.size >= pageSlots) break
                }
                fwdIdx++
            }
            if (!hasAnyTranslation) {
                val fallback = currentLineTranslation
                    ?.replace('\n', ' ')?.trim()?.takeIf { it.isNotBlank() }
                if (fallback != null) hasAnyTranslation = true
            }
            if (hasAnyTranslation) {
                while (interleaved.size < maxSlots) interleaved += ""
                HyperFocusLyricStyle.MultiLineWindow(
                    lines = interleaved,
                    interleavedTranslations = true,
                    visibleCount = pageSlots,
                    currentLineSlot = 0
                )
            } else {
                // 无翻译：回退为纯原文多行，避免显示异常
                val plain = ArrayList<String>(maxSlots)
                var plainIdx = currentIndex
                while (plain.size < pageSlots && plainIdx < lyricLines.size) {
                    val text = lyricLines.getOrNull(plainIdx)?.text?.trim()?.takeIf { it.isNotBlank() } ?: ""
                    if (text.isNotEmpty()) plain += text
                    plainIdx++
                }
                while (plain.size < maxSlots) plain += ""
                HyperFocusLyricStyle.MultiLineWindow(
                    lines = plain,
                    visibleCount = pageSlots,
                    currentLineSlot = 0
                )
            }
        } else {
            val lines = ArrayList<String>(maxSlots)
            var fwdIdx = currentIndex
            while (lines.size < pageSlots && fwdIdx < lyricLines.size) {
                val text = lyricLines.getOrNull(fwdIdx)?.text?.trim()?.takeIf { it.isNotBlank() } ?: ""
                if (text.isNotEmpty()) lines += text
                fwdIdx++
            }
            while (lines.size < maxSlots) lines += ""
            HyperFocusLyricStyle.MultiLineWindow(
                lines = lines,
                visibleCount = pageSlots,
                currentLineSlot = 0
            )
        }

        cachedMultiLineWindow = result
        cachedMultiLinePosition = currentPosition
        cachedMultiLineOffset = lyricOffset
        cachedMultiLineSyncAdvance = syncAdvanceMs
        return result
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
        cachedMultiLineWindow = null
        cachedMultiLinePosition = -1L
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
        return pm?.isInteractive ?: true
    }

    /** 息屏 AOD：仅首次绑定（needsAodRebind）时 cancel+notify；后续换行靠 notify+enableAlert=true 驱动 AOD 刷新。 */
    private fun isAodActive(): Boolean {
        // HyperOS4 上 PowerManager.isInteractive() 在 AOD 下返回 true，导致 !isInteractive() 判断失效；
        // 优先使用 MiuiDozeService 回调跟踪的权威状态，hook 未安装时回退旧逻辑。
        return if (aodStateTrackingInstalled) aodState else !isScreenInteractive()
    }

    /** 边沿触发：仅 AOD 状态从 false→true 时执行一次 cancel+repost，防止系统回调重复触发 */
    private fun handlePossibleAodStateChange() {
        val active = isAodActive()
        if (active && !wasAodActive) {
            forceCancelAndRepostForAod(AodRecreateReason.SCREEN_ENTER_AOD)
        }
        wasAodActive = active
    }

    private fun isSourcePackageAllowed(packageName: String): Boolean {
        val context = systemUIContext ?: return true
        return FocusPreferences.readIsPackageAllowed(context, packageName)
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
                // AOD 去重：同一首歌相同内容不重复 notify，避免 AOD 重复动画
                if (isAodActive() && !contentChanged && !metaChanged) {
                    return
                }
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
                // AOD 保活无需触发通知刷新，避免万象息屏 cancel/repost 动画
                if (isAodActive()) {
                    lastFocusNotifyTime = now
                    return
                }
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
            // AOD 下无歌词且无歌曲信息时不发通知
            if (isAodActive() && isPlaceholderLyric(currentLyricText) && currentTitle.isBlank()) return
            // 构建多行窗口
            val multiLine = buildMultiLineWindow()
            val aodActive = isAodActive()
            // 仅 AOD 显示多行：锁屏回退双行；但 HyperOS4 的 AOD 实际显示 rv（锁屏视图）而非 rvAod，
            // 因此处于 AOD 状态时锁屏视图仍需多行布局，否则 AOD 也会变成双行
            val lockUsesMultiLine = if (multiLine != null && FocusStyleSnapshot.aodMultiLineOnly) {
                aodActive
            } else {
                multiLine != null && !FocusStyleSnapshot.aodMultiLineOnly
            }
            val recreateForTransition = wasAodActive != aodActive
            val recreateForSongChange = aodNeedsRecreate && currentSongKey() != lastSongKey
            wasAodActive = aodActive
            log("sendFocus: aodActive=$aodActive aodMultiLineOnly=${FocusStyleSnapshot.aodMultiLineOnly} lockMultiLine=$lockUsesMultiLine multiLine=${multiLine != null}")
            if (recreateForSongChange) {
                lastSongKey = currentSongKey()
                aodNeedsRecreate = false
            }
            val recreateForAod = recreateForTransition || recreateForSongChange
            HyperFocusLyricStyle.postFocusNotification(
                systemContext = context,
                notificationManager = nm,
                content = HyperFocusLyricStyle.FocusContent(
                    songTitle = currentTitle,
                    artist = currentArtist,
                    lyricText = currentLyricText,
                    secondLineText = if (lockUsesMultiLine) "" else currentSecondLine.ifBlank { currentArtist },
                    lineTranslation = if (lockUsesMultiLine) null else currentLineTranslation,
                    musicPackage = musicPackage,
                    multiLine = multiLine,
                    aodActive = aodActive
                ),
                showInShade = showInShade,
                pinAboveMedia = pinAboveMedia,
                showOnIsland = showOnIsland,
                refreshKind = refreshKind,
                forceRefresh = forceRefresh,
                recreateForAod = recreateForAod
            )
            lastFocusNotifyTime = System.currentTimeMillis()
            rememberNotifiedLyricContent()
        } catch (e: Throwable) {
            logE("Failed to send focus notification", e)
        }
    }

    private fun cancelFocusNotification() {
        try {
            if (lastFocusNotifyTime == 0L && currentLyricText.isBlank()) {
                return
            }
            
            lastFocusNotifyTime = 0L
            clearNotifiedLyricContent()
            invalidateLayoutCache()
            cachedFocusRow = null
            notificationManager?.let { HyperFocusLyricStyle.cancelFocusNotification(it) }
        } catch (_: Throwable) {
        }
    }

    /** AOD 进入 / 切歌时刷新焦点通知。仅接受明确原因，不得从 KEEPALIVE 调用。 */
    private fun forceCancelAndRepostForAod(reason: AodRecreateReason) {
        if (!focusEnabled || !isPlaying || currentLyricText.isBlank()) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastCancelAndRepostTime < CANCEL_REPOST_DEBOUNCE_MS) {
            handler.postDelayed({
                lastCancelAndRepostTime = 0L
                forceCancelAndRepostForAod(reason)
            }, CANCEL_REPOST_DEBOUNCE_MS)
            return
        }
        lastCancelAndRepostTime = now
        val aodActive = isAodActive()
        // 切歌时：清理旧状态后 cancel+repost；AOD 进入时：仅 AOD 下 cancel 确保置顶
        when (reason) {
            AodRecreateReason.SONG_CHANGED -> {
                lastSongKey = currentSongKey()
                aodNeedsRecreate = true
                cancelFocusNotification()
                HyperFocusLyricStyle.resetPostedCache()
                clearNotifiedLyricContent()
            }
            AodRecreateReason.SCREEN_ENTER_AOD -> {
                if (aodActive) {
                    cancelFocusNotification()
                    HyperFocusLyricStyle.resetPostedCache()
                }
            }
        }
        clearNotifiedLyricContent()
        HyperFocusLyricStyle.resetPostedCache()
        postFocusUpdate(FocusRefreshMode.LINE_CHANGE, force = true)
        scheduleNextUpdate()
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

    private fun hookForceAodUpdate(classLoader: ClassLoader, module: XposedModule) {
        try {
            val entryClass = classLoader.loadClass("com.android.systemui.statusbar.notification.collection.NotificationEntry")
            val onAddMethod = ReflectUtil.findMethod("com.android.systemui.statusbar.notification.focus.AodFocusControllerV2\$3", classLoader, "onAdd", entryClass)
            module.hook(onAddMethod).intercept { chain ->
                if (!aodchangeMode) {
                    try {
                        val entry = chain.args[0]
                        val sbn = ReflectUtil.getField(entry, "mSbn")
                        val notification = ReflectUtil.callMethod(sbn!!, "getNotification") as? Notification
                        if (notification?.channelId == HyperFocusLyricStyle.CHANNEL_ID) {
                            notification.extras.putBoolean("miui.focus.enableAlert", true)
                        }
                    } catch (_: Throwable) {}
                }
                chain.proceed()
            }
            log("AodFocusControllerV2 hooks installed")
        } catch (e: Throwable) { log("AodFocusControllerV2 hook skipped: ${e.message}") }
    }

    /**
     * HyperOS4 上 PowerManager.isInteractive() 在 AOD 下返回 true，无法用 !isInteractive() 判断 AOD。
     * MiuiDozeService.onDreamingStarted/Stopped 是 AOD 启停的权威回调（SystemUI 进程），
     * 回调后读取 AodFocusControllerV2.mAodStart 字段值作为 AOD 状态。
     */
    private fun hookAodStateTracking(classLoader: ClassLoader, module: XposedModule) {
        try {
            val clazz = ReflectUtil.findClass("com.android.keyguard.doze.MiuiDozeService", classLoader)
            val started = clazz.getDeclaredMethod("onDreamingStarted")
            val stopped = clazz.getDeclaredMethod("onDreamingStopped")
            module.hook(started).intercept { chain ->
                chain.proceed()
                aodState = readAodStart(chain.thisObject) ?: true
                log("AOD state via onDreamingStarted: $aodState")
            }
            module.hook(stopped).intercept { chain ->
                chain.proceed()
                aodState = readAodStart(chain.thisObject) ?: false
                log("AOD state via onDreamingStopped: $aodState")
            }
            aodStateTrackingInstalled = true
            log("MiuiDozeService AOD state hooks installed")
        } catch (e: Throwable) {
            log("MiuiDozeService AOD state hook skipped: ${e.message}")
        }
    }

    /** 读取 AodFocusControllerV2.mAodStart 权威值；任一环节缺失返回 null */
    private fun readAodStart(dozeService: Any?): Boolean? {
        if (dozeService == null) return null
        return try {
            val injector = ReflectUtil.getField(dozeService, "mDozeServiceHostInjector") ?: return null
            val listener = ReflectUtil.getField(injector, "mDozeStatusChangedListener") ?: return null
            val controller = ReflectUtil.getField(listener, "this\$0") ?: return null
            ReflectUtil.getField(controller, "mAodStart") as? Boolean
        } catch (_: Throwable) {
            null
        }
    }

}