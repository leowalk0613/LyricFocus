package com.lyricnotify.focus.notification

import android.app.Notification
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.lyricnotify.focus.R

object RegularLyricNotificationStyle {

    private const val PLACEHOLDER = "\u200B"
    private const val NBSP = "\u00A0"

    fun buildNotification(
        context: Context,
        channelId: String,
        lyricText: String,
        secondLineText: String
    ): Notification {
        val line1 = NotificationIconHelper.truncateLine(
            lyricText.ifBlank { "?" },
            NotificationIconHelper.MAX_LINE2_CHARS
        )
        val line2 = NotificationIconHelper.truncateLine(
            secondLineText,
            NotificationIconHelper.MAX_LINE3_CHARS
        )

        val contentView = RemoteViews(context.packageName, R.layout.notification_lyric_small)
        bindLyricViews(context, contentView, line1, line2)

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(NotificationIconHelper.createBlankIcon(context))
            .setContentTitle(PLACEHOLDER)
            .setContentText(PLACEHOLDER)
            .setCustomContentView(contentView)
            .setCustomBigContentView(contentView)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setDefaults(0)
            .setVibrate(null)
            .setSound(null)

        val notification = builder.build()
        notification.flags = notification.flags or
                Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_NO_CLEAR or
                Notification.FLAG_ONLY_ALERT_ONCE
        return notification
    }

    private fun bindLyricViews(
        context: Context,
        remoteViews: RemoteViews,
        line1: String,
        line2: String
    ) {
        val primaryColor = resolveTextColor(context, primary = true)
        val secondaryColor = resolveTextColor(context, primary = false)
        remoteViews.setTextViewText(R.id.tv_current_lyric, line1)
        remoteViews.setTextColor(R.id.tv_current_lyric, primaryColor)
        if (line2.isBlank() || line2 == NBSP) {
            remoteViews.setViewVisibility(R.id.tv_next_lyric, View.GONE)
        } else {
            remoteViews.setTextViewText(R.id.tv_next_lyric, line2)
            remoteViews.setTextColor(R.id.tv_next_lyric, secondaryColor)
            remoteViews.setViewVisibility(R.id.tv_next_lyric, View.VISIBLE)
        }
    }

    private fun resolveTextColor(context: Context, primary: Boolean): Int {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            if (primary) Color.parseColor("#E6FFFFFF") else Color.parseColor("#B3FFFFFF")
        } else {
            if (primary) Color.parseColor("#DE000000") else Color.parseColor("#99000000")
        }
    }
}
