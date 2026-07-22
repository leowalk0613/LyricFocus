package com.leowalk.LyricFocus.xposed.hook.systemui

import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedModule

/**
 * 焦点通知置顶：仅在 AOD / 切歌时通过 cancel+repost 实现；通知中心保持原生排序（PRIORITY_MAX + sortKey=0）。
 * 所有 View/List 操作已移除，避免通知中心操作卡顿。
 */
object FocusPinAboveHook {

    @Suppress("UNUSED_PARAMETER")
    fun install(classLoader: ClassLoader, module: XposedModule, tag: String) {
        // 置顶逻辑已迁移至 SystemUIHyperFocusHook.forceCancelAndRepostForAod()
        // 仅在屏幕关闭进入 AOD 时 / 切歌时触发 cancel+repost，通知中心不干预
        module.log(Log.INFO, tag, "FocusPin disabled: pin-to-top now managed by AOD-only cancel+repost")
    }

    fun scheduleViewReorder(root: View?) {
        // no-op
    }
}
