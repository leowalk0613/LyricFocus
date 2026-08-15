package com.leowalk.LyricFocus.xposed.hook.systemui

import android.content.Context
import android.service.notification.StatusBarNotification
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.notification.HyperFocusLyricStyle
import com.leowalk.LyricFocus.xposed.ReflectUtil
import io.github.libxposed.api.XposedModule

object FocusIslandSuppressHook {

    /**
     * HyperOS4/Android17: 岛显示判定集中在 DynamicIslandController，
     * hasCustomFocusView(StatusBarNotification) 检查 miui.focus.rv，在 onDynamicPluginCallback 中
     * 被调用决定是否显示岛。hook 其返回 false 抑制 LyricFocus 焦点通知的岛显示。
     */
    fun install(classLoader: ClassLoader, module: XposedModule, tag: String, contextProvider: () -> Context?) {
        try {
            val clazz = ReflectUtil.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandController",
                classLoader
            )
            val method = clazz.getDeclaredMethod(
                "hasCustomFocusView",
                StatusBarNotification::class.java
            )
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val sbn = chain.args.getOrNull(0) as? StatusBarNotification
                    ?: return@intercept chain.proceed()
                if (!shouldSuppressIsland(sbn, contextProvider())) return@intercept chain.proceed()
                false
            }
            module.log(
                android.util.Log.INFO,
                tag,
                "island suppress DynamicIslandController#hasCustomFocusView"
            )
        } catch (e: Throwable) {
            module.log(android.util.Log.INFO, tag, "island suppress hook skipped: ${e.message}")
        }
    }

    private fun shouldSuppressIsland(sbn: StatusBarNotification, context: Context?): Boolean {
        if (sbn.notification?.channelId != HyperFocusLyricStyle.CHANNEL_ID) return false
        context ?: return true
        return !FocusPreferences.readShowOnIsland(context)
    }
}