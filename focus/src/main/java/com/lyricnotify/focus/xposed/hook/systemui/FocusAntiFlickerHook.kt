package com.lyricnotify.focus.xposed.hook.systemui

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 抑制焦点歌词更新时的状态栏 / 岛展开动画（对齐 HyperCeiler FocusNotifLyric）。
 */
object FocusAntiFlickerHook {

    private const val FOCUS_PROMPT_VIEW =
        "com.android.systemui.statusbar.phone.FocusedNotifPromptView"

    fun install(classLoader: ClassLoader, tag: String) {
        hookFocusPromptView(classLoader, tag)
        hookFocusPromptController(classLoader, tag)
    }

    private fun hookFocusPromptView(classLoader: ClassLoader, tag: String) {
        try {
            val clazz = XposedHelpers.findClass(FOCUS_PROMPT_VIEW, classLoader)
            XposedBridge.hookAllMethods(clazz, "setData", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    skipNextAnimation(param.thisObject)
                }
            })
            for (methodName in clazz.declaredMethods.map { it.name }.distinct()) {
                if (!methodName.contains("Anim", ignoreCase = true)) continue
                try {
                    XposedBridge.hookAllMethods(clazz, methodName, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            skipNextAnimation(param.thisObject)
                        }
                    })
                } catch (_: Throwable) {
                }
            }
            XposedBridge.log("$tag: FocusedNotifPromptView anti-flicker hooked")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: FocusedNotifPromptView hook skipped: ${e.message}")
        }
    }

    private fun hookFocusPromptController(classLoader: ClassLoader, tag: String) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.phone.FocusedNotifPromptController",
                classLoader,
                "notifyNotifBeanChanged",
                "com.android.systemui.statusbar.phone.FocusedNotifPromptController\$NotifBean",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = XposedHelpers.getObjectField(param.thisObject, "mView") ?: return
                        skipNextAnimation(view)
                    }
                }
            )
            XposedBridge.log("$tag: FocusedNotifPromptController anti-flicker hooked")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: FocusedNotifPromptController hook skipped: ${e.message}")
        }
    }

    private fun skipNextAnimation(view: Any) {
        val now = System.currentTimeMillis()
        try {
            XposedHelpers.setLongField(view, "mLastAnimationTime", now)
        } catch (_: Throwable) {
        }
        for (fieldName in listOf("mIsAnimating", "mAppearAnimating", "mDisappearAnimating")) {
            try {
                XposedHelpers.setBooleanField(view, fieldName, false)
            } catch (_: Throwable) {
            }
        }
    }
}
