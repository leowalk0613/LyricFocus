package com.leowalk.LyricFocus.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.leowalk.LyricFocus.MainActivity
import com.leowalk.LyricFocus.R
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.FocusStyleSnapshot
import com.leowalk.LyricFocus.lyric.LyricInfo
import com.leowalk.LyricFocus.lyric.LyricManager
import com.leowalk.LyricFocus.util.AlbumColorExtractor
import com.leowalk.LyricFocus.util.AlbumArtLoader
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LyricService : Service(), MusicMonitorService.MusicStateListener {

    data class PreviewState(
        val lyricText: String = "",
        val secondLine: String = "",
        val lineTranslation: String? = null,
        val title: String = "",
        val artist: String = "",
        val isPlaying: Boolean = false,
        val musicPackage: String = "",
        val nextLyricLines: List<String> = emptyList(),
        val nextLyricTranslations: List<String> = emptyList()
    )

    companion object {
        private const val TAG = "LyricService"

        const val ACTION_START = "com.leowalk.LyricFocus.action.START"
        const val ACTION_STOP = "com.leowalk.LyricFocus.action.STOP"
        const val ACTION_UPDATE_LYRIC = "com.leowalk.LyricFocus.action.UPDATE_LYRIC"
        const val ACTION_ALARM_TICK = "com.leowalk.LyricFocus.action.ALARM_TICK"
        const val ACTION_RESYNC = FocusPreferences.ACTION_REQUEST_RESYNC
        const val EXTRA_LYRIC_TEXT = "lyric_text"
        const val EXTRA_SECOND_LINE = "second_line"
        const val EXTRA_LINE_TRANSLATION = "line_translation"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_POSITION = "position"
        const val EXTRA_MUSIC_PACKAGE = "music_package"

        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val MIN_SCHEDULE_MS = 100L
        private const val UPDATE_INTERVAL_MS = 250L

        var isServiceRunning = false
            private set

        @Volatile
        var currentLyricSourceHit: String = ""
            private set

        @Volatile
        var currentLyricSongLabel: String = ""
            private set

        @Volatile
        var currentFetchedLyricTitle: String = ""
            private set

        @Volatile
        var currentFetchedLyricArtist: String = ""
            private set

        @Volatile
        var currentFetchedLyricAlbum: String = ""
            private set

        @Volatile
        var currentLyricHasTranslation: Boolean = false
            private set

        @Volatile
        var currentLyricFromAi: Boolean = false
            private set

        @Volatile
        var currentLyricInfoForPreview: LyricInfo = LyricInfo.EMPTY
            private set

        @Volatile
        var currentPlaybackPositionMs: Long = 0L
            private set

        @Volatile
        var previewState: PreviewState = PreviewState()
            private set

        @Volatile
        var onPreviewStateChanged: (() -> Unit)? = null

        private fun notifyPreviewStateChanged() {
            onPreviewStateChanged?.invoke()
        }

        fun start(context: Context) {
            val intent = Intent(context, LyricService::class.java)
            intent.action = ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LyricService::class.java)
            intent.action = ACTION_STOP
            context.stopService(intent)
        }
    }

    private lateinit var lyricNotificationManager: LyricNotificationManager
    private lateinit var lyricManager: LyricManager
    private val superLyricBridge = com.leowalk.LyricFocus.lyric.SuperLyricBridge()
    private lateinit var lyriconBridge: com.leowalk.LyricFocus.lyric.LyriconBridge
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmIntent: PendingIntent
    private val handler = Handler(Looper.getMainLooper())

    private var currentLyricInfo: LyricInfo = LyricInfo.EMPTY
    private var currentTitle = ""
    private var currentArtist = ""
    private var currentAlbumArt: Bitmap? = null
    private var currentAlbumArtKey: String = ""
    private var isPlaying = false
    private var currentPosition: Long = 0
    private var lastUpdateTime: Long = 0

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var fetchLyricJob: Job? = null
    private var albumArtRetryJob: Job? = null
    private var lastBroadcastLyric = ""
    private var lastBroadcastSecond = ""

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_ALARM_TICK) {
                acquireWakeLock()
                updateLyricProgress()
            }
        }
    }

    private val lyricUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) {
                return
            }
            updateLyricProgress()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    private val resyncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_RESYNC) {
                resyncFocusState()
            }
        }
    }

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FocusPreferences.ACTION_SETTINGS_CHANGED) {
                if (!isCurrentAppAllowed()) {
                    clearLyricStateForBlockedApp()
                    return
                }
                if (FocusPreferences.shouldExtractAlbumColors(this@LyricService)) {
                    extractAndSaveAlbumColor(currentAlbumArt)
                } else {
                    FocusPreferences.clearExtractedTextColor(this@LyricService)
                }
                if (intent.hasExtra(FocusPreferences.EXTRA_LYRIC_SOURCE)) {
                    val metadata = MusicMonitorService.currentController?.metadata
                        ?: MusicMonitorService.currentMetadata
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: currentTitle
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: currentArtist
                    if (title.isNotBlank()) {
                        currentTitle = title
                        currentArtist = artist
                        fetchLyric(title, artist)
                        return
                    }
                }
                if (intent.getBooleanExtra(FocusStyleSnapshot.EXTRA_STYLE_CHANGED, false)) {
                    val needsColorResync = FocusPreferences.shouldExtractAlbumColors(this@LyricService) &&
                        FocusPreferences.getExtractedTextColor(this@LyricService) != null &&
                        !intent.hasExtra(FocusStyleSnapshot.EXTRA_STYLE_EXTRACTED_COLOR)
                    if (needsColorResync) {
                        FocusPreferences.notifyStyleSettingsChanged(this@LyricService)
                        return
                    }
                    resyncFocusState()
                    return
                }
                resyncFocusState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LyricService onCreate")

        lyricNotificationManager = LyricNotificationManager(this)
        lyricManager = LyricManager(this)
        lyriconBridge = com.leowalk.LyricFocus.lyric.LyriconBridge(application)

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LyricFocus::LyricWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_ALARM_TICK).setPackage(packageName)
        alarmIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val filter = IntentFilter(ACTION_ALARM_TICK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(alarmReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(alarmReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register alarm receiver", e)
        }

        try {
            val resyncFilter = IntentFilter(ACTION_RESYNC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(resyncReceiver, resyncFilter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(resyncReceiver, resyncFilter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register resync receiver", e)
        }

        try {
            val settingsFilter = IntentFilter(FocusPreferences.ACTION_SETTINGS_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(settingsReceiver, settingsFilter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(settingsReceiver, settingsFilter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register settings receiver", e)
        }

        val initialNotification = NotificationCompat.Builder(this, LyricNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("\u6b63\u5728\u542f\u52a8...")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                LyricNotificationManager.NOTIFICATION_ID,
                initialNotification
            )
        } else {
            startForeground(LyricNotificationManager.NOTIFICATION_ID, initialNotification)
        }

        MusicMonitorService.addListener(this)
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Action START")
                startLyricUpdate()
            }
            ACTION_RESYNC -> {
                Log.d(TAG, "Action RESYNC")
                resyncFocusState()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Action STOP")
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startLyricUpdate() {
        handler.removeCallbacks(lyricUpdateRunnable)
        handler.post(lyricUpdateRunnable)
        scheduleNextAlarm()
        acquireWakeLock()
        Log.d(TAG, "Lyric update started, isPlaying=$isPlaying")
    }

    private fun stopLyricUpdate() {
        handler.removeCallbacks(lyricUpdateRunnable)
        cancelAlarm()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire()
                Log.d(TAG, "WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    private fun scheduleNextAlarm() {
        try {
            val triggerTime = SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    alarmIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    alarmIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }

    private fun scheduleNextLyricUpdate() {
        try {
            val syncAdvanceMs = FocusPreferences.getSyncAdvanceMs(this)
            val delayMs = if (isPlaying && !currentLyricInfo.isEmpty) {
                currentLyricInfo.getNextLineSwitchDelay(currentPosition, syncAdvanceMs)
            } else {
                3000L
            }.coerceAtLeast(MIN_SCHEDULE_MS)

            val triggerTime = SystemClock.elapsedRealtime() + delayMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    alarmIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    alarmIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next lyric update", e)
            scheduleNextAlarm()
        }
    }

    private fun cancelAlarm() {
        try {
            alarmManager.cancel(alarmIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarm", e)
        }
    }

    private fun clampToSongDuration(position: Long): Long {
        val duration = MusicMonitorService.currentMetadata
            ?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        return if (duration > 0L) position.coerceIn(0L, duration) else position.coerceAtLeast(0L)
    }

    private fun extrapolatePlaybackPosition(state: PlaybackState): Long {
        val updateTime = state.lastPositionUpdateTime
        if (updateTime <= 0L) {
            return clampToSongDuration(state.position)
        }

        val elapsed = SystemClock.elapsedRealtime() - updateTime
        if (elapsed < 0L || elapsed > 30_000L) {
            return clampToSongDuration(state.position)
        }
        return clampToSongDuration(state.position + elapsed)
    }

    private fun resolveCurrentPosition(): Long {
        val playbackState = MusicMonitorService.currentController?.playbackState
        if (playbackState == null) {
            if (isPlaying && lastUpdateTime > 0L) {
                val elapsed = System.currentTimeMillis() - lastUpdateTime
                if (elapsed in 1L..30_000L) {
                    currentPosition = clampToSongDuration(currentPosition + elapsed)
                    lastUpdateTime = System.currentTimeMillis()
                }
            }
            return currentPosition
        }

        when (playbackState.state) {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING -> {
                currentPosition = extrapolatePlaybackPosition(playbackState)
                lastUpdateTime = System.currentTimeMillis()
            }
            else -> {
                currentPosition = clampToSongDuration(playbackState.position)
                lastUpdateTime = System.currentTimeMillis()
            }
        }
        return currentPosition
    }

    private fun restartLyricTickerIfPlaying() {
        if (isPlaying && !currentLyricInfo.isEmpty) {
            startLyricUpdate()
        }
    }

    private fun updateLyricProgress() {
        resolveCurrentPosition()
        currentPlaybackPositionMs = currentPosition
        updateNotification()
        scheduleNextLyricUpdate()
    }

    private fun updateNotification() {
        if (!isCurrentAppAllowed()) {
            clearLyricStateForBlockedApp()
            return
        }
        if (currentLyricInfo.isEmpty) {
            return
        }

        val syncAdvanceMs = FocusPreferences.getSyncAdvanceMs(this)
        val currentLine = currentLyricInfo.getCurrentLine(currentPosition, syncAdvanceMs)
        val currentLyricText: String
        val secondLineText: String
        val lineTranslation: String?
        if (currentLine == null && currentTitle.isNotBlank()) {
            // 还没到第一句歌词，显示首行歌词
            currentLyricText = currentLyricInfo.lines.firstOrNull()?.text?.ifBlank { currentTitle } ?: currentTitle
            secondLineText = currentLyricInfo.lines.firstOrNull()?.translation
                ?: currentLyricInfo.lines.getOrNull(1)?.text
                ?: currentArtist
            lineTranslation = currentLyricInfo.lines.firstOrNull()?.translation
        } else {
            currentLyricText = currentLine?.text?.ifBlank { "\u266A" } ?: "\u266A"
            lineTranslation = currentLyricInfo.getCurrentTranslation(currentPosition, syncAdvanceMs)
            secondLineText = currentLyricInfo.getSecondLineText(
                currentPosition,
                syncAdvanceMs,
                lyricNotificationManager.buildSongSubtitle(currentTitle, currentArtist)
            )
        }

        if (FocusPreferences.isShowInShade(this) && isPlaying) {
            lyricNotificationManager.updateLyricNotification(
                lyricText = currentLyricText,
                secondLineText = secondLineText,
                title = currentTitle,
                artist = currentArtist,
                isPlaying = isPlaying
            )
        } else {
            lyricNotificationManager.updateForegroundNotification(
                title = currentTitle,
                artist = currentArtist,
                isPlaying = isPlaying
            )
        }

        sendLyricBroadcastIfChanged(currentLyricText, secondLineText, lineTranslation)

        val (nextLines, nextTrans) = collectNextLyricLines(syncAdvanceMs)
        previewState = PreviewState(
            lyricText = currentLyricText,
            secondLine = secondLineText,
            lineTranslation = lineTranslation,
            title = currentTitle,
            artist = currentArtist,
            isPlaying = isPlaying,
            musicPackage = currentMusicPackage(),
            nextLyricLines = nextLines,
            nextLyricTranslations = nextTrans
        )
        notifyPreviewStateChanged()
    }

    private fun collectNextLyricLines(syncAdvanceMs: Long): Pair<List<String>, List<String>> {
        if (currentLyricInfo.isEmpty) return Pair(emptyList(), emptyList())
        val idx = currentLyricInfo.getCurrentLineIndex(currentPosition, syncAdvanceMs)
        val lines = currentLyricInfo.lines
        if (idx < 0) {
            return Pair(
                lines.take(8).map { it.text },
                lines.take(8).map { it.translation ?: "" }
            )
        }
        val texts = mutableListOf<String>()
        val trans = mutableListOf<String>()
        for (i in idx until (idx + 8).coerceAtMost(lines.size)) {
            texts += lines[i].text
            trans += lines[i].translation ?: ""
        }
        return Pair(texts, trans)
    }

    private fun resyncFocusState() {
        if (!FocusPreferences.isFocusEnabled(this)) {
            return
        }
        refreshPlaybackFromMonitor()
        resolveCurrentPosition()
        lyricNotificationManager.sendPlaybackState(isPlaying)
        lastBroadcastLyric = ""
        lastBroadcastSecond = ""
        when {
            !isPlaying -> return
            currentTitle.isBlank() -> refreshMetadataFromMonitor()
        }
        if (currentTitle.isBlank()) {
            return
        }
        if (currentLyricInfo.isEmpty) {
            fetchLyric(currentTitle, currentArtist)
            return
        }
        sendLyricDataToSystemUI(currentLyricInfo, currentTitle, currentArtist, force = true)
        updateNotification()
        if (isPlaying) {
            startLyricUpdate()
        }
        Log.d(TAG, "Focus state resynced to SystemUI, playing=$isPlaying title=$currentTitle")
    }

    private fun refreshPlaybackFromMonitor() {
        val state = MusicMonitorService.currentPlaybackState
        if (state != null) {
            currentPosition = extrapolatePlaybackPosition(state)
            lastUpdateTime = System.currentTimeMillis()
            isPlaying = state.state == PlaybackState.STATE_PLAYING
        }
    }

    private fun refreshMetadataFromMonitor() {
        val metadata = MusicMonitorService.currentController?.metadata
            ?: MusicMonitorService.currentMetadata
            ?: return
        currentTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        currentArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
    }

    private fun forceSendLyricBroadcast() {
        if (!FocusPreferences.isFocusEnabled(this) || currentLyricInfo.isEmpty) {
            return
        }
        val syncAdvanceMs = FocusPreferences.getSyncAdvanceMs(this)
        val currentLine = currentLyricInfo.getCurrentLine(currentPosition, syncAdvanceMs)
        val lyricText: String
        val secondLineText: String
        val lineTranslation: String?
        if (currentLine == null && currentTitle.isNotBlank()) {
            // 还没到第一句歌词，显示首行歌词
            lyricText = currentLyricInfo.lines.firstOrNull()?.text?.ifBlank { currentTitle } ?: currentTitle
            secondLineText = currentLyricInfo.lines.firstOrNull()?.translation
                ?: currentLyricInfo.lines.getOrNull(1)?.text
                ?: currentArtist
            lineTranslation = currentLyricInfo.lines.firstOrNull()?.translation
        } else {
            lyricText = currentLine?.text ?: "\u266A"
            lineTranslation = currentLyricInfo.getCurrentTranslation(currentPosition, syncAdvanceMs)
            secondLineText = currentLyricInfo.getSecondLineText(
                currentPosition,
                syncAdvanceMs,
                lyricNotificationManager.buildSongSubtitle(currentTitle, currentArtist)
            )
        }
        lastBroadcastLyric = lyricText
        lastBroadcastSecond = secondLineText
        sendLyricBroadcastTo(PACKAGE_SYSTEMUI, lyricText, secondLineText, lineTranslation, force = true)
    }

    private fun resetBroadcastCache() {
        lastBroadcastLyric = ""
        lastBroadcastSecond = ""
    }

    private fun sendLyricBroadcastIfChanged(
        lyricText: String,
        secondLine: String,
        lineTranslation: String? = null
    ) {
        if (lyricText == lastBroadcastLyric && secondLine == lastBroadcastSecond) {
            return
        }
        lastBroadcastLyric = lyricText
        lastBroadcastSecond = secondLine
        sendLyricBroadcast(lyricText, secondLine, lineTranslation)
    }

    private fun sendLyricBroadcast(
        lyricText: String,
        secondLine: String,
        lineTranslation: String? = null
    ) {
        if (FocusPreferences.isFocusEnabled(this)) {
            sendLyricBroadcastTo(PACKAGE_SYSTEMUI, lyricText, secondLine, lineTranslation)
        }
    }

    private fun sendLyricBroadcastTo(
        packageName: String,
        lyricText: String,
        secondLine: String,
        lineTranslation: String? = null,
        force: Boolean = false
    ) {
        try {
            val intent = Intent(ACTION_UPDATE_LYRIC).apply {
                setPackage(packageName)
                putExtra(EXTRA_LYRIC_TEXT, lyricText)
                putExtra(EXTRA_SECOND_LINE, secondLine)
                if (lineTranslation != null) {
                    putExtra(EXTRA_LINE_TRANSLATION, lineTranslation)
                }
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_TITLE, currentTitle)
                putExtra(EXTRA_ARTIST, currentArtist)
                putExtra(EXTRA_POSITION, currentPosition)
                putExtra(EXTRA_MUSIC_PACKAGE, currentMusicPackage())
                putExtra("sync_advance", FocusPreferences.getSyncAdvanceMs(this@LyricService))
                if (force) {
                    putExtra("force_resync", true)
                }
                FocusPreferences.fillStyleExtras(this, this@LyricService)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send broadcast to $packageName", e)
        }
    }

    private fun sendLyricDataToSystemUI(
        lyricInfo: LyricInfo,
        title: String,
        artist: String,
        force: Boolean = false
    ) {
        if (!FocusPreferences.isFocusEnabled(this)) {
            return
        }
        val syncAdvanceMs = FocusPreferences.getSyncAdvanceMs(this)
        val lyricJson = lyricInfo.toJson()
        val currentLine = lyricInfo.getCurrentLine(currentPosition, syncAdvanceMs)
        val currentLyricText: String
        val secondLineText: String
        val lineTranslation: String?
        if (currentLine == null && title.isNotBlank()) {
            // 还没到第一句歌词，显示首行歌词
            currentLyricText = currentLyricInfo.lines.firstOrNull()?.text?.ifBlank { title } ?: title
            secondLineText = currentLyricInfo.lines.firstOrNull()?.translation
                ?: currentLyricInfo.lines.getOrNull(1)?.text
                ?: artist
            lineTranslation = currentLyricInfo.lines.firstOrNull()?.translation
        } else {
            currentLyricText = currentLine?.text?.takeIf { it.isNotBlank() } ?: "\u266A"
            lineTranslation = lyricInfo.getCurrentTranslation(currentPosition, syncAdvanceMs)
            secondLineText = lyricInfo.getSecondLineText(
                currentPosition,
                syncAdvanceMs,
                lyricNotificationManager.buildSongSubtitle(title, artist)
            )
        }
        lyricNotificationManager.sendLyricData(
            lyricJson = lyricJson,
            position = currentPosition,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            offset = lyricInfo.offset,
            lyricText = currentLyricText,
            secondLineText = secondLineText,
            lineTranslation = lineTranslation,
            musicPackage = currentMusicPackage(),
            forceResync = force
        )
    }

    private fun extractAndSaveAlbumColor(bitmap: Bitmap?, forceNotify: Boolean = false) {
        val monetEnabled = FocusPreferences.isMonetDynamicColorEnabled(this)
        val textExtractionEnabled = FocusPreferences.isTextColorExtractionEnabled(this)
        val colorModeEnabled = FocusPreferences.isColorModeEnabled(this)
        val customAodAlbum = FocusPreferences.isCustomAodLayout(this) &&
            FocusPreferences.getCustomAodColorMode(this) == FocusPreferences.CUSTOM_AOD_COLOR_ALBUM
        if (!monetEnabled && !textExtractionEnabled && !customAodAlbum) {
            FocusPreferences.clearExtractedTextColor(this)
            return
        }
        val previous = FocusPreferences.getExtractedTextColor(this)
        val previousBg = FocusPreferences.getExtractedBgColor(this)
        val previousAccent = FocusPreferences.getExtractedAccentColor(this)
        val art = bitmap ?: AlbumArtLoader.load(this, MusicMonitorService.currentMetadata)
        if (colorModeEnabled && (monetEnabled || textExtractionEnabled)) {
            val distinct = AlbumColorExtractor.extractDistinctColors(art)
            if (distinct == null) {
                if (previous != null || previousBg != null || previousAccent != null || forceNotify) {
                    FocusPreferences.clearExtractedTextColor(this)
                    publishAlbumColorUpdate()
                }
                return
            }
            if (distinct.primaryText == previous && distinct.background == previousBg &&
                distinct.accent == previousAccent && !forceNotify
            ) {
                return
            }
            FocusPreferences.setExtractedDistinctColors(this, distinct)
        } else if (monetEnabled) {
            val scheme = AlbumColorExtractor.extractMonetScheme(art)
            if (scheme == null) {
                if (previous != null || previousBg != null || previousAccent != null || forceNotify) {
                    FocusPreferences.clearExtractedTextColor(this)
                    publishAlbumColorUpdate()
                }
                return
            }
            if (scheme.primaryText == previous && scheme.background == previousBg &&
                scheme.accent == previousAccent && !forceNotify
            ) {
                return
            }
            FocusPreferences.setExtractedMonetScheme(this, scheme)
        } else {
            val colors = AlbumColorExtractor.extractLyricColors(art)
            if (colors == null) {
                if (previous != null || previousBg != null || previousAccent != null || forceNotify) {
                    FocusPreferences.clearExtractedTextColor(this)
                    publishAlbumColorUpdate()
                }
                return
            }
            if (colors.accent == previous && colors.backgroundEstimate == previousBg &&
                colors.accent == previousAccent && !forceNotify
            ) {
                return
            }
            FocusPreferences.setExtractedColors(this, colors.accent, colors.backgroundEstimate)
        }
        publishAlbumColorUpdate()
    }

    private fun publishAlbumColorUpdate() {
        FocusPreferences.notifyStyleSettingsChanged(this)
        if (isPlaying && currentTitle.isNotBlank()) {
            resyncFocusState()
        }
    }

    private fun scheduleAlbumArtRetry() {
        if (!FocusPreferences.shouldExtractAlbumColors(this)) return
        albumArtRetryJob?.cancel()
        albumArtRetryJob = serviceScope.launch {
            for (attempt in 0 until 6) {
                delay(400L * (attempt + 1))
                val metadata = MusicMonitorService.currentMetadata ?: continue
                val art = AlbumArtLoader.load(this@LyricService, metadata) ?: continue
                val artKey = AlbumArtLoader.artKey(metadata).ifBlank {
                    "${currentTitle}|${currentArtist}"
                }
                if (artKey == currentAlbumArtKey &&
                    FocusPreferences.getExtractedTextColor(this@LyricService) != null
                ) {
                    return@launch
                }
                currentAlbumArtKey = artKey
                currentAlbumArt = art
                extractAndSaveAlbumColor(art, forceNotify = true)
                return@launch
            }
        }
    }

    private fun clearAlbumColorForNewSong() {
        if (!FocusPreferences.shouldExtractAlbumColors(this)) return
        FocusPreferences.clearExtractedTextColor(this)
        publishAlbumColorUpdate()
    }

    private fun currentMusicPackage(): String {
        return MusicMonitorService.currentController?.packageName ?: ""
    }

    private fun isCurrentAppAllowed(): Boolean {
        return FocusPreferences.isPackageAllowed(this, currentMusicPackage())
    }

    private fun clearLyricStateForBlockedApp() {
        fetchLyricJob?.cancel()
        stopLyricUpdate()
        stopRealtimeBridges()
        currentTitle = ""
        currentArtist = ""
        currentAlbumArt = null
        currentAlbumArtKey = ""
        currentLyricInfo = LyricInfo.EMPTY
        currentLyricSourceHit = ""
        currentLyricSongLabel = ""
        currentFetchedLyricTitle = ""
        currentFetchedLyricArtist = ""
        currentFetchedLyricAlbum = ""
        currentLyricHasTranslation = false
        currentLyricFromAi = false
        resetBroadcastCache()
        lyricNotificationManager.cancelNotification()
        lyricNotificationManager.sendPlaybackState(false)
        Log.d(TAG, "Cleared lyric state for non-whitelisted app: ${currentMusicPackage()}")
    }

    override fun onSessionChanged(controller: MediaController?) {
        Log.d(TAG, "Session changed: ${controller?.packageName}")
        if (controller != null && !FocusPreferences.isPackageAllowed(this, controller.packageName)) {
            clearLyricStateForBlockedApp()
            return
        }
        if (controller != null) {
            val state = controller.playbackState
            currentPosition = if (state != null) {
                extrapolatePlaybackPosition(state)
            } else {
                0L
            }
            lastUpdateTime = System.currentTimeMillis()
        }
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        Log.d(TAG, "Metadata changed")
        if (!isCurrentAppAllowed()) {
            clearLyricStateForBlockedApp()
            return
        }
        if (metadata == null) {
            currentTitle = ""
            currentArtist = ""
            currentAlbumArt = null
            currentAlbumArtKey = ""
            currentLyricInfo = LyricInfo.EMPTY
            lastBroadcastLyric = ""
            lastBroadcastSecond = ""
            lyricNotificationManager.cancelNotification()
            lyricNotificationManager.sendPlaybackState(false)
            return
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val artKey = AlbumArtLoader.artKey(metadata)
        val songChanged = title != currentTitle || artist != currentArtist
        val artChanged = artKey.isNotBlank() && artKey != currentAlbumArtKey

        if (songChanged) {
            currentTitle = title
            currentArtist = artist
            currentLyricInfo = LyricInfo.EMPTY
            currentLyricInfoForPreview = LyricInfo.EMPTY
            currentLyricSourceHit = ""
            resetBroadcastCache()
            clearAlbumColorForNewSong()
            clearAlbumColorForNewSong()
            fetchLyric(title, artist)
            previewState = PreviewState(
                lyricText = title,
                secondLine = artist,
                lineTranslation = null,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                musicPackage = currentMusicPackage()
            )
            notifyPreviewStateChanged()
            if (isPlaying) {
                restartLyricTickerIfPlaying()
            }
        }

        if (songChanged || artChanged) {
            currentAlbumArtKey = artKey
            currentAlbumArt = AlbumArtLoader.load(this, metadata)
            extractAndSaveAlbumColor(currentAlbumArt, forceNotify = songChanged || artChanged)
            if (currentAlbumArt == null && FocusPreferences.shouldExtractAlbumColors(this)) {
                scheduleAlbumArtRetry()
            }
        }

        currentPosition = MusicMonitorService.currentController?.playbackState?.let { state ->
            extrapolatePlaybackPosition(state)
        } ?: 0L
        lastUpdateTime = System.currentTimeMillis()
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        Log.d(TAG, "Playback state changed: ${state?.state}")
        if (!isCurrentAppAllowed()) {
            clearLyricStateForBlockedApp()
            return
        }
        if (state != null) {
            currentPosition = extrapolatePlaybackPosition(state)
            lastUpdateTime = System.currentTimeMillis()
            isPlaying = state.state == PlaybackState.STATE_PLAYING
            previewState = previewState.copy(isPlaying = isPlaying)
            notifyPreviewStateChanged()

            // notify SystemUI playback state; pause cancels focus notification
            lyricNotificationManager.sendPlaybackState(isPlaying)

            if (isPlaying) {
                startLyricUpdate()
                if (currentLyricInfo.isEmpty && currentTitle.isNotBlank()) {
                    if (fetchLyricJob?.isActive == true) {
                        // ??????loading ???? fetchLyric ???
                    } else {
                        sendNoLyricStateToSystemUI(currentTitle, currentArtist)
                    }
                }
            } else {
                stopLyricUpdate()
                lyricNotificationManager.updateForegroundNotification(
                    title = currentTitle,
                    artist = currentArtist,
                    isPlaying = false
                )
            }
        }
    }

    private fun fetchLyric(title: String, artist: String) {
        fetchLyricJob?.cancel()
        stopRealtimeBridges()

        if (title.isEmpty() || !isCurrentAppAllowed()) {
            currentLyricInfo = LyricInfo.EMPTY
            return
        }

        val source = FocusPreferences.getLyricSource(this)

        if (source == FocusPreferences.LYRIC_SOURCE_SUPERLYRIC) {
            superLyricBridge.start("SuperLyric", title, artist, "", superLyricCb)
            return
        }
        if (source == FocusPreferences.LYRIC_SOURCE_LYRICON) {
            lyriconBridge.start(lyriconCb)
            return
        }
        if (source == FocusPreferences.LYRIC_SOURCE_LYRICINFO) {
            fetchFromLyricInfo(title, artist)
            return
        }

        fetchLyricJob = serviceScope.launch {
            try {
                var lyricInfo = lyricManager.fetchLyric(title, artist)
                if (lyricInfo != null && !lyricInfo.isEmpty) {
                    applyLyricResult(lyricInfo, title, artist)

                    if (FocusPreferences.isAiTranslateEnabled(this@LyricService)) {
                        try {
                            lyricNotificationManager.showAiTranslating(title, artist)
                            val translated = lyricManager.translateWithAi(lyricInfo, title, artist)
                            if (translated !== lyricInfo) {
                                applyLyricResult(translated, title, artist)
                                lyricInfo = translated
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "AI translate error", e)
                        }
                    }

                    if (FocusPreferences.isAiPolishEnabled(this@LyricService)) {
                        try {
                            val polished = lyricManager.polishWithAi(lyricInfo, title, artist)
                            if (polished !== lyricInfo) {
                                applyLyricResult(polished, title, artist)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "AI polish error", e)
                        }
                    }
                } else {
                    clearLyricResult()
                    sendNoLyricStateToSystemUI(title, artist, force = true)
                    Log.d(TAG, "No lyric found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch lyric error", e)
                clearLyricResult()
                sendNoLyricStateToSystemUI(title, artist, force = true)
            }
        }
    }

    private fun applyLyricResult(lyricInfo: LyricInfo, title: String, artist: String, restartTicker: Boolean = true) {
        currentLyricInfo = lyricInfo
        currentLyricInfoForPreview = lyricInfo
        currentLyricSourceHit = lyricInfo.source
        currentLyricSongLabel = listOf(title, artist)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { title }
        currentFetchedLyricTitle = lyricInfo.title
        currentFetchedLyricArtist = lyricInfo.artist
        currentFetchedLyricAlbum = lyricInfo.album
        currentLyricHasTranslation = lyricInfo.lines.any { it.translation != null }
        currentLyricFromAi = lyricInfo.source.contains("AI翻译")
        currentPosition = MusicMonitorService.currentController?.playbackState?.let { state ->
            extrapolatePlaybackPosition(state)
        } ?: 0L
        lastUpdateTime = System.currentTimeMillis()
        resetBroadcastCache()
        sendLyricDataToSystemUI(currentLyricInfo, currentTitle, currentArtist, force = true)
        updateNotification()
        if (restartTicker) restartLyricTickerIfPlaying()
        Log.d(TAG, "Lyric loaded: ${lyricInfo.lines.size} lines, source=${lyricInfo.source}, first=${lyricInfo.lines.firstOrNull()?.text?.take(20)}")
    }

    private fun fetchFromLyricInfo(title: String, artist: String) {
        fetchLyricJob = serviceScope.launch {
            try {
                val metadata = MusicMonitorService.currentMetadata ?: return@launch
                val lyricInfoJson = metadata.getString("lyricInfo") ?: return@launch
                val root = org.json.JSONObject(lyricInfoJson)
                val lyricText = root.optString("lyric", "")
                if (lyricText.isBlank()) return@launch
                val songName = root.optString("songName", title)
                val songArtist = root.optString("artist", artist)
                // 校验歌词是否匹配当前播放的歌曲，避免切歌后显示旧歌词
                if (title.isNotBlank() && songName.isNotBlank()) {
                    val titleMatch = title.equals(songName, ignoreCase = true) ||
                        title.replace(" ", "").equals(songName.replace(" ", ""), ignoreCase = true) ||
                        songName.contains(title, ignoreCase = true) ||
                        title.contains(songName, ignoreCase = true)
                    if (!titleMatch) {
                        Log.d(TAG, "LyricInfo song mismatch: expected='$title', got='$songName', retrying in 500ms")
                        clearLyricResult()
                        kotlinx.coroutines.delay(500L)
                        val retryMetadata = MusicMonitorService.currentMetadata
                        if (retryMetadata != null) {
                            val retryJson = retryMetadata.getString("lyricInfo")
                            if (retryJson != null) {
                                val retryRoot = org.json.JSONObject(retryJson)
                                val retryName = retryRoot.optString("songName", "")
                                val retryMatch = title.equals(retryName, ignoreCase = true) ||
                                    title.replace(" ", "").equals(retryName.replace(" ", ""), ignoreCase = true) ||
                                    retryName.contains(title, ignoreCase = true) ||
                                    title.contains(retryName, ignoreCase = true)
                                if (!retryMatch) {
                                    Log.d(TAG, "LyricInfo retry still mismatch: expected='$title', got='$retryName'")
                                    return@launch
                                }
                                return@launch parseAndApplyLyricInfo(retryRoot, title, artist)
                            }
                        }
                        return@launch
                    }
                }
                parseAndApplyLyricInfo(root, title, artist)
            } catch (e: Exception) {
                Log.e(TAG, "LyricInfo parse error", e)
            }
        }
    }

    private fun parseAndApplyLyricInfo(root: org.json.JSONObject, title: String, artist: String) {
        try {
            // 防止切歌后旧协程的解析结果覆盖新歌词
            if (currentTitle.isNotBlank() && title.isNotBlank() &&
                currentTitle != title && !currentTitle.contains(title, ignoreCase = true)) {
                Log.d(TAG, "LyricInfo stale result ignored: current='$currentTitle', expected='$title'")
                return
            }
            val lyricText = root.optString("lyric", "")
            val songName = root.optString("songName", title)
            val songArtist = root.optString("artist", artist)
            val lyricInfo = com.leowalk.LyricFocus.lyric.LrcParser.parse(lyricText)
            if (lyricInfo.lines.size < 2) return
            val result = lyricInfo.copy(
                title = songName,
                artist = songArtist,
                source = "LyricInfo"
            )
            applyLyricResult(result, songName, songArtist)
            Log.d(TAG, "LyricInfo loaded: ${result.lines.size} lines")
            for (i in 0 until minOf(5, result.lines.size)) {
                Log.d(TAG, "LyricInfo line[$i]: text='${result.lines[i].text.take(40)}' trans='${result.lines[i].translation?.take(40) ?: ""}' time=${result.lines[i].time}")
            }
            // Dump raw lines around the timestamp range to debug parsing
            val rawAround = lyricText.lines().filter { it.contains("[00:06") || it.contains("[00:07") }
            for (rl in rawAround.take(6)) {
                Log.d(TAG, "LyricInfo raw: $rl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LyricInfo parse error", e)
        }
    }

    private fun clearLyricResult() {
        currentLyricInfo = LyricInfo.EMPTY
        currentLyricSourceHit = ""
        currentLyricSongLabel = ""
        currentFetchedLyricTitle = ""
        currentFetchedLyricArtist = ""
        currentFetchedLyricAlbum = ""
        currentLyricHasTranslation = false
        currentLyricFromAi = false
    }

    private val superLyricCb = object : com.leowalk.LyricFocus.lyric.SuperLyricBridge.Callback {
        private var tickerStarted = false

        override fun onLineReceived(lineText: String, lineTranslation: String?, accumulatedLyricInfo: LyricInfo) {
            if (lineText.isBlank()) return
            // 校验歌词来源是否匹配当前播放歌曲，防止切歌后旧 Bridge 回调残留
            if (currentTitle.isNotBlank() && accumulatedLyricInfo.title.isNotBlank()) {
                val match = currentTitle.equals(accumulatedLyricInfo.title, ignoreCase = true) ||
                    currentTitle.replace(" ", "").equals(accumulatedLyricInfo.title.replace(" ", ""), ignoreCase = true) ||
                    accumulatedLyricInfo.title.contains(currentTitle, ignoreCase = true) ||
                    currentTitle.contains(accumulatedLyricInfo.title, ignoreCase = true)
                if (!match) {
                    Log.d(TAG, "SuperLyric song mismatch ignored: current='$currentTitle', received='${accumulatedLyricInfo.title}'")
                    return
                }
            }
            Log.d(TAG, "SuperLyric line: $lineText, hasTrans=${lineTranslation != null}, totalLines=${accumulatedLyricInfo.lines.size}")
            applyLyricResult(accumulatedLyricInfo, currentTitle, currentArtist, restartTicker = !tickerStarted)
            tickerStarted = true
        }

        override fun onStop() {
            Log.d(TAG, "SuperLyric onStop, totalLines=${currentLyricInfo.lines.size}")
            tickerStarted = false
            stopLyricUpdate()
        }
    }

    private val lyriconCb = object : com.leowalk.LyricFocus.lyric.LyriconBridge.Callback {
        override fun onSongReceived(lyricInfo: LyricInfo) {
            if (lyricInfo.lines.isEmpty()) return
            // 校验歌词来源是否匹配当前播放歌曲，防止切歌后旧 Bridge 回调残留
            if (currentTitle.isNotBlank() && lyricInfo.title.isNotBlank()) {
                val match = currentTitle.equals(lyricInfo.title, ignoreCase = true) ||
                    currentTitle.replace(" ", "").equals(lyricInfo.title.replace(" ", ""), ignoreCase = true) ||
                    lyricInfo.title.contains(currentTitle, ignoreCase = true) ||
                    currentTitle.contains(lyricInfo.title, ignoreCase = true)
                if (!match) {
                    Log.d(TAG, "Lyricon song mismatch ignored: current='$currentTitle', received='${lyricInfo.title}'")
                    return
                }
            }
            Log.d(TAG, "Lyricon song: ${lyricInfo.title}, ${lyricInfo.lines.size} lines")
            applyLyricResult(lyricInfo, lyricInfo.title.ifBlank { currentTitle },
                lyricInfo.artist.ifBlank { currentArtist })
        }

        override fun onStop() {
            stopLyricUpdate()
        }
    }

    private fun stopRealtimeBridges() {
        superLyricBridge.stop()
        if (::lyriconBridge.isInitialized) lyriconBridge.stop()
    }

    private fun sendLoadingStateToSystemUI(title: String, artist: String) {
        val subtitle = lyricNotificationManager.buildSongSubtitle(title, artist)
        if (FocusPreferences.isShowInShade(this)) {
            lyricNotificationManager.showLoadingNotification(title, artist)
        }
        val loadingText = "\u52a0\u8f7d\u6b4c\u8bcd\u4e2d..."
        val loadingJson = buildSingleLineLyricJson(loadingText)
        pushPlaceholderFocusToSystemUI(
            lyricText = loadingText,
            secondLine = subtitle,
            title = title,
            artist = artist,
            lyricJson = loadingJson
        )
    }

    private fun sendNoLyricStateToSystemUI(title: String, artist: String, force: Boolean = true) {
        if (FocusPreferences.isShowInShade(this)) {
            lyricNotificationManager.showNoLyricNotification(title, artist)
        }
        val noLyricText = title.ifBlank { "\u6682\u65e0\u6b4c\u8bcd" }
        val noLyricSecond = artist.ifBlank { lyricNotificationManager.buildSongSubtitle(title, artist) }
        val noLyricJson = buildSingleLineLyricJson(noLyricText)
        pushPlaceholderFocusToSystemUI(
            lyricText = noLyricText,
            secondLine = noLyricSecond,
            title = title,
            artist = artist,
            lyricJson = noLyricJson
        )
    }

    /** ???/????????????????????????? */
    private fun pushPlaceholderFocusToSystemUI(
        lyricText: String,
        secondLine: String,
        title: String,
        artist: String,
        lyricJson: String,
        position: Long = currentPosition
    ) {
        if (!FocusPreferences.isFocusEnabled(this)) {
            return
        }
        refreshPlaybackFromMonitor()
        lyricNotificationManager.sendPlaybackState(isPlaying)
        resetBroadcastCache()
        lyricNotificationManager.sendLyricData(
            lyricJson = lyricJson,
            position = position,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            lyricText = lyricText,
            secondLineText = secondLine,
            lineTranslation = null,
            musicPackage = currentMusicPackage(),
            forceResync = true
        )
        lastBroadcastLyric = lyricText
        lastBroadcastSecond = secondLine
        sendLyricBroadcastTo(PACKAGE_SYSTEMUI, lyricText, secondLine, lineTranslation = null, force = true)
    }

    private fun buildSingleLineLyricJson(text: String): String {
        return JSONArray()
            .put(JSONObject().put("time", 0).put("text", text))
            .toString()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LyricService onDestroy")
        isServiceRunning = false

        stopLyricUpdate()
        albumArtRetryJob?.cancel()
        stopRealtimeBridges()

        try {
            unregisterReceiver(alarmReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister alarm receiver", e)
        }

        try {
            unregisterReceiver(resyncReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister resync receiver", e)
        }

        try {
            unregisterReceiver(settingsReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister settings receiver", e)
        }

        fetchLyricJob?.cancel()
        MusicMonitorService.removeListener(this)
        lyricNotificationManager.cancelNotification()
        lyricNotificationManager.sendPlaybackState(false)
    }

}
