package com.toyph.app.effects

interface Effect {
    val frameIntervalMs: Long
    val frameCount: Int
    val trailingIntervalMs: Long
    fun apply(frame: IntArray, tick: Int): IntArray
}
