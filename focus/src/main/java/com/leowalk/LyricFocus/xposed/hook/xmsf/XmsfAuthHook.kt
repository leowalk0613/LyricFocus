package com.leowalk.LyricFocus.xposed.hook.xmsf

import com.leowalk.LyricFocus.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule

/**
 * 移除焦点通知的签名认证限制（XMSF 进程）。
 *
 * 作用域：com.xiaomi.xmsf（小米服务框架）
 *
 * SystemUI 焦点通知插件渲染焦点通知前，会通过 binder 调用 XMSF 的认证服务
 * 校验应用是否有焦点通知权限。认证失败时插件回调 onAuthFailed 并移除焦点通知。
 *
 * Hook AuthSession.b(error)：当 error 不为 null（认证失败）时，将 errorCode
 * 字段 a 强制置 0 并调用成功回调 h()，跳过原方法，使认证始终成功。
 */
class XmsfAuthHook : BaseHook() {

    override val tag = "LyricFocus[XmsfAuthHook]"

    override fun install(classLoader: ClassLoader, module: XposedModule) {
        try {
            val authSessionClass = classLoader.loadClass("com.xiaomi.xms.auth.AuthSession")
            val targetMethod = authSessionClass.declaredMethods
                .filter { it.name == "b" && it.parameterCount == 1 }
                .firstOrNull()
            if (targetMethod == null) {
                logE("method 'b(error)' not found in AuthSession")
                return
            }
            module.hook(targetMethod).intercept { chain ->
                val error = chain.args[0]
                if (error == null) return@intercept chain.proceed()
                try {
                    val originalCode = getIntField(error, "a")
                    log("auth error intercepted, original errorCode=$originalCode, forcing to 0")
                    setField(error, "a", 0)
                    val successResult = callMethod(chain.thisObject!!, "h")
                    log("auth bypassed successfully")
                    successResult
                } catch (e: Throwable) {
                    logE("bypass failed - ${e.message}")
                    chain.proceed()
                }
            }
            log("hooked AuthSession.b(error)")
        } catch (e: Throwable) {
            logE("failed to hook AuthSession - ${e.message}")
        }
    }

    private fun getIntField(instance: Any, fieldName: String): Int {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                return (f.get(instance) as? Int) ?: 0
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return 0
    }

    private fun setField(instance: Any, fieldName: String, value: Any?) {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                f.set(instance, value)
                return
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
    }

    private fun callMethod(instance: Any, methodName: String): Any? {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val m = c.getDeclaredMethod(methodName)
                m.isAccessible = true
                return m.invoke(instance)
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        return null
    }
}
