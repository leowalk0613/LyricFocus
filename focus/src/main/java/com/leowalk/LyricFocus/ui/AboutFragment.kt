package com.leowalk.LyricFocus.ui

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
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

        view.findViewById<MaterialButton>(R.id.btn_hyperfocus_api).setOnClickListener {
            openUrl("https://github.com/ghhccghk/HyperFocusApi")
        }

        view.findViewById<MaterialButton>(R.id.btn_hyperceiler).setOnClickListener {
            openUrl("https://github.com/ReChronoRain/HyperCeiler")
        }

        view.findViewById<MaterialButton>(R.id.btn_lsposed).setOnClickListener {
            openUrl("https://github.com/LSPosed/LSPosed")
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
        view.findViewById<MaterialButton>(R.id.btn_view_logs).setOnClickListener {
            showLogSourceSelection()
        }
    }

    private fun showLogSourceSelection() {
        val options = arrayOf("自动扫描 LSPosed 日志", "手动选择日志文件")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择日志来源")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> scanLspLogs()
                    1 -> openDocumentLauncher.launch(arrayOf("*/*"))
                }
            }
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