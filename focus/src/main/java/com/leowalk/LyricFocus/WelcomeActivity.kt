package com.leowalk.LyricFocus

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leowalk.LyricFocus.util.RootHelper

class WelcomeActivity : AppCompatActivity() {

    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvPostNotificationStatus: TextView
    private lateinit var tvRootStatus: TextView
    private var isCheckingRoot = false

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FocusPreferences.isWelcomeCompleted(this)) {
            openMainAndFinish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_welcome)
        setupWindowInsets()
        tvNotificationStatus = findViewById(R.id.tv_welcome_notification_status)
        tvPostNotificationStatus = findViewById(R.id.tv_welcome_post_notification_status)
        tvRootStatus = findViewById(R.id.tv_welcome_root_status)

        findViewById<MaterialButton>(R.id.btn_welcome_grant_notification).setOnClickListener {
            openNotificationAccessSettings()
        }
        findViewById<MaterialButton>(R.id.btn_welcome_grant_post_notification).setOnClickListener {
            requestPostNotificationPermission()
        }
        findViewById<MaterialButton>(R.id.btn_enter_app).setOnClickListener {
            FocusPreferences.setWelcomeCompleted(this)
            openMainAndFinish()
        }
        updateStatus()
        checkRootAccessAsync()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        checkRootAccessAsync()
    }

    private fun setupWindowInsets() {
        val appBar = findViewById<android.view.View>(R.id.welcome_app_bar)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)
        val content = findViewById<android.view.View>(R.id.welcome_content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
    }

    private fun openMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun updateStatus() {
        if (isNotificationServiceEnabled()) {
            tvNotificationStatus.text = "已授权"
            tvNotificationStatus.setTextColor(getColor(R.color.green))
        } else {
            tvNotificationStatus.text = "未授权"
            tvNotificationStatus.setTextColor(getColor(R.color.red))
        }

        if (!NotificationPermissionHelper.needsPostNotificationsPermission()) {
            tvPostNotificationStatus.text = "无需单独授权"
            tvPostNotificationStatus.setTextColor(getColor(R.color.grey))
        } else if (NotificationPermissionHelper.hasPostNotificationsPermission(this)) {
            tvPostNotificationStatus.text = "已允许"
            tvPostNotificationStatus.setTextColor(getColor(R.color.green))
        } else {
            tvPostNotificationStatus.text = "未允许"
            tvPostNotificationStatus.setTextColor(getColor(R.color.red))
        }
    }

    private fun requestPostNotificationPermission() {
        if (!NotificationPermissionHelper.needsPostNotificationsPermission()) return
        if (NotificationPermissionHelper.hasPostNotificationsPermission(this)) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("允许发送通知")
                .setMessage("拉取歌词与前台服务需要「发送通知」权限。")
                .setPositiveButton("去开启") { _, _ ->
                    NotificationPermissionHelper.openAppNotificationSettings(this)
                }
                .setNegativeButton("稍后", null)
                .show()
        } else {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return try {
            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
            flat.split(":").any { part ->
                ComponentName.unflattenFromString(part)?.packageName == packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun checkRootAccessAsync() {
        if (isCheckingRoot) return
        isCheckingRoot = true
        tvRootStatus.text = "检测中…"
        tvRootStatus.setTextColor(getColor(R.color.grey))
        Thread {
            val granted = RootHelper.checkRootAccess()
            runOnUiThread {
                isCheckingRoot = false
                if (granted) {
                    tvRootStatus.text = "已授权"
                    tvRootStatus.setTextColor(getColor(R.color.green))
                } else {
                    tvRootStatus.text = "未授权"
                    tvRootStatus.setTextColor(getColor(R.color.red))
                }
            }
        }.start()
    }
}
