package com.smart.shepherd.vision

import android.graphics.Bitmap
import com.smart.shepherd.core.RuntimePolicy
import com.smart.shepherd.core.ModelKind

class LiveVisionEngine(private val detector: SheepDetector) {
    private val policy = RuntimePolicy()

    fun analyze(bitmap: Bitmap): LiveVisionResult {
        val detections = detector.detect(bitmap)
        val tracked = tracker.update(detections)
        val parts = if (policy.shouldRun(ModelKind.PARTS, tracked.size)) emptyList<PartObservation>() else emptyList()
        val behaviors = if (policy.shouldRun(ModelKind.BEHAVIOR, tracked.size)) emptyList<BehaviorObservation>() else emptyList()
        return LiveVisionResult(tracked, parts, behaviors)
    }

    private val tracker = SheepTracker()
}

data class PartObservation(val trackId: Long, val part: String, val confidence: Float)
data class BehaviorObservation(val trackId: Long, val behavior: String, val confidence: Float)
data class LiveVisionResult(
    val tracked: List<TrackedSheep>,
    val parts: List<PartObservation>,
    val behaviors: List<BehaviorObservation>
)
