package com.flashcards.ocr

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class SkewDetectorTest {
    /** White page with rows of "ink" bars, rotated by [tiltDeg]. */
    private fun page(w: Int, h: Int, tiltDeg: Double): IntArray {
        val lum = IntArray(w * h) { 255 }
        val t = Math.toRadians(tiltDeg)
        for (row in 0 until 12) {
            val y0 = 100 + row * 60
            for (x in 80 until w - 80) for (dy in 0 until 20) {
                val cx = x - w / 2.0
                val cy = y0 + dy - h / 2.0
                val rx = (cx * cos(t) - cy * sin(t) + w / 2).roundToInt()
                val ry = (cx * sin(t) + cy * cos(t) + h / 2).roundToInt()
                if (rx in 0 until w && ry in 0 until h) lum[ry * w + rx] = 20
            }
        }
        return lum
    }

    @Test
    fun findsTiltWithinAFifthOfADegree() {
        for (tilt in listOf(0.0, 1.4, 2.0, -3.2, 4.6)) {
            val found = Math.toDegrees(SkewDetector.detect(page(1600, 1200, tilt), 1600, 1200))
            assertEquals("tilt $tilt", tilt, found, 0.21)
        }
    }

    @Test
    fun blankPageReturnsZero() {
        assertEquals(0.0, SkewDetector.detect(IntArray(400 * 300) { 255 }, 400, 300), 0.0)
    }
}
