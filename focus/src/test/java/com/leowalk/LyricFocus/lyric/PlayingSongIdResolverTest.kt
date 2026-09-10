package com.leowalk.LyricFocus.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingSongIdResolverTest {

    private val staleFocus = """
        {"param_v2":{"param_island":{"shareData":{"title":"STARSEEK WAYFARER","shareContent":"https://y.music.163.com/m/song?id=1365036272"}}}}
    """.trimIndent()

    private val freshFocus = """
        {"param_v2":{"param_island":{"shareData":{"title":"ミラー (feat. 小豆沢こはね)","shareContent":"https://y.music.163.com/m/song?id=2030454452"}}}}
    """.trimIndent()

    @Test
    fun ignoreStaleFocusIdWhenTitleMismatch() {
        val resolved = PlayingSongIdResolver.resolve(
            packageName = PlayingSongIdResolver.PKG_NETEASE,
            metadata = null,
            focusMediaJsons = listOf(staleFocus),
            expectedTitle = "ミラー (feat. 小豆沢こはね&白石杏) (未来)",
        )
        assertNull(resolved)
    }

    @Test
    fun acceptFocusIdWhenTitleMatches() {
        val resolved = PlayingSongIdResolver.resolve(
            packageName = PlayingSongIdResolver.PKG_NETEASE,
            metadata = null,
            focusMediaJsons = listOf(staleFocus, freshFocus),
            expectedTitle = "ミラー (feat. 小豆沢こはね&白石杏) (未来)",
        )
        assertEquals(2030454452L, resolved?.songId)
    }

    @Test
    fun titlesLooselyMatchStripsFeatSuffix() {
        assertTrue(
            PlayingSongIdResolver.titlesLooselyMatch(
                "ミラー (feat. 小豆沢こはね)",
                "ミラー (feat. 小豆沢こはね&白石杏) (未来)",
            )
        )
        assertFalse(
            PlayingSongIdResolver.titlesLooselyMatch("STARSEEK WAYFARER", "ミラー (未来)")
        )
    }
}
