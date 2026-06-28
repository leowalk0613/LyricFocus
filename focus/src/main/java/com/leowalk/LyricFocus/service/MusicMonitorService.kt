package com.leowalk.LyricFocus.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.leowalk.LyricFocus.FocusPreferences

class MusicMonitorService : NotificationListenerService() {

    interface MusicStateListener {
        fun onMetadataChanged(metadata: MediaMetadata?)
        fun onPlaybackStateChanged(state: PlaybackState?)
        fun onSessionChanged(controller: MediaController?)
    }

    companion object {
        private const val TAG = "MusicMonitorService"
        private const val NOTIFICATION_ID = LyricNotificationManager.NOTIFICATION_ID
        private const val CHANNEL_ID = LyricNotificationManager.CHANNEL_ID
        private const val CHANNEL_NAME = "歌词服务"
        private const val REFRESH_INTERVAL_MS = 5000L

        var isServiceRunning = false
            private set

        var currentController: MediaController? = null
            private set

        var currentMetadata: MediaMetadata? = null
            private set

        var currentPlaybackState: PlaybackState? = null
            private set

        private val listeners = mutableListOf<MusicStateListener>()

        fun addListener(listener: MusicStateListener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }

        fun removeListener(listener: MusicStateListener) {
            listeners.remove(listener)
        }
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaControllerCallback: MediaController.Callback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var settingsReceiver: BroadcastReceiver? = null
    private var lastKnownTitle: String? = null
    private var lastKnownArtist: String? = null

    private val mediaSessionListener = object : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            Log.d(TAG, "Active sessions changed: ${controllers?.size ?: 0}")
            updateActiveSessions(controllers)
        }
    }

    private val periodicRefreshRunnable = object : Runnable {
        override fun run() {
            try {
                checkSessionHealth()
                refreshSessions()
            } catch (e: Exception) {
                Log.e(TAG, "Periodic refresh error", e)
            } finally {
                handler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicMonitorService onCreate")
        isServiceRunning = true

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        setupMediaSessionListener()
        registerSettingsReceiver()
        startPeriodicRefresh()
    }

    private fun registerSettingsReceiver() {
        unregisterSettingsReceiver()
        settingsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == FocusPreferences.ACTION_SETTINGS_CHANGED) {
                    reevaluateCurrentSession()
                }
            }
        }
        val filter = IntentFilter(FocusPreferences.ACTION_SETTINGS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
        }
    }

    private fun unregisterSettingsReceiver() {
        settingsReceiver?.let {
            runCatching { unregisterReceiver(it) }
        }
        settingsReceiver = null
    }

    private fun reevaluateCurrentSession() {
        val currentPkg = currentController?.packageName
        if (currentPkg != null && !FocusPreferences.isPackageAllowed(this, currentPkg)) {
            Log.d(TAG, "Current session blocked by whitelist: $currentPkg")
            clearCurrentSession()
        }
        refreshSessions()
    }

    private fun filterAllowedControllers(controllers: List<MediaController>): List<MediaController> {
        return controllers.filter { FocusPreferences.isPackageAllowed(this, it.packageName) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        refreshSessions()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MusicMonitorService onStartCommand")
        refreshSessions()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        // 使用 LyricNotificationManager 的统一渠道，无需重复创建
        // LyricNotificationManager.CHANNEL_ID 已在该类初始化时创建
    }

    private fun createNotification(): Notification {
        // 使用统一的前台服务通知，由 LyricNotificationManager 管理
        val lyricManager = LyricNotificationManager(this)
        return lyricManager.buildForegroundNotification()
    }

    private fun setupMediaSessionListener() {
        try {
            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, MusicMonitorService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                mediaSessionListener,
                componentName
            )
            Log.d(TAG, "Media session listener setup successful")
        } catch (e: SecurityException) {
            Log.e(TAG, "No notification access permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Setup media session listener error", e)
        }
    }

    private fun refreshSessions() {
        try {
            val componentName = ComponentName(this, MusicMonitorService::class.java)
            val sessions = mediaSessionManager?.getActiveSessions(componentName)
            updateActiveSessions(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Refresh sessions error", e)
        }
    }

    private fun updateActiveSessions(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            clearCurrentSession()
            return
        }

        val allowed = filterAllowedControllers(controllers)
        if (allowed.isEmpty()) {
            Log.d(TAG, "No whitelisted media session active")
            clearCurrentSession()
            return
        }

        val playingController = allowed.find { controller ->
            controller.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: allowed.firstOrNull()

        if (playingController != null &&
            playingController.sessionToken != currentController?.sessionToken
        ) {
            switchToSession(playingController)
        }
    }

    private fun switchToSession(controller: MediaController) {
        Log.d(TAG, "Switching to session: ${controller.packageName}")

        if (mediaControllerCallback != null) {
            currentController?.unregisterCallback(mediaControllerCallback!!)
        }

        currentController = controller

        mediaControllerCallback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                super.onMetadataChanged(metadata)
                currentMetadata = metadata
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                Log.d(TAG, "Metadata changed: $title")
                notifyMetadataChanged(metadata)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                super.onPlaybackStateChanged(state)
                currentPlaybackState = state
                Log.d(TAG, "Playback state changed: ${state?.state}")
                notifyPlaybackStateChanged(state)
            }
        }

        controller.registerCallback(mediaControllerCallback!!)

        currentMetadata = controller.metadata
        currentPlaybackState = controller.playbackState

        lastKnownTitle = currentMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        lastKnownArtist = currentMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

        notifySessionChanged(controller)
        notifyMetadataChanged(currentMetadata)
        notifyPlaybackStateChanged(currentPlaybackState)
    }

    private fun clearCurrentSession() {
        if (mediaControllerCallback != null) {
            currentController?.unregisterCallback(mediaControllerCallback!!)
        }
        currentController = null
        currentMetadata = null
        currentPlaybackState = null
        lastKnownTitle = null
        lastKnownArtist = null
        mediaControllerCallback = null

        notifySessionChanged(null)
        notifyMetadataChanged(null)
        notifyPlaybackStateChanged(null)
    }

    private fun notifySessionChanged(controller: MediaController?) {
        handler.post {
            listeners.forEach { it.onSessionChanged(controller) }
        }
    }

    private fun notifyMetadataChanged(metadata: MediaMetadata?) {
        handler.post {
            listeners.forEach { it.onMetadataChanged(metadata) }
        }
    }

    private fun notifyPlaybackStateChanged(state: PlaybackState?) {
        handler.post {
            listeners.forEach { it.onPlaybackStateChanged(state) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MusicMonitorService onDestroy")
        isServiceRunning = false
        unregisterSettingsReceiver()
        stopPeriodicRefresh()

        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(mediaSessionListener)
        } catch (e: Exception) {
            Log.e(TAG, "Remove listener error", e)
        }

        if (mediaControllerCallback != null) {
            currentController?.unregisterCallback(mediaControllerCallback!!)
        }
    }

    private fun startPeriodicRefresh() {
        handler.removeCallbacks(periodicRefreshRunnable)
        handler.post(periodicRefreshRunnable)
        Log.d(TAG, "Periodic refresh started")
    }

    private fun stopPeriodicRefresh() {
        handler.removeCallbacks(periodicRefreshRunnable)
        Log.d(TAG, "Periodic refresh stopped")
    }

    private fun checkSessionHealth() {
        val currentMeta = currentMetadata
        val title = currentMeta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = currentMeta?.getString(MediaMetadata.METADATA_KEY_ARTIST)

        if (title != null && artist != null && (title != lastKnownTitle || artist != lastKnownArtist)) {
            Log.d(TAG, "Health check detected metadata change: $title - $artist")
            lastKnownTitle = title
            lastKnownArtist = artist
            notifyMetadataChanged(currentMeta)
        }

        if (currentController != null) {
            try {
                val freshMeta = currentController?.metadata
                val freshState = currentController?.playbackState

                if (freshMeta != null && freshMeta != currentMetadata) {
                    Log.d(TAG, "Health check: stale metadata detected, refreshing")
                    currentMetadata = freshMeta
                    notifyMetadataChanged(freshMeta)
                }

                if (freshState != null && freshState != currentPlaybackState) {
                    Log.d(TAG, "Health check: stale playback state detected")
                    currentPlaybackState = freshState
                    notifyPlaybackStateChanged(freshState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Health check failed", e)
                clearCurrentSession()
            }
        }
    }
}
