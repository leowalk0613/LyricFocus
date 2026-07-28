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
                    fetchAutoWithQQFirst(title, artist, album, musicPackage)
            }
        }
    }

    /** Auto 模式：QQ 搜歌确认标题 → 网易拿歌词+翻译 → 严格匹配 → 本地兜底 */
    private suspend fun fetchAutoWithQQFirst(
        title: String, artist: String, album: String, musicPackage: String = ""
    ): LyricInfo? {
        val qqProvider = if (musicPackage == "com.netease.cloudmusic") netEaseProvider else qqMusicProvider
        val fallbackProvider = if (musicPackage == "com.netease.cloudmusic") qqMusicProvider else netEaseProvider

        val qqResult = qqProvider.searchLyric(title, artist, album)
        if (qqResult != null && !qqResult.isEmpty) {
            val confirmedTitle = qqResult.title.ifBlank { title }
            val confirmedArtist = qqResult.artist.ifBlank { artist }

            if (qqProvider.id == netEaseProvider.id) {
                return qqResult
            }

            val netEaseResult = netEaseProvider.searchLyric(confirmedTitle, confirmedArtist, album)
            if (netEaseResult != null && !netEaseResult.isEmpty
                && isStrictMatch(netEaseResult, confirmedTitle, confirmedArtist)
                && netEaseResult.lines.any { it.translation != null }) {
                return netEaseResult
            }
            return qqResult
        }

        val fbResult = fallbackProvider.searchLyric(title, artist, album)
        return fbResult?.takeIf { !it.isEmpty }
            ?: localProvider.searchLyric(title, artist, album)
    }

    private fun isStrictMatch(lyricInfo: LyricInfo, expectedTitle: String, expectedArtist: String): Boolean {
        val titleScore = LyricSearchHelper.scoreTitleMatch(lyricInfo.title, expectedTitle)
        if (titleScore <= 0) return false
        if (expectedArtist.isNotBlank() && lyricInfo.artist.isNotBlank()) {
            val artistScore = LyricSearchHelper.scoreArtistMatch(listOf(lyricInfo.artist), expectedArtist)
            return artistScore >= 12
        }
        return true
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
