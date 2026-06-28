package com.leowalk.LyricFocus.util

import java.util.concurrent.TimeUnit

object RootHelper {

    private const val SU_TIMEOUT_SEC = 15L

    fun checkRootAccess(): Boolean {
        return try {
            runSuCommand("id")?.contains("uid=0") == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 结束 SystemUI 进程，系统会自动拉起新的系统界面。
     */
    fun restartSystemUi(): Boolean {
        if (!checkRootAccess()) {
            return false
        }
        runSuCommand("killall com.android.systemui", ignoreExitCode = true)
        runSuCommand("pkill -f com.android.systemui", ignoreExitCode = true)
        return true
    }

    fun restartSystemUiAsync(onResult: (success: Boolean, message: String?) -> Unit) {
        Thread {
            try {
                if (!checkRootAccess()) {
                    onResult(false, "未获取 Root 权限，请在 Magisk / KernelSU 中允许本应用")
                    return@Thread
                }
                restartSystemUi()
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "重启失败")
            }
        }.start()
    }

    private fun runSuCommand(command: String, ignoreExitCode: Boolean = false): String? {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val finished = process.waitFor(SU_TIMEOUT_SEC, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            throw IllegalStateException("Root 命令超时")
        }
        if (!ignoreExitCode && process.exitValue() != 0) {
            throw IllegalStateException(stderr.trim().ifBlank { "exit ${process.exitValue()}" })
        }
        return stdout.ifBlank { stderr }.trim().ifBlank { null }
    }

    /**
     * 读取文件内容（需要 Root 权限）
     */
    fun readFile(path: String): String? {
        return try {
            runSuCommand("cat '$path'")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 读取 LSPosed 日志目录下的所有日志文件
     */
    fun readLsposedLogs(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val logPaths = listOf(
            "/data/adb/lspd/log/startup.log",
            "/data/adb/lspd/log/error.log",
            "/data/adb/lspd/log/logcat.log"
        )
        for (path in logPaths) {
            val content = readFile(path)
            if (content != null) {
                val fileName = path.substringAfterLast("/")
                result[fileName] = content
            }
        }
        return result
    }
}
