package com.leowalk.LyricFocus

import android.content.Intent
import android.media.MediaMetadata
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.leowalk.LyricFocus.lyric.AiLyricTranslator
import com.leowalk.LyricFocus.lyric.LocalLrcBootstrap
import com.leowalk.LyricFocus.service.LyricService
import com.leowalk.LyricFocus.service.MusicMonitorService
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class LyricSourceActivity : AppCompatActivity() {

    private lateinit var tvDebugNotification: TextView
    private lateinit var tvDebugNotifAlbum: TextView
    private lateinit var tvDebugLyricMatch: TextView
    private lateinit var tvDebugLyricAlbum: TextView
    private lateinit var tvDebugSource: TextView
    private lateinit var tvDebugHasTranslation: TextView
    private lateinit var tvDebugFromAi: TextView
    private lateinit var llLyricSourceItems: LinearLayout

    private var expandedSourceKey: String? = null

    private val pickLocalLrcFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            FocusPreferences.setLocalLrcTreeUri(this, uri.toString())
            FocusPreferences.setLocalLrcBootstrapped(this, false)
            LocalLrcBootstrap.ensureReady(this)
            updateLyricSourceUi()
            broadcastSettingsChanged(includeLyricSource = true)
        } catch (_: Exception) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_lyric_source)
        setupWindowInsets()

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        tvDebugNotification = findViewById(R.id.tv_debug_notification)
        tvDebugNotifAlbum = findViewById(R.id.tv_debug_notif_album)
        tvDebugLyricMatch = findViewById(R.id.tv_debug_lyric_match)
        tvDebugLyricAlbum = findViewById(R.id.tv_debug_lyric_album)
        tvDebugSource = findViewById(R.id.tv_debug_source)
        tvDebugHasTranslation = findViewById(R.id.tv_debug_has_translation)
        tvDebugFromAi = findViewById(R.id.tv_debug_from_ai)
        llLyricSourceItems = findViewById(R.id.ll_lyric_source_items)

        updateLyricSourceUi()
    }

    override fun onResume() {
        super.onResume()
        updateLyricSourceUi()
    }

    private fun setupWindowInsets() {
        val appBar = findViewById<View>(R.id.app_bar)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)
        val content = findViewById<View>(R.id.lyric_source_content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
    }

    private fun updateSongDebugInfo() {
        val metadata = MusicMonitorService.currentMetadata
        val notifTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val notifArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val notifAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        tvDebugNotification.text = if (notifTitle.isNotBlank())
            "当前播放: $notifTitle · $notifArtist"
        else "当前播放: —"
        tvDebugNotifAlbum.text = if (notifAlbum.isNotBlank())
            "播放专辑: $notifAlbum"
        else "播放专辑: —"

        val fetchedTitle = LyricService.currentFetchedLyricTitle
        val fetchedArtist = LyricService.currentFetchedLyricArtist
        tvDebugLyricMatch.text = if (fetchedTitle.isNotBlank())
            "匹配歌词: $fetchedTitle · $fetchedArtist"
        else "匹配歌词: —"
        val fetchedAlbum = LyricService.currentFetchedLyricAlbum
        tvDebugLyricAlbum.text = if (fetchedAlbum.isNotBlank())
            "匹配专辑: $fetchedAlbum"
        else "匹配专辑: —"

        val sourceHit = LyricService.currentLyricSourceHit
        tvDebugSource.text = if (sourceHit.isNotBlank()) "来源: $sourceHit" else "来源: —"
        tvDebugHasTranslation.text = if (sourceHit.isNotBlank())
            "翻译: ${if (LyricService.currentLyricHasTranslation) "有" else "无"}"
        else "翻译: —"
        tvDebugFromAi.text = if (sourceHit.isNotBlank())
            "AI 翻译: ${if (LyricService.currentLyricFromAi) "是" else "否"}"
        else "AI 翻译: —"
    }

    private fun updateLyricSourceUi() {
        updateSongDebugInfo()
        buildLyricSourceItems()
    }

    private fun buildLyricSourceItems() {
        llLyricSourceItems.removeAllViews()
        val ctx = this
        val current = FocusPreferences.getLyricSource(ctx)
        val options = FocusPreferences.lyricSourceOptions()
        val theme = ctx.theme

        for ((index, option) in options.withIndex()) {
            val key = option.first
            val label = option.second
            val isSelected = key == current
            val canExpand = key == FocusPreferences.LYRIC_SOURCE_LOCAL ||
                    key == FocusPreferences.LYRIC_SOURCE_AI ||
                    key == FocusPreferences.LYRIC_SOURCE_SUPERLYRIC ||
                    key == FocusPreferences.LYRIC_SOURCE_LYRICON ||
                    key == FocusPreferences.LYRIC_SOURCE_LYRICINFO
            val isExpanded = expandedSourceKey == key && canExpand

            val header = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(10), 0, dpToPx(10))
                isClickable = true
                isFocusable = true
                val tv = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                background = androidx.core.content.ContextCompat.getDrawable(
                    ctx, tv.resourceId
                )
                setOnClickListener {
                    if (!isSelected) {
                        FocusPreferences.setLyricSource(ctx, key)
                        expandedSourceKey = if (canExpand) key else null
                        updateLyricSourceUi()
                        broadcastSettingsChanged(includeLyricSource = true)
                    } else if (canExpand) {
                        expandedSourceKey = if (isExpanded) null else key
                        updateLyricSourceUi()
                    }
                }
            }

            val dot = View(ctx).apply {
                val size = dpToPx(12)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(12)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    if (isSelected) {
                        setColor(MaterialColors.getColor(
                            ctx, com.google.android.material.R.attr.colorPrimary, "LyricFocus"
                        ))
                    } else {
                        setStroke(
                            dpToPx(2),
                            androidx.core.content.ContextCompat.getColor(ctx, R.color.grey)
                        )
                    }
                }
            }
            header.addView(dot)

            val labelView = TextView(ctx).apply {
                text = label
                setTextAppearance(
                    if (isSelected) com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
                    else com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
                )
                setTextColor(
                    if (isSelected) MaterialColors.getColor(
                        ctx, com.google.android.material.R.attr.colorPrimary, "LyricFocus"
                    )
                    else MaterialColors.getColor(
                        ctx, com.google.android.material.R.attr.colorOnSurface, "LyricFocus"
                    )
                )
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            header.addView(labelView)

            if (canExpand) {
                val arrow = ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_expand_more)
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(24), dpToPx(24)
                    )
                    if (isExpanded) rotation = 180f
                }
                header.addView(arrow)
            }

            llLyricSourceItems.addView(header)

            if (index < options.size - 1) {
                val divider = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
                    ).apply { marginStart = dpToPx(28) }
                    setBackgroundColor(
                        MaterialColors.getColor(
                            ctx,
                            com.google.android.material.R.attr.colorOutlineVariant,
                            "LyricFocus"
                        )
                    )
                }
                llLyricSourceItems.addView(divider)
            }

            if (isExpanded) {
                when (key) {
                    FocusPreferences.LYRIC_SOURCE_LOCAL -> buildLocalLrcInline(ctx)
                    FocusPreferences.LYRIC_SOURCE_AI -> buildAiInline(ctx)
                    FocusPreferences.LYRIC_SOURCE_SUPERLYRIC -> buildSuperLyricInline(ctx)
                    FocusPreferences.LYRIC_SOURCE_LYRICON -> buildLyriconInline(ctx)
                    FocusPreferences.LYRIC_SOURCE_LYRICINFO -> buildLyricInfoInline(ctx)
                }
            }
        }
    }

    private fun buildLocalLrcInline(ctx: android.content.Context) {
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(28), dpToPx(4), 0, dpToPx(12))
        }

        val tvLocation = TextView(ctx).apply {
            text = "当前目录: ${FocusPreferences.getLocalLrcLocationLabel(ctx)}"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(
                MaterialColors.getColor(
                    ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, "LyricFocus"
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(8) }
        }
        body.addView(tvLocation)

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnPick = MaterialButton(android.view.ContextThemeWrapper(ctx,
            com.google.android.material.R.style.Widget_Material3_Button_TonalButton)).apply {
            text = "选择文件夹"
            setOnClickListener { pickLocalLrcFolder.launch(null) }
        }
        btnRow.addView(btnPick)
        btnRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(8), 0)
        })
        val btnReset = MaterialButton(android.view.ContextThemeWrapper(ctx,
            com.google.android.material.R.style.Widget_Material3_Button_OutlinedButton)).apply {
            text = "重置"
            setOnClickListener {
                FocusPreferences.clearLocalLrcTreeUri(ctx)
                FocusPreferences.setLocalLrcBootstrapped(ctx, false)
                LocalLrcBootstrap.ensureReady(ctx)
                updateLyricSourceUi()
                broadcastSettingsChanged(includeLyricSource = true)
            }
        }
        btnRow.addView(btnReset)
        body.addView(btnRow)
        llLyricSourceItems.addView(body)
    }

    private fun buildLyriconInline(ctx: android.content.Context) {
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(28), dpToPx(4), 0, dpToPx(12))
        }
        val textColor = MaterialColors.getColor(
            ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, "LyricFocus"
        )
        val warnings = listOf(
            "· Lyricon 提供完整歌词，一次加载含原文+翻译",
            "· 支持所有功能：多行模式、翻译互换、万象息屏",
            "· 需安装 LyricProvider (LSPosed) 并在作用域勾选音乐App"
        )
        for (line in warnings) {
            body.addView(TextView(ctx).apply {
                text = line
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(2) }
            })
        }
        llLyricSourceItems.addView(body)
    }

    private fun buildLyricInfoInline(ctx: android.content.Context) {
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(28), dpToPx(4), 0, dpToPx(12))
        }
        val textColor = MaterialColors.getColor(
            ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, "LyricFocus"
        )
        val warnings = listOf(
            "· 读取通知栏 MediaMetadata.extras.lyricInfo 字段",
            "· 需安装 LyricInfo Xposed 模块并勾选音乐App",
            "· 零外部依赖，LRC 格式直接解析"
        )
        for (line in warnings) {
            body.addView(TextView(ctx).apply {
                text = line
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(2) }
            })
        }
        llLyricSourceItems.addView(body)
    }

    private fun buildSuperLyricInline(ctx: android.content.Context) {
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(28), dpToPx(4), 0, dpToPx(12))
        }

        val textColor = MaterialColors.getColor(
            ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, "LyricFocus"
        )
        val warnings = listOf(
            "· 实时推送单行歌词，不支持多行模式及翻译",
            "· 仅在播放时逐行显示，暂停/切歌时清空",
            "· 需安装 SuperLyric 支持的播放器"
        )
        for (line in warnings) {
            val tv = TextView(ctx).apply {
                text = line
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(2) }
            }
            body.addView(tv)
        }

        llLyricSourceItems.addView(body)
    }

    private fun buildAiInline(ctx: android.content.Context) {
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(28), dpToPx(4), 0, dpToPx(12))
        }

        val textColor = MaterialColors.getColor(
            ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, "LyricFocus"
        )
        val infos = listOf(
            "Base URL" to FocusPreferences.getAiApiBaseUrl(ctx),
            "Model" to FocusPreferences.getAiApiModel(ctx),
            "目标语言" to FocusPreferences.getAiTargetLanguage(ctx)
        )
        for ((label, value) in infos) {
            val tv = TextView(ctx).apply {
                text = "$label: $value"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(4) }
            }
            body.addView(tv)
        }

        val btnEdit = MaterialButton(android.view.ContextThemeWrapper(ctx,
            com.google.android.material.R.style.Widget_Material3_Button_TonalButton)).apply {
            text = "编辑配置"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
            setOnClickListener { showAiLyricSettingsDialog() }
        }
        body.addView(btnEdit)
        llLyricSourceItems.addView(body)
    }

    private fun showAiLyricSettingsDialog() {
        val ctx = this
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_lyric_settings, null)
        val inputBaseUrl = dialogView.findViewById<TextInputEditText>(R.id.input_ai_base_url)
        val inputApiKey = dialogView.findViewById<TextInputEditText>(R.id.input_ai_api_key)
        val inputModel = dialogView.findViewById<TextInputEditText>(R.id.input_ai_model)
        val inputTargetLanguage = dialogView.findViewById<TextInputEditText>(R.id.input_ai_target_language)
        val switchTranslateAll = dialogView.findViewById<MaterialSwitch>(R.id.switch_ai_translate_all)
        val btnTestConnectivity = dialogView.findViewById<MaterialButton>(R.id.btn_test_ai_connectivity)
        val tvConnectivityResult = dialogView.findViewById<TextView>(R.id.tv_ai_connectivity_result)

        inputBaseUrl.setText(FocusPreferences.getAiApiBaseUrl(ctx))
        inputApiKey.setText(FocusPreferences.getAiApiKey(ctx))
        inputModel.setText(FocusPreferences.getAiApiModel(ctx))
        inputTargetLanguage.setText(FocusPreferences.getAiTargetLanguage(ctx))
        switchTranslateAll.isChecked = FocusPreferences.isAiTranslateAllLyrics(ctx)

        btnTestConnectivity.setOnClickListener {
            btnTestConnectivity.isEnabled = false
            tvConnectivityResult.visibility = View.VISIBLE
            tvConnectivityResult.setTextColor(ctx.getColor(R.color.grey))
            tvConnectivityResult.text = "正在检测…"

            val config = AiLyricTranslator.ApiConfig(
                baseUrl = inputBaseUrl.text?.toString().orEmpty(),
                apiKey = inputApiKey.text?.toString().orEmpty(),
                model = inputModel.text?.toString().orEmpty()
            )
            lifecycleScope.launch {
                val result = AiLyricTranslator(ctx).testConnectivity(config)
                if (isFinishing || isDestroyed) return@launch
                btnTestConnectivity.isEnabled = true
                when (result) {
                    is AiLyricTranslator.ConnectivityResult.Success -> {
                        tvConnectivityResult.setTextColor(ctx.getColor(R.color.green))
                        tvConnectivityResult.text = result.message
                    }
                    is AiLyricTranslator.ConnectivityResult.Failure -> {
                        tvConnectivityResult.setTextColor(ctx.getColor(R.color.red))
                        tvConnectivityResult.text = result.message
                    }
                }
            }
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("AI 翻译配置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                FocusPreferences.setAiApiBaseUrl(ctx, inputBaseUrl.text?.toString().orEmpty())
                FocusPreferences.setAiApiKey(ctx, inputApiKey.text?.toString().orEmpty())
                FocusPreferences.setAiApiModel(ctx, inputModel.text?.toString().orEmpty())
                FocusPreferences.setAiTargetLanguage(ctx, inputTargetLanguage.text?.toString().orEmpty())
                FocusPreferences.setAiTranslateAllLyrics(ctx, switchTranslateAll.isChecked)
                updateLyricSourceUi()
                broadcastSettingsChanged(includeLyricSource = true)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun broadcastSettingsChanged(includeLyricSource: Boolean = false) {
        val ctx = this
        try {
            val base = Intent(FocusPreferences.ACTION_SETTINGS_CHANGED).apply {
                putExtra(FocusPreferences.EXTRA_FOCUS_ENABLED, FocusPreferences.isFocusEnabled(ctx))
                putExtra(FocusPreferences.EXTRA_SHOW_IN_SHADE, FocusPreferences.isShowInShade(ctx))
                putExtra(FocusPreferences.EXTRA_SHOW_ON_ISLAND, FocusPreferences.isShowOnIsland(ctx))
                putExtra(FocusPreferences.EXTRA_SYNC_ADVANCE_MS, FocusPreferences.getSyncAdvanceMs(ctx))
                putExtra(FocusPreferences.EXTRA_APP_WHITELIST_ENABLED, FocusPreferences.isAppWhitelistEnabled(ctx))
                if (includeLyricSource) {
                    putExtra(FocusPreferences.EXTRA_LYRIC_SOURCE, FocusPreferences.getLyricSource(ctx))
                }
            }
            sendBroadcast(Intent(base).setPackage("com.android.systemui"))
            sendBroadcast(Intent(base).setPackage(packageName))
        } catch (_: Exception) {
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
