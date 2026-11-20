package com.flashcards.ocr

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Rebuilds reading order from recognized lines.
 *
 * ML Kit groups text into blocks, so a two-column page (term | definition) would come
 * back as every term followed by every definition. Instead we place lines on a
 * de-skewed page, merge lines that share a baseline into one row, and separate pieces
 * from different blocks with a tab so the parser can split them into front/back.
 */
object TextLayout {
    data class Pt(val x: Double, val y: Double)

    /** One recognized line with its four corners (clockwise from top-left). */
    data class RawLine(val text: String, val corners: List<Pt>, val block: Int)

    private class Placed(val text: String, val left: Double, val y: Double, val height: Double, val block: Int)

    /**
     * @param knownSkew page tilt in radians measured from the image (see [SkewDetector]).
     *   When null it is estimated from the lines themselves, which is much noisier.
     */
    fun layout(raw: List<RawLine>, knownSkew: Double? = null): String {
        val usable = raw.filter { it.corners.size == 4 && it.text.isNotBlank() }
        if (usable.isEmpty()) return raw.joinToString("\n") { it.text }

        // Fallback tilt = width-weighted median angle of the top edge of every line.
        val skew = knownSkew ?: weightedMedian(usable.map { l ->
            val (tl, tr) = l.corners
            atan2(tr.y - tl.y, tr.x - tl.x) to hypot(tr.x - tl.x, tr.y - tl.y)
        })
        val c = cos(-skew)
        val s = sin(-skew)
        fun rotX(p: Pt) = p.x * c - p.y * s
        fun rotY(p: Pt) = p.x * s + p.y * c

        val placed = usable.map { l ->
            val (tl, _, _, bl) = l.corners
            // Height along the line's own vertical edge, which unlike the axis-aligned
            // bounding box does not grow when the photo is tilted.
            val height = hypot(bl.x - tl.x, bl.y - tl.y)
            val cy = l.corners.map { rotY(it) }.average()
            Placed(l.text, rotX(tl), cy, height, l.block)
        }

        // Two lines are on the same row if their centers are closer than ~half a
        // typical line height. Median, so a big heading cannot inflate the tolerance.
        val tolerance = median(placed.map { it.height }) * 0.6

        val rows = mutableListOf<MutableList<Placed>>()
        for (line in placed.sortedBy { it.y }) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(row.map { it.y }.average() - line.y) < tolerance) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }

        return rows.joinToString("\n") { row ->
            val ordered = row.sortedBy { it.left }
            buildString {
                ordered.forEachIndexed { i, l ->
                    if (i > 0) append(if (l.block != ordered[i - 1].block) "\t" else " ")
                    append(l.text)
                }
            }
        }
    }

    private fun weightedMedian(valuesWithWeights: List<Pair<Double, Double>>): Double {
        val sorted = valuesWithWeights.sortedBy { it.first }
        val half = sorted.sumOf { it.second } / 2
        var acc = 0.0
        for ((v, w) in sorted) {
            acc += w
            if (acc >= half) return v
        }
        return sorted.lastOrNull()?.first ?: 0.0
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
