package com.toyph.app.pipeline

import com.toyph.app.TransitionConfig
import com.toyph.app.sequence.SequenceItem
import com.toyph.app.util.GridConstants.GRID_SIZE
import com.toyph.app.transitions.FadeTransition
import com.toyph.app.transitions.NoneTransition
import com.toyph.app.transitions.Transition

class SequencePlayer(
    private val items: List<SequenceItem>,
    private val defaultTransition: TransitionConfig,
    private val transitionConfigs: List<TransitionConfig?>
) {
    private val blackFrame = IntArray(GRID_SIZE * GRID_SIZE)
    private var currentIndex = 0
    private var itemStartMs = 0L
    private var started = false

    private var inTransition = false
    private var transitionStartMs = 0L
    private var activeTransition: Transition? = null
    private var previousFrame: IntArray = blackFrame
    private var targetFrame: IntArray = blackFrame

    val currentFrameIntervalMs: Long
        get() {
            if (items.isEmpty()) return Long.MAX_VALUE
            if (inTransition) return activeTransition?.frameIntervalMs ?: 50L
            return items[currentIndex].frameIntervalMs
        }

    fun getCurrentFrame(elapsedMs: Long): IntArray {
        if (items.isEmpty()) return blackFrame.copyOf()

        if (!started) {
            started = true
            itemStartMs = elapsedMs
        }

        if (inTransition) {
            return handleTransition(elapsedMs)
        }

        val currentItem = items[currentIndex]
        val itemElapsed = elapsedMs - itemStartMs

        if (items.size == 1) {
            val tick = computeTick(currentItem, itemElapsed)
            return currentItem.getFrame(tick)
        }

        if (currentItem.displayDurationMs > 0 && itemElapsed >= currentItem.displayDurationMs) {
            return startTransitionToNext(elapsedMs, currentItem, itemElapsed)
        }

        val tick = computeTick(currentItem, itemElapsed)
        return currentItem.getFrame(tick)
    }

    private fun computeTick(item: SequenceItem, itemElapsed: Long): Int {
        if (item.frameCount <= 1) return 0
        return ((itemElapsed / item.frameIntervalMs) % item.frameCount).toInt()
    }

    private fun startTransitionToNext(elapsedMs: Long, currentItem: SequenceItem, itemElapsed: Long): IntArray {
        val lastTick = computeTick(currentItem, itemElapsed)
        previousFrame = currentItem.getFrame(lastTick)

        val nextIndex = (currentIndex + 1) % items.size
        val nextTransitionConfig = transitionConfigs.getOrNull(nextIndex)
        val transitionConfig = nextTransitionConfig ?: defaultTransition

        activeTransition = createTransition(transitionConfig)

        if (activeTransition == null || activeTransition!!.frameCount == 0) {
            advanceToNext(elapsedMs)
            return items[currentIndex].getFrame(0)
        }

        inTransition = true
        transitionStartMs = elapsedMs
        currentIndex = nextIndex
        itemStartMs = elapsedMs
        targetFrame = items[currentIndex].getFrame(0)

        return activeTransition!!.blend(previousFrame, targetFrame, 0)
    }

    private fun handleTransition(elapsedMs: Long): IntArray {
        val transition = activeTransition ?: run {
            inTransition = false
            return items[currentIndex].getFrame(0)
        }

        val transitionElapsed = elapsedMs - transitionStartMs
        val tick = (transitionElapsed / transition.frameIntervalMs).toInt()

        if (tick >= transition.frameCount) {
            inTransition = false
            activeTransition = null
            itemStartMs = elapsedMs
            return targetFrame.copyOf()
        }

        return transition.blend(previousFrame, targetFrame, tick)
    }

    private fun advanceToNext(elapsedMs: Long) {
        currentIndex = (currentIndex + 1) % items.size
        itemStartMs = elapsedMs
    }

    private fun createTransition(config: TransitionConfig): Transition? {
        return when (config.type) {
            "fade" -> FadeTransition(config.durationMs, config.frameIntervalMs)
            "none" -> NoneTransition()
            else -> FadeTransition(config.durationMs, config.frameIntervalMs)
        }
    }
}
