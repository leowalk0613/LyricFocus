package com.leowalk.LyricFocus.ui

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * 设置页变灰工具：aodchange 外部渲染开启时，所有依赖 hook 的设置项
 * 禁用并降低透明度，避免用户在外部渲染模式下误改焦点通知样式。
 */
object SettingsDim {

    fun apply(root: View, dimmed: Boolean) {
        walk(root, dimmed)
    }

    private fun walk(v: View, dimmed: Boolean) {
        if (v is MaterialSwitch || v is Slider || v is MaterialButton ||
            v is MaterialButtonToggleGroup || v is EditText
        ) {
            v.isEnabled = !dimmed
            v.alpha = if (dimmed) 0.4f else 1f
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                walk(v.getChildAt(i), dimmed)
            }
        }
    }
}
