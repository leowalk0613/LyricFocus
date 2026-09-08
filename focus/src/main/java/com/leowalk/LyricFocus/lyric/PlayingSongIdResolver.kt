package com.leowalk.LyricFocus.lyric

import android.media.MediaMetadata
import android.util.Log
import com.leowalk.LyricFocus.FocusPreferences
import org.json.JSONObject

/**
 * 从当前播放会话解析平台歌曲 ID，供直拉歌词跳过搜歌名。
 * - 网易云：focus media / MEDIA_ID → 数字 songId
 * - QQ 音乐：MEDIA_ID → 数字 songId
 * - 小米音乐：focus media shareContent 的 songmid（勿信不可靠的 mediaId）
 */
object PlayingSongIdResolver {
    private const val TAG = "PlayingSongId"

    const val PKG_NETEASE = "com.netease.cloudmusic"
    const val PKG_QQ = "com.tencent.qqmusic"
    const val PKG_MIUI = "com.miui.player"

    private val NETEASE_SONG_ID_IN_URL = Regex("""(?:song\?|[?&]id=)(\d{5,})""")
    private val SONGMID_IN_URL = Regex("""[?&]songmid=([0-9A-Za-z]+)""")

    data class ResolvedId(
        val platform: String,
        val songId: Long = 0L,
        val songMid: String? = null,
    ) {
        fun hasDirectKey(): Boolean = songId > 0L || !songMid.isNullOrBlank()
    }

    fun resolve(
        packageName: String?,
        metadata: MediaMetadata?,
        focusMediaJsons: List<String> = emptyList(),
    ): ResolvedId? {
        val pkg = packageName.orEmpty()
        when {
            pkg == PKG_NETEASE || pkg.contains("netease", ignoreCase = true) ->
                resolveNetEase(metadata, focusMediaJsons)?.let {
                    return ResolvedId(FocusPreferences.LYRIC_SOURCE_NETEASE, songId = it)
                }
            pkg == PKG_MIUI || pkg.contains("miui.player", ignoreCase = true) ->
                resolveMiuiSongMid(focusMediaJsons)?.let {
                    return ResolvedId(FocusPreferences.LYRIC_SOURCE_QQ, songMid = it)
                }
            pkg == PKG_QQ || pkg.contains("qqmusic", ignoreCase = true) ->
                resolveQq(metadata)?.let {
                    return ResolvedId(FocusPreferences.LYRIC_SOURCE_QQ, songId = it)
                }
        }
        return null
    }

    private fun resolveNetEase(metadata: MediaMetadata?, focusMediaJsons: List<String>): Long? {
        for (json in focusMediaJsons) {
            parseNetEaseIdFromFocusJson(json)?.let {
                Log.d(TAG, "netease id from focus media: $it")
                return it
            }
        }
        parseNumericMediaId(metadata)?.let {
            Log.d(TAG, "netease id from MEDIA_ID: $it")
            return it
        }
        return null
    }

    private fun resolveQq(metadata: MediaMetadata?): Long? {
        return parseNumericMediaId(metadata)?.also {
            Log.d(TAG, "qq id from MEDIA_ID: $it")
        }
    }

    private fun resolveMiuiSongMid(focusMediaJsons: List<String>): String? {
        for (json in focusMediaJsons) {
            parseSongMidFromFocusJson(json)?.let {
                Log.d(TAG, "miui songmid from focus media: $it")
                return it
            }
        }
        return null
    }

    fun parseNetEaseIdFromFocusJson(json: String?): Long? {
        if (json.isNullOrBlank()) return null
        return try {
            val shareContent = JSONObject(json)
                .optJSONObject("param_v2")
                ?.optJSONObject("param_island")
                ?.optJSONObject("shareData")
                ?.optString("shareContent")
            parseNetEaseIdFromText(shareContent) ?: parseNetEaseIdFromText(json)
        } catch (_: Throwable) {
            parseNetEaseIdFromText(json)
        }
    }

    fun parseSongMidFromFocusJson(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return try {
            val shareContent = JSONObject(json)
                .optJSONObject("param_v2")
                ?.optJSONObject("param_island")
                ?.optJSONObject("shareData")
                ?.optString("shareContent")
            parseSongMidFromText(shareContent) ?: parseSongMidFromText(json)
        } catch (_: Throwable) {
            parseSongMidFromText(json)
        }
    }

    fun parseNetEaseIdFromText(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.trim().toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        return NETEASE_SONG_ID_IN_URL.find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun parseSongMidFromText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return SONGMID_IN_URL.find(raw)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    fun parseNumericMediaId(metadata: MediaMetadata?): Long? {
        val raw = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: return null
        return raw.trim().toLongOrNull()?.takeIf { it > 0 }
    }
}
