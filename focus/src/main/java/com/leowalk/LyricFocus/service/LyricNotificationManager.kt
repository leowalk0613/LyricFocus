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
import com.leowalk.LyricFocus.notification.HyperFocusLyricStyle
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
        private const val CHANNEL_NAME = "LyricFocus 后台服务"
        const val NOTIFICATION_ID = 1

        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val ACTION_PLAYBACK_STATE = "com.leowalk.LyricFocus.action.PLAYBACK_STATE"
        private const val EXTRA_PLAYING = "playing"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "前台服务保活通道；锁屏 / 息屏歌词由焦点通知展示，不走此渠道"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)

            // 焦点通知渠道：应用进程直接发送，系统按 miui.focus.* extras 渲染为焦点通知
            val focusChannel = NotificationChannel(
                HyperFocusLyricStyle.CHANNEL_ID,
                "LyricFocus 焦点歌词",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "锁屏 / 息屏歌词焦点通知"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(focusChannel)
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

        val isShadeLyric = showLyricInShade && lastIsPlaying && lastLyricText.isNotBlank()
        if (isShadeLyric) {
            return RegularLyricNotificationStyle.buildNotification(
                context = context,
                channelId = CHANNEL_ID,
                lyricText = lastLyricText,
                secondLineText = lastSecondLineText
            ).apply {
                contentIntent = pendingIntent
            }
        }
        val iconHidden = FocusPreferences.isHideDesktopIcon(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (iconHidden) "LyricFocus" else "")
            .setContentText(if (iconHidden) "点击打开应用" else "")
            .setSmallIcon(if (iconHidden) R.drawable.ic_music_note else R.drawable.ic_transparent)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(if (iconHidden) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun updateForegroundNotification(title: String = "", artist: String = "", isPlaying: Boolean = false) {
        lastTitle = title
        lastArtist = artist
        lastIsPlaying = isPlaying
        lastLyricText = ""
        lastSecondLineText = ""
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

            if (!isPlaying || lyricText.isBlank()) {
                updateForegroundNotification(title, artist, isPlaying)
                return
            }

            showLyricInShade = true
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

    fun showAiTranslating(title: String, artist: String) {
        lastLyricText = ""
        lastSecondLineText = ""
        updateLyricNotification(
            lyricText = title.ifBlank { "AI翻译中..." },
            secondLineText = "AI翻译中...",
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
        lineTranslation: String? = null,
        musicPackage: String = "",
        forceResync: Boolean = false
    ) {
        postFocusLyric(
            lyricText = lyricText,
            secondLineText = secondLineText,
            lineTranslation = lineTranslation,
            title = title,
            artist = artist,
            musicPackage = musicPackage,
            isPlaying = isPlaying,
            force = forceResync
        )
    }

    /** 应用进程不再直接发焦点通知，改为广播到 SystemUI（uid=1000）发送，绕过 XMSF 认证 */
    fun postFocusLyric(
        lyricText: String,
        secondLineText: String,
        lineTranslation: String?,
        title: String,
        artist: String,
        musicPackage: String,
        isPlaying: Boolean,
        force: Boolean = false
    ) {
        // 已迁移至 SystemUI 侧发送，此处留空，避免应用进程直接发焦点通知导致重复/认证失败
    }

    fun sendPlaybackState(isPlaying: Boolean) {
        try {
            // 应用进程不再直接发焦点通知，改为广播到 SystemUI，由 SystemUI 处理停止/恢复
            val intent = Intent(ACTION_PLAYBACK_STATE).apply {
                setPackage(PACKAGE_SYSTEMUI)
                putExtra(EXTRA_PLAYING, isPlaying)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}