package com.toyph.app.pipeline

import android.os.SystemClock
import com.toyph.app.util.GridConstants.GRID_SIZE
import com.toyph.app.util.GridConstants.MAX_BRIGHTNESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RenderingPipeline(
    private val sequencePlayer: SequencePlayer?,
    private val effectCycler: EffectCycler?,
    private val filterChain: FilterChain?,
    private val display: (IntArray) -> Unit
) {
    private val blackFrame = IntArray(GRID_SIZE * GRID_SIZE)

    fun start(scope: CoroutineScope): Job {
        val startTime = SystemClock.elapsedRealtime()
        return scope.launch {
            while (isActive) {
                val elapsedMs = SystemClock.elapsedRealtime() - startTime

                var frame = sequencePlayer?.getCurrentFrame(elapsedMs)
                    ?: blackFrame.copyOf()

                frame = effectCycler?.apply(frame, elapsedMs) ?: frame

                frame = filterChain?.apply(frame) ?: frame

                clampFrame(frame)
                display(frame)

                delay(computeFrameInterval())
            }
        }
    }

    private fun computeFrameInterval(): Long {
        var interval = Long.MAX_VALUE

        sequencePlayer?.let {
            val seqInterval = it.currentFrameIntervalMs
            if (seqInterval < interval) interval = seqInterval
        }

        effectCycler?.let {
            val effInterval = it.currentFrameIntervalMs
            if (effInterval < interval) interval = effInterval
        }

        return if (interval == Long.MAX_VALUE) 100L else interval
    }

    private fun clampFrame(frame: IntArray) {
        for (i in frame.indices) {
            frame[i] = frame[i].coerceIn(0, MAX_BRIGHTNESS)
        }
    }
}
