package com.leowalk.LyricFocus.xposed.hook.aod

import com.leowalk.LyricFocus.xposed.hook.BaseHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AOD 息屏渲染走 [com.miui.aod] 进程，补焦点通知权限 bypass。
 */
class AodFocusPluginHook : BaseHook() {

    override val tag: String = "AodFocusPluginHook"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        AodFocusBypass.install(lpparam.classLoader, tag)
    }
}

/** 与 SystemUI Plugin bypass 相同，供 AOD 进程使用 */
object AodFocusBypass {
    fun install(classLoader: ClassLoader, tag: String) {
        bypassBoolean(classLoader, tag, "miui.systemui.notification.NotificationSettingsManager", "canShowFocus")
        bypassBoolean(classLoader, tag, "miui.systemui.notification.NotificationSettingsManager", "canCustomFocus")
    }

    private fun bypassBoolean(
        classLoader: ClassLoader,
        tag: String,
        className: String,
        methodName: String
    ) {
        try {
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(
                className,
                classLoader,
                methodName,
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
            de.robv.android.xposed.XposedBridge.log("$tag: bypassed $className.$methodName in AOD")
        } catch (_: Throwable) {
        }
    }
}
