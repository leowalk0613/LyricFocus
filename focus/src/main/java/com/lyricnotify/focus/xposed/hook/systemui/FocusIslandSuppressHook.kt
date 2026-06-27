package com.lyricnotify.focus.xposed.hook.systemui

import android.content.Context
import android.service.notification.StatusBarNotification
import com.lyricnotify.focus.FocusPreferences
import com.lyricnotify.focus.notification.HyperFocusLyricStyle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * HyperOS 3 在未传 param_island 时仍可能用通知来源 App 图标（SystemUI）生成默认小岛。
 * 在 SystemUI 侧对焦点歌词渠道做兜底拦截。
 */
object FocusIslandSuppressHook {

    private val islandGateMethods = listOf(
        "needShowIsland" to true,
        "shouldShowIsland" to true,
        "canShowIsland" to true,
        "isIslandNotification" to true,
        "shouldShowOnIsland" to true,
        "supportIsland" to true
    )

    private val islandGateClasses = listOf(
        "miui.systemui.statusbar.island.IslandViewController",
        "miui.systemui.statusbar.island.DynamicIslandController",
        "miui.systemui.statusbar.island.IslandWindowController",
        "miui.systemui.notification.island.IslandWindowController",
        "miui.systemui.notification.island.IslandViewController",
        "com.android.systemui.statusbar.island.IslandCoordinator"
    )

    fun install(classLoader: ClassLoader, tag: String, contextProvider: () -> Context?) {
        var hooked = 0
        for (className in islandGateClasses) {
            for ((methodName, returnsBoolean) in islandGateMethods) {
                if (tryHookIslandGate(classLoader, className, methodName, returnsBoolean, contextProvider)) {
                    hooked++
                    XposedBridge.log("$tag: island suppress $className#$methodName")
                }
            }
        }
        if (hooked == 0) {
            XposedBridge.log("$tag: island suppress hooks skipped (no matching methods)")
        }
    }

    private fun tryHookIslandGate(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        returnsBoolean: Boolean,
        contextProvider: () -> Context?
    ): Boolean {
        return try {
            val clazz = XposedHelpers.findClass(className, classLoader)
            val methods = clazz.declaredMethods.filter { it.name == methodName }
            if (methods.isEmpty()) return false
            for (method in methods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val sbn = findStatusBarNotification(param.args) ?: return
                        if (!shouldSuppressIsland(sbn, contextProvider())) return
                        if (returnsBoolean) {
                            param.result = false
                        }
                    }
                })
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun findStatusBarNotification(args: Array<Any?>): StatusBarNotification? {
        for (arg in args) {
            when (arg) {
                is StatusBarNotification -> return arg
                else -> {
                    val nested = runCatching {
                        XposedHelpers.callMethod(arg, "getSbn") as? StatusBarNotification
                    }.getOrNull()
                    if (nested != null) return nested
                }
            }
        }
        return null
    }

    private fun shouldSuppressIsland(sbn: StatusBarNotification, context: Context?): Boolean {
        if (sbn.notification?.channelId != HyperFocusLyricStyle.CHANNEL_ID) return false
        context ?: return true
        return !FocusPreferences.readShowOnIsland(context)
    }
}
