package com.lyricnotify.focus.lyric

import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import com.lyricnotify.focus.FocusPreferences

class NetEaseLyricProvider : LyricProvider {
    override val id: String = FocusPreferences.LYRIC_SOURCE_NETEASE
    override val name: String = "网易云音乐"

    private val client = HttpClient.instance

    override suspend fun searchLyric(title: String, artist: String, album: String): LyricInfo? {
        return try {
            val candidates = searchSongs(title, artist)
            for (candidate in candidates) {
                val (lyricText, tlyricText) = getLyricWithTranslation(candidate.id)
                if (lyricText.isNullOrBlank()) continue
                val lyricInfo = LrcParser.parseWithTranslation(lyricText, tlyricText)
                if (lyricInfo.lines.size < 3) continue
                return lyricInfo.copy(
                    title = candidate.title,
                    artist = candidate.artist,
                    source = name
                )
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private data class SongCandidate(
        val id: Long,
        val title: String,
        val artist: String
    )

    private suspend fun searchSongs(title: String, artist: String): List<SongCandidate> {
        val keyword = if (artist.isNotEmpty()) "$title $artist" else title
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://music.163.com/api/search/get?s=$encodedKeyword&type=1&offset=0&limit=8"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://music.163.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
            if (songs.length() == 0) return emptyList()

            return (0 until songs.length())
                .map { index ->
                    val song = songs.getJSONObject(index)
                    song to scoreSongMatch(song, title, artist)
                }
                .sortedByDescending { it.second }
                .map { (song, _) -> songToCandidate(song, title) }
        }
    }

    private fun songToCandidate(song: JSONObject, fallbackTitle: String): SongCandidate {
        val artists = song.optJSONArray("artists")
        val artistName = if (artists != null && artists.length() > 0) {
            artists.getJSONObject(0).optString("name", "")
        } else {
            ""
        }
        return SongCandidate(
            id = song.getLong("id"),
            title = song.optString("name", fallbackTitle),
            artist = artistName
        )
    }

    private fun scoreSongMatch(song: JSONObject, title: String, artist: String): Int {
        var score = 0
        val name = song.optString("name", "")
        if (name.equals(title, ignoreCase = true)) score += 12
        else if (name.contains(title, ignoreCase = true)) score += 6

        song.optJSONArray("artists")?.let { artists ->
            for (i in 0 until artists.length()) {
                val artistName = artists.getJSONObject(i).optString("name", "")
                if (artist.isBlank()) continue
                if (artistName.equals(artist, ignoreCase = true)) score += 24
                else if (artistName.contains(artist, ignoreCase = true)) score += 12
            }
        }
        return score
    }

    private suspend fun getLyricWithTranslation(songId: Long): Pair<String?, String?> {
        val url = "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://music.163.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Pair(null, null)
            val body = response.body?.string() ?: return Pair(null, null)
            val json = JSONObject(body)
            val lrc = json.optJSONObject("lrc")
            val lyric = lrc?.optString("lyric")
            var tlyric = json.optJSONObject("tlyric")?.optString("lyric")
            if (tlyric.isNullOrBlank()) {
                tlyric = json.optJSONObject("trans")?.optString("lyric")
            }
            return Pair(lyric, tlyric)
        }
    }
}
