package com.leowalk.LyricFocus.lyric

import android.content.Context
import com.leowalk.LyricFocus.FocusPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricManager(context: Context) {

    private val appContext = context.applicationContext
    private val allProviders: List<LyricProvider> = listOf(
        NetEaseLyricProvider(),
        QQMusicLyricProvider()
    )

    suspend fun fetchLyric(title: String, artist: String = "", album: String = ""): LyricInfo? {
        return withContext(Dispatchers.IO) {
            for (provider in providersForCurrentSource()) {
                try {
                    val lyric = provider.searchLyric(title, artist, album)
                    if (lyric != null && !lyric.isEmpty) {
                        return@withContext lyric
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            null
        }
    }

    fun getProviderNames(): List<String> = allProviders.map { it.name }

    private fun providersForCurrentSource(): List<LyricProvider> {
        return when (FocusPreferences.getLyricSource(appContext)) {
            FocusPreferences.LYRIC_SOURCE_NETEASE ->
                allProviders.filter { it.id == FocusPreferences.LYRIC_SOURCE_NETEASE }
            FocusPreferences.LYRIC_SOURCE_QQ ->
                allProviders.filter { it.id == FocusPreferences.LYRIC_SOURCE_QQ }
            else -> allProviders
        }
    }
}
