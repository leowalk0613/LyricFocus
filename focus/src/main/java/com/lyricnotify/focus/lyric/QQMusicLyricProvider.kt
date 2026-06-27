package com.lyricnotify.focus.lyric

import com.lyricnotify.focus.FocusPreferences
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class QQMusicLyricProvider : LyricProvider {
    override val id: String = FocusPreferences.LYRIC_SOURCE_QQ
    override val name: String = "QQ音乐"

    private val client = HttpClient.instance

    override suspend fun searchLyric(title: String, artist: String, album: String): LyricInfo? {
        return try {
            val songMid = searchSong(title, artist) ?: return null
            val lyricText = getLyric(songMid) ?: return null
            val decodedLyric = decodeLyric(lyricText) ?: return null
            val lyricInfo = LrcParser.parse(decodedLyric)
            lyricInfo.copy(source = name)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun searchSong(title: String, artist: String): String? {
        val keyword = if (artist.isNotEmpty()) "$title $artist" else title
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encodedKeyword&n=5&p=1&format=json"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: return null
            val song = data.optJSONObject("song") ?: return null
            val list = song.optJSONArray("list") ?: return null
            if (list.length() == 0) return null

            val firstSong = list.getJSONObject(0)
            return firstSong.optString("songmid")
        }
    }

    private suspend fun getLyric(songMid: String): String? {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json&nobase64=0"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://y.qq.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            return json.optString("lyric")
        }
    }

    private fun decodeLyric(base64Lyric: String): String? {
        return try {
            val decoded = android.util.Base64.decode(base64Lyric, android.util.Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
