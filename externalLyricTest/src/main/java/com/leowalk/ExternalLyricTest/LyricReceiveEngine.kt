package com.leowalk.ExternalLyricTest

/**
 * 第三方接收端参考实现的核心状态机（无 Android 依赖，便于单测）。
 *
 * 必做规则：
 * 1. `seq > 0 && seq < acceptedSeq` → 丢弃（防旧 putlyricfd 盖住切歌清空）
 * 2. `loading == true` 或空时间轴 clear → 立刻清空歌词行 / 时间轴缓存
 * 3. 异曲且本包无歌词内容 → 清空；异曲但已带新词/时间轴 → 直接上屏并丢弃旧 ctx
 */
object LyricReceiveEngine {

    data class Incoming(
        val method: String,
        val title: String,
        val artist: String,
        val line: String,
        val second: String,
        val timeMs: Long,
        val playing: Boolean,
        val loading: Boolean,
        val seq: Int,
        val pkg: String,
        val ctxIdx: Int?,
        val ctxLineCount: Int?,
    )

    data class State(
        val acceptedSeq: Int = 0,
        val method: String = "",
        val title: String = "",
        val artist: String = "",
        val line: String = "",
        val second: String = "",
        val timeMs: Long = 0L,
        val playing: Boolean = false,
        val loading: Boolean = false,
        val pkg: String = "",
        val ctxIdx: Int? = null,
        val ctxLineCount: Int? = null,
        val dropCount: Int = 0,
        val lastDropReason: String = "",
    )

    sealed class Result {
        data class Dropped(val reason: String, val state: State) : Result()
        data class Applied(val state: State, val cleared: Boolean) : Result()
    }

    fun apply(state: State, incoming: Incoming): Result {
        if (incoming.seq > 0 && incoming.seq < state.acceptedSeq) {
            return Result.Dropped(
                reason = "stale seq=${incoming.seq} < accepted=${state.acceptedSeq}",
                state = state.copy(
                    dropCount = state.dropCount + 1,
                    lastDropReason = "stale seq=${incoming.seq}",
                ),
            )
        }
        // clear/loading 抬高 acceptedSeq 后，同 seq 的非 loading 包不得复活旧词
        if (incoming.seq > 0 &&
            incoming.seq == state.acceptedSeq &&
            state.loading &&
            !incoming.loading
        ) {
            return Result.Dropped(
                reason = "post-clear same seq=${incoming.seq}",
                state = state.copy(
                    dropCount = state.dropCount + 1,
                    lastDropReason = "post-clear same seq",
                ),
            )
        }

        val nextSeq = maxOf(state.acceptedSeq, incoming.seq)
        val titleChanged = titlesChanged(state.title, incoming.title)
        // 异曲但本包已带新词/时间轴：直接上屏，勿清空（常见于首包 putlyricfd）
        val shouldClear = incoming.loading || isClearPayload(incoming) ||
            (titleChanged && incoming.line.isBlank() &&
                (incoming.ctxLineCount == null || incoming.ctxLineCount == 0))

        if (shouldClear) {
            return Result.Applied(
                state = state.copy(
                    acceptedSeq = nextSeq,
                    method = incoming.method,
                    title = incoming.title.ifBlank { state.title },
                    artist = incoming.artist.ifBlank { state.artist },
                    line = "",
                    second = "",
                    timeMs = 0L,
                    playing = incoming.playing,
                    loading = incoming.loading || titleChanged,
                    pkg = incoming.pkg.ifBlank { state.pkg },
                    ctxIdx = null,
                    ctxLineCount = null,
                    lastDropReason = "",
                ),
                cleared = true,
            )
        }

        // 异曲时丢弃旧歌时间轴；轻量同曲保留缓存行数
        val keepCtxCount = when {
            incoming.ctxLineCount != null && incoming.ctxLineCount > 0 -> incoming.ctxLineCount
            titleChanged -> null
            else -> state.ctxLineCount
        }
        val keepCtxIdx = when {
            incoming.ctxLineCount != null && incoming.ctxLineCount > 0 -> incoming.ctxIdx
            titleChanged -> incoming.ctxIdx
            else -> state.ctxIdx
        }

        return Result.Applied(
            state = state.copy(
                acceptedSeq = nextSeq,
                method = incoming.method,
                title = incoming.title.ifBlank { state.title },
                artist = incoming.artist.ifBlank { state.artist },
                line = incoming.line,
                second = incoming.second,
                timeMs = incoming.timeMs,
                playing = incoming.playing,
                loading = false,
                pkg = incoming.pkg.ifBlank { state.pkg },
                ctxIdx = keepCtxIdx,
                ctxLineCount = keepCtxCount,
                lastDropReason = "",
            ),
            cleared = false,
        )
    }

    fun titlesChanged(previous: String, incoming: String): Boolean {
        val a = previous.trim()
        val b = incoming.trim()
        if (a.isEmpty() || b.isEmpty()) return false
        return !titlesMatch(a, b)
    }

    fun titlesMatch(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val left = a.replace(" ", "")
        val right = b.replace(" ", "")
        return left.equals(right, ignoreCase = true)
    }

    fun isClearPayload(incoming: Incoming): Boolean {
        if (incoming.line.isNotBlank() || incoming.second.isNotBlank()) return false
        // 全量空时间轴：切歌 clear 的 putlyricfd（ctx.lines = []）
        if (incoming.ctxLineCount != null && incoming.ctxLineCount == 0) return true
        // 旧协议轻量清空：无 ctx、未播放、t=0
        if (incoming.ctxLineCount == null && !incoming.playing && incoming.timeMs == 0L) return true
        return false
    }
}
