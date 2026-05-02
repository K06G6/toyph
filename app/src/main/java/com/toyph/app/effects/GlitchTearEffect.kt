package com.toyph.app.effects

import com.toyph.app.util.GridConstants.GRID_SIZE

class GlitchTearEffect(
    override val frameIntervalMs: Long = 80L,
    override val trailingIntervalMs: Long = 0L
) : Effect {

    override val frameCount: Int = FRAME_COUNT

    override fun apply(frame: IntArray, tick: Int): IntArray {
        val result = frame.copyOf()
        when (tick) {
            4 -> shiftBandRight(result)
            5 -> shiftBandLeft(result)
        }
        return result
    }

    private fun shiftBandRight(frame: IntArray) {
        for (row in BAND_START..BAND_END) {
            val offset = row * GRID_SIZE
            System.arraycopy(frame, offset, frame, offset + 1, GRID_SIZE - 1)
            frame[offset] = 0
        }
    }

    private fun shiftBandLeft(frame: IntArray) {
        for (row in BAND_START..BAND_END) {
            val offset = row * GRID_SIZE
            System.arraycopy(frame, offset + 1, frame, offset, GRID_SIZE - 1)
            frame[offset + GRID_SIZE - 1] = 0
        }
    }

    companion object {
        private const val FRAME_COUNT = 6
        private const val BAND_START = 4
        private const val BAND_END = 8
    }
}
