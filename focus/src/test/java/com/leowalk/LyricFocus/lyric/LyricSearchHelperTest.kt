package com.leowalk.LyricFocus.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSearchHelperTest {

    @Test
    fun normalizeTitleForSearch_stripsFeatSuffix() {
        val title = "透明なパレット (feat. 星乃一歌&天馬咲希&望月穂波&日野森志歩&鏡音リン)"
        assertEquals("透明なパレット", LyricSearchHelper.normalizeTitleForSearch(title))
    }

    @Test
    fun normalizeTitleForSearch_stripsFeatSuffix_kanjikanji() {
        // 用户反馈: 心眼 (feat. 鈴田景凪) 应归一化为 心眼
        val title = "心眼 (feat. 鈴田景凪)"
        assertEquals("心眼", LyricSearchHelper.normalizeTitleForSearch(title))
    }

    @Test
    fun buildSearchKeywords_prefersNormalizedTitleFirst() {
        val title = "透明なパレット (feat. 星乃一歌&天馬咲希)"
        val keys = LyricSearchHelper.buildSearchKeywords(title, "Leo/need")
        assertEquals("透明なパレット Leo/need", keys.first())
        assertTrue(keys.contains("透明なパレット"))
    }

    @Test
    fun scoreTitleMatch_matchesShortCatalogName() {
        val title = "透明なパレット (feat. 星乃一歌&天馬咲希)"
        assertTrue(LyricSearchHelper.scoreTitleMatch("透明なパレット", title) >= 7)
    }

    @Test
    fun scoreArtistMatch_slashSeparatedInput_matchesSingleCandidate() {
        // 用户反馈: 心眼 (feat. 鈴田景凪) by ぬゆり/須田景凪
        // 播放器给出的 artist 是「ぬゆり/須田景凪」组合，网易云 API 返回单个艺术家「ぬゆり」
        val score = LyricSearchHelper.scoreArtistMatch(
            candidateArtists = listOf("ぬゆり"),
            inputArtist = "ぬゆり/須田景凪"
        )
        assertEquals(24, score)
    }

    @Test
    fun scoreArtistMatch_splitPartsMatchExactly() {
        // API 返回 [ぬゆり, 須田景凪]，输入「ぬゆり/須田景凪」拆分后任一相等 → 满分
        val score = LyricSearchHelper.scoreArtistMatch(
            candidateArtists = listOf("ぬゆり", "須田景凪"),
            inputArtist = "ぬゆり/須田景凪"
        )
        assertEquals(24, score)
    }

    @Test
    fun scoreArtistMatch_unrelatedArtist_returnsZero() {
        val score = LyricSearchHelper.scoreArtistMatch(
            candidateArtists = listOf("YOASOBI"),
            inputArtist = "ぬゆり/須田景凪"
        )
        assertEquals(0, score)
    }

    @Test
    fun scoreArtistMatch_blankInput_returnsZero() {
        val score = LyricSearchHelper.scoreArtistMatch(
            candidateArtists = listOf("ぬゆり"),
            inputArtist = ""
        )
        assertEquals(0, score)
    }

    @Test
    fun splitArtists_leoNeedNotSplit() {
        // Leo/need 是组合名，/ 属于名字本身，不应拆成 Leo 和 need
        assertEquals(listOf("Leo/need"), LyricSearchHelper.splitArtists("Leo/need"))
        assertEquals(listOf("LEO/NEED"), LyricSearchHelper.splitArtists("LEO/NEED"))
    }

    @Test
    fun scoreArtistMatch_leoNeedKeptWhole() {
        val score = LyricSearchHelper.scoreArtistMatch(
            candidateArtists = listOf("Leo/need"),
            inputArtist = "Leo/need"
        )
        assertEquals(24, score)
    }
}
