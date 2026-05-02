package com.toyph.app.transitions

interface Transition {
    val frameIntervalMs: Long
    val frameCount: Int
    fun blend(source: IntArray, target: IntArray, tick: Int): IntArray
}
