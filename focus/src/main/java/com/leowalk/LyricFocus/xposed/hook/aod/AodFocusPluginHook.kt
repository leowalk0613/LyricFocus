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
                val isLyric = key.contains(LYRIC_NOTIFY_ID_HASH)

                // 万象息屏时：拒绝所有非歌词通知
                if (!isLyric) {
                    return@intercept java.lang.Boolean.FALSE
                }

                // 歌词通知已存在：原地更新 View，不触发动画
                val thisObj = chain.thisObject
                val viewsSet = ReflectUtil.getField(thisObj, "viewsSet") as? java.util.HashSet<*>
                if (viewsSet == null || !viewsSet.contains(key)) return@intercept chain.proceed()
                val views = ReflectUtil.getField(thisObj, "views") as? java.util.ArrayList<*>
                    ?: return@intercept chain.proceed()
                val view = chain.args.getOrNull(1) as? View ?: return@intercept chain.proceed()
                val packageName = bundle.getString("packageName") ?: ""
                for (i in 0 until views.size) {
                    val data = views[i] ?: continue
                    val dataKey = ReflectUtil.getField(data, "mKey") as? String
                    if (dataKey == key) {
                        try { val vf = data.javaClass.getDeclaredField("mView"); vf.isAccessible = true; vf.set(data, view) } catch (_: Throwable) {}
                        try { val pf = data.javaClass.getDeclaredField("mPackageName"); pf.isAccessible = true; if (packageName.isNotEmpty()) pf.set(data, packageName) } catch (_: Throwable) {}
                        return@intercept java.lang.Boolean.TRUE
                    }
                }
                chain.proceed()
            }
            module.log(android.util.Log.INFO, tag, "AOD suppress + animate hooked (marker: $LYRIC_NOTIFY_ID_HASH)")
        } catch (e: Throwable) { module.log(android.util.Log.INFO, tag, "AOD suppress skipped: ${e.message}") }
    }

    private val LYRIC_NOTIFY_ID_HASH = HyperFocusLyricStyle.CHANNEL_ID.hashCode().toString()
}
