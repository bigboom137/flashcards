package com.flashcards.ocr

/**
 * Decides whether a recognized line is real text or noise: bleed-through from the
 * other side of the page, smudges, page edges. Such lines come back with low
 * confidence and full of tell-tale artifacts (digits inside words, capitals
 * mid-word, punctuation clusters).
 */
object LineFilter {
    /** Below this the line is dropped no matter what it says. */
    private const val HARD_MIN_CONFIDENCE = 0.5f

    /** Below this the line is dropped if it also looks like gibberish. */
    private const val SOFT_MIN_CONFIDENCE = 0.75f

    private const val WEIRD_TOKEN_RATIO = 0.3

    private val digit = Regex("\\d")
    private val letter = Regex("\\p{L}")
    private val lowerThenUpper = Regex("\\p{Ll}\\p{Lu}")
    private val punctCluster = Regex("[.,;:][.,;:-]|-[.,;:]")
    private val leadingPunct = Regex("^[.,;:]\\p{L}")
    private val unitLike = Regex("^\\d+(st|nd|rd|th|s|x|k|m|g|kg|mg|ml|l|mm|cm|km|am|pm|h|min|sec)$", RegexOption.IGNORE_CASE)
    private val wrapping = Regex("^[\"'(\\[{]+|[\"')\\]}!?]+$")

    fun isNoise(text: String, confidence: Float): Boolean {
        if (confidence < HARD_MIN_CONFIDENCE) return true
        if (confidence >= SOFT_MIN_CONFIDENCE) return false
        return weirdRatio(text) >= WEIRD_TOKEN_RATIO
    }

    fun weirdRatio(text: String): Double {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 0.0
        return tokens.count { isWeird(it) }.toDouble() / tokens.size
    }

    private fun isWeird(raw: String): Boolean {
        val t = raw.replace(wrapping, "")
        if (t.isEmpty()) return false
        if (digit.containsMatchIn(t) && letter.containsMatchIn(t)) {
            val letters = t.filter { it.isLetter() }
            // Chemical formulas (H2O, CO2) and units (5pm, 3rd) are fine.
            if (!letters.all { it.isUpperCase() } && !unitLike.matches(t)) return true
        }
        if (lowerThenUpper.containsMatchIn(t)) return true
        if (punctCluster.containsMatchIn(t)) return true
        if (leadingPunct.containsMatchIn(t)) return true
        return false
    }
}
