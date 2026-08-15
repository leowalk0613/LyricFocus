package com.leowalk.LyricFocus.xposed.hook.systemui

import android.content.Context
import com.leowalk.LyricFocus.xposed.ReflectUtil
import io.github.libxposed.api.XposedModule

/**
 * 压制 SystemUI 焦点通知更新时的动画（对齐 v1.6.2 FocusAntiFlickerHook）。
 * HyperOS4/Android17: FocusedNotifPromptView/FocusedNotifPromptController 已移除，
 * 焦点通知在锁屏由 StatusBarFocusNotifUtils.needAnim 门控动画，hook 其返回 false 禁用动画。
 */
object FocusAntiFlickerHook {

    private const val FOCUS_UTILS =
        "com.android.systemui.statusbar.phone.StatusBarFocusNotifUtils"

    fun install(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val clazz = ReflectUtil.findClass(FOCUS_UTILS, classLoader)
            val method = clazz.getDeclaredMethod("needAnim", Context::class.java)
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                false
            }
            module.log(android.util.Log.INFO, tag, "StatusBarFocusNotifUtils anti-flicker hooked")
        } catch (e: Throwable) {
            module.log(android.util.Log.INFO, tag, "StatusBarFocusNotifUtils hook skipped: ${e.message}")
        }
    }
}