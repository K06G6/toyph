package com.toyph.app.sequence

class GlyphItem(
    private val pixels: IntArray,
    override val displayDurationMs: Long
) : SequenceItem {

    override val frameIntervalMs: Long = Long.MAX_VALUE
    override val frameCount: Int = 1

    override fun getFrame(tick: Int): IntArray = pixels.copyOf()
}
