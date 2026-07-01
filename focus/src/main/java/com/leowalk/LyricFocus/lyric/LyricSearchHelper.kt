package com.leowalk.LyricFocus.lyric

object LyricSearchHelper {

    private val FEAT_SUFFIX = Regex(
        """[\s(\[（【]+(?:feat\.|ft\.|with)\b[\s\S]*$""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_PAREN = Regex("""[\(\[（【][^)\]）】]*[\)\]）】]\s*$""")

    /** 多艺术家分隔符：/ & 、 以及 " and " / " x " */
    private val ARTIST_SEPARATOR = Regex(
        """\s*[/&、，,]\s*|\s+(?:and|x|feat\.?|ft\.?)\s+""",
        RegexOption.IGNORE_CASE
    )

    /** 名字本身含分隔符的艺人/组合特例（小写比较），不做拆分。例如 Leo/need */
    private val ARTIST_NAME_EXCEPTIONS = hashSetOf("leo/need")

    /** 去掉 (feat. …) 等后缀，便于搜索「透明なパレット (feat. …)」这类长标题 */
    fun normalizeTitleForSearch(title: String): String {
        var t = title.trim()
        if (t.isEmpty()) return t
        t = FEAT_SUFFIX.replace(t, "").trim()
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
        val normalized = normalizeTitleForSearch(title)
        val keys = linkedSetOf<String>()
        if (normalized.isNotBlank() && artist.isNotBlank()) keys.add("$normalized $artist")
        if (title.isNotBlank() && artist.isNotBlank() && title != normalized) {
            keys.add("$title $artist")
        }
        if (normalized.isNotBlank()) keys.add(normalized)
        if (title.isNotBlank() && title != normalized) keys.add(title)
        return keys.toList()
    }

    fun scoreTitleMatch(songName: String, title: String): Int {
        if (songName.isBlank() || title.isBlank()) return 0
        val normalized = normalizeTitleForSearch(title)
        return when {
            songName.equals(title, ignoreCase = true) -> 12
            songName.equals(normalized, ignoreCase = true) -> 11
            title.contains(songName, ignoreCase = true) -> 8
            normalized.contains(songName, ignoreCase = true) && songName.length >= 3 -> 8
            songName.contains(normalized, ignoreCase = true) && normalized.length >= 3 -> 7
            songName.contains(title, ignoreCase = true) -> 6
            else -> 0
        }
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
                // 完全相等（含整体或拆分后任一相等）
                c.equals(inputArtist, ignoreCase = true) ||
                    inputParts.any { it.equals(c, ignoreCase = true) } -> return 24
                // 包含匹配（双向）：输入拆分项包含候选，或候选包含输入整体
                inputParts.any { it.length >= 2 && it.contains(c, ignoreCase = true) } ||
                    c.contains(inputArtist, ignoreCase = true) -> best = maxOf(best, 12)
            }
        }
        return best
    }
}
