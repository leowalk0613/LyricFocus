package com.leowalk.LyricFocus.util

import android.graphics.Color

/** 万象息屏 AOD 推荐文字色：深色背景下清晰且不刺眼 */
object AodColorPresets {

    data class Preset(val name: String, val color: Int)

    val presets: List<Preset> = listOf(
        Preset("纯白", Color.WHITE),
        Preset("柔白", 0xFFF5F5F5.toInt()),
        Preset("米白", 0xFFFFF8E1.toInt()),
        Preset("浅灰", 0xFFECEFF1.toInt()),
        Preset("银灰", 0xFFB0BEC5.toInt()),
        Preset("冰蓝", 0xFFB3E5FC.toInt()),
        Preset("天蓝", 0xFF81D4FA.toInt()),
        Preset("浅青", 0xFF80DEEA.toInt()),
        Preset("薄荷", 0xFFA5D6A7.toInt()),
        Preset("草绿", 0xFFC5E1A5.toInt()),
        Preset("柠檬", 0xFFE6EE9C.toInt()),
        Preset("淡黄", 0xFFFFF59D.toInt()),
        Preset("杏色", 0xFFFFCCBC.toInt()),
        Preset("珊瑚", 0xFFFFAB91.toInt()),
        Preset("玫瑰", 0xFFF48FB1.toInt()),
        Preset("樱花", 0xFFF8BBD0.toInt()),
        Preset("薰衣草", 0xFFE1BEE7.toInt()),
        Preset("淡紫", 0xFFCE93D8.toInt()),
        Preset("浅紫", 0xFFB39DDB.toInt()),
        Preset("金杏", 0xFFFFE082.toInt()),
        Preset("暖橙", 0xFFFFCC80.toInt()),
        Preset("雾蓝", 0xFF90CAF9.toInt()),
        Preset("水绿", 0xFF80CBC4.toInt()),
        Preset("淡粉", 0xFFF06292.toInt())
    )

    fun defaultPresetColor(): Int = presets.first().color

    fun indexOfColor(color: Int): Int {
        return presets.indexOfFirst { it.color == color }.coerceAtLeast(0)
    }
}
