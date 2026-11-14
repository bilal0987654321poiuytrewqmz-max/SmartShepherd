package com.smart.shepherd.learning

import android.content.Context
import com.smart.shepherd.data.TrainingExampleRepository

/**
 * On-device training boundary. The Android app owns the dataset, evaluation and promotion
 * lifecycle. Weight updates are intentionally refused until a trainable TFLite model with
 * a verified training signature is supplied; this avoids pretending that inference-only
 * EfficientDet weights can be trained by the app.
 */
class TrainingEngine(
    context: Context,
    private val registry: ModelRegistry = ModelRegistry(context),
    private val dataset: TrainingExampleRepository = TrainingExampleRepository(context)
) {
    fun status(): String = "بيانات التدريب: ${dataset.count()} | التدريب المحلي: ينتظر نموذجاً قابلاً للتدريب"

    fun requestTraining(modelId: String): Result<String> = Result.failure(
        UnsupportedOperationException(
            "Training plan accepted for $modelId, but no trainable model artifact is bundled yet. " +
                "The inference model is intentionally not modified in-place."
        )
    )
}
