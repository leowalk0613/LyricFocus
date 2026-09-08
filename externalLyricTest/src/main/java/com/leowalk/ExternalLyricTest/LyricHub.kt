package com.leowalk.ExternalLyricTest

import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Provider（任意进程）与 Activity 之间的内存状态 + 监听。
 * 同进程内用静态单例即可；LyricFocus 通过 ContentResolver.call 打到本进程 Provider。
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
        val pkg: String,
        val ctxIdx: Int?,
        val ctxLineCount: Int?,
        val rawPreview: String,
        val receivedAtElapsed: Long = SystemClock.elapsedRealtime()
    )

    interface Listener {
        fun onUpdate(snapshot: Snapshot)
    }

    @Volatile
    var latest: Snapshot? = null
        private set

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val logLines = CopyOnWriteArrayList<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun addListener(listener: Listener) {
        listeners.add(listener)
        latest?.let { listener.onUpdate(it) }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun publish(snapshot: Snapshot) {
        latest = snapshot
        val stamp = timeFmt.format(Date())
        val ctx = when {
            snapshot.ctxLineCount != null -> " ctx=${snapshot.ctxLineCount}lines idx=${snapshot.ctxIdx}"
            else -> ""
        }
        val line = "[$stamp] ${snapshot.method} | ${snapshot.title} — ${snapshot.line}$ctx"
        logLines.add(0, line)
        while (logLines.size > 200) {
            logLines.removeAt(logLines.lastIndex)
        }
        listeners.forEach { it.onUpdate(snapshot) }
    }

    fun logText(): String = logLines.joinToString("\n")

    fun clearLog() {
        logLines.clear()
    }
}
