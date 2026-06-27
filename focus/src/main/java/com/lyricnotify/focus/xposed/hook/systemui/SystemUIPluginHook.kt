package com.lyricnotify.focus.xposed.hook.systemui

import android.content.ComponentName
import android.content.ContextWrapper
import android.os.Bundle
import com.lyricnotify.focus.xposed.hook.BaseHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * SystemUI 插件 ClassLoader 内补焦点通知权限（对齐 HyperCeiler FocusNotifLyric.initLoader）。
 * 焦点/AOD 渲染走 FocusNotificationPluginImpl，仅 hook 主进程不够。
 */
class SystemUIPluginHook : BaseHook() {

    override val tag: String = "SystemUIPluginHook"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookPluginFactory(lpparam.classLoader)
    }

    private fun hookPluginFactory(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory",
                classLoader,
                "createPluginContext",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as? ContextWrapper ?: return
                        val factory = param.thisObject
                        val component = XposedHelpers.getObjectField(factory, "mComponentName")
                        val className = when (component) {
                            is ComponentName -> component.className
                            else -> component?.toString().orEmpty()
                        }
                        if (className.contains("FocusNotificationPluginImpl")) {
                            bypassFocusPluginClassLoader(result.classLoader, "FocusNotificationPluginImpl")
                            FocusAntiFlickerHook.install(result.classLoader, tag)
                            FocusIslandSuppressHook.install(result.classLoader, tag) {
                                result.applicationContext
                            }
                        }
                        if (className.contains("DozeServicePluginImpl")) {
                            bypassFocusPluginClassLoader(result.classLoader, "DozeServicePluginImpl")
                            FocusAntiFlickerHook.install(result.classLoader, tag)
                        }
                    }
                }
            )
            log("PluginInstance.PluginFactory hooked")
        } catch (e: Throwable) {
            logE("Failed to hook PluginFactory", e)
        }
    }

    private fun bypassFocusPluginClassLoader(pluginLoader: ClassLoader, label: String) {
        bypassBooleanMethod(
            pluginLoader,
            "miui.systemui.notification.NotificationSettingsManager",
            "canShowFocus",
            label
        )
        bypassBooleanMethod(
            pluginLoader,
            "miui.systemui.notification.NotificationSettingsManager",
            "canCustomFocus",
            label
        )
        tryHookAuthBypass(pluginLoader, label)
    }

    private fun bypassBooleanMethod(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        label: String
    ) {
        try {
            XposedHelpers.findAndHookMethod(
                className,
                classLoader,
                methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
            log("[$label] bypassed $className.$methodName")
        } catch (e: Throwable) {
            log("[$label] skip $className.$methodName: ${e.message}")
        }
    }

    private fun tryHookAuthBypass(classLoader: ClassLoader, label: String) {
        try {
            val authClass = classLoader.loadClass(
                "miui.systemui.notification.auth.AuthManager\$AuthServiceCallback\$onAuthResult\$1"
            )
            XposedHelpers.findAndHookMethod(
                authClass,
                "invokeSuspend",
                Object::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val bundle = XposedHelpers.getObjectField(
                            param.thisObject,
                            "\$authBundle"
                        ) as? Bundle
                        bundle?.putInt("result_code", 0)
                    }
                }
            )
            log("[$label] auth bypass hooked")
        } catch (_: Throwable) {
        }
    }

    override fun log(msg: String) {
        XposedBridge.log("$tag: $msg")
    }

    override fun logE(msg: String, throwable: Throwable?) {
        XposedBridge.log("$tag: $msg")
        throwable?.let { XposedBridge.log(it) }
    }
}
