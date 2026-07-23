package com.leowalk.LyricFocus.lyric

import android.os.RemoteException
import android.util.Log
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper

class SuperLyricBridge {

    private val tag = "SuperLyricBridge"
    private var receiver: ISuperLyricReceiver.Stub? = null
    private var callback: Callback? = null
    private val accumulatedLines = mutableListOf<LyricLine>()
    private var currentTitle = ""
    private var currentArtist = ""
    private var currentAlbum = ""
    private var lastLineTime = 0L

    interface Callback {
        fun onLineReceived(lineText: String, lineTranslation: String?, accumulatedLyricInfo: LyricInfo)
        fun onStop()
    }

    val isAvailable: Boolean
        get() = try {
            SuperLyricHelper.isAvailable()
        } catch (e: Exception) {
            Log.w(tag, "SuperLyricApi availability check failed", e)
            false
        }

    fun start(name: String, title: String, artist: String = "", album: String = "", cb: Callback) {
        stop()
        this.callback = cb
        this.currentTitle = title
        this.currentArtist = artist
        this.currentAlbum = album
        accumulatedLines.clear()
        lastLineTime = 0L

        receiver = object : ISuperLyricReceiver.Stub() {
            override fun onLyric(publisher: String?, data: SuperLyricData?) {
                if (data == null) return
                try {
                    if (data.hasTitle()) currentTitle = data.title ?: currentTitle
                    if (data.hasArtist()) currentArtist = data.artist ?: currentArtist
                    if (data.hasAlbum()) currentAlbum = data.album ?: currentAlbum

                    if (data.hasLyric()) {
                        val line = data.getLyric() ?: return
                        val text = line.text?.trim() ?: return
                        if (text.isBlank()) return

                        Log.d(tag, "onLyric: $text (time=${line.startTime})")
                        accumulatedLines.add(
                            LyricLine(time = line.startTime, text = text)
                        )

                        var translation: String? = null
                        if (data.hasTranslation()) {
                            val tLine = data.getTranslation()
                            val tText = tLine?.text?.trim()
                            if (!tText.isNullOrBlank()) {
                                accumulatedLines.lastOrNull()?.let { prev ->
                                    if (prev.time == line.startTime) {
                                        accumulatedLines.removeAt(accumulatedLines.lastIndex)
                                        accumulatedLines.add(prev.copy(translation = tText))
                                    }
                                }
                                translation = tText
                            }
                        }

                        val info = LyricInfo(
                            title = currentTitle,
                            artist = currentArtist,
                            album = currentAlbum,
                            lines = accumulatedLines.toList().sortedBy { it.time },
                            source = "Super Lyric"
                        )
                        callback?.onLineReceived(text, translation, info)
                    }
                } catch (e: RemoteException) {
                    Log.e(tag, "onLyric error", e)
                }
            }

            override fun onStop(publisher: String?, data: SuperLyricData?) {
                callback?.onStop()
            }
        }

        val rec = receiver ?: return
        try {
            SuperLyricHelper.registerReceiver(rec)
            Log.d(tag, "registered receiver")
        } catch (e: Exception) {
            Log.e(tag, "registerReceiver failed", e)
        }
    }

    fun stop() {
        callback = null
        receiver?.let {
            try {
                SuperLyricHelper.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(tag, "unregisterReceiver failed", e)
            }
        }
        receiver = null
        accumulatedLines.clear()
        lastLineTime = 0L
    }
}
