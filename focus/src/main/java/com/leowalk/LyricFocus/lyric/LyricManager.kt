package com.leowalk.LyricFocus.lyric

import android.content.Context
import android.util.Log
import com.leowalk.LyricFocus.FocusPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricManager(context: Context) {

    private val appContext = context.applicationContext
    private val netEaseProvider = NetEaseLyricProvider()
    private val qqMusicProvider = QQMusicLyricProvider(appContext)
    private val lrcLibProvider = LRCLibLyricProvider()
    private val localProvider = LocalLrcLyricProvider(appContext)
    private val aiTranslator = AiLyricTranslator(appContext)

    suspend fun fetchLyric(
        title: String,
        artist: String = "",
        album: String = "",
        musicPackage: String = "",
        platformSongId: PlayingSongIdResolver.ResolvedId? = null,
    ): LyricInfo? {
        return withContext(Dispatchers.IO) {
            LocalLrcBootstrap.ensureReady(appContext)
            val source = FocusPreferences.getLyricSource(appContext)
            when (source) {
                FocusPreferences.LYRIC_SOURCE_LOCAL ->
                    localProvider.searchLyric(title, artist, album)
                FocusPreferences.LYRIC_SOURCE_AI ->
                    fetchBaseForAi(title, artist, album, platformSongId)
                FocusPreferences.LYRIC_SOURCE_NETEASE ->
                    fetchByIdThenSearch(FocusPreferences.LYRIC_SOURCE_NETEASE, platformSongId, title, artist, album)
                        ?: localProvider.searchLyric(title, artist, album)
                FocusPreferences.LYRIC_SOURCE_QQ ->
                    fetchByIdThenSearch(FocusPreferences.LYRIC_SOURCE_QQ, platformSongId, title, artist, album)
                        ?: localProvider.searchLyric(title, artist, album)
                FocusPreferences.LYRIC_SOURCE_LRCLIB ->
                    lrcLibProvider.searchLyric(title, artist, album)
                        ?: localProvider.searchLyric(title, artist, album)
                else ->
                    fetchAutoByPackage(title, artist, album, musicPackage, platformSongId)
            }
        }
    }

    /**
     * Auto 模式：按播放器包名匹配歌词源。
     * - 网易云 → 先网易，再 QQ
     * - QQ 音乐 / 小米音乐（同源）→ 先 QQ，再网易
     * - 其他 → 先 QQ，再网易
     * 均失败则本地兜底。
     */
    private suspend fun fetchAutoByPackage(
        title: String,
        artist: String,
        album: String,
        musicPackage: String,
        platformSongId: PlayingSongIdResolver.ResolvedId?,
    ): LyricInfo? {
        val preferredId = FocusPreferences.preferredOnlineLyricSourceForPackage(musicPackage)
        val preferred = if (preferredId == FocusPreferences.LYRIC_SOURCE_NETEASE) {
            FocusPreferences.LYRIC_SOURCE_NETEASE
        } else {
            FocusPreferences.LYRIC_SOURCE_QQ
        }
        val secondary = if (preferred == FocusPreferences.LYRIC_SOURCE_QQ) {
            FocusPreferences.LYRIC_SOURCE_NETEASE
        } else {
            FocusPreferences.LYRIC_SOURCE_QQ
        }

        fetchByIdThenSearch(preferred, platformSongId, title, artist, album)
            ?.takeIf { !it.isEmpty }?.let { return it }
        // 次选源仅在 ID 属于该平台时直拉，否则搜歌名
        val secondaryId = platformSongId?.takeIf { it.platform == secondary }
        fetchByIdThenSearch(secondary, secondaryId, title, artist, album)
            ?.takeIf { !it.isEmpty }?.let { return it }
        return localProvider.searchLyric(title, artist, album)
    }

    private suspend fun fetchByIdThenSearch(
        platform: String,
        platformSongId: PlayingSongIdResolver.ResolvedId?,
        title: String,
        artist: String,
        album: String,
    ): LyricInfo? {
        if (platformSongId != null &&
            platformSongId.platform == platform &&
            platformSongId.hasDirectKey()
        ) {
            val byId = when (platform) {
                FocusPreferences.LYRIC_SOURCE_NETEASE ->
                    if (platformSongId.songId > 0L) {
                        netEaseProvider.fetchLyricById(platformSongId.songId, title, artist, album)
                    } else null
                FocusPreferences.LYRIC_SOURCE_QQ ->
                    qqMusicProvider.fetchLyricById(
                        songId = platformSongId.songId,
                        title = title,
                        artist = artist,
                        album = album,
                        songMid = platformSongId.songMid.orEmpty(),
                    )
                else -> null
            }
            if (byId != null && !byId.isEmpty) {
                Log.d(
                    TAG,
                    "lyric by id ok platform=$platform id=${platformSongId.songId} mid=${platformSongId.songMid}"
                )
                return byId
            }
            Log.d(
                TAG,
                "lyric by id miss platform=$platform id=${platformSongId.songId} mid=${platformSongId.songMid}, fallback search"
            )
        }
        return when (platform) {
            FocusPreferences.LYRIC_SOURCE_NETEASE -> netEaseProvider.searchLyric(title, artist, album)
            FocusPreferences.LYRIC_SOURCE_QQ -> qqMusicProvider.searchLyric(title, artist, album)
            else -> null
        }
    }

    suspend fun translateWithAi(lyricInfo: LyricInfo, title: String, artist: String): LyricInfo {
        return withContext(Dispatchers.IO) {
            aiTranslator.translateIfNeeded(lyricInfo, title, artist)
        }
    }

    suspend fun polishWithAi(lyricInfo: LyricInfo, title: String, artist: String): LyricInfo {
        return withContext(Dispatchers.IO) {
            aiTranslator.polishIfNeeded(lyricInfo, title, artist)
        }
    }

    fun getProviderNames(): List<String> = listOf(
        netEaseProvider.name,
        qqMusicProvider.name,
        localProvider.name,
        "Super Lyric"
    )

    fun clearCache() {
        aiTranslator.clearCache()
    }

    private suspend fun fetchBaseForAi(
        title: String,
        artist: String,
        album: String,
        platformSongId: PlayingSongIdResolver.ResolvedId?,
    ): LyricInfo? {
        val preferred = platformSongId?.platform ?: FocusPreferences.LYRIC_SOURCE_QQ
        val secondary = if (preferred == FocusPreferences.LYRIC_SOURCE_NETEASE) {
            FocusPreferences.LYRIC_SOURCE_QQ
        } else {
            FocusPreferences.LYRIC_SOURCE_NETEASE
        }
        return fetchByIdThenSearch(preferred, platformSongId, title, artist, album)
            ?: fetchByIdThenSearch(secondary, platformSongId?.takeIf { it.platform == secondary }, title, artist, album)
            ?: localProvider.searchLyric(title, artist, album)
    }

    companion object {
        private const val TAG = "LyricManager"
    }
}

object LocalLrcBootstrap {
    fun ensureReady(context: Context) {
        LocalLrcStore.getBootstrapDirectory(context)
        if (FocusPreferences.isLocalLrcBootstrapped(context)) return
        if (!LocalLrcStore.hasAnyLrcFile(context)) {
            LocalLrcStore.copyBundledLyricsIfNeeded(context)
        }
        FocusPreferences.setLocalLrcBootstrapped(context, true)
    }
}
