package com.leowalk.LyricFocus

import android.content.Context
import android.graphics.Color

object FocusPreferences {

    const val MODULE_PACKAGE = "com.leowalk.LyricFocus"
    const val PREFS_NAME = "lyric_focus_prefs"
    const val PREF_FOCUS_ENABLED = "focus_lyric_enabled"
    const val PREF_SHOW_IN_SHADE = "show_in_notification_shade"
    const val PREF_PIN_ABOVE_MEDIA = "pin_above_media_controls"
    const val PREF_SHOW_ON_ISLAND = "show_on_super_island"
    const val PREF_AOD_KEEPALIVE_SEC = "aod_keepalive_sec"
    const val PREF_SYNC_ADVANCE_MS = "sync_advance_ms"
    const val PREF_APP_WHITELIST_ENABLED = "app_whitelist_enabled"
    const val PREF_APP_WHITELIST_PACKAGES = "app_whitelist_packages"
    const val PREF_LYRIC_SOURCE = "lyric_source"

    const val PREF_LYRIC_TEXT_SIZE = "lyric_text_size"
    const val PREF_LYRIC_TEXT_COLOR = "lyric_text_color"
    const val PREF_LYRIC_MAX_LINES = "lyric_max_lines"
    const val PREF_TRANSLATION_MAX_LINES = "translation_max_lines"
    const val PREF_LYRIC_GRAVITY = "lyric_gravity"
    const val PREF_FOCUS_BACKGROUND = "focus_background"
    const val PREF_LYRIC_COLOR_EXTRACTION = "lyric_color_extraction"
    const val PREF_MONET_DYNAMIC_COLOR = "monet_dynamic_color"
    const val PREF_EXTRACTED_TEXT_COLOR = "extracted_text_color"
    const val PREF_EXTRACTED_BG_COLOR = "extracted_bg_color"
    const val PREF_TEXT_STROKE_ENABLED = "text_stroke_enabled"
    const val PREF_TEXT_STROKE_WIDTH = "text_stroke_width"
    const val PREF_TEXT_STROKE_COLOR_MODE = "text_stroke_color_mode"

    const val TEXT_COLOR_BLACK = "black"
    const val TEXT_COLOR_WHITE = "white"

    const val STROKE_COLOR_AUTO = "auto"
    const val STROKE_COLOR_BLACK = "black"
    const val STROKE_COLOR_WHITE = "white"

    const val GRAVITY_LEFT = "left"
    const val GRAVITY_CENTER = "center"
    const val GRAVITY_RIGHT = "right"

    const val BACKGROUND_DEFAULT = "default"
    const val BACKGROUND_BLACK = "black"
    const val BACKGROUND_WHITE = "white"

    const val LYRIC_SOURCE_AUTO = "auto"
    const val LYRIC_SOURCE_NETEASE = "netease"
    const val LYRIC_SOURCE_QQ = "qq"

    const val ACTION_SETTINGS_CHANGED = "com.leowalk.LyricFocus.action.SETTINGS_CHANGED"
    /** SystemUI 重启后请求 App 重推当前歌词/焦点状态 */
    const val ACTION_REQUEST_RESYNC = "com.leowalk.LyricFocus.action.REQUEST_RESYNC"
    const val EXTRA_FOCUS_ENABLED = "focus_enabled"
    const val EXTRA_SHOW_IN_SHADE = "show_in_shade"
    const val EXTRA_PIN_ABOVE_MEDIA = "pin_above_media"
    const val EXTRA_SHOW_ON_ISLAND = "show_on_island"
    const val EXTRA_AOD_KEEPALIVE_SEC = "aod_keepalive_sec"
    const val EXTRA_SYNC_ADVANCE_MS = "sync_advance_ms"
    const val EXTRA_APP_WHITELIST_ENABLED = "app_whitelist_enabled"
    const val EXTRA_LYRIC_SOURCE = "lyric_source"

    const val DEFAULT_AOD_KEEPALIVE_SEC = 9
    const val MIN_AOD_KEEPALIVE_SEC = 3
    const val MAX_AOD_KEEPALIVE_SEC = 20
    /** HyperOS 焦点通知 updatable 会话约 10s 超时，实际保活间隔不会超过此值 */
    const val SYSTEM_FOCUS_MAX_KEEPALIVE_SEC = 9

    const val DEFAULT_SYNC_ADVANCE_MS = 200L
    const val MIN_SYNC_ADVANCE_MS = -1000L
    const val MAX_SYNC_ADVANCE_MS = 3000L

    const val DEFAULT_LYRIC_TEXT_SIZE_SP = 18f
    const val MIN_LYRIC_TEXT_SIZE_SP = 12f
    const val MAX_LYRIC_TEXT_SIZE_SP = 32f

    const val DEFAULT_LYRIC_MAX_LINES = 2
    const val DEFAULT_TRANSLATION_MAX_LINES = 1

    const val DEFAULT_TEXT_STROKE_WIDTH = 3f
    const val MIN_TEXT_STROKE_WIDTH = 0f
    const val MAX_TEXT_STROKE_WIDTH = 10f

    fun defaultMusicPackages(): Set<String> = linkedSetOf(
        "com.netease.cloudmusic",
        "com.tencent.qqmusic",
        "com.luna.music",
        "com.miui.player",
        "com.kugou.android",
        "com.kuwo.kwmusiccar",
        "cn.kuwo.player",
        "com.apple.android.music",
        "com.google.android.apps.youtube.music",
        "com.spotify.music"
    )

    fun isAppWhitelistEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_APP_WHITELIST_ENABLED, false)
    }

    fun setAppWhitelistEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_APP_WHITELIST_ENABLED, enabled)
            .apply()
    }

    fun getWhitelistedPackages(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(PREF_APP_WHITELIST_PACKAGES, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    fun setWhitelistedPackages(context: Context, packages: Collection<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PREF_APP_WHITELIST_PACKAGES, packages.toSet())
            .apply()
    }

    fun getLyricSource(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_LYRIC_SOURCE, LYRIC_SOURCE_AUTO)
            ?: LYRIC_SOURCE_AUTO
    }

    fun setLyricSource(context: Context, source: String) {
        val normalized = when (source) {
            LYRIC_SOURCE_NETEASE, LYRIC_SOURCE_QQ -> source
            else -> LYRIC_SOURCE_AUTO
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LYRIC_SOURCE, normalized)
            .apply()
    }

    fun formatLyricSourceLabel(source: String): String {
        return when (source) {
            LYRIC_SOURCE_NETEASE -> "网易云音乐"
            LYRIC_SOURCE_QQ -> "QQ音乐"
            else -> "自动（网易云 → QQ音乐）"
        }
    }

    fun lyricSourceOptions(): List<Pair<String, String>> = listOf(
        LYRIC_SOURCE_AUTO to formatLyricSourceLabel(LYRIC_SOURCE_AUTO),
        LYRIC_SOURCE_NETEASE to formatLyricSourceLabel(LYRIC_SOURCE_NETEASE),
        LYRIC_SOURCE_QQ to formatLyricSourceLabel(LYRIC_SOURCE_QQ)
    )

    fun isPackageAllowed(context: Context, packageName: String): Boolean {
        if (!isAppWhitelistEnabled(context)) {
            return true
        }
        if (packageName.isBlank()) {
            return false
        }
        return getWhitelistedPackages(context).contains(packageName)
    }

    fun isFocusEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_FOCUS_ENABLED, true)
    }

    fun setFocusEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_FOCUS_ENABLED, enabled)
            .apply()
    }

    fun isShowInShade(context: Context): Boolean = false

    fun setShowInShade(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SHOW_IN_SHADE, false)
            .apply()
    }

    fun isPinAboveMedia(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_PIN_ABOVE_MEDIA, false)
    }

    fun setPinAboveMedia(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_PIN_ABOVE_MEDIA, enabled)
            .apply()
    }

    fun isShowOnIsland(context: Context): Boolean = false

    fun setShowOnIsland(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SHOW_ON_ISLAND, false)
            .apply()
    }

    fun getAodKeepaliveSec(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_AOD_KEEPALIVE_SEC, DEFAULT_AOD_KEEPALIVE_SEC)
            .coerceIn(MIN_AOD_KEEPALIVE_SEC, MAX_AOD_KEEPALIVE_SEC)
    }

    fun getEffectiveKeepaliveSec(context: Context): Int {
        return getAodKeepaliveSec(context).coerceAtMost(SYSTEM_FOCUS_MAX_KEEPALIVE_SEC)
    }

    fun getEffectiveKeepaliveMs(context: Context): Long {
        return getEffectiveKeepaliveSec(context) * 1000L
    }

    fun setAodKeepaliveSec(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                PREF_AOD_KEEPALIVE_SEC,
                seconds.coerceIn(MIN_AOD_KEEPALIVE_SEC, MAX_AOD_KEEPALIVE_SEC)
            )
            .apply()
    }

    fun formatAodKeepaliveLabel(seconds: Int): String {
        val effective = seconds.coerceAtMost(SYSTEM_FOCUS_MAX_KEEPALIVE_SEC)
        return if (seconds > effective) {
            "${seconds}s（实际 ${effective}s）"
        } else {
            "${seconds}s"
        }
    }

    fun getSyncAdvanceMs(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_SYNC_ADVANCE_MS, DEFAULT_SYNC_ADVANCE_MS)
            .coerceIn(MIN_SYNC_ADVANCE_MS, MAX_SYNC_ADVANCE_MS)
    }

    fun setSyncAdvanceMs(context: Context, advanceMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(
                PREF_SYNC_ADVANCE_MS,
                advanceMs.coerceIn(MIN_SYNC_ADVANCE_MS, MAX_SYNC_ADVANCE_MS)
            )
            .apply()
    }

    fun formatSyncAdvanceLabel(advanceMs: Long): String {
        return when {
            advanceMs > 0 -> "提前 ${advanceMs} ms"
            advanceMs < 0 -> "延后 ${-advanceMs} ms"
            else -> "无偏移"
        }
    }

    fun readFocusEnabled(context: Context): Boolean {
        return readFromModule(context) { isFocusEnabled(it) } ?: true
    }

    fun readShowInShade(context: Context): Boolean = false

    fun readPinAboveMedia(context: Context): Boolean {
        return readFromModule(context) { isPinAboveMedia(it) } ?: false
    }

    fun readShowOnIsland(context: Context): Boolean = false

    fun readAodKeepaliveSec(context: Context): Int {
        return readFromModule(context) { getAodKeepaliveSec(it) } ?: DEFAULT_AOD_KEEPALIVE_SEC
    }

    fun readEffectiveKeepaliveMs(context: Context): Long {
        return readFromModule(context) { getEffectiveKeepaliveMs(it) }
            ?: (DEFAULT_AOD_KEEPALIVE_SEC * 1000L)
    }

    fun readSyncAdvanceMs(context: Context): Long {
        return readFromModule(context) { getSyncAdvanceMs(it) } ?: DEFAULT_SYNC_ADVANCE_MS
    }

    fun readAppWhitelistEnabled(context: Context): Boolean {
        return readFromModule(context) { isAppWhitelistEnabled(it) } ?: false
    }

    fun readIsPackageAllowed(context: Context, packageName: String): Boolean {
        return readFromModule(context) { isPackageAllowed(it, packageName) } ?: true
    }

    private inline fun <T> readFromModule(
        context: Context,
        reader: (Context) -> T
    ): T? {
        return try {
            val appContext = context.createPackageContext(
                MODULE_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
            )
            reader(appContext)
        } catch (_: Throwable) {
            null
        }
    }

    fun getLyricTextSize(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(PREF_LYRIC_TEXT_SIZE, DEFAULT_LYRIC_TEXT_SIZE_SP)
            .coerceIn(MIN_LYRIC_TEXT_SIZE_SP, MAX_LYRIC_TEXT_SIZE_SP)
    }

    fun setLyricTextSize(context: Context, sizeSp: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(PREF_LYRIC_TEXT_SIZE, sizeSp.coerceIn(MIN_LYRIC_TEXT_SIZE_SP, MAX_LYRIC_TEXT_SIZE_SP))
            .commit()
    }

    fun getLyricTextColor(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_LYRIC_TEXT_COLOR, TEXT_COLOR_WHITE)
            ?: TEXT_COLOR_WHITE
    }

    fun setLyricTextColor(context: Context, color: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LYRIC_TEXT_COLOR, color)
            .commit()
    }

    fun getLyricMaxLines(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_LYRIC_MAX_LINES, DEFAULT_LYRIC_MAX_LINES)
            .coerceIn(1, 3)
    }

    fun setLyricMaxLines(context: Context, lines: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_LYRIC_MAX_LINES, lines.coerceIn(1, 3))
            .commit()
    }

    fun getTranslationMaxLines(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_TRANSLATION_MAX_LINES, DEFAULT_TRANSLATION_MAX_LINES)
            .coerceIn(1, 3)
    }

    fun setTranslationMaxLines(context: Context, lines: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_TRANSLATION_MAX_LINES, lines.coerceIn(1, 3))
            .commit()
    }

    fun getLyricGravity(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_LYRIC_GRAVITY, GRAVITY_CENTER)
            ?: GRAVITY_CENTER
    }

    fun setLyricGravity(context: Context, gravity: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LYRIC_GRAVITY, gravity)
            .commit()
    }

    fun getFocusBackground(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_FOCUS_BACKGROUND, BACKGROUND_DEFAULT)
            ?: BACKGROUND_DEFAULT
    }

    fun setFocusBackground(context: Context, background: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_FOCUS_BACKGROUND, background)
            .commit()
    }

    fun isTextColorExtractionEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_LYRIC_COLOR_EXTRACTION, false)
    }

    fun setTextColorExtractionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_LYRIC_COLOR_EXTRACTION, enabled)
            .commit()
    }

    fun isMonetDynamicColorEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_MONET_DYNAMIC_COLOR, false)
    }

    fun setMonetDynamicColorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_MONET_DYNAMIC_COLOR, enabled)
            .commit()
    }

    fun isAlbumColorExtractionActive(context: Context): Boolean {
        return isMonetDynamicColorEnabled(context) || isTextColorExtractionEnabled(context)
    }

    fun isColorExtractionEnabled(context: Context): Boolean = isAlbumColorExtractionActive(context)

    fun setColorExtractionEnabled(context: Context, enabled: Boolean) {
        setTextColorExtractionEnabled(context, enabled)
    }

    fun readLyricTextSize(context: Context): Float {
        return readFromModule(context) { getLyricTextSize(it) } ?: DEFAULT_LYRIC_TEXT_SIZE_SP
    }

    fun readLyricTextColor(context: Context): String {
        return readFromModule(context) { getLyricTextColor(it) } ?: TEXT_COLOR_WHITE
    }

    fun readLyricMaxLines(context: Context): Int {
        return readFromModule(context) { getLyricMaxLines(it) } ?: DEFAULT_LYRIC_MAX_LINES
    }

    fun readTranslationMaxLines(context: Context): Int {
        return readFromModule(context) { getTranslationMaxLines(it) } ?: DEFAULT_TRANSLATION_MAX_LINES
    }

    fun readLyricGravity(context: Context): String {
        return readFromModule(context) { getLyricGravity(it) } ?: GRAVITY_CENTER
    }

    fun readFocusBackground(context: Context): String {
        return readFromModule(context) { getFocusBackground(it) } ?: BACKGROUND_DEFAULT
    }

    fun readColorExtractionEnabled(context: Context): Boolean {
        return readFromModule(context) { isColorExtractionEnabled(it) } ?: false
    }

    fun getExtractedTextColor(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_EXTRACTED_TEXT_COLOR)) return null
        return prefs.getInt(PREF_EXTRACTED_TEXT_COLOR, Color.WHITE)
    }

    fun getExtractedBgColor(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_EXTRACTED_BG_COLOR)) return null
        return prefs.getInt(PREF_EXTRACTED_BG_COLOR, Color.GRAY)
    }

    fun setExtractedColors(context: Context, accent: Int, backgroundEstimate: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_EXTRACTED_TEXT_COLOR, accent)
            .putInt(PREF_EXTRACTED_BG_COLOR, backgroundEstimate)
            .commit()
    }

    fun setExtractedMonetScheme(context: Context, scheme: com.leowalk.LyricFocus.util.AlbumColorExtractor.MonetScheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_EXTRACTED_TEXT_COLOR, scheme.primaryText)
            .putInt(PREF_EXTRACTED_BG_COLOR, scheme.background)
            .commit()
    }

    fun setExtractedTextColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_EXTRACTED_TEXT_COLOR, color)
            .commit()
    }

    fun clearExtractedTextColor(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_EXTRACTED_TEXT_COLOR)
            .remove(PREF_EXTRACTED_BG_COLOR)
            .commit()
    }

    fun isTextStrokeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_TEXT_STROKE_ENABLED, false)
    }

    fun setTextStrokeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_TEXT_STROKE_ENABLED, enabled)
            .commit()
    }

    fun getTextStrokeWidth(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(PREF_TEXT_STROKE_WIDTH, DEFAULT_TEXT_STROKE_WIDTH)
            .coerceIn(MIN_TEXT_STROKE_WIDTH, MAX_TEXT_STROKE_WIDTH)
    }

    fun setTextStrokeWidth(context: Context, width: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(PREF_TEXT_STROKE_WIDTH, width.coerceIn(MIN_TEXT_STROKE_WIDTH, MAX_TEXT_STROKE_WIDTH))
            .commit()
    }

    fun getTextStrokeColorMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_TEXT_STROKE_COLOR_MODE, STROKE_COLOR_AUTO)
            ?: STROKE_COLOR_AUTO
    }

    fun setTextStrokeColorMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_TEXT_STROKE_COLOR_MODE, mode)
            .commit()
    }

    fun readExtractedTextColor(context: Context): Int? {
        return readFromModule(context) { getExtractedTextColor(it) }
    }

    fun fillStyleExtras(intent: android.content.Intent, context: Context) {
        val monet = isMonetDynamicColorEnabled(context)
        val textExtraction = isTextColorExtractionEnabled(context)
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_MONET_DYNAMIC_COLOR, monet)
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_COLOR_EXTRACTION, textExtraction)
        if (!monet && !textExtraction) return
        val color = getExtractedTextColor(context)
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_COLOR_SET, color != null)
        if (color != null) {
            intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_COLOR, color)
            getExtractedBgColor(context)?.let {
                intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_BG_COLOR_SET, true)
                intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_BG_COLOR, it)
            }
        }
    }

    fun notifySettingsChanged(context: Context) {
        try {
            val intent = android.content.Intent(ACTION_SETTINGS_CHANGED)
            context.sendBroadcast(android.content.Intent(intent).setPackage("com.android.systemui"))
            context.sendBroadcast(android.content.Intent(intent).setPackage(context.packageName))
        } catch (_: Exception) {
        }
    }

    fun notifyStyleSettingsChanged(context: Context) {
        try {
            val intent = android.content.Intent(ACTION_SETTINGS_CHANGED).apply {
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_CHANGED, true)
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_TEXT_SIZE, getLyricTextSize(context))
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_TEXT_COLOR, getLyricTextColor(context))
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_LYRIC_MAX_LINES, getLyricMaxLines(context))
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_TRANSLATION_MAX_LINES,
                    getTranslationMaxLines(context)
                )
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_GRAVITY, getLyricGravity(context))
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_BACKGROUND, getFocusBackground(context))
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_MONET_DYNAMIC_COLOR,
                    isMonetDynamicColorEnabled(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_COLOR_EXTRACTION,
                    isTextColorExtractionEnabled(context)
                )
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_STROKE_ENABLED, isTextStrokeEnabled(context))
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_STROKE_WIDTH, getTextStrokeWidth(context))
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_STROKE_COLOR_MODE, getTextStrokeColorMode(context))
                val extracted = getExtractedTextColor(context)
                putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_COLOR_SET, extracted != null)
                if (extracted != null) {
                    putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_COLOR, extracted)
                    getExtractedBgColor(context)?.let { bg ->
                        putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_BG_COLOR_SET, true)
                        putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_BG_COLOR, bg)
                    }
                }
            }
            context.sendBroadcast(android.content.Intent(intent).setPackage("com.android.systemui"))
            context.sendBroadcast(android.content.Intent(intent).setPackage(context.packageName))
        } catch (_: Exception) {
        }
    }
}
