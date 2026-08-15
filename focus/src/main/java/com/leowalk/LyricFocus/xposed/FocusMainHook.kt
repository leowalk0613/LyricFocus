package com.leowalk.LyricFocus.xposed

import android.content.Context
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.xposed.hook.aod.AodFocusPluginHook
import com.leowalk.LyricFocus.xposed.hook.systemui.SystemUIHyperFocusHook
import com.leowalk.LyricFocus.xposed.hook.systemui.SystemUIPluginHook
import com.leowalk.LyricFocus.xposed.hook.xmsf.XmsfAuthHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File

class FocusMainHook : XposedModule() {

    companion object {
        private const val TAG = "LyricFocus_Xposed"
        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val PACKAGE_AOD = "com.miui.aod"
        private const val PACKAGE_XMSF = "com.xiaomi.xmsf"
        private const val MODULE_PACKAGE = "com.leowalk.LyricFocus"
        private const val PREFS_FILE_NAME = "lyric_focus_prefs.xml"
        private const val SETTINGS_URI = "content://com.leowalk.LyricFocus.settings"

        /** 系统属性通道：app 侧通过 RootHelper setprop（persist. 持久化，系统重启后仍可读）。 */
        private const val PROP_AODCHANGE = "persist.lyricfocus.aodchange"
        private const val PROP_FOCUS = "persist.lyricfocus.focus"

        /** onPackageReady 早期 Application 未就绪时，延迟重试的等待时长与次数。 */
        private const val RETRY_DELAY_MS = 3000L
        private const val RETRY_COUNT = 5

        /** 读取系统属性（system_app 进程可读，跨进程最可靠，不依赖文件权限/Provider）。 */
        fun readSystemProperty(name: String): Boolean? {
            return try {
                val clazz = Class.forName("android.os.SystemProperties")
                val get = clazz.getMethod("get", String::class.java)
                val value = get.invoke(null, name) as? String
                when (value) {
                    "1", "true" -> true
                    "0", "false" -> false
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }

        /** 通过本应用 ContentProvider 查询开关状态（跨进程，无文件权限依赖）。 */
        fun queryMode(providerUri: String, method: String): Boolean? {
            return try {
                val ctx = appContext()
                if (ctx == null) {
                    android.util.Log.i(TAG, "queryMode($method): appContext null")
                    return null
                }
                val r = ctx.contentResolver.call(
                    android.net.Uri.parse(providerUri), method, null, null
                )
                val value = if (r != null && r.containsKey("enabled")) r.getBoolean("enabled", false) else null
                android.util.Log.i(TAG, "queryMode($method): provider=$value")
                value
            } catch (e: Throwable) {
                android.util.Log.i(TAG, "queryMode($method): exception ${e.message}")
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
                // HyperOS4/Android17: onPackageReady 早期 Application 可能尚未就绪，
                // ContentProvider 查询失败会误判为关闭。延迟重试，待 app 就绪后重新判定。
                log(android.util.Log.INFO, TAG, "focus lyric disabled, retry after app ready")
                scheduleRetryInstall(param)
                return
            }

            installHooks(param)
        } catch (e: Throwable) {
            log(android.util.Log.ERROR, TAG, "hook failed", e)
        }
    }

    private fun installHooks(param: XposedModuleInterface.PackageReadyParam) {
        val classLoader = param.classLoader
        when (param.packageName) {
            PACKAGE_SYSTEMUI -> {
                SystemUIHyperFocusHook().install(classLoader, this)
                SystemUIPluginHook().install(classLoader, this)
            }
            PACKAGE_AOD -> {
                AodFocusPluginHook().install(classLoader, this)
            }
            PACKAGE_XMSF -> {
                XmsfAuthHook().install(classLoader, this)
            }
        }
    }

    /** 延迟重试：等待 Application 就绪后 ContentProvider 才能可靠返回开关状态。 */
    private fun scheduleRetryInstall(param: XposedModuleInterface.PackageReadyParam) {
        Thread {
            try {
                for (attempt in 1..RETRY_COUNT) {
                    Thread.sleep(RETRY_DELAY_MS)
                    val aodchangeMode = readMode("aodchange_mode")
                    if (aodchangeMode == true) {
                        log(android.util.Log.INFO, TAG, "aodchange external rendering ON, skip all hooks")
                        return@Thread
                    }
                    val focusEnabled = readMode("focus_mode")
                    if (focusEnabled == true) {
                        installHooks(param)
                        return@Thread
                    }
                    log(android.util.Log.INFO, TAG, "focus lyric not enabled on attempt $attempt/$RETRY_COUNT")
                }
                log(android.util.Log.INFO, TAG, "focus lyric disabled after retries, skip all hooks")
            } catch (e: Throwable) {
                log(android.util.Log.ERROR, TAG, "hook failed on retry", e)
            }
        }.start()
    }

    /**
     * 读取模块开关状态：
     * 1. 优先系统属性（system_app 进程可读，跨进程最可靠，不依赖文件权限/Provider）；
     * 2. 再试本应用 ContentProvider（跨进程，无文件权限依赖）；
     * 3. 失败时直接解析 prefs XML 文件（SystemUI/AOD 均为 system 进程，早期可读）；
     * 4. 再失败回退 getRemotePreferences；
     * 5. 全部失败返回 null（由调用方决定行为，避免误判导致冲突）。
     */
    private fun readMode(method: String): Boolean? {
        val propName = if ("aodchange_mode" == method) PROP_AODCHANGE else PROP_FOCUS
        val propValue = readSystemProperty(propName)
        if (propValue != null) {
            log(android.util.Log.INFO, TAG, "readMode($method): prop=$propValue")
            return propValue
        }

        val providerValue = queryMode(SETTINGS_URI, method)
        if (providerValue != null) return providerValue
        log(android.util.Log.INFO, TAG, "readMode($method): provider null, fallback")

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
                if (!file.exists() || !file.canRead()) {
                    log(android.util.Log.INFO, TAG, "readMode($method): prefs unreadable: $path")
                    continue
                }
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
            val remote = getRemotePreferences(FocusPreferences.PREFS_NAME)
            if (remote.contains(prefName)) {
                val value = remote.getBoolean(prefName, false)
                log(android.util.Log.INFO, TAG, "readMode($method): remote=$value")
                value
            } else {
                log(android.util.Log.INFO, TAG, "readMode($method): remote key not found")
                null
            }
        } catch (e: Throwable) {
            log(android.util.Log.INFO, TAG, "readMode($method): remote failed: ${e.message}")
            null
        }
    }
}
