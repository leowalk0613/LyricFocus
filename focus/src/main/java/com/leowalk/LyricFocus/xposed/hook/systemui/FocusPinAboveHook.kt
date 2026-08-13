package com.leowalk.LyricFocus.xposed.hook.systemui

/**
 * 焦点通知置顶：仅在 AOD / 切歌时通过 cancel+repost 实现；通知中心保持原生排序（PRIORITY_MAX + sortKey=0）。
 * 所有 View/List 操作已移除，避免通知中心操作卡顿。
 */
