package com.toyph.app.effects

import com.toyph.app.util.GridConstants.GRID_SIZE

class ScanlineEffect(
    override val frameIntervalMs: Long = 50L,
    override val trailingIntervalMs: Long = 0L
) : Effect {

    override val frameCount: Int = GRID_SIZE

    override fun apply(frame: IntArray, tick: Int): IntArray {
        val result = frame.copyOf()
        RowOps.shiftRowRight(result, tick)
        RowOps.boostRow(result, tick)
        return result
    }
}
