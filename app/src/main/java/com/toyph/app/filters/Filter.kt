package com.toyph.app.filters

interface Filter {
    fun apply(frame: IntArray): IntArray
}
