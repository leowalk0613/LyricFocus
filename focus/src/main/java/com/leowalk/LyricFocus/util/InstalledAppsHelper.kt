package com.leowalk.LyricFocus.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

object InstalledAppsHelper {

    data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable?
    )

    fun loadLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                AppEntry(
                    packageName = pkg,
                    label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg,
                    icon = runCatching { info.loadIcon(pm) }.getOrNull()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun labelFor(context: Context, packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrElse { packageName }
    }

    fun iconFor(context: Context, packageName: String): Drawable? {
        return runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }
}
