package com.leowalk.LyricFocus.lyric

import android.content.Context
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

    suspend fun fetchLyric(title: String, artist: String = "", album: String = "", musicPackage: String = ""): LyricInfo? {
        return withContext(Dispatchers.IO) {
            LocalLrcBootstrap.ensureReady(appContext)
            when (FocusPreferences.getLyricSource(appContext)) {
                FocusPreferences.LYRIC_SOURCE_LOCAL ->
                    localProvider.searchLyric(title, artist, album)
                FocusPreferences.LYRIC_SOURCE_AI ->
                    fetchBaseForAi(title, artist, album)
                FocusPreferences.LYRIC_SOURCE_NETEASE ->
                    netEaseProvider.searchLyric(title, artist, album)
                        ?: localProvider.searchLyric(title, artist, album)
                FocusPreferences.LYRIC_SOURCE_QQ ->
                    qqMusicProvider.searchLyric(title, artist, album)
                        ?: localProvider.searchLyric(title, artist, album)
                FocusPreferences.LYRIC_SOURCE_LRCLIB ->
                    lrcLibProvider.searchLyric(title, artist, album)
                        ?: localProvider.searchLyric(title, artist, album)
                else ->
                    fetchAutoByPackage(title, artist, album, musicPackage)
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
        musicPackage: String
    ): LyricInfo? {
        val preferredId = FocusPreferences.preferredOnlineLyricSourceForPackage(musicPackage)
        val preferred = if (preferredId == FocusPreferences.LYRIC_SOURCE_NETEASE) {
            netEaseProvider
        } else {
            qqMusicProvider
        }
        val secondary = if (preferred === qqMusicProvider) netEaseProvider else qqMusicProvider

        preferred.searchLyric(title, artist, album)?.takeIf { !it.isEmpty }?.let { return it }
        secondary.searchLyric(title, artist, album)?.takeIf { !it.isEmpty }?.let { return it }
        return localProvider.searchLyric(title, artist, album)
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
        album: String
    ): LyricInfo? {
        return qqMusicProvider.searchLyric(title, artist, album)
            ?: netEaseProvider.searchLyric(title, artist, album)
            ?: localProvider.searchLyric(title, artist, album)
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
