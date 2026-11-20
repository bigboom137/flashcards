package com.flashcards.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineFilterTest {
    @Test
    fun bleedThroughGibberishIsDropped() {
        // Real lines seen from a mirrored page on the reverse side of thin paper.
        listOf(
            "VisludsooV IIX 19qsrd" to 0.56f,
            "biol .m i-,aunimob" to 0.62f,
            "Ihip .i 9s- slleuq" to 0.50f,
            "9V6le .M i-2uV192" to 0.39f,
            "biow .n i-,mudi9v" to 0.51f,
            "992 of 919-,09biv" to 0.66f,
            "biow - oudhev" to 0.47f,
        ).forEach { (text, conf) -> assertTrue(text, LineFilter.isNoise(text, conf)) }
    }

    @Test
    fun realLinesAreKeptEvenAtLowConfidence() {
        listOf(
            "quoque" to 0.60f,
            "war" to 0.63f,
            "Says, said" to 0.77f,
            "dominus, -i m. lord" to 0.70f,
            "H2O - water" to 0.65f,
            "Meet at 5pm on the 3rd" to 0.62f,
            "inquit" to 0.78f,
        ).forEach { (text, conf) -> assertFalse(text, LineFilter.isNoise(text, conf)) }
    }

    @Test
    fun confidentLinesAreNeverDroppedForLookingOdd() {
        assertFalse(LineFilter.isNoise("iPhone 15Pro", 0.9f))
    }
}
