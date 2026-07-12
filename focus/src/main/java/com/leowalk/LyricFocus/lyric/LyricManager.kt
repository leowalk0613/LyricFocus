package com.leowalk.LyricFocus.lyric

import android.content.Context
import com.leowalk.LyricFocus.FocusPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricManager(context: Context) {

    private val appContext = context.applicationContext
    private val onlineProviders: List<LyricProvider> = listOf(
        NetEaseLyricProvider(),
        QQMusicLyricProvider()
    )
    private val localProvider = LocalLrcLyricProvider(appContext)
    private val aiTranslator = AiLyricTranslator(appContext)

    suspend fun fetchLyric(title: String, artist: String = "", album: String = ""): LyricInfo? {
        return withContext(Dispatchers.IO) {
            LocalLrcBootstrap.ensureReady(appContext)
            when (FocusPreferences.getLyricSource(appContext)) {
                FocusPreferences.LYRIC_SOURCE_LOCAL ->
                    localProvider.searchLyric(title, artist, album)
                FocusPreferences.LYRIC_SOURCE_AI ->
                    fetchWithAiTranslation(title, artist, album)
                else ->
                    fetchFromProviders(providersForCurrentSource(), title, artist, album)
            }
        }
    }

    fun getProviderNames(): List<String> = buildList {
        addAll(onlineProviders.map { it.name })
        add(localProvider.name)
        add("AI翻译")
    }

    private suspend fun fetchWithAiTranslation(
        title: String,
        artist: String,
        album: String
    ): LyricInfo? {
        val baseLyric = fetchFromProviders(onlineProviders, title, artist, album)
            ?: localProvider.searchLyric(title, artist, album)
            ?: return null
        return aiTranslator.translateIfNeeded(baseLyric, title, artist)
    }

    private suspend fun fetchFromProviders(
        providers: List<LyricProvider>,
        title: String,
        artist: String,
        album: String
    ): LyricInfo? {
        for (provider in providers) {
            try {
                val lyric = provider.searchLyric(title, artist, album)
                if (lyric != null && !lyric.isEmpty) {
                    return lyric
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun providersForCurrentSource(): List<LyricProvider> {
        return when (FocusPreferences.getLyricSource(appContext)) {
            FocusPreferences.LYRIC_SOURCE_NETEASE ->
                onlineProviders.filter { it.id == FocusPreferences.LYRIC_SOURCE_NETEASE }
            FocusPreferences.LYRIC_SOURCE_QQ ->
                onlineProviders.filter { it.id == FocusPreferences.LYRIC_SOURCE_QQ }
            else -> onlineProviders
        }
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
