package com.leowalk.LyricFocus

import android.content.Intent
import android.graphics.Color
import de.robv.android.xposed.XSharedPreferences

/**
 * SystemUI 进程内可用的样式快照。
 * 普通 SharedPreferences 无法跨进程及时同步，需结合 XSharedPreferences 读盘 + 广播 extras 更新。
 */
object FocusStyleSnapshot {

    const val EXTRA_STYLE_CHANGED = "style_changed"
    const val EXTRA_STYLE_TEXT_SIZE = "style_text_size"
    const val EXTRA_STYLE_TEXT_COLOR = "style_text_color"
    const val EXTRA_STYLE_LYRIC_MAX_LINES = "style_lyric_max_lines"
    const val EXTRA_STYLE_TRANSLATION_MAX_LINES = "style_translation_max_lines"
    const val EXTRA_STYLE_GRAVITY = "style_gravity"
    const val EXTRA_STYLE_BACKGROUND = "style_background"
    const val EXTRA_STYLE_MONET_DYNAMIC_COLOR = "style_monet_dynamic_color"
    const val EXTRA_STYLE_COLOR_EXTRACTION = "style_color_extraction"
    const val EXTRA_STYLE_EXTRACTED_COLOR = "style_extracted_color"
    const val EXTRA_STYLE_EXTRACTED_COLOR_SET = "style_extracted_color_set"
    const val EXTRA_STYLE_EXTRACTED_BG_COLOR = "style_extracted_bg_color"
    const val EXTRA_STYLE_EXTRACTED_BG_COLOR_SET = "style_extracted_bg_color_set"
    const val EXTRA_STYLE_STROKE_ENABLED = "style_stroke_enabled"
    const val EXTRA_STYLE_STROKE_WIDTH = "style_stroke_width"
    const val EXTRA_STYLE_STROKE_COLOR_MODE = "style_stroke_color_mode"

    @Volatile
    var textSizeSp: Float = FocusPreferences.DEFAULT_LYRIC_TEXT_SIZE_SP
        private set

    @Volatile
    var textColor: String = FocusPreferences.TEXT_COLOR_WHITE
        private set

    @Volatile
    var lyricMaxLines: Int = FocusPreferences.DEFAULT_LYRIC_MAX_LINES
        private set

    @Volatile
    var translationMaxLines: Int = FocusPreferences.DEFAULT_TRANSLATION_MAX_LINES
        private set

    @Volatile
    var gravity: String = FocusPreferences.GRAVITY_CENTER
        private set

    @Volatile
    var background: String = FocusPreferences.BACKGROUND_DEFAULT
        private set

    @Volatile
    var monetDynamicColorEnabled: Boolean = false
        private set

    @Volatile
    var textColorExtractionEnabled: Boolean = false
        private set

    @Volatile
    var colorExtractionEnabled: Boolean = false
        private set

    @Volatile
    var extractedTextColor: Int? = null
        private set

    @Volatile
    var extractedBgColor: Int? = null
        private set

    @Volatile
    var textStrokeEnabled: Boolean = false
        private set

    @Volatile
    var textStrokeWidth: Float = FocusPreferences.DEFAULT_TEXT_STROKE_WIDTH
        private set

    @Volatile
    var textStrokeColorMode: String = FocusPreferences.STROKE_COLOR_AUTO
        private set

    fun reloadFromDisk() {
        val prefs = crossProcessPrefs() ?: return
        if (!prefs.file.canRead()) return
        textSizeSp = prefs.getFloat(
            FocusPreferences.PREF_LYRIC_TEXT_SIZE,
            FocusPreferences.DEFAULT_LYRIC_TEXT_SIZE_SP
        ).coerceIn(
            FocusPreferences.MIN_LYRIC_TEXT_SIZE_SP,
            FocusPreferences.MAX_LYRIC_TEXT_SIZE_SP
        )
        textColor = prefs.getString(
            FocusPreferences.PREF_LYRIC_TEXT_COLOR,
            FocusPreferences.TEXT_COLOR_WHITE
        ) ?: FocusPreferences.TEXT_COLOR_WHITE
        lyricMaxLines = prefs.getInt(
            FocusPreferences.PREF_LYRIC_MAX_LINES,
            FocusPreferences.DEFAULT_LYRIC_MAX_LINES
        ).coerceIn(1, 3)
        translationMaxLines = prefs.getInt(
            FocusPreferences.PREF_TRANSLATION_MAX_LINES,
            FocusPreferences.DEFAULT_TRANSLATION_MAX_LINES
        ).coerceIn(1, 3)
        gravity = prefs.getString(
            FocusPreferences.PREF_LYRIC_GRAVITY,
            FocusPreferences.GRAVITY_CENTER
        ) ?: FocusPreferences.GRAVITY_CENTER
        background = prefs.getString(
            FocusPreferences.PREF_FOCUS_BACKGROUND,
            FocusPreferences.BACKGROUND_DEFAULT
        ) ?: FocusPreferences.BACKGROUND_DEFAULT
        monetDynamicColorEnabled = prefs.getBoolean(
            FocusPreferences.PREF_MONET_DYNAMIC_COLOR,
            false
        )
        textColorExtractionEnabled = prefs.getBoolean(
            FocusPreferences.PREF_LYRIC_COLOR_EXTRACTION,
            false
        )
        colorExtractionEnabled = monetDynamicColorEnabled || textColorExtractionEnabled
        extractedTextColor = if (prefs.contains(FocusPreferences.PREF_EXTRACTED_TEXT_COLOR)) {
            prefs.getInt(FocusPreferences.PREF_EXTRACTED_TEXT_COLOR, Color.WHITE)
        } else {
            null
        }
        extractedBgColor = if (prefs.contains(FocusPreferences.PREF_EXTRACTED_BG_COLOR)) {
            prefs.getInt(FocusPreferences.PREF_EXTRACTED_BG_COLOR, Color.GRAY)
        } else {
            null
        }
        textStrokeEnabled = prefs.getBoolean(FocusPreferences.PREF_TEXT_STROKE_ENABLED, false)
        textStrokeWidth = prefs.getFloat(
            FocusPreferences.PREF_TEXT_STROKE_WIDTH,
            FocusPreferences.DEFAULT_TEXT_STROKE_WIDTH
        ).coerceIn(
            FocusPreferences.MIN_TEXT_STROKE_WIDTH,
            FocusPreferences.MAX_TEXT_STROKE_WIDTH
        )
        textStrokeColorMode = prefs.getString(
            FocusPreferences.PREF_TEXT_STROKE_COLOR_MODE,
            FocusPreferences.STROKE_COLOR_AUTO
        ) ?: FocusPreferences.STROKE_COLOR_AUTO
    }

    fun applyFromIntent(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_STYLE_CHANGED, false)) return
        applyStyleFields(intent)
    }

    fun applyFromLyricIntent(intent: Intent) {
        if (!intent.hasExtra(EXTRA_STYLE_MONET_DYNAMIC_COLOR) &&
            !intent.hasExtra(EXTRA_STYLE_COLOR_EXTRACTION) &&
            !intent.hasExtra(EXTRA_STYLE_EXTRACTED_COLOR_SET) &&
            !intent.hasExtra(EXTRA_STYLE_EXTRACTED_BG_COLOR_SET)
        ) {
            return
        }
        applyStyleFields(intent)
    }

    private fun applyStyleFields(intent: Intent) {
        if (intent.hasExtra(EXTRA_STYLE_TEXT_SIZE)) {
            textSizeSp = intent.getFloatExtra(EXTRA_STYLE_TEXT_SIZE, textSizeSp)
                .coerceIn(
                    FocusPreferences.MIN_LYRIC_TEXT_SIZE_SP,
                    FocusPreferences.MAX_LYRIC_TEXT_SIZE_SP
                )
        }
        if (intent.hasExtra(EXTRA_STYLE_TEXT_COLOR)) {
            textColor = intent.getStringExtra(EXTRA_STYLE_TEXT_COLOR) ?: textColor
        }
        if (intent.hasExtra(EXTRA_STYLE_LYRIC_MAX_LINES)) {
            lyricMaxLines = intent.getIntExtra(EXTRA_STYLE_LYRIC_MAX_LINES, lyricMaxLines)
                .coerceIn(1, 3)
        }
        if (intent.hasExtra(EXTRA_STYLE_TRANSLATION_MAX_LINES)) {
            translationMaxLines = intent.getIntExtra(
                EXTRA_STYLE_TRANSLATION_MAX_LINES,
                translationMaxLines
            ).coerceIn(1, 3)
        }
        if (intent.hasExtra(EXTRA_STYLE_GRAVITY)) {
            gravity = intent.getStringExtra(EXTRA_STYLE_GRAVITY) ?: gravity
        }
        if (intent.hasExtra(EXTRA_STYLE_BACKGROUND)) {
            background = intent.getStringExtra(EXTRA_STYLE_BACKGROUND) ?: background
        }
        if (intent.hasExtra(EXTRA_STYLE_MONET_DYNAMIC_COLOR)) {
            monetDynamicColorEnabled = intent.getBooleanExtra(EXTRA_STYLE_MONET_DYNAMIC_COLOR, false)
        }
        if (intent.hasExtra(EXTRA_STYLE_COLOR_EXTRACTION)) {
            textColorExtractionEnabled = intent.getBooleanExtra(EXTRA_STYLE_COLOR_EXTRACTION, false)
        }
        colorExtractionEnabled = monetDynamicColorEnabled || textColorExtractionEnabled
        if (intent.hasExtra(EXTRA_STYLE_EXTRACTED_COLOR_SET)) {
            extractedTextColor = if (intent.getBooleanExtra(EXTRA_STYLE_EXTRACTED_COLOR_SET, false)) {
                intent.getIntExtra(EXTRA_STYLE_EXTRACTED_COLOR, Color.WHITE)
            } else {
                null
            }
        } else if (!colorExtractionEnabled) {
            extractedTextColor = null
        }
        if (intent.hasExtra(EXTRA_STYLE_EXTRACTED_BG_COLOR_SET)) {
            extractedBgColor = if (intent.getBooleanExtra(EXTRA_STYLE_EXTRACTED_BG_COLOR_SET, false)) {
                intent.getIntExtra(EXTRA_STYLE_EXTRACTED_BG_COLOR, Color.GRAY)
            } else {
                null
            }
        } else if (!colorExtractionEnabled || extractedTextColor == null) {
            extractedBgColor = null
        }
        if (intent.hasExtra(EXTRA_STYLE_STROKE_ENABLED)) {
            textStrokeEnabled = intent.getBooleanExtra(EXTRA_STYLE_STROKE_ENABLED, false)
        }
        if (intent.hasExtra(EXTRA_STYLE_STROKE_WIDTH)) {
            textStrokeWidth = intent.getFloatExtra(EXTRA_STYLE_STROKE_WIDTH, textStrokeWidth)
                .coerceIn(
                    FocusPreferences.MIN_TEXT_STROKE_WIDTH,
                    FocusPreferences.MAX_TEXT_STROKE_WIDTH
                )
        }
        if (intent.hasExtra(EXTRA_STYLE_STROKE_COLOR_MODE)) {
            textStrokeColorMode = intent.getStringExtra(EXTRA_STYLE_STROKE_COLOR_MODE) ?: textStrokeColorMode
        }
    }

    private fun crossProcessPrefs(): XSharedPreferences? {
        return try {
            XSharedPreferences(
                FocusPreferences.MODULE_PACKAGE,
                "${FocusPreferences.PREFS_NAME}.xml"
            ).apply { reload() }
        } catch (_: Throwable) {
            null
        }
    }
}
