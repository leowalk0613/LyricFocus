package com.leowalk.LyricFocus

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leowalk.LyricFocus.util.AutostartHelper
import com.leowalk.LyricFocus.util.InstalledAppsHelper
import com.leowalk.LyricFocus.util.RootHelper

class WelcomeActivity : AppCompatActivity() {

    private var currentPage = 0
    private lateinit var pages: List<View>
    private lateinit var dots: List<View>
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnNext: MaterialButton
    private var isCheckingRoot = false
    private var hasRoot = false

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updatePermissionStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FocusPreferences.isWelcomeCompleted(this)) {
            openMainAndFinish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_welcome)
        setupWindowInsets()

        pages = listOf(
            findViewById(R.id.page_intro),
            findViewById(R.id.page_permissions),
            findViewById(R.id.page_optional)
        )
        dots = listOf(
            findViewById(R.id.dot_0),
            findViewById(R.id.dot_1),
            findViewById(R.id.dot_2)
        )
        btnPrev = findViewById(R.id.btn_welcome_prev)
        btnNext = findViewById(R.id.btn_welcome_next)

        btnPrev.setOnClickListener { showPage(currentPage - 1) }
        btnNext.setOnClickListener {
            if (currentPage == 2) {
                FocusPreferences.setWelcomeCompleted(this)
                openMainAndFinish()
            } else {
                showPage(currentPage + 1)
            }
        }

        // Page 1: app intro, no permission bindings needed
        // Page 2: required permissions
        findViewById<MaterialButton>(R.id.btn_step_notification).setOnClickListener { openNotificationAccessSettings() }
        findViewById<MaterialButton>(R.id.btn_step_post).setOnClickListener { requestPostNotificationPermission() }

        // Page 3: optional permissions + root
        findViewById<MaterialButton>(R.id.btn_step_applist).setOnClickListener { InstalledAppsHelper.openAppListPermissionSettings(this) }
        findViewById<MaterialButton>(R.id.btn_step_autostart).setOnClickListener { AutostartHelper.openAutostartSettings(this) }
        findViewById<MaterialButton>(R.id.btn_step_battery).setOnClickListener { openBatteryOptimizationSettings() }
        findViewById<MaterialButton>(R.id.btn_step_root).setOnClickListener { if (hasRoot) confirmRestartSystemUi() }

        showPage(0)
        updatePermissionStatus()
        checkRootAccessAsync()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        checkRootAccessAsync()
    }

    private fun showPage(index: Int) {
        currentPage = index.coerceIn(0, pages.size - 1)
        pages.forEachIndexed { i, page -> page.visibility = if (i == currentPage) View.VISIBLE else View.GONE }
        dots.forEachIndexed { i, dot ->
            dot.setBackgroundResource(if (i == currentPage) R.drawable.welcome_dot_active else R.drawable.welcome_dot_inactive)
        }
        btnPrev.visibility = if (currentPage == 0) View.INVISIBLE else View.VISIBLE
        btnNext.text = if (currentPage == 2) "进入软件" else "下一步"
    }

    private fun setupWindowInsets() {
        val appBar = findViewById<View>(R.id.welcome_app_bar)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)
        val content = findViewById<View>(R.id.welcome_content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
            insets
        }
    }

    private fun openMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun updatePermissionStatus() {
        val notifGranted = isNotificationServiceEnabled()
        setStatus(findViewById(R.id.tv_step_notification_status), notifGranted, "已授权", "未授权")

        val postNeeded = NotificationPermissionHelper.needsPostNotificationsPermission()
        val postGranted = if (!postNeeded) true else NotificationPermissionHelper.hasPostNotificationsPermission(this)
        if (!postNeeded) setStatus(findViewById(R.id.tv_step_post_status), true, "无需单独授权", null)
        else setStatus(findViewById(R.id.tv_step_post_status), postGranted, "已允许", "未允许")

        val appListGranted = !InstalledAppsHelper.hasLimitedPackageVisibility(this)
        setStatus(findViewById(R.id.tv_step_applist_status), appListGranted, "已允许", "未允许")

        val autostartGranted = AutostartHelper.isAutostartEnabled(this) == true
        setStatus(findViewById(R.id.tv_step_autostart_status), autostartGranted, "已开启", "未开启")

        val batteryGranted = isIgnoringBatteryOptimizations()
        setStatus(findViewById(R.id.tv_step_battery_status), batteryGranted, "已忽略", "未忽略")
    }

    private fun setStatus(view: TextView, granted: Boolean, ok: String, fail: String?) {
        if (granted) {
            view.text = "\u2713 $ok"
            view.setTextColor(getColor(R.color.green))
        } else {
            view.text = if (fail != null) "\u2717 $fail" else "\u2713 $ok"
            view.setTextColor(if (fail != null) getColor(R.color.red) else getColor(R.color.grey))
        }
    }

    private fun requestPostNotificationPermission() {
        if (!NotificationPermissionHelper.needsPostNotificationsPermission()) return
        if (NotificationPermissionHelper.hasPostNotificationsPermission(this)) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("允许发送通知")
                .setMessage("拉取歌词与前台服务需要「发送通知」权限。")
                .setPositiveButton("去开启") { _, _ -> NotificationPermissionHelper.openAppNotificationSettings(this) }
                .setNegativeButton("稍后", null).show()
        } else {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return try {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                ?.split(":")?.any { ComponentName.unflattenFromString(it)?.packageName == packageName } == true
        } catch (_: Exception) { false }
    }

    private fun openNotificationAccessSettings() {
        try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return try { (getSystemService(PowerManager::class.java) as? PowerManager)?.isIgnoringBatteryOptimizations(packageName) == true }
        catch (_: Exception) { false }
    }

    private fun openBatteryOptimizationSettings() {
        for (intent in listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = android.net.Uri.parse("package:$packageName") },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.fromParts("package", packageName, null) }
        )) {
            try { if (intent.resolveActivity(packageManager) != null) { startActivity(intent); return } }
            catch (_: Exception) {}
        }
    }

    private fun checkRootAccessAsync() {
        if (isCheckingRoot) return
        isCheckingRoot = true
        val tv = findViewById<TextView>(R.id.tv_step_root_status)
        val btn = findViewById<MaterialButton>(R.id.btn_step_root)
        tv.text = "检测中…"
        tv.setTextColor(getColor(R.color.grey))
        Thread {
            val granted = RootHelper.checkRootAccess()
            runOnUiThread {
                isCheckingRoot = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                hasRoot = granted
                btn.isEnabled = granted
                setStatus(tv, granted, "可使用", "未获取（可选）")
                if (!granted) tv.setTextColor(android.graphics.Color.parseColor("#FF9800"))
            }
        }.start()
    }

    private fun confirmRestartSystemUi() {
        MaterialAlertDialogBuilder(this)
            .setTitle("重启系统界面")
            .setMessage("将结束 SystemUI 进程并自动恢复，屏幕可能短暂黑屏或闪烁。")
            .setPositiveButton("重启") { _, _ -> RootHelper.restartSystemUiAsync { _, _ -> runOnUiThread { checkRootAccessAsync() } } }
            .setNegativeButton("取消", null).show()
    }
}
