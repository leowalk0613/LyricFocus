package com.leowalk.LyricFocus.lyric

import android.app.Application
import android.util.Log
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.LyriconFactory
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.ProviderInfo
import io.github.proify.lyricon.lyric.model.Song

class LyriconBridge(context: Application) {

    private val tag = "LyriconBridge"
    private val appContext = context
    private var subscriber: LyriconSubscriber? = null
    private var callback: Callback? = null

    interface Callback {
        fun onSongReceived(lyricInfo: LyricInfo)
        fun onStop()
    }

    val isAvailable: Boolean
        get() = try {
            val sub = LyriconFactory.createSubscriber(appContext)
            sub.destroy()
            true
        } catch (e: Exception) {
            Log.w(tag, "Lyricon not available", e)
            false
        }

    fun start(cb: Callback) {
        stop()
        this.callback = cb

        try {
            val sub = LyriconFactory.createSubscriber(appContext)
            subscriber = sub
            sub.addConnectionListener(connectionListener)
            sub.subscribeActivePlayer(activePlayerListener)
            sub.register()
            Log.d(tag, "subscriber registered")
        } catch (e: Exception) {
            Log.e(tag, "createSubscriber failed", e)
        }
    }

    fun stop() {
        callback = null
        subscriber?.let {
            try {
                it.unsubscribeActivePlayer(activePlayerListener)
                it.unregister()
                it.destroy()
            } catch (e: Exception) {
                Log.e(tag, "destroy failed", e)
            }
        }
        subscriber = null
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnected(subscriber: LyriconSubscriber) {
            Log.d(tag, "connected")
        }

        override fun onReconnected(subscriber: LyriconSubscriber) {
            Log.d(tag, "reconnected")
        }

        override fun onDisconnected(subscriber: LyriconSubscriber) {
            Log.w(tag, "disconnected")
        }

        override fun onConnectTimeout(subscriber: LyriconSubscriber) {
            Log.w(tag, "connect timeout")
        }
    }

    private val activePlayerListener = object : ActivePlayerListener {
        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            Log.d(tag, "active provider: ${providerInfo?.providerPackageName}")
        }

        override fun onSongChanged(song: Song?) {
            if (song == null) return
            Log.d(tag, "onSongChanged: ${song.name}, ${song.lyrics?.size ?: 0} lines")

            val lines = song.lyrics?.map { line ->
                LyricLine(
                    time = line.begin,
                    text = line.text ?: "",
                    translation = line.translation?.takeIf { it.isNotBlank() }
                )
            } ?: emptyList()

            if (lines.isEmpty()) return

            val info = LyricInfo(
                title = song.name ?: "",
                artist = song.artist ?: "",
                album = "",
                lines = lines.sortedBy { it.time },
                source = "词幕 Lyricon"
            )
            callback?.onSongReceived(info)
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                callback?.onStop()
            }
        }

        override fun onPositionChanged(position: Long) {
            // ticker handles position via media session
        }

        override fun onSeekTo(position: Long) {}

        override fun onReceiveText(text: String?) {
            if (text.isNullOrBlank()) return
            val info = LyricInfo(
                title = "",
                artist = "",
                lines = listOf(LyricLine(time = 0, text = text)),
                source = "词幕 Lyricon"
            )
            callback?.onSongReceived(info)
        }

        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit
        override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit
    }
}
