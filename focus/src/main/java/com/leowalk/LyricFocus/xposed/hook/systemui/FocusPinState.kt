package com.leowalk.LyricFocus.xposed.hook.systemui

/**
 * SystemUI / 插件进程共享的焦点置顶开关与播放状态。
 */
object FocusPinState {

    @Volatile
    var pinAboveMedia: Boolean = true

    @Volatile
    var isPlaying: Boolean = false

    @Volatile
    var lyricActive: Boolean = false

}
