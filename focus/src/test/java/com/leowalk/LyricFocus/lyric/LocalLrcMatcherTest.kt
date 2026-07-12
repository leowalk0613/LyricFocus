package com.leowalk.LyricFocus.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalLrcMatcherTest {

    @Test
    fun scoreFileName_phSong_matchesStem() {
        val score = LocalLrcMatcher.scoreFileName(
            fileStem = "p.h. - SEVENTHLINKS",
            title = "p.h.",
            artist = "SEVENTHLINKS/v flower"
        )
        assertTrue(score >= 12)
    }

    @Test
    fun scoreFileName_jayChouSong_matchesStem() {
        val score = LocalLrcMatcher.scoreFileName(
            fileStem = "告白气球 - 周杰伦",
            title = "告白气球",
            artist = "周杰伦"
        )
        assertTrue(score >= 12)
    }

    @Test
    fun scoreFileName_artistFirstWithDoubleDotTitle_matches() {
        val score = LocalLrcMatcher.scoreFileName(
            fileStem = "SEVENTHLINKS v flower - p.h..",
            title = "p.h.",
            artist = "SEVENTHLINKS/v flower"
        )
        assertTrue(score >= 24)
    }

    @Test
    fun findBestLrcFile_picksArtistFirstFilename() {
        val dir = File.createTempFile("lyricfocus_lrc_", "").apply {
            delete()
            mkdirs()
        }
        try {
            File(dir, "針 - SEVENTHLINKS.lrc").writeText("[00:00.00]wrong\n")
            val target = File(dir, "SEVENTHLINKS v flower - p.h..lrc")
            target.writeText("[00:00.00]right\n[00:01.00]line2\n[00:02.00]line3\n")
            val best = LocalLrcMatcher.findBestLrcFile(dir, "p.h.", "SEVENTHLINKS/v flower")
            assertEquals(target.absolutePath, best?.absolutePath)
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun buildCandidateNames_includesTitleArtistVariants() {
        val names = LocalLrcMatcher.buildCandidateNames("p.h.", "SEVENTHLINKS/v flower")
        assertTrue(names.any { it.contains("p.h.") })
        assertTrue(names.any { it.contains("SEVENTHLINKS") })
    }
}
