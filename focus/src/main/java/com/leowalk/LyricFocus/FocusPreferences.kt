package com.leowalk.LyricFocus

import android.content.Context

object FocusPreferences {

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

    const val DEFAULT_SYNC_ADVANCE_MS = 1300L
    const val MIN_SYNC_ADVANCE_MS = -1000L
    const val MAX_SYNC_ADVANCE_MS = 3000L

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

    fun isShowInShade(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_SHOW_IN_SHADE, false)
    }

    fun setShowInShade(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SHOW_IN_SHADE, enabled)
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

    fun isShowOnIsland(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_SHOW_ON_ISLAND, false)
    }

    fun setShowOnIsland(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SHOW_ON_ISLAND, enabled)
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

    fun readShowInShade(context: Context): Boolean {
        return readFromModule(context) { isShowInShade(it) } ?: false
    }

    fun readPinAboveMedia(context: Context): Boolean {
        return readFromModule(context) { isPinAboveMedia(it) } ?: false
    }

    fun readShowOnIsland(context: Context): Boolean {
        return readFromModule(context) { isShowOnIsland(it) } ?: false
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
                "com.leowalk.LyricFocus",
                Context.CONTEXT_IGNORE_SECURITY
            )
            reader(appContext)
        } catch (_: Throwable) {
            null
        }
    }
}
