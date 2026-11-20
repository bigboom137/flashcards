package com.flashcards.ocr

import com.flashcards.ocr.TextLayout.Pt
import com.flashcards.ocr.TextLayout.RawLine
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TextLayoutTest {
    /** Builds a line of text at (x, y) on a page rotated by [tiltDeg]. */
    private fun line(text: String, x: Double, y: Double, width: Double, height: Double, block: Int, tiltDeg: Double): RawLine {
        val t = Math.toRadians(tiltDeg)
        fun rot(px: Double, py: Double) = Pt(px * cos(t) - py * sin(t), px * sin(t) + py * cos(t))
        return RawLine(
            text,
            listOf(rot(x, y), rot(x + width, y), rot(x + width, y + height), rot(x, y + height)),
            block,
        )
    }

    @Test
    fun compactTiltedTwoColumnPageKeepsRowsSeparate() {
        // 30px tall text, 34px row pitch, page tilted 3 degrees, terms and definitions
        // in different ML Kit blocks (as happens with a wide gap between columns).
        val lines = mutableListOf<RawLine>()
        val terms = listOf("osmosis", "diffusion", "mitosis", "meiosis")
        val defs = listOf("water across a membrane", "spreading out of particles", "cell division", "gamete formation")
        terms.forEachIndexed { i, term ->
            lines += line(term, 0.0, i * 34.0, 200.0, 30.0, block = 0, tiltDeg = 3.0)
            lines += line(defs[i], 900.0, i * 34.0, 800.0, 30.0, block = 1, tiltDeg = 3.0)
        }
        // A big heading must not widen the row tolerance.
        lines.add(0, line("BIOLOGY", 0.0, -120.0, 600.0, 90.0, block = 2, tiltDeg = 3.0))

        val expected = "BIOLOGY\n" + terms.indices.joinToString("\n") { "${terms[it]}\t${defs[it]}" }
        assertEquals(expected, TextLayout.layout(lines))
    }

    @Test
    fun linesInTheSameBlockAreJoinedWithSpaces() {
        val lines = listOf(
            line("Hello", 0.0, 0.0, 100.0, 20.0, block = 0, tiltDeg = 0.0),
            line("world", 110.0, 0.0, 100.0, 20.0, block = 0, tiltDeg = 0.0),
            line("Next line", 0.0, 30.0, 200.0, 20.0, block = 0, tiltDeg = 0.0),
        )
        assertEquals("Hello world\nNext line", TextLayout.layout(lines))
    }
}
