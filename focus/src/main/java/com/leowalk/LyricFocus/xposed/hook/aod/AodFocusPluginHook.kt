package com.leowalk.LyricFocus.xposed.hook.aod

import android.os.Bundle
import android.view.View
import com.leowalk.LyricFocus.notification.HyperFocusLyricStyle
import com.leowalk.LyricFocus.xposed.FocusMainHook
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

/**
 * 运行时模式门卫：AOD 进程内所有 hook 回调都先过这里。
 * 当 aodchange 外部渲染开启或焦点通知歌词关闭时，hook 直接放行原逻辑，
 * 避免本模块与 aodchange 模块在 AOD 进程内叠加冲突。
 * 开关切换后最多 [CACHE_MS] 内生效；彻底卸载需重启 AOD 进程。
 */
private object ModeGuard {
    private const val SETTINGS_URI = "content://com.leowalk.LyricFocus.settings"
    private const val CACHE_MS = 2_000L
    private var lastQuery = 0L
    private var lastDisabled = false

    /** true 表示焦点通知 hook 应让道（不拦截）。查询失败按 false 处理，保证单模块使用时功能不受影响。 */
    fun isDisabled(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastQuery < CACHE_MS) return lastDisabled
        lastQuery = now
        val aodchange = FocusMainHook.queryMode(SETTINGS_URI, "aodchange_mode")
        val focus = FocusMainHook.queryMode(SETTINGS_URI, "focus_mode")
        lastDisabled = aodchange == true || focus == false
        return lastDisabled
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
            module.hook(method).intercept { chain ->
                if (ModeGuard.isDisabled()) chain.proceed() else java.lang.Boolean.TRUE
            }
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
                if (ModeGuard.isDisabled()) return@intercept chain.proceed()
                val bundle = chain.args.getOrNull(2) as? Bundle ?: return@intercept chain.proceed()
                val isAdd = bundle.getBoolean("add", false)
                if (!isAdd) return@intercept chain.proceed()
                val key = bundle.getString("key") ?: return@intercept chain.proceed()
                val isLyric = key.contains(LYRIC_NOTIFY_ID_HASH)

                if (!isLyric) {
                    return@intercept chain.proceed()
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
