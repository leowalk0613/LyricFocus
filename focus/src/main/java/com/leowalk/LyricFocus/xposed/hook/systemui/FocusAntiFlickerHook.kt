package com.leowalk.LyricFocus.xposed.hook.systemui

import com.leowalk.LyricFocus.xposed.ReflectUtil
import io.github.libxposed.api.XposedModule

/**
 * 压制 SystemUI 焦点通知更新时的动画（对齐 v1.6.2 FocusAntiFlickerHook）。
 * Hook FocusedNotifPromptView / FocusedNotifPromptController，将动画状态置为 false。
 */
object FocusAntiFlickerHook {

    private const val FOCUS_PROMPT_VIEW =
        "com.android.systemui.statusbar.phone.FocusedNotifPromptView"

    fun install(classLoader: ClassLoader, module: XposedModule, tag: String) {
        hookFocusPromptView(classLoader, module, tag)
        hookFocusPromptController(classLoader, module, tag)
    }

    private fun hookFocusPromptView(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val clazz = ReflectUtil.findClass(FOCUS_PROMPT_VIEW, classLoader)
            // Hook setData
            val setDataMethod = ReflectUtil.findMethodsByName(clazz, "setData")
            for (method in setDataMethod) {
                module.hook(method).intercept { chain ->
                    skipNextAnimation(chain.thisObject)
                    chain.proceed()
                }
            }
            // Hook all methods containing "Anim"
            for (method in clazz.declaredMethods) {
                if (!method.name.contains("Anim", ignoreCase = true)) continue
                try {
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        skipNextAnimation(chain.thisObject)
                        chain.proceed()
                    }
                } catch (_: Throwable) {
                }
            }
            module.log(android.util.Log.INFO, tag, "FocusedNotifPromptView anti-flicker hooked")
        } catch (e: Throwable) {
            module.log(android.util.Log.INFO, tag, "FocusedNotifPromptView hook skipped: ${e.message}")
        }
    }

    private fun hookFocusPromptController(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val clazz = ReflectUtil.findClass(
                "com.android.systemui.statusbar.phone.FocusedNotifPromptController",
                classLoader
            )
            val notifyMethods = ReflectUtil.findMethodsByName(clazz, "notifyNotifBeanChanged")
            for (notifyMethod in notifyMethods) {
                module.hook(notifyMethod).intercept { chain ->
                    val view = ReflectUtil.getField(chain.thisObject, "mView") ?: return@intercept chain.proceed()
                    skipNextAnimation(view)
                    chain.proceed()
                }
            }
            if (notifyMethods.isNotEmpty()) {
                module.log(android.util.Log.INFO, tag, "FocusedNotifPromptController anti-flicker hooked")
            }
        } catch (e: Throwable) {
            module.log(android.util.Log.INFO, tag, "FocusedNotifPromptController hook skipped: ${e.message}")
        }
    }

    private fun skipNextAnimation(view: Any) {
        val now = System.currentTimeMillis()
        try {
            val field = view.javaClass.getDeclaredField("mLastAnimationTime")
            field.isAccessible = true
            field.setLong(view, now)
        } catch (_: Throwable) {
        }
        for (fieldName in listOf("mIsAnimating", "mAppearAnimating", "mDisappearAnimating")) {
            try {
                val field = view.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                field.setBoolean(view, false)
            } catch (_: Throwable) {
            }
        }
    }
}
