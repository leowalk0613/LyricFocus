package com.leowalk.LyricFocus.xposed.hook

import de.robv.android.xposed.callbacks.XC_LoadPackage

abstract class BaseHook {

    abstract val tag: String

    abstract fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam)

    protected open fun log(msg: String) {
        android.util.Log.d(tag, msg)
    }

    protected open fun logE(msg: String, throwable: Throwable? = null) {
        android.util.Log.e(tag, msg, throwable)
    }
}
