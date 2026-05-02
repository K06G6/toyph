package com.toyph.app.effects

import com.toyph.app.util.GridConstants.GRID_SIZE
import com.toyph.app.util.GridConstants.MAX_BRIGHTNESS

object RowOps {
    fun shiftRowRight(frame: IntArray, row: Int) {
        val start = row * GRID_SIZE
        for (c in GRID_SIZE - 1 downTo 1) {
            frame[start + c] = frame[start + c - 1]
        }
        frame[start] = 0
    }

    fun shiftRowLeft(frame: IntArray, row: Int) {
        val start = row * GRID_SIZE
        for (c in 0 until GRID_SIZE - 1) {
            frame[start + c] = frame[start + c + 1]
        }
        frame[start + GRID_SIZE - 1] = 0
    }

    fun boostRow(frame: IntArray, row: Int) {
        val start = row * GRID_SIZE
        for (c in 0 until GRID_SIZE) {
            frame[start + c] = (frame[start + c] * 3 / 2).coerceAtMost(MAX_BRIGHTNESS)
        }
    }
}
