package com.leowalk.LyricFocus.util

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

object AutostartHelper {

    /** MIUI / HyperOS AppOps：应用自启动 */
    private const val OP_AUTO_START = 10008

    /**
     * @return `true`/`false` 可判定；`null` 表示当前系统无法读取该状态
     */
    fun isAutostartEnabled(context: Context): Boolean? {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return null
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val mode = method.invoke(
                appOps,
                OP_AUTO_START,
                Process.myUid(),
                context.packageName
            ) as Int
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            null
        }
    }

    fun openAutostartSettings(context: Context) {
        val pkg = context.packageName
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent("miui.intent.action.OP_AUTO_START").apply {
                setPackage("com.miui.securitycenter")
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", pkg)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
            }
        )
        for (intent in candidates) {
            if (intent.resolveActivity(context.packageManager) == null) continue
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
