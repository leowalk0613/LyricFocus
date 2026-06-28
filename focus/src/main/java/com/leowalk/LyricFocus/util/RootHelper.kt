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
     * 列出目录内容（需要 Root 权限）
     */
    fun listDirectory(path: String): List<String>? {
        return try {
            runSuCommand("ls '$path'")?.split("\n")?.filter { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 检查文件是否存在（需要 Root 权限）
     */
    fun fileExists(path: String): Boolean {
        return try {
            runSuCommand("test -e '$path' && echo 'yes'")?.contains("yes") == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 读取 LSPosed 日志目录下的所有日志文件
     * 支持多种 LSPosed 版本的日志路径
     */
    fun readLsposedLogs(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        // 尝试多个可能的日志目录
        val logDirs = listOf(
            "/data/adb/lspd/log",
            "/data/adb/lspd/logs",
            "/data/adb/modules/zygisk_lsposed/log",
            "/data/adb/lspd"
        )
        
        for (dir in logDirs) {
            if (!fileExists(dir)) continue
            
            val files = listDirectory(dir)
            if (files == null) continue
            
            for (file in files) {
                val filePath = "$dir/$file"
                val content = readFile(filePath)
                if (content != null && content.isNotBlank()) {
                    // 使用相对路径作为 key，避免重复
                    val key = if (file.endsWith(".log")) {
                        file.substringBefore(".log")
                    } else {
                        file
                    }
                    result["$key.log"] = content
                }
            }
        }
        
        return result
    }
}
