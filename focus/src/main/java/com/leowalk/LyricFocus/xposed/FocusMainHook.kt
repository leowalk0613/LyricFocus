package com.leowalk.LyricFocus.xposed

import android.content.Context
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.xposed.hook.aod.AodFocusPluginHook
import com.leowalk.LyricFocus.xposed.hook.systemui.SystemUIHyperFocusHook
import com.leowalk.LyricFocus.xposed.hook.systemui.SystemUIPluginHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File

class FocusMainHook : XposedModule() {

    companion object {
        private const val TAG = "LyricFocus_Xposed"
        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val PACKAGE_AOD = "com.miui.aod"
        private const val MODULE_PACKAGE = "com.leowalk.LyricFocus"
        private const val PREFS_FILE_NAME = "lyric_focus_prefs.xml"
        private const val SETTINGS_URI = "content://com.leowalk.LyricFocus.settings"

        /** 通过本应用 ContentProvider 查询开关状态（跨进程，无文件权限依赖）。 */
        fun queryMode(providerUri: String, method: String): Boolean? {
            return try {
                val ctx = appContext()
                if (ctx == null) return null
                val r = ctx.contentResolver.call(
                    android.net.Uri.parse(providerUri), method, null, null
                )
                if (r != null && r.containsKey("enabled")) r.getBoolean("enabled", false) else null
            } catch (e: Throwable) {
                null
            }
        }

        /** 反射获取当前进程的 Application Context（onPackageReady 阶段可能尚未就绪）。 */
        fun appContext(): Context? {
            return try {
                val at = Class.forName("android.app.ActivityThread")
                val app = at.getMethod("currentApplication").invoke(null)
                val ctx = app?.let { at.getMethod("getApplicationContext").invoke(it) }
                ctx as? Context
            } catch (_: Throwable) {
                null
            }
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            // aodchange 外部渲染开启：本模块仅负责获取并推送歌词数据，
            // 不注入任何 hook，避免与其他模块在 SystemUI/AOD 中叠加负载。
            // 开关读取优先走本应用 ContentProvider（HyperOS 3.0 上 systemui/aod
            // 进程无法读取本应用私有 prefs，文件/remote prefs 均会失败）。
            val aodchangeMode = readMode("aodchange_mode")
            if (aodchangeMode == true) {
                log(android.util.Log.INFO, TAG, "aodchange external rendering ON, skip all hooks")
                return
            }

            // 焦点通知歌词开关：关闭时同样不注入任何 hook。
            val focusEnabled = readMode("focus_mode")
            if (focusEnabled == false) {
                log(android.util.Log.INFO, TAG, "focus lyric disabled, skip all hooks")
                return
            }

            val classLoader = param.classLoader
            when (param.packageName) {
                PACKAGE_SYSTEMUI -> {
                    SystemUIHyperFocusHook().install(classLoader, this)
                    SystemUIPluginHook().install(classLoader, this)
                }
                PACKAGE_AOD -> {
                    AodFocusPluginHook().install(classLoader, this)
                }
            }
        } catch (e: Throwable) {
            log(android.util.Log.ERROR, TAG, "hook failed", e)
        }
    }

    /**
     * 读取模块开关状态：
     * 1. 优先本应用 ContentProvider（跨进程可靠，无需文件权限）；
     * 2. 失败时直接解析 prefs XML 文件（SystemUI/AOD 均为 system 进程，早期可读）；
     * 3. 再失败回退 getRemotePreferences；
     * 4. 全部失败返回 null（由调用方决定行为，避免误判导致冲突）。
     */
    private fun readMode(method: String): Boolean? {
        val providerValue = queryMode(SETTINGS_URI, method)
        if (providerValue != null) return providerValue

        val prefName = if ("aodchange_mode" == method) {
            FocusPreferences.PREF_AODCHANGE_ENABLED
        } else {
            FocusPreferences.PREF_FOCUS_ENABLED
        }
        val candidates = listOf(
            "/data/user/0/$MODULE_PACKAGE/shared_prefs/$PREFS_FILE_NAME",
            "/data/data/$MODULE_PACKAGE/shared_prefs/$PREFS_FILE_NAME"
        )
        for (path in candidates) {
            try {
                val file = File(path)
                if (!file.exists() || !file.canRead()) continue
                val text = file.readText()
                val m = Regex("""name="$prefName"\s+value="(true|false)"""").find(text)
                if (m != null) {
                    return m.groupValues[1] == "true"
                }
            } catch (e: Throwable) {
                // continue to next candidate
            }
        }
        return try {
            getRemotePreferences(FocusPreferences.PREFS_NAME)
                .getBoolean(prefName, false)
        } catch (e: Throwable) {
            null
        }
    }
}
