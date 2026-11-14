package com.smart.shepherd.learning

import android.content.Context
import java.io.File

/**
 * Local dataset/feedback manager.
 *
 * This safely stores reviewed training examples and metadata on-device.
 * It is NOT a neural-network trainer. Actual weight updates require a compatible
 * trainable model, model-specific loss/preprocessing code, and validation data.
 */
class LocalLearningManager(context: Context) {
    private val root = File(context.filesDir, "learning").apply { mkdirs() }
    private val examples = File(root, "reviewed_examples").apply { mkdirs() }

    fun reviewedExamplesDirectory(): File = examples
    fun reviewedExampleCount(): Int = examples.listFiles()?.count { it.isFile } ?: 0
    fun status(): String =
        "أمثلة محلية محفوظة: ${reviewedExampleCount()}. التدريب الفعلي غير مفعل حتى إضافة نموذج قابل للتدريب."
}
