package com.leowalk.ExternalLyricTest

import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Provider ↔ Activity 状态中枢。
 * 所有包经 [LyricReceiveEngine] 裁决后再更新 UI。
 */
object LyricHub {

    data class Snapshot(
        val method: String,
        val title: String,
        val artist: String,
        val line: String,
        val second: String,
        val timeMs: Long,
        val playing: Boolean,
        val loading: Boolean,
        val seq: Int,
        val pkg: String,
        val ctxIdx: Int?,
        val ctxLineCount: Int?,
        val rawPreview: String,
        val dropped: Boolean = false,
        val dropReason: String = "",
        val receivedAtElapsed: Long = SystemClock.elapsedRealtime(),
    )

    interface Listener {
        fun onUpdate(snapshot: Snapshot)
        fun onLogChanged() {}
    }

    @Volatile
    var latest: Snapshot? = null
        private set

    @Volatile
    private var engineState: LyricReceiveEngine.State = LyricReceiveEngine.State()

    val acceptedSeq: Int get() = engineState.acceptedSeq
    val dropCount: Int get() = engineState.dropCount

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val logLines = CopyOnWriteArrayList<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun addListener(listener: Listener) {
        listeners.add(listener)
        latest?.let { listener.onUpdate(it) }
        listener.onLogChanged()
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * @return false 表示包被引擎丢弃（过期 seq 等）
     */
    fun ingest(
        method: String,
        title: String,
        artist: String,
        line: String,
        second: String,
        timeMs: Long,
        playing: Boolean,
        loading: Boolean,
        seq: Int,
        pkg: String,
        ctxIdx: Int?,
        ctxLineCount: Int?,
        rawPreview: String,
    ): Boolean {
        val incoming = LyricReceiveEngine.Incoming(
            method = method,
            title = title,
            artist = artist,
            line = line,
            second = second,
            timeMs = timeMs,
            playing = playing,
            loading = loading,
            seq = seq,
            pkg = pkg,
            ctxIdx = ctxIdx,
            ctxLineCount = ctxLineCount,
        )
        val result = LyricReceiveEngine.apply(engineState, incoming)
        val stamp = timeFmt.format(Date())

        when (result) {
            is LyricReceiveEngine.Result.Dropped -> {
                engineState = result.state
                prependLog(
                    "[$stamp] DROP ${result.reason} | $method seq=$seq $title — ${line.take(40)}"
                )
                notifyLogOnly()
                return false
            }
            is LyricReceiveEngine.Result.Applied -> {
                engineState = result.state
                val s = result.state
                val snapshot = Snapshot(
                    method = s.method,
                    title = s.title,
                    artist = s.artist,
                    line = s.line,
                    second = s.second,
                    timeMs = s.timeMs,
                    playing = s.playing,
                    loading = s.loading,
                    seq = s.acceptedSeq,
                    pkg = s.pkg,
                    ctxIdx = s.ctxIdx,
                    ctxLineCount = s.ctxLineCount,
                    rawPreview = rawPreview,
                    dropped = false,
                )
                latest = snapshot
                val flag = when {
                    result.cleared && s.loading -> " CLEAR/loading"
                    result.cleared -> " CLEAR"
                    else -> ""
                }
                val ctx = s.ctxLineCount?.let { " ctx=${it}lines idx=${s.ctxIdx}" }.orEmpty()
                prependLog(
                    "[$stamp] $method seq=${s.acceptedSeq}$flag | ${s.title} — ${s.line.take(40)}$ctx"
                )
                listeners.forEach {
                    it.onUpdate(snapshot)
                    it.onLogChanged()
                }
                return true
            }
        }
    }

    fun logText(): String = logLines.joinToString("\n")

    /** 只清显示日志，不重置 acceptedSeq（否则迟到的旧包会再次上屏） */
    fun clearLog() {
        logLines.clear()
        notifyLogOnly()
    }

    fun resetSession() {
        engineState = LyricReceiveEngine.State()
        latest = null
        logLines.clear()
        notifyLogOnly()
    }

    private fun prependLog(line: String) {
        logLines.add(0, line)
        while (logLines.size > 200) {
            logLines.removeAt(logLines.lastIndex)
        }
    }

    private fun notifyLogOnly() {
        listeners.forEach { it.onLogChanged() }
    }
}
