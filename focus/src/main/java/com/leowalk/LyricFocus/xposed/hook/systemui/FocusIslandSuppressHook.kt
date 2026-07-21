package com.leowalk.LyricFocus.xposed.hook.systemui

import android.content.Context
import android.service.notification.StatusBarNotification
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.notification.HyperFocusLyricStyle
import com.leowalk.LyricFocus.xposed.ReflectUtil
import io.github.libxposed.api.XposedModule

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

    fun install(classLoader: ClassLoader, module: XposedModule, tag: String, contextProvider: () -> Context?) {
        var hooked = 0
        for (className in islandGateClasses) {
            for ((methodName, returnsBoolean) in islandGateMethods) {
                if (tryHookIslandGate(classLoader, module, className, methodName, returnsBoolean, contextProvider)) {
                    hooked++
                    module.log(android.util.Log.INFO, tag, "island suppress $className#$methodName")
                }
            }
        }
        if (hooked == 0) {
            module.log(android.util.Log.INFO, tag, "island suppress hooks skipped (no matching methods)")
        }
    }

    private fun tryHookIslandGate(
        classLoader: ClassLoader,
        module: XposedModule,
        className: String,
        methodName: String,
        returnsBoolean: Boolean,
        contextProvider: () -> Context?
    ): Boolean {
        return try {
            val clazz = ReflectUtil.findClass(className, classLoader)
            val methods = ReflectUtil.findMethodsByName(clazz, methodName)
            if (methods.isEmpty()) return false
            for (method in methods) {
                module.hook(method).intercept { chain ->
                    val sbn = findStatusBarNotification(chain.args) ?: return@intercept chain.proceed()
                    if (!shouldSuppressIsland(sbn, contextProvider())) return@intercept chain.proceed()
                    if (returnsBoolean) false else chain.proceed()
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun findStatusBarNotification(args: List<Any?>): StatusBarNotification? {
        for (arg in args) {
            when (arg) {
                is StatusBarNotification -> return arg
                else -> {
                    try {
                        val nested = ReflectUtil.callMethod(arg!!, "getSbn") as? StatusBarNotification
                        if (nested != null) return nested
                    } catch (_: Throwable) {
                    }
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
