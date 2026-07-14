package com.leowalk.LyricFocus

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuItemCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leowalk.LyricFocus.ui.AboutFragment
import com.leowalk.LyricFocus.ui.HomeFragment
import com.leowalk.LyricFocus.ui.StyleSettingsFragment
import com.leowalk.LyricFocus.util.RootHelper
import com.leowalk.LyricFocus.util.UpdateChecker

class MainActivity : AppCompatActivity(), HomeFragment.UpdateCallback {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private var isCheckingRoot = false
    private var hasRootAccess = false
    private var pendingUpdateInfo: UpdateChecker.UpdateInfo? = null
    private var updateDialog: AlertDialog? = null
    private var updateMenuItem: MenuItem? = null
    private var restartMenuItem: MenuItem? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!FocusPreferences.isWelcomeCompleted(this)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        bottomNav = findViewById(R.id.bottom_navigation)
        setSupportActionBar(toolbar)
        setupWindowInsets()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showFragment(HomeFragment(), getString(R.string.app_name))
                R.id.nav_style -> showFragment(StyleSettingsFragment(), "样式设置")
                R.id.nav_about -> showFragment(AboutFragment(), "关于")
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        restartMenuItem = menu.findItem(R.id.action_restart_systemui)
        updateMenuItem = menu.findItem(R.id.action_update)
        applyRestartMenuState()
        applyUpdateMenuState()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        applyRestartMenuState()
        applyUpdateMenuState()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_restart_systemui -> {
                window.decorView.post {
                    if (!isFinishing && !isDestroyed) confirmRestartSystemUi()
                }
                true
            }
            R.id.action_update -> {
                val info = pendingUpdateInfo
                if (info == null) {
                    Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show()
                } else {
                    // 与「重启 SystemUI」相同：下一帧再弹，避开菜单点击抬起
                    window.decorView.post {
                        if (!isFinishing && !isDestroyed) showUpdateDialog(info)
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        checkRootAccessAsync()
    }

    private fun showFragment(fragment: Fragment, title: String): Boolean {
        toolbar.title = title
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.nav_host_fragment, fragment)
        }
        return true
    }

    private fun setupWindowInsets() {
        val appBar = findViewById<android.view.View>(R.id.app_bar)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)

        val navHost = findViewById<android.view.View>(R.id.nav_host_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(navHost) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomPad = bottomNav.height.takeIf { it > 0 }
                ?: (56 * resources.displayMetrics.density).toInt()
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                bottomPad + bars.bottom
            )
            insets
        }
        bottomNav.post { ViewCompat.requestApplyInsets(navHost) }

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
    }

    private fun checkRootAccessAsync() {
        if (isCheckingRoot) return
        isCheckingRoot = true
        applyRestartMenuState(enabled = false)
        Thread {
            val granted = RootHelper.checkRootAccess()
            runOnUiThread {
                isCheckingRoot = false
                hasRootAccess = granted
                applyRestartMenuState()
            }
        }.start()
    }

    private fun applyRestartMenuState(enabled: Boolean = hasRootAccess && !isCheckingRoot) {
        restartMenuItem?.isEnabled = enabled
        restartMenuItem?.icon?.mutate()?.alpha = if (enabled) 255 else 97
    }

    private fun resolveColorOnSurface(): Int {
        val typed = theme.obtainStyledAttributes(
            intArrayOf(com.google.android.material.R.attr.colorOnSurface)
        )
        return try {
            typed.getColor(0, Color.GRAY)
        } finally {
            typed.recycle()
        }
    }

    private fun applyUpdateMenuState() {
        val item = updateMenuItem ?: return
        val hasUpdate = pendingUpdateInfo?.hasUpdate == true
        val tint = if (hasUpdate) getColor(R.color.red) else resolveColorOnSurface()
        MenuItemCompat.setIconTintList(item, ColorStateList.valueOf(tint))
    }

    private fun confirmRestartSystemUi() {
        MaterialAlertDialogBuilder(this)
            .setTitle("重启系统界面")
            .setMessage("将结束 SystemUI 进程并自动恢复，屏幕可能短暂黑屏或闪烁。需要 Root 权限。")
            .setPositiveButton("重启") { _, _ -> restartSystemUi() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun restartSystemUi() {
        applyRestartMenuState(enabled = false)
        RootHelper.restartSystemUiAsync { success, message ->
            runOnUiThread {
                checkRootAccessAsync()
                if (success) {
                    Toast.makeText(this, "已发送重启指令，系统界面即将恢复", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, message ?: "重启失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onUpdateChecked(info: UpdateChecker.UpdateInfo) {
        pendingUpdateInfo = info
        applyUpdateMenuState()
        invalidateOptionsMenu()
    }

    /**
     * 与其它可用弹窗一致：只用 setTitle / setMessage / 按钮。
     * 菜单抬起容易误触默认按钮，弹出后短暂禁用按钮。
     * 更新日志在点开后再异步加载。
     */
    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        if (isFinishing || isDestroyed) return
        if (updateDialog?.isShowing == true) return

        val checker = UpdateChecker(this)
        val currentVersion = checker.getCurrentVersion(this)
        val hasUpdate = info.hasUpdate
        val versionBlock = if (hasUpdate) {
            "当前版本：$currentVersion\n最新版本：${info.latestVersion}"
        } else {
            "当前版本：$currentVersion"
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (hasUpdate) "发现新版本" else "当前版本")
            .setMessage("$versionBlock\n\n正在加载更新日志…")
            .setNeutralButton("下载渠道") { _, _ ->
                mainHandler.post { showDownloadChannels(info) }
            }
            .setNegativeButton("关闭", null)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            if (updateDialog === dialog) updateDialog = null
        }
        updateDialog = dialog
        dialog.setOnShowListener {
            // 菜单点击的抬起事件落在默认按钮上会导致「一点开就关」
            guardDialogButtons(dialog)
        }
        dialog.show()

        Thread {
            val notes = try {
                checker.fetchReleaseNotes(info)
            } catch (_: Exception) {
                "加载更新日志失败"
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (updateDialog !== dialog || !dialog.isShowing) return@runOnUiThread
                dialog.findViewById<TextView>(android.R.id.message)?.text =
                    "$versionBlock\n\n$notes"
            }
        }.start()
    }

    private fun guardDialogButtons(dialog: AlertDialog) {
        val buttons = listOf(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        )
        buttons.forEach { it?.isClickable = false }
        mainHandler.postDelayed({
            if (dialog.isShowing) {
                buttons.forEach { it?.isClickable = true }
            }
        }, 450L)
    }

    private fun showDownloadChannels(info: UpdateChecker.UpdateInfo) {
        if (isFinishing || isDestroyed) return
        val labels = arrayOf("GitHub", "Gitee", "123网盘")
        MaterialAlertDialogBuilder(this)
            .setTitle("下载渠道")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> openUrl(
                        info.githubUrl
                            ?: "https://github.com/leowalk0613/LyricFocus/releases"
                    )
                    1 -> openUrl(
                        info.giteeUrl
                            ?: "https://gitee.com/leowalk0613/LyricFocus/releases"
                    )
                    2 -> openUrl("https://1825191091.share.123pan.cn/123pan/jNBsjv-vZrV?pwd=Ifn3")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        updateDialog?.setOnDismissListener(null)
        updateDialog?.dismiss()
        updateDialog = null
        super.onDestroy()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: Exception) {
        }
    }
}
