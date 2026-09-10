package com.leowalk.LyricFocus.service

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import com.leowalk.LyricFocus.lyric.LyricInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 外部歌词推送协议（纯推送）：LyricFocus 只搜词/对齐/推送，渲染由接收方完成。
 *
 * 第三方在 Manifest 声明：
 * ```xml
 * <meta-data
 *     android:name="com.leowalk.LyricFocus.EXTERNAL_LYRIC"
 *     android:value="content://your.pkg.authority" />
 * ```
 *
 * Provider 需实现 `putlyric`（轻量）与 `putlyricfd`（全量 FD）；可选 `settings`。
 */
object ExternalLyricProtocol {
    private const val TAG = "ExternalLyricProtocol"

    const val META_KEY = "com.leowalk.LyricFocus.EXTERNAL_LYRIC"
    const val SCHEMA_VERSION = 1

    const val METHOD_PUT_LYRIC = "putlyric"
    const val METHOD_PUT_LYRIC_FD = "putlyricfd"
    const val METHOD_SETTINGS = "settings"

    /** 尚未声明 meta-data 的内置兼容端 */
    val BUILTIN_URIS: List<String> = listOf(
        "content://com.leowalk.aodchange.notifications",
        "content://com.leowalk.musiclockscreen.lyric"
    )

    data class Endpoint(
        val uri: Uri,
        val authority: String
    ) {
        val key: String get() = authority
    }

    data class PushPayload(
        val lyricLine: String,
        val secondLine: String,
        val timeMs: Long,
        val title: String,
        val artist: String,
        val musicPackage: String = "",
        val playing: Boolean = true,
        val loading: Boolean = false,
        val ctx: JSONObject? = null
    )

    fun discoverEndpoints(context: Context): List<Endpoint> {
        val found = LinkedHashMap<String, Endpoint>()
        val pm = context.packageManager
        try {
            @Suppress("DEPRECATION")
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val value = app.metaData?.getString(META_KEY)?.trim().orEmpty()
                if (value.isBlank()) continue
                parseEndpoint(value)?.let { ep ->
                    if (providerExists(pm, ep.authority)) {
                        found.putIfAbsent(ep.key, ep)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "discoverEndpoints scan failed", e)
        }
        for (raw in BUILTIN_URIS) {
            parseEndpoint(raw)?.let { ep ->
                if (providerExists(pm, ep.authority)) {
                    found.putIfAbsent(ep.key, ep)
                }
            }
        }
        return found.values.toList()
    }

    private fun parseEndpoint(raw: String): Endpoint? {
        // 兼容旧草稿格式 "uri|mode"：只取 URI 部分
        val uriPart = raw.substringBefore('|').trim()
        if (uriPart.isBlank()) return null
        return try {
            val uri = Uri.parse(uriPart)
            val authority = uri.authority?.takeIf { it.isNotBlank() } ?: return null
            Endpoint(uri = uri, authority = authority)
        } catch (_: Exception) {
            null
        }
    }

    private fun providerExists(pm: PackageManager, authority: String): Boolean {
        return try {
            pm.resolveContentProvider(authority, 0) != null
        } catch (_: Exception) {
            false
        }
    }

    fun buildContextJson(
        lyricInfo: LyricInfo,
        title: String,
        positionMs: Long,
        syncAdvanceMs: Long
    ): JSONObject? {
        if (lyricInfo.isEmpty) return null
        val rawLines = lyricInfo.lines
        if (rawLines.isEmpty()) return null

        var lines = rawLines
        if (title.isNotBlank() && lines.size >= 2) {
            val first = lines[0]
            val second = lines[1]
            val firstText = first.text.trim()
            val t1 = firstText.lowercase().replace(" ", "")
            val t2 = title.lowercase().replace(" ", "")
            val titleMatches = t1.isNotEmpty() && (t2.startsWith(t1) || t1.startsWith(t2))
            if (titleMatches) {
                val nearStart = first.time <= 1000L
                val farFromNext = second.time - first.time > 10_000L
                if (nearStart || farFromNext) {
                    lines = lines.drop(1)
                }
            }
        }

        val currentIndex = lyricInfo.getCurrentLineIndex(positionMs, syncAdvanceMs)
        val offset = rawLines.size - lines.size
        val shiftedIndex = if (currentIndex < 0) -1 else currentIndex - offset

        val arr = JSONArray()
        for (i in lines.indices) {
            val line = lines[i]
            arr.put(
                JSONObject().apply {
                    put("t", line.text.trim())
                    put("tm", line.time)
                    put("r", line.translation?.replace('\n', ' ')?.trim() ?: "")
                    put("isCur", i == shiftedIndex)
                }
            )
        }
        if (arr.length() == 0) return null
        return JSONObject().apply {
            put("idx", shiftedIndex)
            put("lines", arr)
        }
    }

    fun buildJson(payload: PushPayload, includeCtx: Boolean): JSONObject {
        return JSONObject().apply {
            put("v", SCHEMA_VERSION)
            put("l", payload.lyricLine)
            put("s", payload.secondLine)
            put("t", payload.timeMs)
            put("title", payload.title)
            put("artist", payload.artist)
            if (payload.musicPackage.isNotBlank()) put("pkg", payload.musicPackage)
            put("playing", payload.playing)
            put("loading", payload.loading)
            if (includeCtx && payload.ctx != null) put("ctx", payload.ctx)
        }
    }

    /** 有状态推送器：每 endpoint 独立去重，避免一端消费全量后另一端永远收不到 ctx。 */
    class Pusher(
        private val context: Context,
        private val cacheDir: File
    ) {
        private data class DedupState(
            var linesKey: String = "",
            var ctxIdx: Int = -2,
            var lastL: String = "",
            var lastS: String = ""
        )

        private val stateByEndpoint = ConcurrentHashMap<String, DedupState>()
        @Volatile
        private var cachedEndpoints: List<Endpoint>? = null

        fun invalidateDiscovery() {
            cachedEndpoints = null
        }

        fun resetDedup() {
            stateByEndpoint.clear()
        }

        fun endpoints(): List<Endpoint> {
            cachedEndpoints?.let { return it }
            val list = discoverEndpoints(context)
            cachedEndpoints = list
            return list
        }

        fun push(payload: PushPayload) {
            val ctx = payload.ctx
            val linesKey = ctx?.optJSONArray("lines")?.toString() ?: ""
            val ctxIdx = ctx?.optInt("idx", -1) ?: -1
            for (ep in endpoints()) {
                pushOne(ep, payload, linesKey, ctxIdx)
            }
        }

        /** 切歌时空数据 / loading，清接收方残留 */
        fun clear(title: String, artist: String) {
            val payload = PushPayload(
                lyricLine = "",
                secondLine = "",
                timeMs = 0L,
                title = title,
                artist = artist,
                playing = false,
                loading = true,
                ctx = null
            )
            val json = buildJson(payload, includeCtx = false).toString()
            for (ep in endpoints()) {
                try {
                    val extras = Bundle().apply { putString("n", json) }
                    context.contentResolver.call(ep.uri, METHOD_PUT_LYRIC, null, extras)
                } catch (e: Exception) {
                    Log.d(TAG, "clear failed ${ep.authority}: ${e.message}")
                }
            }
            resetDedup()
        }

        fun readSyncAdvanceMs(fallback: Long): Long {
            for (ep in endpoints()) {
                try {
                    val r = context.contentResolver.call(ep.uri, METHOD_SETTINGS, null, null)
                    val json = r?.getString("n")
                    if (!json.isNullOrBlank() && json != "{}") {
                        val s = JSONObject(json)
                        if (s.has("lyric_advance_ms")) {
                            return s.getInt("lyric_advance_ms").toLong()
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            return fallback
        }

        private fun pushOne(
            ep: Endpoint,
            payload: PushPayload,
            linesKey: String,
            ctxIdx: Int
        ) {
            val st = stateByEndpoint.getOrPut(ep.key) { DedupState() }
            val full = linesKey.isNotEmpty() && linesKey != st.linesKey
            if (full) {
                st.linesKey = linesKey
            }
            if (!full &&
                ctxIdx == st.ctxIdx &&
                payload.lyricLine == st.lastL &&
                payload.secondLine == st.lastS
            ) {
                return
            }
            st.ctxIdx = ctxIdx
            st.lastL = payload.lyricLine
            st.lastS = payload.secondLine
            try {
                if (full) {
                    sendFd(ep, buildJson(payload, includeCtx = true).toString())
                } else {
                    val extras = Bundle().apply {
                        putString("n", buildJson(payload, includeCtx = false).toString())
                    }
                    context.contentResolver.call(ep.uri, METHOD_PUT_LYRIC, null, extras)
                }
            } catch (e: Exception) {
                Log.d(TAG, "push failed ${ep.authority}: ${e.message}")
            }
        }

        private fun sendFd(ep: Endpoint, json: String) {
            val prefix = "ext_lyric_${ep.authority.replace('.', '_')}_"
            val file = File(cacheDir, "${prefix}${System.currentTimeMillis()}.json")
            file.writeText(json)
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val extras = Bundle().apply {
                    putParcelable("fd", pfd)
                }
                context.contentResolver.call(ep.uri, METHOD_PUT_LYRIC_FD, null, extras)
            } finally {
                try {
                    pfd.close()
                } catch (_: Exception) {
                }
            }
            try {
                cacheDir.listFiles { f -> f.name.startsWith(prefix) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(3)
                    ?.forEach { it.delete() }
            } catch (_: Exception) {
            }
        }
    }
}
