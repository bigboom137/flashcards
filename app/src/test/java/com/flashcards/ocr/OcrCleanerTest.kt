package com.flashcards.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrCleanerTest {
    @Test
    fun stripsInventedDiacritics() {
        assertEquals("mitochondria - powerhouse", OcrCleaner.stripAccents("mïtochöndria - pöwerhoüse"))
        assertEquals("resume, naive, Uber", OcrCleaner.stripAccents("résumé, naïve, Über"))
    }

    @Test
    fun normalizesLigaturesAndQuotes() {
        assertEquals("first \"office\" it's", OcrCleaner.stripAccents("ﬁrst “oﬃce” it’s"))
    }

    @Test
    fun leavesPlainTextAndPunctuationAlone() {
        val s = "Q: What is 3 - 2?\tA: 1 – one\nend."
        assertEquals(s, OcrCleaner.stripAccents(s))
    }
}
