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

            val rawLines = song.lyrics?.map { line ->
                LyricLine(
                    time = line.begin,
                    text = line.text ?: "",
                    translation = line.translation?.takeIf { it.isNotBlank() }
                )
            } ?: emptyList()

            if (rawLines.isEmpty()) return

            val merged = mergeSameTimeLines(rawLines)

            val info = LyricInfo(
                title = song.name ?: "",
                artist = song.artist ?: "",
                album = "",
                lines = merged.sortedBy { it.time },
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

    private fun mergeSameTimeLines(lines: List<LyricLine>): List<LyricLine> {
        if (lines.size < 2) return lines
        val sorted = lines.sortedBy { it.time }
        val result = mutableListOf<LyricLine>()
        var i = 0
        while (i < sorted.size) {
            val current = sorted[i]
            val group = mutableListOf(current)
            var j = i + 1
            while (j < sorted.size && (sorted[j].time - current.time) <= MERGE_TIME_THRESHOLD_MS) {
                group += sorted[j]
                j++
            }
            if (group.size == 1) {
                result.add(current)
            } else {
                val primary = group.firstOrNull { it.text.isNotBlank() }
                val trans = group.firstOrNull { it.translation?.isNotBlank() == true }
                val reading = group.firstOrNull { it.reading?.isNotBlank() == true }
                result.add(LyricLine(
                    time = current.time,
                    text = primary?.text?.takeIf { it.isNotBlank() } ?: group.firstOrNull { it.text.isNotBlank() }?.text ?: current.text,
                    translation = trans?.translation ?: primary?.translation,
                    reading = reading?.reading ?: primary?.reading
                ))
            }
            i = j
        }
        return result
    }

    companion object {
        private const val MERGE_TIME_THRESHOLD_MS = 150L
    }
}
