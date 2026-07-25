package com.leowalk.LyricFocus.lyric

import android.content.Context
import android.util.Log
import com.leowalk.LyricFocus.FocusPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class AiLyricTranslator(context: Context) {

    private val appContext = context.applicationContext
    private val client = HttpClient.instance
    private val cacheDir = File(appContext.cacheDir, "AiLyricCache").also { it.mkdirs() }
    private val memoryCache = mutableMapOf<String, LyricInfo>()

    data class ApiConfig(
        val baseUrl: String,
        val apiKey: String,
        val model: String
    )

    sealed class ConnectivityResult {
        data class Success(val message: String) : ConnectivityResult()
        data class Failure(val message: String) : ConnectivityResult()
    }

    suspend fun translateIfNeeded(lyricInfo: LyricInfo, title: String, artist: String): LyricInfo {
        if (!hasConfiguredApi()) {
            Log.d("LyricFocusAI", "AI translate skipped: API not configured")
            return lyricInfo
        }
        if (lyricInfo.lines.isEmpty()) return lyricInfo

        val translateAll = FocusPreferences.isAiTranslateAllLyrics(appContext)
        if (!translateAll && lyricInfo.lines.any { !it.translation.isNullOrBlank() }) {
            return lyricInfo
        }

        val cacheKey = buildCacheKey(title, artist, lyricInfo, translateAll, "translate")
        memoryCache[cacheKey]?.let { return it }
        if (FocusPreferences.isAiCacheEnabled(appContext)) {
            loadCached(cacheKey)?.let { memoryCache[cacheKey] = it; return it }
        }

        val translated = requestTranslation(lyricInfo, title, artist) ?: return lyricInfo
        memoryCache[cacheKey] = translated
        if (FocusPreferences.isAiCacheEnabled(appContext)) {
            saveCache(cacheKey, translated, "translate")
        }
        return translated
    }

    suspend fun polishIfNeeded(lyricInfo: LyricInfo, title: String, artist: String): LyricInfo {
        if (!hasConfiguredApi()) {
            Log.d("LyricFocusAI", "AI polish skipped: API not configured")
            return lyricInfo
        }
        if (!FocusPreferences.isAiPolishEnabled(appContext)) {
            Log.d("LyricFocusAI", "AI polish skipped: disabled")
            return lyricInfo
        }
        if (lyricInfo.lines.isEmpty()) return lyricInfo

        val cacheKey = buildCacheKey(title, artist, lyricInfo, false, "polish")
        memoryCache[cacheKey]?.let { return it }
        if (FocusPreferences.isAiCacheEnabled(appContext)) {
            loadCached(cacheKey)?.let { memoryCache[cacheKey] = it; return it }
        }

        val polished = requestPolishing(lyricInfo, title, artist) ?: return lyricInfo
        memoryCache[cacheKey] = polished
        if (FocusPreferences.isAiCacheEnabled(appContext)) {
            saveCache(cacheKey, polished, "polish")
        }
        return polished
    }

    fun hasConfiguredApi(): Boolean {
        return FocusPreferences.getAiApiKey(appContext).isNotBlank() &&
            FocusPreferences.getAiApiBaseUrl(appContext).isNotBlank() &&
            FocusPreferences.getAiApiModel(appContext).isNotBlank()
    }

    suspend fun testConnectivity(config: ApiConfig): ConnectivityResult = withContext(Dispatchers.IO) {
        val baseUrl = config.baseUrl.trim()
        val apiKey = config.apiKey.trim()
        val model = config.model.trim()
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return@withContext ConnectivityResult.Failure("请先填写 Base URL、API Key 与模型名称")
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put("max_tokens", 8)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Reply with OK only.")
                })
            })
        }

        val request = Request.Builder()
            .url(resolveChatCompletionsUrl(baseUrl))
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", "LyricFocus/1.6.2")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = extractErrorMessage(body)?.let { "：$it" }.orEmpty()
                    return@withContext ConnectivityResult.Failure(
                        "连接失败（HTTP ${response.code}$detail）"
                    )
                }
                val hasChoices = JSONObject(body).optJSONArray("choices")?.length()?.let { it > 0 } == true
                if (!hasChoices) {
                    return@withContext ConnectivityResult.Failure("接口已响应，但未返回有效的 completions 结果")
                }
                ConnectivityResult.Success("连通正常（HTTP ${response.code}，模型 $model）")
            }
        } catch (e: Exception) {
            ConnectivityResult.Failure("连接失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun requestTranslation(
        lyricInfo: LyricInfo,
        title: String,
        artist: String
    ): LyricInfo? {
        val payload = buildTranslationPayload(lyricInfo, title, artist)
        val request = Request.Builder()
            .url(resolveChatCompletionsUrl(FocusPreferences.getAiApiBaseUrl(appContext)))
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer ${FocusPreferences.getAiApiKey(appContext)}")
            .header("Content-Type", "application/json")
            .header("User-Agent", "LyricFocus/1.6.2")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("LyricFocusAI", "AI translate HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                return null
            }
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val content = json
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?: return null.also { Log.w("LyricFocusAI", "AI translate: empty response content") }
            val usage = json.optJSONObject("usage")
            if (usage != null) {
                val totalTokens = usage.optLong("total_tokens", 0)
                if (totalTokens > 0) {
                    FocusPreferences.addAiTokens(appContext, totalTokens)
                }
            }
            val result = mergeTranslation(lyricInfo, content)
            if (result == null) Log.w("LyricFocusAI", "AI translate: mergeTranslation returned null")
            return result
        }
    }

    private suspend fun requestPolishing(
        lyricInfo: LyricInfo,
        title: String,
        artist: String
    ): LyricInfo? {
        val payload = buildPolishPayload(lyricInfo, title, artist)
        val request = Request.Builder()
            .url(resolveChatCompletionsUrl(FocusPreferences.getAiApiBaseUrl(appContext)))
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer ${FocusPreferences.getAiApiKey(appContext)}")
            .header("Content-Type", "application/json")
            .header("User-Agent", "LyricFocus/1.6.2")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("LyricFocusAI", "AI polish HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                return null
            }
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val content = json
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?: return null.also { Log.w("LyricFocusAI", "AI polish: empty response content") }
            val usage = json.optJSONObject("usage")
            if (usage != null) {
                val totalTokens = usage.optLong("total_tokens", 0)
                if (totalTokens > 0) {
                    FocusPreferences.addAiTokens(appContext, totalTokens)
                }
            }
            val result = mergePolishing(lyricInfo, content)
            if (result == null) Log.w("LyricFocusAI", "AI polish: mergePolishing returned null")
            return result
        }
    }

    private fun mergePolishing(original: LyricInfo, rawOutput: String): LyricInfo? {
        val polishedLrc = extractFinalBlock(rawOutput) ?: rawOutput
        val polishedLines = LrcParser.parse(polishedLrc).lines
        if (polishedLines.isEmpty()) return null

        val merged = original.lines.mapIndexed { index, line ->
            val polishedText = when {
                polishedLines.size == original.lines.size ->
                    polishedLines[index].text
                else -> findClosestLine(line.time, polishedLines)?.text
            }
            val cleaned = polishedText?.takeIf { it.isNotBlank() && it != line.text }
            line.copy(polished = cleaned)
        }
        if (merged.none { it.polished != null }) return null
        return original.copy(
            lines = merged,
            source = "${original.source} + AI润色"
        )
    }

    private fun buildPolishPayload(
        lyricInfo: LyricInfo,
        title: String,
        artist: String
    ): JSONObject {
        val systemPrompt = """
            你是歌词润色助手。请对提供的 LRC 歌词进行格式统一和语言润色：
            1. 严格保留原有时间轴标签 [mm:ss.xx]
            2. 统一标点符号（中文歌词用中文标点，外语歌词保留原标点）
            3. 修正明显的错别字或语法错误（但不要改变歌词原意）
            4. 删除歌词开头的歌曲名/歌手名/作词作曲信息
            5. 不要省略任何一行，不要合并行
            6. 最终结果放在 [FINAL] 与 [/FINAL] 之间，且仍为完整 LRC 文本
        """.trimIndent()

        val userPrompt = buildString {
            append("歌曲：").append(title)
            if (artist.isNotBlank()) {
                append(" - ").append(artist)
            }
            append('\n')
            append(lyricInfo.toLrcText())
        }

        return JSONObject().apply {
            put("model", FocusPreferences.getAiApiModel(appContext))
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }
    }

    private fun buildTranslationPayload(
        lyricInfo: LyricInfo,
        title: String,
        artist: String
    ): JSONObject {
        val targetLang = FocusPreferences.getAiTargetLanguage(appContext)
        val systemPrompt = """
            你是歌词翻译助手。请将用户提供的 LRC 歌词翻译成$targetLang。
            要求：
            1. 严格保留原有时间轴标签，格式如 [mm:ss.xx]
            2. 只翻译歌词正文，不要翻译 [ti:] [ar:] [al:] 等元数据行
            3. 不要省略任何一行，不要合并行
            4. 若原文已是$targetLang，则原样返回
            5. 最终译文放在 [FINAL] 与 [/FINAL] 之间，且仍为完整 LRC 文本
        """.trimIndent()

        val userPrompt = buildString {
            append("歌曲：").append(title)
            if (artist.isNotBlank()) {
                append(" - ").append(artist)
            }
            append('\n')
            append(lyricInfo.toLrcText())
        }

        return JSONObject().apply {
            put("model", FocusPreferences.getAiApiModel(appContext))
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }
    }

    private fun mergeTranslation(original: LyricInfo, rawOutput: String): LyricInfo? {
        val translatedLrc = extractFinalBlock(rawOutput) ?: rawOutput
        val translatedLines = LrcParser.parse(translatedLrc).lines
        if (translatedLines.isEmpty()) return null

        val merged = original.lines.mapIndexed { index, line ->
            val translation = when {
                translatedLines.size == original.lines.size ->
                    translatedLines[index].text
                else -> findClosestLine(line.time, translatedLines)?.text
            }
            line.copy(translation = translation?.takeIf { it.isNotBlank() })
        }
        if (merged.none { !it.translation.isNullOrBlank() }) return null
        return original.copy(
            lines = merged,
            source = "${original.source} + AI翻译"
        )
    }

    private fun extractFinalBlock(rawOutput: String): String? {
        val match = FINAL_BLOCK.find(rawOutput) ?: return null
        return match.groupValues[1].trim()
    }

    private fun findClosestLine(time: Long, lines: List<LyricLine>): LyricLine? {
        return lines.minByOrNull { kotlin.math.abs(it.time - time) }
            ?.takeIf { kotlin.math.abs(it.time - time) <= 1500 }
    }

    private fun buildCacheKey(
        title: String,
        artist: String,
        lyricInfo: LyricInfo,
        translateAll: Boolean,
        type: String
    ): String {
        return listOf(
            type,
            title,
            artist,
            FocusPreferences.getAiApiModel(appContext),
            FocusPreferences.getAiTargetLanguage(appContext),
            if (translateAll) "all" else "missing",
            lyricInfo.lines.size.toString(),
            lyricInfo.lines.firstOrNull()?.text.orEmpty()
        ).joinToString("|")
    }

    private fun resolveChatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val root = JSONObject(body)
            val error = root.optJSONObject("error")
            error?.optString("message")?.takeIf { it.isNotBlank() }
                ?: root.optString("message").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            body.take(120).takeIf { it.isNotBlank() }
        }
    }

    private fun loadCached(key: String): LyricInfo? {
        val file = File(cacheDir, sha256(key))
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val data = json.getJSONObject("data")
            val linesArray = data.getJSONArray("lines")
            val lines = (0 until linesArray.length()).map {
                val obj = linesArray.getJSONObject(it)
                LyricLine(
                    time = obj.getLong("time"),
                    text = obj.getString("text"),
                    translation = obj.optString("translation").takeIf { it.isNotBlank() },
                    polished = obj.optString("polished").takeIf { it.isNotBlank() },
                    reading = obj.optString("reading").takeIf { it.isNotBlank() }
                )
            }
            LyricInfo(
                title = data.optString("title"),
                artist = data.optString("artist"),
                album = data.optString("album"),
                offset = data.optLong("offset"),
                lines = lines,
                source = data.optString("source")
            )
        } catch (e: Exception) {
            Log.w("LyricFocusAI", "Cache read error: ${e.message}")
            file.delete()
            null
        }
    }

    private fun saveCache(key: String, lyricInfo: LyricInfo, type: String) {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val json = JSONObject().apply {
                put("key", key)
                put("type", type)
                put("timestamp", System.currentTimeMillis())
                put("data", JSONObject().apply {
                    put("title", lyricInfo.title)
                    put("artist", lyricInfo.artist)
                    put("album", lyricInfo.album)
                    put("offset", lyricInfo.offset)
                    put("source", lyricInfo.source)
                    put("lines", JSONArray().apply {
                        for (line in lyricInfo.lines) {
                            put(JSONObject().apply {
                                put("time", line.time)
                                put("text", line.text)
                                line.translation?.takeIf { it.isNotBlank() }?.let { put("translation", it) }
                                line.polished?.takeIf { it.isNotBlank() }?.let { put("polished", it) }
                                line.reading?.takeIf { it.isNotBlank() }?.let { put("reading", it) }
                            })
                        }
                    })
                })
            }
            val file = File(cacheDir, sha256(key))
            file.writeText(json.toString())
            Log.d("LyricFocusAI", "Cache saved: $type ($key) -> ${file.name} (${file.length()} bytes)")
        } catch (e: Exception) {
            Log.w("LyricFocusAI", "Cache write error: ${e.message}")
        }
    }

    fun getCacheSizeBytes(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getCacheCount(): Int {
        return cacheDir.listFiles()?.size ?: 0
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        memoryCache.clear()
        FocusPreferences.setAiCacheSizeBytes(appContext, 0L)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val FINAL_BLOCK = Regex("""\[FINAL\](.*?)\[/FINAL\]""", RegexOption.DOT_MATCHES_ALL)
    }
}

private fun LyricInfo.toLrcText(): String {
    val meta = buildString {
        if (title.isNotBlank()) append("[ti:$title]\n")
        if (artist.isNotBlank()) append("[ar:$artist]\n")
        if (offset != 0L) append("[offset:$offset]\n")
    }
    val body = lines.joinToString("\n") { line -> "${line.toLrcTimestamp()}${line.text}" }
    return meta + body
}

private fun LyricLine.toLrcTimestamp(): String {
    val minutes = time / 60_000
    val seconds = (time % 60_000) / 1000
    val centiseconds = (time % 1000) / 10
    return String.format("[%02d:%02d.%02d]", minutes, seconds, centiseconds)
}
