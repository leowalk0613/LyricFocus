package com.leowalk.LyricFocus.lyric

import android.util.Log
import com.leowalk.LyricFocus.FocusPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.zip.Inflater

class QQMusicLyricProvider(context: android.content.Context) : LyricProvider {
    override val id: String = FocusPreferences.LYRIC_SOURCE_QQ
    override val name: String = "QQ音乐"

    private val client = HttpClient.instance

    private data class SongCandidate(
        val songMid: String,
        val songId: Long,
        val title: String,
        val artist: String,
        val album: String
    )

    override suspend fun searchLyric(title: String, artist: String, album: String): LyricInfo? {
        return try {
            val candidate = searchSong(title, artist, album) ?: return null
            Log.d(TAG, "searchLyric: candidate mid=${candidate.songMid} id=${candidate.songId}")
            // 优先新接口（含翻译），DES 解密失败则走明文兜底
            val result = fetchQrcLyric(candidate) ?: fetchLegacyLyric(candidate)
            if (result != null) Log.d(TAG, "searchLyric: got ${result.lines.size} lines, hasTrans=${result.lines.any { it.translation != null }}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "searchLyric error", e)
            null
        }
    }

    private suspend fun tryEnrichLyric(candidate: SongCandidate, fallback: LyricInfo): LyricInfo? {
        return try {
            fetchQrcLyric(candidate)
        } catch (e: Exception) {
            Log.d(TAG, "tryEnrichLyric failed", e)
            null
        }
    }

    private suspend fun searchSong(title: String, artist: String, album: String = ""): SongCandidate? {
        val keywords = LyricSearchHelper.buildSearchKeywords(title, artist)
        var bestCandidate: SongCandidate? = null
        var bestScore = Int.MIN_VALUE

        for (keyword in keywords) {
            for ((candidate, score) in searchSongsByKeyword(keyword, title, artist)) {
                val albumScore = LyricSearchHelper.scoreAlbumMatch(candidate.album, album)
                val total = score + albumScore
                if (total > bestScore) {
                    bestScore = total
                    bestCandidate = candidate
                }
            }
        }
        val result = bestCandidate.takeIf { bestScore >= 0 }
        if (result == null) Log.d(TAG, "searchSong: no match (bestScore=$bestScore) for '$title' - '$artist', tried ${keywords.size} keywords")
        return result
    }

    private suspend fun searchSongsByKeyword(
        keyword: String,
        title: String,
        artist: String
    ): List<Pair<SongCandidate, Int>> {
        val fallback = searchViaSoso(keyword, title, artist)
        if (fallback.isNotEmpty()) return fallback
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
    ): List<Pair<SongCandidate, Int>> {
        val request = Request.Builder()
            .url(SEARCH_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.d(TAG, "executeSearch: HTTP ${response.code}")
                return emptyList()
            }
            val body = response.body?.string() ?: return emptyList()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (json == null) {
                Log.d(TAG, "executeSearch: invalid JSON response, body preview: ${body.take(200)}")
                return emptyList()
            }
            val list = extractSearchSongList(json)
            if (list == null) {
                Log.d(TAG, "executeSearch: no song list found, keys: ${json.keys().asSequence().toList()}")
                return emptyList()
            }
            Log.d(TAG, "executeSearch: found ${list.length()} results")

            val results = mutableListOf<Pair<SongCandidate, Int>>()
            songLoop@ for (i in 0 until list.length()) {
                val song = list.getJSONObject(i)
                val songMid = song.optString("mid").ifBlank { song.optString("songmid") }
                    .ifBlank { song.optString("songMid") }
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
                    .ifBlank { song.optString("songName") }
                    .ifBlank { song.optString("song") }
                val titleScore = LyricSearchHelper.scoreTitleMatch(songName, title, artist)
                if (titleScore <= 0) continue

                var score = titleScore
                if (artist.isNotBlank()) {
                    score += LyricSearchHelper.scoreArtistMatch(candidateArtists, artist)
                }
                val candidate = SongCandidate(
                    songMid = songMid,
                    songId = song.optLong("id", 0L).let { if (it == 0L) song.optLong("songid", 0L) else it },
                    title = songName,
                    artist = candidateArtists.firstOrNull() ?: "",
                    album = song.optJSONObject("album")?.optString("name", "")
                        ?: song.optString("albumname", "")
                        ?: song.optString("albumName", "")
                )
                results.add(candidate to score)
            }
            return results
        }
    }

    private fun extractSearchSongList(json: JSONObject): org.json.JSONArray? {
        val paths = listOf(
            arrayOf("music.search.SearchCgiService", "data", "body", "song", "list"),
            arrayOf("req_1", "data", "body", "song", "list"),
            arrayOf("req_0", "data", "body", "song", "list"),
            arrayOf("data", "body", "song", "list"),
            arrayOf("data", "song", "list"),
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

    private suspend fun searchViaSoso(
        keyword: String,
        title: String,
        artist: String
    ): List<Pair<SongCandidate, Int>> {
        return try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&format=json&p=1&n=8"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y.qq.com/")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val jsonText = JSONP_WRAPPER.replace(body.trim(), "$1")
                val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return emptyList()
                val list = json.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
                    ?: return emptyList()

                val results = mutableListOf<Pair<SongCandidate, Int>>()
                for (i in 0 until list.length()) {
                    val song = list.getJSONObject(i)
                    val songMid = song.optString("mid").ifBlank { song.optString("songmid") }
                    if (songMid.isBlank()) continue
                    val songName = song.optString("songname").ifBlank { song.optString("name").ifBlank { song.optString("title") } }
                    val titleScore = LyricSearchHelper.scoreTitleMatch(songName, title, artist)
                    if (titleScore <= 0) continue
                    val candidateArtists = song.optJSONArray("singer")?.let { sing ->
                        (0 until sing.length()).flatMap { LyricSearchHelper.splitArtists(sing.getJSONObject(it).optString("name", "")) }
                    } ?: emptyList()
                    var score = titleScore
                    if (artist.isNotBlank()) {
                        score += LyricSearchHelper.scoreArtistMatch(candidateArtists, artist)
                    }
                    results.add(
                        SongCandidate(
                            songMid = songMid,
                            songId = song.optLong("songid", 0L).let { if (it == 0L) song.optLong("id", 0L) else it },
                            title = songName,
                            artist = candidateArtists.firstOrNull() ?: "",
                            album = song.optString("albumname").ifBlank { song.optJSONObject("album")?.optString("name") ?: "" }
                        ) to score
                    )
                }
                results
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun postMusicu(module: String, method: String, param: JSONObject): JSONObject? {
        val body = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("ct", "11")
                put("cv", "1003006")
                put("v", "1003006")
                put("tmeAppID", "qqmusiclight")
            })
            put("req_0", JSONObject().apply {
                put("method", method)
                put("module", module)
                put("param", param)
            })
        }
        val request = Request.Builder()
            .url(MUSICU_URL)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string() ?: return null
                JSONObject(text)
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchQrcLyric(candidate: SongCandidate): LyricInfo? {
        Log.d(TAG, "fetchQrcLyric: songId=${candidate.songId}")
        val param = JSONObject().apply {
            put("songID", candidate.songId)
            put("songName", android.util.Base64.encodeToString(candidate.title.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            put("singerName", android.util.Base64.encodeToString(candidate.artist.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            put("albumName", android.util.Base64.encodeToString(candidate.album.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            put("crypt", 1)
            put("qrc", 1)
            put("trans", 1)
            put("roma", 1)
            put("cv", 2111)
            put("ct", 19)
            put("lrc_t", 0)
            put("qrc_t", 0)
            put("roma_t", 0)
            put("trans_t", 0)
            put("type", 0)
            put("interval", 0)
        }
        val resp = postMusicu("music.musichallSong.PlayLyricInfo", "GetPlayLyricInfo", param)
        if (resp == null) {
            Log.d(TAG, "fetchQrcLyric: postMusicu returned null, trying fallback")
            return fetchLegacyLyric(candidate)
        }
        val data = resp.optJSONObject("req_0")?.optJSONObject("data")
        if (data == null) {
            Log.d(TAG, "fetchQrcLyric: no data in response")
            return fetchLegacyLyric(candidate)
        }
        val qrcRaw = data.optString("lyric", "")
        val transRaw = data.optString("trans", "")
        val romaRaw = data.optString("roma", "")
        Log.d(TAG, "fetchQrcLyric: lyric hex len=${qrcRaw.length} trans hex len=${transRaw.length}")
        val qrcText = decryptQrcStr(qrcRaw)
        if (qrcText.isEmpty()) {
            Log.d(TAG, "fetchQrcLyric: DES decrypt lyric failed, trying fallback")
            return fetchLegacyLyric(candidate)
        }
        val transText = decryptQrcStr(transRaw)
        val romaText = decryptQrcStr(romaRaw)
        Log.d(TAG, "fetchQrcLyric: lyric ok len=${qrcText.length} trans len=${transText.length} roma len=${romaText.length}")
        Log.d(TAG, "fetchQrcLyric: lyric preview=${qrcText.take(80)}")
        val lines = parseQrcToLyricLines(qrcText, transText, romaText)
        if (lines.size < 2) {
            Log.d(TAG, "fetchQrcLyric: too few lines (${lines.size})")
            return null
        }
        Log.d(TAG, "fetchQrcLyric: parsed ${lines.size} lines, hasTrans=${transText.isNotBlank()}")
        return LyricInfo(
            title = candidate.title,
            artist = candidate.artist,
            album = candidate.album,
            lines = lines,
            source = name
        )
    }

    private suspend fun fetchLegacyLyric(candidate: SongCandidate): LyricInfo? {
        val url = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        val body = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("ct", "11")
                put("cv", "1003006")
                put("v", "1003006")
                put("tmeAppID", "qqmusiclight")
            })
            put("req_0", JSONObject().apply {
                put("method", "GetPlayLyricInfo")
                put("module", "music.musichallSong.PlayLyricInfo")
                put("param", JSONObject().apply {
                    put("songID", candidate.songId)
                    put("songName", android.util.Base64.encodeToString(candidate.title.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
                    put("singerName", android.util.Base64.encodeToString(candidate.artist.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
                    put("albumName", android.util.Base64.encodeToString(candidate.album.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
                    put("crypt", 0)
                    put("qrc", 0)
                    put("lrc", 1)
                    put("trans", 1)
                    put("cv", 2111)
                    put("ct", 19)
                    put("type", 0)
                    put("interval", 0)
                })
            })
        }
        val request = Request.Builder().url(url).post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: return null)
                val data = json.optJSONObject("req_0")?.optJSONObject("data") ?: return null
                val lrcB64 = data.optString("lyric", "")
                if (lrcB64.isBlank()) return null
                val lyricText = try {
                    String(android.util.Base64.decode(lrcB64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: Exception) { return null }
                val lyricInfo = LrcParser.parse(lyricText)
                if (lyricInfo.lines.size < 2) return null
                Log.d(TAG, "fetchLegacyLyric: ${lyricInfo.lines.size} lines")
                lyricInfo.copy(title = candidate.title, artist = candidate.artist, album = candidate.album, source = name)
            }
        } catch (e: Exception) { Log.d(TAG, "fetchLegacyLyric failed", e); null }
    }

    private fun parseQrcToLyricLines(qrcText: String, transText: String, romaText: String): List<LyricLine> {
        val origLines = parseQrcFormat(qrcText)
        if (origLines.isEmpty()) return emptyList()
        val transLines = if (transText.isNotBlank()) parseLrcLines(transText) else emptyList()
        val romaLines = if (romaText.isNotBlank()) parseQrcFormat(romaText) else emptyList()
        val result = mutableListOf<LyricLine>()
        var transIdx = 0
        var romaIdx = 0
        for (i in origLines.indices) {
            val orig = origLines[i]
            val lineStart = orig.first
            val lineEnd = if (i + 1 < origLines.size) origLines[i + 1].first else lineStart + 5000
            val text = orig.second.map { it.text }.joinToString("")
            var translation: String? = null
            while (transIdx < transLines.size && transLines[transIdx].first < lineStart - 500) transIdx++
            if (transIdx < transLines.size && transLines[transIdx].first < lineEnd) {
                translation = transLines[transIdx].second.map { it.text }.joinToString("").takeIf { it.isNotBlank() }
                transIdx++
            }
            var reading: String? = null
            while (romaIdx < romaLines.size && romaLines[romaIdx].first < lineStart - 500) romaIdx++
            if (romaIdx < romaLines.size && romaLines[romaIdx].first < lineEnd) {
                reading = romaLines[romaIdx].second.map { it.text }.joinToString("").takeIf { it.isNotBlank() }
                romaIdx++
            }
            if (text.isNotBlank()) {
                result.add(LyricLine(time = lineStart, text = text, translation = translation, reading = reading))
            }
        }
        return result
    }

    private fun parseQrcFormat(text: String): List<Pair<Long, List<QrcWord>>> {
        val result = mutableListOf<Pair<Long, List<QrcWord>>>()
        val content = text.let { t ->
            Regex("<Lyric_1 LyricType=\"1\" LyricContent=\"([\\s\\S]*?)\"/>").find(t)?.groupValues?.getOrNull(1)?.let {
                it.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            } ?: t
        }
        for (rawLine in content.split(Regex("\r?\n"))) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (Regex("^\\[(\\w+):([^\\]]*)]$").matches(line)) continue
            val match = Regex("^\\[(\\d+),(\\d+)](.*)$", RegexOption.DOT_MATCHES_ALL).find(line) ?: continue
            val lineStart = match.groupValues[1].toLong()
            val lineDuration = match.groupValues[2].toLong()
            val lineEnd = lineStart + lineDuration
            val lineContent = match.groupValues[3]
            val words = mutableListOf<QrcWord>()
            val wordRe = Regex("((?:(?!\\(\\d+,\\d+\\)).)*)\\((\\d+),(\\d+)\\)")
            var wordMatch = wordRe.find(lineContent)
            while (wordMatch != null) {
                val wordText = wordMatch.groupValues[1]
                val wordStart = wordMatch.groupValues[2].toLong()
                words.add(QrcWord(wordStart, wordText))
                wordMatch = wordMatch.next()
            }
            if (words.isEmpty()) {
                words.add(QrcWord(lineStart, lineContent))
            }
            val wordList = words.mapIndexed { idx, w ->
                val end = if (idx + 1 < words.size) words[idx + 1].time else lineEnd
                QrcWord(w.time, w.text, end)
            }
            result.add(lineStart to wordList)
        }
        return result
    }

    private fun parseLrcLines(text: String): List<Pair<Long, List<QrcWord>>> {
        val result = mutableListOf<Pair<Long, List<QrcWord>>>()
        val tempLines = mutableListOf<Pair<Long, String>>()
        for (line in text.split(Regex("\r?\n"))) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = Regex("^\\[(\\d+):(\\d+\\.\\d+)](.*)$").find(trimmed) ?: continue
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toDouble()
            val start = (minutes * 60_000 + (seconds * 1000).toLong())
            tempLines.add(start to match.groupValues[3])
        }
        if (tempLines.isEmpty()) return result
        tempLines.sortBy { it.first }
        for (i in tempLines.indices) {
            val start = tempLines[i].first
            val end = if (i + 1 < tempLines.size) maxOf(start, tempLines[i + 1].first - 10) else start + 2000
            result.add(start to listOf(QrcWord(start, tempLines[i].second, end)))
        }
        return result
    }

    private data class QrcWord(val time: Long, val text: String, val endTime: Long = 0L)

    companion object {
        private const val TAG = "QQMusicLyricProvider"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val SEARCH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val JSONP_WRAPPER = Regex("""^(?:\w+|MusicJsonCallback(?:_\w+)?|jsonCallback|callback)\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)

        private const val QRC_KEY = """!@#)(${"$"}%123ZXC!@!@#)(NHL"""

        private val SBOX = arrayOf(
            intArrayOf(14,4,13,1,2,15,11,8,3,10,6,12,5,9,0,7,0,15,7,4,14,2,13,1,10,6,12,11,9,5,3,8,4,1,14,8,13,6,2,11,15,12,9,7,3,10,5,0,15,12,8,2,4,9,1,7,5,11,3,14,10,0,6,13),
            intArrayOf(15,1,8,14,6,11,3,4,9,7,2,13,12,0,5,10,3,13,4,7,15,2,8,15,12,0,1,10,6,9,11,5,0,14,7,11,10,4,13,1,5,8,12,6,9,3,2,15,13,8,10,1,3,15,4,2,11,6,7,12,0,5,14,9),
            intArrayOf(10,0,9,14,6,3,15,5,1,13,12,7,11,4,2,8,13,7,0,9,3,4,6,10,2,8,5,14,12,11,15,1,13,6,4,9,8,15,3,0,11,1,2,12,5,10,14,7,1,10,13,0,6,9,8,7,4,15,14,3,11,5,2,12),
            intArrayOf(7,13,14,3,0,6,9,10,1,2,8,5,11,12,4,15,13,8,11,5,6,15,0,3,4,7,2,12,1,10,14,9,10,6,9,0,12,11,7,13,15,1,3,14,5,2,8,4,3,15,0,6,10,10,13,8,9,4,5,11,12,7,2,14),
            intArrayOf(2,12,4,1,7,10,11,6,8,5,3,15,13,0,14,9,14,11,2,12,4,7,13,1,5,0,15,10,3,9,8,6,4,2,1,11,10,13,7,8,15,9,12,5,6,3,0,14,11,8,12,7,1,14,2,13,6,15,0,9,10,4,5,3),
            intArrayOf(12,1,10,15,9,2,6,8,0,13,3,4,14,7,5,11,10,15,4,2,7,12,9,5,6,1,13,14,0,11,3,8,9,14,15,5,2,8,12,3,7,0,4,10,1,13,11,6,4,3,2,12,9,5,15,10,11,14,1,7,6,0,8,13),
            intArrayOf(4,11,2,14,15,0,8,13,3,12,9,7,5,10,6,1,13,0,11,7,4,9,1,10,14,3,5,12,2,15,8,6,1,4,11,13,12,3,7,14,10,15,6,8,0,5,9,2,6,11,13,8,1,4,10,7,9,5,0,15,14,2,3,12),
            intArrayOf(13,2,8,4,6,15,11,1,10,9,3,14,5,0,12,7,1,15,13,8,10,3,7,4,12,5,6,11,0,14,9,2,7,11,4,1,9,12,14,2,0,6,10,13,15,3,5,8,2,1,14,7,4,10,8,13,15,12,9,0,3,5,6,11)
        )

        private fun bitnum(bytes: ByteArray, b: Int, c: Int): Int {
            val bi = (b / 32) * 4 + 3 - ((b % 32) / 8)
            if (bi >= bytes.size) return 0
            return (((bytes[bi].toInt() and 0xFF) ushr (7 - (b % 8))) and 1) shl c
        }

        private fun bitnumIntr(value: Int, b: Int, c: Int): Int {
            return ((value ushr (31 - b)) and 1) shl c
        }

        private fun bitnumIntl(value: Int, b: Int, c: Int): Int {
            return ((value shl b) and 0x80000000.toInt()) ushr c
        }

        private fun sboxBit(v: Int): Int = (v and 32) or ((v and 31) ushr 1) or ((v and 1) shl 4)

        private fun initialPermutation(input: ByteArray): IntArray {
            fun b(b: Int, c: Int) = bitnum(input, b, c)
            val s0 = b(57,31) or b(49,30) or b(41,29) or b(33,28) or b(25,27) or b(17,26) or b(9,25) or b(1,24) or
                b(59,23) or b(51,22) or b(43,21) or b(35,20) or b(27,19) or b(19,18) or b(11,17) or b(3,16) or
                b(61,15) or b(53,14) or b(45,13) or b(37,12) or b(29,11) or b(21,10) or b(13,9) or b(5,8) or
                b(63,7) or b(55,6) or b(47,5) or b(39,4) or b(31,3) or b(23,2) or b(15,1) or b(7,0)
            val s1 = b(56,31) or b(48,30) or b(40,29) or b(32,28) or b(24,27) or b(16,26) or b(8,25) or b(0,24) or
                b(58,23) or b(50,22) or b(42,21) or b(34,20) or b(26,19) or b(18,18) or b(10,17) or b(2,16) or
                b(60,15) or b(52,14) or b(44,13) or b(36,12) or b(28,11) or b(20,10) or b(12,9) or b(4,8) or
                b(62,7) or b(54,6) or b(46,5) or b(38,4) or b(30,3) or b(22,2) or b(14,1) or b(6,0)
            return intArrayOf(s0, s1)
        }

        private fun inversePermutation(s0: Int, s1: Int): ByteArray {
            fun b(v: Int, bit: Int, pos: Int) = ((v ushr (31 - bit)) and 1) shl (7 - (pos % 8))
            return byteArrayOf(
                (b(s1,4,0) or b(s0,4,1) or b(s1,12,2) or b(s0,12,3) or b(s1,20,4) or b(s0,20,5) or b(s1,28,6) or b(s0,28,7)).toByte(),
                (b(s1,5,0) or b(s0,5,1) or b(s1,13,2) or b(s0,13,3) or b(s1,21,4) or b(s0,21,5) or b(s1,29,6) or b(s0,29,7)).toByte(),
                (b(s1,6,0) or b(s0,6,1) or b(s1,14,2) or b(s0,14,3) or b(s1,22,4) or b(s0,22,5) or b(s1,30,6) or b(s0,30,7)).toByte(),
                (b(s1,7,0) or b(s0,7,1) or b(s1,15,2) or b(s0,15,3) or b(s1,23,4) or b(s0,23,5) or b(s1,31,6) or b(s0,31,7)).toByte(),
                (b(s1,0,0) or b(s0,0,1) or b(s1,8,2) or b(s0,8,3) or b(s1,16,4) or b(s0,16,5) or b(s1,24,6) or b(s0,24,7)).toByte(),
                (b(s1,1,0) or b(s0,1,1) or b(s1,9,2) or b(s0,9,3) or b(s1,17,4) or b(s0,17,5) or b(s1,25,6) or b(s0,25,7)).toByte(),
                (b(s1,2,0) or b(s0,2,1) or b(s1,10,2) or b(s0,10,3) or b(s1,18,4) or b(s0,18,5) or b(s1,26,6) or b(s0,26,7)).toByte(),
                (b(s1,3,0) or b(s0,3,1) or b(s1,11,2) or b(s0,11,3) or b(s1,19,4) or b(s0,19,5) or b(s1,27,6) or b(s0,27,7)).toByte()
            )
        }

        private fun desF(state: Int, key: IntArray): Int {
            val t1 = (bitnumIntl(state,31,0) or ((state and 0xF0000000.toInt()) ushr 1) or
                bitnumIntl(state,4,5) or bitnumIntl(state,3,6) or ((state and 0x0F000000.toInt()) ushr 3) or
                bitnumIntl(state,8,11) or bitnumIntl(state,7,12) or ((state and 0x00F00000) ushr 5) or
                bitnumIntl(state,12,17) or bitnumIntl(state,11,18) or ((state and 0x000F0000) ushr 7) or
                bitnumIntl(state,16,23))
            val t2 = (bitnumIntl(state,15,0) or ((state and 0x0000F000) shl 15) or
                bitnumIntl(state,20,5) or bitnumIntl(state,19,6) or ((state and 0x00000F00) shl 13) or
                bitnumIntl(state,24,11) or bitnumIntl(state,23,12) or ((state and 0x000000F0) shl 11) or
                bitnumIntl(state,28,17) or bitnumIntl(state,27,18) or ((state and 0x0000000F) shl 9) or
                bitnumIntl(state,0,23))
            val l = IntArray(6) { i ->
                val v = when (i) { 0 -> t1 ushr 24; 1 -> t1 ushr 16; 2 -> t1 ushr 8; 3 -> t2 ushr 24; 4 -> t2 ushr 16; 5 -> t2 ushr 8; else -> 0 }
                (v and 0xFF) xor key[i]
            }
            val r = ((SBOX[0][sboxBit(l[0] ushr 2)] shl 28) or
                (SBOX[1][sboxBit(((l[0] and 3) shl 4) or (l[1] ushr 4))] shl 24) or
                (SBOX[2][sboxBit(((l[1] and 15) shl 2) or (l[2] ushr 6))] shl 20) or
                (SBOX[3][sboxBit(l[2] and 63)] shl 16) or
                (SBOX[4][sboxBit(l[3] ushr 2)] shl 12) or
                (SBOX[5][sboxBit(((l[3] and 3) shl 4) or (l[4] ushr 4))] shl 8) or
                (SBOX[6][sboxBit(((l[4] and 15) shl 2) or (l[5] ushr 6))] shl 4) or
                SBOX[7][sboxBit(l[5] and 63)])
            return (bitnumIntl(r,15,0) or bitnumIntl(r,6,1) or bitnumIntl(r,19,2) or bitnumIntl(r,20,3) or
                bitnumIntl(r,28,4) or bitnumIntl(r,11,5) or bitnumIntl(r,27,6) or bitnumIntl(r,16,7) or
                bitnumIntl(r,0,8) or bitnumIntl(r,14,9) or bitnumIntl(r,22,10) or bitnumIntl(r,25,11) or
                bitnumIntl(r,4,12) or bitnumIntl(r,17,13) or bitnumIntl(r,30,14) or bitnumIntl(r,9,15) or
                bitnumIntl(r,1,16) or bitnumIntl(r,7,17) or bitnumIntl(r,23,18) or bitnumIntl(r,13,19) or
                bitnumIntl(r,31,20) or bitnumIntl(r,26,21) or bitnumIntl(r,2,22) or bitnumIntl(r,8,23) or
                bitnumIntl(r,18,24) or bitnumIntl(r,12,25) or bitnumIntl(r,29,26) or bitnumIntl(r,5,27) or
                bitnumIntl(r,21,28) or bitnumIntl(r,10,29) or bitnumIntl(r,3,30) or bitnumIntl(r,24,31))
        }

        private fun cryptBlock(input: ByteArray, schedule: Array<IntArray>): ByteArray {
            var (s0, s1) = initialPermutation(input)
            for (i in 0 until 15) {
                val prev = s1
                s1 = desF(s1, schedule[i]) xor s0
                s0 = prev
            }
            s0 = desF(s1, schedule[15]) xor s0
            return inversePermutation(s0, s1)
        }

        private fun keySchedule(key: ByteArray, decrypt: Boolean): Array<IntArray> {
            val schedule = Array(16) { IntArray(6) }
            val shifts = intArrayOf(1,1,2,2,2,2,2,2,1,2,2,2,2,2,2,1)
            val pc = intArrayOf(56,48,40,32,24,16,8,0,57,49,41,33,25,17,9,1,58,50,42,34,26,18,10,2,59,51,43,35)
            val pd = intArrayOf(62,54,46,38,30,22,14,6,61,53,45,37,29,21,13,5,60,52,44,36,28,20,12,4,27,19,11,3)
            val kc = intArrayOf(13,16,10,23,0,4,2,27,14,5,20,9,22,18,11,3,25,7,15,6,26,19,12,1,40,51,30,36,46,54,29,39,50,44,32,47,43,48,38,55,33,52,45,41,49,35,28,31)
            var c = 0; var d = 0
            for (i in 0 until 28) {
                c = (c + bitnum(key, pc[i], 31 - i))
                d = (d + bitnum(key, pd[i], 31 - i))
            }
            for (i in 0 until 16) {
                c = ((c shl shifts[i]) or (c ushr (28 - shifts[i]))) and 0x0FFFFFFF
                d = ((d shl shifts[i]) or (d ushr (28 - shifts[i]))) and 0x0FFFFFFF
                val idx = if (decrypt) 15 - i else i
                for (j in 0 until 24) schedule[idx][j / 8] = schedule[idx][j / 8] or bitnumIntr(c, kc[j], 7 - (j % 8))
                for (j in 24 until 48) schedule[idx][j / 8] = schedule[idx][j / 8] or bitnumIntr(d, kc[j] - 27, 7 - (j % 8))
            }
            return schedule
        }

        private fun tripleDesDecrypt(bytes: ByteArray): ByteArray {
            val key = QRC_KEY.toByteArray(Charsets.UTF_8)
            val s1 = keySchedule(key.copyOfRange(16, 24), true)
            val s2 = keySchedule(key.copyOfRange(8, 16), false)
            val s3 = keySchedule(key.copyOfRange(0, 8), true)
            val output = java.io.ByteArrayOutputStream()
            var i = 0
            while (i + 8 <= bytes.size) {
                var block = bytes.copyOfRange(i, i + 8)
                block = cryptBlock(block, s1)
                block = cryptBlock(block, s2)
                block = cryptBlock(block, s3)
                output.write(block)
                i += 8
            }
            return output.toByteArray()
        }

        private fun hexToBytes(hex: String): ByteArray {
            val clean = hex.replace(Regex("[^0-9A-Fa-f]"), "")
            val bytes = ByteArray(clean.length / 2)
            for (i in bytes.indices) {
                bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return bytes
        }

        fun decryptQrcStr(raw: String): String {
            if (raw.isBlank()) return ""
            val bytes = hexToBytes(raw)
            if (bytes.isEmpty() || bytes.size % 8 != 0) {
                Log.d(TAG, "decryptQrcStr: invalid hex, len=${bytes.size}")
                return ""
            }
            val decrypted = try {
                tripleDesDecrypt(bytes)
            } catch (e: Exception) {
                Log.d(TAG, "decryptQrcStr: 3DES failed", e)
                return ""
            }
            // Log first bytes to verify DES output
            Log.d(TAG, "decryptQrcStr: 3DES output len=${decrypted.size} first=${decrypted.take(8).joinToString { (it.toInt() and 0xFF).toString(16).padStart(2, '\u0030') }}")
            return try {
                val inflated = inflateToString(decrypted)
                if (inflated.isNotBlank()) return inflated
                Log.d(TAG, "decryptQrcStr: inflate returned empty, trying raw")
                val raw = String(decrypted, Charsets.UTF_8)
                Log.d(TAG, "decryptQrcStr: raw text=${raw.take(40)}")
                raw
            } catch (e: Exception) {
                Log.d(TAG, "decryptQrcStr: inflate failed: ${e.message}, trying raw")
                try {
                    val r = String(decrypted, Charsets.UTF_8)
                    Log.d(TAG, "decryptQrcStr: raw text=${r.take(40)}")
                    r
                } catch (e2: Exception) { "" }
            }
        }

        private fun inflateToString(data: ByteArray): String {
            // try raw deflate first (no header), then zlib
            for (nowrap in booleanArrayOf(true, false)) {
                try {
                    val inflater = Inflater(nowrap)
                    inflater.setInput(data)
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    while (!inflater.finished()) {
                        val len = inflater.inflate(buf)
                        if (len > 0) out.write(buf, 0, len)
                    }
                    inflater.end()
                    val result = out.toString("UTF-8")
                    if (result.isNotBlank()) return result
                } catch (e: Exception) { /* try next */ }
            }
            return ""
        }
    }
}
