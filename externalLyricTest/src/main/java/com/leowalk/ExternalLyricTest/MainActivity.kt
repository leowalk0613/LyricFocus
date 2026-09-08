package com.leowalk.ExternalLyricTest

import android.os.Bundle
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.leowalk.ExternalLyricTest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), LyricHub.Listener {
    private lateinit var binding: ActivityMainBinding
    private var pushCount = 0
    private var fdCount = 0
    private var lightCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClear.setOnClickListener {
            LyricHub.clearLog()
            pushCount = 0
            fdCount = 0
            lightCount = 0
            binding.tvLog.text = ""
            binding.tvStatus.text = getString(R.string.status_idle)
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

    override fun onUpdate(snapshot: LyricHub.Snapshot) {
        runOnUiThread {
            pushCount++
            when (snapshot.method) {
                LyricReceiverProvider.METHOD_PUT_LYRIC_FD -> fdCount++
                LyricReceiverProvider.METHOD_PUT_LYRIC -> lightCount++
            }

            binding.tvStatus.text =
                "已收到 $pushCount 次（全量 putlyricfd=$fdCount · 轻量 putlyric=$lightCount）"
            binding.tvMethod.text = "method: ${snapshot.method}"
            binding.tvTitle.text = snapshot.title.ifBlank { "（无歌名）" }
            binding.tvArtist.text = buildString {
                append(snapshot.artist.ifBlank { "（无歌手）" })
                if (snapshot.pkg.isNotBlank()) append(" · ${snapshot.pkg}")
                append(if (snapshot.playing) " · 播放中" else " · 已暂停/清空")
            }

            val cleared = snapshot.line.isEmpty() && snapshot.second.isEmpty() && snapshot.timeMs == 0L
            binding.tvLine.text = when {
                cleared -> "（清空推送）"
                snapshot.line.isNotBlank() -> snapshot.line
                else -> getString(R.string.hint_waiting)
            }
            binding.tvSecond.text = snapshot.second

            binding.tvMeta.text = buildString {
                append("t=${snapshot.timeMs}ms")
                if (snapshot.ctxLineCount != null) {
                    append(" · ctx.idx=${snapshot.ctxIdx} · lines=${snapshot.ctxLineCount}")
                } else {
                    append(" · 无 ctx（轻量）")
                }
            }

            binding.tvLog.text = LyricHub.logText()
            binding.scrollLog.post {
                binding.scrollLog.fullScroll(ScrollView.FOCUS_UP)
            }
        }
    }
}
