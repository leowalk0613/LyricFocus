package com.leowalk.LyricFocus.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.leowalk.LyricFocus.FocusPreferences
import kotlin.math.max
import kotlin.math.pow

object AlbumColorExtractor {

    // 提高最小对比度要求，确保文字更清晰
    private const val MIN_PRIMARY_CONTRAST = 4.5  // WCAG AA 标准
    private const val MIN_SECONDARY_CONTRAST = 3.5
    private const val TARGET_CONTRAST = 7.0  // WCAG AAA 标准作为目标

    data class LyricColors(
        val accent: Int,
        val backgroundEstimate: Int
    )

    data class MonetScheme(
        val background: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val accent: Int
    )

    fun extractMonetScheme(bitmap: Bitmap?): MonetScheme? {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        val palette = try {
            Palette.from(bitmap).generate()
        } catch (_: Throwable) {
            return null
        }

        val seedSwatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.dominantSwatch
            ?: return null

        val seed = seedSwatch.rgb
        val average = estimateAverageColor(bitmap) ?: seed
        val darkTheme = relativeLuminance(average) < 0.45
        val background = buildMonetSurface(palette, seed, darkTheme)
        val accentCandidate = pickOnSurfaceAccent(palette, seed, darkTheme)
        val primaryText = ensureContrast(accentCandidate, background, MIN_PRIMARY_CONTRAST)
        val secondaryText = ensureContrast(
            blendSecondary(primaryText, background),
            background,
            MIN_SECONDARY_CONTRAST
        )

        return MonetScheme(
            background = background,
            primaryText = primaryText,
            secondaryText = secondaryText,
            accent = seed
        )
    }

    fun extractLyricColors(bitmap: Bitmap?): LyricColors? {
        val accent = extractAccentColor(bitmap) ?: return null
        val backgroundEstimate = estimateAverageColor(bitmap) ?: Color.GRAY
        return LyricColors(accent, backgroundEstimate)
    }

    fun resolveTextColors(
        accent: Int,
        backgroundEstimate: Int,
        backgroundMode: String
    ): Pair<Int, Int> {
        val bg = when (backgroundMode) {
            FocusPreferences.BACKGROUND_BLACK -> Color.BLACK
            FocusPreferences.BACKGROUND_WHITE -> Color.WHITE
            else -> Color.BLACK  // 焦点通知实际使用系统深色背景
        }
        val primary = ensureContrast(accent, bg, MIN_PRIMARY_CONTRAST)
        val secondary = ensureContrast(blendSecondary(primary, bg), bg, MIN_SECONDARY_CONTRAST)
        
        // 如果取到黑色或接近黑色，强制改为灰白色
        val finalPrimary = avoidPureBlack(primary)
        val finalSecondary = avoidPureBlack(secondary)
        
        return finalPrimary to finalSecondary
    }

    /**
     * 如果颜色是黑色或接近黑色，改为灰白色（#E0E0E0）
     */
    private fun avoidPureBlack(color: Int): Int {
        val luminance = relativeLuminance(color)
        // 如果亮度低于 0.08（接近黑色），改为灰白色
        if (luminance < 0.08) {
            return Color.rgb(224, 224, 224)  // #E0E0E0
        }
        return color
    }

    fun extractAccentColor(bitmap: Bitmap?): Int? {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        extractWithPalette(bitmap)?.let { return it }
        return extractWithSampling(bitmap)
    }

    fun blendSecondary(primary: Int, background: Int = Color.BLACK): Int {
        val blend = if (relativeLuminance(background) > 0.45) Color.BLACK else Color.WHITE
        return blendColors(primary, blend, 0.35f)
    }

    fun ensureContrast(foreground: Int, background: Int, minRatio: Double = MIN_PRIMARY_CONTRAST): Int {
        val currentContrast = contrastRatio(foreground, background)
        
        // 如果已经达到目标对比度，直接返回
        if (currentContrast >= TARGET_CONTRAST) {
            return foreground
        }
        
        // 如果达到最小对比度但未达到目标，尝试进一步优化
        if (currentContrast >= minRatio) {
            // 尝试向更极端的方向调整，以达到更高的对比度
            val bgLuminance = relativeLuminance(background)
            val fgLuminance = relativeLuminance(foreground)
            
            // 如果背景是深色，尝试让文字更亮
            if (bgLuminance < 0.35 && fgLuminance > 0.5) {
                val brighter = blendColors(foreground, Color.WHITE, 0.25f)
                if (contrastRatio(brighter, background) > currentContrast) {
                    return brighter
                }
            }
            // 如果背景是浅色，尝试让文字更暗
            else if (bgLuminance > 0.65 && fgLuminance < 0.5) {
                val darker = blendColors(foreground, Color.BLACK, 0.25f)
                if (contrastRatio(darker, background) > currentContrast) {
                    return darker
                }
            }
            return foreground
        }

        // 对比度不足，需要大幅调整
        val lighten = relativeLuminance(background) < 0.45
        val target = if (lighten) Color.WHITE else Color.BLACK
        var ratio = 0.15f  // 从更高的比例开始
        while (ratio <= 1f) {
            val candidate = blendColors(foreground, target, ratio)
            if (contrastRatio(candidate, background) >= minRatio) {
                // 继续尝试更高的比例以达到更好的对比度
                var bestRatio = ratio
                var bestColor = candidate
                var testRatio = ratio + 0.05f
                while (testRatio <= 1f) {
                    val testColor = blendColors(foreground, target, testRatio)
                    if (contrastRatio(testColor, background) > contrastRatio(bestColor, background)) {
                        bestRatio = testRatio
                        bestColor = testColor
                    }
                    testRatio += 0.05f
                }
                return bestColor
            }
            ratio += 0.08f
        }
        return if (lighten) Color.WHITE else Color.BLACK
    }

    fun estimateAverageColor(bitmap: Bitmap?): Int? {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        try {
            val palette = Palette.from(bitmap).generate()
            palette.dominantSwatch?.rgb?.let { return it }
            palette.mutedSwatch?.rgb?.let { return it }
        } catch (_: Throwable) {
        }

        return sampleAverageColor(bitmap)
    }

    private fun extractWithPalette(bitmap: Bitmap): Int? {
        return try {
            val palette = Palette.from(bitmap).generate()
            val swatches = listOf(
                palette.vibrantSwatch,
                palette.lightVibrantSwatch,
                palette.darkVibrantSwatch,
                palette.mutedSwatch,
                palette.lightMutedSwatch,
                palette.darkMutedSwatch,
                palette.dominantSwatch
            )
            for (swatch in swatches) {
                if (swatch != null && swatch.population > 0) {
                    return swatch.rgb
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractWithSampling(bitmap: Bitmap): Int? {
        val sampleSize = 32
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, true)
        val colorScores = LinkedHashMap<Int, Float>()

        for (x in 0 until sampleSize) {
            for (y in 0 until sampleSize) {
                val pixel = scaled.getPixel(x, y)
                if (Color.alpha(pixel) < 128) continue

                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val maxChannel = max(max(red, green), blue)
                val minChannel = minOf(red, green, blue)
                val saturation = if (maxChannel == 0) 0f else (maxChannel - minChannel) / maxChannel.toFloat()
                val key = quantize(red, green, blue)
                val weight = 1f + saturation * 2f
                colorScores[key] = (colorScores[key] ?: 0f) + weight
            }
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        val best = colorScores.maxByOrNull { it.value }?.key ?: return null
        return Color.rgb(
            Color.red(best),
            Color.green(best),
            Color.blue(best)
        )
    }

    private fun sampleAverageColor(bitmap: Bitmap): Int? {
        val sampleSize = 16
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, true)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0

        for (x in 0 until sampleSize) {
            for (y in 0 until sampleSize) {
                val pixel = scaled.getPixel(x, y)
                if (Color.alpha(pixel) < 128) continue
                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
                count++
            }
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }
        if (count == 0) return null

        return Color.rgb(
            (red / count).toInt(),
            (green / count).toInt(),
            (blue / count).toInt()
        )
    }

    private fun buildMonetSurface(palette: Palette, seed: Int, darkTheme: Boolean): Int {
        return if (darkTheme) {
            val base = palette.darkMutedSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: blendColors(seed, Color.BLACK, 0.72f)
            val surface = blendColors(base, Color.BLACK, 0.32f)
            if (relativeLuminance(surface) > 0.22) {
                blendColors(surface, Color.BLACK, 0.35f)
            } else {
                surface
            }
        } else {
            val base = palette.lightMutedSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: blendColors(seed, Color.WHITE, 0.78f)
            val surface = blendColors(base, Color.WHITE, 0.28f)
            if (relativeLuminance(surface) < 0.78) {
                blendColors(surface, Color.WHITE, 0.25f)
            } else {
                surface
            }
        }
    }

    private fun pickOnSurfaceAccent(palette: Palette, seed: Int, darkTheme: Boolean): Int {
        return if (darkTheme) {
            palette.lightVibrantSwatch?.rgb
                ?: palette.vibrantSwatch?.rgb?.let { blendColors(it, Color.WHITE, 0.28f) }
                ?: blendColors(seed, Color.WHITE, 0.58f)
        } else {
            palette.darkVibrantSwatch?.rgb
                ?: palette.vibrantSwatch?.rgb?.let { blendColors(it, Color.BLACK, 0.28f) }
                ?: blendColors(seed, Color.BLACK, 0.42f)
        }
    }

    private fun quantize(red: Int, green: Int, blue: Int): Int {
        val qr = (red / 32) * 32
        val qg = (green / 32) * 32
        val qb = (blue / 32) * 32
        return Color.rgb(qr, qg, qb)
    }

    private fun contrastRatio(foreground: Int, background: Int): Double {
        val fg = relativeLuminance(foreground)
        val bg = relativeLuminance(background)
        val lighter = max(fg, bg)
        val darker = minOf(fg, bg)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                ((normalized + 0.055) / 1.055).pow(2.4)
            }
        }

        val r = channel(Color.red(color))
        val g = channel(Color.green(color))
        val b = channel(Color.blue(color))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        return Color.rgb(
            (Color.red(from) * inverse + Color.red(to) * ratio).toInt().coerceIn(0, 255),
            (Color.green(from) * inverse + Color.green(to) * ratio).toInt().coerceIn(0, 255),
            (Color.blue(from) * inverse + Color.blue(to) * ratio).toInt().coerceIn(0, 255)
        )
    }
}
