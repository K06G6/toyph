package com.toyph.app.filters

import com.toyph.app.util.GridConstants.MAX_BRIGHTNESS

class BrightnessFilter(private val value: Double) : Filter {
    override fun apply(frame: IntArray): IntArray {
        val result = frame.copyOf()
        for (i in result.indices) {
            result[i] = (result[i] * value).toInt().coerceIn(0, MAX_BRIGHTNESS)
        }
        return result
    }
}
