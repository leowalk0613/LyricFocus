package com.lyricnotify.focus.lyric

object LrcParser {
    /** 标准 LRC：[分:秒.毫秒/百分秒] */
    private val TIME_LINE_PATTERN = Regex("\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\]")
    /** 网易等源：[分:秒:百分秒]，如 [00:18:21] */
    private val TIME_LINE_COLON_CS_PATTERN = Regex("\\[(\\d{1,2}):(\\d{2}):(\\d{2})\\]")
    private val META_PATTERN = Regex("\\[([a-z]+):(.+?)\\]", RegexOption.IGNORE_CASE)
    private val CREDIT_LINE_PATTERN = Regex(
        """^(作[词詞]|作曲|編曲|编曲|词/曲|詞/曲|监修|監修)\s*[:：]?""",
        RegexOption.IGNORE_CASE
    )

    /** 将 [MM:SS:CC] 转为 [MM:SS.CC]，统一走标准解析。 */
    fun normalizeLrcTimestamps(lrcText: String): String {
        return TIME_LINE_COLON_CS_PATTERN.replace(lrcText) { match ->
            "[${match.groupValues[1]}:${match.groupValues[2]}.${match.groupValues[3]}]"
        }
    }

    private fun hasTimeTags(line: String): Boolean {
        return TIME_LINE_PATTERN.containsMatchIn(line)
    }

    private fun stripTimeTags(line: String): String {
        return TIME_LINE_PATTERN.replace(line, "").trim()
    }

    private fun parseTimeTags(line: String): List<Long> {
        return TIME_LINE_PATTERN.findAll(line).map { parseDotTime(it) }.toList()
    }

    private fun isSkippableLine(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        if (t.length > 80) return false
        return CREDIT_LINE_PATTERN.containsMatchIn(t)
    }

    fun parseWithTranslation(lrcText: String, tlyricText: String?): LyricInfo {
        val lyricInfo = parse(lrcText)
        if (tlyricText.isNullOrBlank()) return lyricInfo

        val translationLines = parseLyricLinesOnly(tlyricText)
        if (translationLines.isEmpty()) return lyricInfo

        val mergedLines = mergeTranslations(lyricInfo.lines, translationLines)
        return lyricInfo.copy(lines = mergedLines)
    }

    private fun mergeTranslations(
        lyrics: List<LyricLine>,
        translations: List<LyricLine>
    ): List<LyricLine> {
        val useIndexMatch = shouldMatchTranslationByIndex(lyrics, translations)
        return lyrics.mapIndexed { index, line ->
            val translation = if (useIndexMatch) {
                translations.getOrNull(index)?.text
            } else {
                findClosestTranslation(line.time, translations)
            }
            line.copy(translation = translation?.takeIf { it.isNotBlank() })
        }
    }

    private fun shouldMatchTranslationByIndex(
        lyrics: List<LyricLine>,
        translations: List<LyricLine>
    ): Boolean {
        if (translations.size == lyrics.size) {
            val distinctTimes = translations.map { it.time }.distinct().size
            if (distinctTimes <= 1) return true
            if (!hasGoodTimeCorrelation(lyrics, translations)) return true
        }
        return translations.map { it.time }.distinct().size <= 1
    }

    private fun hasGoodTimeCorrelation(
        lyrics: List<LyricLine>,
        translations: List<LyricLine>
    ): Boolean {
        if (lyrics.size != translations.size) return false
        val matched = lyrics.zip(translations).count { (l, t) ->
            kotlin.math.abs(l.time - t.time) <= 500
        }
        return matched > lyrics.size / 2
    }

    private fun parseLyricLinesOnly(lrcText: String): List<LyricLine> {
        return buildLyricLines(normalizeLrcTimestamps(lrcText), parseMeta = false).first
    }

    private fun findClosestTranslation(time: Long, translations: List<LyricLine>): String? {
        if (translations.isEmpty()) return null

        var closest: LyricLine? = null
        var minDiff = Long.MAX_VALUE

        for (t in translations) {
            val diff = kotlin.math.abs(t.time - time)
            if (diff < minDiff) {
                minDiff = diff
                closest = t
            }
        }

        return if (minDiff <= 500) closest?.text else null
    }

    fun parse(lrcText: String): LyricInfo {
        if (lrcText.isBlank()) return LyricInfo.EMPTY

        val (lines, meta) = buildLyricLines(normalizeLrcTimestamps(lrcText), parseMeta = true)
        return LyricInfo(
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            lines = lines,
            offset = meta.offset
        )
    }

    private data class LrcMeta(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val offset: Long = 0L
    )

    private fun buildLyricLines(lrcText: String, parseMeta: Boolean): Pair<List<LyricLine>, LrcMeta> {
        val lyricLines = mutableListOf<LyricLine>()
        var meta = LrcMeta()

        for (line in lrcText.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            if (parseMeta) {
                val metaMatch = META_PATTERN.find(trimmedLine)
                if (metaMatch != null && !hasTimeTags(trimmedLine)) {
                    when (metaMatch.groupValues[1].lowercase()) {
                        "ti" -> meta = meta.copy(title = metaMatch.groupValues[2].trim())
                        "ar" -> meta = meta.copy(artist = metaMatch.groupValues[2].trim())
                        "al" -> meta = meta.copy(album = metaMatch.groupValues[2].trim())
                        "offset" -> meta = meta.copy(
                            offset = metaMatch.groupValues[2].trim().toLongOrNull() ?: 0
                        )
                    }
                    continue
                }
            }

            val text = stripTimeTags(trimmedLine)
            if (text.isEmpty() || isSkippableLine(text)) continue

            for (time in parseTimeTags(trimmedLine)) {
                lyricLines.add(LyricLine(time, text))
            }
        }

        lyricLines.sortBy { it.time }
        return lyricLines to meta
    }

    private fun parseDotTime(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val millisStr = match.groupValues[3]
        val millis = when (millisStr.length) {
            2 -> millisStr.toLong() * 10
            3 -> millisStr.toLong()
            else -> millisStr.padEnd(3, '0').take(3).toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + millis
    }
}
