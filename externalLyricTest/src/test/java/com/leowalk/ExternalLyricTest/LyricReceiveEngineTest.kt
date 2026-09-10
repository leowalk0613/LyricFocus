package com.leowalk.ExternalLyricTest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricReceiveEngineTest {

    private fun incoming(
        title: String = "Song A",
        line: String = "hello",
        seq: Int = 1,
        loading: Boolean = false,
        playing: Boolean = true,
        timeMs: Long = 1000L,
        ctxLineCount: Int? = null,
        method: String = "putlyric",
    ) = LyricReceiveEngine.Incoming(
        method = method,
        title = title,
        artist = "Artist",
        line = line,
        second = "",
        timeMs = timeMs,
        playing = playing,
        loading = loading,
        seq = seq,
        pkg = "com.demo",
        ctxIdx = if (ctxLineCount != null) 0 else null,
        ctxLineCount = ctxLineCount,
    )

    @Test
    fun dropStaleSeq() {
        val state = LyricReceiveEngine.State(acceptedSeq = 5, title = "Song A", line = "old")
        val result = LyricReceiveEngine.apply(state, incoming(seq = 3, line = "stale"))
        assertTrue(result is LyricReceiveEngine.Result.Dropped)
        assertEquals(1, (result as LyricReceiveEngine.Result.Dropped).state.dropCount)
    }

    @Test
    fun loadingClearsLines() {
        val state = LyricReceiveEngine.State(
            acceptedSeq = 2,
            title = "Song A",
            line = "old lyric",
            ctxLineCount = 40,
        )
        val result = LyricReceiveEngine.apply(
            state,
            incoming(title = "Song B", line = "", seq = 3, loading = true, playing = false, timeMs = 0L),
        )
        val applied = result as LyricReceiveEngine.Result.Applied
        assertTrue(applied.cleared)
        assertEquals("", applied.state.line)
        assertEquals(null, applied.state.ctxLineCount)
        assertTrue(applied.state.loading)
        assertEquals("Song B", applied.state.title)
        assertEquals(3, applied.state.acceptedSeq)
    }

    @Test
    fun titleChangeWithoutContentClears() {
        val state = LyricReceiveEngine.State(acceptedSeq = 2, title = "Song A", line = "old")
        val result = LyricReceiveEngine.apply(
            state,
            incoming(title = "Song B", line = "", seq = 3, playing = true, timeMs = 100L),
        )
        val applied = result as LyricReceiveEngine.Result.Applied
        assertTrue(applied.cleared)
        assertEquals("", applied.state.line)
        assertEquals("Song B", applied.state.title)
        assertTrue(applied.state.loading)
    }

    @Test
    fun titleChangeWithLyricAppliesNewLine() {
        val state = LyricReceiveEngine.State(
            acceptedSeq = 2,
            title = "Song A",
            line = "old",
            ctxLineCount = 40,
        )
        val result = LyricReceiveEngine.apply(
            state,
            incoming(title = "Song B", line = "new first line", seq = 3, ctxLineCount = 12),
        )
        val applied = result as LyricReceiveEngine.Result.Applied
        assertFalse(applied.cleared)
        assertEquals("new first line", applied.state.line)
        assertEquals("Song B", applied.state.title)
        assertEquals(12, applied.state.ctxLineCount)
        assertFalse(applied.state.loading)
    }

    @Test
    fun lightUpdateKeepsCachedCtxCount() {
        val state = LyricReceiveEngine.State(
            acceptedSeq = 4,
            title = "Song A",
            line = "line1",
            ctxLineCount = 97,
            ctxIdx = 10,
        )
        val result = LyricReceiveEngine.apply(
            state,
            incoming(title = "Song A", line = "line2", seq = 4, ctxLineCount = null),
        )
        val applied = result as LyricReceiveEngine.Result.Applied
        assertFalse(applied.cleared)
        assertEquals("line2", applied.state.line)
        assertEquals(97, applied.state.ctxLineCount)
    }

    @Test
    fun dropSameSeqContentAfterLoadingClear() {
        val cleared = LyricReceiveEngine.apply(
            LyricReceiveEngine.State(acceptedSeq = 4, title = "A", line = "old"),
            incoming(title = "B", line = "", seq = 5, loading = true, playing = false, timeMs = 0L),
        ) as LyricReceiveEngine.Result.Applied
        assertTrue(cleared.state.loading)

        val late = LyricReceiveEngine.apply(
            cleared.state,
            incoming(title = "A", line = "old lyric revived", seq = 5, loading = false),
        )
        assertTrue(late is LyricReceiveEngine.Result.Dropped)
        assertEquals("", cleared.state.line)
        assertTrue((late as LyricReceiveEngine.Result.Dropped).state.loading)
    }

    @Test
    fun emptyFdClearPayloadClears() {
        val state = LyricReceiveEngine.State(acceptedSeq = 1, title = "A", line = "x", ctxLineCount = 10)
        val result = LyricReceiveEngine.apply(
            state,
            incoming(
                title = "A",
                line = "",
                seq = 2,
                loading = false,
                playing = false,
                timeMs = 0L,
                ctxLineCount = 0,
                method = "putlyricfd",
            ),
        )
        assertTrue((result as LyricReceiveEngine.Result.Applied).cleared)
        assertEquals(null, result.state.ctxLineCount)
    }
}
