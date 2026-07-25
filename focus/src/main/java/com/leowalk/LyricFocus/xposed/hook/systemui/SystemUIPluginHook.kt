package com.leowalk.LyricFocus.xposed.hook.systemui

import android.content.ComponentName
import android.content.ContextWrapper
import android.os.Bundle
import com.leowalk.LyricFocus.xposed.hook.BaseHook
import com.leowalk.LyricFocus.xposed.ReflectUtil
import io.github.libxposed.api.XposedModule

class SystemUIPluginHook : BaseHook() {

    override val tag: String = "SystemUIPluginHook"

    override fun install(classLoader: ClassLoader, module: XposedModule) {
        hookPluginFactory(classLoader, module)
    }

    private fun hookPluginFactory(classLoader: ClassLoader, module: XposedModule) {
        try {
            val method = ReflectUtil.findMethod(
                "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory",
                classLoader,
                "createPluginContext"
            )
            module.hook(method).intercept { chain ->
                val proceeded = chain.proceed()
                val result = proceeded as? ContextWrapper ?: return@intercept proceeded
                val factory = chain.thisObject
                val component = ReflectUtil.getField(factory, "mComponentName")
                val className = when (component) {
                    is ComponentName -> component.className
                    else -> component?.toString().orEmpty()
                }
                if (className.contains("FocusNotification")) {
                    val label = className.split(".").lastOrNull() ?: "FocusPlugin"
                    bypassFocusPluginClassLoader(result.classLoader, module, label)
                    FocusIslandSuppressHook.install(result.classLoader, module, tag) { result.applicationContext }
                }
                if (className.contains("DozeServicePluginImpl")) {
                    bypassFocusPluginClassLoader(result.classLoader, module, "DozeServicePluginImpl")
                }
                result
            }
            log("PluginInstance.PluginFactory hooked")
        } catch (e: Throwable) {
            logE("Failed to hook PluginFactory", e)
        }
    }

    private fun bypassFocusPluginClassLoader(pluginLoader: ClassLoader, module: XposedModule, label: String) {
        bypassBoolean(pluginLoader, module, "miui.systemui.notification.NotificationSettingsManager", "canShowFocus", label)
        bypassBoolean(pluginLoader, module, "miui.systemui.notification.NotificationSettingsManager", "canCustomFocus", label)
        tryHookAuthBypass(pluginLoader, module, label)
    }

    private fun bypassBoolean(
        classLoader: ClassLoader,
        module: XposedModule,
        className: String,
        methodName: String,
        label: String
    ) {
        try {
            val method = ReflectUtil.findMethod(className, classLoader, methodName)
            module.hook(method).intercept { _ -> java.lang.Boolean.TRUE }
            log("[$label] bypassed $className.$methodName")
        } catch (e: Throwable) {
            log("[$label] skip $className.$methodName: ${e.message}")
        }
    }

    private fun tryHookAuthBypass(classLoader: ClassLoader, module: XposedModule, label: String) {
        try {
            val authClass = classLoader.loadClass(
                "miui.systemui.notification.auth.AuthManager\$AuthServiceCallback\$onAuthResult\$1"
            )
            val method = authClass.getDeclaredMethod("invokeSuspend", Object::class.java)
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val bundle = ReflectUtil.getField(chain.thisObject, "\$authBundle") as? Bundle
                bundle?.putInt("result_code", 0)
                chain.proceed()
            }
            log("[$label] auth bypass hooked")
        } catch (_: Throwable) {
        }
    }
}
