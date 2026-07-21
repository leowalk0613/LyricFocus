package com.leowalk.LyricFocus.xposed.hook.systemui

/**
 * SystemUI / 插件进程共享的焦点置顶开关与播放状态。
 * 当焦点通知列表检测到歌词通知排位降低时，通过 onRepostRequested 回调触发 cancel+repost 重新置顶。
 */
object FocusPinState {

    @Volatile
    var pinAboveMedia: Boolean = true

    @Volatile
    var isPlaying: Boolean = false

    @Volatile
    var lyricActive: Boolean = false

    @Volatile
    var onRepostRequested: (() -> Unit)? = null

    fun shouldPin(): Boolean = pinAboveMedia && isPlaying && lyricActive
}
