package com.leowalk.LyricFocus.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.leowalk.LyricFocus.FocusPreferences
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object AlbumColorExtractor {

    // 提高最小对比度要求，确保文字更清晰
    private const val MIN_PRIMARY_CONTRAST = 4.5  // WCAG AA 标准
    private const val MIN_PRIMARY_CONTRAST_SAFE = 5.5  // 关闭色彩模式时的保守阈值
    private const val MIN_SECONDARY_CONTRAST = 3.5
    private const val MIN_SECONDARY_CONTRAST_SAFE = 4.0
    private const val TARGET_CONTRAST = 7.0  // WCAG AAA 标准作为目标
    /** 白底白字 / 黑底黑字的最低亮度差 */
    private const val MIN_LUMINANCE_DELTA = 0.18

    data class LyricColors(
        val accent: Int,
        val backgroundEstimate: Int
    )

    data class DistinctColors(
        val background: Int,
        val primaryText: Int,
        val accent: Int
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

    fun extractDistinctColors(bitmap: Bitmap?): DistinctColors? {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }
        val palette = try {
            Palette.from(bitmap).generate()
        } catch (_: Throwable) {
            return null
        }

        val colors = mutableListOf<Int>()
        palette.vibrantSwatch?.rgb?.let { colors.add(it) }
        palette.lightVibrantSwatch?.rgb?.let { colors.add(it) }
        palette.darkVibrantSwatch?.rgb?.let { colors.add(it) }
        palette.mutedSwatch?.rgb?.let { colors.add(it) }
        palette.lightMutedSwatch?.rgb?.let { colors.add(it) }
        palette.darkMutedSwatch?.rgb?.let { colors.add(it) }
        palette.dominantSwatch?.rgb?.let { colors.add(it) }
        if (colors.size < 3) return null

            val (a, b, c) = pickThreeDistinct(colors)
            // 背景=最暗，主文字=中间亮度，强调色=饱和度最高的（不再用亮度排序）
            val sorted = listOf(a, b, c).sortedBy { relativeLuminance(it) }
            val bg = sorted.first()
            val mid = sorted[1]
            val bright = sorted[2]
            // 饱和度更高的作为 accent，另一个作为 text
            val acc = if (saturation(mid) >= saturation(bright)) mid else bright
            val txt = if (acc == mid) bright else mid
            val text = ensureContrast(txt, bg, 3.5)
            val accent = ensureContrast(acc, bg, 2.5)

            return DistinctColors(
                background = bg,
                primaryText = text,
                accent = accent
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
            else -> Color.BLACK
        }
        val primary = ensureContrast(accent, bg, MIN_PRIMARY_CONTRAST)
        val secondary = ensureContrast(blendSecondary(primary, bg), bg, MIN_SECONDARY_CONTRAST)

        val isDarkBackground = backgroundMode != FocusPreferences.BACKGROUND_WHITE
        val finalPrimary = guardSameColor(avoidPureColor(primary, isDarkBackground), bg)
        val finalSecondary = guardSameColor(avoidPureColor(secondary, isDarkBackground), bg)

        return finalPrimary to finalSecondary
    }

    /**
     * 避免纯色：
     * - 深色背景：黑色/接近黑色 -> 灰白色 (#E0E0E0)
     * - 浅色背景：白色/接近白色 -> 灰黑色 (#1F1F1F)
     */
    private fun avoidPureColor(color: Int, isDarkBackground: Boolean): Int {
        val luminance = relativeLuminance(color)
        
        if (isDarkBackground) {
            // 深色背景：避免黑色
            if (luminance < 0.08) {
                return Color.rgb(224, 224, 224)  // #E0E0E0 灰白色
            }
        } else {
            // 浅色背景：避免白色
            if (luminance > 0.92) {
                return Color.rgb(31, 31, 31)  // #1F1F1F 灰黑色
            }
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
        return blendColors(primary, blend, 0.22f)
    }

    fun ensureContrast(foreground: Int, background: Int, minRatio: Double = MIN_PRIMARY_CONTRAST): Int {
        val currentContrast = contrastRatio(foreground, background)
        val safeFg = guardSameColor(foreground, background)
        
        if (safeFg != foreground) return safeFg
        
        if (currentContrast >= TARGET_CONTRAST) {
            return foreground
        }
        
        if (currentContrast >= minRatio) {
            val bgLuminance = relativeLuminance(background)
            val fgLuminance = relativeLuminance(foreground)
            
            if (bgLuminance < 0.35 && fgLuminance > 0.5) {
                val brighter = blendColors(foreground, Color.WHITE, 0.25f)
                if (contrastRatio(brighter, background) > currentContrast) {
                    return brighter
                }
            }
            else if (bgLuminance > 0.65 && fgLuminance < 0.5) {
                val darker = blendColors(foreground, Color.BLACK, 0.25f)
                if (contrastRatio(darker, background) > currentContrast) {
                    return darker
                }
            }
            return foreground
        }

        val lighten = relativeLuminance(background) < 0.45
        val target = if (lighten) Color.WHITE else Color.BLACK
        var ratio = 0.15f
        while (ratio <= 1f) {
            val candidate = blendColors(foreground, target, ratio)
            if (contrastRatio(candidate, background) >= minRatio) {
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

    /** 关闭色彩模式时使用更高对比度，增强可读性 */
    fun ensureContrastSafe(foreground: Int, background: Int): Int {
        val safeFg = guardSameColor(foreground, background)
        return ensureContrast(safeFg, background, MIN_PRIMARY_CONTRAST_SAFE)
    }

    /** 保持色相和饱和度，仅调整明度来满足对比度，比 [ensureContrast] 更鲜艳。
     *  绝对不允许白底白字或黑底黑字。 */
    fun ensureContrastColorful(foreground: Int, background: Int, minRatio: Double = MIN_PRIMARY_CONTRAST): Int {
        val safeFg = guardSameColor(foreground, background)
        if (safeFg != foreground) return safeFg
        if (contrastRatio(foreground, background) >= minRatio) return foreground
        val bgLum = relativeLuminance(background)
        val needLighter = bgLum < 0.45
        val hsv = FloatArray(3)
        Color.colorToHSV(safeFg, hsv)
        var step = 0.02f
        var maxSteps = 50
        while (maxSteps-- > 0) {
            if (needLighter) {
                hsv[2] = (hsv[2] + step).coerceIn(0f, 1f)
            } else {
                hsv[2] = (hsv[2] - step).coerceIn(0f, 1f)
            }
            val candidate = Color.HSVToColor(hsv)
            if (contrastRatio(candidate, background) >= minRatio) {
                return candidate
            }
            if (hsv[2] <= 0.02f || hsv[2] >= 0.98f) break
        }
        return ensureContrast(foreground, background, minRatio)
    }

    /** 白底白字或黑底黑字时强制改变文字颜色，确保前景与背景足够区分 */
    fun guardSameColor(foreground: Int, background: Int): Int {
        val fgLum = relativeLuminance(foreground)
        val bgLum = relativeLuminance(background)
        val delta = kotlin.math.abs(fgLum - bgLum)
        if (delta >= MIN_LUMINANCE_DELTA) return foreground
        if (bgLum < 0.35) return Color.rgb(224, 224, 224)
        return Color.rgb(31, 31, 31)
    }

    fun applyOpacity(color: Int, opacityPercent: Int): Int {
        if (opacityPercent >= 100) return color
        val a = (Color.alpha(color) * opacityPercent / 100f).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    /** 取色安全包装：关闭色彩模式时优先可读性（高对比度），开启时保留色彩再兼顾可读性 */
    fun safeColor(foreground: Int, background: Int, colorfulMode: Boolean, minRatio: Double = 4.5): Int {
        val adjusted = if (colorfulMode) {
            ensureContrastColorful(foreground, background, minRatio)
        } else {
            determineReadableColor(foreground, background, minRatio)
        }
        return guardSameColor(adjusted, background)
    }

    /** 优先可读性：如果前景色与背景对比度不足，大幅偏向白/黑 */
    fun determineReadableColor(foreground: Int, background: Int, minRatio: Double = MIN_PRIMARY_CONTRAST): Int {
        val currentRatio = contrastRatio(foreground, background)
        if (currentRatio >= TARGET_CONTRAST) return foreground
        if (currentRatio >= minRatio) {
            val bgLum = relativeLuminance(background)
            val fgLum = relativeLuminance(foreground)
            if (bgLum < 0.35 && fgLum < 0.65) {
                return blendColors(foreground, Color.WHITE, 0.4f)
            } else if (bgLum > 0.65 && fgLum > 0.35) {
                return blendColors(foreground, Color.BLACK, 0.4f)
            }
            return foreground
        }
        return ensureContrast(foreground, background, minRatio)
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

    private fun saturation(color: Int): Float {
        val maxC = maxOf(Color.red(color), Color.green(color), Color.blue(color))
        val minC = minOf(Color.red(color), Color.green(color), Color.blue(color))
        return if (maxC == 0) 0f else (maxC - minC) / maxC.toFloat()
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        return Color.rgb(
            (Color.red(from) * inverse + Color.red(to) * ratio).toInt().coerceIn(0, 255),
            (Color.green(from) * inverse + Color.green(to) * ratio).toInt().coerceIn(0, 255),
            (Color.blue(from) * inverse + Color.blue(to) * ratio).toInt().coerceIn(0, 255)
        )
    }

    private fun pickThreeDistinct(colors: List<Int>): Triple<Int, Int, Int> {
        var best = Triple(colors[0], colors[1], colors[2])
        var bestMin = 0f
        for (i in colors.indices) {
            for (j in i + 1 until colors.size) {
                for (k in j + 1 until colors.size) {
                    val d12 = colorDistance(colors[i], colors[j])
                    val d13 = colorDistance(colors[i], colors[k])
                    val d23 = colorDistance(colors[j], colors[k])
                    val minD = minOf(d12, d13, d23)
                    if (minD > bestMin) {
                        bestMin = minD
                        best = Triple(colors[i], colors[j], colors[k])
                    }
                }
            }
        }
        return best
    }

    private fun colorDistance(c1: Int, c2: Int): Float {
        val rMean = (Color.red(c1) + Color.red(c2)) / 2f
        val dr = (Color.red(c1) - Color.red(c2)).toFloat()
        val dg = (Color.green(c1) - Color.green(c2)).toFloat()
        val db = (Color.blue(c1) - Color.blue(c2)).toFloat()
        return kotlin.math.sqrt(
            (2 + rMean / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rMean) / 256) * db * db
        )
    }
}
