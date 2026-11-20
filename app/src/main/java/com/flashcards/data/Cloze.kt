package com.flashcards.data

/**
 * Cloze deletions: a card front like "The powerhouse of the cell is the {{mitochondria}}"
 * is shown with the braces hidden ("... is the ____") and reveals them on the back.
 */
object Cloze {
    private val pattern = Regex("\\{\\{(.+?)\\}\\}")

    fun isCloze(text: String): Boolean = pattern.containsMatchIn(text)

    /** The text with every deletion replaced by a blank. */
    fun hidden(text: String): String = pattern.replace(text) { "______" }

    /** The text with the braces removed, deletions left in place. */
    fun revealed(text: String): String = pattern.replace(text) { it.groupValues[1] }

    /** The deleted pieces, in order. */
    fun answers(text: String): List<String> = pattern.findAll(text).map { it.groupValues[1].trim() }.toList()

    /** What to type / pick in a quiz: all deletions joined. */
    fun answerText(text: String): String = answers(text).joinToString(", ")

    /** Character ranges of the deletions inside [revealed], for highlighting. */
    fun revealedRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var removed = 0
        for (m in pattern.findAll(text)) {
            val start = m.range.first - removed
            val len = m.groupValues[1].length
            ranges += start until start + len
            removed += 4 // the two pairs of braces
        }
        return ranges
    }
}
