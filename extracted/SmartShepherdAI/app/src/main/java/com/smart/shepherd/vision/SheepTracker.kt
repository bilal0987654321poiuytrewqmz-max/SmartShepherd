package com.smart.shepherd.vision

import android.graphics.RectF
import kotlin.math.max

/** Lightweight IoU tracker. IDs are valid only during the current camera session. */
class SheepTracker(
    private val minimumIou: Float = 0.22f,
    private val maxMissedFrames: Int = 12
) {
    private data class Track(val id: Long, var box: RectF, var lastSeen: Int)

    private val tracks = mutableListOf<Track>()
    private var frame = 0
    private var nextId = 1L
    private val seenIds = linkedSetOf<Long>()

    val totalSeenThisSession: Int get() = seenIds.size

    @Synchronized
    fun update(detections: List<SheepDetection>): List<TrackedSheep> {
        frame++
        tracks.removeAll { frame - it.lastSeen > maxMissedFrames }

        data class Candidate(val detectionIndex: Int, val track: Track, val iou: Float)
        val candidates = mutableListOf<Candidate>()
        detections.forEachIndexed { index, detection ->
            tracks.forEach { track ->
                val overlap = iou(detection.box, track.box)
                if (overlap >= minimumIou) candidates += Candidate(index, track, overlap)
            }
        }

        val assignedDetections = mutableSetOf<Int>()
        val assignedTracks = mutableSetOf<Long>()
        val matches = mutableMapOf<Int, Track>()
        candidates.sortedByDescending { it.iou }.forEach { candidate ->
            if (candidate.detectionIndex !in assignedDetections && candidate.track.id !in assignedTracks) {
                assignedDetections += candidate.detectionIndex
                assignedTracks += candidate.track.id
                matches[candidate.detectionIndex] = candidate.track
            }
        }

        return detections.mapIndexed { index, detection ->
            val track = matches[index] ?: Track(nextId++, RectF(detection.box), frame).also {
                tracks += it
                seenIds += it.id
            }
            track.box = RectF(detection.box)
            track.lastSeen = frame
            TrackedSheep(track.id, RectF(detection.box), detection.confidence)
        }
    }

    @Synchronized
    fun reset() {
        tracks.clear()
        seenIds.clear()
        frame = 0
        nextId = 1L
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = kotlin.math.min(a.right, b.right)
        val bottom = kotlin.math.min(a.bottom, b.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val union = a.width() * a.height() + b.width() * b.height() - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
