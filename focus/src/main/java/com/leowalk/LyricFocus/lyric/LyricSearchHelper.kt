package com.leowalk.LyricFocus.lyric

object LyricSearchHelper {

    private val FEAT_SUFFIX = Regex(
        """[\s(\[（【]+(?:feat\.|ft\.|with)\b[\s\S]*$""",
        RegexOption.IGNORE_CASE
    )

    private val VERSION_TAG = Regex(
        """[\s(\[（【]+(?:live|现场版?|acoustic|remix|demo|instrumental|cover|studio|edit|mix|version|ver)\b[\s\S]*?[\)\]）】]\s*""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_PAREN = Regex("""[\(\[（【][^)\]）】]*[\)\]）】]\s*$""")

    /** 多艺术家分隔符：/ & 、 ; 以及 " and " / " x " */
    private val ARTIST_SEPARATOR = Regex(
        """\s*[/&、，,;；]\s*|\s+(?:and|x|feat\.?|ft\.?)\s+""",
        RegexOption.IGNORE_CASE
    )

    /** 名字本身含分隔符的艺人/组合特例（小写比较），不做拆分。例如 Leo/need */
    private val ARTIST_NAME_EXCEPTIONS = hashSetOf("leo/need")

    /** 艺术家黑名单（小写比较），匹配到这些艺术家的结果会被排除 */
    private val ARTIST_BLACKLIST = hashSetOf("sazablue")

    fun isArtistBlacklisted(artist: String): Boolean {
        return artist.trim().lowercase() in ARTIST_BLACKLIST
    }

    /** 去掉 (feat. …)、(Live)、(现场版) 等后缀，以及尾部的 -ArtistName，便于搜索「透明なパレット (feat. …)」「倒数 (Live)-G.E.M. 邓紫棋」这类长标题 */
    fun normalizeTitleForSearch(title: String, artist: String = ""): String {
        var t = title.trim()
        if (t.isEmpty()) return t
        t = FEAT_SUFFIX.replace(t, "").trim()
        repeat(3) {
            val next = VERSION_TAG.replace(t, "").trim()
            if (next == t) return@repeat
            t = next
        }
        t = stripTrailingArtist(t, artist)
        repeat(3) {
            val next = TRAILING_PAREN.replace(t, "").trim()
            if (next == t) return@repeat
            t = next
        }
        return t.ifBlank { title.trim() }
    }

    /** 拆分多艺术家字符串：「ぬゆり/須田景凪」→ [ぬゆり, 須田景凪]。
     *  名字本身含分隔符的特例（如 Leo/need）保持整体不拆分。 */
    fun splitArtists(artist: String): List<String> {
        val trimmed = artist.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.lowercase() in ARTIST_NAME_EXCEPTIONS) return listOf(trimmed)
        return trimmed.split(ARTIST_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun buildSearchKeywords(title: String, artist: String): List<String> {
        val normalized = normalizeTitleForSearch(title, artist)
        val keys = linkedSetOf<String>()
        val artistParts = splitArtists(artist)
        if (normalized.isNotBlank() && artist.isNotBlank()) {
            keys.add("$normalized $artist")
            keys.add("$artist $normalized")
            for (part in artistParts) {
                if (part.isNotBlank()) {
                    keys.add("$part $normalized")
                    keys.add("$normalized $part")
                }
            }
            if (artistParts.size >= 2) {
                val joined = artistParts.joinToString(" ")
                keys.add("$joined $normalized")
                keys.add("$normalized $joined")
            }
        }
        if (title.isNotBlank() && artist.isNotBlank() && title != normalized) {
            keys.add("$title $artist")
            keys.add("$artist $title")
        }
        if (normalized.isNotBlank()) keys.add(normalized)
        if (title.isNotBlank() && title != normalized) keys.add(title)
        if (normalized.endsWith('.') && normalized.length > 1) {
            val withoutDot = normalized.dropLast(1)
            if (artist.isNotBlank()) {
                keys.add("$withoutDot $artist")
                keys.add("$artist $withoutDot")
            }
            keys.add(withoutDot)
        }
        return keys.toList()
    }

    private fun stripTrailingArtist(title: String, artist: String): String {
        if (title.isBlank() || artist.isBlank()) return title
        val lowerTitle = title.lowercase()
        val lowerArtist = artist.lowercase()
        val separators = listOf(" - ", " – ", "—", "-")
        for (sep in separators) {
            val sepIndex = lowerTitle.lastIndexOf(sep)
            if (sepIndex > 0) {
                val afterSep = lowerTitle.substring(sepIndex + sep.length).trim()
                if (afterSep.contains(lowerArtist) || lowerArtist.contains(afterSep)) {
                    return title.substring(0, sepIndex).trim()
                }
            }
        }
        return title
    }

    fun scoreTitleMatch(songName: String, title: String, artist: String = ""): Int {
        if (songName.isBlank() || title.isBlank()) return 0
        val normalized = normalizeTitleForSearch(title, artist)
        return when {
            songName.equals(title, ignoreCase = true) -> 12
            songName.equals(normalized, ignoreCase = true) -> 11
            title.contains(songName, ignoreCase = true) && significantContains(songName, title) -> 8
            normalized.contains(songName, ignoreCase = true) && significantContains(songName, normalized) -> 8
            songName.contains(normalized, ignoreCase = true) && significantContains(normalized, songName) -> 7
            songName.contains(title, ignoreCase = true) && significantContains(title, songName) -> 6
            else -> 0
        }
    }

    /** 防止短字符串（≤1字）匹配到包含它的长标题，导致歌曲张冠李戴 */
    private fun significantContains(needle: String, haystack: String): Boolean {
        if (needle.length < 2) return false
        if (needle.length >= 3) return true
        return needle.length.toDouble() / haystack.length >= 0.2
    }

    /**
     * 艺术家匹配评分（0-24）。
     * [inputArtist] 可能是「ぬゆり/須田景凪」这样的组合，[candidateArtists] 是 API 返回的单个艺术家列表。
     * 拆分后逐一比对，避免因分隔符导致短名永远 contains 不到长组合而漏匹配。
     */
    fun scoreArtistMatch(candidateArtists: List<String>, inputArtist: String): Int {
        if (inputArtist.isBlank() || candidateArtists.isEmpty()) return 0
        val inputParts = splitArtists(inputArtist)
        var best = 0
        for (candidate in candidateArtists) {
            val c = candidate.trim()
            if (c.isBlank()) continue
            when {
                c.equals(inputArtist, ignoreCase = true) ||
                    inputParts.any { it.equals(c, ignoreCase = true) } -> return 24
                inputParts.any { it.length >= 2 && it.contains(c, ignoreCase = true) && c.length >= 2 } ||
                    c.contains(inputArtist, ignoreCase = true) && inputArtist.length >= 2 -> best = maxOf(best, 12)
                inputParts.any { p -> artistWordsOverlap(p, c) } -> best = maxOf(best, 6)
            }
        }
        return best
    }

    private fun artistWordsOverlap(a: String, b: String): Boolean {
        val words = a.lowercase().split(" ").filter { it.length >= 2 }
        return words.isNotEmpty() && words.any { b.lowercase().contains(it) }
    }

    fun scoreAlbumMatch(candidateAlbum: String, inputAlbum: String): Int {
        if (candidateAlbum.isBlank() || inputAlbum.isBlank()) return 0
        val c = candidateAlbum.trim()
        val i = inputAlbum.trim()
        return when {
            c.equals(i, ignoreCase = true) -> 12
            c.contains(i, ignoreCase = true) || i.contains(c, ignoreCase = true) -> when {
                c.length >= 3 && i.length >= 3 && significantContains(i, c) -> 8
                else -> 4
            }
            else -> 0
        }
    }
}
