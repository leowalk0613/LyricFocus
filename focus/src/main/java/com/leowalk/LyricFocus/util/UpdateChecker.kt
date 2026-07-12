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
        val releaseNotes: String?,
        val githubUrl: String?,
        val giteeUrl: String?,
        val apkUrl: String?,
        val error: String? = null
    )

    fun checkForUpdates(): UpdateInfo {
        val latch = CountDownLatch(2)
        val results = mutableListOf<UpdateInfo>()

        Thread {
            checkGitHub()?.let { results.add(it) }
            latch.countDown()
        }.start()

        Thread {
            checkGitee()?.let { results.add(it) }
            latch.countDown()
        }.start()

        latch.await(15, TimeUnit.SECONDS)

        return mergeResults(results)
    }

    private fun mergeResults(results: List<UpdateInfo>): UpdateInfo {
        if (results.isEmpty()) {
            return UpdateInfo(
                hasUpdate = false,
                latestVersion = null,
                releaseNotes = null,
                githubUrl = null,
                giteeUrl = null,
                apkUrl = null
            )
        }

        val githubResult = results.find { it.githubUrl != null }
        val giteeResult = results.find { it.giteeUrl != null }

        return if (githubResult != null && giteeResult != null) {
            val githubNewer = compareVersions(githubResult.latestVersion ?: "", giteeResult.latestVersion ?: "") >= 0
            if (githubNewer) githubResult else giteeResult
        } else {
            githubResult ?: giteeResult ?: results.first()
        }
    }

    private fun checkGitHub(): UpdateInfo? {
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
                val bodyText = json.optString("body", "")

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
                    releaseNotes = bodyText,
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

    private fun checkGitee(): UpdateInfo? {
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
                val htmlUrl = json.optString("html_url", "")
                val bodyText = json.optString("body", "")

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
                    releaseNotes = bodyText,
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
            "1.6.0"
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

    fun resolveReleaseNotes(context: Context, remoteNotes: String?, hasUpdate: Boolean): String {
        if (hasUpdate && !remoteNotes.isNullOrBlank()) {
            return remoteNotes
        }
        getBundledReleaseNotes(context)?.let { return it }
        return remoteNotes?.takeIf { it.isNotBlank() } ?: "暂无更新日志"
    }
}