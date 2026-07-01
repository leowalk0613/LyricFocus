package com.leowalk.LyricFocus

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnRestartSystemUi: ImageButton
    private var isCheckingRoot = false

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
        btnRestartSystemUi = findViewById(R.id.btn_restart_systemui)
        setSupportActionBar(toolbar)
        setupWindowInsets()

        btnRestartSystemUi.setOnClickListener { confirmRestartSystemUi() }

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
            val bottomPad = bottomNav.height.takeIf { it > 0 } ?: (56 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomPad + bars.bottom)
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
        btnRestartSystemUi.isEnabled = false
        btnRestartSystemUi.alpha = 0.38f
        Thread {
            val granted = RootHelper.checkRootAccess()
            runOnUiThread {
                isCheckingRoot = false
                btnRestartSystemUi.isEnabled = granted
                btnRestartSystemUi.alpha = if (granted) 1f else 0.38f
            }
        }.start()
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
        btnRestartSystemUi.isEnabled = false
        btnRestartSystemUi.alpha = 0.38f
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
}
