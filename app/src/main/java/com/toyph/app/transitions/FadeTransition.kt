package com.toyph.app.transitions

import com.toyph.app.util.GridConstants.MAX_BRIGHTNESS

class FadeTransition(
    durationMs: Long = 1000L,
    override val frameIntervalMs: Long = 50L
) : Transition {

    override val frameCount: Int = (durationMs / frameIntervalMs).toInt().coerceAtLeast(1)

    override fun blend(source: IntArray, target: IntArray, tick: Int): IntArray {
        val frame = IntArray(source.size)
        for (i in frame.indices) {
            frame[i] = ((source[i] * (frameCount - tick) + target[i] * tick) / frameCount)
                .coerceIn(0, MAX_BRIGHTNESS)
        }
        return frame
    }
}
