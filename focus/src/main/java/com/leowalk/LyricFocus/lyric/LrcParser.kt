package com.leowalk.LyricFocus.lyric

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
    private val INLINE_TRILINGUAL_SEP = Regex("""\s*(?:\||/|//)\s*""")
    private val KANA_PATTERN = Regex("""[\u3040-\u309F\u30A0-\u30FF]""")
    private val HAN_PATTERN = Regex("""[\u4E00-\u9FFF]""")
    private val LATIN_LETTER_PATTERN = Regex("""[A-Za-z\u00C0-\u024F]""")

    private enum class LineKind {
        ORIGINAL,
        TRANSLATION,
        READING,
        UNKNOWN
    }

    private data class RawEntry(
        val time: Long,
        val text: String
    )

    private data class TrilingualParts(
        val original: String,
        val translation: String? = null,
        val reading: String? = null
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

        val mergedLines = mergeExternalTranslations(lyricInfo.lines, translationLines)
        return lyricInfo.copy(lines = mergedLines)
    }

    private fun mergeExternalTranslations(
        lyrics: List<LyricLine>,
        translations: List<LyricLine>
    ): List<LyricLine> {
        val useIndexMatch = shouldMatchTranslationByIndex(lyrics, translations)
        return lyrics.mapIndexed { index, line ->
            if (!line.translation.isNullOrBlank()) {
                return@mapIndexed line
            }
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
        val rawEntries = mutableListOf<RawEntry>()
        var meta = LrcMeta()
        var pendingTime: Long? = null

        for (line in lrcText.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                pendingTime = null
                continue
            }

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

            val times = parseTimeTags(trimmedLine)
            if (times.isNotEmpty()) {
                for (time in times) {
                    rawEntries.add(RawEntry(time, text))
                }
                pendingTime = times.last()
            } else if (pendingTime != null) {
                rawEntries.add(RawEntry(pendingTime, text))
            }
        }

        val lines = mergeMultilingualGroups(rawEntries)
        return lines to meta
    }

    private fun mergeMultilingualGroups(entries: List<RawEntry>): List<LyricLine> {
        if (entries.isEmpty()) return emptyList()

        val result = mutableListOf<LyricLine>()
        var index = 0
        while (index < entries.size) {
            val time = entries[index].time
            val groupTexts = mutableListOf<String>()
            while (index < entries.size && entries[index].time == time) {
                groupTexts.add(entries[index].text)
                index++
            }

            val parts = when (groupTexts.size) {
                1 -> splitInlineTrilingual(groupTexts[0]) ?: TrilingualParts(groupTexts[0])
                else -> classifyTrilingualLines(groupTexts)
            }
            result.add(
                LyricLine(
                    time = time,
                    text = parts.original,
                    translation = parts.translation,
                    reading = parts.reading
                )
            )
        }
        return result.sortedBy { it.time }
    }

    private fun splitInlineTrilingual(text: String): TrilingualParts? {
        val parts = INLINE_TRILINGUAL_SEP.split(text)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.size < 2) return null
        return classifyTrilingualLines(parts)
    }

    private fun classifyTrilingualLines(texts: List<String>): TrilingualParts {
        if (texts.isEmpty()) return TrilingualParts("")
        if (texts.size == 1) return TrilingualParts(texts[0])

        val tagged = texts.map { text -> text to detectLineKind(text) }

        if (texts.size >= 3) {
            val byKind = tagged.groupBy { it.second }
            val original = byKind[LineKind.ORIGINAL]?.firstOrNull()?.first ?: texts[0]
            val translation = byKind[LineKind.TRANSLATION]?.firstOrNull()?.first ?: texts[1]
            val reading = byKind[LineKind.READING]?.firstOrNull()?.first ?: texts[2]
            return TrilingualParts(original, translation, reading)
        }

        val firstKind = tagged[0].second
        val secondKind = tagged[1].second
        return when {
            firstKind == LineKind.ORIGINAL && secondKind == LineKind.TRANSLATION ->
                TrilingualParts(texts[0], texts[1])
            firstKind == LineKind.ORIGINAL && secondKind == LineKind.READING ->
                TrilingualParts(texts[0], reading = texts[1])
            firstKind == LineKind.TRANSLATION && secondKind == LineKind.ORIGINAL ->
                TrilingualParts(texts[1], texts[0])
            firstKind == LineKind.READING && secondKind == LineKind.ORIGINAL ->
                TrilingualParts(texts[1], reading = texts[0])
            detectLineKind(texts[0]) != LineKind.READING && detectLineKind(texts[1]) == LineKind.READING ->
                TrilingualParts(texts[0], reading = texts[1])
            detectLineKind(texts[1]) != LineKind.READING && detectLineKind(texts[0]) == LineKind.READING ->
                TrilingualParts(texts[1], reading = texts[0])
            else -> TrilingualParts(texts[0], texts[1])
        }
    }

    private fun detectLineKind(text: String): LineKind {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return LineKind.UNKNOWN

        val latinCount = LATIN_LETTER_PATTERN.findAll(trimmed).count()
        val meaningfulCount = trimmed.count { !it.isWhitespace() && !it.isISOControl() }
        val latinRatio = if (meaningfulCount == 0) 0.0 else latinCount.toDouble() / meaningfulCount

        val hasKana = KANA_PATTERN.containsMatchIn(trimmed)
        val hasHan = HAN_PATTERN.containsMatchIn(trimmed)

        return when {
            latinRatio >= 0.45 -> LineKind.READING
            hasKana -> LineKind.ORIGINAL
            hasHan -> LineKind.TRANSLATION
            latinCount >= 3 -> LineKind.READING
            else -> LineKind.UNKNOWN
        }
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
