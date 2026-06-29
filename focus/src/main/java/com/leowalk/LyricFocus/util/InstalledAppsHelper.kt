package com.leowalk.LyricFocus.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings

object InstalledAppsHelper {

    data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable?
    )

    private val packageNamePattern =
        Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$""")

    fun loadInstalledApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val self = context.packageName
        val appInfos = queryInstalledApplications(pm)

        return appInfos.asSequence()
            .filter { it.packageName != self }
            .mapNotNull { appInfo -> toAppEntry(pm, appInfo) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
            .toList()
    }

    @Deprecated("Use loadInstalledApps", ReplaceWith("loadInstalledApps(context)"))
    fun loadLaunchableApps(context: Context): List<AppEntry> = loadInstalledApps(context)

    fun hasLimitedPackageVisibility(context: Context): Boolean {
        return queryInstalledApplications(context.packageManager).size < 12
    }

    fun isValidPackageName(packageName: String): Boolean {
        return packageName.isNotBlank() && packageNamePattern.matches(packageName)
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun labelFor(context: Context, packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
            ).toString()
        }.getOrElse { packageName }
    }

    fun iconFor(context: Context, packageName: String): Drawable? {
        return runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }

    fun openAppListPermissionSettings(context: Context) {
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )
        for (intent in candidates) {
            if (intent.resolveActivity(context.packageManager) == null) continue
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    private fun queryInstalledApplications(pm: PackageManager): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }
    }

    private fun toAppEntry(pm: PackageManager, appInfo: ApplicationInfo): AppEntry? {
        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val hasLauncher = pm.getLaunchIntentForPackage(appInfo.packageName) != null
        if (isSystem && !isUpdatedSystem && !hasLauncher) {
            return null
        }

        return AppEntry(
            packageName = appInfo.packageName,
            label = label,
            icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
        )
    }
}
