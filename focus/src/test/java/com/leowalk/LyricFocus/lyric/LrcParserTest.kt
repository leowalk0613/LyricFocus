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
        assertTrue(
            "expected vocal lines, got ${info.lines.size}: ${info.lines.map { it.text }}",
            info.lines.size >= 3
        )
        assertEquals(18210L, info.lines.first { it.text.contains("少々") }.time)
        assertTrue(info.lines.none { it.text.contains("作詞") || it.text.contains("作词") })
    }

    @Test
    fun parse_trilingualSameTimestamp_mergesIntoOneLine() {
        val sample = """
            [00:19.84]心臓に傷を負った
            [00:19.84]心脏受了伤
            [00:19.84]shinzou ni kizu wo otta
        """.trimIndent()

        val info = LrcParser.parse(sample)
        assertEquals(1, info.lines.size)
        val line = info.lines.first()
        assertEquals("心臓に傷を負った", line.text)
        assertEquals("心脏受了伤", line.translation)
        assertEquals("shinzou ni kizu wo otta", line.reading)
        assertEquals("心脏受了伤\nshinzou ni kizu wo otta", line.secondaryText())
    }

    @Test
    fun parse_trilingualContinuationLinesWithoutTimestamp_mergesIntoOneLine() {
        val sample = """
            [00:19.84]心臓に傷を負った
            心脏受了伤
            shinzou ni kizu wo otta
        """.trimIndent()

        val info = LrcParser.parse(sample)
        assertEquals(1, info.lines.size)
        assertEquals("心臓に傷を負った", info.lines.first().text)
        assertEquals("心脏受了伤", info.lines.first().translation)
        assertEquals("shinzou ni kizu wo otta", info.lines.first().reading)
    }

    @Test
    fun parse_inlineTrilingualPipeFormat_mergesIntoOneLine() {
        val sample = """
            [00:19.84]心臓に傷を負った | 心脏受了伤 | shinzou ni kizu wo otta
        """.trimIndent()

        val info = LrcParser.parse(sample)
        assertEquals(1, info.lines.size)
        assertEquals("心臓に傷を負った", info.lines.first().text)
        assertEquals("心脏受了伤", info.lines.first().translation)
        assertEquals("shinzou ni kizu wo otta", info.lines.first().reading)
    }

    @Test
    fun parse_trilingualFile_producesMergedVocalLineCount() {
        val sample = """
            [00:19.84]原文一
            [00:19.84]翻译一
            [00:19.84]romaji ichi
            [00:21.91]原文二
            [00:21.91]翻译二
            [00:21.91]romaji ni
            [00:23.67]原文三
            [00:23.67]翻译三
            [00:23.67]romaji san
        """.trimIndent()

        val info = LrcParser.parse(sample)
        assertEquals(3, info.lines.size)
        assertTrue(info.lines.all { !it.translation.isNullOrBlank() && !it.reading.isNullOrBlank() })
    }
}
