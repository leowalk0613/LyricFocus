package com.lyricnotify.focus.notification

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
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.hyperfocus.api.FocusApi
import com.hyperfocus.api.IslandApi
import com.lyricnotify.focus.R
import org.json.JSONObject

/**
 * 小米 HyperOS 焦点通知 + 超级岛（miui.focus.rv / miui.focus.param.custom）。
 * 对齐 HyperCeiler MusicBaseHook，不使用 Live Update / promoteNotifications。
 */
object HyperFocusLyricStyle {

    const val MODULE_PACKAGE = "com.lyricnotify.focus"
    /** 与 HyperCeiler 焦点歌词渠道一致，便于 SystemUI 识别为焦点通知 */
    const val CHANNEL_ID = "channel_id_focusNotifLyrics"
    private const val TIMEOUT_SEC = 999999
    private const val LYRIC_PRIMARY_SP = 18f
    private const val LYRIC_SECONDARY_SP = 14f
    private const val ISLAND_LYRIC_PRIMARY_SP = 17f
    private const val COLOR_LYRIC_PRIMARY = Color.WHITE
    private const val COLOR_LYRIC_SECONDARY = 0xFFE0E0E0.toInt()

    @Volatile
    private var lastPostedLyric = ""
    @Volatile
    private var lastPostedSecond = ""

    data class FocusContent(
        val songTitle: String,
        val artist: String,
        val lyricText: String,
        val secondLineText: String,
        val musicPackage: String = ""
    )

    enum class RefreshKind {
        /** 主歌词换行：立即 in-place 更新，不 cancel */
        LINE_CHANGE,
        /** 同一句 AOD 保活：续期 rvAod 会话 */
        KEEPALIVE
    }

    /**
     * @param recreateForAod 仅 AOD 换行时为 true（cancel+notify 更新 rvAod 文字）；
     *                       保活续期只用 notify，避免 periodic cancel 导致息屏闪没。
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
        val translation = content.secondLineText.takeIf {
            it.isNotBlank() && it != "\u00A0"
        }
        val secondKey = translation.orEmpty()
        if (!forceRefresh &&
            refreshKind == RefreshKind.LINE_CHANGE &&
            lyric == lastPostedLyric &&
            secondKey == lastPostedSecond
        ) {
            return
        }
        val musicLabel = content.songTitle.ifBlank { content.artist }.ifBlank { "音乐" }

        val lightIcon = NotificationIconHelper.createMusicIcon(moduleContext)
        val darkIcon = tintIcon(moduleContext, lightIcon, Color.BLACK)
        val circularIcon = circleCropIcon(moduleContext, lightIcon)

        val lockViews = buildLyricRemoteViews(moduleContext, R.layout.focus_lyric_lock, lyric, translation)
        val aodViews = buildAodRemoteViews(moduleContext, lyric, translation, lightIcon)

        val iconsBundle = Bundle().apply {
            if (showOnIsland) {
                putParcelable("miui.focus.icon", circularIcon)
                putParcelable("miui.focus.share_icon", circularIcon)
            }
            putParcelable("miui.appIcon", lightIcon)
        }

        val islandTemplate = if (showOnIsland) {
            buildIslandTemplate(lyric, musicLabel, content.musicPackage)
        } else {
            buildDismissIslandTemplate()
        }
        val islandViews = if (showOnIsland) {
            buildLyricRemoteViews(moduleContext, R.layout.focus_lyric_island, lyric, translation)
        } else {
            null
        }

        // 自定义 rvAod（白字）+ cancel/re-notify 刷新息屏；不可与 aodTitle 同传
        // 关闭超级岛时传 dismissIsland 模板，避免 HyperOS 3 用 SystemUI 图标生成默认小岛
        val focusExtras = FocusApi.sendDiyFocus(
            picticker = lightIcon,
            pictickerdark = darkIcon,
            ticker = lyric,
            island = islandTemplate,
            rv = lockViews,
            rvIsLand = islandViews,
            rvAod = aodViews,
            addpics = iconsBundle,
            updatable = true,
            enableFloat = false,
            islandFirstFloat = false,
            timeout = TIMEOUT_SEC,
            isShowNotification = true
        )
        patchFocusTimeout(focusExtras, TIMEOUT_SEC)
        if (!showOnIsland) {
            patchDismissIslandFocusExtras(focusExtras)
        }

        val builder = NotificationCompat.Builder(systemContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(lyric)
            .setContentText(translation ?: content.artist)
            .setSubText(translation ?: content.artist)
            .setTicker(lyric)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(
                if (pinAboveMedia) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setSilent(true)
            .addExtras(focusExtras)
            .setCustomContentView(lockViews)
            .setCustomBigContentView(lockViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        if (pinAboveMedia) {
            builder.setSortKey("\u0000")
            builder.setWhen(lyric.hashCode().toLong())
        }

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
    }

    fun cancelFocusNotification(notificationManager: NotificationManager) {
        resetPostedCache()
        notificationManager.cancel(CHANNEL_ID.hashCode())
    }

    fun resetPostedCache() {
        lastPostedLyric = ""
        lastPostedSecond = ""
    }

    private fun buildDismissIslandTemplate(): JSONObject {
        return IslandApi.IslandTemplate(
            dismissIsland = true,
            islandTimeout = 1,
            needCloseAnimation = false,
            islandOrder = false,
            islandProperty = 1,
            bigIslandArea = JSONObject()
        )
    }

    private fun buildIslandTemplate(lyric: String, musicLabel: String, musicPackage: String): org.json.JSONObject {
        // 固定单块布局，避免长短句切换时左右分岛变形动画
        val displayText = lyric.trim().ifBlank { "\u266A" }
        val shareContent = if (musicPackage.isNotBlank()) {
            "$musicLabel · $displayText"
        } else {
            displayText
        }
        val shareData = IslandApi.shareData(
            title = "歌词",
            content = "LyricFocus",
            pic = "miui.focus.share_icon",
            shareContent = shareContent
        )
        val mainInfo = IslandApi.imageTextInfo(
            picInfo = IslandApi.picInfo(pic = "miui.focus.icon"),
            textInfo = IslandApi.TextInfo(title = displayText)
        )
        return IslandApi.IslandTemplate(
            shareData = shareData,
            highlightColor = "#FFFFFFFF",
            islandTimeout = TIMEOUT_SEC,
            bigIslandArea = IslandApi.bigIslandArea(
                imageTextInfoLeft = mainInfo,
                imageTextInfoRight = null
            ),
            smallIslandArea = IslandApi.SmallIslandArea(
                picInfo = IslandApi.picInfo(pic = "miui.focus.icon")
            )
        )
    }

    private fun buildLyricRemoteViews(
        moduleContext: Context,
        layoutId: Int,
        lyric: String,
        translation: String?
    ): RemoteViews {
        val views = RemoteViews(moduleContext.packageName, layoutId)
        val primarySize = if (layoutId == R.layout.focus_lyric_island) {
            ISLAND_LYRIC_PRIMARY_SP
        } else {
            LYRIC_PRIMARY_SP
        }

        views.setTextViewText(R.id.focuslyric, lyric)
        views.setTextColor(R.id.focuslyric, COLOR_LYRIC_PRIMARY)
        views.setTextViewTextSize(R.id.focuslyric, TypedValue.COMPLEX_UNIT_SP, primarySize)

        if (translation.isNullOrBlank()) {
            views.setViewVisibility(R.id.focustflyric, View.GONE)
        } else {
            views.setTextViewText(R.id.focustflyric, translation)
            views.setTextColor(R.id.focustflyric, COLOR_LYRIC_SECONDARY)
            views.setTextViewTextSize(R.id.focustflyric, TypedValue.COMPLEX_UNIT_SP, LYRIC_SECONDARY_SP)
            views.setViewVisibility(R.id.focustflyric, View.VISIBLE)
        }
        if (layoutId == R.layout.focus_lyric_lock) {
            views.setViewVisibility(R.id.focus_song_title, View.GONE)
        }
        return views
    }

    private fun buildAodRemoteViews(
        moduleContext: Context,
        lyric: String,
        translation: String?,
        icon: Icon
    ): RemoteViews {
        val views = RemoteViews(moduleContext.packageName, R.layout.focus_lyric_aod)
        views.setImageViewIcon(R.id.focusicon, icon)
        views.setTextViewText(R.id.focuslyric, lyric)
        views.setTextColor(R.id.focuslyric, COLOR_LYRIC_PRIMARY)
        views.setTextViewTextSize(R.id.focuslyric, TypedValue.COMPLEX_UNIT_SP, LYRIC_PRIMARY_SP)
        if (translation.isNullOrBlank()) {
            views.setViewVisibility(R.id.focustflyric, View.GONE)
        } else {
            views.setTextViewText(R.id.focustflyric, translation)
            views.setTextColor(R.id.focustflyric, COLOR_LYRIC_SECONDARY)
            views.setTextViewTextSize(R.id.focustflyric, TypedValue.COMPLEX_UNIT_SP, LYRIC_SECONDARY_SP)
            views.setViewVisibility(R.id.focustflyric, View.VISIBLE)
        }
        return views
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
        return systemContext.createPackageContext(
            MODULE_PACKAGE,
            Context.CONTEXT_IGNORE_SECURITY
        )
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
