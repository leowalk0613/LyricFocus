package com.leowalk.LyricFocus.lyric

import android.util.Log
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

class LRCLibLyricProvider : LyricProvider {
    override val id: String = "lrclib"
    override val name: String = "LRCLib"

    private val client = HttpClient.instance

    override suspend fun searchLyric(title: String, artist: String, album: String): LyricInfo? {
        return try {
            val encodedQ = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encodedQ&artist_name=$encodedArtist"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LyricFocus/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val results = JSONArray(body)
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val synced = item.optString("syncedLyrics", "")
                    if (synced.isBlank()) continue

                    val itemTitle = item.optString("trackName", "")
                    val itemArtist = item.optString("artistName", "")
                    val titleScore = LyricSearchHelper.scoreTitleMatch(itemTitle, title, artist)
                    if (titleScore <= 0) continue

                    val lyricInfo = LrcParser.parse(synced)
                    if (lyricInfo.lines.size < 2) continue

                    Log.d(TAG, "LRCLib found: $itemTitle - $itemArtist (${lyricInfo.lines.size} lines)")
                    return lyricInfo.copy(
                        title = itemTitle,
                        artist = itemArtist,
                        album = item.optString("albumName", ""),
                        source = name
                    )
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "LRCLib error", e)
            null
        }
    }

    companion object {
        private const val TAG = "LRCLibLyricProvider"
    }
}
