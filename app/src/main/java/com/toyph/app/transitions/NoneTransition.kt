package com.toyph.app.transitions

class NoneTransition : Transition {
    override val frameIntervalMs: Long = 1L
    override val frameCount: Int = 0

    override fun blend(source: IntArray, target: IntArray, tick: Int): IntArray {
        return target.copyOf()
    }
}
