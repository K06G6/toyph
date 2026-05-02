package com.toyph.app.filters

import com.toyph.app.util.GridConstants.GRID_SIZE
import com.toyph.app.util.GridConstants.MAX_BRIGHTNESS
import kotlin.math.pow
import kotlin.math.sqrt

class PipBoyFilter(intensity: Double) : Filter {

    private val gammaLUT: IntArray
    private val bloomStrength: Double
    private val scanlineRowFactors: DoubleArray
    private val vignetteLUT: DoubleArray

    init {
        val clamped = intensity.coerceIn(0.0, 1.0)

        // Pass 1: Gamma contrast LUT
        val gamma = 1.0 + 0.6 * clamped
        gammaLUT = IntArray(MAX_BRIGHTNESS + 1) { i ->
            val n = i.toDouble() / MAX_BRIGHTNESS
            (n.pow(gamma) * MAX_BRIGHTNESS).toInt().coerceIn(0, MAX_BRIGHTNESS)
        }

        // Pass 2: Bloom strength
        bloomStrength = 0.35 * clamped

        // Pass 3: Scanline row factors (dim odd rows)
        val scanlineDim = 1.0 - 0.35 * clamped
        scanlineRowFactors = DoubleArray(GRID_SIZE) { row ->
            if (row % 2 == 1) scanlineDim else 1.0
        }

        // Pass 4: Vignette LUT (quadratic falloff from center)
        val vignetteStrength = 0.55 * clamped
        val centerR = (GRID_SIZE - 1) / 2.0
        val centerC = (GRID_SIZE - 1) / 2.0
        val maxDist = sqrt(centerR * centerR + centerC * centerC)
        vignetteLUT = DoubleArray(GRID_SIZE * GRID_SIZE) { i ->
            val row = i / GRID_SIZE
            val col = i % GRID_SIZE
            val dr = row - centerR
            val dc = col - centerC
            val nd = sqrt(dr * dr + dc * dc) / maxDist
            1.0 - vignetteStrength * nd * nd
        }
    }

    override fun apply(frame: IntArray): IntArray {
        val result = frame.copyOf()

        // Pass 1: Gamma contrast
        for (i in result.indices) {
            result[i] = gammaLUT[result[i].coerceIn(0, MAX_BRIGHTNESS)]
        }

        // Pass 2: Phosphor bloom (additive blur)
        if (bloomStrength > 0.01) {
            val blurred = IntArray(result.size)
            for (row in 0 until GRID_SIZE) {
                for (col in 0 until GRID_SIZE) {
                    val idx = row * GRID_SIZE + col
                    var sum = result[idx]
                    var count = 1
                    if (row > 0) { sum += result[idx - GRID_SIZE]; count++ }
                    if (row < GRID_SIZE - 1) { sum += result[idx + GRID_SIZE]; count++ }
                    if (col > 0) { sum += result[idx - 1]; count++ }
                    if (col < GRID_SIZE - 1) { sum += result[idx + 1]; count++ }
                    blurred[idx] = sum / count
                }
            }
            for (i in result.indices) {
                result[i] = (result[i] + (blurred[i] * bloomStrength).toInt())
                    .coerceAtMost(MAX_BRIGHTNESS)
            }
        }

        // Pass 3: Scanlines (dim odd rows)
        for (row in 0 until GRID_SIZE) {
            val factor = scanlineRowFactors[row]
            if (factor < 1.0) {
                val start = row * GRID_SIZE
                for (col in 0 until GRID_SIZE) {
                    result[start + col] = (result[start + col] * factor).toInt()
                }
            }
        }

        // Pass 4: Vignette
        for (i in result.indices) {
            result[i] = (result[i] * vignetteLUT[i]).toInt().coerceIn(0, MAX_BRIGHTNESS)
        }

        return result
    }
}
