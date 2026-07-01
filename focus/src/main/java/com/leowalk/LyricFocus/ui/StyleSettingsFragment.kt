package com.leowalk.LyricFocus.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
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
import com.leowalk.LyricFocus.util.AodColorPresets
import kotlin.math.roundToInt

class StyleSettingsFragment : Fragment(R.layout.activity_style_settings) {

    private lateinit var switchSwapLyricTranslation: MaterialSwitch
    private lateinit var switchSingleLineOnly: MaterialSwitch
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

    private val lockScreenControls = mutableListOf<View>()
    private val customAodControls = mutableListOf<View>()
    private val colorPresetViews = mutableListOf<View>()

    private var isBindingUi = false
    private var isTextSizeSliderUpdating = false
    private var isCustomAodTextSizeUpdating = false
    private var isCustomAodWidthUpdating = false
    private var selectedPresetColor: Int = AodColorPresets.defaultPresetColor()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.app_bar).visibility = View.GONE
        setupContentInsets(view)
        bindViews(view)
        setupColorPresetGrid()
        bindUiFromPreferences()
        setupListeners()
        updateLockScreenSectionState()
        updateCustomAodSectionState()
        updateCustomAodColorUi()
    }

    override fun onResume() {
        super.onResume()
        updateLockScreenSectionState()
        updateCustomAodSectionState()
    }

    private fun bindViews(view: View) {
        switchSwapLyricTranslation = view.findViewById(R.id.switch_swap_lyric_translation)
        switchSingleLineOnly = view.findViewById(R.id.switch_single_line_only)
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

        lockScreenControls += listOf(
            sliderTextSize,
            textColorGroup,
            textColorWhite,
            textColorBlack,
            lyricLinesGroup,
            translationLinesGroup,
            gravityGroup,
            backgroundGroup,
            backgroundDefault,
            backgroundBlack,
            backgroundWhite
        )
        customAodControls += listOf(
            sliderCustomAodTextSize,
            sliderCustomAodWidth,
            customAodSongInfoGroup,
            customAodSongInfoGroupRow2,
            customAodLyricLinesGroup,
            customAodTranslationLinesGroup,
            customAodGravityGroup,
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
            FocusPreferences.setSingleLineOnly(requireContext(), checked)
            notifyStyleChanged()
        }

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
            FocusPreferences.setLyricMaxLines(
                requireContext(),
                if (checkedId == R.id.lyric_lines_1) 1 else 2
            )
            notifyStyleChanged()
        }

        translationLinesGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
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

    private fun notifyStyleChanged() {
        FocusPreferences.notifyStyleSettingsChanged(requireContext())
    }
}
