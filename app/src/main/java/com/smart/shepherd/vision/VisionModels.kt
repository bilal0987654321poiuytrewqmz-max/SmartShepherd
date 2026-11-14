package com.smart.shepherd.vision

import android.graphics.RectF

data class SheepDetection(
    val box: RectF,
    val confidence: Float,
    val label: String = "sheep"
)

data class TrackedSheep(
    val trackId: Long,
    val box: RectF,
    val confidence: Float
)
