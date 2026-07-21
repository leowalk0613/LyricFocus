package com.leowalk.LyricFocus.xposed.hook

import io.github.libxposed.api.XposedModule

abstract class BaseHook {

    abstract val tag: String

    abstract fun install(classLoader: ClassLoader, module: XposedModule)

    protected fun log(msg: String) {
        android.util.Log.d(tag, msg)
    }

    protected fun logE(msg: String, throwable: Throwable? = null) {
        android.util.Log.e(tag, msg, throwable)
    }
}
