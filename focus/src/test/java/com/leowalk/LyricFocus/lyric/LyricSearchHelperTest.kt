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
}
