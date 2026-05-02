package com.toyph.app.sequence

import com.toyph.app.util.GridConstants.GRID_SIZE

class ScrollingTextItem(
    private val textMatrix: IntArray,
    private val matrixWidth: Int,
    override val frameIntervalMs: Long = 80L
) : SequenceItem {

    override val frameCount: Int = matrixWidth - GRID_SIZE + 1

    override val displayDurationMs: Long = frameCount.toLong() * frameIntervalMs

    override fun getFrame(tick: Int): IntArray {
        val frame = IntArray(GRID_SIZE * GRID_SIZE)
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                frame[row * GRID_SIZE + col] = textMatrix[row * matrixWidth + tick + col]
            }
        }
        return frame
    }
}
