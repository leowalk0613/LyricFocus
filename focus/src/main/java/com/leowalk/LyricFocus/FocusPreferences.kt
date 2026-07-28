package com.leowalk.LyricFocus

import android.content.Context
import android.graphics.Color

object FocusPreferences {

    const val MODULE_PACKAGE = "com.leowalk.LyricFocus"
    const val PREFS_NAME = "lyric_focus_prefs"
    const val PREF_WELCOME_COMPLETED = "welcome_completed"
    const val PREF_FOCUS_ENABLED = "focus_lyric_enabled"
    const val PREF_SHOW_IN_SHADE = "show_in_notification_shade"
    const val PREF_PIN_ABOVE_MEDIA = "pin_above_media_controls"
    const val PREF_SHOW_ON_ISLAND = "show_on_super_island"
    const val PREF_AOD_KEEPALIVE_SEC = "aod_keepalive_sec"
    /** 万象息屏（自定义）AOD：使用横向 rvAod 布局，默认关（锁屏样式 AOD 保持竖排布局） */
    const val PREF_CUSTOM_AOD_LAYOUT = "custom_aod_layout"
    /** 全局：歌词与翻译位置互换 */
    const val PREF_SWAP_LYRIC_TRANSLATION = "swap_lyric_translation"
    /** 全局：仅显示第一行（隐藏第二行翻译/歌词） */
    const val PREF_SINGLE_LINE_ONLY = "single_line_only"
    /** 锁屏样式 AOD：多行模式（按页显示原文；有翻译时交错填满所选行数） */
    const val PREF_MULTI_LINE_LYRICS = "multi_line_lyrics"
    /** 多行模式下是否显示翻译（有翻译时交错显示原文与翻译） */
    const val PREF_MULTI_LINE_SHOW_TRANSLATION = "multi_line_show_translation"
    /** 多行模式一页行数：4~8 */
    const val PREF_MULTI_LINE_LINE_COUNT = "multi_line_line_count"
    /** 仅 AOD 显示多行歌词，锁屏保持双行 */
    const val PREF_AOD_MULTI_LINE_ONLY = "aod_multi_line_only"
    /** 多行歌词独立字号（原文/翻译统一） */
    const val PREF_MULTI_LINE_TEXT_SIZE = "multi_line_text_size"
    /** 万象息屏 AOD 独立样式 */
    const val PREF_CUSTOM_AOD_TEXT_SIZE = "custom_aod_text_size"
    const val PREF_CUSTOM_AOD_LYRIC_WIDTH = "custom_aod_lyric_width"
    const val PREF_CUSTOM_AOD_LYRIC_MAX_LINES = "custom_aod_lyric_max_lines"
    const val PREF_CUSTOM_AOD_TRANSLATION_MAX_LINES = "custom_aod_translation_max_lines"
    const val PREF_CUSTOM_AOD_COLOR_MODE = "custom_aod_color_mode"
    const val PREF_CUSTOM_AOD_PRESET_COLOR = "custom_aod_preset_color"
    const val PREF_CUSTOM_AOD_GRAVITY = "custom_aod_gravity"
    const val PREF_CUSTOM_AOD_SONG_INFO = "custom_aod_song_info"
    const val PREF_CUSTOM_AOD_TITLE_ICON_ENABLED = "custom_aod_title_icon_enabled"
    const val PREF_CUSTOM_AOD_TITLE_ICON_PRESET = "custom_aod_title_icon_preset"
    const val PREF_CUSTOM_AOD_TITLE_ICON_SIZE = "custom_aod_title_icon_size"
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
    const val PREF_MONET_BG_ONLY = "monet_bg_only"
    const val PREF_COLOR_MODE = "color_mode"
    const val PREF_EXTRACTED_TEXT_COLOR = "extracted_text_color"
    const val PREF_EXTRACTED_BG_COLOR = "extracted_bg_color"
    const val PREF_EXTRACTED_ACCENT_COLOR = "extracted_accent_color"
    const val PREF_EXTRACTED_COLOR_OPACITY = "extracted_color_opacity"
    const val PREF_LYRIC_TEXT_PRESET_COLOR = "lyric_text_preset_color"
    const val PREF_LYRIC_CUSTOM_COLOR = "lyric_custom_color"
    const val PREF_FOCUS_BG_CUSTOM_COLOR = "focus_bg_custom_color"
    const val PREF_CUSTOM_AOD_CUSTOM_COLOR = "custom_aod_custom_color"

    const val TEXT_COLOR_BLACK = "black"
    const val TEXT_COLOR_WHITE = "white"
    const val TEXT_COLOR_PRESET = "preset"
    const val TEXT_COLOR_CUSTOM = "custom"

    const val GRAVITY_LEFT = "left"
    const val GRAVITY_CENTER = "center"
    const val GRAVITY_RIGHT = "right"

    const val BACKGROUND_DEFAULT = "default"
    const val BACKGROUND_BLACK = "black"
    const val BACKGROUND_WHITE = "white"
    const val BACKGROUND_CUSTOM = "custom"

    const val LYRIC_SOURCE_AUTO = "auto"
    const val LYRIC_SOURCE_NETEASE = "netease"
    const val LYRIC_SOURCE_QQ = "qq"
    const val LYRIC_SOURCE_LOCAL = "local"
    const val LYRIC_SOURCE_AI = "ai"
    const val LYRIC_SOURCE_SUPERLYRIC = "superlyric"
    const val LYRIC_SOURCE_LYRICON = "lyricon"
    const val LYRIC_SOURCE_LYRICINFO = "lyricinfo"
    const val LYRIC_SOURCE_LRCLIB = "lrclib"

    const val PREF_LOCAL_LRC_DIR = "local_lrc_dir"
    const val PREF_LOCAL_LRC_TREE_URI = "local_lrc_tree_uri"
    const val PREF_AI_API_BASE_URL = "ai_api_base_url"
    const val PREF_AI_API_KEY = "ai_api_key"
    const val PREF_AI_API_MODEL = "ai_api_model"
    const val PREF_AI_TARGET_LANGUAGE = "ai_target_language"
    const val PREF_AI_TRANSLATE_ALL_LYRICS = "ai_translate_all_lyrics"
    const val PREF_AI_LYRIC_ENABLED = "ai_lyric_enabled"
    const val PREF_AI_TOTAL_TOKENS = "ai_total_tokens"
    const val PREF_AI_POLISH_ENABLED = "ai_polish_enabled"
    const val PREF_AI_TRANSLATE_ENABLED = "ai_translate_enabled"
    const val PREF_AI_CACHE_ENABLED = "ai_cache_enabled"
    const val PREF_AI_CACHE_SIZE = "ai_cache_size_bytes"
    const val PREF_LOCAL_LRC_BOOTSTRAPPED = "local_lrc_bootstrapped"
    const val PREF_HIDE_DESKTOP_ICON = "hide_desktop_icon"

    const val DEFAULT_LOCAL_LRC_DIR = "/sdcard/LyricFocus/lyrics"
    const val DEFAULT_AI_API_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_AI_API_MODEL = "gpt-4o-mini"
    const val DEFAULT_AI_TARGET_LANGUAGE = "简体中文"

    const val CUSTOM_AOD_COLOR_WHITE = "white"
    const val CUSTOM_AOD_COLOR_ALBUM = "album"
    const val CUSTOM_AOD_COLOR_PRESET = "preset"
    const val CUSTOM_AOD_COLOR_CUSTOM = "custom"

    const val CUSTOM_AOD_SONG_INFO_ALL = "all"
    const val CUSTOM_AOD_SONG_INFO_HIDE_TITLE = "hide_title"
    const val CUSTOM_AOD_SONG_INFO_HIDE_ARTIST = "hide_artist"
    const val CUSTOM_AOD_SONG_INFO_HIDE_ALL = "hide_all"

    const val CUSTOM_AOD_TITLE_ICON_AUTO = "auto"
    const val CUSTOM_AOD_TITLE_ICON_MUSIC_NOTE = "music_note"
    const val CUSTOM_AOD_TITLE_ICON_NETEASE = "netease"
    const val CUSTOM_AOD_TITLE_ICON_QQ = "qq"
    const val CUSTOM_AOD_TITLE_ICON_KUGOU = "kugou"
    const val CUSTOM_AOD_TITLE_ICON_KUWO = "kuwo"
    const val CUSTOM_AOD_TITLE_ICON_QISHUI = "qishui"
    const val CUSTOM_AOD_TITLE_ICON_BODIAN = "bodian"
    const val CUSTOM_AOD_TITLE_ICON_SPOTIFY = "spotify"
    const val CUSTOM_AOD_TITLE_ICON_APPLE = "apple"

    const val DEFAULT_CUSTOM_AOD_TITLE_ICON_SIZE = 150
    const val MIN_CUSTOM_AOD_TITLE_ICON_SIZE = 75
    const val MAX_CUSTOM_AOD_TITLE_ICON_SIZE = 200

    const val DEFAULT_CUSTOM_AOD_LYRIC_WIDTH = 100
    const val MIN_CUSTOM_AOD_LYRIC_WIDTH = 50
    const val MAX_CUSTOM_AOD_LYRIC_WIDTH = 100

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
    const val DEFAULT_MULTI_LINE_TEXT_SIZE_SP = 14f

    const val DEFAULT_MULTI_LINE_LINE_COUNT = 8
    const val MIN_MULTI_LINE_COUNT = 3
    const val MAX_MULTI_LINE_COUNT = 8

    fun coerceMultiLineLineCount(lines: Int): Int {
        return lines.coerceIn(MIN_MULTI_LINE_COUNT, MAX_MULTI_LINE_COUNT)
    }
    /** 3->4, 4->4, 5->6, 6->6, 7->8, 8->8 */
    fun multiLinePageSlots(lines: Int): Int {
        return ((lines + 1) / 2 * 2).coerceIn(4, 8)
    }

    const val MIN_LYRIC_TEXT_SIZE_SP = 12f
    const val MAX_LYRIC_TEXT_SIZE_SP = 32f

    const val DEFAULT_LYRIC_MAX_LINES = 2
    const val DEFAULT_TRANSLATION_MAX_LINES = 1

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
            LYRIC_SOURCE_NETEASE,
            LYRIC_SOURCE_QQ,
            LYRIC_SOURCE_LOCAL,
            LYRIC_SOURCE_AI,
            LYRIC_SOURCE_SUPERLYRIC,
            LYRIC_SOURCE_LYRICON,
            LYRIC_SOURCE_LYRICINFO,
            LYRIC_SOURCE_LRCLIB -> source
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
            LYRIC_SOURCE_LOCAL -> "本地 LRC 文件"
            LYRIC_SOURCE_AI -> "AI 翻译（在线 + 翻译）"
            LYRIC_SOURCE_SUPERLYRIC -> "Super Lyric"
            LYRIC_SOURCE_LYRICON -> "词幕 Lyricon"
            LYRIC_SOURCE_LYRICON -> "词幕 Lyricon"
            LYRIC_SOURCE_LYRICINFO -> "LyricInfo"
            LYRIC_SOURCE_LRCLIB -> "LRCLib（海外）"
            else -> "自动（QQ确认 → 网易翻译）"
        }
    }

    fun lyricSourceOptions(): List<Pair<String, String>> = listOf(
        LYRIC_SOURCE_AUTO to formatLyricSourceLabel(LYRIC_SOURCE_AUTO),
        LYRIC_SOURCE_NETEASE to formatLyricSourceLabel(LYRIC_SOURCE_NETEASE),
        LYRIC_SOURCE_QQ to formatLyricSourceLabel(LYRIC_SOURCE_QQ),
        LYRIC_SOURCE_LRCLIB to formatLyricSourceLabel(LYRIC_SOURCE_LRCLIB),
        LYRIC_SOURCE_SUPERLYRIC to formatLyricSourceLabel(LYRIC_SOURCE_SUPERLYRIC),
        LYRIC_SOURCE_LYRICON to formatLyricSourceLabel(LYRIC_SOURCE_LYRICON),
        LYRIC_SOURCE_LYRICINFO to formatLyricSourceLabel(LYRIC_SOURCE_LYRICINFO),
        LYRIC_SOURCE_LOCAL to formatLyricSourceLabel(LYRIC_SOURCE_LOCAL)
    )

    fun getLocalLrcDirectory(context: Context): String {
        return getDefaultLocalLrcDirectoryFile(context).absolutePath
    }

    fun getDefaultLocalLrcDirectoryFile(context: Context): java.io.File {
        return java.io.File(context.getExternalFilesDir(null), "lyrics")
    }

    fun getLocalLrcTreeUri(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_LOCAL_LRC_TREE_URI, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun setLocalLrcTreeUri(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (uri.isNullOrBlank()) {
                    remove(PREF_LOCAL_LRC_TREE_URI)
                } else {
                    putString(PREF_LOCAL_LRC_TREE_URI, uri.trim())
                }
            }
            .apply()
    }

    fun clearLocalLrcTreeUri(context: Context) {
        setLocalLrcTreeUri(context, null)
    }

    fun getLocalLrcLocationLabel(context: Context): String {
        return com.leowalk.LyricFocus.lyric.LocalLrcStore.getLocationLabel(context)
    }

    @Deprecated("Use folder picker + LocalLrcStore", ReplaceWith("setLocalLrcTreeUri(context, null)"))
    fun setLocalLrcDirectory(context: Context, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LOCAL_LRC_DIR, path.trim())
            .apply()
    }

    fun getAiApiBaseUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_AI_API_BASE_URL, DEFAULT_AI_API_BASE_URL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_AI_API_BASE_URL
    }

    fun setAiApiBaseUrl(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_AI_API_BASE_URL, value.trim())
            .apply()
    }

    fun getAiApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_AI_API_KEY, "")
            ?.trim()
            .orEmpty()
    }

    fun setAiApiKey(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_AI_API_KEY, value.trim())
            .apply()
    }

    fun getAiApiModel(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_AI_API_MODEL, DEFAULT_AI_API_MODEL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_AI_API_MODEL
    }

    fun setAiApiModel(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_AI_API_MODEL, value.trim())
            .apply()
    }

    fun getAiTargetLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_AI_TARGET_LANGUAGE, DEFAULT_AI_TARGET_LANGUAGE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_AI_TARGET_LANGUAGE
    }

    fun setAiTargetLanguage(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_AI_TARGET_LANGUAGE, value.trim())
            .apply()
    }

    fun isAiTranslateAllLyrics(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AI_TRANSLATE_ALL_LYRICS, false)
    }

    fun setAiTranslateAllLyrics(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AI_TRANSLATE_ALL_LYRICS, enabled)
            .apply()
    }

    fun isAiLyricEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AI_LYRIC_ENABLED, false)
    }

    fun setAiLyricEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AI_LYRIC_ENABLED, enabled)
            .apply()
    }

    fun getAiTotalTokens(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_AI_TOTAL_TOKENS, 0L)
    }

    fun addAiTokens(context: Context, tokens: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(PREF_AI_TOTAL_TOKENS, prefs.getLong(PREF_AI_TOTAL_TOKENS, 0L) + tokens)
            .apply()
    }

    fun isAiPolishEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AI_POLISH_ENABLED, false)
    }

    fun setAiPolishEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AI_POLISH_ENABLED, enabled)
            .apply()
    }

    fun isAiTranslateEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AI_TRANSLATE_ENABLED, false)
    }

    fun setAiTranslateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AI_TRANSLATE_ENABLED, enabled)
            .apply()
    }

    fun isAiCacheEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AI_CACHE_ENABLED, true)
    }

    fun setAiCacheEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AI_CACHE_ENABLED, enabled)
            .apply()
    }

    fun getAiCacheSizeBytes(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_AI_CACHE_SIZE, 0L)
    }

    fun setAiCacheSizeBytes(context: Context, bytes: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_AI_CACHE_SIZE, bytes)
            .apply()
    }

    fun isHideDesktopIcon(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_HIDE_DESKTOP_ICON, false)
    }

    fun setHideDesktopIcon(context: Context, hide: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_HIDE_DESKTOP_ICON, hide)
            .apply()
        applyDesktopIconVisibility(context, hide)
    }

    fun applyDesktopIconVisibility(context: Context, hide: Boolean) {
        try {
            val aliasComponent = android.content.ComponentName(
                context.packageName,
                "${context.packageName}.LauncherAlias"
            )
            val state = if (hide) {
                context.packageManager.getComponentEnabledSetting(aliasComponent).let {
                    if (it == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return
                }
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            context.packageManager.setComponentEnabledSetting(
                aliasComponent, state, android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isLocalLrcBootstrapped(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_LOCAL_LRC_BOOTSTRAPPED, false)
    }

    fun setLocalLrcBootstrapped(context: Context, bootstrapped: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_LOCAL_LRC_BOOTSTRAPPED, bootstrapped)
            .apply()
    }

    fun isPackageAllowed(context: Context, packageName: String): Boolean {
        if (!isAppWhitelistEnabled(context)) {
            return true
        }
        if (packageName.isBlank()) {
            return false
        }
        return getWhitelistedPackages(context).contains(packageName)
    }

    fun isWelcomeCompleted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_WELCOME_COMPLETED, false)
    }

    fun setWelcomeCompleted(context: Context, completed: Boolean = true) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_WELCOME_COMPLETED, completed)
            .commit()
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

    fun isPinAboveMedia(context: Context): Boolean = true

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

    fun readPinAboveMedia(context: Context): Boolean = true

    fun readShowOnIsland(context: Context): Boolean = false

    fun isCustomAodLayout(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_CUSTOM_AOD_LAYOUT, false)
    }

    fun setCustomAodLayout(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_CUSTOM_AOD_LAYOUT, enabled)
            .apply()
    }

    fun readCustomAodLayout(context: Context): Boolean {
        return readFromModule(context) { isCustomAodLayout(it) } ?: false
    }

    fun isSwapLyricTranslation(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_SWAP_LYRIC_TRANSLATION, false)
    }

    fun setSwapLyricTranslation(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SWAP_LYRIC_TRANSLATION, enabled)
            .apply()
    }

    fun readSwapLyricTranslation(context: Context): Boolean {
        return readFromModule(context) { isSwapLyricTranslation(it) } ?: false
    }

    fun isSingleLineOnly(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_SINGLE_LINE_ONLY, false)
    }

    fun setSingleLineOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SINGLE_LINE_ONLY, enabled)
            .apply()
    }

    fun readSingleLineOnly(context: Context): Boolean {
        return readFromModule(context) { isSingleLineOnly(it) } ?: false
    }

    fun isMultiLineLyrics(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_MULTI_LINE_LYRICS, false)
    }

    fun setMultiLineLyrics(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_MULTI_LINE_LYRICS, enabled)
            .apply()
    }

    fun readMultiLineLyrics(context: Context): Boolean {
        return readFromModule(context) { isMultiLineLyrics(it) } ?: false
    }

    fun isAodMultiLineOnly(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AOD_MULTI_LINE_ONLY, false)
    }

    fun setAodMultiLineOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AOD_MULTI_LINE_ONLY, enabled)
            .apply()
    }

    fun readAodMultiLineOnly(context: Context): Boolean {
        return readFromModule(context) { isAodMultiLineOnly(it) } ?: false
    }

    fun isMultiLineShowTranslation(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_MULTI_LINE_SHOW_TRANSLATION, true)
    }

    fun setMultiLineShowTranslation(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_MULTI_LINE_SHOW_TRANSLATION, enabled)
            .apply()
    }

    fun readMultiLineShowTranslation(context: Context): Boolean {
        return readFromModule(context) { isMultiLineShowTranslation(it) } ?: true
    }

    fun getMultiLineLineCount(context: Context): Int {
        return coerceMultiLineLineCount(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(PREF_MULTI_LINE_LINE_COUNT, DEFAULT_MULTI_LINE_LINE_COUNT)
        )
    }

    fun setMultiLineLineCount(context: Context, lines: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_MULTI_LINE_LINE_COUNT, coerceMultiLineLineCount(lines))
            .apply()
    }

    fun readMultiLineLineCount(context: Context): Int {
        return readFromModule(context) { getMultiLineLineCount(it) } ?: DEFAULT_MULTI_LINE_LINE_COUNT
    }

    fun getMultiLineTextSize(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(PREF_MULTI_LINE_TEXT_SIZE, DEFAULT_MULTI_LINE_TEXT_SIZE_SP)
            .coerceIn(MIN_LYRIC_TEXT_SIZE_SP, MAX_LYRIC_TEXT_SIZE_SP)
    }

    fun setMultiLineTextSize(context: Context, sizeSp: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(
                PREF_MULTI_LINE_TEXT_SIZE,
                sizeSp.coerceIn(MIN_LYRIC_TEXT_SIZE_SP, MAX_LYRIC_TEXT_SIZE_SP)
            )
            .commit()
    }

    fun readMultiLineTextSize(context: Context): Float {
        return readFromModule(context) { getMultiLineTextSize(it) } ?: DEFAULT_MULTI_LINE_TEXT_SIZE_SP
    }

    fun getCustomAodTextSize(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(PREF_CUSTOM_AOD_TEXT_SIZE, DEFAULT_LYRIC_TEXT_SIZE_SP)
            .coerceIn(MIN_LYRIC_TEXT_SIZE_SP, MAX_LYRIC_TEXT_SIZE_SP)
    }

    fun setCustomAodTextSize(context: Context, sizeSp: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(
                PREF_CUSTOM_AOD_TEXT_SIZE,
                sizeSp.coerceIn(MIN_LYRIC_TEXT_SIZE_SP, MAX_LYRIC_TEXT_SIZE_SP)
            )
            .commit()
    }

    fun readCustomAodTextSize(context: Context): Float {
        return readFromModule(context) { getCustomAodTextSize(it) } ?: DEFAULT_LYRIC_TEXT_SIZE_SP
    }

    fun getCustomAodLyricWidth(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_CUSTOM_AOD_LYRIC_WIDTH, DEFAULT_CUSTOM_AOD_LYRIC_WIDTH)
            .coerceIn(MIN_CUSTOM_AOD_LYRIC_WIDTH, MAX_CUSTOM_AOD_LYRIC_WIDTH)
    }

    fun setCustomAodLyricWidth(context: Context, widthPercent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                PREF_CUSTOM_AOD_LYRIC_WIDTH,
                widthPercent.coerceIn(MIN_CUSTOM_AOD_LYRIC_WIDTH, MAX_CUSTOM_AOD_LYRIC_WIDTH)
            )
            .commit()
    }

    fun readCustomAodLyricWidth(context: Context): Int {
        return readFromModule(context) { getCustomAodLyricWidth(it) } ?: DEFAULT_CUSTOM_AOD_LYRIC_WIDTH
    }

    fun formatCustomAodLyricWidthLabel(widthPercent: Int): String = "${widthPercent}%"

    fun getCustomAodLyricMaxLines(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_CUSTOM_AOD_LYRIC_MAX_LINES, DEFAULT_LYRIC_MAX_LINES)
            .coerceIn(1, 2)
    }

    fun setCustomAodLyricMaxLines(context: Context, lines: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_CUSTOM_AOD_LYRIC_MAX_LINES, lines.coerceIn(1, 2))
            .commit()
    }

    fun readCustomAodLyricMaxLines(context: Context): Int {
        return readFromModule(context) { getCustomAodLyricMaxLines(it) } ?: DEFAULT_LYRIC_MAX_LINES
    }

    fun getCustomAodTranslationMaxLines(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_CUSTOM_AOD_TRANSLATION_MAX_LINES, DEFAULT_TRANSLATION_MAX_LINES)
            .coerceIn(1, 2)
    }

    fun setCustomAodTranslationMaxLines(context: Context, lines: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_CUSTOM_AOD_TRANSLATION_MAX_LINES, lines.coerceIn(1, 2))
            .commit()
    }

    fun readCustomAodTranslationMaxLines(context: Context): Int {
        return readFromModule(context) { getCustomAodTranslationMaxLines(it) }
            ?: DEFAULT_TRANSLATION_MAX_LINES
    }

    fun getCustomAodColorMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_CUSTOM_AOD_COLOR_MODE, CUSTOM_AOD_COLOR_WHITE)
            ?: CUSTOM_AOD_COLOR_WHITE
    }

    fun setCustomAodColorMode(context: Context, mode: String) {
        val normalized = when (mode) {
            CUSTOM_AOD_COLOR_ALBUM, CUSTOM_AOD_COLOR_PRESET -> mode
            else -> CUSTOM_AOD_COLOR_WHITE
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CUSTOM_AOD_COLOR_MODE, normalized)
            .commit()
    }

    fun readCustomAodColorMode(context: Context): String {
        return readFromModule(context) { getCustomAodColorMode(it) } ?: CUSTOM_AOD_COLOR_WHITE
    }

    fun getCustomAodPresetColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(
            PREF_CUSTOM_AOD_PRESET_COLOR,
            com.leowalk.LyricFocus.util.AodColorPresets.defaultPresetColor()
        )
    }

    fun setCustomAodPresetColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_CUSTOM_AOD_PRESET_COLOR, color)
            .commit()
    }

    fun readCustomAodPresetColor(context: Context): Int {
        return readFromModule(context) { getCustomAodPresetColor(it) }
            ?: com.leowalk.LyricFocus.util.AodColorPresets.defaultPresetColor()
    }

    fun getCustomAodCustomColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREF_CUSTOM_AOD_CUSTOM_COLOR, Color.WHITE)
    }

    fun setCustomAodCustomColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_CUSTOM_AOD_CUSTOM_COLOR, color)
            .apply()
    }

    fun getLyricTextPresetColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(
            PREF_LYRIC_TEXT_PRESET_COLOR,
            com.leowalk.LyricFocus.util.AodColorPresets.defaultPresetColor()
        )
    }

    fun setLyricTextPresetColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_LYRIC_TEXT_PRESET_COLOR, color)
            .apply()
    }

    fun readLyricTextPresetColor(context: Context): Int {
        return readFromModule(context) { getLyricTextPresetColor(it) }
            ?: com.leowalk.LyricFocus.util.AodColorPresets.defaultPresetColor()
    }

    fun formatCustomAodColorModeLabel(mode: String): String = when (mode) {
        CUSTOM_AOD_COLOR_ALBUM -> "专辑主色取色"
        CUSTOM_AOD_COLOR_PRESET -> "推荐颜色"
        CUSTOM_AOD_COLOR_CUSTOM -> "自定义颜色"
        else -> "白色"
    }

    fun getLyricCustomColor(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_LYRIC_CUSTOM_COLOR, Color.WHITE)
    }

    fun setLyricCustomColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_LYRIC_CUSTOM_COLOR, color)
            .apply()
    }

    fun getFocusBgCustomColor(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_FOCUS_BG_CUSTOM_COLOR, Color.BLACK)
    }

    fun setFocusBgCustomColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_FOCUS_BG_CUSTOM_COLOR, color)
            .apply()
    }

    fun getCustomAodGravity(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_CUSTOM_AOD_GRAVITY, GRAVITY_CENTER)
            ?: GRAVITY_CENTER
    }

    fun setCustomAodGravity(context: Context, gravity: String) {
        val normalized = when (gravity) {
            GRAVITY_LEFT, GRAVITY_RIGHT -> gravity
            else -> GRAVITY_CENTER
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CUSTOM_AOD_GRAVITY, normalized)
            .commit()
    }

    fun readCustomAodGravity(context: Context): String {
        return readFromModule(context) { getCustomAodGravity(it) } ?: GRAVITY_CENTER
    }

    fun getCustomAodSongInfo(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_CUSTOM_AOD_SONG_INFO, CUSTOM_AOD_SONG_INFO_ALL)
            ?: CUSTOM_AOD_SONG_INFO_ALL
    }

    fun setCustomAodSongInfo(context: Context, mode: String) {
        val normalized = when (mode) {
            CUSTOM_AOD_SONG_INFO_HIDE_TITLE,
            CUSTOM_AOD_SONG_INFO_HIDE_ARTIST,
            CUSTOM_AOD_SONG_INFO_HIDE_ALL -> mode
            else -> CUSTOM_AOD_SONG_INFO_ALL
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CUSTOM_AOD_SONG_INFO, normalized)
            .commit()
    }

    fun readCustomAodSongInfo(context: Context): String {
        return readFromModule(context) { getCustomAodSongInfo(it) } ?: CUSTOM_AOD_SONG_INFO_ALL
    }

    fun isCustomAodTitleIconEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_CUSTOM_AOD_TITLE_ICON_ENABLED, false)
    }

    fun setCustomAodTitleIconEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_CUSTOM_AOD_TITLE_ICON_ENABLED, enabled)
            .commit()
    }

    fun readCustomAodTitleIconEnabled(context: Context): Boolean {
        return readFromModule(context) { isCustomAodTitleIconEnabled(it) } ?: false
    }

    fun getCustomAodTitleIconPreset(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_CUSTOM_AOD_TITLE_ICON_PRESET, CUSTOM_AOD_TITLE_ICON_AUTO)
            ?: CUSTOM_AOD_TITLE_ICON_AUTO
    }

    fun setCustomAodTitleIconPreset(context: Context, preset: String) {
        val normalized = when (preset) {
            CUSTOM_AOD_TITLE_ICON_MUSIC_NOTE,
            CUSTOM_AOD_TITLE_ICON_NETEASE,
            CUSTOM_AOD_TITLE_ICON_QQ,
            CUSTOM_AOD_TITLE_ICON_KUGOU,
            CUSTOM_AOD_TITLE_ICON_KUWO,
            CUSTOM_AOD_TITLE_ICON_QISHUI,
            CUSTOM_AOD_TITLE_ICON_BODIAN,
            CUSTOM_AOD_TITLE_ICON_SPOTIFY,
            CUSTOM_AOD_TITLE_ICON_APPLE -> preset
            else -> CUSTOM_AOD_TITLE_ICON_AUTO
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CUSTOM_AOD_TITLE_ICON_PRESET, normalized)
            .commit()
    }

    fun readCustomAodTitleIconPreset(context: Context): String {
        return readFromModule(context) { getCustomAodTitleIconPreset(it) } ?: CUSTOM_AOD_TITLE_ICON_AUTO
    }

    fun formatCustomAodTitleIconPresetLabel(preset: String): String = when (preset) {
        CUSTOM_AOD_TITLE_ICON_AUTO -> "自动"
        CUSTOM_AOD_TITLE_ICON_MUSIC_NOTE -> "音符"
        CUSTOM_AOD_TITLE_ICON_NETEASE -> "网易云音乐"
        CUSTOM_AOD_TITLE_ICON_QQ -> "QQ音乐"
        CUSTOM_AOD_TITLE_ICON_KUGOU -> "酷狗音乐"
        CUSTOM_AOD_TITLE_ICON_KUWO -> "酷我音乐"
        CUSTOM_AOD_TITLE_ICON_SPOTIFY -> "Spotify"
        CUSTOM_AOD_TITLE_ICON_APPLE -> "Apple Music"
        else -> "自动"
    }

    fun getCustomAodTitleIconSize(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_CUSTOM_AOD_TITLE_ICON_SIZE, DEFAULT_CUSTOM_AOD_TITLE_ICON_SIZE)
            .coerceIn(MIN_CUSTOM_AOD_TITLE_ICON_SIZE, MAX_CUSTOM_AOD_TITLE_ICON_SIZE)
    }

    fun setCustomAodTitleIconSize(context: Context, sizePercent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                PREF_CUSTOM_AOD_TITLE_ICON_SIZE,
                sizePercent.coerceIn(MIN_CUSTOM_AOD_TITLE_ICON_SIZE, MAX_CUSTOM_AOD_TITLE_ICON_SIZE)
            )
            .commit()
    }

    fun readCustomAodTitleIconSize(context: Context): Int {
        return readFromModule(context) { getCustomAodTitleIconSize(it) } ?: DEFAULT_CUSTOM_AOD_TITLE_ICON_SIZE
    }

    fun formatCustomAodTitleIconSizeLabel(sizePercent: Int): String = "${sizePercent}%"

    fun resolvePackageToIconPreset(packageName: String?): String {
        if (packageName == null) return CUSTOM_AOD_TITLE_ICON_APPLE
        return when {
            packageName.contains("netease", ignoreCase = true) -> CUSTOM_AOD_TITLE_ICON_NETEASE
            packageName.contains("qqmusic", ignoreCase = true) || packageName.contains("tencent", ignoreCase = true) || packageName.contains("miui.player", ignoreCase = true) -> CUSTOM_AOD_TITLE_ICON_QQ
            packageName.contains("kugou", ignoreCase = true) || packageName.contains("kugou.android", ignoreCase = true) -> CUSTOM_AOD_TITLE_ICON_KUGOU
            packageName.contains("kuwo", ignoreCase = true) -> CUSTOM_AOD_TITLE_ICON_KUWO
            packageName.contains("luna.music", ignoreCase = true) -> CUSTOM_AOD_TITLE_ICON_QISHUI
            packageName.contains("wenyu.bodian", ignoreCase = true) -> CUSTOM_AOD_TITLE_ICON_BODIAN
            packageName.contains("spotify", ignoreCase = true) -> CUSTOM_AOD_TITLE_ICON_SPOTIFY
            packageName.contains("apple", ignoreCase = true) || packageName.contains("itunes", ignoreCase = true) -> CUSTOM_AOD_TITLE_ICON_APPLE
            else -> CUSTOM_AOD_TITLE_ICON_APPLE
        }
    }

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

    fun isMonetBgOnly(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_MONET_BG_ONLY, false)
    }

    fun setMonetBgOnly(context: Context, bgOnly: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_MONET_BG_ONLY, bgOnly)
            .commit()
    }

    fun isColorModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_COLOR_MODE, false)
    }

    fun setColorModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_COLOR_MODE, enabled)
            .commit()
    }

    fun readColorModeEnabled(context: Context): Boolean {
        return readFromModule(context) { isColorModeEnabled(it) } ?: false
    }

    fun isAlbumColorExtractionActive(context: Context): Boolean {
        return isMonetDynamicColorEnabled(context) || isTextColorExtractionEnabled(context)
    }

    fun isColorExtractionEnabled(context: Context): Boolean = isAlbumColorExtractionActive(context)

    fun shouldExtractAlbumColors(context: Context): Boolean {
        return isAlbumColorExtractionActive(context) ||
            (isCustomAodLayout(context) && getCustomAodColorMode(context) == CUSTOM_AOD_COLOR_ALBUM)
    }

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

    fun getExtractedAccentColor(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_EXTRACTED_ACCENT_COLOR)) return null
        return prefs.getInt(PREF_EXTRACTED_ACCENT_COLOR, Color.WHITE)
    }

    fun setExtractedColors(context: Context, accent: Int, backgroundEstimate: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_EXTRACTED_TEXT_COLOR, accent)
            .putInt(PREF_EXTRACTED_BG_COLOR, backgroundEstimate)
            .putInt(PREF_EXTRACTED_ACCENT_COLOR, accent)
            .commit()
    }

    fun setExtractedMonetScheme(context: Context, scheme: com.leowalk.LyricFocus.util.AlbumColorExtractor.MonetScheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_EXTRACTED_TEXT_COLOR, scheme.primaryText)
            .putInt(PREF_EXTRACTED_BG_COLOR, scheme.background)
            .putInt(PREF_EXTRACTED_ACCENT_COLOR, scheme.accent)
            .commit()
    }

    fun setExtractedDistinctColors(context: Context, colors: com.leowalk.LyricFocus.util.AlbumColorExtractor.DistinctColors) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_EXTRACTED_TEXT_COLOR, colors.primaryText)
            .putInt(PREF_EXTRACTED_BG_COLOR, colors.background)
            .putInt(PREF_EXTRACTED_ACCENT_COLOR, colors.accent)
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
            .remove(PREF_EXTRACTED_ACCENT_COLOR)
            .commit()
    }

    fun getExtractedColorOpacity(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_EXTRACTED_COLOR_OPACITY, 100)
            .coerceIn(10, 100)
    }

    fun setExtractedColorOpacity(context: Context, opacity: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_EXTRACTED_COLOR_OPACITY, opacity.coerceIn(10, 100))
            .commit()
    }

    fun readExtractedTextColor(context: Context): Int? {
        return readFromModule(context) { getExtractedTextColor(it) }
    }

    fun fillStyleExtras(intent: android.content.Intent, context: Context) {
        val monet = isMonetDynamicColorEnabled(context)
        val textExtraction = isTextColorExtractionEnabled(context)
        val colorMode = isColorModeEnabled(context)
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_MONET_DYNAMIC_COLOR, monet)
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_COLOR_EXTRACTION, textExtraction)
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_COLOR_MODE, colorMode)
        intent.putExtra("monet_bg_only", isMonetBgOnly(context))
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_BACKGROUND, getFocusBackground(context))
        appendExtractedColorExtras(intent, context)
    }

    private fun appendExtractedColorExtras(intent: android.content.Intent, context: Context) {
        val color = getExtractedTextColor(context)
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_COLOR_SET, color != null)
        if (color != null) {
            intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_COLOR, color)
        }
        val bg = getExtractedBgColor(context)
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_BG_COLOR_SET, bg != null)
        if (bg != null) {
            intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_BG_COLOR, bg)
        }
        val accent = getExtractedAccentColor(context)
        intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_ACCENT_SET, accent != null)
        if (accent != null) {
            intent.putExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_ACCENT, accent)
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
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_LAYOUT,
                    isCustomAodLayout(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_SWAP_LYRIC_TRANSLATION,
                    isSwapLyricTranslation(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_SINGLE_LINE_ONLY,
                    isSingleLineOnly(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_MULTI_LINE_LYRICS,
                    isMultiLineLyrics(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_MULTI_LINE_SHOW_TRANSLATION,
                    isMultiLineShowTranslation(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_MULTI_LINE_LINE_COUNT,
                    getMultiLineLineCount(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_MULTI_LINE_TEXT_SIZE,
                    getMultiLineTextSize(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_AOD_MULTI_LINE_ONLY,
                    isAodMultiLineOnly(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_TEXT_SIZE,
                    getCustomAodTextSize(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_LYRIC_WIDTH,
                    getCustomAodLyricWidth(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_LYRIC_MAX_LINES,
                    getCustomAodLyricMaxLines(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_TRANSLATION_MAX_LINES,
                    getCustomAodTranslationMaxLines(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_COLOR_MODE,
                    getCustomAodColorMode(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_PRESET_COLOR,
                    getCustomAodPresetColor(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_BG_CUSTOM_COLOR,
                    getFocusBgCustomColor(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_COLOR_OPACITY,
                    getExtractedColorOpacity(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_LYRIC_TEXT_PRESET_COLOR,
                    getLyricTextPresetColor(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_GRAVITY,
                    getCustomAodGravity(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_SONG_INFO,
                    getCustomAodSongInfo(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_TITLE_ICON_ENABLED,
                    isCustomAodTitleIconEnabled(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_TITLE_ICON_PRESET,
                    getCustomAodTitleIconPreset(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_CUSTOM_AOD_TITLE_ICON_SIZE,
                    getCustomAodTitleIconSize(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_MONET_DYNAMIC_COLOR,
                    isMonetDynamicColorEnabled(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_COLOR_EXTRACTION,
                    isTextColorExtractionEnabled(context)
                )
                putExtra(
                    FocusStyleSnapshot.EXTRA_STYLE_COLOR_MODE,
                    isColorModeEnabled(context)
                )
                appendExtractedColorExtras(this, context)
            }
            context.sendBroadcast(android.content.Intent(intent).setPackage("com.android.systemui"))
            context.sendBroadcast(android.content.Intent(intent).setPackage(context.packageName))
        } catch (_: Exception) {
        }
    }
}
