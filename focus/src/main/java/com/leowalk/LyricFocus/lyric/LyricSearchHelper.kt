package com.leowalk.LyricFocus.lyric

object LyricSearchHelper {

    private val FEAT_SUFFIX = Regex(
        """[\s(\[（【]+(?:feat\.|ft\.|with)\b[\s\S]*$""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_PAREN = Regex("""[\(\[（【][^)\]）】]*[\)\]）】]\s*$""")

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
}
