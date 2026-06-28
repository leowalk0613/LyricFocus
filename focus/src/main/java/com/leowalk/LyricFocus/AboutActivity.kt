package com.leowalk.LyricFocus

import android.content.ClipboardManager
import android.content.Context
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class AboutActivity : AppCompatActivity() {

    private val TAG = "LyricFocus_About"
    private val LOG_TAGS = listOf(
        "LyricFocus",
        "LyricService",
        "MusicMonitorService",
        "SystemUIHyperFocusHook",
        "HyperFocusLyricStyle",
        "FocusMainHook",
        "LSPosed-Bridge",
        "LSPosed"
    )

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleSelectedLogFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_about)
        setupWindowInsets()
        setupToolbar()
        setupLinks()
        setupLogViewer()
    }

    private fun setupWindowInsets() {
        val appBar = findViewById<android.view.View>(R.id.app_bar_about)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)

        val content = findViewById<android.view.View>(R.id.about_content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_about)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupLinks() {
        findViewById<MaterialButton>(R.id.btn_github_repo).setOnClickListener {
            openUrl("https://github.com/leowalk0613/LyricFocus")
        }

        findViewById<MaterialButton>(R.id.btn_github_issue).setOnClickListener {
            openUrl("https://github.com/leowalk0613/LyricFocus/issues")
        }

        findViewById<MaterialButton>(R.id.btn_coolapk).setOnClickListener {
            openUrl("https://www.coolapk.com/u/551303")
        }

        findViewById<MaterialButton>(R.id.btn_hyperfocus_api).setOnClickListener {
            openUrl("https://github.com/ghhccghk/HyperFocusApi")
        }

        findViewById<MaterialButton>(R.id.btn_hyperceiler).setOnClickListener {
            openUrl("https://github.com/ReChronoRain/HyperCeiler")
        }

        findViewById<MaterialButton>(R.id.btn_lsposed).setOnClickListener {
            openUrl("https://github.com/LSPosed/LSPosed")
        }
    }

    private fun setupLogViewer() {
        findViewById<MaterialButton>(R.id.btn_view_logs).setOnClickListener {
            openDocumentLauncher.launch(arrayOf("*/*"))
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

        val dialog = MaterialAlertDialogBuilder(this)
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
                val mimeType = contentResolver.getType(uri)
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

            runOnUiThread {
                loadingIndicator.visibility = View.GONE

                if (logs.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    logContent.visibility = View.GONE
                    tabLayout.visibility = View.GONE
                    actionBar.visibility = View.GONE
                    emptyState.text = "未找到 LyricFocus 相关日志\n\n已搜索的标签：\n${LOG_TAGS.joinToString(", ")}"
                } else {
                    logContent.visibility = View.VISIBLE
                    actionBar.visibility = View.VISIBLE

                    if (logFiles.size > 1) {
                        tabLayout.visibility = View.VISIBLE
                        for (fileName in logFiles) {
                            val displayName = fileName.substringBefore(".log")
                            tabLayout.addTab(tabLayout.newTab().setText(displayName))
                        }

                        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                            override fun onTabSelected(tab: TabLayout.Tab?) {
                                tab?.text?.let { showLog ->
                                    val content = logs["$showLog.log"]
                                    logContent.text = content ?: "日志为空"
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
        val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
        val clip = android.content.ClipData.newPlainText("LyricFocus 日志", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun readLogsFromZip(uri: Uri, logs: MutableMap<String, String>) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".log", true)) {
                        val fileName = entry.name.substringAfterLast("/")
                        val content = BufferedReader(InputStreamReader(zip)).use { reader ->
                            reader.lineSequence()
                                .filter { line ->
                                    LOG_TAGS.any { tag -> line.contains(tag) }
                                }
                                .toList()
                                .joinToString("\n")
                        }
                        if (content.isNotBlank()) {
                            logs[fileName] = content
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun readLogsFromTextFile(uri: Uri, logs: MutableMap<String, String>) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val filteredLines = reader.lineSequence()
                    .filter { line ->
                        LOG_TAGS.any { tag -> line.contains(tag) }
                    }
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