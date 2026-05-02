package com.toyph.app.pipeline

import com.toyph.app.filters.Filter

class FilterChain(private val filters: List<Filter>) {
    fun apply(frame: IntArray): IntArray {
        if (filters.isEmpty()) return frame
        var result = frame
        for (filter in filters) {
            result = filter.apply(result)
        }
        return result
    }
}
