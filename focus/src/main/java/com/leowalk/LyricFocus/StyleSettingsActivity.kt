package com.leowalk.LyricFocus

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class StyleSettingsActivity : AppCompatActivity() {

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

    private var isBindingUi = false
    private var isTextSizeSliderUpdating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_style_settings)
        setupWindowInsets()
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

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

        bindUiFromPreferences()
        setupListeners()
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

    private fun bindUiFromPreferences() {
        isBindingUi = true

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

        updateDynamicColorUi()

        isBindingUi = false
    }

    private fun updateDynamicColorUi() {
        val monetEnabled = FocusPreferences.isMonetDynamicColorEnabled(this)
        val textExtractionEnabled = FocusPreferences.isTextColorExtractionEnabled(this)
        val manualTextEnabled = !monetEnabled && !textExtractionEnabled

        setSectionEnabled(
            card = textColorCard,
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
            card = backgroundCard,
            title = backgroundTitle,
            hint = backgroundHint,
            hintText = if (monetEnabled) "Monet 动态取色已接管焦点通知背景" else null,
            enabled = !monetEnabled,
            controls = listOf(backgroundGroup, backgroundDefault, backgroundBlack, backgroundWhite)
        )

        setSectionEnabled(
            card = colorExtractionCard,
            title = colorExtractionTitle,
            hint = colorExtractionHint,
            hintText = if (monetEnabled) "Monet 动态取色已包含文字取色" else null,
            enabled = !monetEnabled,
            controls = listOf(colorExtractionSwitch)
        )
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

    private fun formatTextSizeLabel(sizeSp: Float): String = "${sizeSp.roundToInt()} sp"

    private fun isManualTextColorLocked(): Boolean {
        return FocusPreferences.isMonetDynamicColorEnabled(this) ||
            FocusPreferences.isTextColorExtractionEnabled(this)
    }

    private fun setupListeners() {
        sliderTextSize.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || isTextSizeSliderUpdating) return@addOnChangeListener
            tvTextSizeLabel.text = formatTextSizeLabel(value)
        }
        sliderTextSize.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                val normalized = slider.value.coerceIn(
                    FocusPreferences.MIN_LYRIC_TEXT_SIZE_SP,
                    FocusPreferences.MAX_LYRIC_TEXT_SIZE_SP
                )
                FocusPreferences.setLyricTextSize(this@StyleSettingsActivity, normalized)
                bindTextSizeSlider(normalized)
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
    }

    private fun notifyStyleChanged() {
        FocusPreferences.notifyStyleSettingsChanged(this)
    }
}
