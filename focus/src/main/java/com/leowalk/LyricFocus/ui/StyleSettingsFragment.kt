package com.leowalk.LyricFocus.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var multiLineCountGroup: MaterialButtonToggleGroup
    private lateinit var multiLineTranslationRow: View
    private lateinit var switchMultiLineShowTranslation: MaterialSwitch
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
    private lateinit var backgroundSection: View
    private lateinit var backgroundTitle: TextView
    private lateinit var backgroundHint: TextView
    private lateinit var backgroundGroup: MaterialButtonToggleGroup
    private lateinit var backgroundDefault: MaterialButton
    private lateinit var backgroundBlack: MaterialButton
    private lateinit var backgroundWhite: MaterialButton
    private lateinit var colorExtractionSection: View
    private lateinit var colorExtractionTitle: TextView
    private lateinit var colorExtractionHint: TextView
    private lateinit var colorExtractionSwitch: MaterialSwitch
    private lateinit var monetDynamicSwitch: MaterialSwitch

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

    private var isBindingUi = false
    private var isTextSizeSliderUpdating = false
    private var isMultiLineTextSizeUpdating = false
    private var isCustomAodTextSizeUpdating = false
    private var isCustomAodWidthUpdating = false
    private var selectedPresetColor: Int = AodColorPresets.defaultPresetColor()

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
    }

    override fun onResume() {
        super.onResume()
        updateLockScreenSectionState()
        updateCustomAodSectionState()
        updateMultiLineDependentUi()
        refreshPreview()
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
        multiLineCountGroup = view.findViewById(R.id.multi_line_count_group)
        multiLineTranslationRow = view.findViewById(R.id.multi_line_translation_row)
        switchMultiLineShowTranslation = view.findViewById(R.id.switch_multi_line_show_translation)
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
        backgroundSection = view.findViewById(R.id.background_card)
        backgroundTitle = view.findViewById(R.id.background_title)
        backgroundHint = view.findViewById(R.id.background_hint)
        backgroundGroup = view.findViewById(R.id.background_group)
        backgroundDefault = view.findViewById(R.id.background_default)
        backgroundBlack = view.findViewById(R.id.background_black)
        backgroundWhite = view.findViewById(R.id.background_white)
        colorExtractionSection = view.findViewById(R.id.color_extraction_card)
        colorExtractionTitle = view.findViewById(R.id.color_extraction_title)
        colorExtractionHint = view.findViewById(R.id.color_extraction_hint)
        colorExtractionSwitch = view.findViewById(R.id.color_extraction_switch)
        monetDynamicSwitch = view.findViewById(R.id.monet_dynamic_switch)

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
            multiLineCountGroup,
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

    private fun bindUiFromPreferences() {
        isBindingUi = true

        switchSwapLyricTranslation.isChecked = FocusPreferences.isSwapLyricTranslation(requireContext())
        switchSingleLineOnly.isChecked = FocusPreferences.isSingleLineOnly(requireContext())
        switchMultiLineLyrics.isChecked = FocusPreferences.isMultiLineLyrics(requireContext())
        switchMultiLineShowTranslation.isChecked =
            FocusPreferences.isMultiLineShowTranslation(requireContext())
        multiLineCountGroup.check(
            when (FocusPreferences.getMultiLineLineCount(requireContext())) {
                4 -> R.id.multi_line_count_4
                6 -> R.id.multi_line_count_6
                else -> R.id.multi_line_count_8
            }
        )
        bindMultiLineTextSizeSlider(FocusPreferences.getMultiLineTextSize(requireContext()))

        bindTextSizeSlider(FocusPreferences.getLyricTextSize(requireContext()))

        val textColorId = if (FocusPreferences.getLyricTextColor(requireContext()) == FocusPreferences.TEXT_COLOR_BLACK) {
            R.id.text_color_black
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
        backgroundGroup.check(
            when (FocusPreferences.getFocusBackground(requireContext())) {
                FocusPreferences.BACKGROUND_BLACK -> R.id.background_black
                FocusPreferences.BACKGROUND_WHITE -> R.id.background_white
                else -> R.id.background_default
            }
        )

        monetDynamicSwitch.isChecked = FocusPreferences.isMonetDynamicColorEnabled(requireContext())
        colorExtractionSwitch.isChecked = FocusPreferences.isTextColorExtractionEnabled(requireContext())

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
    }

    private fun updateCustomAodSectionState() {
        val customAodEnabled = FocusPreferences.isCustomAodLayout(requireContext())
        val alpha = if (customAodEnabled) 1f else 0.38f

        customAodSectionHint.visibility = if (customAodEnabled) View.GONE else View.VISIBLE
        customAodStyleCard.alpha = if (customAodEnabled) 1f else 0.72f
        customAodControls.forEach { control ->
            control.isEnabled = customAodEnabled
            control.alpha = alpha
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

        multiLineCountRow.visibility = if (multiLineEnabled) View.VISIBLE else View.GONE
        multiLineTranslationRow.visibility = if (multiLineEnabled) View.VISIBLE else View.GONE
        multiLineTextSizeRow.visibility = if (multiLineEnabled) View.VISIBLE else View.GONE
        val multiLineInteractive = lockScreenInteractive && multiLineEnabled
        switchMultiLineShowTranslation.isEnabled = multiLineInteractive
        switchMultiLineShowTranslation.alpha = if (multiLineInteractive) 1f else 0.38f
        multiLineCountGroup.isEnabled = multiLineInteractive
        multiLineCountGroup.alpha = if (multiLineInteractive) 1f else 0.38f
        sliderMultiLineTextSize.isEnabled = multiLineInteractive
        sliderMultiLineTextSize.alpha = if (multiLineInteractive) 1f else 0.38f
        tvMultiLineTextSizeLabel.alpha = if (multiLineInteractive) 1f else 0.38f

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
        val manualTextEnabled = !monetEnabled && !textExtractionEnabled

        setSectionEnabled(
            section = textColorSection,
            title = textColorTitle,
            hint = textColorHint,
            hintText = when {
                monetEnabled -> "Monet 动态取色已接管文字颜色"
                textExtractionEnabled -> "通知文字取色已接管文字颜色"
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
                monetEnabled -> "Monet 动态取色已接管焦点通知背景"
                else -> null
            },
            enabled = !monetEnabled,
            controls = listOf(backgroundGroup, backgroundDefault, backgroundBlack, backgroundWhite)
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
        return FocusPreferences.isMonetDynamicColorEnabled(requireContext()) ||
            FocusPreferences.isTextColorExtractionEnabled(requireContext())
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
        multiLineCountGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
            if (FocusPreferences.isCustomAodLayout(requireContext()) ||
                !FocusPreferences.isMultiLineLyrics(requireContext())
            ) {
                return@addOnButtonCheckedListener
            }
            val count = when (checkedId) {
                R.id.multi_line_count_4 -> 4
                R.id.multi_line_count_6 -> 6
                else -> 8
            }
            FocusPreferences.setMultiLineLineCount(requireContext(), count)
            notifyStyleChanged()
        }

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
                else -> FocusPreferences.TEXT_COLOR_WHITE
            }
            FocusPreferences.setLyricTextColor(requireContext(), color)
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

        backgroundGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked || FocusPreferences.isMonetDynamicColorEnabled(requireContext())) {
                return@addOnButtonCheckedListener
            }
            val background = when (checkedId) {
                R.id.background_black -> FocusPreferences.BACKGROUND_BLACK
                R.id.background_white -> FocusPreferences.BACKGROUND_WHITE
                else -> FocusPreferences.BACKGROUND_DEFAULT
            }
            FocusPreferences.setFocusBackground(requireContext(), background)
            notifyStyleChanged()
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

        colorExtractionSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingUi || FocusPreferences.isMonetDynamicColorEnabled(requireContext())) return@setOnCheckedChangeListener
            FocusPreferences.setTextColorExtractionEnabled(requireContext(), isChecked)
            if (!isChecked) {
                FocusPreferences.clearExtractedTextColor(requireContext())
            }
            updateDynamicColorUi()
            notifyStyleChanged()
        }

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
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_color_picker, null)
        val preview = dialogView.findViewById<View>(R.id.color_preview)
        val sliderR = dialogView.findViewById<Slider>(R.id.slider_color_r)
        val sliderG = dialogView.findViewById<Slider>(R.id.slider_color_g)
        val sliderB = dialogView.findViewById<Slider>(R.id.slider_color_b)
        val labelR = dialogView.findViewById<TextView>(R.id.label_color_r)
        val labelG = dialogView.findViewById<TextView>(R.id.label_color_g)
        val labelB = dialogView.findViewById<TextView>(R.id.label_color_b)

        fun refreshPreview() {
            val color = Color.rgb(sliderR.value.toInt(), sliderG.value.toInt(), sliderB.value.toInt())
            preview.setBackgroundDrawable(chipDrawable(color, true))
            labelR.text = "R ${sliderR.value.toInt()}"
            labelG.text = "G ${sliderG.value.toInt()}"
            labelB.text = "B ${sliderB.value.toInt()}"
        }

        sliderR.value = Color.red(selectedPresetColor).toFloat()
        sliderG.value = Color.green(selectedPresetColor).toFloat()
        sliderB.value = Color.blue(selectedPresetColor).toFloat()
        refreshPreview()

        val listener = Slider.OnChangeListener { _, _, _ -> refreshPreview() }
        sliderR.addOnChangeListener(listener)
        sliderG.addOnChangeListener(listener)
        sliderB.addOnChangeListener(listener)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("自定义颜色")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val color = Color.rgb(sliderR.value.toInt(), sliderG.value.toInt(), sliderB.value.toInt())
                FocusPreferences.setCustomAodColorMode(requireContext(), FocusPreferences.CUSTOM_AOD_COLOR_PRESET)
                FocusPreferences.setCustomAodPresetColor(requireContext(), color)
                customAodColorModeGroup.check(R.id.custom_aod_color_preset)
                selectPresetColor(color)
                updateCustomAodColorUi()
                notifyStyleChanged()
            }
            .setNegativeButton("取消", null)
            .show()
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
        previewHeader.setOnClickListener { togglePreview() }
        // 默认展开
        previewContent.visibility = View.VISIBLE
        previewExpandIcon.rotation = 180f
        previewBound = true
    }

    private fun togglePreview() {
        previewExpanded = !previewExpanded
        if (previewExpanded) {
            previewContent.visibility = View.VISIBLE
            previewExpandIcon.animate().rotation(180f).setDuration(200).start()
            refreshPreview()
        } else {
            previewContent.visibility = View.GONE
            previewExpandIcon.animate().rotation(0f).setDuration(200).start()
        }
    }

    private fun refreshPreview() {
        if (!previewBound) return
        val ctx = requireContext()
        val isCustomAod = FocusPreferences.isCustomAodLayout(ctx)
        previewModeLabel.text = if (isCustomAod) "\u4e07\u8c61\u606f\u5c4f AOD" else "\u9501\u5c4f\u901a\u77e5"
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
        val extractedColor = if (monetEnabled || textExtractionEnabled) {
            FocusPreferences.getExtractedTextColor(ctx)
        } else null
        val extractedBgColor = FocusPreferences.getExtractedBgColor(ctx)
        val background = FocusPreferences.getFocusBackground(ctx)
        val textColor = FocusPreferences.getLyricTextColor(ctx)

        when {
            monetEnabled && extractedColor != null && extractedBgColor != null -> {
                val primary = extractedColor
                val secondary = AlbumColorExtractor.ensureContrast(
                    AlbumColorExtractor.blendSecondary(extractedColor, extractedBgColor),
                    extractedBgColor,
                    3.0
                )
                return Triple(primary, secondary, extractedBgColor)
            }
            textExtractionEnabled && extractedColor != null -> {
                val (primary, secondary) = AlbumColorExtractor.resolveTextColors(
                    accent = extractedColor,
                    backgroundEstimate = extractedBgColor ?: android.graphics.Color.GRAY,
                    backgroundMode = background
                )
                val bg = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> android.graphics.Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> android.graphics.Color.WHITE
                    else -> null
                }
                return Triple(primary, secondary, bg)
            }
            textColor == "black" -> {
                val bg = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> android.graphics.Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> android.graphics.Color.WHITE
                    else -> null
                }
                return Triple(android.graphics.Color.BLACK, 0xFF333333.toInt(), bg)
            }
            else -> {
                val bg = when (background) {
                    FocusPreferences.BACKGROUND_BLACK -> android.graphics.Color.BLACK
                    FocusPreferences.BACKGROUND_WHITE -> android.graphics.Color.WHITE
                    else -> null
                }
                return Triple(android.graphics.Color.WHITE, 0xFFE0E0E0.toInt(), bg)
            }
        }
    }

    private fun refreshAodPreview(ctx: android.content.Context, state: LyricService.PreviewState, hasLyric: Boolean) {
        previewRoot.setBackgroundColor(android.graphics.Color.BLACK)
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
        previewSecond.textSize = textSize * 0.78f
        previewSecond.setTextColor(secondaryColor)
        previewSecond.gravity = gravity
        previewSecond.minLines = translationMaxLines
        previewSecond.maxLines = translationMaxLines
    }

    private fun refreshLockScreenPreview(ctx: android.content.Context, state: LyricService.PreviewState, hasLyric: Boolean) {
        // 锁屏样式不显示歌曲信息
        previewSongRow.visibility = View.GONE

        val multiLine = FocusPreferences.isMultiLineLyrics(ctx)
        val singleLineOnly = FocusPreferences.isSingleLineOnly(ctx)
        val swapLyricTranslation = FocusPreferences.isSwapLyricTranslation(ctx)
        val textSize = FocusPreferences.getLyricTextSize(ctx)
        val gravity = gravityToInt(FocusPreferences.getLyricGravity(ctx))
        val lyricMaxLines = FocusPreferences.getLyricMaxLines(ctx)
        val translationMaxLines = FocusPreferences.getTranslationMaxLines(ctx)
        val background = FocusPreferences.getFocusBackground(ctx)
        val (primaryColor, secondaryColor, extractedBg) = resolvePreviewColors(ctx)

        // 背景：Monet 取色时优先用提取的背景色，否则用手动设置
        previewRoot.setBackgroundColor(
            if (extractedBg != null) extractedBg
            else when (background) {
                FocusPreferences.BACKGROUND_BLACK -> android.graphics.Color.BLACK
                FocusPreferences.BACKGROUND_WHITE -> android.graphics.Color.WHITE
                else -> 0xFF2A2A2A.toInt()
            }
        )

        // 重置 padding
        previewLyric.setPadding(0, previewLyric.paddingTop, 0, previewLyric.paddingBottom)
        previewSecond.setPadding(0, previewSecond.paddingTop, 0, previewSecond.paddingBottom)

        if (multiLine) {
            previewLyric.visibility = View.GONE
            previewSecond.visibility = View.GONE
            previewMultiLines.visibility = View.VISIBLE

            val count = FocusPreferences.getMultiLineLineCount(ctx)
            val mlTextSize = FocusPreferences.getMultiLineTextSize(ctx)
            val showTranslation = FocusPreferences.isMultiLineShowTranslation(ctx)
            val realLines = if (hasLyric && state.nextLyricLines.isNotEmpty()) state.nextLyricLines else emptyList()
            val realTranslations = if (hasLyric && state.nextLyricTranslations.isNotEmpty()) state.nextLyricTranslations else emptyList()
            val origins = if (realLines.size >= 4) {
                realLines.take(4)
            } else if (realLines.isNotEmpty()) {
                (realLines + List(4 - realLines.size) { "\u6b4c\u8bcd \u7b2c${realLines.size + it + 1}\u53e5" }).take(4)
            } else {
                listOf("\u6b4c\u8bcd \u7b2c\u4e00\u53e5", "\u6b4c\u8bcd \u7b2c\u4e8c\u53e5", "\u6b4c\u8bcd \u7b2c\u4e09\u53e5", "\u6b4c\u8bcd \u7b2c\u56db\u53e5")
            }
            val trans = if (realTranslations.size >= 4) {
                realTranslations.take(4).map { it.ifBlank { "" } }
            } else if (realTranslations.isNotEmpty()) {
                (realTranslations + List(4 - realTranslations.size) { "" }).take(4)
            } else {
                listOf("\u7ffb\u8bd1 \u7b2c\u4e00\u53e5", "\u7ffb\u8bd1 \u7b2c\u4e8c\u53e5", "\u7ffb\u8bd1 \u7b2c\u4e09\u53e5", "\u7ffb\u8bd1 \u7b2c\u56db\u53e5")
            }
            for (i in 0 until 8) {
                val tv = previewMultiTextViews[i]
                if (i < count) {
                    val isTranslationSlot = showTranslation && i % 2 == 1
                    val pairIdx = i / 2
                    val swapped = showTranslation && swapLyricTranslation
                    val text = if (isTranslationSlot) {
                        if (swapped) origins[pairIdx.coerceAtMost(3)] else trans[pairIdx.coerceAtMost(3)]
                    } else {
                        if (swapped && showTranslation) trans[pairIdx.coerceAtMost(3)] else origins[pairIdx.coerceAtMost(3)]
                    }
                    if (text.isBlank()) {
                        tv.visibility = View.GONE
                    } else {
                        tv.visibility = View.VISIBLE
                        tv.text = text
                        tv.textSize = mlTextSize
                        tv.setTextColor(if (isTranslationSlot) secondaryColor else primaryColor)
                        tv.gravity = gravity
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
                previewSecond.textSize = textSize * 0.78f
                previewSecond.setTextColor(secondaryColor)
                previewSecond.gravity = gravity
                previewSecond.minLines = translationMaxLines
                previewSecond.maxLines = translationMaxLines
            }
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
