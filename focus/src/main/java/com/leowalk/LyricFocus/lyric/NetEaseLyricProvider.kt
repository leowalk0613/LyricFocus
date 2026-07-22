package com.leowalk.LyricFocus.lyric

import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import com.leowalk.LyricFocus.FocusPreferences

class NetEaseLyricProvider : LyricProvider {
    override val id: String = FocusPreferences.LYRIC_SOURCE_NETEASE
    override val name: String = "网易云音乐"

    private val client = HttpClient.instance

    override suspend fun searchLyric(title: String, artist: String, album: String): LyricInfo? {
        return try {
            findKnownSongId(title, artist)?.let { knownId ->
                fetchLyricBySongId(knownId)?.let { return it }
            }
            val candidates = searchSongs(title, artist, album)
            for (candidate in candidates) {
                val (lyricText, tlyricText) = getLyricWithTranslation(candidate.id)
                if (lyricText.isNullOrBlank()) continue
                val lyricInfo = LrcParser.parseWithTranslation(lyricText, tlyricText)
                if (lyricInfo.lines.size < 3) continue
                return lyricInfo.copy(
                    title = candidate.title,
                    artist = candidate.artist,
                    album = candidate.album,
                    source = name
                )
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private data class KnownSongEntry(
        val id: Long,
        val titlePattern: Regex,
        val artistMatcher: (String) -> Boolean
    )

    /** 网易云搜索对极短/特殊标题不可靠时的已知单曲 ID 兜底 */
    private val knownSongs = listOf(
        KnownSongEntry(
            id = 1449599572L,
            titlePattern = Regex("""^p\.h\.?$""", RegexOption.IGNORE_CASE),
            artistMatcher = { artist ->
                val lower = artist.lowercase()
                lower.contains("seventhlinks") && lower.contains("v flower")
            }
        ),
        KnownSongEntry(
            id = 2672796492L,
            titlePattern = Regex("""それでも僕らは歌うことをやめない"""),
            artistMatcher = { artist ->
                artist.trim().equals("Leo/need", ignoreCase = true)
            }
        ),
        KnownSongEntry(
            id = 402801L,
            titlePattern = Regex("""^Reach Out To The Truth$""", RegexOption.IGNORE_CASE),
            artistMatcher = { artist ->
                val lower = artist.lowercase()
                lower.contains("平田志穂子")
            }
        )
    )

    private fun findKnownSongId(title: String, artist: String): Long? {
        val normalized = LyricSearchHelper.normalizeTitleForSearch(title, artist)
        for (entry in knownSongs) {
            val titleMatches = entry.titlePattern.matches(title.trim()) ||
                (normalized.isNotBlank() && entry.titlePattern.matches(normalized))
            if (titleMatches && entry.artistMatcher(artist)) {
                return entry.id
            }
        }
        return null
    }

    private suspend fun fetchLyricBySongId(songId: Long): LyricInfo? {
        val (lyricText, tlyricText) = getLyricWithTranslation(songId)
        if (lyricText.isNullOrBlank()) return null
        val lyricInfo = LrcParser.parseWithTranslation(lyricText, tlyricText)
        if (lyricInfo.lines.size < 3) return null
        return lyricInfo.copy(source = name)
    }

    private data class SongCandidate(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String
    )

    private suspend fun searchSongs(title: String, artist: String, album: String = ""): List<SongCandidate> {
        val seenIds = mutableSetOf<Long>()
        val ranked = mutableListOf<Pair<SongCandidate, Int>>()
        for (keyword in LyricSearchHelper.buildSearchKeywords(title, artist)) {
            for ((candidate, score) in searchSongsByKeyword(keyword, title, artist)) {
                if (seenIds.add(candidate.id)) {
                    val albumScore = LyricSearchHelper.scoreAlbumMatch(candidate.album, album)
                    ranked.add(candidate to (score + albumScore))
                }
            }
        }
        return ranked
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .ifEmpty {
                ranked.sortedByDescending { it.second }.take(3).map { it.first }
            }
    }

    private suspend fun searchSongsByKeyword(
        keyword: String,
        title: String,
        artist: String
    ): List<Pair<SongCandidate, Int>> {
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
                    songToCandidate(song, title) to scoreSongMatch(song, title, artist)
                }
                .sortedByDescending { it.second }
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
            artist = artistName,
            album = song.optJSONObject("album")?.optString("name", "") ?: ""
        )
    }

    private fun scoreSongMatch(song: JSONObject, title: String, artist: String): Int {
        val artists = song.optJSONArray("artists")
        val candidateArtists = if (artists != null) {
            (0 until artists.length())
                .map { artists.getJSONObject(it).optString("name", "") }
        } else {
            emptyList()
        }

        for (candidateArtist in candidateArtists) {
            if (LyricSearchHelper.isArtistBlacklisted(candidateArtist)) {
                return Int.MIN_VALUE
            }
        }

        var score = LyricSearchHelper.scoreTitleMatch(song.optString("name", ""), title, artist)
        if (title.isNotBlank() && score == 0) {
            return Int.MIN_VALUE
        }

        if (artist.isNotBlank()) {
            score += LyricSearchHelper.scoreArtistMatch(candidateArtists, artist)
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
