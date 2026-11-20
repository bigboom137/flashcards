package com.flashcards.ocr

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Estimates page tilt from an enhanced (white paper / dark ink) luminance image.
 *
 * Projection-profile method: rotate the ink pixels by each candidate angle and
 * measure how sharply they pile up into horizontal rows. Text lines make the
 * profile spiky at the correct angle and smeared at every other one.
 *
 * Returns radians; positive means the text descends toward the right.
 */
object SkewDetector {
    private const val TARGET_EDGE = 900
    private const val INK_THRESHOLD = 110
    private const val MAX_DEGREES = 6.0
    private const val STEP_DEGREES = 0.2
    private const val MIN_POINTS = 150

    fun detect(lum: IntArray, width: Int, height: Int): Double {
        val step = maxOf(1, (maxOf(width, height) + TARGET_EDGE - 1) / TARGET_EDGE)

        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        var y = 0
        while (y < height) {
            val row = y * width
            var x = 0
            while (x < width) {
                if (lum[row + x] < INK_THRESHOLD) {
                    xs += x / step
                    ys += y / step
                }
                x += step
            }
            y += step
        }
        if (xs.size < MIN_POINTS) return 0.0

        val w = width / step + 1
        val h = height / step + 1
        val bins = IntArray(h + w + 2)

        var bestAngle = 0.0
        var bestScore = -1.0
        var deg = -MAX_DEGREES
        while (deg <= MAX_DEGREES + 1e-9) {
            val rad = Math.toRadians(deg)
            val c = cos(rad)
            val s = sin(rad)
            bins.fill(0)
            for (i in xs.indices) {
                val yp = (ys[i] * c - xs[i] * s).roundToInt() + w // offset keeps it non-negative
                if (yp in bins.indices) bins[yp]++
            }
            // Sum of squares rewards profiles concentrated into few bins.
            var score = 0.0
            for (b in bins) if (b != 0) score += b.toDouble() * b
            if (score > bestScore) {
                bestScore = score
                bestAngle = rad
            }
            deg += STEP_DEGREES
        }
        return bestAngle
    }
}
