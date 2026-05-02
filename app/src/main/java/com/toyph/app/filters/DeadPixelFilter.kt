package com.toyph.app.filters

import com.toyph.app.util.GridConstants.GRID_SIZE

class DeadPixelFilter(private val pixels: List<Pair<Int, Int>>) : Filter {
    override fun apply(frame: IntArray): IntArray {
        val result = frame.copyOf()
        for ((row, col) in pixels) {
            if (row in 0 until GRID_SIZE && col in 0 until GRID_SIZE) {
                result[row * GRID_SIZE + col] = 0
            }
        }
        return result
    }
}
