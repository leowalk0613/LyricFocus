package com.leowalk.LyricFocus.util

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UpdateChecker(private val context: Context) {

    private val TAG = "LyricFocus_Update"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val currentVersion = getCurrentVersion(context)

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String?,
        /** 后台检测不填充；点开弹窗后再 [fetchReleaseNotes] */
        val releaseNotes: String? = null,
        val githubUrl: String?,
        val giteeUrl: String?,
        val apkUrl: String?,
        val error: String? = null
    )

    /**
     * 轻量检测：只比对本地版本与远端 tag，不解析/携带更新日志正文。
     */
    fun checkForUpdates(): UpdateInfo {
        val latch = CountDownLatch(2)
        val results = mutableListOf<UpdateInfo>()

        Thread {
            checkGitHub(includeNotes = false)?.let { results.add(it) }
            latch.countDown()
        }.start()

        Thread {
            checkGitee(includeNotes = false)?.let { results.add(it) }
            latch.countDown()
        }.start()

        latch.await(15, TimeUnit.SECONDS)
        return mergeResults(results)
    }

    /**
     * 用户点开弹窗后再加载日志：有更新拉远端 body，否则读内置 assets。
     */
    fun fetchReleaseNotes(info: UpdateInfo): String {
        if (!info.hasUpdate) {
            return getBundledReleaseNotes(context) ?: "暂无更新日志"
        }
        val remote = when {
            !info.githubUrl.isNullOrBlank() ->
                checkGitHub(includeNotes = true)?.releaseNotes
            !info.giteeUrl.isNullOrBlank() ->
                checkGitee(includeNotes = true)?.releaseNotes
            else -> null
        }
        if (!remote.isNullOrBlank()) return remote
        // 主源失败时换另一源
        val fallback = when {
            !info.githubUrl.isNullOrBlank() ->
                checkGitee(includeNotes = true)?.releaseNotes
            else -> checkGitHub(includeNotes = true)?.releaseNotes
        }
        if (!fallback.isNullOrBlank()) return fallback
        return getBundledReleaseNotes(context) ?: "暂无更新日志"
    }

    private fun mergeResults(results: List<UpdateInfo>): UpdateInfo {
        if (results.isEmpty()) {
            return UpdateInfo(
                hasUpdate = false,
                latestVersion = null,
                githubUrl = null,
                giteeUrl = null,
                apkUrl = null
            )
        }

        val githubResult = results.find { it.githubUrl != null }
        val giteeResult = results.find { it.giteeUrl != null }

        return if (githubResult != null && giteeResult != null) {
            val githubNewer = compareVersions(
                githubResult.latestVersion ?: "",
                giteeResult.latestVersion ?: ""
            ) >= 0
            if (githubNewer) githubResult else giteeResult
        } else {
            githubResult ?: giteeResult ?: results.first()
        }
    }

    private fun checkGitHub(includeNotes: Boolean): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/leowalk0613/LyricFocus/releases/latest")
                .header("User-Agent", "LyricFocus/UpdateChecker")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "GitHub API failed: ${response.code}")
                    return null
                }

                val body = response.body?.string() ?: return null
                val json = JSONObject(body)

                val tagName = json.optString("tag_name", "")
                val latestVersion = tagName.replaceFirst("^[vVxX]+".toRegex(), "")
                if (latestVersion.isBlank()) {
                    Log.d(TAG, "GitHub version is blank")
                    return null
                }

                val htmlUrl = json.optString("html_url", "")
                val bodyText = if (includeNotes) json.optString("body", "") else null

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i)
                        val name = asset?.optString("name", "")
                        if (name?.endsWith(".apk") == true) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val hasUpdate = compareVersions(currentVersion, latestVersion) < 0
                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = latestVersion,
                    releaseNotes = bodyText?.takeIf { it.isNotBlank() },
                    githubUrl = htmlUrl,
                    giteeUrl = null,
                    apkUrl = apkUrl
                )
            }
        } catch (e: IOException) {
            Log.d(TAG, "GitHub request failed: ${e.message}")
            null
        } catch (e: Exception) {
            Log.d(TAG, "GitHub parse failed: ${e.message}")
            null
        }
    }

    private fun checkGitee(includeNotes: Boolean): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url("https://gitee.com/api/v5/repos/leowalk0613/LyricFocus/releases/latest")
                .header("User-Agent", "LyricFocus/UpdateChecker")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "Gitee API failed: ${response.code}")
                    return null
                }

                val body = response.body?.string() ?: return null
                val json = JSONObject(body)

                val tagName = json.optString("tag_name", "")
                val latestVersion = tagName.replaceFirst("^[vVxX]+".toRegex(), "")
                if (latestVersion.isBlank()) return null
                val htmlUrl = json.optString("html_url", "")
                val bodyText = if (includeNotes) json.optString("body", "") else null

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i)
                        val name = asset?.optString("name", "")
                        if (name?.endsWith(".apk") == true) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val hasUpdate = compareVersions(currentVersion, latestVersion) < 0
                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = latestVersion,
                    releaseNotes = bodyText?.takeIf { it.isNotBlank() },
                    githubUrl = null,
                    giteeUrl = htmlUrl,
                    apkUrl = apkUrl
                )
            }
        } catch (e: IOException) {
            Log.d(TAG, "Gitee request failed: ${e.message}")
            null
        } catch (e: Exception) {
            Log.d(TAG, "Gitee parse failed: ${e.message}")
            null
        }
    }

    fun compareVersions(current: String, latest: String): Int {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrNull(i) ?: 0
            val latestPart = latestParts.getOrNull(i) ?: 0
            if (currentPart < latestPart) return -1
            if (currentPart > latestPart) return 1
        }
        return 0
    }

    fun getCurrentVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName
        } catch (e: Exception) {
            "1.6.1"
        }
    }

    fun getBundledReleaseNotes(context: Context): String? {
        val assetName = "release_notes_${getCurrentVersion(context).replace('.', '_')}.md"
        return try {
            context.assets.open(assetName).bufferedReader().use { it.readText().trim() }
        } catch (_: Exception) {
            null
        }
    }
}
