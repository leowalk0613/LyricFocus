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
    const val EXTRA_STYLE_CUSTOM_AOD_LAYOUT = "style_custom_aod_layout"
    const val EXTRA_STYLE_SWAP_LYRIC_TRANSLATION = "style_swap_lyric_translation"
    const val EXTRA_STYLE_SINGLE_LINE_ONLY = "style_single_line_only"
    const val EXTRA_STYLE_CUSTOM_AOD_TEXT_SIZE = "style_custom_aod_text_size"
    const val EXTRA_STYLE_CUSTOM_AOD_LYRIC_WIDTH = "style_custom_aod_lyric_width"
    const val EXTRA_STYLE_CUSTOM_AOD_LYRIC_MAX_LINES = "style_custom_aod_lyric_max_lines"
    const val EXTRA_STYLE_CUSTOM_AOD_TRANSLATION_MAX_LINES = "style_custom_aod_translation_max_lines"
    const val EXTRA_STYLE_CUSTOM_AOD_COLOR_MODE = "style_custom_aod_color_mode"
    const val EXTRA_STYLE_CUSTOM_AOD_PRESET_COLOR = "style_custom_aod_preset_color"
    const val EXTRA_STYLE_CUSTOM_AOD_GRAVITY = "style_custom_aod_gravity"
    const val EXTRA_STYLE_CUSTOM_AOD_SONG_INFO = "style_custom_aod_song_info"
    const val EXTRA_STYLE_MONET_DYNAMIC_COLOR = "style_monet_dynamic_color"
    const val EXTRA_STYLE_COLOR_EXTRACTION = "style_color_extraction"
    const val EXTRA_STYLE_EXTRACTED_COLOR = "style_extracted_color"
    const val EXTRA_STYLE_EXTRACTED_COLOR_SET = "style_extracted_color_set"
    const val EXTRA_STYLE_EXTRACTED_BG_COLOR = "style_extracted_bg_color"
    const val EXTRA_STYLE_EXTRACTED_BG_COLOR_SET = "style_extracted_bg_color_set"

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
    var customAodLayout: Boolean = false
        private set

    @Volatile
    var swapLyricTranslation: Boolean = false
        private set

    @Volatile
    var singleLineOnly: Boolean = false
        private set

    @Volatile
    var customAodTextSizeSp: Float = FocusPreferences.DEFAULT_LYRIC_TEXT_SIZE_SP
        private set

    @Volatile
    var customAodLyricWidth: Int = FocusPreferences.DEFAULT_CUSTOM_AOD_LYRIC_WIDTH
        private set

    @Volatile
    var customAodLyricMaxLines: Int = FocusPreferences.DEFAULT_LYRIC_MAX_LINES
        private set

    @Volatile
    var customAodTranslationMaxLines: Int = FocusPreferences.DEFAULT_TRANSLATION_MAX_LINES
        private set

    @Volatile
    var customAodColorMode: String = FocusPreferences.CUSTOM_AOD_COLOR_WHITE
        private set

    @Volatile
    var customAodPresetColor: Int = com.leowalk.LyricFocus.util.AodColorPresets.defaultPresetColor()
        private set

    @Volatile
    var customAodGravity: String = FocusPreferences.GRAVITY_CENTER
        private set

    @Volatile
    var customAodSongInfo: String = FocusPreferences.CUSTOM_AOD_SONG_INFO_ALL
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
        customAodLayout = prefs.getBoolean(
            FocusPreferences.PREF_CUSTOM_AOD_LAYOUT,
            false
        )
        swapLyricTranslation = prefs.getBoolean(
            FocusPreferences.PREF_SWAP_LYRIC_TRANSLATION,
            false
        )
        singleLineOnly = prefs.getBoolean(
            FocusPreferences.PREF_SINGLE_LINE_ONLY,
            false
        )
        customAodTextSizeSp = prefs.getFloat(
            FocusPreferences.PREF_CUSTOM_AOD_TEXT_SIZE,
            FocusPreferences.DEFAULT_LYRIC_TEXT_SIZE_SP
        ).coerceIn(
            FocusPreferences.MIN_LYRIC_TEXT_SIZE_SP,
            FocusPreferences.MAX_LYRIC_TEXT_SIZE_SP
        )
        customAodLyricWidth = prefs.getInt(
            FocusPreferences.PREF_CUSTOM_AOD_LYRIC_WIDTH,
            FocusPreferences.DEFAULT_CUSTOM_AOD_LYRIC_WIDTH
        ).coerceIn(
            FocusPreferences.MIN_CUSTOM_AOD_LYRIC_WIDTH,
            FocusPreferences.MAX_CUSTOM_AOD_LYRIC_WIDTH
        )
        customAodLyricMaxLines = prefs.getInt(
            FocusPreferences.PREF_CUSTOM_AOD_LYRIC_MAX_LINES,
            FocusPreferences.DEFAULT_LYRIC_MAX_LINES
        ).coerceIn(1, 2)
        customAodTranslationMaxLines = prefs.getInt(
            FocusPreferences.PREF_CUSTOM_AOD_TRANSLATION_MAX_LINES,
            FocusPreferences.DEFAULT_TRANSLATION_MAX_LINES
        ).coerceIn(1, 2)
        customAodColorMode = prefs.getString(
            FocusPreferences.PREF_CUSTOM_AOD_COLOR_MODE,
            FocusPreferences.CUSTOM_AOD_COLOR_WHITE
        ) ?: FocusPreferences.CUSTOM_AOD_COLOR_WHITE
        customAodPresetColor = prefs.getInt(
            FocusPreferences.PREF_CUSTOM_AOD_PRESET_COLOR,
            com.leowalk.LyricFocus.util.AodColorPresets.defaultPresetColor()
        )
        customAodGravity = prefs.getString(
            FocusPreferences.PREF_CUSTOM_AOD_GRAVITY,
            FocusPreferences.GRAVITY_CENTER
        ) ?: FocusPreferences.GRAVITY_CENTER
        customAodSongInfo = prefs.getString(
            FocusPreferences.PREF_CUSTOM_AOD_SONG_INFO,
            FocusPreferences.CUSTOM_AOD_SONG_INFO_ALL
        ) ?: FocusPreferences.CUSTOM_AOD_SONG_INFO_ALL
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
        if (intent.hasExtra(EXTRA_STYLE_CUSTOM_AOD_LAYOUT)) {
            customAodLayout = intent.getBooleanExtra(EXTRA_STYLE_CUSTOM_AOD_LAYOUT, customAodLayout)
        }
        if (intent.hasExtra(EXTRA_STYLE_SWAP_LYRIC_TRANSLATION)) {
            swapLyricTranslation = intent.getBooleanExtra(
                EXTRA_STYLE_SWAP_LYRIC_TRANSLATION,
                swapLyricTranslation
            )
        }
        if (intent.hasExtra(EXTRA_STYLE_SINGLE_LINE_ONLY)) {
            singleLineOnly = intent.getBooleanExtra(EXTRA_STYLE_SINGLE_LINE_ONLY, singleLineOnly)
        }
        if (intent.hasExtra(EXTRA_STYLE_CUSTOM_AOD_TEXT_SIZE)) {
            customAodTextSizeSp = intent.getFloatExtra(EXTRA_STYLE_CUSTOM_AOD_TEXT_SIZE, customAodTextSizeSp)
                .coerceIn(
                    FocusPreferences.MIN_LYRIC_TEXT_SIZE_SP,
                    FocusPreferences.MAX_LYRIC_TEXT_SIZE_SP
                )
        }
        if (intent.hasExtra(EXTRA_STYLE_CUSTOM_AOD_LYRIC_WIDTH)) {
            customAodLyricWidth = intent.getIntExtra(EXTRA_STYLE_CUSTOM_AOD_LYRIC_WIDTH, customAodLyricWidth)
                .coerceIn(
                    FocusPreferences.MIN_CUSTOM_AOD_LYRIC_WIDTH,
                    FocusPreferences.MAX_CUSTOM_AOD_LYRIC_WIDTH
                )
        }
        if (intent.hasExtra(EXTRA_STYLE_CUSTOM_AOD_LYRIC_MAX_LINES)) {
            customAodLyricMaxLines = intent.getIntExtra(
                EXTRA_STYLE_CUSTOM_AOD_LYRIC_MAX_LINES,
                customAodLyricMaxLines
            ).coerceIn(1, 2)
        }
        if (intent.hasExtra(EXTRA_STYLE_CUSTOM_AOD_TRANSLATION_MAX_LINES)) {
            customAodTranslationMaxLines = intent.getIntExtra(
                EXTRA_STYLE_CUSTOM_AOD_TRANSLATION_MAX_LINES,
                customAodTranslationMaxLines
            ).coerceIn(1, 2)
        }
        if (intent.hasExtra(EXTRA_STYLE_CUSTOM_AOD_COLOR_MODE)) {
            customAodColorMode = intent.getStringExtra(EXTRA_STYLE_CUSTOM_AOD_COLOR_MODE)
                ?: customAodColorMode
        }
        if (intent.hasExtra(EXTRA_STYLE_CUSTOM_AOD_PRESET_COLOR)) {
            customAodPresetColor = intent.getIntExtra(
                EXTRA_STYLE_CUSTOM_AOD_PRESET_COLOR,
                customAodPresetColor
            )
        }
        if (intent.hasExtra(EXTRA_STYLE_CUSTOM_AOD_GRAVITY)) {
            customAodGravity = intent.getStringExtra(EXTRA_STYLE_CUSTOM_AOD_GRAVITY)
                ?: customAodGravity
        }
        if (intent.hasExtra(EXTRA_STYLE_CUSTOM_AOD_SONG_INFO)) {
            customAodSongInfo = intent.getStringExtra(EXTRA_STYLE_CUSTOM_AOD_SONG_INFO)
                ?: customAodSongInfo
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
