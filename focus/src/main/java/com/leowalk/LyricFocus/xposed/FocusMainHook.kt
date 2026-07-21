package com.leowalk.LyricFocus.xposed

import com.leowalk.LyricFocus.xposed.hook.aod.AodFocusPluginHook
import com.leowalk.LyricFocus.xposed.hook.systemui.SystemUIHyperFocusHook
import com.leowalk.LyricFocus.xposed.hook.systemui.SystemUIPluginHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class FocusMainHook : XposedModule() {

    companion object {
        private const val TAG = "LyricFocus_Xposed"
        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val PACKAGE_AOD = "com.miui.aod"
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            val classLoader = param.classLoader
            when (param.packageName) {
                PACKAGE_SYSTEMUI -> {
                    SystemUIHyperFocusHook().install(classLoader, this)
                    SystemUIPluginHook().install(classLoader, this)
                }
                PACKAGE_AOD -> {
                    AodFocusPluginHook().install(classLoader, this)
                }
            }
        } catch (e: Throwable) {
            log(android.util.Log.ERROR, TAG, "hook failed", e)
        }
    }
}
