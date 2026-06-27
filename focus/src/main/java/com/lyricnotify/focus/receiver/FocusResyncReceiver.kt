package com.lyricnotify.focus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lyricnotify.focus.FocusPreferences
import com.lyricnotify.focus.service.LyricService

/** SystemUI 重启后拉起 LyricService 并重推焦点状态 */
class FocusResyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != FocusPreferences.ACTION_REQUEST_RESYNC) return
        val resync = Intent(context, LyricService::class.java).apply {
            action = FocusPreferences.ACTION_REQUEST_RESYNC
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(resync)
            } else {
                context.startService(resync)
            }
        } catch (_: Exception) {
            LyricService.start(context)
        }
    }
}
