package com.leowalk.LyricFocus.notification



import android.app.Notification

import android.app.NotificationManager

import android.content.Context

import android.graphics.Bitmap

import android.graphics.Canvas

import android.graphics.Color

import android.graphics.Paint

import android.graphics.PorterDuff

import android.graphics.PorterDuffXfermode

import android.graphics.drawable.Icon

import android.os.Bundle

import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.StyleSpan
import android.util.TypedValue

import android.view.View

import android.widget.RemoteViews

import androidx.core.app.NotificationCompat

import com.leowalk.LyricFocus.R
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.FocusStyleSnapshot
import com.leowalk.LyricFocus.util.AlbumColorExtractor
import org.json.JSONObject



/**

 * 小米 HyperOS 焦点通知 + 超级岛（miui.focus.rv / miui.focus.param.custom）。

 * 对齐 HyperCeiler MusicBaseHook，不使用 Live Update / promoteNotifications。

 */

object HyperFocusLyricStyle {



    const val MODULE_PACKAGE = FocusPreferences.MODULE_PACKAGE

    /** 与 HyperCeiler 焦点歌词渠道一致，便于 SystemUI 识别为焦点通知 */

    const val CHANNEL_ID = "channel_id_focusNotifLyrics"

    private const val TIMEOUT_SEC = 999999

    private const val COLOR_LYRIC_PRIMARY = Color.WHITE

    private const val COLOR_LYRIC_SECONDARY = 0xFFE0E0E0.toInt()



    @Volatile
    private var lastPostedLyric = ""

    @Volatile
    private var lastPostedSecond = ""

    @Volatile
    private var lastPostedMultiLineKey = ""

    @Volatile
    private var focusUpdateSequence = 0L

    private const val FOCUS_ORDER_ID = "lyricfocus_pin_top"



    data class FocusContent(

        val songTitle: String,

        val artist: String,

        val lyricText: String,

        val secondLineText: String,

        /** 当前行真实翻译；无翻译时为 null，与 secondLineText（可能为下一句预览）区分 */
        val lineTranslation: String? = null,

        val musicPackage: String = "",

        /** 多行模式窗口；null 时走默认双行布局 */
        val multiLine: MultiLineWindow? = null,

        /** 当前是否处于 AOD 状态（HyperOS4 的 AOD 实际显示 rv 锁屏视图） */
        val aodActive: Boolean = false

    )

    data class MultiLineWindow(
        /** 固定 [MULTI_LINE_MAX_SLOTS] 槽，空串表示该位置无歌词 */
        val lines: List<String>,
        /**
         * true：交错排布 (原文, 翻译) 成组，填满固定高度区域。
         * false：全部为原文行。
         */
        val interleavedTranslations: Boolean = false,
        /** 实际展示行数：4~10 */
        val visibleCount: Int = MULTI_LINE_MAX_SLOTS,
        /** 当前正在播放的歌词行在 [lines] 中的槽位索引，-1 表示不标识 */
        val currentLineSlot: Int = -1
    ) {
        fun contentKey(): String {
            return lines.joinToString("\u0001") + "\u0001|" +
                (if (interleavedTranslations) "1" else "0") +
                "\u0001@" + visibleCount +
                "\u0001#" + currentLineSlot
        }
    }

    fun resolveLockScreenColors(
        monetEnabled: Boolean,
        textExtractionEnabled: Boolean,
        colorModeEnabled: Boolean,
        extractedTextColor: Int?,
        extractedBgColor: Int?,
        extractedAccentColor: Int?,
        textColor: String,
        background: String
    ): Triple<Int, Int, Int?> {
        val colorPrimary: Int
        val colorSecondary: Int
        var backgroundColor: Int?
        when {
            monetEnabled && !FocusStyleSnapshot.monetBgOnly && colorModeEnabled && extractedTextColor != null && extractedBgColor != null -> {
                colorPrimary = AlbumColorExtractor.ensureContrastColorful(extractedTextColor, extractedBgColor)
                val accent = extractedAccentColor ?: extractedTextColor
                colorSecondary = AlbumColorExtractor.ensureContrastColorful(
                    AlbumColorExtractor.blendSecondary(accent, extractedBgColor),
                    extractedBgColor, 2.5
                )
                backgroundColor = extractedBgColor
            }
            monetEnabled && !FocusStyleSnapshot.monetBgOnly && extractedTextColor != null && extractedBgColor != null -> {
                colorPrimary = extractedTextColor
                colorSecondary = AlbumColorExtractor.ensureContrast(
                    AlbumColorExtractor.blendSecondary(extractedTextColor, extractedBgColor),
                    extractedBgColor, 3.0
                )
                backgroundColor = extractedBgColor
            }
            textExtractionEnabled && colorModeEnabled && extractedTextColor != null && extractedAccentColor != null -> {
                val bg = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> Color.BLACK
                }
                colorPrimary = AlbumColorExtractor.ensureContrastColorful(extractedTextColor, bg)
                colorSecondary = AlbumColorExtractor.ensureContrastColorful(
                    AlbumColorExtractor.blendSecondary(extractedAccentColor, bg),
                    bg, 2.5
                )
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
            textExtractionEnabled && extractedTextColor != null -> {
                val (primary, secondary) = AlbumColorExtractor.resolveTextColors(
                    accent = extractedTextColor,
                    backgroundEstimate = extractedBgColor ?: Color.GRAY,
                    backgroundMode = background
                )
                colorPrimary = primary
                colorSecondary = secondary
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
            textColor == FocusPreferences.TEXT_COLOR_BLACK -> {
                colorPrimary = Color.BLACK
                colorSecondary = 0xFF333333.toInt()
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
            else -> {
                colorPrimary = COLOR_LYRIC_PRIMARY
                colorSecondary = COLOR_LYRIC_SECONDARY
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
        }
        if (monetEnabled || textExtractionEnabled) {
            val opacity = FocusStyleSnapshot.extractedColorOpacity
            if (opacity < 100 && backgroundColor != null) {
                backgroundColor = AlbumColorExtractor.applyOpacity(backgroundColor, opacity)
            }
        }
        if (monetEnabled && FocusStyleSnapshot.monetBgOnly && FocusStyleSnapshot.extractedBgColor != null) {
            backgroundColor = FocusStyleSnapshot.extractedBgColor!!
            val opacity = FocusStyleSnapshot.extractedColorOpacity
            if (opacity < 100) {
                backgroundColor = AlbumColorExtractor.applyOpacity(backgroundColor!!, opacity)
            }
        }
        if (background == FocusPreferences.BACKGROUND_ALBUM && FocusStyleSnapshot.extractedBgColor != null) {
            backgroundColor = FocusStyleSnapshot.extractedBgColor!!
            val opacity = FocusStyleSnapshot.extractedColorOpacity
            if (opacity < 100) {
                backgroundColor = AlbumColorExtractor.applyOpacity(backgroundColor, opacity)
            }
        }
        if (background == FocusPreferences.BACKGROUND_CUSTOM) {
            backgroundColor = FocusStyleSnapshot.bgCustomColor
            val opacity = FocusStyleSnapshot.extractedColorOpacity
            if (opacity < 100) {
                backgroundColor = AlbumColorExtractor.applyOpacity(backgroundColor, opacity)
            }
        }
        return Triple(colorPrimary, colorSecondary, backgroundColor)
    }

    /** 布局最大槽位数 */
    const val MULTI_LINE_MAX_SLOTS = 10

    private val MULTI_LINE_IDS = intArrayOf(
        R.id.focus_ml_line_0,
        R.id.focus_ml_line_1,
        R.id.focus_ml_line_2,
        R.id.focus_ml_line_3,
        R.id.focus_ml_line_4,
        R.id.focus_ml_line_5,
        R.id.focus_ml_line_6,
        R.id.focus_ml_line_7,
        R.id.focus_ml_line_8,
        R.id.focus_ml_line_9
    )



    enum class RefreshKind {

        /** 主歌词换行：立即 in-place 更新，不 cancel */

        LINE_CHANGE,

        /** 同一句 AOD 保活：续期 rvAod 会话 */

        KEEPALIVE

    }



    /**

     * @param recreateForAod 仅 needsAodRebind 时为 true（cancel+notify 首次绑定 rvAod）；

     *                       其余全走 notify，靠 updatable=true 驱动 SystemUI 原地刷新 RemoteViews。

     *                       参考 HyperCeiler MusicBaseHook：AOD/非AOD 均只 notify，不 cancel。

     */



    fun postFocusNotification(

        systemContext: Context,

        notificationManager: NotificationManager,

        content: FocusContent,

        showInShade: Boolean = false,

        pinAboveMedia: Boolean = false,

        showOnIsland: Boolean = false,

        refreshKind: RefreshKind = RefreshKind.LINE_CHANGE,

        forceRefresh: Boolean = false,

        recreateForAod: Boolean = false

    ) {

        val moduleContext = getModuleContext(systemContext)

        val lyric = content.lyricText.ifBlank { "\u266A" }

        val secondLine = content.secondLineText.takeIf {

            it.isNotBlank() && it != "\u00A0"

        }

        val lineTranslation = content.lineTranslation?.takeIf {

            it.isNotBlank() && it != "\u00A0"

        }

        val secondKey = secondLine.orEmpty()

        val multiLine = content.multiLine?.takeIf {
            it.lines.size == MULTI_LINE_MAX_SLOTS
        }
        val multiLineKey = multiLine?.contentKey().orEmpty()

        if (!forceRefresh &&
            refreshKind == RefreshKind.LINE_CHANGE
        ) {
            if (multiLine != null) {
                if (multiLineKey == lastPostedMultiLineKey) return
            } else if (lyric == lastPostedLyric && secondKey == lastPostedSecond) {
                return
            }
        }

        val musicLabel = content.songTitle.ifBlank { content.artist }.ifBlank { "音乐" }



        val lightIcon = NotificationIconHelper.createMusicIcon(moduleContext)

        val darkIcon = tintIcon(moduleContext, lightIcon, Color.BLACK)

        val circularIcon = circleCropIcon(moduleContext, lightIcon)



        // 仅 AOD 显示多行：锁屏回退双行布局；但 HyperOS4 的 AOD 实际显示 rv（锁屏视图），
        // 因此 AOD 状态下锁屏视图仍需多行布局，否则 AOD 会退化为单行/双行
        val lockMultiLine = if (FocusStyleSnapshot.aodMultiLineOnly) {
            if (content.aodActive) multiLine else null
        } else {
            multiLine
        }

        val lockViews = if (lockMultiLine != null) {
            buildMultiLineRemoteViews(
                moduleContext,
                R.layout.focus_lyric_lock_multiline,
                lockMultiLine,
                hideIcon = true,
                icon = null
            )
        } else {
            buildLyricRemoteViews(
                moduleContext,
                R.layout.focus_lyric_lock,
                lyric,
                secondLine,
                lineTranslation
            )
        }

        val aodViews = buildAodRemoteViews(
            moduleContext,
            content.songTitle,
            content.artist,
            lyric,
            secondLine,
            lineTranslation,
            lightIcon,
            content.musicPackage,
            multiLine
        )

        val iconsBundle = Bundle().apply {
            if (showOnIsland) {

                putParcelable("miui.focus.icon", circularIcon)

                putParcelable("miui.focus.share_icon", circularIcon)

            }

            putParcelable("miui.appIcon", lightIcon)

        }



        val islandTemplate = if (showOnIsland) {

            buildIslandTemplate(lyric, musicLabel, content.musicPackage, moduleContext)

        } else {

            buildDismissIslandTemplate()

        }

        val islandViews = if (showOnIsland) {

            buildLyricRemoteViews(
                moduleContext,
                R.layout.focus_lyric_island,
                lyric,
                secondLine,
                lineTranslation
            )

        } else {

            null

        }



        // 系统原生焦点通知规则（HyperOS4）：miui.focus.rv 为 RemoteViews 即被判定为焦点通知，
        // 配合 miui.focus.isFocus=true 显式标记，避免依赖 HyperCeiler 的 HyperFocusApi。
        val focusExtras = buildSystemFocusExtras(
            picticker = lightIcon,
            pictickerdark = darkIcon,
            ticker = lyric,
            island = islandTemplate,
            rv = lockViews,
            rvIsLand = islandViews,
            rvAod = aodViews,
            addpics = iconsBundle
        )

        patchFocusTimeout(focusExtras, TIMEOUT_SEC)
        patchFocusOrdering(focusExtras)

        ensureParamV2(focusExtras)

        if (!showOnIsland) {

            patchDismissIslandFocusExtras(focusExtras)

        }



        val builder = NotificationCompat.Builder(systemContext, CHANNEL_ID)

            .setSmallIcon(R.drawable.ic_music_note)

            .setContentTitle(lyric)

            .setContentText(secondLine ?: content.artist)

            .setSubText(secondLine ?: content.artist)

            .setTicker(lyric)

            .setOngoing(true)

            .setShowWhen(false)

            .setOnlyAlertOnce(true)

            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            .setPriority(NotificationCompat.PRIORITY_MAX)

            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

            .setSilent(true)

            .addExtras(focusExtras)

            .setCustomContentView(lockViews)

            .setCustomBigContentView(lockViews)

            .setStyle(NotificationCompat.DecoratedCustomViewStyle())


        builder.setSortKey("0")
        builder.setWhen(Long.MAX_VALUE)



        if (content.musicPackage.isNotBlank()) {

            builder.addExtras(Bundle().apply { putString("app_package", content.musicPackage) })

        }



        val notification = builder.build()

        applyMiuiQuietExtras(notification)

        val notifyId = CHANNEL_ID.hashCode()

        if (recreateForAod) {
            notificationManager.cancel(notifyId)
        }

        notificationManager.notify(notifyId, notification)

        lastPostedLyric = lyric

        lastPostedSecond = secondKey

        lastPostedMultiLineKey = multiLineKey

    }



    fun cancelFocusNotification(notificationManager: NotificationManager) {
        resetPostedCache()
        notificationManager.cancel(CHANNEL_ID.hashCode())
    }

    fun resetPostedCache() {
        lastPostedLyric = ""
        lastPostedSecond = ""
        lastPostedMultiLineKey = ""
    }

    /** AOD 去重参考：持久缓存比对外部传入的当前内容是否与上次已 post 的相同 */
    fun isPostedContentSame(
        songTitle: String,
        artist: String,
        lyricText: String,
        secondLineText: String,
        multiLineKey: String
    ): Boolean {
        if (multiLineKey.isNotEmpty() && lastPostedMultiLineKey.isNotEmpty()) {
            return multiLineKey == lastPostedMultiLineKey
        }
        return lyricText.isNotEmpty() &&
            lyricText == lastPostedLyric &&
            secondLineText == lastPostedSecond
    }



    /**
     * 系统原生焦点通知 extras（HyperOS4）。
     * 依据反编译的 FocusUtils.isFocusNotification：miui.focus.rv 为 RemoteViews 即判定为焦点通知，
     * miui.focus.isFocus=true 为显式标记。其余 extras 供 SystemUI 渲染 AOD / 岛 / 状态栏图标。
     */
    private fun buildSystemFocusExtras(
        picticker: Icon,
        pictickerdark: Icon?,
        ticker: String,
        island: JSONObject?,
        rv: RemoteViews,
        rvIsLand: RemoteViews?,
        rvAod: RemoteViews?,
        addpics: Bundle?
    ): Bundle {
        val focus = Bundle()
        val pics = Bundle()
        pics.putParcelable("miui.focus.pic_ticker", picticker)
        if (pictickerdark != null) {
            pics.putParcelable("miui.focus.pic_ticker_dark", pictickerdark)
        }
        addpics?.let { pics.putAll(it) }

        // 自定义焦点参数（JSON），对应 HyperOS 读取的 miui.focus.param.custom
        val cus = JSONObject()
        cus.put("ticker", ticker)
        cus.put("tickerPic", "miui.focus.pic_ticker")
        cus.put("enableFloat", false)
        cus.put("updatable", true)
        cus.put("isShowNotification", true)
        cus.put("timeout", TIMEOUT_SEC)
        cus.put("islandFirstFloat", false)
        island?.let { cus.put("param_island", it) }

        focus.putString("miui.focus.param.custom", cus.toString())
        focus.putBundle("miui.focus.pics", pics)
        focus.putParcelable("miui.focus.rv", rv)
        focus.putString("miui.focus.ticker", ticker)
        // 显式标记，确保系统判定为焦点通知
        focus.putBoolean("miui.focus.isFocus", true)
        rvAod?.let { focus.putParcelable("miui.focus.rvAod", it) }
        rvIsLand?.let { focus.putParcelable("miui.focus.rv.island.expand", it) }
        return focus
    }

    private fun buildDismissIslandTemplate(): JSONObject {

        // 关闭小岛：dismissIsland=true，避免 HyperOS 用 SystemUI 图标生成默认小岛
        val param = JSONObject()
        param.put("dismissIsland", true)
        param.put("islandTimeout", 1)
        param.put("needCloseAnimation", false)
        param.put("islandOrder", false)
        param.put("islandProperty", 1)
        param.put("bigIslandArea", JSONObject())
        return param
    }



    private fun buildIslandTemplate(
        lyric: String,
        musicLabel: String,
        musicPackage: String,
        moduleContext: Context
    ): org.json.JSONObject {

        // 固定单块布局，避免长短句切换时左右分岛变形动画
        val displayText = lyric.trim().ifBlank { "\u266A" }
        val shareContent = if (musicPackage.isNotBlank()) {
            "$musicLabel · $displayText"
        } else {
            displayText
        }

        val shareData = JSONObject()
        shareData.put("title", "歌词")
        shareData.put("content", "LyricFocus")
        shareData.put("pic", "miui.focus.share_icon")
        shareData.put("shareContent", shareContent)

        val pic = JSONObject()
        pic.put("pic", "miui.focus.icon")
        pic.put("type", 1)

        val textInfo = JSONObject()
        textInfo.put("title", displayText)

        val mainInfo = JSONObject()
        mainInfo.put("type", 1)
        mainInfo.put("picInfo", pic)
        mainInfo.put("textInfo", textInfo)

        val islandStyle = resolveLyricStyle(moduleContext, R.layout.focus_lyric_island)

        val bigIslandArea = JSONObject()
        bigIslandArea.put("imageTextInfoLeft", mainInfo)

        val smallIslandArea = JSONObject()
        smallIslandArea.put("picInfo", pic)

        val param = JSONObject()
        param.put("shareData", shareData)
        param.put("highlightColor", colorToIslandHex(islandStyle.colorPrimary))
        param.put("islandTimeout", TIMEOUT_SEC)
        param.put("islandOrder", true)
        param.put("bigIslandArea", bigIslandArea)
        param.put("smallIslandArea", smallIslandArea)
        return param
    }



    private fun buildLyricRemoteViews(
        moduleContext: Context,
        layoutId: Int,
        lyric: String,
        secondLine: String?,
        lineTranslation: String?
    ): RemoteViews {
        val views = RemoteViews(moduleContext.packageName, layoutId)
        val style = resolveLyricStyle(moduleContext, layoutId)
        applyLyricStyle(
            moduleContext,
            views,
            lyric,
            secondLine,
            lineTranslation,
            style,
            hideSongTitle = layoutId == R.layout.focus_lyric_lock,
            layoutId = layoutId
        )
        return views
    }

    private data class LyricStyle(
        val primarySizeSp: Float,
        val secondarySizeSp: Float,
        val colorPrimary: Int,
        val colorSecondary: Int,
        val lyricMaxLines: Int,
        val translationMaxLines: Int,
        val gravityValue: Int,
        var backgroundColor: Int?
    )

    private fun resolveLyricStyle(moduleContext: Context, layoutId: Int): LyricStyle {
        if (layoutId == R.layout.focus_lyric_aod_custom) {
            return resolveCustomAodLyricStyle(moduleContext)
        }
        val textSize = FocusStyleSnapshot.textSizeSp
        val textColor = FocusStyleSnapshot.textColor
        val lyricMaxLines = FocusStyleSnapshot.lyricMaxLines
        val translationMaxLines = FocusStyleSnapshot.translationMaxLines
        val gravity = FocusStyleSnapshot.gravity
        val background = FocusStyleSnapshot.background
        val monetEnabled = FocusStyleSnapshot.monetDynamicColorEnabled
        val textExtractionEnabled = FocusStyleSnapshot.textColorExtractionEnabled
        val colorModeEnabled = FocusStyleSnapshot.colorModeEnabled
        val hasExtractedColors = monetEnabled || textExtractionEnabled
        val extractedColor = if (hasExtractedColors) {
            FocusStyleSnapshot.extractedTextColor
        } else {
            null
        }
        val extractedBgColor = FocusStyleSnapshot.extractedBgColor
        val extractedAccent = FocusStyleSnapshot.extractedAccentColor

        val colorPrimary: Int
        val colorSecondary: Int
        var backgroundColor: Int?
        when {
            monetEnabled && !FocusStyleSnapshot.monetBgOnly && colorModeEnabled && extractedColor != null && extractedBgColor != null -> {
                colorPrimary = AlbumColorExtractor.ensureContrastColorful(extractedColor, extractedBgColor)
                val accent = extractedAccent ?: extractedColor
                colorSecondary = AlbumColorExtractor.ensureContrastColorful(
                    AlbumColorExtractor.blendSecondary(accent, extractedBgColor),
                    extractedBgColor,
                    3.0
                )
                backgroundColor = extractedBgColor
            }
            monetEnabled && !FocusStyleSnapshot.monetBgOnly && extractedColor != null && extractedBgColor != null -> {
                colorPrimary = extractedColor
                colorSecondary = AlbumColorExtractor.ensureContrast(
                    AlbumColorExtractor.blendSecondary(extractedColor, extractedBgColor),
                    extractedBgColor,
                    3.0
                )
                backgroundColor = extractedBgColor
            }
            textExtractionEnabled && colorModeEnabled && extractedColor != null && extractedAccent != null -> {
                val bg = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> Color.BLACK
                }
                colorPrimary = AlbumColorExtractor.ensureContrastColorful(extractedColor, bg)
                colorSecondary = AlbumColorExtractor.ensureContrastColorful(
                    AlbumColorExtractor.blendSecondary(extractedAccent, bg),
                    bg,
                    3.0
                )
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
            textExtractionEnabled && extractedColor != null -> {
                val (primary, secondary) = AlbumColorExtractor.resolveTextColors(
                    accent = extractedColor,
                    backgroundEstimate = extractedBgColor ?: Color.GRAY,
                    backgroundMode = background
                )
                colorPrimary = primary
                colorSecondary = secondary
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
            textColor == FocusPreferences.TEXT_COLOR_BLACK -> {
                colorPrimary = Color.BLACK
                colorSecondary = 0xFF333333.toInt()
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
            textColor == FocusPreferences.TEXT_COLOR_PRESET -> {
                colorPrimary = FocusStyleSnapshot.presetTextColor
                colorSecondary = blendSecondaryTextColor(colorPrimary)
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
            textColor == FocusPreferences.TEXT_COLOR_CUSTOM -> {
                colorPrimary = FocusStyleSnapshot.lyricCustomColor
                colorSecondary = blendSecondaryTextColor(colorPrimary)
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
            else -> {
                colorPrimary = COLOR_LYRIC_PRIMARY
                colorSecondary = COLOR_LYRIC_SECONDARY
                backgroundColor = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
                    else -> null
                }
            }
        }

        if (hasExtractedColors) {
            val opacity = FocusStyleSnapshot.extractedColorOpacity
            if (opacity < 100 && backgroundColor != null) {
                backgroundColor = AlbumColorExtractor.applyOpacity(backgroundColor, opacity)
            }
        }
        if (monetEnabled && FocusStyleSnapshot.monetBgOnly && FocusStyleSnapshot.extractedBgColor != null) {
            backgroundColor = FocusStyleSnapshot.extractedBgColor!!
            val opacity = FocusStyleSnapshot.extractedColorOpacity
            if (opacity < 100) {
                backgroundColor = AlbumColorExtractor.applyOpacity(backgroundColor!!, opacity)
            }
        }
        if (background == FocusPreferences.BACKGROUND_ALBUM && FocusStyleSnapshot.extractedBgColor != null) {
            backgroundColor = FocusStyleSnapshot.extractedBgColor!!
            val opacity = FocusStyleSnapshot.extractedColorOpacity
            if (opacity < 100) {
                backgroundColor = AlbumColorExtractor.applyOpacity(backgroundColor, opacity)
            }
        }
        if (background == FocusPreferences.BACKGROUND_CUSTOM) {
            backgroundColor = FocusStyleSnapshot.bgCustomColor
            val opacity = FocusStyleSnapshot.extractedColorOpacity
            if (opacity < 100) {
                backgroundColor = AlbumColorExtractor.applyOpacity(backgroundColor, opacity)
            }
        }

        val gravityValue = lyricGravityValue(gravity)

        val primarySize = if (layoutId == R.layout.focus_lyric_island) {
            textSize * 0.94f
        } else {
            textSize
        }

        return LyricStyle(
            primarySizeSp = primarySize,
            secondarySizeSp = textSize * 0.78f,
            colorPrimary = colorPrimary,
            colorSecondary = colorSecondary,
            lyricMaxLines = lyricMaxLines,
            translationMaxLines = translationMaxLines,
            gravityValue = gravityValue,
            backgroundColor = backgroundColor
        )
    }

    private fun resolveCustomAodLyricStyle(moduleContext: Context): LyricStyle {
        val textSize = FocusStyleSnapshot.customAodTextSizeSp
        val lyricMaxLines = FocusStyleSnapshot.customAodLyricMaxLines
        val translationMaxLines = FocusStyleSnapshot.customAodTranslationMaxLines

        val colorPrimary: Int
        val colorSecondary: Int
        when (FocusStyleSnapshot.customAodColorMode) {
            FocusPreferences.CUSTOM_AOD_COLOR_ALBUM -> {
                val accent = FocusStyleSnapshot.extractedAccentColor
                val text = FocusStyleSnapshot.extractedTextColor
                if (accent != null && text != null && accent != text) {
                    // 万象息屏色彩模式：黑底双色，accent 与 text 取专辑主色、文字色两组
                    colorPrimary = AlbumColorExtractor.ensureContrastColorful(accent, Color.BLACK, 4.5)
                    colorSecondary = AlbumColorExtractor.ensureContrastColorful(text, Color.BLACK, 3.5)
                } else if (accent != null) {
                    colorPrimary = AlbumColorExtractor.ensureContrastColorful(accent, Color.BLACK, 4.5)
                    val blended = AlbumColorExtractor.blendSecondary(accent, Color.BLACK)
                    colorSecondary = AlbumColorExtractor.ensureContrastColorful(blended, Color.BLACK, 3.5)
                } else if (text != null) {
                    colorPrimary = AlbumColorExtractor.ensureContrastColorful(text, Color.BLACK, 4.5)
                    val blended = AlbumColorExtractor.blendSecondary(text, Color.BLACK)
                    colorSecondary = AlbumColorExtractor.ensureContrastColorful(blended, Color.BLACK, 3.5)
                } else {
                    colorPrimary = COLOR_LYRIC_PRIMARY
                    colorSecondary = COLOR_LYRIC_SECONDARY
                }
            }
            FocusPreferences.CUSTOM_AOD_COLOR_PRESET -> {
                colorPrimary = FocusStyleSnapshot.customAodPresetColor
                colorSecondary = blendSecondaryTextColor(colorPrimary)
            }
            else -> {
                colorPrimary = COLOR_LYRIC_PRIMARY
                colorSecondary = COLOR_LYRIC_SECONDARY
            }
        }

        return LyricStyle(
            primarySizeSp = textSize,
            secondarySizeSp = textSize * 0.78f,
            colorPrimary = colorPrimary,
            colorSecondary = colorSecondary,
            lyricMaxLines = lyricMaxLines,
            translationMaxLines = translationMaxLines,
            gravityValue = lyricGravityValue(FocusStyleSnapshot.customAodGravity),
            backgroundColor = null
        )
    }

    private fun lyricGravityValue(gravity: String): Int {
        return when (gravity) {
            FocusPreferences.GRAVITY_LEFT ->
                android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            FocusPreferences.GRAVITY_RIGHT ->
                android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            else -> android.view.Gravity.CENTER
        }
    }

    private fun blendSecondaryTextColor(primary: Int): Int {
        val r = ((Color.red(primary) * 0.82f) + (255 * 0.18f)).toInt().coerceIn(0, 255)
        val g = ((Color.green(primary) * 0.82f) + (255 * 0.18f)).toInt().coerceIn(0, 255)
        val b = ((Color.blue(primary) * 0.82f) + (255 * 0.18f)).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun applyLyricStyle(
        moduleContext: Context,
        views: RemoteViews,
        lyric: String,
        secondLine: String?,
        lineTranslation: String?,
        style: LyricStyle,
        hideSongTitle: Boolean,
        trackLabel: String? = null,
        songTitle: String? = null,
        songArtist: String? = null,
        layoutId: Int = 0,
        musicPackage: String = ""
    ) {
        views.setInt(R.id.focus_lyric_content, "setGravity", style.gravityValue)

        var primaryText = lyric
        var secondaryText = secondLine?.takeIf { it.isNotBlank() }
        val actualTranslation = lineTranslation?.takeIf { it.isNotBlank() }
        if (FocusStyleSnapshot.swapLyricTranslation && actualTranslation != null) {
            primaryText = actualTranslation
            secondaryText = lyric
        }
        // 多行模式下「仅显示第一行」不生效；默认双行布局仍尊重该开关
        if (FocusStyleSnapshot.singleLineOnly && !FocusStyleSnapshot.multiLineLyrics) {
            secondaryText = null
        }

        if (layoutId == R.layout.focus_lyric_aod_custom && primaryText.isNotEmpty()) {
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            primaryText = ellipsizeForAodLyric(primaryText, style, metrics)
        }

        views.setTextViewText(R.id.focuslyric, primaryText)
        views.setTextColor(R.id.focuslyric, style.colorPrimary)
        views.setTextViewTextSize(R.id.focuslyric, TypedValue.COMPLEX_UNIT_SP, style.primarySizeSp)
        views.setInt(R.id.focuslyric, "setMaxLines", style.lyricMaxLines)
        views.setInt(R.id.focuslyric, "setGravity", style.gravityValue)

        if (secondaryText.isNullOrBlank()) {
            views.setViewVisibility(R.id.focustflyric, View.GONE)
        } else {
            val secondarySize = if (isJapaneseText(secondaryText)) {
                style.secondarySizeSp * 0.88f
            } else {
                style.secondarySizeSp
            }
            views.setTextViewText(R.id.focustflyric, secondaryText)
            views.setTextColor(R.id.focustflyric, style.colorSecondary)
            views.setTextViewTextSize(R.id.focustflyric, TypedValue.COMPLEX_UNIT_SP, secondarySize)
            views.setInt(R.id.focustflyric, "setMaxLines", style.translationMaxLines)
            views.setInt(R.id.focustflyric, "setGravity", style.gravityValue)
            views.setViewVisibility(R.id.focustflyric, View.VISIBLE)
        }

        if (style.backgroundColor != null) {
            val bg = style.backgroundColor!!
            safeSetViewVisibility(views, R.id.focus_lyric_bg, View.VISIBLE)
            safeSetImageViewBitmap(views, R.id.focus_lyric_bg, solidColorBitmap(bg))
        } else {
            safeSetViewVisibility(views, R.id.focus_lyric_bg, View.GONE)
        }

        when {
            hideSongTitle -> {
                safeSetViewVisibility(views, R.id.focus_song_row, View.GONE)
                safeSetViewVisibility(views, R.id.focus_song_title, View.GONE)
            }
            layoutId == R.layout.focus_lyric_aod_custom -> {
                applyCustomAodSongRow(moduleContext, views, songTitle.orEmpty(), songArtist.orEmpty(), style, musicPackage)
            }
            !trackLabel.isNullOrBlank() -> {
                views.setTextViewText(R.id.focus_song_title, trackLabel)
                views.setTextColor(R.id.focus_song_title, style.colorSecondary)
                views.setTextViewTextSize(
                    R.id.focus_song_title,
                    TypedValue.COMPLEX_UNIT_SP,
                    style.secondarySizeSp * 0.85f
                )
                views.setInt(R.id.focus_song_title, "setMaxLines", 1)
                views.setInt(R.id.focus_song_title, "setGravity", style.gravityValue)
                safeSetBoolean(views, R.id.focus_song_title, "setSingleLine", true)
                safeSetViewVisibility(views, R.id.focus_song_title, View.VISIBLE)
            }
            else -> {
                safeSetViewVisibility(views, R.id.focus_song_row, View.GONE)
                safeSetViewVisibility(views, R.id.focus_song_title, View.GONE)
            }
        }

        if (layoutId == R.layout.focus_lyric_aod_custom) {
            applyCustomAodContentWidth(views)
        }
    }

    private fun applyCustomAodSongRow(
        moduleContext: Context,
        views: RemoteViews,
        title: String,
        artist: String,
        style: LyricStyle,
        musicPackage: String = ""
    ) {
        val songInfoMode = FocusStyleSnapshot.customAodSongInfo
        if (songInfoMode == FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ALL) {
            safeSetViewVisibility(views, R.id.focus_song_row, View.GONE)
            safeSetViewVisibility(views, R.id.focus_song_inner, View.GONE)
            safeSetViewVisibility(views, R.id.focus_song_icon, View.GONE)
            return
        }

        val showTitle = songInfoMode != FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_TITLE
        val showArtist = songInfoMode != FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ARTIST
        val t = if (showTitle) title.trim() else ""
        val a = if (showArtist) artist.trim() else ""
        val songSize = style.secondarySizeSp * 0.85f
        val songColor = style.colorSecondary
        val rowGravity = style.gravityValue
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val slotWidth = estimateCustomAodContentWidthPx(metrics)
        val dotWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            12f,
            metrics
        ).toInt()

        val iconWidth = if (FocusStyleSnapshot.customAodTitleIconEnabled) {
            (songSize * metrics.scaledDensity).toInt()
        } else {
            0
        }
        val textBudget = (slotWidth - dotWidth - iconWidth - 16).coerceAtLeast(60)

        when {
            t.isNotEmpty() && a.isNotEmpty() -> {
                val titleMax = (textBudget * 3 / 5f).toInt()
                val artistMax = (textBudget * 2 / 5f).toInt()
                views.setTextViewText(
                    R.id.focus_song_title,
                    ellipsizeForAodSong(t, titleMax, songSize, metrics)
                )
                views.setTextViewText(
                    R.id.focus_song_artist,
                    ellipsizeForAodSong(a, artistMax, songSize, metrics)
                )
                views.setTextColor(R.id.focus_song_title, songColor)
                views.setTextColor(R.id.focus_song_artist, songColor)
                views.setTextViewTextSize(R.id.focus_song_title, TypedValue.COMPLEX_UNIT_SP, songSize)
                views.setTextViewTextSize(R.id.focus_song_artist, TypedValue.COMPLEX_UNIT_SP, songSize)
                views.setTextColor(R.id.focus_song_dot, songColor)
                views.setInt(R.id.focus_song_inner, "setGravity", rowGravity)
                views.setInt(R.id.focus_song_title, "setGravity", rowGravity)
                views.setInt(R.id.focus_song_artist, "setGravity", rowGravity)
                applyCustomAodSongMaxWidths(views, titleMax, artistMax)
                safeSetViewVisibility(views, R.id.focus_song_row, View.VISIBLE)
                safeSetViewVisibility(views, R.id.focus_song_inner, View.VISIBLE)
                views.setViewVisibility(R.id.focus_song_title, View.VISIBLE)
                views.setViewVisibility(R.id.focus_song_dot, View.VISIBLE)
                views.setViewVisibility(R.id.focus_song_artist, View.VISIBLE)
                applyCustomAodTitleIcon(moduleContext, views, songColor, songSize, metrics, musicPackage)
            }
            t.isNotEmpty() -> {
                views.setTextViewText(
                    R.id.focus_song_title,
                    ellipsizeForAodSong(t, textBudget, songSize, metrics)
                )
                views.setTextColor(R.id.focus_song_title, songColor)
                views.setTextViewTextSize(R.id.focus_song_title, TypedValue.COMPLEX_UNIT_SP, songSize)
                views.setInt(R.id.focus_song_inner, "setGravity", rowGravity)
                views.setInt(R.id.focus_song_title, "setGravity", rowGravity)
                applyCustomAodSongMaxWidths(views, textBudget, textBudget)
                safeSetViewVisibility(views, R.id.focus_song_row, View.VISIBLE)
                safeSetViewVisibility(views, R.id.focus_song_inner, View.VISIBLE)
                views.setViewVisibility(R.id.focus_song_title, View.VISIBLE)
                safeSetViewVisibility(views, R.id.focus_song_dot, View.GONE)
                safeSetViewVisibility(views, R.id.focus_song_artist, View.GONE)
                applyCustomAodTitleIcon(moduleContext, views, songColor, songSize, metrics, musicPackage)
            }
            a.isNotEmpty() -> {
                views.setTextViewText(
                    R.id.focus_song_title,
                    ellipsizeForAodSong(a, textBudget, songSize, metrics)
                )
                views.setTextColor(R.id.focus_song_title, songColor)
                views.setTextViewTextSize(R.id.focus_song_title, TypedValue.COMPLEX_UNIT_SP, songSize)
                views.setInt(R.id.focus_song_inner, "setGravity", rowGravity)
                views.setInt(R.id.focus_song_title, "setGravity", rowGravity)
                applyCustomAodSongMaxWidths(views, textBudget, textBudget)
                safeSetViewVisibility(views, R.id.focus_song_row, View.VISIBLE)
                safeSetViewVisibility(views, R.id.focus_song_inner, View.VISIBLE)
                views.setViewVisibility(R.id.focus_song_title, View.VISIBLE)
                safeSetViewVisibility(views, R.id.focus_song_dot, View.GONE)
                safeSetViewVisibility(views, R.id.focus_song_artist, View.GONE)
                applyCustomAodTitleIcon(moduleContext, views, songColor, songSize, metrics, musicPackage)
            }
            else -> {
                safeSetViewVisibility(views, R.id.focus_song_row, View.GONE)
                safeSetViewVisibility(views, R.id.focus_song_inner, View.GONE)
                safeSetViewVisibility(views, R.id.focus_song_icon, View.GONE)
            }
        }
    }

    private fun applyCustomAodTitleIcon(
        moduleContext: Context,
        views: RemoteViews,
        color: Int,
        sizeSp: Float,
        metrics: android.util.DisplayMetrics,
        musicPackage: String
    ) {
        if (!FocusStyleSnapshot.customAodTitleIconEnabled) {
            safeSetViewVisibility(views, R.id.focus_song_icon, View.GONE)
            return
        }

        val iconSizePercent = FocusStyleSnapshot.customAodTitleIconSize / 100f
        val iconSizeSp = sizeSp * iconSizePercent

        val iconResId = resolveTitleIconResId(musicPackage)
        val iconBitmap = drawableToBitmap(moduleContext, iconResId, color, iconSizeSp, metrics)

        if (iconBitmap != null) {
            safeSetImageViewBitmap(views, R.id.focus_song_icon, iconBitmap)
            safeSetViewVisibility(views, R.id.focus_song_icon, View.VISIBLE)
        } else {
            safeSetViewVisibility(views, R.id.focus_song_icon, View.GONE)
        }
    }

    private fun resolveTitleIconResId(musicPackage: String): Int {
        val preset = FocusPreferences.resolvePackageToIconPreset(musicPackage)
        return iconPresetToResId(preset)
    }

    private fun iconPresetToResId(preset: String): Int {
        return when (preset) {
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_NETEASE -> R.drawable.ic_app_icon_netease
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_QQ -> R.drawable.ic_app_icon_qq
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_KUGOU -> R.drawable.ic_app_icon_kugou
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_KUWO -> R.drawable.ic_app_icon_kuwo
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_QISHUI -> R.drawable.ic_app_icon_qishui
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_BODIAN -> R.drawable.ic_app_icon_bodian
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_SPOTIFY -> R.drawable.ic_app_icon_spotify
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_APPLE -> R.drawable.ic_app_icon_apple
            else -> R.drawable.ic_app_icon_apple
        }
    }

    private fun drawableToBitmap(
        moduleContext: Context,
        resId: Int,
        tintColor: Int,
        sizeSp: Float,
        metrics: android.util.DisplayMetrics
    ): Bitmap {
        val sizePx = (sizeSp * metrics.scaledDensity).toInt()
        val drawable = moduleContext.resources.getDrawable(resId, null)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        drawable.mutate().setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun ellipsizeForAodSong(
        text: String,
        maxWidthPx: Int,
        textSizeSp: Float,
        metrics: android.util.DisplayMetrics
    ): String {
        if (text.isEmpty() || maxWidthPx <= 0) return text
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                metrics
            )
        }
        if (paint.measureText(text) <= maxWidthPx) return text
        val ellipsized = TextUtils.ellipsize(
            text,
            paint,
            maxWidthPx.toFloat(),
            TextUtils.TruncateAt.END
        )
        return ellipsized?.toString()?.trimEnd().orEmpty().ifEmpty { text }
    }

    private fun ellipsizeForAodLyric(
        text: String,
        style: LyricStyle,
        metrics: android.util.DisplayMetrics
    ): String {
        if (text.isEmpty()) return text
        val slotWidth = estimateCustomAodContentWidthPx(metrics)
        val maxLines = style.lyricMaxLines.coerceAtLeast(1)
        val maxWidthPx = (slotWidth * maxLines).coerceAtLeast(slotWidth)
        return ellipsizeForAodSong(text, maxWidthPx, style.primarySizeSp, metrics)
    }

    private fun applyCustomAodSongMaxWidths(
        views: RemoteViews,
        titleMax: Int,
        artistMax: Int
    ) {
        try {
            views.setInt(R.id.focus_song_title, "setMaxWidth", titleMax.coerceAtLeast(1))
            views.setInt(R.id.focus_song_artist, "setMaxWidth", artistMax.coerceAtLeast(1))
        } catch (_: Throwable) {
        }
    }

    private fun estimateCustomAodContentWidthPx(metrics: android.util.DisplayMetrics): Int {
        val widthPercent = FocusStyleSnapshot.customAodLyricWidth
        val basePadDp = 4
        val extraPadDp = ((100 - widthPercent) * 48 / 50).coerceAtLeast(0)
        val padPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            (basePadDp + extraPadDp).toFloat(),
            metrics
        ).toInt()
        return ((metrics.widthPixels - padPx * 2) * widthPercent / 100f * 0.92f)
            .toInt()
            .coerceAtLeast(120)
    }

    private fun applyCustomAodContentWidth(views: RemoteViews) {
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val widthPercent = FocusStyleSnapshot.customAodLyricWidth
        val basePadDp = 4
        val extraPadDp = ((100 - widthPercent) * 48 / 50).coerceAtLeast(0)
        val padPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            (basePadDp + extraPadDp).toFloat(),
            metrics
        ).toInt()
        try {
            views.setViewPadding(R.id.focus_lyric_content, padPx, 0, padPx, 0)
        } catch (_: Throwable) {
        }
    }

    private fun safeSetViewVisibility(views: RemoteViews, viewId: Int, visibility: Int) {
        try {
            views.setViewVisibility(viewId, visibility)
        } catch (_: Throwable) {
        }
    }

    private fun safeSetImageViewBitmap(views: RemoteViews, viewId: Int, bitmap: Bitmap) {
        try {
            views.setImageViewBitmap(viewId, bitmap)
        } catch (_: Throwable) {
        }
    }

    private fun safeSetBoolean(views: RemoteViews, viewId: Int, method: String, value: Boolean) {
        try {
            views.setBoolean(viewId, method, value)
        } catch (_: Throwable) {
        }
    }

    private fun solidColorBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    private fun colorToIslandHex(color: Int): String {
        return String.format("#%08X", color.toLong() and 0xFFFFFFFFL)
    }



    private fun buildAodRemoteViews(

        moduleContext: Context,

        songTitle: String,

        artist: String,

        lyric: String,

        secondLine: String?,

        lineTranslation: String?,

        icon: Icon,

        musicPackage: String,

        multiLine: MultiLineWindow? = null

    ): RemoteViews {

        val useCustomLayout = FocusStyleSnapshot.customAodLayout
        if (!useCustomLayout && multiLine != null) {
            return buildMultiLineRemoteViews(
                moduleContext,
                R.layout.focus_lyric_aod_multiline,
                multiLine,
                hideIcon = false,
                icon = icon
            )
        }

        val layoutId = if (useCustomLayout) {
            R.layout.focus_lyric_aod_custom
        } else {
            R.layout.focus_lyric_aod
        }

        val views = RemoteViews(moduleContext.packageName, layoutId)

        if (!useCustomLayout) {
            views.setImageViewIcon(R.id.focusicon, icon)
        }

        val style = resolveLyricStyle(moduleContext, layoutId)

        applyLyricStyle(
            moduleContext,
            views,
            lyric,
            secondLine,
            lineTranslation,
            style,
            hideSongTitle = !useCustomLayout,
            songTitle = if (useCustomLayout) songTitle else null,
            songArtist = if (useCustomLayout) artist else null,
            layoutId = layoutId,
            musicPackage = musicPackage
        )

        return views

    }

    private fun buildMultiLineRemoteViews(
        moduleContext: Context,
        layoutId: Int,
        multiLine: MultiLineWindow,
        hideIcon: Boolean,
        icon: Icon?
    ): RemoteViews {
        val views = RemoteViews(moduleContext.packageName, layoutId)
        val style = resolveLyricStyle(
            moduleContext,
            if (layoutId == R.layout.focus_lyric_aod_multiline) {
                R.layout.focus_lyric_aod
            } else {
                R.layout.focus_lyric_lock
            }
        )
        if (!hideIcon && icon != null) {
            views.setImageViewIcon(R.id.focusicon, icon)
        }
        applyMultiLineStyle(moduleContext, views, multiLine, style)
        return views
    }

    private fun applyMultiLineStyle(
        moduleContext: Context,
        views: RemoteViews,
        multiLine: MultiLineWindow,
        style: LyricStyle
    ) {
        // 多行歌词顶格显示：内容从区域顶部开始排布（保留水平对齐）
        val contentGravity = when (style.gravityValue and android.view.Gravity.HORIZONTAL_GRAVITY_MASK) {
            android.view.Gravity.START -> android.view.Gravity.START or android.view.Gravity.TOP
            android.view.Gravity.END -> android.view.Gravity.END or android.view.Gravity.TOP
            else -> android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
        }
        views.setInt(R.id.focus_lyric_content, "setGravity", contentGravity)

        val interleaved = multiLine.interleavedTranslations
        val rawLines = multiLine.lines
        val visibleCount = multiLine.visibleCount.coerceIn(4, MULTI_LINE_MAX_SLOTS)
        val displayLines = if (interleaved && FocusStyleSnapshot.swapLyricTranslation) {
            // 每对内互换：偶数槽显示翻译（主色），奇数槽显示原文（淡色）
            List(MULTI_LINE_MAX_SLOTS) { i ->
                if (i >= visibleCount) return@List ""
                val pair = i / 2
                val partner = if (i % 2 == 0) pair * 2 + 1 else pair * 2
                rawLines.getOrNull(partner).orEmpty()
            }
        } else {
            List(MULTI_LINE_MAX_SLOTS) { i ->
                if (i >= visibleCount) "" else rawLines.getOrNull(i).orEmpty()
            }
        }

        val textSizeSp = FocusStyleSnapshot.multiLineTextSizeSp
        val colorModeBgColor = if (FocusStyleSnapshot.colorModeEnabled) {
            FocusStyleSnapshot.extractedBgColor
        } else null
        val monetBgColor = if (FocusStyleSnapshot.monetDynamicColorEnabled) {
            FocusStyleSnapshot.extractedBgColor
        } else null
        val extractedBg = colorModeBgColor ?: monetBgColor
        val defaultLineColor = if (extractedBg != null) {
            val refColor = if (FocusStyleSnapshot.monetBgOnly) style.colorPrimary
                else (FocusStyleSnapshot.extractedTextColor ?: style.colorPrimary)
            if (FocusStyleSnapshot.monetBgOnly) refColor
            else if (colorModeBgColor != null) {
                AlbumColorExtractor.ensureContrastColorful(refColor, extractedBg, 4.0)
            } else {
                AlbumColorExtractor.ensureContrast(refColor, extractedBg, 4.0)
            }
        } else {
            FocusStyleSnapshot.extractedTextColor ?: style.colorPrimary
        }
        val currentLineColor = if (colorModeBgColor != null) {
            val accentRef = if (FocusStyleSnapshot.monetBgOnly) defaultLineColor
                else (FocusStyleSnapshot.extractedAccentColor ?: defaultLineColor)
            if (FocusStyleSnapshot.monetBgOnly) accentRef
            else AlbumColorExtractor.ensureContrastColorful(accentRef, colorModeBgColor, 5.0)
        } else if (monetBgColor != null) {
            if (FocusStyleSnapshot.monetBgOnly) defaultLineColor
            else AlbumColorExtractor.ensureContrast(defaultLineColor, monetBgColor, 7.0)
        } else when (style.backgroundColor) {
            Color.WHITE -> Color.BLACK
            else -> COLOR_LYRIC_PRIMARY
        }
        val nonCurrentTransColor = fadeTextColor(defaultLineColor)
        val currentLineSlot = multiLine.currentLineSlot.coerceAtLeast(0)
        val currentTransSlot = if (interleaved && currentLineSlot < visibleCount - 1) currentLineSlot + 1 else -1

        for (i in 0 until MULTI_LINE_MAX_SLOTS) {
            val viewId = MULTI_LINE_IDS[i]
            val displayText = displayLines[i]
            val isTranslation = interleaved && i < visibleCount && i % 2 == 1
            val isCurrentLine = i == currentLineSlot
            val isCurrentTrans = i == currentTransSlot && displayText.isNotBlank()
            if (i >= visibleCount || displayText.isBlank()) {
                views.setTextViewText(viewId, " ")
                views.setViewVisibility(viewId, View.VISIBLE)
                continue
            }
            views.setViewVisibility(viewId, View.VISIBLE)
            if (isCurrentLine || isCurrentTrans) {
                views.setCharSequence(viewId, "setText", boldText(displayText))
                views.setTextColor(viewId, currentLineColor)
                views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP,
                    if (isCurrentTrans) textSizeSp * 0.62f else textSizeSp)
                views.setInt(viewId, "setMaxLines", if (isCurrentTrans) 1 else 4)
            } else if (isTranslation) {
                views.setTextViewText(viewId, displayText)
                views.setTextColor(viewId, nonCurrentTransColor)
                views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, textSizeSp * 0.55f)
                views.setInt(viewId, "setMaxLines", 1)
            } else {
                views.setTextViewText(viewId, displayText)
                views.setTextColor(viewId, defaultLineColor)
                views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, textSizeSp * 0.75f)
                views.setInt(viewId, "setMaxLines", 1)
            }
            views.setInt(viewId, "setGravity", style.gravityValue)
            // 行间距：组内翻译小间距，组间（或普通行）大间距
            val paddingTop = when {
                i == 0 -> 0
                interleaved && i % 2 == 1 -> 4
                interleaved -> 28
                else -> 24
            }
            views.setViewPadding(viewId, 0, paddingTop, 0, 0)
        }

        if (style.backgroundColor != null) {
            val bg = style.backgroundColor!!
            safeSetViewVisibility(views, R.id.focus_lyric_bg, View.VISIBLE)
            safeSetImageViewBitmap(views, R.id.focus_lyric_bg, solidColorBitmap(bg))
        } else {
            safeSetViewVisibility(views, R.id.focus_lyric_bg, View.GONE)
        }

        // 多行歌词区域高度可调（200-450dp）
        try {
            views.setViewLayoutHeight(
                R.id.focus_lyric_content,
                FocusStyleSnapshot.multiLineHeightDp.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP
            )
        } catch (_: Throwable) {
        }
    }

    /** 翻译行比次要色再淡一档，避免与原文抢视觉 */
    private fun fadeTextColor(color: Int, factor: Float = 0.72f): Int {
        val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun boldText(text: String): CharSequence {
        if (text.isBlank()) return text
        val span = SpannableString(text)
        span.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    /** 检测文本是否包含日语字符（平假名/片假名/汉字） */
    private fun isJapaneseText(text: String): Boolean {
        return text.any { c ->
            c in '\u3040'..'\u309F' ||  // 平假名
            c in '\u30A0'..'\u30FF' ||  // 片假名
            c in '\u4E00'..'\u9FFF'     // CJK 汉字
        }
    }



    private fun ensureParamV2(extras: Bundle) {
        try {
            val paramKey = "miui.focus.param"
            val existing = extras.getString(paramKey)
            if (existing != null && existing.contains("\"param_v2\"")) return

            val customKey = "miui.focus.param.custom"
            val rawCustom = extras.getString(customKey) ?: return
            val customRoot = JSONObject(rawCustom)

            val paramV2 = JSONObject()
            for (key in customRoot.keys()) {
                paramV2.put(key, customRoot.get(key))
            }
            paramV2.put("protocol", 1)
            paramV2.put("enableFloat", false)
            paramV2.put("islandFirstFloat", false)

            extras.putString(
                paramKey,
                JSONObject().put("param_v2", paramV2).put("protocol", 1).toString()
            )
        } catch (_: Throwable) {
        }
    }


    private fun patchDismissIslandFocusExtras(extras: Bundle) {

        try {

            val customKey = "miui.focus.param.custom"

            val rawCustom = extras.getString(customKey) ?: return

            val customRoot = JSONObject(rawCustom)

            val paramIsland = customRoot.optJSONObject("param_island") ?: JSONObject()

            paramIsland.put("dismissIsland", true)

            paramIsland.put("islandTimeout", 1)

            paramIsland.put("needCloseAnimation", false)

            if (!paramIsland.has("bigIslandArea")) {

                paramIsland.put("bigIslandArea", JSONObject())

            }

            paramIsland.remove("smallIslandArea")

            paramIsland.remove("shareData")

            customRoot.put("param_island", paramIsland)

            extras.putString(customKey, customRoot.toString())



            // HyperOS 3 部分版本会额外读取 miui.focus.param.param_v2

            val paramV2 = JSONObject()

            for (key in customRoot.keys()) {

                paramV2.put(key, customRoot.get(key))

            }

            paramV2.put("protocol", 1)

            paramV2.put("enableFloat", false)

            paramV2.put("islandFirstFloat", false)

            extras.putString(

                "miui.focus.param",

                JSONObject().put("param_v2", paramV2).put("protocol", 1).toString()

            )

        } catch (_: Throwable) {

        }

    }



    private fun patchFocusTimeout(extras: Bundle, timeoutSec: Int) {

        try {

            extras.putInt("timeout", timeoutSec)

            val paramKeys = listOf(

                "miui.focus.param",

                "miui.focus.param.custom",

                "miui.focus.params"

            )

            for (key in paramKeys) {

                val raw = extras.getString(key) ?: continue

                if (raw.contains("\"timeout\"")) {

                    extras.putString(

                        key,

                        raw.replace(Regex("\"timeout\"\\s*:\\s*\\d+"), "\"timeout\":$timeoutSec")

                    )

                }

            }

        } catch (_: Throwable) {

        }

    }



    private fun patchFocusOrdering(extras: Bundle) {
        try {
            focusUpdateSequence++
            val sequence = focusUpdateSequence
            val paramKeys = listOf(
                "miui.focus.param",
                "miui.focus.param.custom",
                "miui.focus.params"
            )
            for (key in paramKeys) {
                val raw = extras.getString(key) ?: continue
                var updated = raw
                if (updated.contains("\"sequence\"")) {
                    updated = updated.replace(
                        Regex("\"sequence\"\\s*:\\s*\\d+"),
                        "\"sequence\":$sequence"
                    )
                } else if (updated.contains("\"param_v2\"")) {
                    updated = updated.replace(
                        Regex("\"param_v2\"\\s*:\\s*\\{"),
                        "\"param_v2\":{\"sequence\":$sequence,\"orderId\":\"$FOCUS_ORDER_ID\","
                    )
                }
                if (updated.contains("\"orderId\"")) {
                    updated = updated.replace(
                        Regex("\"orderId\"\\s*:\\s*\"[^\"]*\""),
                        "\"orderId\":\"$FOCUS_ORDER_ID\""
                    )
                } else if (updated.contains("\"param_v2\"")) {
                    updated = updated.replace(
                        Regex("\"param_v2\"\\s*:\\s*\\{"),
                        "\"param_v2\":{\"orderId\":\"$FOCUS_ORDER_ID\","
                    )
                }
                if (!updated.contains("\"business\"") && updated.contains("\"param_v2\"")) {
                    updated = updated.replace(
                        Regex("\"param_v2\"\\s*:\\s*\\{"),
                        "\"param_v2\":{\"business\":\"lyricfocus\","
                    )
                }
                extras.putString(key, updated)
            }
        } catch (_: Throwable) {
        }
    }



    private fun applyMiuiQuietExtras(notification: Notification) {

        try {

            val miuiNotificationClass = Class.forName("android.app.MiuiNotification")

            val miuiNotification = miuiNotificationClass.getDeclaredConstructor().newInstance()

            setMiuiBoolean(miuiNotification, "canFloat", false)

            setMiuiBoolean(miuiNotification, "canShowFloat", false)

            setMiuiBoolean(miuiNotification, "customizedExpandableView", true)

            val extraField = Notification::class.java.getField("extraNotification")

            extraField.isAccessible = true

            extraField.set(notification, miuiNotification)

        } catch (_: Throwable) {

        }

    }



    private fun setMiuiBoolean(target: Any, fieldName: String, value: Boolean) {

        try {

            val field = target.javaClass.getDeclaredField(fieldName)

            field.isAccessible = true

            field.setBoolean(target, value)

        } catch (_: Throwable) {

        }

    }



    private fun getModuleContext(systemContext: Context): Context {

        try {
            return systemContext.createPackageContext(
                MODULE_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (_: Throwable) {
        }
        try {
            val dataDir = java.io.File("/data/data/$MODULE_PACKAGE")
            if (!dataDir.exists()) dataDir.mkdirs()
            return systemContext.createPackageContext(
                MODULE_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (_: Throwable) {
            return systemContext
        }

    }



    private fun circleCropIcon(context: Context, icon: Icon): Icon {

        return try {

            val drawable = icon.loadDrawable(context) ?: return icon

            val size = 128

            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

            val canvas = Canvas(output)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

            drawable.setBounds(0, 0, size, size)

            drawable.draw(canvas)

            Icon.createWithBitmap(output)

        } catch (_: Throwable) {

            icon

        }

    }



    private fun tintIcon(context: Context, icon: Icon, tint: Int): Icon {

        return try {

            val drawable = icon.loadDrawable(context)?.mutate() ?: return icon

            drawable.setTint(tint)

            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)

            val canvas = Canvas(bitmap)

            drawable.setBounds(0, 0, 96, 96)

            drawable.draw(canvas)

            Icon.createWithBitmap(bitmap)

        } catch (_: Throwable) {

            icon

        }

    }

}


