package com.smart.shepherd.vision

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * On-device COCO detector. The bundled EfficientDet-Lite0 model has a "sheep" class.
 * It detects sheep as a category; it does NOT identify individual animals.
 */
class SheepDetector(context: Context) : AutoCloseable {
    private val detector: ObjectDetector

    init {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(30)
            .setScoreThreshold(0.30f)
            .setLabelAllowList(listOf("sheep"))
            .build()
        detector = ObjectDetector.createFromFileAndOptions(
            context.applicationContext,
            MODEL_FILE,
            options
        )
    }

    fun detect(bitmap: Bitmap): List<SheepDetection> {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        return detector.detect(tensorImage).mapNotNull { detection ->
            val category = detection.categories.maxByOrNull { it.score } ?: return@mapNotNull null
            if (!category.label.equals("sheep", ignoreCase = true)) return@mapNotNull null
            val b = detection.boundingBox
            val left = (b.left / bitmap.width).coerceIn(0f, 1f)
            val top = (b.top / bitmap.height).coerceIn(0f, 1f)
            val right = (b.right / bitmap.width).coerceIn(0f, 1f)
            val bottom = (b.bottom / bitmap.height).coerceIn(0f, 1f)
            if (right <= left || bottom <= top) return@mapNotNull null
            SheepDetection(android.graphics.RectF(left, top, right, bottom), category.score)
        }
    }

    override fun close() = detector.close()

    companion object {
        const val MODEL_FILE = "models/sheep_detector.tflite"
    }
}
