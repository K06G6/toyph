package com.toyph.app.filters

import com.toyph.app.util.GridConstants.GRID_SIZE
import com.toyph.app.util.GridConstants.MAX_BRIGHTNESS

class BlurFilter : Filter {
    override fun apply(frame: IntArray): IntArray {
        val result = IntArray(frame.size)
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                var sum = frame[row * GRID_SIZE + col]
                var count = 1
                if (row > 0) { sum += frame[(row - 1) * GRID_SIZE + col]; count++ }
                if (row < GRID_SIZE - 1) { sum += frame[(row + 1) * GRID_SIZE + col]; count++ }
                if (col > 0) { sum += frame[row * GRID_SIZE + col - 1]; count++ }
                if (col < GRID_SIZE - 1) { sum += frame[row * GRID_SIZE + col + 1]; count++ }
                result[row * GRID_SIZE + col] = (sum / count).coerceIn(0, MAX_BRIGHTNESS)
            }
        }
        return result
    }
}
