package com.leowalk.LyricFocus.lyric

import com.leowalk.LyricFocus.FocusPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class QQMusicLyricProvider : LyricProvider {
    override val id: String = FocusPreferences.LYRIC_SOURCE_QQ
    override val name: String = "QQ音乐"

    private val client = HttpClient.instance

    override suspend fun searchLyric(title: String, artist: String, album: String): LyricInfo? {
        return try {
            val songMid = searchSong(title, artist) ?: return null
            val lyricText = getLyric(songMid) ?: return null
            val lyricInfo = LrcParser.parse(lyricText)
            if (lyricInfo.lines.size < 3) return null
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
            for ((songMid, score) in searchSongsByKeyword(keyword, title, artist)) {
                if (score > bestScore) {
                    bestScore = score
                    bestMid = songMid
                }
            }
        }
        return bestMid.takeIf { bestScore > 0 }
    }

    private suspend fun searchSongsByKeyword(
        keyword: String,
        title: String,
        artist: String
    ): List<Pair<String, Int>> {
        for (payload in buildSearchPayloads(keyword)) {
            val results = executeSearch(payload, title, artist)
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }

    private fun buildSearchPayloads(keyword: String): List<JSONObject> {
        val param = JSONObject().apply {
            put("search_type", 0)
            put("query", keyword)
            put("page_num", 1)
            put("num_per_page", 8)
        }
        val service = JSONObject().apply {
            put("module", "music.search.SearchCgiService")
            put("method", "DoSearchForQQMusicDesktop")
            put("param", param)
        }
        return listOf(
            JSONObject().put("music.search.SearchCgiService", service),
            JSONObject().apply {
                put("comm", JSONObject().apply {
                    put("ct", 24)
                    put("cv", 0)
                })
                put("req_1", service)
            }
        )
    }

    private suspend fun executeSearch(
        payload: JSONObject,
        title: String,
        artist: String
    ): List<Pair<String, Int>> {
        val request = Request.Builder()
            .url(SEARCH_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val list = extractSearchSongList(json) ?: return emptyList()

            val results = mutableListOf<Pair<String, Int>>()
            songLoop@ for (i in 0 until list.length()) {
                val song = list.getJSONObject(i)
                val songMid = song.optString("mid").ifBlank { song.optString("songmid") }
                if (songMid.isBlank()) continue

                val singers = song.optJSONArray("singer")
                val candidateArtists = if (singers != null) {
                    (0 until singers.length())
                        .map { singers.getJSONObject(it).optString("name", "") }
                } else {
                    emptyList()
                }

                for (candidateArtist in candidateArtists) {
                    if (LyricSearchHelper.isArtistBlacklisted(candidateArtist)) {
                        continue@songLoop
                    }
                }

                val songName = song.optString("name")
                    .ifBlank { song.optString("title") }
                    .ifBlank { song.optString("songname") }
                val titleScore = LyricSearchHelper.scoreTitleMatch(songName, title, artist)
                if (titleScore <= 0) continue

                var score = titleScore
                if (artist.isNotBlank()) {
                    score += LyricSearchHelper.scoreArtistMatch(candidateArtists, artist)
                }
                results.add(songMid to score)
            }
            return results
        }
    }

    private fun extractSearchSongList(json: JSONObject): org.json.JSONArray? {
        val paths = listOf(
            arrayOf("music.search.SearchCgiService", "data", "body", "song", "list"),
            arrayOf("req_1", "data", "body", "song", "list")
        )
        for (path in paths) {
            var node: Any? = json
            for (key in path) {
                node = when (node) {
                    is JSONObject -> node.opt(key)
                    else -> null
                }
            }
            if (node is org.json.JSONArray && node.length() > 0) {
                return node
            }
        }
        return null
    }

    private suspend fun getLyric(songMid: String): String? {
        fetchPlainLyric(songMid)?.let { return it }
        return fetchBase64Lyric(songMid)
    }

    private suspend fun fetchPlainLyric(songMid: String): String? {
        val url = "$LYRIC_URL?songmid=$songMid&format=json&nobase64=1&g_tk=5381" +
            "&loginUin=0&hostUin=0&platform=yqq"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/portal/player.html")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return parseLyricPayload(body)?.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun fetchBase64Lyric(songMid: String): String? {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid" +
            "&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8" +
            "&notice=0&platform=yqq&needNewCode=0&nobase64=0"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/portal/player.html")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val encoded = parseLyricPayload(body) ?: return null
            return decodeLyric(encoded)
        }
    }

    private fun parseLyricPayload(rawBody: String): String? {
        val jsonText = rawBody.trim().let { text ->
            JSONP_WRAPPER.replace(text, "$1")
        }
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        if (json.optInt("code", -1) != 0 && json.optInt("retcode", -1) != 0) {
            return null
        }
        return json.optString("lyric").takeIf { it.isNotBlank() }
    }

    private fun decodeLyric(base64Lyric: String): String? {
        return try {
            val decoded = android.util.Base64.decode(base64Lyric, android.util.Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val SEARCH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val LYRIC_URL = "https://i.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val JSONP_WRAPPER = Regex("""^(?:\w+|MusicJsonCallback(?:_\w+)?|jsonCallback)\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)
    }
}
