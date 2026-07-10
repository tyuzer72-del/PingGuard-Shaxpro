package com.pingguard.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PingGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val data = mutableListOf<Long>()
    private val linePaint = Paint().apply {
        color = Color.parseColor("#34C759")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val thresholdPaint = Paint().apply {
        color = Color.parseColor("#55FF3B30")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    fun updateData(newData: List<Long>) {
        data.clear()
        data.addAll(newData)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val maxVal = (data.maxOrNull() ?: 100L).coerceAtLeast(100L).toFloat()
        val w = width.toFloat()
        val h = height.toFloat()
        val stepX = if (data.size > 1) w / (data.size - 1) else w

        val thresholdY = h - (500f / maxVal) * h
        if (thresholdY in 0f..h) {
            canvas.drawLine(0f, thresholdY, w, thresholdY, thresholdPaint)
        }

        val path = android.graphics.Path()
        data.forEachIndexed { i, value ->
            val x = i * stepX
            val y = h - (value.coerceAtMost(999).toFloat() / maxVal) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)

            linePaint.color = when {
                value >= 500 -> Color.parseColor("#FF3B30")
                value >= 150 -> Color.parseColor("#FF9500")
                else -> Color.parseColor("#34C759")
            }
        }
        canvas.drawPath(path, linePaint)
    }
}
