package com.leowalk.LyricFocus.lyric

import android.content.Context
import com.leowalk.LyricFocus.FocusPreferences

class LocalLrcLyricProvider(
    context: Context
) : LyricProvider {
    override val id: String = FocusPreferences.LYRIC_SOURCE_LOCAL
    override val name: String = "本地 LRC"

    private val appContext = context.applicationContext

    override suspend fun searchLyric(title: String, artist: String, album: String): LyricInfo? {
        if (title.isBlank()) return null
        val match = LocalLrcStore.findBestMatch(appContext, title, artist) ?: return null
        val rawText = match.readText() ?: return null
        if (rawText.isBlank()) return null

        val lyricInfo = LrcParser.parse(rawText)
        if (lyricInfo.lines.size < 3) return null
        return lyricInfo.copy(
            title = title,
            artist = artist,
            album = album,
            source = "${name}（${match.name}）"
        )
    }
}
