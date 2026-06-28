package com.leowalk.LyricFocus.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parse_toumeiNaPalette_parsesAllVocalLines() {
        val sample = """
            [00:00.00] 作词 : Aqu3ra
            [00:01.00] 作曲 : Aqu3ra
            [00:18:21]少々妄想気味　他人はくだらないと言うけれど
            [00:22:91]胸に秘めた花は咲いたのです
            [01:04:20]透明なままのパレット　これはこれで綺麗だよね
        """.trimIndent()

        val info = LrcParser.parseWithTranslation(sample, null)
        assertTrue("expected vocal lines, got ${info.lines.size}: ${info.lines.map { it.text }}",
            info.lines.size >= 3)
        assertEquals(18210L, info.lines.first { it.text.contains("少々") }.time)
        assertTrue(info.lines.none { it.text.contains("作詞") || it.text.contains("作词") })
    }
}
