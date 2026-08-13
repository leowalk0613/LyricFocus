package com.leowalk.LyricFocus.ui

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.leowalk.LyricFocus.FocusPreferences
import com.leowalk.LyricFocus.R
import com.leowalk.LyricFocus.service.LyricService
import com.leowalk.LyricFocus.util.AlbumColorExtractor
import com.leowalk.LyricFocus.util.AodColorPresets
import kotlin.math.roundToInt

class StyleSettingsFragment : Fragment(R.layout.activity_style_settings) {

    private lateinit var switchSwapLyricTranslation: MaterialSwitch
    private lateinit var switchSingleLineOnly: MaterialSwitch
    private lateinit var singleLineOnlyRow: View
    private lateinit var singleLineOnlyTitle: TextView
    private lateinit var singleLineOnlyHint: TextView
    private lateinit var linesCard: View
    private lateinit var linesCardTitle: TextView
    private lateinit var linesCardHint: TextView
    private lateinit var switchMultiLineLyrics: MaterialSwitch
    private lateinit var multiLineCountRow: View
    private lateinit var sliderMultiLineCount: Slider
    private lateinit var tvMultiLineCountLabel: TextView
    private lateinit var multiLineTranslationRow: View
    private lateinit var switchMultiLineShowTranslation: MaterialSwitch
    private lateinit var aodMultiLineOnlyRow: View
    private lateinit var switchAodMultiLineOnly: MaterialSwitch
    private lateinit var multiLineTextSizeRow: View
    private lateinit var sliderMultiLineTextSize: Slider
    private lateinit var tvMultiLineTextSizeLabel: TextView
    private lateinit var lockScreenSection: LinearLayout
    private lateinit var lockScreenSectionHint: TextView
    private lateinit var lockScreenStyleCard: MaterialCardView
    private lateinit var customAodSection: LinearLayout
    private lateinit var customAodSectionHint: TextView
    private lateinit var customAodStyleCard: MaterialCardView

    private lateinit var sliderTextSize: Slider
    private lateinit var tvTextSizeLabel: TextView
    private lateinit var textColorSection: View
    private lateinit var textColorTitle: TextView
    private lateinit var textColorHint: TextView
    private lateinit var textColorGroup: MaterialButtonToggleGroup
    private lateinit var textColorWhite: MaterialButton
    private lateinit var textColorBlack: MaterialButton
    private lateinit var textColorPreset: MaterialButton
    private lateinit var lockScreenColorPalette: GridLayout
    private lateinit var btnLockScreenPickColor: MaterialButton
    private lateinit var backgroundSection: View
    private lateinit var backgroundTitle: TextView
    private lateinit var backgroundHint: TextView
    private lateinit var backgroundGroup: MaterialButtonToggleGroup
    private lateinit var backgroundGroupRow2: MaterialButtonToggleGroup
    private lateinit var backgroundDefault: MaterialButton
    private lateinit var backgroundBlack: MaterialButton
    private lateinit var backgroundWhite: MaterialButton
    private lateinit var backgroundCustom: MaterialButton
    private lateinit var btnBackgroundPickColor: MaterialButton
    private lateinit var colorExtractionSection: View
    private lateinit var colorExtractionTitle: TextView
    private lateinit var colorExtractionHint: TextView
    private lateinit var colorExtractionSwitch: MaterialSwitch
    private lateinit var monetDynamicSwitch: MaterialSwitch
    private lateinit var monetBgOnlyRow: View
    private lateinit var monetBgOnlySwitch: MaterialSwitch
    private lateinit var colorModeCard: View
    private lateinit var colorModeTitle: TextView
    private lateinit var colorModeHint: TextView
    private lateinit var colorModeSwitch: MaterialSwitch
    private lateinit var extractedOpacitySection: View
    private lateinit var sliderExtractedOpacity: Slider
    private lateinit var tvExtractedOpacityLabel: TextView

    private lateinit var sliderCustomAodTextSize: Slider
    private lateinit var tvCustomAodTextSizeLabel: TextView
    private lateinit var sliderCustomAodWidth: Slider
    private lateinit var tvCustomAodWidthLabel: TextView
    private lateinit var customAodColorModeGroup: MaterialButtonToggleGroup
    private lateinit var customAodSongInfoGroup: MaterialButtonToggleGroup
    private lateinit var customAodSongInfoGroupRow2: MaterialButtonToggleGroup
    private lateinit var customAodColorPaletteSection: LinearLayout
    private lateinit var customAodColorPalette: GridLayout
    private lateinit var btnCustomAodPickColor: MaterialButton

    private lateinit var lyricLinesGroup: MaterialButtonToggleGroup
    private lateinit var translationLinesGroup: MaterialButtonToggleGroup
    private lateinit var gravityGroup: MaterialButtonToggleGroup
    private lateinit var customAodLyricLinesGroup: MaterialButtonToggleGroup
    private lateinit var customAodTranslationLinesGroup: MaterialButtonToggleGroup
    private lateinit var customAodGravityGroup: MaterialButtonToggleGroup
    private lateinit var customAodTitleIconSwitch: MaterialSwitch
    private lateinit var customAodTitleIconPresetSection: LinearLayout
    private lateinit var sliderCustomAodTitleIconSize: Slider
    private lateinit var tvCustomAodTitleIconSizeLabel: TextView

    private val lockScreenControls = mutableListOf<View>()
    private val customAodControls = mutableListOf<View>()
    private val colorPresetViews = mutableListOf<View>()
    private val lockScreenPresetViews = mutableListOf<View>()
    private var selectedPresetColor = android.graphics.Color.WHITE
    private var selectedLockScreenPresetColor = android.graphics.Color.WHITE

    private var isBindingUi = false
    private var isTextSizeSliderUpdating = false
    private var isMultiLineTextSizeUpdating = false
    private var isCustomAodTextSizeUpdating = false
    private var isCustomAodWidthUpdating = false
    private var aodchangeDimmed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.app_bar).visibility = View.GONE
        setupContentInsets(view)
        bindViews(view)
        bindPreviewViews(view)
        setupColorPresetGrid()
        bindUiFromPreferences()
        setupListeners()
        updateLockScreenSectionState()
        updateCustomAodSectionState()
        updateCustomAodColorUi()
        updateMultiLineDependentUi()
        refreshPreview()
        LyricService.onPreviewStateChanged = { view.post { refreshPreview() } }
        applyAodchangeDim(view)
    }

    /** aodchange 外部渲染开启：本页全部设置（依赖 hook）变灰禁用 */
    private fun applyAodchangeDim(view: View) {
        try {
            val dimmed = FocusPreferences.isAodchangeEnabled(requireContext())
            if (dimmed == aodchangeDimmed) return
            aodchangeDimmed = dimmed
            SettingsDim.apply(view, dimmed)
            if (dimmed) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "外部渲染模式下样式设置不可用，关闭“aodchange 外部渲染”后恢复",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                // 恢复后重新应用控件依赖状态（多行开关联动等）
                updateLockScreenSectionState()
                updateCustomAodSectionState()
                updateMultiLineDependentUi()
            }
        } catch (_: Throwable) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        previewHeightAnimator?.cancel()
        previewHeightAnimator = null
        LyricService.onPreviewStateChanged = null
    }

    override fun onResume() {
        super.onResume()
        updateLockScreenSectionState()
        updateCustomAodSectionState()
        updateMultiLineDependentUi()
        refreshPreview()
        applyAodchangeDim(requireView())
    }

    private fun bindViews(view: View) {
        switchSwapLyricTranslation = view.findViewById(R.id.switch_swap_lyric_translation)
        switchSingleLineOnly = view.findViewById(R.id.switch_single_line_only)
        singleLineOnlyRow = view.findViewById(R.id.single_line_only_row)
        singleLineOnlyTitle = view.findViewById(R.id.single_line_only_title)
        singleLineOnlyHint = view.findViewById(R.id.single_line_only_hint)
        linesCard = view.findViewById(R.id.lines_card)
        linesCardTitle = view.findViewById(R.id.lines_card_title)
        linesCardHint = view.findViewById(R.id.lines_card_hint)
        switchMultiLineLyrics = view.findViewById(R.id.switch_multi_line_lyrics)
        multiLineCountRow = view.findViewById(R.id.multi_line_count_row)
        sliderMultiLineCount = view.findViewById(R.id.slider_multi_line_count)
        tvMultiLineCountLabel = view.findViewById(R.id.multi_line_count_label)
        multiLineTranslationRow = view.findViewById(R.id.multi_line_translation_row)
        switchMultiLineShowTranslation = view.findViewById(R.id.switch_multi_line_show_translation)
        aodMultiLineOnlyRow = view.findViewById(R.id.aod_multi_line_only_row)
        switchAodMultiLineOnly = view.findViewById(R.id.switch_aod_multi_line_only)
        multiLineTextSizeRow = view.findViewById(R.id.multi_line_text_size_row)
        sliderMultiLineTextSize = view.findViewById(R.id.slider_multi_line_text_size)
        tvMultiLineTextSizeLabel = view.findViewById(R.id.multi_line_text_size_label)
        lockScreenSection = view.findViewById(R.id.lock_screen_style_section)
        lockScreenSectionHint = view.findViewById(R.id.lock_screen_section_hint)
        lockScreenStyleCard = view.findViewById(R.id.lock_screen_style_card)
        customAodSection = view.findViewById(R.id.custom_aod_style_section)
        customAodSectionHint = view.findViewById(R.id.custom_aod_section_hint)
        customAodStyleCard = view.findViewById(R.id.custom_aod_style_card)

        sliderTextSize = view.findViewById(R.id.slider_text_size)
        tvTextSizeLabel = view.findViewById(R.id.text_size_label)
        textColorSection = view.findViewById(R.id.text_color_card)
        textColorTitle = view.findViewById(R.id.text_color_title)
        textColorHint = view.findViewById(R.id.text_color_hint)
        textColorGroup = view.findViewById(R.id.text_color_group)
        textColorWhite = view.findViewById(R.id.text_color_white)
        textColorBlack = view.findViewById(R.id.text_color_black)
        textColorPreset = view.findViewById(R.id.text_color_preset)
        lockScreenColorPalette = view.findViewById(R.id.lock_screen_color_palette)
        btnLockScreenPickColor = view.findViewById(R.id.btn_lock_screen_pick_color)
        backgroundSection = view.findViewById(R.id.background_card)
        backgroundTitle = view.findViewById(R.id.background_title)
        backgroundHint = view.findViewById(R.id.background_hint)
        backgroundGroup = view.findViewById(R.id.background_group)
        backgroundDefault = view.findViewById(R.id.background_default)
        backgroundBlack = view.findViewById(R.id.background_black)
        backgroundWhite = view.findViewById(R.id.background_white)
        backgroundCustom = view.findViewById(R.id.background_custom)
        btnBackgroundPickColor = view.findViewById(R.id.btn_background_pick_color)
        backgroundGroupRow2 = view.findViewById(R.id.background_group_row2)
        colorExtractionSection = view.findViewById(R.id.color_extraction_card)
        colorExtractionTitle = view.findViewById(R.id.color_extraction_title)
        colorExtractionHint = view.findViewById(R.id.color_extraction_hint)
        colorExtractionSwitch = view.findViewById(R.id.color_extraction_switch)
        monetDynamicSwitch = view.findViewById(R.id.monet_dynamic_switch)
        monetBgOnlyRow = view.findViewById(R.id.monet_bg_only_row)
        monetBgOnlySwitch = view.findViewById(R.id.monet_bg_only_switch)
        colorModeCard = view.findViewById(R.id.color_mode_card)
        colorModeTitle = view.findViewById(R.id.color_mode_title)
        colorModeHint = view.findViewById(R.id.color_mode_hint)
        colorModeSwitch = view.findViewById(R.id.color_mode_switch)
        extractedOpacitySection = view.findViewById(R.id.extracted_opacity_section)
        sliderExtractedOpacity = view.findViewById(R.id.slider_extracted_opacity)
        tvExtractedOpacityLabel = view.findViewById(R.id.extracted_opacity_label)

        sliderCustomAodTextSize = view.findViewById(R.id.slider_custom_aod_text_size)
        tvCustomAodTextSizeLabel = view.findViewById(R.id.custom_aod_text_size_label)
        sliderCustomAodWidth = view.findViewById(R.id.slider_custom_aod_width)
        tvCustomAodWidthLabel = view.findViewById(R.id.custom_aod_width_label)
        customAodColorModeGroup = view.findViewById(R.id.custom_aod_color_mode_group)
        customAodSongInfoGroup = view.findViewById(R.id.custom_aod_song_info_group)
        customAodSongInfoGroupRow2 = view.findViewById(R.id.custom_aod_song_info_group_row2)
        customAodColorPaletteSection = view.findViewById(R.id.custom_aod_color_palette_section)
        customAodColorPalette = view.findViewById(R.id.custom_aod_color_palette)
        btnCustomAodPickColor = view.findViewById(R.id.btn_custom_aod_pick_color)

        lyricLinesGroup = view.findViewById(R.id.lyric_lines_group)
        translationLinesGroup = view.findViewById(R.id.translation_lines_group)
        gravityGroup = view.findViewById(R.id.gravity_group)
        customAodLyricLinesGroup = view.findViewById(R.id.custom_aod_lyric_lines_group)
        customAodTranslationLinesGroup = view.findViewById(R.id.custom_aod_translation_lines_group)
        customAodGravityGroup = view.findViewById(R.id.custom_aod_gravity_group)
        customAodTitleIconSwitch = view.findViewById(R.id.custom_aod_title_icon_switch)
        customAodTitleIconPresetSection = view.findViewById(R.id.custom_aod_title_icon_preset_section)
        sliderCustomAodTitleIconSize = view.findViewById(R.id.slider_custom_aod_title_icon_size)
        tvCustomAodTitleIconSizeLabel = view.findViewById(R.id.custom_aod_title_icon_size_label)

        lockScreenControls += listOf(
            sliderTextSize,
            textColorGroup,
            textColorWhite,
            textColorBlack,
            lyricLinesGroup,
            translationLinesGroup,
            gravityGroup,
            switchMultiLineLyrics,
            sliderMultiLineCount,
            switchMultiLineShowTranslation,
            sliderMultiLineTextSize
        )
        customAodControls += listOf(
            sliderCustomAodTextSize,
            sliderCustomAodWidth,
            customAodSongInfoGroup,
            customAodSongInfoGroupRow2,
            customAodLyricLinesGroup,
            customAodTranslationLinesGroup,
            customAodGravityGroup,
            customAodTitleIconSwitch,
            customAodTitleIconPresetSection,
            sliderCustomAodTitleIconSize,
            customAodColorModeGroup,
            btnCustomAodPickColor
        )
    }

    private fun setupContentInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.style_content)) { content, insets ->
            insets
        }
    }

    private fun setupColorPresetGrid() {
        val size = resources.displayMetrics.density.times(36).toInt()
        val margin = resources.displayMetrics.density.times(4).toInt()
        colorPresetViews.clear()
        customAodColorPalette.removeAllViews()

        AodColorPresets.presets.forEach { preset ->
            val chip = View(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(margin, margin, margin, margin)
                }
                background = chipDrawable(preset.color, false)
                contentDescription = preset.name
                setOnClickListener {
                    selectPresetColor(preset.color)
                    FocusPreferences.setCustomAodColorMode(requireContext(), FocusPreferences.CUSTOM_AOD_COLOR_PRESET)
                    FocusPreferences.setCustomAodPresetColor(requireContext(), preset.color)
                    notifyStyleChanged()
                }
            }
            colorPresetViews += chip
            customAodColorPalette.addView(chip)
        }

        // 锁屏文字推荐色
        lockScreenPresetViews.clear()
        lockScreenColorPalette.removeAllViews()
        AodColorPresets.presets.forEach { preset ->
            val chip = View(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(margin, margin, margin, margin)
                }
                background = chipDrawable(preset.color, false)
                contentDescription = preset.name
                setOnClickListener {
                    selectLockScreenPresetColor(preset.color)
                    FocusPreferences.setLyricTextColor(requireContext(), FocusPreferences.TEXT_COLOR_PRESET)
                    FocusPreferences.setLyricTextPresetColor(requireContext(), preset.color)
                    notifyStyleChanged()
                }
            }
            lockScreenPresetViews += chip
            lockScreenColorPalette.addView(chip)
        }

        // 锁屏自定义颜色按钮
        btnLockScreenPickColor.setOnClickListener {
            showLockScreenColorPicker()
        }
    }

    private fun chipDrawable(color: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(
                resources.displayMetrics.density.times(if (selected) 3 else 1).toInt(),
                if (selected) Color.WHITE else Color.parseColor("#55FFFFFF")
            )
        }
    }

    private fun selectPresetColor(color: Int) {
        selectedPresetColor = color
        var matched = false
        AodColorPresets.presets.forEachIndexed { index, preset ->
            val view = colorPresetViews.getOrNull(index) ?: return@forEachIndexed
            val selected = preset.color == color
            if (selected) matched = true
            view.background = chipDrawable(preset.color, selected)
        }
        if (!matched) {
            colorPresetViews.forEach { view ->
                view.background = chipDrawable(Color.TRANSPARENT, false)
            }
        }
    }

    private fun selectLockScreenPresetColor(color: Int) {
        selectedLockScreenPresetColor = color
        var matched = false
        AodColorPresets.presets.forEachIndexed { index, preset ->
            val view = lockScreenPresetViews.getOrNull(index) ?: return@forEachIndexed
            val selected = preset.color == color
            if (selected) matched = true
            view.background = chipDrawable(preset.color, selected)
        }
        if (!matched) {
            lockScreenPresetViews.forEach { view ->
                view.background = chipDrawable(Color.TRANSPARENT, false)
            }
        }
    }

    private fun showLockScreenColorPicker() {
        val savedColor = FocusPreferences.getLyricTextPresetColor(requireContext())
        showColorPickerDialog("自定义文字颜色", savedColor,
            onConfirm = { color ->
                FocusPreferences.setLyricTextPresetColor(requireContext(), color)
                FocusPreferences.setLyricTextColor(requireContext(), FocusPreferences.TEXT_COLOR_PRESET)
                selectedLockScreenPresetColor = color
                bindUiFromPreferences()
                notifyStyleChanged()
            },
            onReset = {
                FocusPreferences.setLyricTextColor(requireContext(), FocusPreferences.TEXT_COLOR_WHITE)
                bindUiFromPreferences()
                notifyStyleChanged()
            }
        )
    }

    private fun showBackgroundColorPicker() {
        val currentColor = FocusPreferences.getFocusBgCustomColor(requireContext())
        showColorPickerDialog("自定义背景颜色", currentColor, showAlpha = false,
            onConfirm = { color ->
                FocusPreferences.setFocusBgCustomColor(requireContext(), color)
                updateDynamicColorUi()
                notifyStyleChanged()
            }
        )
    }

    private fun showColorPickerDialog(
        title: String,
        initialColor: Int,
        showAlpha: Boolean = true,
        onConfirm: (Int) -> Unit,
        onReset: (() -> Unit)? = null
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_color_picker, null)
        val preview = dialogView.findViewById<View>(R.id.color_preview)
        val sliderR = dialogView.findViewById<Slider>(R.id.slider_color_r)
        val sliderG = dialogView.findViewById<Slider>(R.id.slider_color_g)
        val sliderB = dialogView.findViewById<Slider>(R.id.slider_color_b)
        val sliderA = dialogView.findViewById<Slider>(R.id.slider_color_a)
        val labelR = dialogView.findViewById<TextView>(R.id.label_color_r)
        val labelG = dialogView.findViewById<TextView>(R.id.label_color_g)
        val labelB = dialogView.findViewById<TextView>(R.id.label_color_b)
        val labelA = dialogView.findViewById<TextView>(R.id.label_color_a)
        val hexInput = dialogView.findViewById<android.widget.EditText>(R.id.hex_color_input)

        if (!showAlpha) {
            sliderA.value = 255f; sliderA.visibility = View.GONE
            labelA.visibility = View.GONE
        }

        var updatingHex = false
        fun colorArgb() = Color.argb(if (showAlpha) sliderA.value.toInt() else 255, sliderR.value.toInt(), sliderG.value.toInt(), sliderB.value.toInt())
        fun toHex(c: Int) = String.format("#%08X", c)
        fun refreshPreview() {
            val color = colorArgb()
            preview.setBackgroundColor(color)
            labelR.text = "R ${sliderR.value.toInt()}"
            labelG.text = "G ${sliderG.value.toInt()}"
            labelB.text = "B ${sliderB.value.toInt()}"
            labelA.text = "A ${sliderA.value.toInt()}"
            if (!updatingHex) { updatingHex = true; hexInput.setText(toHex(color)); updatingHex = false }
        }

        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (updatingHex) return
                val hex = s?.toString()?.trim()?.removePrefix("#") ?: return
                if (hex.length != 8) return
                try {
                    val color = hex.toLong(16).toInt()
                    updatingHex = true
                    sliderR.value = Color.red(color).toFloat()
                    sliderG.value = Color.green(color).toFloat()
                    sliderB.value = Color.blue(color).toFloat()
                    sliderA.value = Color.alpha(color).toFloat()
                    updatingHex = false
                    refreshPreview()
                } catch (_: Throwable) {}
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        sliderR.value = Color.red(initialColor).toFloat()
        sliderG.value = Color.green(initialColor).toFloat()
        sliderB.value = Color.blue(initialColor).toFloat()
        sliderA.value = Color.alpha(initialColor).toFloat()
        hexInput.setText(toHex(initialColor))
        refreshPreview()

        val listener = Slider.OnChangeListener { _, _, _ -> refreshPreview() }
        sliderR.addOnChangeListener(listener)
        sliderG.addOnChangeListener(listener)
        sliderB.addOnChangeListener(listener)
        sliderA.addOnChangeListener(listener)

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ -> onConfirm(colorArgb()) }
            .setNegativeButton("取消", null)

        if (onReset != null) {
            builder.setNeutralButton("恢复默认") { _, _ -> onReset() }
        }

        builder.show()
    }

    private fun bindUiFromPreferences() {
        isBindingUi = true

        switchSwapLyricTranslation.isChecked = FocusPreferences.isSwapLyricTranslation(requireContext())
        switchSingleLineOnly.isChecked = FocusPreferences.isSingleLineOnly(requireContext())
        switchMultiLineLyrics.isChecked = FocusPreferences.isMultiLineLyrics(requireContext())
        switchMultiLineShowTranslation.isChecked =
            FocusPreferences.isMultiLineShowTranslation(requireContext())
        val lineCount = FocusPreferences.getMultiLineLineCount(requireContext()).toFloat()
        sliderMultiLineCount.value = lineCount
        tvMultiLineCountLabel.text = "${lineCount.toInt()} 行"
        switchAodMultiLineOnly.isChecked = FocusPreferences.isAodMultiLineOnly(requireContext())
        bindMultiLineTextSizeSlider(FocusPreferences.getMultiLineTextSize(requireContext()))

        bindTextSizeSlider(FocusPreferences.getLyricTextSize(requireContext()))
        bindExtractedOpacitySlider(FocusPreferences.getExtractedColorOpacity(requireContext()))

        val textColorId = if (FocusPreferences.getLyricTextColor(requireContext()) == FocusPreferences.TEXT_COLOR_BLACK) {
            R.id.text_color_black
        } else if (FocusPreferences.getLyricTextColor(requireContext()) == FocusPreferences.TEXT_COLOR_PRESET) {
            R.id.text_color_preset
        } else {
            R.id.text_color_white
        }
        textColorGroup.check(textColorId)

        lyricLinesGroup.check(
            if (FocusPreferences.getLyricMaxLines(requireContext()) == 1) R.id.lyric_lines_1 else R.id.lyric_lines_2
        )
        translationLinesGroup.check(
            if (FocusPreferences.getTranslationMaxLines(requireContext()) == 1) {
                R.id.translation_lines_1
            } else {
                R.id.translation_lines_2
            }
        )
        gravityGroup.check(
            when (FocusPreferences.getLyricGravity(requireContext())) {
                FocusPreferences.GRAVITY_LEFT -> R.id.gravity_left
                FocusPreferences.GRAVITY_RIGHT -> R.id.gravity_right
                else -> R.id.gravity_center
            }
        )
        backgroundGroup.clearChecked()
        backgroundGroupRow2.clearChecked()
        when (FocusPreferences.getFocusBackground(requireContext())) {
            FocusPreferences.BACKGROUND_BLACK -> backgroundGroup.check(R.id.background_black)
            FocusPreferences.BACKGROUND_WHITE -> backgroundGroupRow2.check(R.id.background_white)
            FocusPreferences.BACKGROUND_CUSTOM -> backgroundGroupRow2.check(R.id.background_custom)
            else -> backgroundGroup.check(R.id.background_default)
        }
        btnBackgroundPickColor.visibility = if (FocusPreferences.getFocusBackground(requireContext()) == FocusPreferences.BACKGROUND_CUSTOM) View.VISIBLE else View.GONE

        monetDynamicSwitch.isChecked = FocusPreferences.isMonetDynamicColorEnabled(requireContext())
        monetBgOnlySwitch.isChecked = FocusPreferences.isMonetBgOnly(requireContext())
        colorExtractionSwitch.isChecked = FocusPreferences.isTextColorExtractionEnabled(requireContext())
        colorModeSwitch.isChecked = FocusPreferences.isColorModeEnabled(requireContext())

        bindCustomAodTextSizeSlider(FocusPreferences.getCustomAodTextSize(requireContext()))
        bindCustomAodWidthSlider(FocusPreferences.getCustomAodLyricWidth(requireContext()))

        customAodLyricLinesGroup.check(
            if (FocusPreferences.getCustomAodLyricMaxLines(requireContext()) == 1) {
                R.id.custom_aod_lyric_lines_1
            } else {
                R.id.custom_aod_lyric_lines_2
            }
        )
        customAodTranslationLinesGroup.check(
            if (FocusPreferences.getCustomAodTranslationMaxLines(requireContext()) == 1) {
                R.id.custom_aod_translation_lines_1
            } else {
                R.id.custom_aod_translation_lines_2
            }
        )
        customAodGravityGroup.check(
            when (FocusPreferences.getCustomAodGravity(requireContext())) {
                FocusPreferences.GRAVITY_LEFT -> R.id.custom_aod_gravity_left
                FocusPreferences.GRAVITY_RIGHT -> R.id.custom_aod_gravity_right
                else -> R.id.custom_aod_gravity_center
            }
        )
        bindCustomAodTitleIcon(FocusPreferences.isCustomAodTitleIconEnabled(requireContext()))
        bindCustomAodTitleIconSize(FocusPreferences.getCustomAodTitleIconSize(requireContext()))
        bindCustomAodSongInfo(FocusPreferences.getCustomAodSongInfo(requireContext()))

            val colorMode = FocusPreferences.getCustomAodColorMode(requireContext())
            customAodColorModeGroup.check(
                when (colorMode) {
                    FocusPreferences.CUSTOM_AOD_COLOR_ALBUM -> R.id.custom_aod_color_album
                    FocusPreferences.CUSTOM_AOD_COLOR_PRESET -> R.id.custom_aod_color_preset
                    else -> R.id.custom_aod_color_white
                }
            )
        selectedPresetColor = FocusPreferences.getCustomAodPresetColor(requireContext())
        selectPresetColor(selectedPresetColor)

        val isPresetTextColor = FocusPreferences.getLyricTextColor(requireContext()) == FocusPreferences.TEXT_COLOR_PRESET
        lockScreenColorPalette.visibility = if (isPresetTextColor) View.VISIBLE else View.GONE
        btnLockScreenPickColor.visibility = if (isPresetTextColor) View.VISIBLE else View.GONE
        selectedLockScreenPresetColor = FocusPreferences.getLyricTextPresetColor(requireContext())
        selectLockScreenPresetColor(selectedLockScreenPresetColor)

        updateDynamicColorUi()
        isBindingUi = false
    }

    private fun updateLockScreenSectionState() {
        val customAodEnabled = FocusPreferences.isCustomAodLayout(requireContext())
        val enabled = !customAodEnabled
        val alpha = if (enabled) 1f else 0.38f

        lockScreenSectionHint.visibility = if (customAodEnabled) View.VISIBLE else View.GONE
        lockScreenStyleCard.alpha = if (enabled) 1f else 0.72f
        lockScreenControls.forEach { control ->
            control.isEnabled = enabled
            control.alpha = alpha
        }
        updateDynamicColorUi()
        updateMultiLineDependentUi()
        reorderStyleCards()
    }

    private fun reorderStyleCards() {
        val parent = lockScreenSection.parent as? ViewGroup ?: return
        val customAodEnabled = FocusPreferences.isCustomAodLayout(requireContext())
        val lockIdx = parent.indexOfChild(lockScreenSection)
        val aodIdx = parent.indexOfChild(customAodSection)
        if (lockIdx < 0 || aodIdx < 0) return

        if (customAodEnabled && lockIdx < aodIdx) {
            parent.removeView(customAodSection)
            parent.addView(customAodSection, lockIdx)
        } else if (!customAodEnabled && aodIdx < lockIdx) {
            parent.removeView(lockScreenSection)
            parent.addView(lockScreenSection, aodIdx)
        }
    }

    private fun isRealtimeSource(): Boolean {
        return FocusPreferences.getLyricSource(requireContext()) == FocusPreferences.LYRIC_SOURCE_SUPERLYRIC
    }

    private fun updateCustomAodSectionState() {
        val customAodEnabled = FocusPreferences.isCustomAodLayout(requireContext())
        val realtimeSource = isRealtimeSource()
        val alpha = if (customAodEnabled) 1f else 0.38f

        customAodSectionHint.visibility = if (customAodEnabled) View.GONE else View.VISIBLE
        customAodStyleCard.alpha = if (customAodEnabled) 1f else 0.72f
        customAodControls.forEach { control ->
            if (realtimeSource && control == customAodTranslationLinesGroup) {
                control.isEnabled = false
                control.alpha = 0.38f
            } else {
                control.isEnabled = customAodEnabled
                control.alpha = alpha
            }
        }
        colorPresetViews.forEach { chip ->
            chip.isEnabled = customAodEnabled
            chip.alpha = alpha
        }
    }

    private fun updateMultiLineDependentUi() {
        val multiLineEnabled = FocusPreferences.isMultiLineLyrics(requireContext())
        val customAodEnabled = FocusPreferences.isCustomAodLayout(requireContext())
        val lockScreenInteractive = !customAodEnabled
        val realtimeSource = isRealtimeSource()

        if (customAodEnabled) {
            switchMultiLineLyrics.isEnabled = false
            switchMultiLineLyrics.alpha = 0.38f
            if (multiLineEnabled) {
                FocusPreferences.setMultiLineLyrics(requireContext(), false)
                switchMultiLineLyrics.isChecked = false
            }
            multiLineCountRow.visibility = View.GONE
            multiLineTranslationRow.visibility = View.GONE
            multiLineTextSizeRow.visibility = View.GONE
            aodMultiLineOnlyRow.visibility = View.GONE
            return
        }
        switchMultiLineLyrics.isEnabled = true
        switchMultiLineLyrics.alpha = 1f

        if (realtimeSource) {
            multiLineCountRow.visibility = View.GONE
            multiLineTranslationRow.visibility = View.GONE
            multiLineTextSizeRow.visibility = View.GONE
            aodMultiLineOnlyRow.visibility = View.GONE
            switchMultiLineLyrics.isEnabled = false
            switchMultiLineLyrics.alpha = 0.38f
            switchSwapLyricTranslation.isEnabled = false
            switchSwapLyricTranslation.alpha = 0.38f
            setSectionEnabled(section = singleLineOnlyRow, title = singleLineOnlyTitle,
                hint = singleLineOnlyHint, hintText = "SuperLyric 源不支持",
                enabled = false, controls = listOf(switchSingleLineOnly))
            translationLinesGroup.isEnabled = false
            translationLinesGroup.alpha = 0.38f
            return
        }

        multiLineCountRow.visibility = if (multiLineEnabled) View.VISIBLE else View.GONE
        multiLineTranslationRow.visibility = if (multiLineEnabled) View.VISIBLE else View.GONE
        multiLineTextSizeRow.visibility = if (multiLineEnabled) View.VISIBLE else View.GONE
        aodMultiLineOnlyRow.visibility = if (multiLineEnabled) View.VISIBLE else View.GONE
        val multiLineInteractive = lockScreenInteractive && multiLineEnabled
        switchAodMultiLineOnly.isEnabled = multiLineInteractive
        switchAodMultiLineOnly.alpha = if (multiLineInteractive) 1f else 0.38f
        switchMultiLineShowTranslation.isEnabled = multiLineInteractive
        switchMultiLineShowTranslation.alpha = if (multiLineInteractive) 1f else 0.38f
        sliderMultiLineCount.isEnabled = multiLineInteractive
        sliderMultiLineCount.alpha = if (multiLineInteractive) 1f else 0.38f
        tvMultiLineCountLabel.alpha = if (multiLineInteractive) 1f else 0.38f
        sliderMultiLineTextSize.isEnabled = multiLineInteractive
        sliderMultiLineTextSize.alpha = if (multiLineInteractive) 1f else 0.38f
        tvMultiLineTextSizeLabel.alpha = if (multiLineInteractive) 1f else 0.38f

        // 多行模式下锁屏字号由多行字号接管，原字号滑块失效
        val textSizeInteractive = lockScreenInteractive && !multiLineEnabled
        sliderTextSize.isEnabled = textSizeInteractive
        sliderTextSize.alpha = if (textSizeInteractive) 1f else 0.38f
        tvTextSizeLabel.alpha = if (textSizeInteractive) 1f else 0.38f

        setSectionEnabled(
            section = singleLineOnlyRow,
            title = singleLineOnlyTitle,
            hint = singleLineOnlyHint,
            hintText = if (multiLineEnabled) "多行模式下不可用" else null,
            enabled = !multiLineEnabled,
            controls = listOf(switchSingleLineOnly)
        )

        val linesInteractive = lockScreenInteractive && !multiLineEnabled
        setSectionEnabled(
            section = linesCard,
            title = linesCardTitle,
            hint = linesCardHint,
            hintText = if (multiLineEnabled) "多行模式下不可用" else null,
            enabled = linesInteractive,
            controls = listOf(lyricLinesGroup, translationLinesGroup)
        )
    }

    private fun updateDynamicColorUi() {
        val monetEnabled = FocusPreferences.isMonetDynamicColorEnabled(requireContext())
        val textExtractionEnabled = FocusPreferences.isTextColorExtractionEnabled(requireContext())
        val colorModeEnabled = FocusPreferences.isColorModeEnabled(requireContext())
        val anyExtraction = monetEnabled || textExtractionEnabled
        val manualTextEnabled = (!monetEnabled || FocusPreferences.isMonetBgOnly(requireContext())) && !textExtractionEnabled

        monetBgOnlyRow.visibility = if (monetEnabled) View.VISIBLE else View.GONE
        colorModeSwitch.isEnabled = anyExtraction

        setSectionEnabled(
            section = textColorSection,
            title = textColorTitle,
            hint = textColorHint,
            hintText = when {
                monetEnabled && FocusPreferences.isMonetBgOnly(requireContext()) -> "仅背景取色已接管通知背景，文字颜色可手动设置"
                monetEnabled -> if (colorModeEnabled) "色彩模式已接管文字颜色" else "Monet 动态取色已接管文字颜色"
                textExtractionEnabled -> if (colorModeEnabled) "色彩模式已接管文字颜色" else "通知文字取色已接管文字颜色"
                else -> null
            },
            enabled = manualTextEnabled,
            controls = listOf(textColorGroup, textColorWhite, textColorBlack)
        )

        setSectionEnabled(
            section = backgroundSection,
            title = backgroundTitle,
            hint = backgroundHint,
            hintText = when {
                monetEnabled -> if (colorModeEnabled) "色彩模式已接管焦点通知背景" else "Monet 动态取色已接管焦点通知背景"
                else -> null
            },
            enabled = !monetEnabled,
            controls = listOf(backgroundGroup, backgroundGroupRow2, backgroundDefault, backgroundBlack, backgroundWhite, backgroundCustom, btnBackgroundPickColor)
        )

        setSectionEnabled(
            section = colorExtractionSection,
            title = colorExtractionTitle,
            hint = colorExtractionHint,
            hintText = when {
                monetEnabled -> "Monet 动态取色已包含文字取色"
                else -> null
            },
            enabled = !monetEnabled,
            controls = listOf(colorExtractionSwitch)
        )

        colorModeSwitch.isEnabled = anyExtraction
        val bgCustom = FocusPreferences.getFocusBackground(requireContext()) == FocusPreferences.BACKGROUND_CUSTOM
        extractedOpacitySection.visibility = if (anyExtraction || bgCustom) View.VISIBLE else View.GONE
        if (bgCustom && !monetEnabled) {
            btnBackgroundPickColor.visibility = View.VISIBLE
        } else if (!bgCustom) {
            btnBackgroundPickColor.visibility = View.GONE
        }
    }

    private fun bindCustomAodSongInfo(mode: String) {
        customAodSongInfoGroup.clearChecked()
        customAodSongInfoGroupRow2.clearChecked()
        when (mode) {
            FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_TITLE ->
                customAodSongInfoGroup.check(R.id.custom_aod_song_info_hide_title)
            FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ARTIST ->
                customAodSongInfoGroupRow2.check(R.id.custom_aod_song_info_hide_artist)
            FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ALL ->
                customAodSongInfoGroupRow2.check(R.id.custom_aod_song_info_hide_all)
            else -> customAodSongInfoGroup.check(R.id.custom_aod_song_info_all)
        }
    }

    private fun bindCustomAodTitleIcon(enabled: Boolean) {
        customAodTitleIconSwitch.isChecked = enabled
        customAodTitleIconPresetSection.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun bindCustomAodTitleIconSize(sizePercent: Int) {
        sliderCustomAodTitleIconSize.value = sizePercent.toFloat()
        tvCustomAodTitleIconSizeLabel.text = FocusPreferences.formatCustomAodTitleIconSizeLabel(sizePercent)
    }

    private fun songInfoModeFromCheckedId(checkedId: Int): String {
        return when (checkedId) {
            R.id.custom_aod_song_info_hide_title ->
                FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_TITLE
            R.id.custom_aod_song_info_hide_artist ->
                FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ARTIST
            R.id.custom_aod_song_info_hide_all ->
                FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ALL
            else -> FocusPreferences.CUSTOM_AOD_SONG_INFO_ALL
        }
    }

    private fun updateCustomAodColorUi() {
        val mode = FocusPreferences.getCustomAodColorMode(requireContext())
        val showPalette = mode == FocusPreferences.CUSTOM_AOD_COLOR_PRESET
        customAodColorPaletteSection.visibility = if (showPalette) View.VISIBLE else View.GONE
        if (showPalette) {
            selectPresetColor(FocusPreferences.getCustomAodPresetColor(requireContext()))
        }
    }

    private fun setSectionEnabled(
        section: View,
        title: TextView,
        hint: TextView,
        hintText: String?,
        enabled: Boolean,
        controls: List<View>
    ) {
        val alpha = if (enabled) 1f else 0.38f
        section.alpha = if (enabled) 1f else 0.72f
        title.alpha = alpha
        controls.forEach { control ->
            control.isEnabled = enabled
            control.alpha = alpha
        }
        if (hintText.isNullOrBlank()) {
            hint.visibility = View.GONE
        } else {
            hint.text = hintText
            hint.visibility = View.VISIBLE
        }
    }

    private fun bindTextSizeSlider(sizeSp: Float) {
        isTextSizeSliderUpdating = true
        sliderTextSize.value = sizeSp
        tvTextSizeLabel.text = formatTextSizeLabel(sizeSp)
        isTextSizeSliderUpdating = false
    }

    private fun bindExtractedOpacitySlider(opacity: Int) {
        sliderExtractedOpacity.value = opacity.toFloat()
        tvExtractedOpacityLabel.text = "${opacity}%"
    }

    private fun bindMultiLineTextSizeSlider(sizeSp: Float) {
        isMultiLineTextSizeUpdating = true
        sliderMultiLineTextSize.value = sizeSp
        tvMultiLineTextSizeLabel.text = formatTextSizeLabel(sizeSp)
        isMultiLineTextSizeUpdating = false
    }

    private fun bindCustomAodTextSizeSlider(sizeSp: Float) {
        isCustomAodTextSizeUpdating = true
        sliderCustomAodTextSize.value = sizeSp
        tvCustomAodTextSizeLabel.text = formatTextSizeLabel(sizeSp)
        isCustomAodTextSizeUpdating = false
    }

    private fun bindCustomAodWidthSlider(widthPercent: Int) {
        isCustomAodWidthUpdating = true
        sliderCustomAodWidth.value = widthPercent.toFloat()
        tvCustomAodWidthLabel.text = FocusPreferences.formatCustomAodLyricWidthLabel(widthPercent)
        isCustomAodWidthUpdating = false
    }

    private fun formatTextSizeLabel(sizeSp: Float): String = "${sizeSp.roundToInt()} sp"

    private fun isManualTextColorLocked(): Boolean {
        val monetEnabled = FocusPreferences.isMonetDynamicColorEnabled(requireContext())
        if (monetEnabled && FocusPreferences.isMonetBgOnly(requireContext())) return false
        return monetEnabled || FocusPreferences.isTextColorExtractionEnabled(requireContext())
    }

    private fun setupListeners() {
        switchSwapLyricTranslation.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            FocusPreferences.setSwapLyricTranslation(requireContext(), checked)
            notifyStyleChanged()
        }
        switchSingleLineOnly.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            if (FocusPreferences.isMultiLineLyrics(requireContext())) {
                switchSingleLineOnly.isChecked = FocusPreferences.isSingleLineOnly(requireContext())
                return@setOnCheckedChangeListener
            }
            FocusPreferences.setSingleLineOnly(requireContext(), checked)
            notifyStyleChanged()
        }
        switchMultiLineLyrics.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            if (FocusPreferences.isCustomAodLayout(requireContext())) {
                switchMultiLineLyrics.isChecked = FocusPreferences.isMultiLineLyrics(requireContext())
                return@setOnCheckedChangeListener
            }
            FocusPreferences.setMultiLineLyrics(requireContext(), checked)
            updateMultiLineDependentUi()
            notifyStyleChanged()
            if (!checked) {
                previewExpanded = true
                previewContent.visibility = View.VISIBLE
                previewExpandIcon.rotation = 180f
                refreshPreview()
            }
        }
        switchMultiLineShowTranslation.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            if (FocusPreferences.isCustomAodLayout(requireContext()) ||
                !FocusPreferences.isMultiLineLyrics(requireContext())
            ) {
                switchMultiLineShowTranslation.isChecked =
                    FocusPreferences.isMultiLineShowTranslation(requireContext())
                return@setOnCheckedChangeListener
            }
            FocusPreferences.setMultiLineShowTranslation(requireContext(), checked)
            notifyStyleChanged()
        }
        switchAodMultiLineOnly.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            FocusPreferences.setAodMultiLineOnly(requireContext(), checked)
            notifyStyleChanged()
        }
        sliderMultiLineCount.addOnChangeListener { _, value, fromUser ->
            tvMultiLineCountLabel.text = "${value.toInt()} 行"
        }
        sliderMultiLineCount.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                if (!FocusPreferences.isMultiLineLyrics(requireContext()) ||
                    !sliderMultiLineCount.isEnabled
                ) {
                    return
                }
                val count = slider.value.toInt()
                FocusPreferences.setMultiLineLineCount(requireContext(), count)
                notifyStyleChanged()
            }
        })

        sliderMultiLineTextSize.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isMultiLineTextSizeUpdating) return@addOnChangeListener
            tvMultiLineTextSizeLabel.text = formatTextSizeLabel(value)
        }
        sliderMultiLineTextSize.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                if (!FocusPreferences.isMultiLineLyrics(requireContext()) ||
                    !sliderMultiLineTextSize.isEnabled
                ) {
                    return
                }
                val normalized = slider.value.coerceIn(
                    FocusPreferences.MIN_LYRIC_TEXT_SIZE_SP,
                    FocusPreferences.MAX_LYRIC_TEXT_SIZE_SP
                )
                FocusPreferences.setMultiLineTextSize(requireContext(), normalized)
                bindMultiLineTextSizeSlider(normalized)
                notifyStyleChanged()
            }
        })

        sliderTextSize.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isTextSizeSliderUpdating) return@addOnChangeListener
            tvTextSizeLabel.text = formatTextSizeLabel(value)
        }
        sliderTextSize.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                if (!lockScreenControls.first().isEnabled) return
                val normalized = slider.value.coerceIn(
                    FocusPreferences.MIN_LYRIC_TEXT_SIZE_SP,
                    FocusPreferences.MAX_LYRIC_TEXT_SIZE_SP
                )
                FocusPreferences.setLyricTextSize(requireContext(), normalized)
                bindTextSizeSlider(normalized)
                notifyStyleChanged()
            }
        })

        sliderCustomAodTextSize.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isCustomAodTextSizeUpdating) return@addOnChangeListener
            tvCustomAodTextSizeLabel.text = formatTextSizeLabel(value)
        }
        sliderCustomAodTextSize.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                if (!customAodControls.first().isEnabled) return
                val normalized = slider.value.coerceIn(
                    FocusPreferences.MIN_LYRIC_TEXT_SIZE_SP,
                    FocusPreferences.MAX_LYRIC_TEXT_SIZE_SP
                )
                FocusPreferences.setCustomAodTextSize(requireContext(), normalized)
                bindCustomAodTextSizeSlider(normalized)
                notifyStyleChanged()
            }
        })

        sliderCustomAodWidth.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isCustomAodWidthUpdating) return@addOnChangeListener
            tvCustomAodWidthLabel.text = FocusPreferences.formatCustomAodLyricWidthLabel(value.toInt())
        }
        sliderCustomAodWidth.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                if (!customAodControls.first().isEnabled) return
                val normalized = slider.value.toInt().coerceIn(
                    FocusPreferences.MIN_CUSTOM_AOD_LYRIC_WIDTH,
                    FocusPreferences.MAX_CUSTOM_AOD_LYRIC_WIDTH
                )
                FocusPreferences.setCustomAodLyricWidth(requireContext(), normalized)
                bindCustomAodWidthSlider(normalized)
                notifyStyleChanged()
            }
        })

        textColorGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked || isManualTextColorLocked()) return@addOnButtonCheckedListener
            val color = when (checkedId) {
                R.id.text_color_black -> FocusPreferences.TEXT_COLOR_BLACK
                R.id.text_color_preset -> FocusPreferences.TEXT_COLOR_PRESET
                else -> FocusPreferences.TEXT_COLOR_WHITE
            }
            FocusPreferences.setLyricTextColor(requireContext(), color)
            val isPreset = color == FocusPreferences.TEXT_COLOR_PRESET
            lockScreenColorPalette.visibility = if (isPreset) View.VISIBLE else View.GONE
            btnLockScreenPickColor.visibility = if (isPreset) View.VISIBLE else View.GONE
            if (isPreset) {
                FocusPreferences.setLyricTextPresetColor(requireContext(), selectedLockScreenPresetColor)
            }
            notifyStyleChanged()
        }

        lyricLinesGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
            if (FocusPreferences.isMultiLineLyrics(requireContext()) ||
                FocusPreferences.isCustomAodLayout(requireContext())
            ) {
                return@addOnButtonCheckedListener
            }
            FocusPreferences.setLyricMaxLines(
                requireContext(),
                if (checkedId == R.id.lyric_lines_1) 1 else 2
            )
            notifyStyleChanged()
        }

        translationLinesGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
            if (FocusPreferences.isMultiLineLyrics(requireContext()) ||
                FocusPreferences.isCustomAodLayout(requireContext())
            ) {
                return@addOnButtonCheckedListener
            }
            FocusPreferences.setTranslationMaxLines(
                requireContext(),
                if (checkedId == R.id.translation_lines_1) 1 else 2
            )
            notifyStyleChanged()
        }

        gravityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
            val gravity = when (checkedId) {
                R.id.gravity_left -> FocusPreferences.GRAVITY_LEFT
                R.id.gravity_right -> FocusPreferences.GRAVITY_RIGHT
                else -> FocusPreferences.GRAVITY_CENTER
            }
            FocusPreferences.setLyricGravity(requireContext(), gravity)
            notifyStyleChanged()
        }

        val bgListener = MaterialButtonToggleGroup.OnButtonCheckedListener { group, checkedId, isChecked ->
            if (isBindingUi || !isChecked || FocusPreferences.isMonetDynamicColorEnabled(requireContext())) {
                return@OnButtonCheckedListener
            }
            // 互斥：选这组清另一组
            val otherGroup = if (group === backgroundGroup) backgroundGroupRow2 else backgroundGroup
            try { otherGroup.clearChecked() } catch (_: Throwable) {}
            val background = when (checkedId) {
                R.id.background_black -> FocusPreferences.BACKGROUND_BLACK
                R.id.background_white -> FocusPreferences.BACKGROUND_WHITE
                R.id.background_custom -> FocusPreferences.BACKGROUND_CUSTOM
                else -> FocusPreferences.BACKGROUND_DEFAULT
            }
            FocusPreferences.setFocusBackground(requireContext(), background)
            val isCustom = background == FocusPreferences.BACKGROUND_CUSTOM
            btnBackgroundPickColor.visibility = if (isCustom) View.VISIBLE else View.GONE
            updateDynamicColorUi()
            notifyStyleChanged()
        }
        backgroundGroup.addOnButtonCheckedListener(bgListener)
        backgroundGroupRow2.addOnButtonCheckedListener(bgListener)

        btnBackgroundPickColor.setOnClickListener {
            showBackgroundColorPicker()
        }

        monetDynamicSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            FocusPreferences.setMonetDynamicColorEnabled(requireContext(), isChecked)
            if (isChecked) {
                FocusPreferences.setTextColorExtractionEnabled(requireContext(), false)
                colorExtractionSwitch.isChecked = false
            } else {
                FocusPreferences.clearExtractedTextColor(requireContext())
            }
            updateDynamicColorUi()
            notifyStyleChanged()
        }

        monetBgOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            FocusPreferences.setMonetBgOnly(requireContext(), isChecked)
            updateDynamicColorUi()
            notifyStyleChanged()
        }

        colorExtractionSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingUi || FocusPreferences.isMonetDynamicColorEnabled(requireContext())) return@setOnCheckedChangeListener
            FocusPreferences.setTextColorExtractionEnabled(requireContext(), isChecked)
            if (!isChecked) {
                FocusPreferences.clearExtractedTextColor(requireContext())
            }
            updateDynamicColorUi()
            notifyStyleChanged()
        }

        colorModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            FocusPreferences.setColorModeEnabled(requireContext(), isChecked)
            if (!isChecked) {
                FocusPreferences.clearExtractedTextColor(requireContext())
            }
            updateDynamicColorUi()
            notifyStyleChanged()
        }

        sliderExtractedOpacity.addOnChangeListener { _, value, fromUser ->
            if (isBindingUi || !fromUser) return@addOnChangeListener
            val opacity = value.roundToInt()
            tvExtractedOpacityLabel.text = "${opacity}%"
            FocusPreferences.setExtractedColorOpacity(requireContext(), opacity)
            refreshPreview()
        }
        sliderExtractedOpacity.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                notifyStyleChanged()
            }
        })

        customAodLyricLinesGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
            FocusPreferences.setCustomAodLyricMaxLines(
                requireContext(),
                if (checkedId == R.id.custom_aod_lyric_lines_1) 1 else 2
            )
            notifyStyleChanged()
        }

        customAodTranslationLinesGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
            FocusPreferences.setCustomAodTranslationMaxLines(
                requireContext(),
                if (checkedId == R.id.custom_aod_translation_lines_1) 1 else 2
            )
            notifyStyleChanged()
        }

        customAodGravityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
            val gravity = when (checkedId) {
                R.id.custom_aod_gravity_left -> FocusPreferences.GRAVITY_LEFT
                R.id.custom_aod_gravity_right -> FocusPreferences.GRAVITY_RIGHT
                else -> FocusPreferences.GRAVITY_CENTER
            }
            FocusPreferences.setCustomAodGravity(requireContext(), gravity)
            notifyStyleChanged()
        }

        customAodTitleIconSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            FocusPreferences.setCustomAodTitleIconEnabled(requireContext(), isChecked)
            isBindingUi = true
            customAodTitleIconPresetSection.visibility = if (isChecked) View.VISIBLE else View.GONE
            isBindingUi = false
            notifyStyleChanged()
        }

        sliderCustomAodTitleIconSize.addOnChangeListener { _, value, _ ->
            if (isBindingUi) return@addOnChangeListener
            val sizePercent = value.toInt()
            tvCustomAodTitleIconSizeLabel.text = FocusPreferences.formatCustomAodTitleIconSizeLabel(sizePercent)
            FocusPreferences.setCustomAodTitleIconSize(requireContext(), sizePercent)
            notifyStyleChanged()
        }

        val songInfoListener = MaterialButtonToggleGroup.OnButtonCheckedListener { group, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@OnButtonCheckedListener
            isBindingUi = true
            if (group == customAodSongInfoGroup) {
                customAodSongInfoGroupRow2.clearChecked()
            } else {
                customAodSongInfoGroup.clearChecked()
            }
            isBindingUi = false
            FocusPreferences.setCustomAodSongInfo(requireContext(), songInfoModeFromCheckedId(checkedId))
            notifyStyleChanged()
        }
        customAodSongInfoGroup.addOnButtonCheckedListener(songInfoListener)
        customAodSongInfoGroupRow2.addOnButtonCheckedListener(songInfoListener)

        customAodColorModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.custom_aod_color_album -> FocusPreferences.CUSTOM_AOD_COLOR_ALBUM
                R.id.custom_aod_color_preset -> FocusPreferences.CUSTOM_AOD_COLOR_PRESET
                else -> FocusPreferences.CUSTOM_AOD_COLOR_WHITE
            }
            FocusPreferences.setCustomAodColorMode(requireContext(), mode)
            updateCustomAodColorUi()
            notifyStyleChanged()
        }

        btnCustomAodPickColor.setOnClickListener {
            if (!customAodControls.first().isEnabled) return@setOnClickListener
            showCustomColorPickerDialog()
        }
    }

    private fun showCustomColorPickerDialog() {
        showColorPickerDialog("自定义颜色", selectedPresetColor,
            onConfirm = { color ->
                FocusPreferences.setCustomAodColorMode(requireContext(), FocusPreferences.CUSTOM_AOD_COLOR_PRESET)
                FocusPreferences.setCustomAodPresetColor(requireContext(), color)
                selectPresetColor(color)
                updateCustomAodColorUi()
                notifyStyleChanged()
            }
        )
    }

    // ── 预览区 ──

    private lateinit var previewHeader: View
    private lateinit var previewContent: View
    private lateinit var previewExpandIcon: ImageView
    private lateinit var previewModeLabel: TextView
    private lateinit var previewRoot: View
    private lateinit var previewSongRow: View
    private lateinit var previewSongTitle: TextView
    private lateinit var previewLyric: TextView
    private lateinit var previewSecond: TextView
    private lateinit var previewMultiLines: View
    private lateinit var previewIcon: ImageView
    private val previewMultiTextViews = mutableListOf<TextView>()
    private var previewBound = false
    private var previewExpanded = true
    private var previewFullHeight = 0
    private var previewHeightAnimator: ValueAnimator? = null

    private fun bindPreviewViews(view: View) {
        previewHeader = view.findViewById(R.id.preview_header)
        previewContent = view.findViewById(R.id.preview_content)
        previewExpandIcon = view.findViewById(R.id.preview_expand_icon)
        previewModeLabel = view.findViewById(R.id.preview_mode_label)
        previewRoot = view.findViewById(R.id.preview_root)
        previewSongRow = view.findViewById(R.id.preview_song_row)
        previewSongTitle = view.findViewById(R.id.preview_song_title)
        previewLyric = view.findViewById(R.id.preview_lyric)
        previewSecond = view.findViewById(R.id.preview_second)
        previewMultiLines = view.findViewById(R.id.preview_multi_lines)
        previewIcon = view.findViewById(R.id.preview_icon)
        for (id in intArrayOf(
            R.id.preview_ml_0, R.id.preview_ml_1, R.id.preview_ml_2, R.id.preview_ml_3,
            R.id.preview_ml_4, R.id.preview_ml_5, R.id.preview_ml_6, R.id.preview_ml_7
        )) {
            previewMultiTextViews += view.findViewById<TextView>(id)
        }
        previewHeader.setOnTouchListener { v, event -> handlePreviewTouch(event) }
        // 多行模式下向上滑动自动收起预览（带动画）
        val scrollView = view.findViewById<NestedScrollView>(R.id.style_content)
        var lastScrollY = 0
        var lastToggleTime = 0L
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val scrollingDown = scrollY > lastScrollY
            lastScrollY = scrollY
            if (FocusPreferences.isMultiLineLyrics(requireContext())) {
                val now = System.currentTimeMillis()
                if (now - lastToggleTime < 400) return@setOnScrollChangeListener
                if (scrollingDown && scrollY > 500 && previewExpanded) {
                    animatePreviewHeight(false); lastToggleTime = now
                } else if (!scrollingDown && scrollY < 80 && !previewExpanded) {
                    animatePreviewHeight(true); lastToggleTime = now
                }
            }
        }
        // 默认展开
        previewContent.visibility = View.VISIBLE
        previewExpandIcon.rotation = 180f
        previewContent.post {
            previewFullHeight = measureFullHeight(previewContent)
        }
        previewBound = true
    }

    private fun measureFullHeight(view: View): Int {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(view.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private var dragStartY = 0f
    private var dragStartExpanded = false
    private var dragActive = false

    private fun handlePreviewTouch(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartY = event.rawY
                dragStartExpanded = previewExpanded
                dragActive = false
                previewHeightAnimator?.cancel()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - dragStartY
                if (!dragActive && kotlin.math.abs(deltaY) > 10f) {
                    dragActive = true
                }
                if (!dragActive) return@handlePreviewTouch true
                val fh = previewFullHeight
                if (fh <= 0) return@handlePreviewTouch true
                if (previewContent.visibility != View.VISIBLE) {
                    previewContent.visibility = View.VISIBLE
                    previewContent.layoutParams = previewContent.layoutParams.apply { height = 0 }
                }
                val baseHeight = if (dragStartExpanded) fh else 0
                val targetHeight = (baseHeight - deltaY).toInt().coerceIn(0, fh)
                previewContent.layoutParams = previewContent.layoutParams.apply { height = targetHeight }
                previewExpandIcon.rotation = 180f * targetHeight / fh
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragActive) {
                    dragActive = false
                    val deltaY = event.rawY - dragStartY
                    val threshold = (80 * resources.displayMetrics.density).toInt()
                    val newState = when {
                        deltaY < -threshold -> true
                        deltaY > threshold -> false
                        else -> dragStartExpanded
                    }
                    previewExpanded = newState
                    animatePreviewHeight(newState)
                } else {
                    // no drag → treat as click
                    togglePreview()
                }
                true
            }
            else -> false
        }
    }

    private fun togglePreview() {
        val newExpanded = !previewExpanded
        previewExpanded = newExpanded
        animatePreviewHeight(newExpanded)
    }

    private fun animatePreviewHeight(show: Boolean, animate: Boolean = true) {
        val content = previewContent
        val fullHeight = previewFullHeight
        val icon = previewExpandIcon
        val duration = if (animate) 300L else 0L

        previewHeightAnimator?.cancel()
        previewExpanded = show

        if (fullHeight <= 0 || duration <= 0) {
            val lp = content.layoutParams
            lp.height = if (show) ViewGroup.LayoutParams.WRAP_CONTENT else 0
            content.layoutParams = lp
            content.visibility = if (show) View.VISIBLE else View.GONE
            icon.rotation = if (show) 180f else 0f
            if (show) refreshPreview()
            return
        }

        val startHeight = content.height.coerceAtLeast(0)
        val endHeight = if (show) fullHeight else 0

        if (show) {
            content.visibility = View.VISIBLE
        }

        val anim = ValueAnimator.ofInt(startHeight, endHeight).apply {
            this.duration = duration
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { a ->
                val lp = content.layoutParams
                lp.height = a.animatedValue as Int
                content.layoutParams = lp
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (show) {
                        val lp = content.layoutParams
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        content.layoutParams = lp
                    } else {
                        content.visibility = View.GONE
                    }
                }
            })
        }
        previewHeightAnimator = anim
        anim.start()

        icon.animate().rotation(if (show) 180f else 0f).setDuration(duration).start()
        if (show) refreshPreview()
    }

    private fun refreshPreview() {
        if (!previewBound) return
        val ctx = requireContext()
        val isCustomAod = FocusPreferences.isCustomAodLayout(ctx)
        val isMultiLine = FocusPreferences.isMultiLineLyrics(ctx)
        previewModeLabel.text = buildString {
            append(if (isCustomAod) "\u4e07\u8c61\u606f\u5c4f AOD" else "\u9501\u5c4f\u901a\u77e5")
            if (isMultiLine) append(" \u00b7 \u591a\u884c\u6b4c\u8bcd")
        }
        if (!previewExpanded) return
        val state = LyricService.previewState
        val hasLyric = state.isPlaying && state.lyricText.isNotBlank()
        if (isCustomAod) {
            refreshAodPreview(ctx, state, hasLyric)
        } else {
            refreshLockScreenPreview(ctx, state, hasLyric)
        }
    }

    /**
     * 与 HyperFocusLyricStyle.resolveLyricStyle 保持一致的颜色逻辑。
     * @return Triple(primaryColor, secondaryColor, backgroundColorOrNull)
     */
    private fun resolvePreviewColors(ctx: android.content.Context): Triple<Int, Int, Int?> {
        val monetEnabled = FocusPreferences.isMonetDynamicColorEnabled(ctx)
        val textExtractionEnabled = FocusPreferences.isTextColorExtractionEnabled(ctx)
        val colorModeEnabled = FocusPreferences.isColorModeEnabled(ctx)
        val hasExtractedColors = monetEnabled || textExtractionEnabled
        val extractedTextColor = if (hasExtractedColors) FocusPreferences.getExtractedTextColor(ctx) else null
        val extractedBgColor = FocusPreferences.getExtractedBgColor(ctx)
        val extractedAccentColor = FocusPreferences.getExtractedAccentColor(ctx)
        val textColor = FocusPreferences.getLyricTextColor(ctx)
        val background = FocusPreferences.getFocusBackground(ctx)

        return com.leowalk.LyricFocus.notification.HyperFocusLyricStyle.resolveLockScreenColors(
            monetEnabled = monetEnabled,
            textExtractionEnabled = textExtractionEnabled,
            colorModeEnabled = colorModeEnabled,
            extractedTextColor = extractedTextColor,
            extractedBgColor = extractedBgColor,
            extractedAccentColor = extractedAccentColor,
            textColor = textColor,
            background = background
        )
    }

    private fun refreshAodPreview(ctx: android.content.Context, state: LyricService.PreviewState, hasLyric: Boolean) {
        previewRoot.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.BLACK)
            cornerRadius = 16f * ctx.resources.displayMetrics.density
        }
        previewMultiLines.visibility = View.GONE
        previewLyric.visibility = View.VISIBLE
        previewSecond.visibility = View.VISIBLE

        val textSize = FocusPreferences.getCustomAodTextSize(ctx)
        val aodColorMode = FocusPreferences.getCustomAodColorMode(ctx)
        val gravity = gravityToInt(FocusPreferences.getCustomAodGravity(ctx))
        val lyricMaxLines = FocusPreferences.getCustomAodLyricMaxLines(ctx)
        val translationMaxLines = FocusPreferences.getCustomAodTranslationMaxLines(ctx)
        val songInfo = FocusPreferences.getCustomAodSongInfo(ctx)
        val widthPercent = FocusPreferences.getCustomAodLyricWidth(ctx)

        val primaryColor: Int
        val secondaryColor: Int
        when (aodColorMode) {
            FocusPreferences.CUSTOM_AOD_COLOR_PRESET -> {
                val preset = FocusPreferences.getCustomAodPresetColor(ctx)
                primaryColor = preset
                secondaryColor = blendSecondary(preset)
            }
            FocusPreferences.CUSTOM_AOD_COLOR_ALBUM -> {
                val (p, s) = resolvePreviewColors(ctx)
                primaryColor = p
                secondaryColor = s
            }
            else -> {
                primaryColor = android.graphics.Color.WHITE
                secondaryColor = 0xFFE0E0E0.toInt()
            }
        }

        // 歌名行：按歌名显示设置
        val showTitle = songInfo != FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_TITLE &&
            songInfo != FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ALL
        val showArtist = songInfo != FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ARTIST &&
            songInfo != FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ALL
        val hideAll = songInfo == FocusPreferences.CUSTOM_AOD_SONG_INFO_HIDE_ALL

        previewSongRow.visibility = if (hideAll) View.GONE else View.VISIBLE
        if (!hideAll) {
            previewIcon.visibility = if (FocusPreferences.isCustomAodTitleIconEnabled(ctx)) View.VISIBLE else View.GONE
            if (previewIcon.visibility == View.VISIBLE) {
                val iconRes = iconPresetToResId(FocusPreferences.resolvePackageToIconPreset(state.musicPackage))
                previewIcon.setImageResource(iconRes)
                val iconSizePercent = FocusPreferences.getCustomAodTitleIconSize(ctx) / 100f
                val iconSizeSp = textSize * iconSizePercent
                val px = (iconSizeSp * ctx.resources.displayMetrics.scaledDensity).toInt()
                previewIcon.layoutParams = previewIcon.layoutParams.apply {
                    width = px
                    height = px
                }
                previewIcon.requestLayout()
            }
            val parts = mutableListOf<String>()
            if (showTitle && state.title.isNotBlank()) parts += state.title
            if (showArtist && state.artist.isNotBlank()) parts += state.artist
            previewSongTitle.text = if (parts.isNotEmpty()) parts.joinToString(" · ")
            else if (hasLyric) "" else "\u6b4c\u66f2"
            previewSongTitle.setTextColor(secondaryColor)
            previewSongTitle.textSize = textSize * 0.78f
        }

        // 歌词区域：居中，支持 AOD 宽度模拟
        val lyricText = if (hasLyric) state.lyricText else "\u6b4c\u8bcd\u9884\u89c8 \u266A"
        val secondText = if (hasLyric) {
            state.secondLine.ifBlank { "${state.title} - ${state.artist}" }
        } else {
            "\u526f\u6b4c\u8bcd / \u7ffb\u8bd1"
        }

        val padPercent = ((100 - widthPercent) * 48 / 50).coerceAtLeast(0)
        val padPx = (ctx.resources.displayMetrics.density * (4 + padPercent)).toInt()
        previewLyric.setPadding(padPx, 0, padPx, 0)
        previewSecond.setPadding(padPx, 0, padPx, 0)

        previewLyric.text = lyricText
        previewLyric.textSize = textSize
        previewLyric.setTextColor(primaryColor)
        previewLyric.gravity = gravity
        previewLyric.minLines = lyricMaxLines
        previewLyric.maxLines = lyricMaxLines

        previewSecond.text = secondText
        previewSecond.textSize = textSize * 0.78f * if (isJapaneseText(secondText)) 0.88f else 1f
        previewSecond.setTextColor(secondaryColor)
        previewSecond.gravity = gravity
        previewSecond.minLines = translationMaxLines
        previewSecond.maxLines = translationMaxLines
    }

    private fun refreshLockScreenPreview(ctx: android.content.Context, state: LyricService.PreviewState, hasLyric: Boolean) {
        // 锁屏样式不显示歌曲信息
        previewSongRow.visibility = View.GONE

        val multiLine = FocusPreferences.isMultiLineLyrics(ctx)
        val aodMultiLineOnly = multiLine && FocusPreferences.isAodMultiLineOnly(ctx)
        val singleLineOnly = FocusPreferences.isSingleLineOnly(ctx)
        val swapLyricTranslation = FocusPreferences.isSwapLyricTranslation(ctx)
        val textSize = FocusPreferences.getLyricTextSize(ctx)
        val gravity = gravityToInt(FocusPreferences.getLyricGravity(ctx))
        val lyricMaxLines = FocusPreferences.getLyricMaxLines(ctx)
        val translationMaxLines = FocusPreferences.getTranslationMaxLines(ctx)
        val background = FocusPreferences.getFocusBackground(ctx)
        val monetEnabled = FocusPreferences.isMonetDynamicColorEnabled(ctx)
        val (primaryColor, secondaryColor, extractedBg) = resolvePreviewColors(ctx)

        // 背景：有提取色用提取色，否则按手动设置
        val previewBgColor = if (extractedBg != null) extractedBg
        else when (background) {
            FocusPreferences.BACKGROUND_BLACK -> android.graphics.Color.BLACK
            FocusPreferences.BACKGROUND_WHITE -> android.graphics.Color.WHITE
            else -> 0xFF2A2A2A.toInt()
        }
        previewRoot.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(previewBgColor)
            cornerRadius = 16f * ctx.resources.displayMetrics.density
        }

        // 重置 padding
        previewLyric.setPadding(0, previewLyric.paddingTop, 0, previewLyric.paddingBottom)
        previewSecond.setPadding(0, previewSecond.paddingTop, 0, previewSecond.paddingBottom)

        if (multiLine) {
            previewLyric.visibility = View.GONE
            previewSecond.visibility = View.GONE
            previewMultiLines.visibility = View.VISIBLE

            val pageSlots = FocusPreferences.coerceMultiLineLineCount(FocusPreferences.getMultiLineLineCount(ctx))
            val mlTextSize = FocusPreferences.getMultiLineTextSize(ctx)
            val showTransPref = FocusPreferences.isMultiLineShowTranslation(ctx)

            val lyricInfo = LyricService.currentLyricInfoForPreview
            val allLines = if (!lyricInfo.isEmpty) lyricInfo.lines else emptyList()
            val currentIndex = if (allLines.isNotEmpty()) {
                lyricInfo.getCurrentLineIndex(LyricService.currentPlaybackPositionMs, FocusPreferences.getSyncAdvanceMs(ctx))
                    .coerceAtLeast(0)
            } else 0
            val effectiveCurrentSlot = 0
            val interleaved: Boolean
            val lines: List<String>

            if (allLines.isEmpty()) {
                interleaved = false
                lines = List(pageSlots) { "\u6b4c\u8bcd \u7b2c${it + 1}\u53e5" }
            } else if (showTransPref) {
                val flat = mutableListOf<String>()
                var fwdIdx = currentIndex
                var bwdIdx = currentIndex - 1
                var hasTrans = false
                var safety = 0
                while (flat.size < pageSlots && safety++ < 200) {
                    val l = allLines.getOrNull(fwdIdx)
                    val orig = l?.text?.trim()?.takeIf { it.isNotBlank() } ?: ""
                    if (orig.isNotEmpty()) {
                        flat += orig
                        if (flat.size >= pageSlots) break
                    }
                    val t = l?.translation?.replace("\n", " ")?.trim()?.takeIf { it.isNotBlank() } ?: ""
                    if (t.isNotEmpty()) {
                        flat += t; hasTrans = true
                        if (flat.size >= pageSlots) break
                    }
                    fwdIdx++
                    if (fwdIdx >= allLines.size) { if (bwdIdx < 0) bwdIdx = allLines.size - 1; if (bwdIdx < 0) fwdIdx = 0 else { fwdIdx = bwdIdx; bwdIdx-- } }
                }
                interleaved = hasTrans
                lines = flat
            } else {
                interleaved = false
                val flat = mutableListOf<String>()
                var fwdIdx = currentIndex
                var bwdIdx = currentIndex - 1
                var safety = 0
                while (flat.size < pageSlots && safety++ < 200) {
                    val text = allLines.getOrNull(fwdIdx)?.text?.trim()?.takeIf { it.isNotBlank() } ?: ""
                    if (text.isNotEmpty()) flat += text
                    fwdIdx++
                    if (fwdIdx >= allLines.size) { if (bwdIdx < 0) bwdIdx = allLines.size - 1; if (bwdIdx < 0) fwdIdx = 0 else { fwdIdx = bwdIdx; bwdIdx-- } }
                }
                lines = flat
            }

            // 多行取色与 applyMultiLineStyle 完全一致（aodMultiLineOnly 只影响显示场景，不影响颜色）
            val mlDefaultLine: Int
            val mlCurrentLine: Int
            val mlNonCurrentTrans: Int
            val mlGravity: Int
            val colorModeEnabled = FocusPreferences.isColorModeEnabled(ctx)
            val colorModeBgColor = if (colorModeEnabled) FocusPreferences.getExtractedBgColor(ctx) else null
            val mlMonetBgColor = if (monetEnabled) FocusPreferences.getExtractedBgColor(ctx) else null
            val mlExtractedBg = colorModeBgColor ?: mlMonetBgColor
            val extractedText = FocusPreferences.getExtractedTextColor(ctx)
            val extractedAccent = FocusPreferences.getExtractedAccentColor(ctx)
            val monetBgOnly = FocusPreferences.isMonetBgOnly(ctx)

            val defaultTextRef = if (monetBgOnly) primaryColor else (extractedText ?: primaryColor)
            mlDefaultLine = if (mlExtractedBg != null) {
                if (colorModeBgColor != null) {
                    AlbumColorExtractor.ensureContrastColorful(defaultTextRef, mlExtractedBg, 3.5)
                } else {
                    AlbumColorExtractor.ensureContrastSafe(defaultTextRef, mlExtractedBg)
                }
            } else {
                defaultTextRef
            }
            val accentRef = if (monetBgOnly) mlDefaultLine else (extractedAccent ?: mlDefaultLine)
            mlCurrentLine = if (colorModeBgColor != null) {
                AlbumColorExtractor.ensureContrastColorful(accentRef, colorModeBgColor, 3.5)
            } else if (mlMonetBgColor != null) {
                AlbumColorExtractor.ensureContrastSafe(mlDefaultLine, mlMonetBgColor)
            } else when (background) {
                FocusPreferences.BACKGROUND_WHITE -> android.graphics.Color.BLACK
                else -> primaryColor
            }
            mlNonCurrentTrans = fadeTextColor(mlDefaultLine)
            mlGravity = gravity
            val currentTransSlot = if (interleaved) 1 else -1
            for (i in 0 until 8) {
                val tv = previewMultiTextViews[i]
                if (i < pageSlots) {
                    val isTranslationSlot = interleaved && i % 2 == 1
                    val text = lines.getOrElse(i) { "" }
                    if (text.isBlank()) {
                        tv.visibility = View.INVISIBLE
                    } else {
                        tv.visibility = View.VISIBLE
                        tv.text = text
                        val isCurrent = i == effectiveCurrentSlot
                        val isCurrentTransLine = i == currentTransSlot && text.isNotBlank()
                        tv.setTextColor(when {
                            isCurrent || isCurrentTransLine -> mlCurrentLine
                            isTranslationSlot -> mlNonCurrentTrans
                            else -> mlDefaultLine
                        })
                        val jpScale = if (isJapaneseText(text)) 0.88f else 1f
                        tv.textSize = when {
                            isCurrent -> mlTextSize * jpScale
                            isCurrentTransLine -> mlTextSize * 0.8f * jpScale
                            else -> mlTextSize * 0.65f * jpScale
                        }
                        tv.typeface = if (isCurrent || isCurrentTransLine) {
                            android.graphics.Typeface.DEFAULT_BOLD
                        } else {
                            android.graphics.Typeface.DEFAULT
                        }
                        tv.gravity = mlGravity
                    }
                } else {
                    tv.visibility = View.GONE
                }
            }
        } else {
            previewMultiLines.visibility = View.GONE
            previewLyric.visibility = View.VISIBLE
            previewSecond.visibility = if (singleLineOnly) View.GONE else View.VISIBLE

            var lyricText = if (hasLyric) state.lyricText else "\u6b4c\u8bcd\u9884\u89c8 \u266A"
            var secondText = if (hasLyric && !singleLineOnly) {
                state.secondLine.ifBlank { "${state.title} - ${state.artist}" }
            } else if (!singleLineOnly) {
                "\u526f\u6b4c\u8bcd / \u7ffb\u8bd1"
            } else {
                ""
            }

            // 互换仅当有实际翻译内容时才生效，与焦点通知行为一致
            val hasTranslation = state.lineTranslation?.isNotBlank() == true
            if (swapLyricTranslation && hasLyric && hasTranslation && secondText.isNotBlank()) {
                val tmp = lyricText
                lyricText = secondText
                secondText = tmp
            }

            previewLyric.text = lyricText
            previewLyric.textSize = textSize
            previewLyric.setTextColor(primaryColor)
            previewLyric.gravity = gravity
            previewLyric.minLines = lyricMaxLines
            previewLyric.maxLines = lyricMaxLines

            if (!singleLineOnly) {
                previewSecond.text = secondText
                previewSecond.textSize = textSize * 0.78f * if (isJapaneseText(secondText)) 0.88f else 1f
                previewSecond.setTextColor(secondaryColor)
                previewSecond.gravity = gravity
                previewSecond.minLines = translationMaxLines
                previewSecond.maxLines = translationMaxLines
            }
        }
    }

    private fun fadeTextColor(color: Int, factor: Float = 0.72f): Int {
        val a = (android.graphics.Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(a, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
    }

    private fun isJapaneseText(text: String): Boolean {
        return text.any { c ->
            c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' || c in '\u4E00'..'\u9FFF'
        }
    }

    private fun blendSecondary(primary: Int): Int {
        val r = ((android.graphics.Color.red(primary) * 0.82f) + (255 * 0.18f)).toInt().coerceIn(0, 255)
        val g = ((android.graphics.Color.green(primary) * 0.82f) + (255 * 0.18f)).toInt().coerceIn(0, 255)
        val b = ((android.graphics.Color.blue(primary) * 0.82f) + (255 * 0.18f)).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun gravityToInt(gravity: String): Int {
        return when (gravity) {
            FocusPreferences.GRAVITY_LEFT -> android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            FocusPreferences.GRAVITY_RIGHT -> android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            else -> android.view.Gravity.CENTER
        }
    }

    private fun iconPresetToResId(preset: String): Int {
        return when (preset) {
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_NETEASE -> R.drawable.ic_app_icon_netease
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_QQ -> R.drawable.ic_app_icon_qq
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_KUGOU -> R.drawable.ic_app_icon_kugou
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_KUWO -> R.drawable.ic_app_icon_kuwo
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_QISHUI -> R.drawable.ic_app_icon_qishui
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_BODIAN -> R.drawable.ic_app_icon_bodian
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_SPOTIFY -> R.drawable.ic_app_icon_spotify
            FocusPreferences.CUSTOM_AOD_TITLE_ICON_APPLE -> R.drawable.ic_app_icon_apple
            else -> R.drawable.ic_app_icon_apple
        }
    }

    private fun notifyStyleChanged() {
        FocusPreferences.notifyStyleSettingsChanged(requireContext())
        refreshPreview()
    }
}
