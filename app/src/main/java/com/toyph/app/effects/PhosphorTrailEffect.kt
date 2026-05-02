package com.toyph.app.effects

import com.toyph.app.util.GridConstants.GRID_SIZE
import com.toyph.app.util.GridConstants.MAX_BRIGHTNESS

class PhosphorTrailEffect(
    override val frameIntervalMs: Long = 50L,
    override val trailingIntervalMs: Long = 0L
) : Effect {

    override val frameCount: Int = GRID_SIZE + TRAIL_LENGTH

    override fun apply(frame: IntArray, tick: Int): IntArray {
        val result = frame.copyOf()
        for (i in result.indices) {
            val row = i / GRID_SIZE
            val distance = tick - row
            val boost = when (distance) {
                0 -> SWEEP_BOOST
                1 -> TRAIL1_BOOST
                2 -> TRAIL2_BOOST
                else -> 1.0
            }
            if (boost != 1.0) {
                result[i] = (frame[i] * boost).toInt().coerceAtMost(MAX_BRIGHTNESS)
            }
        }
        return result
    }

    companion object {
        private const val TRAIL_LENGTH = 2
        private const val SWEEP_BOOST = 1.5
        private const val TRAIL1_BOOST = 1.25
        private const val TRAIL2_BOOST = 1.10
    }
}
