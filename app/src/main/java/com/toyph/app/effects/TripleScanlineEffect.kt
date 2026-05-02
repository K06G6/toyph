package com.toyph.app.effects

import com.toyph.app.util.GridConstants.GRID_SIZE

class TripleScanlineEffect(
    override val frameIntervalMs: Long = 50L,
    override val trailingIntervalMs: Long = 0L
) : Effect {

    override val frameCount: Int = GRID_SIZE

    override fun apply(frame: IntArray, tick: Int): IntArray {
        val result = frame.copyOf()
        applyLine(result, tick, RowOps::shiftRowLeft)
        applyLine(result, tick + 1, RowOps::shiftRowRight)
        applyLine(result, tick + 2, RowOps::shiftRowLeft)
        return result
    }

    private inline fun applyLine(frame: IntArray, row: Int, shift: (IntArray, Int) -> Unit) {
        if (row !in 0 until GRID_SIZE) return
        shift(frame, row)
        RowOps.boostRow(frame, row)
    }
}
