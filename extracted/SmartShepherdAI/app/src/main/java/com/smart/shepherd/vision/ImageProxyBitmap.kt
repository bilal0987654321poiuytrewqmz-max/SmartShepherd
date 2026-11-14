package com.smart.shepherd.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/** Converts CameraX RGBA_8888 output into an upright ARGB bitmap. */
fun ImageProxy.toUprightBitmap(): Bitmap {
    val plane = planes.first()
    val buffer = plane.buffer.duplicate()
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val rowStart = y * rowStride
        for (x in 0 until width) {
            val i = rowStart + x * pixelStride
            val r = bytes[i].toInt() and 0xFF
            val g = bytes[i + 1].toInt() and 0xFF
            val b = bytes[i + 2].toInt() and 0xFF
            val a = bytes[i + 3].toInt() and 0xFF
            pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    val raw = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
        if (it !== raw) raw.recycle()
    }
}
