package com.smart.shepherd.core

/** Lightweight model plan. Only the detector is bundled in v0.3; the other slots are runtime contracts. */
enum class ModelKind { DETECTOR, REID, PARTS, ATTRIBUTES, BEHAVIOR }

data class ModelSpec(
    val kind: ModelKind,
    val id: String,
    val displayName: String,
    val targetSizeMb: String,
    val liveFrequency: String,
    val bundled: Boolean,
    val description: String
)

object ModelCatalog {
    val specs = listOf(
        ModelSpec(ModelKind.DETECTOR, "sheep_detector_v1", "Sheep detector", "~4.6 MB", "8–15 FPS", true,
            "EfficientDet-Lite0 / COCO sheep class. Detects sheep, not individual identity."),
        ModelSpec(ModelKind.REID, "sheep_reid_v1", "Sheep Re-ID", "~2–5 MB", "1–3 Hz per track", false,
            "Small embedding model returning a 128–256D visual fingerprint. Match against local herd memory."),
        ModelSpec(ModelKind.PARTS, "sheep_parts_v1", "Sheep parts", "~3–7 MB", "1–2 Hz / selected crops", false,
            "Head, ear, eye, legs/hoof, wool and other visible parts."),
        ModelSpec(ModelKind.ATTRIBUTES, "sheep_attributes_v1", "Attributes", "~2–4 MB", "on selected crops", false,
            "Age/size/sex/breed-style attributes when supported by labeled data."),
        ModelSpec(ModelKind.BEHAVIOR, "sheep_behavior_v1", "Behavior", "~1–3 MB", "2–5 Hz", false,
            "Temporal behavior classifier fed by tracked features over a short window.")
    )

    val liveTargetTotalMb = "~13–23 MB" // 4 core models, excluding optional heavier variants.
}
