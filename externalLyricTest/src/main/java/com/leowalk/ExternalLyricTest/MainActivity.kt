package com.leowalk.ExternalLyricTest

import android.os.Bundle
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.leowalk.ExternalLyricTest.databinding.ActivityMainBinding

/**
 * 外部歌词联调界面：展示接收引擎裁决后的状态（非原始乱序包）。
 */
class MainActivity : AppCompatActivity(), LyricHub.Listener {
    private lateinit var binding: ActivityMainBinding
    private var applyCount = 0
    private var fdCount = 0
    private var lightCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClear.setOnClickListener {
            // 只清日志，不重置 seq，避免迟到旧包重新上屏
            LyricHub.clearLog()
            binding.tvLog.text = ""
            binding.tvStatus.text = getString(R.string.status_idle)
        }

        binding.btnClear.setOnLongClickListener {
            LyricHub.resetSession()
            applyCount = 0
            fdCount = 0
            lightCount = 0
            binding.tvLog.text = ""
            binding.tvStatus.text = getString(R.string.status_idle)
            binding.tvMethod.text = "method: —"
            binding.tvTitle.text = "—"
            binding.tvArtist.text = "—"
            binding.tvLine.text = getString(R.string.hint_waiting)
            binding.tvSecond.text = ""
            binding.tvMeta.text = ""
            true
        }

        LyricHub.latest?.let { onUpdate(it) }
        binding.tvLog.text = LyricHub.logText()
    }

    override fun onStart() {
        super.onStart()
        LyricHub.addListener(this)
    }

    override fun onStop() {
        LyricHub.removeListener(this)
        super.onStop()
    }

    override fun onLogChanged() {
        runOnUiThread {
            binding.tvLog.text = LyricHub.logText()
            binding.tvStatus.text =
                "已应用 $applyCount 次（fd=$fdCount · light=$lightCount · drop=${LyricHub.dropCount} · seq=${LyricHub.acceptedSeq}）"
            binding.scrollLog.post {
                binding.scrollLog.fullScroll(ScrollView.FOCUS_UP)
            }
        }
    }

    override fun onUpdate(snapshot: LyricHub.Snapshot) {
        runOnUiThread {
            applyCount++
            when (snapshot.method) {
                LyricReceiverProvider.METHOD_PUT_LYRIC_FD -> fdCount++
                LyricReceiverProvider.METHOD_PUT_LYRIC -> lightCount++
            }

            binding.tvStatus.text =
                "已应用 $applyCount 次（fd=$fdCount · light=$lightCount · drop=${LyricHub.dropCount} · seq=${LyricHub.acceptedSeq}）"
            binding.tvMethod.text = "method: ${snapshot.method}"
            binding.tvTitle.text = snapshot.title.ifBlank { "（无歌名）" }
            binding.tvArtist.text = buildString {
                append(snapshot.artist.ifBlank { "（无歌手）" })
                if (snapshot.pkg.isNotBlank()) append(" · ${snapshot.pkg}")
                when {
                    snapshot.loading -> append(" · 加载中/切歌清空")
                    snapshot.playing -> append(" · 播放中")
                    else -> append(" · 已暂停/清空")
                }
            }

            binding.tvLine.text = when {
                snapshot.loading -> "（loading 清空 · 等待新词）"
                snapshot.line.isNotBlank() -> snapshot.line
                snapshot.ctxLineCount == null || snapshot.ctxLineCount == 0 -> "（已清空）"
                else -> getString(R.string.hint_waiting)
            }
            binding.tvSecond.text = if (snapshot.loading) "" else snapshot.second

            binding.tvMeta.text = buildString {
                append("seq=${snapshot.seq}")
                append(" · t=${snapshot.timeMs}ms")
                if (snapshot.ctxLineCount != null) {
                    append(" · ctx.idx=${snapshot.ctxIdx} · lines=${snapshot.ctxLineCount}")
                } else {
                    append(" · 无时间轴缓存")
                }
            }

            binding.tvLog.text = LyricHub.logText()
            binding.scrollLog.post {
                binding.scrollLog.fullScroll(ScrollView.FOCUS_UP)
            }
        }
    }
}
