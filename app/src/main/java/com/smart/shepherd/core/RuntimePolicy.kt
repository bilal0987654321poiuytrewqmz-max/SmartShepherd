package com.smart.shepherd.core

import android.os.SystemClock

class RuntimePolicy {
    private var lastReId = 0L
    private var lastParts = 0L
    private var lastBehavior = 0L

    fun shouldRun(kind: ModelKind, trackCount: Int): Boolean {
        if (trackCount == 0 && kind != ModelKind.DETECTOR) return false
        val now = SystemClock.elapsedRealtime()
        return when (kind) {
            ModelKind.DETECTOR -> true
            ModelKind.REID -> if (now - lastReId >= 700L) { lastReId = now; true } else false
            ModelKind.PARTS -> if (now - lastParts >= 900L) { lastParts = now; true } else false
            ModelKind.ATTRIBUTES -> true
            ModelKind.BEHAVIOR -> if (now - lastBehavior >= 250L) { lastBehavior = now; true } else false
        }
    }
}
