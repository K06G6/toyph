package com.toyph.app.effects

import com.toyph.app.util.GridConstants.GRID_SIZE
import com.toyph.app.util.GridConstants.MAX_BRIGHTNESS
import kotlin.math.PI
import kotlin.math.sin

class CrtShimmerEffect(
    override val frameIntervalMs: Long = 67L,
    override val trailingIntervalMs: Long = 0L
) : Effect {

    override val frameCount: Int = FRAME_COUNT

    override fun apply(frame: IntArray, tick: Int): IntArray {
        val phase = 2.0 * PI * tick / FRAME_COUNT
        val mod = sin(phase)
        val evenFactor = 1.0 + AMPLITUDE * mod
        val oddFactor = 1.0 - AMPLITUDE * mod

        val result = frame.copyOf()
        for (i in result.indices) {
            val factor = if ((i / GRID_SIZE) % 2 == 0) evenFactor else oddFactor
            result[i] = (frame[i] * factor).toInt().coerceIn(0, MAX_BRIGHTNESS)
        }
        return result
    }

    companion object {
        private const val FRAME_COUNT = 24
        private const val AMPLITUDE = 0.6
    }
}
