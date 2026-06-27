package com.lyricnotify.focus.lyric

import org.json.JSONArray
import org.json.JSONObject

data class LyricInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val lines: List<LyricLine> = emptyList(),
    val offset: Long = 0,
    val source: String = ""
) {
    companion object {
        val EMPTY = LyricInfo()
    }

    val isEmpty: Boolean
        get() = lines.isEmpty()

    val duration: Long
        get() = if (lines.isNotEmpty()) lines.last().time else 0L

    fun getAdjustedPosition(position: Long, syncAdvanceMs: Long): Long =
        position + offset + syncAdvanceMs

    fun getCurrentLineIndex(position: Long, syncAdvanceMs: Long): Int {
        if (lines.isEmpty()) return -1
        val adjustedPosition = getAdjustedPosition(position, syncAdvanceMs)
        var left = 0
        var right = lines.size - 1
        var result = -1

        while (left <= right) {
            val mid = (left + right) / 2
            if (lines[mid].time <= adjustedPosition) {
                result = mid
                left = mid + 1
            } else {
                right = mid - 1
            }
        }
        return result
    }

    fun getCurrentLine(position: Long, syncAdvanceMs: Long): LyricLine? {
        val index = getCurrentLineIndex(position, syncAdvanceMs)
        return if (index >= 0 && index < lines.size) lines[index] else null
    }

    fun getNextLine(position: Long, syncAdvanceMs: Long): LyricLine? {
        val index = getCurrentLineIndex(position, syncAdvanceMs) + 1
        return if (index >= 0 && index < lines.size) lines[index] else null
    }

    fun getNextLineTime(position: Long, syncAdvanceMs: Long): Long {
        val nextLine = getNextLine(position, syncAdvanceMs) ?: return position + 5000L
        return nextLine.time - offset - syncAdvanceMs
    }

    fun getNextLineSwitchDelay(position: Long, syncAdvanceMs: Long): Long {
        val nextLine = getNextLine(position, syncAdvanceMs) ?: return 1000L
        val switchAt = nextLine.time - offset - syncAdvanceMs
        return (switchAt - position).coerceIn(16L, 30_000L)
    }

    fun getSecondLineText(
        position: Long,
        syncAdvanceMs: Long,
        fallback: String = ""
    ): String {
        val current = getCurrentLine(position, syncAdvanceMs)
        val translation = current?.translation
        if (!translation.isNullOrBlank()) return translation
        return getNextLine(position, syncAdvanceMs)?.text ?: fallback
    }

    fun getLineProgress(position: Long, syncAdvanceMs: Long): Float {
        val currentIndex = getCurrentLineIndex(position, syncAdvanceMs)
        if (currentIndex < 0 || currentIndex >= lines.size) return 0f
        val currentLine = lines[currentIndex]
        val nextLine = if (currentIndex + 1 < lines.size) lines[currentIndex + 1] else null
        val adjustedPosition = getAdjustedPosition(position, syncAdvanceMs)
        val currentTime = currentLine.time
        val nextTime = nextLine?.time ?: (currentTime + 5000)
        val duration = nextTime - currentTime
        if (duration <= 0) return 1f
        val progress = (adjustedPosition - currentTime).toFloat() / duration.toFloat()
        return progress.coerceIn(0f, 1f)
    }

    fun toJson(): String {
        val array = JSONArray()
        for (line in lines) {
            val obj = JSONObject()
            obj.put("time", line.time)
            obj.put("text", line.text)
            line.translation?.let { obj.put("translation", it) }
            array.put(obj)
        }
        return array.toString()
    }
}
