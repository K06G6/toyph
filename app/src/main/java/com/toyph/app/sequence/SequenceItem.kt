package com.toyph.app.sequence

interface SequenceItem {
    val displayDurationMs: Long
    val frameIntervalMs: Long
    val frameCount: Int
    fun getFrame(tick: Int): IntArray
}
