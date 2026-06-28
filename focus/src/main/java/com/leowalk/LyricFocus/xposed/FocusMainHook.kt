package com.leowalk.LyricFocus.xposed

import com.leowalk.LyricFocus.xposed.hook.aod.AodFocusPluginHook
import com.leowalk.LyricFocus.xposed.hook.systemui.SystemUIHyperFocusHook
import com.leowalk.LyricFocus.xposed.hook.systemui.SystemUIPluginHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class FocusMainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "LyricFocus_Xposed"
        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val PACKAGE_AOD = "com.miui.aod"

        fun log(msg: String) {
            XposedBridge.log("$TAG: $msg")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam == null) return
        try {
            when (lpparam.packageName) {
                PACKAGE_SYSTEMUI -> {
                    SystemUIHyperFocusHook().handleLoadPackage(lpparam)
                    SystemUIPluginHook().handleLoadPackage(lpparam)
                }
                PACKAGE_AOD -> {
                    AodFocusPluginHook().handleLoadPackage(lpparam)
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: hook failed")
            XposedBridge.log(e)
        }
    }
}
