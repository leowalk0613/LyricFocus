package com.leowalk.LyricFocus.ui

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.leowalk.LyricFocus.R
import com.leowalk.LyricFocus.util.LyricFocusLogFilter
import com.leowalk.LyricFocus.util.RootHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class AboutFragment : Fragment(R.layout.activity_about) {

    private val TAG = "LyricFocus_About"
    private val contactEmail = "walkalone9990613@gmail.com"

    private val LSP_LOG_PATHS = listOf(
        "/data/adb/lspd/log/",
        "/data/adb/lspd/log.old/"
    )

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleSelectedLogFile(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.app_bar_about).visibility = View.GONE
        setupVersionLabel(view)
        setupLinks(view)
        setupLogViewer(view)
        setupSystemRequirementsButton()
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<ImageButton>(R.id.btn_system_requirements)?.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        activity?.findViewById<ImageButton>(R.id.btn_system_requirements)?.visibility = View.GONE
    }

    private fun setupVersionLabel(view: View) {
        view.findViewById<TextView>(R.id.tv_version).text = runCatching {
            val ctx = requireContext()
            @Suppress("DEPRECATION")
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "v${info.versionName}"
        }.getOrElse { getString(R.string.app_version) }
    }

    private fun setupLinks(view: View) {
        view.findViewById<MaterialButton>(R.id.btn_github_repo).setOnClickListener {
            openUrl("https://github.com/leowalk0613/LyricFocus")
        }

        view.findViewById<MaterialButton>(R.id.btn_github_issue).setOnClickListener {
            openUrl("https://github.com/leowalk0613/LyricFocus/issues")
        }

        view.findViewById<MaterialButton>(R.id.btn_contact_email).setOnClickListener {
            showContactEmailDialog()
        }

        view.findViewById<MaterialButton>(R.id.btn_coolapk).setOnClickListener {
            openUrl("https://www.coolapk.com/u/551303")
        }

        view.findViewById<MaterialButton>(R.id.btn_acknowledgments).setOnClickListener {
            showAcknowledgmentsDialog()
        }

        view.findViewById<MaterialButton>(R.id.btn_mit_license).setOnClickListener {
            showMitLicenseDialog()
        }
    }

    private fun showContactEmailDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("联系邮箱")
            .setMessage(contactEmail)
            .setPositiveButton("发邮件") { _, _ -> sendContactEmail() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAcknowledgmentsDialog() {
        val message = buildString {
            append("感谢下列项目提供框架、依赖与实现参考。\n\n")
            append("▎焦点通知\n")
            append("• HyperCeiler — 焦点歌词、MusicBaseHook / FocusNotifLyric 思路；渠道 ID、插件 ClassLoader bypass、防闪烁等\n")
            append("• FocusNotifLyric — 焦点歌词上游原型\n")
            append("• HyperFocusApi — miui.focus 参数封装\n\n")
            append("▎框架与库\n")
            append("• LSPosed · XposedBridge\n")
            append("• AndroidX · OkHttp · Kotlin\n\n")
            append("▎同生态\n")
            append("• Lyric-Getter / Lyric-Getter-Api\n")
            append("• HookTool · Cemiuiler\n\n")
            append("歌词 Web API 版权归网易云、QQ 音乐各自平台所有。")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("致谢")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showMitLicenseDialog() {
        val message = """MIT License

Copyright (c) 2026 leowalk0613

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("MIT License")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun sendContactEmail() {
        try {
            startActivity(
                Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$contactEmail")
                }
            )
        } catch (_: Exception) {
            val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("email", contactEmail))
            Toast.makeText(requireContext(), "未找到邮件应用，邮箱已复制", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLogViewer(view: View) {
        view.findViewById<MaterialButton>(R.id.btn_scan_logs).setOnClickListener {
            scanLspLogs()
        }
        view.findViewById<MaterialButton>(R.id.btn_pick_log_file).setOnClickListener {
            openDocumentLauncher.launch(arrayOf("*/*"))
        }
        view.findViewById<ImageButton>(R.id.btn_log_help).setOnClickListener {
            showLogHelpDialog()
        }
    }

    private fun setupSystemRequirementsButton() {
        activity?.findViewById<ImageButton>(R.id.btn_system_requirements)?.setOnClickListener {
            showSystemRequirementsDialog()
        }
    }

    private fun showSystemRequirementsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("系统要求")
            .setMessage(getString(R.string.system_requirements))
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showLogHelpDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("日志查看指南")
            .setMessage(
                "LSPosed 日志路径：\n" +
                    "• /data/adb/lspd/log/modules_*.log\n" +
                    "• /data/adb/lspd/log.old/（旧日志）\n" +
                    "• LSPosed 导出的 zip 压缩包\n\n" +
                    "使用方法：\n" +
                    "• 自动扫描（需 Root）：点击按钮 → 选择「自动扫描 LSPosed 日志」\n" +
                    "• 手动选择（无需 Root）：先在 LSPosed 中保存日志并解压，或复制 modules_*.log 文件到手机存储，再点击按钮 → 选择「手动选择日志文件」\n" +
                    "• 应用会自动筛选 LyricFocus 相关日志，支持一键复制"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun scanLspLogs() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_log_viewer, null)
        val tabLayout = dialogView.findViewById<TabLayout>(R.id.tab_layout)
        val logContent = dialogView.findViewById<TextView>(R.id.log_content)
        val loadingIndicator = dialogView.findViewById<ProgressBar>(R.id.loading_indicator)
        val emptyState = dialogView.findViewById<TextView>(R.id.empty_state)
        val actionBar = dialogView.findViewById<LinearLayout>(R.id.action_bar)
        val btnCopyLog = dialogView.findViewById<MaterialButton>(R.id.btn_copy_log)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("LSPosed 日志")
            .setView(dialogView)
            .setNegativeButton("关闭", null)
            .setCancelable(true)
            .create()

        loadingIndicator.visibility = View.VISIBLE
        logContent.visibility = View.GONE
        emptyState.visibility = View.GONE
        tabLayout.visibility = View.GONE
        actionBar.visibility = View.GONE

        Thread {
            val logs = mutableMapOf<String, String>()

            try {
                if (!RootHelper.checkRootAccess()) {
                    requireActivity().runOnUiThread {
                        loadingIndicator!!.visibility = View.GONE
                        emptyState!!.visibility = View.VISIBLE
                        emptyState.text = "未获取 Root 权限\n\n请在 Magisk / KernelSU 中允许本应用\n或使用手动选择文件方式"
                    }
                    return@Thread
                }

                for (logPath in LSP_LOG_PATHS) {
                    val files = RootHelper.listDirectory(logPath)
                    if (files != null) {
                        for (file in files) {
                            if (file.endsWith(".log") && file.startsWith("modules_")) {
                                val filePath = if (logPath.endsWith("/")) "$logPath$file" else "$logPath/$file"
                                val content = RootHelper.readFile(filePath)
                                if (content != null && content.isNotBlank()) {
                                    val filteredContent = filterLogContent(content)
                                    if (filteredContent.isNotBlank()) {
                                        logs[file] = filteredContent
                                    }
                                }
                            } else if (file.endsWith(".zip", true)) {
                                val filePath = if (logPath.endsWith("/")) "$logPath$file" else "$logPath/$file"
                                readLogsFromZipFileViaRoot(filePath, logs)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning LSP logs", e)
            }

            val logFiles = logs.keys.sortedByDescending { it }.toList()

            requireActivity().runOnUiThread {
                loadingIndicator!!.visibility = View.GONE

                if (logs.isEmpty()) {
                    emptyState!!.visibility = View.VISIBLE
                    logContent!!.visibility = View.GONE
                    tabLayout!!.visibility = View.GONE
                    actionBar!!.visibility = View.GONE
                    emptyState!!.text = "未找到 LSPosed 日志\n\n请确保已获取 Root 权限\n或使用手动选择文件方式"
                } else {
                    logContent!!.visibility = View.VISIBLE
                    actionBar!!.visibility = View.VISIBLE

                    if (logFiles.size > 1) {
                        tabLayout!!.visibility = View.VISIBLE
                        for (fileName in logFiles) {
                            val displayName = fileName.substringBefore(".log")
                            tabLayout.addTab(tabLayout.newTab().setText(displayName))
                        }

                        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                            override fun onTabSelected(tab: TabLayout.Tab?) {
                                tab?.text?.let { showLog ->
                                    val content = logs["$showLog.log"]
                                    logContent!!.text = content ?: "日志为空"
                                }
                            }
                            override fun onTabUnselected(tab: TabLayout.Tab?) {}
                            override fun onTabReselected(tab: TabLayout.Tab?) {}
                        })
                    }

                    if (logFiles.isNotEmpty()) {
                        logContent.text = logs[logFiles.first()]
                    }

                    btnCopyLog.setOnClickListener {
                        copyToClipboard(logContent.text.toString())
                    }
                }
            }
        }.start()

        dialog.show()
    }

    private fun filterLogContent(content: String): String = LyricFocusLogFilter.filter(content)

    private fun readLogsFromZipFileViaRoot(zipPath: String, logs: MutableMap<String, String>) {
        try {
            val tempDir = requireContext().cacheDir.absolutePath
            val extractPath = "$tempDir/lsp_log_extract"

            RootHelper.runSuCommand("mkdir -p '$extractPath'")
            RootHelper.runSuCommand("unzip -o '$zipPath' -d '$extractPath'", ignoreExitCode = true)

            scanDirectoryViaRoot(extractPath, logs)

            RootHelper.runSuCommand("rm -rf '$extractPath'", ignoreExitCode = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting zip via root: $zipPath", e)
        }
    }

    private fun scanDirectoryViaRoot(dirPath: String, logs: MutableMap<String, String>) {
        val files = RootHelper.listDirectory(dirPath)
        if (files != null) {
            for (file in files) {
                val filePath = if (dirPath.endsWith("/")) "$dirPath$file" else "$dirPath/$file"
                if (RootHelper.isDirectory(filePath)) {
                    scanDirectoryViaRoot(filePath, logs)
                } else if (file.contains(".log", ignoreCase = true)) {
                    val content = RootHelper.readFile(filePath)
                    if (content != null && content.isNotBlank()) {
                        val filteredContent = filterLogContent(content)
                        if (filteredContent.isNotBlank()) {
                            logs[file] = filteredContent
                        }
                    }
                }
            }
        }
    }

    private fun handleSelectedLogFile(uri: Uri) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_log_viewer, null)
        val tabLayout = dialogView.findViewById<TabLayout>(R.id.tab_layout)
        val logContent = dialogView.findViewById<TextView>(R.id.log_content)
        val loadingIndicator = dialogView.findViewById<ProgressBar>(R.id.loading_indicator)
        val emptyState = dialogView.findViewById<TextView>(R.id.empty_state)
        val actionBar = dialogView.findViewById<LinearLayout>(R.id.action_bar)
        val btnCopyLog = dialogView.findViewById<MaterialButton>(R.id.btn_copy_log)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("日志查看")
            .setView(dialogView)
            .setNegativeButton("关闭", null)
            .setCancelable(true)
            .create()

        loadingIndicator.visibility = View.VISIBLE
        logContent.visibility = View.GONE
        emptyState.visibility = View.GONE
        tabLayout.visibility = View.GONE
        actionBar.visibility = View.GONE

        Thread {
            val logs = mutableMapOf<String, String>()

            try {
                val mimeType = requireContext().contentResolver.getType(uri)
                val isZip = mimeType == "application/zip" ||
                        mimeType == "application/x-zip-compressed" ||
                        uri.lastPathSegment?.endsWith(".zip", true) == true

                if (isZip) {
                    readLogsFromZip(uri, logs)
                } else {
                    readLogsFromTextFile(uri, logs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading log file", e)
            }

            val logFiles = logs.keys.toList()

            requireActivity().runOnUiThread {
                loadingIndicator!!.visibility = View.GONE

                if (logs.isEmpty()) {
                    emptyState!!.visibility = View.VISIBLE
                    logContent!!.visibility = View.GONE
                    tabLayout!!.visibility = View.GONE
                    actionBar!!.visibility = View.GONE
                    emptyState.text = "未找到 LyricFocus 相关日志\n\n已搜索的标签：\n${LyricFocusLogFilter.tagSummary}"
                } else {
                    logContent!!.visibility = View.VISIBLE
                    actionBar!!.visibility = View.VISIBLE

                    if (logFiles.size > 1) {
                        tabLayout!!.visibility = View.VISIBLE
                        for (fileName in logFiles) {
                            val displayName = fileName.substringBefore(".log")
                            tabLayout.addTab(tabLayout.newTab().setText(displayName))
                        }

                        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                            override fun onTabSelected(tab: TabLayout.Tab?) {
                                tab?.text?.let { showLog ->
                                    val content = logs["$showLog.log"]
                                    logContent!!.text = content ?: "日志为空"
                                }
                            }
                            override fun onTabUnselected(tab: TabLayout.Tab?) {}
                            override fun onTabReselected(tab: TabLayout.Tab?) {}
                        })
                    }

                    if (logFiles.isNotEmpty()) {
                        logContent.text = logs[logFiles.first()]
                    }

                    btnCopyLog.setOnClickListener {
                        copyToClipboard(logContent.text.toString())
                    }
                }
            }
        }.start()

        dialog.show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        val clip = android.content.ClipData.newPlainText("LyricFocus 日志", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun readLogsFromZip(uri: Uri, logs: MutableMap<String, String>) {
        requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                var foundEntries = 0
                while (entry != null) {
                    Log.d(TAG, "ZIP entry: ${entry.name}, isDirectory: ${entry.isDirectory}")
                    if (!entry.isDirectory) {
                        if (entry.name.contains(".log", ignoreCase = true)) {
                            foundEntries++
                            val fileName = entry.name.substringAfterLast("/")
                            val content = BufferedReader(InputStreamReader(zip)).use { reader ->
                                LyricFocusLogFilter.filter(reader.readText())
                            }
                            if (content.isNotBlank()) {
                                logs[fileName] = content
                                Log.d(TAG, "Found LyricFocus logs in: ${entry.name}")
                            } else {
                                Log.d(TAG, "No matching logs in: ${entry.name}")
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
                Log.d(TAG, "Total ZIP entries checked: $foundEntries")
            }
        } ?: Log.e(TAG, "Failed to open input stream for URI: $uri")
    }

    private fun readLogsFromTextFile(uri: Uri, logs: MutableMap<String, String>) {
        requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val filteredLines = reader.lineSequence()
                    .filter(LyricFocusLogFilter::matches)
                    .toList()

                if (filteredLines.isNotEmpty()) {
                    val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "log.txt"
                    logs[fileName] = filteredLines.joinToString("\n")
                }
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }
}