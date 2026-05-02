package com.toyph.app.pipeline

import com.toyph.app.effects.Effect

class EffectCycler(private val effects: List<Effect>) {
    private var currentIndex = 0
    private var effectStartMs = 0L
    private var trailingStartMs = 0L
    private var inTrailing = false
    private var started = false

    val currentFrameIntervalMs: Long
        get() = if (effects.isEmpty()) Long.MAX_VALUE
                else effects[currentIndex].frameIntervalMs

    fun apply(frame: IntArray, elapsedMs: Long): IntArray {
        if (effects.isEmpty()) return frame

        if (!started) {
            started = true
            effectStartMs = elapsedMs
        }

        val currentEffect = effects[currentIndex]

        if (inTrailing) {
            val trailingElapsed = elapsedMs - trailingStartMs
            if (trailingElapsed >= currentEffect.trailingIntervalMs) {
                advanceToNext(elapsedMs)
                return effects[currentIndex].apply(frame, 0)
            }
            return frame
        }

        val effectElapsed = elapsedMs - effectStartMs
        val tick = (effectElapsed / currentEffect.frameIntervalMs).toInt()

        if (tick >= currentEffect.frameCount) {
            if (currentEffect.trailingIntervalMs > 0) {
                inTrailing = true
                trailingStartMs = elapsedMs
                return frame
            }
            advanceToNext(elapsedMs)
            return effects[currentIndex].apply(frame, 0)
        }

        return currentEffect.apply(frame, tick)
    }

    private fun advanceToNext(elapsedMs: Long) {
        currentIndex = (currentIndex + 1) % effects.size
        effectStartMs = elapsedMs
        inTrailing = false
    }
}
