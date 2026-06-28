package com.leowalk.LyricFocus.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.leowalk.LyricFocus.NotificationPermissionHelper
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.MainActivity
import com.leowalk.LyricFocus.R
import com.leowalk.LyricFocus.notification.RegularLyricNotificationStyle

class LyricNotificationManager(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastLyricText = ""
    private var lastSecondLineText = ""
    private var lastTitle = ""
    private var lastArtist = ""
    private var lastIsPlaying = false
    private var showLyricInShade = false

    companion object {
        const val CHANNEL_ID = "lyric_service"
        private const val CHANNEL_NAME = "歌词服务"
        const val NOTIFICATION_ID = 1
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "歌词显示前台服务通知"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAllowBubbles(false)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (showLyricInShade && lastIsPlaying && lastLyricText.isNotBlank()) {
            RegularLyricNotificationStyle.buildNotification(
                context = context,
                channelId = CHANNEL_ID,
                lyricText = lastLyricText,
                secondLineText = lastSecondLineText
            ).apply {
                contentIntent = pendingIntent
            }
        } else {
            val contentText = when {
                lastIsPlaying && lastTitle.isNotBlank() -> "正在播放: $lastTitle${if (lastArtist.isNotBlank()) " - $lastArtist" else ""}"
                else -> "正在运行"
            }
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("歌词显示服务")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        }
    }

    @SuppressLint("MissingPermission")
    fun updateForegroundNotification(title: String = "", artist: String = "", isPlaying: Boolean = false) {
        lastTitle = title
        lastArtist = artist
        lastIsPlaying = isPlaying

        if (!lastIsPlaying || lastLyricText.isBlank()) {
            lastLyricText = ""
            lastSecondLineText = ""
        }

        if (NotificationPermissionHelper.hasPostNotificationsPermission(context)) {
            notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification())
        }
    }

    @SuppressLint("MissingPermission")
    fun updateLyricNotification(
        lyricText: String,
        secondLineText: String = "",
        title: String = "",
        artist: String = "",
        isPlaying: Boolean = true
    ) {
        try {
            if (!NotificationPermissionHelper.hasPostNotificationsPermission(context)) {
                return
            }

            showLyricInShade = true

            if (!isPlaying || lyricText.isBlank()) {
                cancelLiveNotification()
                return
            }

            lastTitle = title
            lastArtist = artist
            lastIsPlaying = isPlaying

            if (lyricText == lastLyricText && secondLineText == lastSecondLineText) {
                return
            }

            lastLyricText = lyricText
            lastSecondLineText = secondLineText

            notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showLoadingNotification(title: String, artist: String) {
        lastLyricText = ""
        lastSecondLineText = ""
        updateLyricNotification(
            lyricText = "加载歌词中...",
            secondLineText = buildSongSubtitle(title, artist),
            title = title,
            artist = artist,
            isPlaying = true
        )
    }

    fun showNoLyricNotification(title: String, artist: String) {
        lastLyricText = ""
        lastSecondLineText = ""
        val noLyricText = title.ifBlank { "暂无歌词" }
        val noLyricSecond = artist.ifBlank { buildSongSubtitle(title, artist) }
        updateLyricNotification(
            lyricText = noLyricText,
            secondLineText = noLyricSecond,
            title = title,
            artist = artist,
            isPlaying = true
        )
    }

    fun buildSongSubtitle(title: String, artist: String): String {
        return when {
            title.isNotBlank() && artist.isNotBlank() -> "$title - $artist"
            title.isNotBlank() -> title
            artist.isNotBlank() -> artist
            else -> ""
        }
    }

    fun cancelRegularNotification() {
        cancelLiveNotification()
    }

    fun cancelLiveNotification() {
        lastLyricText = ""
        lastSecondLineText = ""
        showLyricInShade = false
        if (NotificationPermissionHelper.hasPostNotificationsPermission(context)) {
            notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification())
        }
    }

    fun cancelNotification() {
        cancelLiveNotification()
    }

    fun sendLyricData(
        lyricJson: String,
        position: Long,
        isPlaying: Boolean,
        title: String,
        artist: String,
        offset: Long = 0L,
        lyricText: String = "",
        secondLineText: String = "",
        musicPackage: String = "",
        forceResync: Boolean = false
    ) {
        try {
            val intent = Intent("com.leowalk.LyricFocus.action.LYRIC_DATA").apply {
                setPackage("com.android.systemui")
                putExtra("lyric_json", lyricJson)
                putExtra("position", position)
                putExtra("is_playing", isPlaying)
                putExtra("title", title)
                putExtra("artist", artist)
                putExtra("offset", offset)
                putExtra("sync_advance", FocusPreferences.getSyncAdvanceMs(context))
                putExtra("lyric_text", lyricText)
                putExtra("second_line", secondLineText)
                putExtra("music_package", musicPackage)
                if (forceResync) {
                    putExtra("force_resync", true)
                }
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendPlaybackState(isPlaying: Boolean) {
        try {
            val intent = Intent("com.leowalk.LyricFocus.action.PLAYBACK_STATE").apply {
                setPackage("com.android.systemui")
                putExtra("playing", isPlaying)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}