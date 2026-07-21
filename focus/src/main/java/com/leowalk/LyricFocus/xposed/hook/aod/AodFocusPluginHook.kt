package com.leowalk.LyricFocus.xposed.hook.aod

import android.os.Bundle
import android.view.View
import com.leowalk.LyricFocus.notification.HyperFocusLyricStyle
import com.leowalk.LyricFocus.xposed.hook.BaseHook
import com.leowalk.LyricFocus.xposed.ReflectUtil
import io.github.libxposed.api.XposedModule

class AodFocusPluginHook : BaseHook() {

    override val tag: String = "AodFocusPluginHook"

    override fun install(classLoader: ClassLoader, module: XposedModule) {
        AodFocusBypass.install(classLoader, module, tag)
        AodTransitionSuppress.install(classLoader, module, tag)
        AodTransitionSuppress.installProtect(classLoader, module, tag)
    }
}

object AodFocusBypass {
    fun install(classLoader: ClassLoader, module: XposedModule, tag: String) {
        bypassBoolean(classLoader, module, tag, "miui.systemui.notification.NotificationSettingsManager", "canShowFocus")
        bypassBoolean(classLoader, module, tag, "miui.systemui.notification.NotificationSettingsManager", "canCustomFocus")
    }

    private fun bypassBoolean(
        classLoader: ClassLoader,
        module: XposedModule,
        tag: String,
        className: String,
        methodName: String
    ) {
        try {
            val method = ReflectUtil.findMethod(className, classLoader, methodName)
            module.hook(method).intercept { _ -> java.lang.Boolean.TRUE }
            module.log(android.util.Log.INFO, tag, "bypassed $className.$methodName in AOD")
        } catch (_: Throwable) {
        }
    }
}

/**
 * Hook MiuiFocusNotification2.onRequestFocusView(int, View, Bundle)
 * 对于已在 viewsSet 中的通知（更新操作）跳过 remove+add 循环，
 * 只更新 views 列表中对应条目的 View 引用，不触发 postFocusNotificationValue 动画。
 */
object AodTransitionSuppress {
    fun install(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val method = ReflectUtil.findMethod(
                "com.miui.aod.doze.MiuiFocusNotification2",
                classLoader,
                "onRequestFocusView",
                Int::class.javaPrimitiveType!!,
                View::class.java,
                Bundle::class.java
            )
            module.hook(method).intercept { chain ->
                val bundle = chain.args.getOrNull(2) as? Bundle ?: return@intercept chain.proceed()
                val isAdd = bundle.getBoolean("add", false)
                if (!isAdd) return@intercept chain.proceed()
                val key = bundle.getString("key") ?: return@intercept chain.proceed()

                // 检查是否已存在于 viewsSet 中（更新操作）
                val thisObj = chain.thisObject
                val viewsSet = ReflectUtil.getField(thisObj, "viewsSet") as? java.util.HashSet<*>
                if (viewsSet == null || !viewsSet.contains(key)) {
                    // 首次添加：正常流程
                    return@intercept chain.proceed()
                }

                // 已存在（更新）：替换 View 引用，不触发动画
                val views = ReflectUtil.getField(thisObj, "views") as? java.util.ArrayList<*>
                    ?: return@intercept chain.proceed()
                val view = chain.args.getOrNull(1) as? View ?: return@intercept chain.proceed()
                val packageName = bundle.getString("packageName") ?: ""

                for (i in 0 until views.size) {
                    val data = views[i] ?: continue
                    val dataKey = ReflectUtil.getField(data, "mKey") as? String
                    if (dataKey == key) {
                        try {
                            val viewField = data.javaClass.getDeclaredField("mView")
                            viewField.isAccessible = true
                            viewField.set(data, view)
                        } catch (_: Throwable) {
                        }
                        try {
                            val pkgField = data.javaClass.getDeclaredField("mPackageName")
                            pkgField.isAccessible = true
                            if (packageName.isNotEmpty()) pkgField.set(data, packageName)
                        } catch (_: Throwable) {
                        }
                        return@intercept java.lang.Boolean.TRUE
                    }
                }
                chain.proceed()
            }
            module.log(android.util.Log.INFO, tag, "AOD transition suppress hooked")
        } catch (e: Throwable) {
            module.log(android.util.Log.INFO, tag, "AOD transition suppress skipped: ${e.message}")
        }
    }

    /** AOD 状态过渡时保存并恢复焦点视图，避免锁屏↔AOD 切换掉帧 */
    fun installProtect(classLoader: ClassLoader, module: XposedModule, tag: String) {
        try {
            val clazz = ReflectUtil.findClass("com.miui.aod.doze.MiuiFocusNotification2", classLoader)
            for (method in clazz.methods) {
                if (method.name != "transitionTo" || method.parameterTypes.size != 2) continue
                module.hook(method).intercept { chain ->
                    val state2 = chain.args.getOrNull(1)
                    if (state2?.toString()?.contains("FINISH") != true) return@intercept chain.proceed()
                    val viewsObj = ReflectUtil.getField(chain.thisObject, "views")
                    @Suppress("UNCHECKED_CAST")
                    val saved = (viewsObj as? ArrayList<*>)?.toList() ?: return@intercept chain.proceed()
                    chain.proceed()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val curViews = ReflectUtil.getField(chain.thisObject, "views") as? ArrayList<*>
                            if (curViews != null && curViews.isNotEmpty()) return@postDelayed
                            val m = chain.thisObject.javaClass.getMethod("onRequestFocusView",
                                Int::class.javaPrimitiveType, android.view.View::class.java, android.os.Bundle::class.java)
                            for (data in saved) {
                                try {
                                    val k = ReflectUtil.getField(data, "mKey") as? String ?: continue
                                    val p = ReflectUtil.getField(data, "mPackageName") as? String ?: ""
                                    val v = ReflectUtil.getField(data, "mView") as? android.view.View ?: continue
                                    val b = android.os.Bundle().apply { putBoolean("add", true); putString("key", k); putString("packageName", p); putLong("postTime", System.currentTimeMillis()) }
                                    m.invoke(chain.thisObject, 2048, v, b)
                                } catch (_: Throwable) {}
                            }
                        } catch (_: Throwable) {}
                    }, 150)
                    null
                }
            }
            module.log(android.util.Log.INFO, tag, "AOD transition protect hooked")
        } catch (e: Throwable) {
            module.log(android.util.Log.INFO, tag, "AOD transition protect skipped: ${e.message}")
        }
    }
}
