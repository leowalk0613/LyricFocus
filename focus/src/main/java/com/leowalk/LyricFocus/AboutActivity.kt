package com.leowalk.LyricFocus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.leowalk.LyricFocus.util.RootHelper

class AboutActivity : AppCompatActivity() {

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
            showLogDialog()
        }
    }

    private fun showLogDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_log_viewer, null)
        val tabLayout = dialogView.findViewById<TabLayout>(R.id.tab_layout)
        val logContent = dialogView.findViewById<TextView>(R.id.log_content)
        val loadingIndicator = dialogView.findViewById<ProgressBar>(R.id.loading_indicator)
        val emptyState = dialogView.findViewById<TextView>(R.id.empty_state)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("LSPosed 日志")
            .setView(dialogView)
            .setNegativeButton("关闭", null)
            .setCancelable(true)
            .create()

        loadingIndicator.visibility = View.VISIBLE
        logContent.visibility = View.GONE
        emptyState.visibility = View.GONE

        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val future = executor.submit<Map<String, String>> {
            try {
                RootHelper.readLsposedLogs()
            } catch (e: Exception) {
                emptyMap()
            }
        }

        Thread {
            try {
                val logs = future.get(30, java.util.concurrent.TimeUnit.SECONDS)
                val logFiles = logs.keys.toList()

                runOnUiThread {
                    loadingIndicator.visibility = View.GONE

                    if (logs.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        logContent.visibility = View.GONE
                        emptyState.text = "未找到 LSPosed 日志\n\n可能原因：\n• 未安装 LSPosed 或日志文件为空\n• LSPosed 日志路径与预期不同\n• Root 权限未正确授予\n\n可尝试：\n• 使用 adb pull /data/adb/lspd/log/ 手动获取"
                    } else {
                        logContent.visibility = View.VISIBLE

                        for (fileName in logFiles) {
                            val displayName = fileName.substringBefore(".log")
                            tabLayout.addTab(tabLayout.newTab().setText(displayName))
                        }

                        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                                tab?.text?.let { showLog ->
                                    val content = logs["$showLog.log"]
                                    logContent.text = content ?: "日志为空"
                                }
                            }
                            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                        })

                        if (logFiles.isNotEmpty()) {
                            logContent.text = logs[logFiles.first()]
                        }
                    }
                }
            } catch (e: java.util.concurrent.TimeoutException) {
                future.cancel(true)
                runOnUiThread {
                    loadingIndicator.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    logContent.visibility = View.GONE
                    emptyState.text = "读取日志超时\n\n可能原因：\n• Root 权限未及时授予\n• 日志文件过大\n• 系统响应缓慢"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadingIndicator.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    logContent.visibility = View.GONE
                    emptyState.text = "读取日志失败: ${e.message}"
                }
            } finally {
                executor.shutdown()
            }
        }.start()

        dialog.show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }
}