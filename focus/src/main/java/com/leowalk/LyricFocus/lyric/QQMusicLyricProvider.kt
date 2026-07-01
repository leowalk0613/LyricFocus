package com.leowalk.LyricFocus.lyric

import com.leowalk.LyricFocus.FocusPreferences
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
        var bestMid: String? = null
        var bestScore = Int.MIN_VALUE
        for (keyword in LyricSearchHelper.buildSearchKeywords(title, artist)) {
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encodedKeyword&n=5&p=1&format=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val json = JSONObject(body)
                val list = json.optJSONObject("data")
                    ?.optJSONObject("song")
                    ?.optJSONArray("list")
                    ?: return@use
                for (i in 0 until list.length()) {
                    val song = list.getJSONObject(i)
                    val titleScore = LyricSearchHelper.scoreTitleMatch(
                        song.optString("songname", ""),
                        title
                    )
                    // 标题需至少弱匹配，避免仅靠艺术家命中拿到无关歌词
                    if (titleScore <= 0) continue
                    var score = titleScore
                    val singers = song.optJSONArray("singer")
                    if (singers != null && artist.isNotBlank()) {
                        val candidateArtists = (0 until singers.length())
                            .map { singers.getJSONObject(it).optString("name", "") }
                        score += LyricSearchHelper.scoreArtistMatch(candidateArtists, artist)
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestMid = song.optString("songmid")
                    }
                }
            }
        }
        return bestMid.takeIf { bestScore > 0 }
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
