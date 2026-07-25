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
import java.util.concurrent.ConcurrentHashMap

class AiLyricTranslator(context: Context) {

    private val appContext = context.applicationContext
    private val client = HttpClient.instance
    private val cache = ConcurrentHashMap<String, LyricInfo>()

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

        val cacheKey = buildCacheKey(title, artist, lyricInfo, translateAll)
        cache[cacheKey]?.let { return it }

        val translated = requestTranslation(lyricInfo, title, artist) ?: return lyricInfo
        cache[cacheKey] = translated
        return translated
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
        translateAll: Boolean
    ): String {
        return listOf(
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
