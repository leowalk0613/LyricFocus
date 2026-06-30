package com.leowalk.LyricFocus.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class StrokeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var strokeEnabled: Boolean = false
    private var strokeWidth: Float = 0f
    private var strokeColor: Int = Color.BLACK

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    fun setStrokeEnabled(enabled: Boolean) {
        strokeEnabled = enabled
        invalidate()
    }

    fun setStrokeWidth(width: Float) {
        strokeWidth = width
        strokePaint.strokeWidth = width * 2
        invalidate()
    }

    fun setStrokeColor(color: Int) {
        strokeColor = color
        strokePaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (strokeEnabled && strokeWidth > 0) {
            val text = text ?: return
            val textSize = textSize
            strokePaint.textSize = textSize
            strokePaint.typeface = typeface
            strokePaint.textAlign = paint.textAlign
            strokePaint.textScaleX = paint.textScaleX
            strokePaint.textSkewX = paint.textSkewX
            strokePaint.letterSpacing = paint.letterSpacing

            val layout = layout ?: return
            for (i in 0 until layout.lineCount) {
                val lineStart = layout.getLineStart(i)
                val lineEnd = layout.getLineEnd(i)
                val lineText = text.substring(lineStart, lineEnd)
                val x = layout.getLineLeft(i)
                val y = layout.getLineBaseline(i).toFloat()
                canvas.drawText(lineText, x, y, strokePaint)
            }
        }
        super.onDraw(canvas)
    }
}