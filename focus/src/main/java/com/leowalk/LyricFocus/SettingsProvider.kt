package com.leowalk.LyricFocus

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * 提供给 SystemUI / AOD 进程查询模块开关的跨进程通道。
 *
 * SystemUI / AOD 进程（UID 1000）无法直接读取本应用私有 prefs
 * （app 数据目录 SELinux 保护，实测 Permission denied），导致
 * aodchange 外部渲染 / 焦点通知开关在 hook 侧检测失败、模块冲突。
 * 因此由本应用进程内读取 prefs 并通过 ContentProvider 返回。
 */
class SettingsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        return when (method) {
            "aodchange_mode" -> Bundle().apply {
                putBoolean("enabled", FocusPreferences.isAodchangeEnabled(ctx))
            }
            "focus_mode" -> Bundle().apply {
                putBoolean("enabled", FocusPreferences.isFocusEnabled(ctx))
            }
            else -> null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
