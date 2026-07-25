package com.leowalk.LyricFocus.lyric

data class LyricLine(
    val time: Long,
    val text: String,
    val translation: String? = null,
    val polished: String? = null,
    val reading: String? = null
) {
    companion object {
        val EMPTY = LyricLine(0, "")
    }

    fun secondaryText(): String? {
        return listOfNotNull(
            translation?.takeIf { it.isNotBlank() },
            reading?.takeIf { it.isNotBlank() }
        ).joinToString("\n").takeIf { it.isNotBlank() }
    }
}
