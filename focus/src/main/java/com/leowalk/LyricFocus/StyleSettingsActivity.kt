package com.leowalk.LyricFocus

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.leowalk.LyricFocus.util.AodColorPresets
import kotlin.math.roundToInt

class StyleSettingsActivity : AppCompatActivity() {

    private lateinit var switchSwapLyricTranslation: MaterialSwitch
    private lateinit var switchSingleLineOnly: MaterialSwitch
    private lateinit var lockScreenSection: LinearLayout
    private lateinit var lockScreenSectionHint: TextView
    private lateinit var customAodSection: LinearLayout
    private lateinit var customAodSectionHint: TextView
    private lateinit var customAodStyleCard: MaterialCardView

    private lateinit var sliderTextSize: Slider
    private lateinit var tvTextSizeLabel: TextView
    private lateinit var textColorCard: MaterialCardView
    private lateinit var textColorTitle: TextView
    private lateinit var textColorHint: TextView
    private lateinit var textColorGroup: MaterialButtonToggleGroup
    private lateinit var textColorWhite: MaterialButton
    private lateinit var textColorBlack: MaterialButton
    private lateinit var backgroundCard: MaterialCardView
    private lateinit var backgroundTitle: TextView
    private lateinit var backgroundHint: TextView
    private lateinit var backgroundGroup: MaterialButtonToggleGroup
    private lateinit var backgroundDefault: MaterialButton
    private lateinit var backgroundBlack: MaterialButton
    private lateinit var backgroundWhite: MaterialButton
    private lateinit var colorExtractionCard: MaterialCardView
    private lateinit var colorExtractionTitle: TextView
    private lateinit var colorExtractionHint: TextView
    private lateinit var colorExtractionSwitch: MaterialSwitch
    private lateinit var monetDynamicSwitch: MaterialSwitch

    private lateinit var sliderCustomAodTextSize: Slider
    private lateinit var tvCustomAodTextSizeLabel: TextView
    private lateinit var sliderCustomAodWidth: Slider
    private lateinit var tvCustomAodWidthLabel: TextView
    private lateinit var customAodColorModeGroup: MaterialButtonToggleGroup
    private lateinit var customAodColorPaletteSection: LinearLayout
    private lateinit var customAodColorPalette: GridLayout
    private lateinit var btnCustomAodPickColor: MaterialButton

    private val lockScreenCards = mutableListOf<MaterialCardView>()
    private val lockScreenControls = mutableListOf<View>()
    private val customAodControls = mutableListOf<View>()
    private val colorPresetViews = mutableListOf<View>()

    private var isBindingUi = false
    private var isTextSizeSliderUpdating = false
    private var isCustomAodTextSizeUpdating = false
    private var isCustomAodWidthUpdating = false
    private var selectedPresetColor: Int = AodColorPresets.defaultPresetColor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_style_settings)
        setupWindowInsets()
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        bindViews()
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

    private fun bindViews() {
        switchSwapLyricTranslation = findViewById(R.id.switch_swap_lyric_translation)
        switchSingleLineOnly = findViewById(R.id.switch_single_line_only)
        lockScreenSection = findViewById(R.id.lock_screen_style_section)
        lockScreenSectionHint = findViewById(R.id.lock_screen_section_hint)
        customAodSection = findViewById(R.id.custom_aod_style_section)
        customAodSectionHint = findViewById(R.id.custom_aod_section_hint)
        customAodStyleCard = findViewById(R.id.custom_aod_style_card)

        sliderTextSize = findViewById(R.id.slider_text_size)
        tvTextSizeLabel = findViewById(R.id.text_size_label)
        textColorCard = findViewById(R.id.text_color_card)
        textColorTitle = findViewById(R.id.text_color_title)
        textColorHint = findViewById(R.id.text_color_hint)
        textColorGroup = findViewById(R.id.text_color_group)
        textColorWhite = findViewById(R.id.text_color_white)
        textColorBlack = findViewById(R.id.text_color_black)
        backgroundCard = findViewById(R.id.background_card)
        backgroundTitle = findViewById(R.id.background_title)
        backgroundHint = findViewById(R.id.background_hint)
        backgroundGroup = findViewById(R.id.background_group)
        backgroundDefault = findViewById(R.id.background_default)
        backgroundBlack = findViewById(R.id.background_black)
        backgroundWhite = findViewById(R.id.background_white)
        colorExtractionCard = findViewById(R.id.color_extraction_card)
        colorExtractionTitle = findViewById(R.id.color_extraction_title)
        colorExtractionHint = findViewById(R.id.color_extraction_hint)
        colorExtractionSwitch = findViewById(R.id.color_extraction_switch)
        monetDynamicSwitch = findViewById(R.id.monet_dynamic_switch)

        sliderCustomAodTextSize = findViewById(R.id.slider_custom_aod_text_size)
        tvCustomAodTextSizeLabel = findViewById(R.id.custom_aod_text_size_label)
        sliderCustomAodWidth = findViewById(R.id.slider_custom_aod_width)
        tvCustomAodWidthLabel = findViewById(R.id.custom_aod_width_label)
        customAodColorModeGroup = findViewById(R.id.custom_aod_color_mode_group)
        customAodColorPaletteSection = findViewById(R.id.custom_aod_color_palette_section)
        customAodColorPalette = findViewById(R.id.custom_aod_color_palette)
        btnCustomAodPickColor = findViewById(R.id.btn_custom_aod_pick_color)

        lockScreenCards += listOf(
            findViewById(R.id.text_size_card),
            findViewById(R.id.text_color_card),
            findViewById(R.id.lines_card),
            findViewById(R.id.gravity_card),
            findViewById(R.id.background_card),
            findViewById(R.id.monet_card),
            findViewById(R.id.color_extraction_card)
        )
        lockScreenControls += listOf(
            sliderTextSize,
            textColorGroup,
            textColorWhite,
            textColorBlack,
            findViewById(R.id.lyric_lines_group),
            findViewById(R.id.translation_lines_group),
            findViewById(R.id.gravity_group),
            backgroundGroup,
            backgroundDefault,
            backgroundBlack,
            backgroundWhite,
            monetDynamicSwitch,
            colorExtractionSwitch
        )
        customAodControls += listOf(
            sliderCustomAodTextSize,
            sliderCustomAodWidth,
            findViewById(R.id.custom_aod_lyric_lines_group),
            findViewById(R.id.custom_aod_translation_lines_group),
            findViewById(R.id.custom_aod_gravity_group),
            customAodColorModeGroup,
            btnCustomAodPickColor
        )
    }

    private fun setupWindowInsets() {
        val appBar = findViewById<View>(R.id.app_bar)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(appBar)

        val content = findViewById<View>(R.id.style_content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
    }

    private fun setupColorPresetGrid() {
        val size = resources.displayMetrics.density.times(36).toInt()
        val margin = resources.displayMetrics.density.times(4).toInt()
        colorPresetViews.clear()
        customAodColorPalette.removeAllViews()

        AodColorPresets.presets.forEach { preset ->
            val chip = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(margin, margin, margin, margin)
                }
                background = chipDrawable(preset.color, false)
                contentDescription = preset.name
                setOnClickListener {
                    selectPresetColor(preset.color)
                    FocusPreferences.setCustomAodColorMode(this@StyleSettingsActivity, FocusPreferences.CUSTOM_AOD_COLOR_PRESET)
                    FocusPreferences.setCustomAodPresetColor(this@StyleSettingsActivity, preset.color)
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

        switchSwapLyricTranslation.isChecked = FocusPreferences.isSwapLyricTranslation(this)
        switchSingleLineOnly.isChecked = FocusPreferences.isSingleLineOnly(this)

        bindTextSizeSlider(FocusPreferences.getLyricTextSize(this))

        val textColorId = if (FocusPreferences.getLyricTextColor(this) == FocusPreferences.TEXT_COLOR_BLACK) {
            R.id.text_color_black
        } else {
            R.id.text_color_white
        }
        textColorGroup.check(textColorId)

        findViewById<MaterialButtonToggleGroup>(R.id.lyric_lines_group).check(
            if (FocusPreferences.getLyricMaxLines(this) == 1) R.id.lyric_lines_1 else R.id.lyric_lines_2
        )
        findViewById<MaterialButtonToggleGroup>(R.id.translation_lines_group).check(
            if (FocusPreferences.getTranslationMaxLines(this) == 1) {
                R.id.translation_lines_1
            } else {
                R.id.translation_lines_2
            }
        )
        findViewById<MaterialButtonToggleGroup>(R.id.gravity_group).check(
            when (FocusPreferences.getLyricGravity(this)) {
                FocusPreferences.GRAVITY_LEFT -> R.id.gravity_left
                FocusPreferences.GRAVITY_RIGHT -> R.id.gravity_right
                else -> R.id.gravity_center
            }
        )
        backgroundGroup.check(
            when (FocusPreferences.getFocusBackground(this)) {
                FocusPreferences.BACKGROUND_BLACK -> R.id.background_black
                FocusPreferences.BACKGROUND_WHITE -> R.id.background_white
                else -> R.id.background_default
            }
        )

        monetDynamicSwitch.isChecked = FocusPreferences.isMonetDynamicColorEnabled(this)
        colorExtractionSwitch.isChecked = FocusPreferences.isTextColorExtractionEnabled(this)

        bindCustomAodTextSizeSlider(FocusPreferences.getCustomAodTextSize(this))
        bindCustomAodWidthSlider(FocusPreferences.getCustomAodLyricWidth(this))

        findViewById<MaterialButtonToggleGroup>(R.id.custom_aod_lyric_lines_group).check(
            if (FocusPreferences.getCustomAodLyricMaxLines(this) == 1) {
                R.id.custom_aod_lyric_lines_1
            } else {
                R.id.custom_aod_lyric_lines_2
            }
        )
        findViewById<MaterialButtonToggleGroup>(R.id.custom_aod_translation_lines_group).check(
            if (FocusPreferences.getCustomAodTranslationMaxLines(this) == 1) {
                R.id.custom_aod_translation_lines_1
            } else {
                R.id.custom_aod_translation_lines_2
            }
        )
        findViewById<MaterialButtonToggleGroup>(R.id.custom_aod_gravity_group).check(
            when (FocusPreferences.getCustomAodGravity(this)) {
                FocusPreferences.GRAVITY_LEFT -> R.id.custom_aod_gravity_left
                FocusPreferences.GRAVITY_RIGHT -> R.id.custom_aod_gravity_right
                else -> R.id.custom_aod_gravity_center
            }
        )

        val colorMode = FocusPreferences.getCustomAodColorMode(this)
        customAodColorModeGroup.check(
            when (colorMode) {
                FocusPreferences.CUSTOM_AOD_COLOR_ALBUM -> R.id.custom_aod_color_album
                FocusPreferences.CUSTOM_AOD_COLOR_PRESET -> R.id.custom_aod_color_preset
                else -> R.id.custom_aod_color_white
            }
        )
        selectedPresetColor = FocusPreferences.getCustomAodPresetColor(this)
        selectPresetColor(selectedPresetColor)

        updateDynamicColorUi()
        isBindingUi = false
    }

    private fun updateLockScreenSectionState() {
        val customAodEnabled = FocusPreferences.isCustomAodLayout(this)
        val enabled = !customAodEnabled
        val alpha = if (enabled) 1f else 0.38f

        lockScreenSectionHint.visibility = if (customAodEnabled) View.VISIBLE else View.GONE
        lockScreenSection.alpha = if (enabled) 1f else 0.72f
        lockScreenCards.forEach { card ->
            card.alpha = if (enabled) 1f else 0.72f
        }
        lockScreenControls.forEach { control ->
            control.isEnabled = enabled
            control.alpha = alpha
        }
        if (!enabled) {
            updateDynamicColorUi()
        }
    }

    private fun updateCustomAodSectionState() {
        val customAodEnabled = FocusPreferences.isCustomAodLayout(this)
        val alpha = if (customAodEnabled) 1f else 0.38f

        customAodSectionHint.visibility = if (customAodEnabled) View.GONE else View.VISIBLE
        customAodSection.alpha = if (customAodEnabled) 1f else 0.72f
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
        val monetEnabled = FocusPreferences.isMonetDynamicColorEnabled(this)
        val textExtractionEnabled = FocusPreferences.isTextColorExtractionEnabled(this)
        val manualTextEnabled = !monetEnabled && !textExtractionEnabled
        val lockEnabled = !FocusPreferences.isCustomAodLayout(this)

        setSectionEnabled(
            card = textColorCard,
            title = textColorTitle,
            hint = textColorHint,
            hintText = when {
                !lockEnabled -> "万象息屏 AOD 已开启，请使用下方专用样式"
                monetEnabled -> "Monet 动态取色已接管文字颜色"
                textExtractionEnabled -> "通知文字取色已接管文字颜色"
                else -> null
            },
            enabled = lockEnabled && manualTextEnabled,
            controls = listOf(textColorGroup, textColorWhite, textColorBlack)
        )

        setSectionEnabled(
            card = backgroundCard,
            title = backgroundTitle,
            hint = backgroundHint,
            hintText = when {
                !lockEnabled -> "万象息屏 AOD 已开启，请使用下方专用样式"
                monetEnabled -> "Monet 动态取色已接管焦点通知背景"
                else -> null
            },
            enabled = lockEnabled && !monetEnabled,
            controls = listOf(backgroundGroup, backgroundDefault, backgroundBlack, backgroundWhite)
        )

        setSectionEnabled(
            card = colorExtractionCard,
            title = colorExtractionTitle,
            hint = colorExtractionHint,
            hintText = when {
                !lockEnabled -> "万象息屏 AOD 已开启，请使用下方专用样式"
                monetEnabled -> "Monet 动态取色已包含文字取色"
                else -> null
            },
            enabled = lockEnabled && !monetEnabled,
            controls = listOf(colorExtractionSwitch)
        )
    }

    private fun updateCustomAodColorUi() {
        val mode = FocusPreferences.getCustomAodColorMode(this)
        val showPalette = mode == FocusPreferences.CUSTOM_AOD_COLOR_PRESET
        customAodColorPaletteSection.visibility = if (showPalette) View.VISIBLE else View.GONE
        if (showPalette) {
            selectPresetColor(FocusPreferences.getCustomAodPresetColor(this))
        }
    }

    private fun setSectionEnabled(
        card: MaterialCardView,
        title: TextView,
        hint: TextView,
        hintText: String?,
        enabled: Boolean,
        controls: List<View>
    ) {
        val alpha = if (enabled) 1f else 0.38f
        card.alpha = if (enabled) 1f else 0.72f
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
        return FocusPreferences.isMonetDynamicColorEnabled(this) ||
            FocusPreferences.isTextColorExtractionEnabled(this)
    }

    private fun setupListeners() {
        switchSwapLyricTranslation.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            FocusPreferences.setSwapLyricTranslation(this, checked)
            notifyStyleChanged()
        }
        switchSingleLineOnly.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            FocusPreferences.setSingleLineOnly(this, checked)
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
                FocusPreferences.setLyricTextSize(this@StyleSettingsActivity, normalized)
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
                FocusPreferences.setCustomAodTextSize(this@StyleSettingsActivity, normalized)
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
                FocusPreferences.setCustomAodLyricWidth(this@StyleSettingsActivity, normalized)
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
            FocusPreferences.setLyricTextColor(this, color)
            notifyStyleChanged()
        }

        findViewById<MaterialButtonToggleGroup>(R.id.lyric_lines_group)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
                FocusPreferences.setLyricMaxLines(
                    this,
                    if (checkedId == R.id.lyric_lines_1) 1 else 2
                )
                notifyStyleChanged()
            }

        findViewById<MaterialButtonToggleGroup>(R.id.translation_lines_group)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
                FocusPreferences.setTranslationMaxLines(
                    this,
                    if (checkedId == R.id.translation_lines_1) 1 else 2
                )
                notifyStyleChanged()
            }

        findViewById<MaterialButtonToggleGroup>(R.id.gravity_group)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
                val gravity = when (checkedId) {
                    R.id.gravity_left -> FocusPreferences.GRAVITY_LEFT
                    R.id.gravity_right -> FocusPreferences.GRAVITY_RIGHT
                    else -> FocusPreferences.GRAVITY_CENTER
                }
                FocusPreferences.setLyricGravity(this, gravity)
                notifyStyleChanged()
            }

        backgroundGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked || FocusPreferences.isMonetDynamicColorEnabled(this)) {
                return@addOnButtonCheckedListener
            }
            val background = when (checkedId) {
                R.id.background_black -> FocusPreferences.BACKGROUND_BLACK
                R.id.background_white -> FocusPreferences.BACKGROUND_WHITE
                else -> FocusPreferences.BACKGROUND_DEFAULT
            }
            FocusPreferences.setFocusBackground(this, background)
            notifyStyleChanged()
        }

        monetDynamicSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            FocusPreferences.setMonetDynamicColorEnabled(this, isChecked)
            if (isChecked) {
                FocusPreferences.setTextColorExtractionEnabled(this, false)
                colorExtractionSwitch.isChecked = false
            } else {
                FocusPreferences.clearExtractedTextColor(this)
            }
            updateDynamicColorUi()
            notifyStyleChanged()
        }

        colorExtractionSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingUi || FocusPreferences.isMonetDynamicColorEnabled(this)) return@setOnCheckedChangeListener
            FocusPreferences.setTextColorExtractionEnabled(this, isChecked)
            if (!isChecked) {
                FocusPreferences.clearExtractedTextColor(this)
            }
            updateDynamicColorUi()
            notifyStyleChanged()
        }

        findViewById<MaterialButtonToggleGroup>(R.id.custom_aod_lyric_lines_group)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
                FocusPreferences.setCustomAodLyricMaxLines(
                    this,
                    if (checkedId == R.id.custom_aod_lyric_lines_1) 1 else 2
                )
                notifyStyleChanged()
            }

        findViewById<MaterialButtonToggleGroup>(R.id.custom_aod_translation_lines_group)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
                FocusPreferences.setCustomAodTranslationMaxLines(
                    this,
                    if (checkedId == R.id.custom_aod_translation_lines_1) 1 else 2
                )
                notifyStyleChanged()
            }

        findViewById<MaterialButtonToggleGroup>(R.id.custom_aod_gravity_group)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
                val gravity = when (checkedId) {
                    R.id.custom_aod_gravity_left -> FocusPreferences.GRAVITY_LEFT
                    R.id.custom_aod_gravity_right -> FocusPreferences.GRAVITY_RIGHT
                    else -> FocusPreferences.GRAVITY_CENTER
                }
                FocusPreferences.setCustomAodGravity(this, gravity)
                notifyStyleChanged()
            }

        customAodColorModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isBindingUi || !isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.custom_aod_color_album -> FocusPreferences.CUSTOM_AOD_COLOR_ALBUM
                R.id.custom_aod_color_preset -> FocusPreferences.CUSTOM_AOD_COLOR_PRESET
                else -> FocusPreferences.CUSTOM_AOD_COLOR_WHITE
            }
            FocusPreferences.setCustomAodColorMode(this, mode)
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
            preview.background = chipDrawable(color, true)
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

        MaterialAlertDialogBuilder(this)
            .setTitle("自定义颜色")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val color = Color.rgb(sliderR.value.toInt(), sliderG.value.toInt(), sliderB.value.toInt())
                FocusPreferences.setCustomAodColorMode(this, FocusPreferences.CUSTOM_AOD_COLOR_PRESET)
                FocusPreferences.setCustomAodPresetColor(this, color)
                customAodColorModeGroup.check(R.id.custom_aod_color_preset)
                selectPresetColor(color)
                updateCustomAodColorUi()
                notifyStyleChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun notifyStyleChanged() {
        FocusPreferences.notifyStyleSettingsChanged(this)
    }
}
