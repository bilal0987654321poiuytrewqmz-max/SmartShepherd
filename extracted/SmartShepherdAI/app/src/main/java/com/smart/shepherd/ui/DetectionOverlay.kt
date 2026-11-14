package com.smart.shepherd.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.smart.shepherd.vision.TrackedSheep
import kotlin.math.max

class DetectionOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7EE6A0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 32f
        style = Paint.Style.FILL
        setShadowLayer(5f, 1f, 2f, 0xFF000000.toInt())
    }
    private var sheep: List<TrackedSheep> = emptyList()
    private var sourceWidth = 1
    private var sourceHeight = 1

    fun submitDetections(items: List<TrackedSheep>, imageWidth: Int, imageHeight: Int) {
        sheep = items
        sourceWidth = imageWidth.coerceAtLeast(1)
        sourceHeight = imageHeight.coerceAtLeast(1)
        postInvalidateOnAnimation()
    }

    fun clear() {
        sheep = emptyList()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sheep.isEmpty() || width == 0 || height == 0) return
        // PreviewView uses FILL_CENTER: match its center-crop geometry.
        val scale = max(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val offsetX = (width - sourceWidth * scale) / 2f
        val offsetY = (height - sourceHeight * scale) / 2f
        sheep.forEach { item ->
            val r = item.box
            val rect = RectF(
                r.left * sourceWidth * scale + offsetX,
                r.top * sourceHeight * scale + offsetY,
                r.right * sourceWidth * scale + offsetX,
                r.bottom * sourceHeight * scale + offsetY
            )
            canvas.drawRoundRect(rect, 12f, 12f, boxPaint)
            canvas.drawText("جلسة #${item.trackId} ${(item.confidence * 100).toInt()}%",
                rect.left.coerceAtLeast(4f), (rect.top - 12f).coerceAtLeast(34f), textPaint)
        }
    }
}
