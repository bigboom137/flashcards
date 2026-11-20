package com.flashcards.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CardParserTest {
    private fun d(front: String, back: String) = CardParser.Draft(front, back)

    @Test
    fun separatorLinesAreSplit() {
        val text = """
            1. mitochondria - powerhouse of the cell
            2. ribosome: builds proteins
            nucleus – holds DNA
            golgi	packages proteins
        """.trimIndent()
        assertEquals(
            listOf(
                d("mitochondria", "powerhouse of the cell"),
                d("ribosome", "builds proteins"),
                d("nucleus", "holds DNA"),
                d("golgi", "packages proteins"),
            ),
            CardParser.parse(text, SplitMode.AUTO),
        )
    }

    @Test
    fun wrappedDefinitionLinesAreAppended() {
        val text = "osmosis - movement of water across\na membrane\nATP - energy currency"
        assertEquals(
            listOf(
                d("osmosis", "movement of water across a membrane"),
                d("ATP", "energy currency"),
            ),
            CardParser.parse(text, SplitMode.SEPARATOR),
        )
    }

    @Test
    fun questionAnswerPairs() {
        val text = """
            Q: What is the capital of France?
            A: Paris
            Q2. Who wrote Hamlet?
            A2. Shakespeare
        """.trimIndent()
        assertEquals(
            listOf(d("What is the capital of France?", "Paris"), d("Who wrote Hamlet?", "Shakespeare")),
            CardParser.parse(text, SplitMode.AUTO),
        )
    }

    @Test
    fun alternatingLinesFallback() {
        val text = "dog\nperro\ncat\ngato\nbird"
        assertEquals(
            listOf(d("dog", "perro"), d("cat", "gato"), d("bird", "")),
            CardParser.parse(text, SplitMode.AUTO),
        )
    }

    @Test
    fun blankLineBlocks() {
        val text = "Photosynthesis\nConverts light to energy\nHappens in chloroplasts\n\nRespiration\nReleases energy from glucose"
        assertEquals(
            listOf(
                d("Photosynthesis", "Converts light to energy\nHappens in chloroplasts"),
                d("Respiration", "Releases energy from glucose"),
            ),
            CardParser.parse(text, SplitMode.AUTO),
        )
    }

    @Test
    fun timesAreNotTreatedAsSeparators() {
        assertEquals(
            listOf(d("Meeting at 3:00", "conference room")),
            CardParser.parse("Meeting at 3:00 - conference room", SplitMode.SEPARATOR),
        )
    }
}

class CardParserContinuationTest {
    private fun d(front: String, back: String) = CardParser.Draft(front, back)

    @Test
    fun capitalizedLineWithoutSeparatorBecomesItsOwnCard() {
        val text = "Osmosis - water across a membrane\nDiffusion spreading of particles\nMitosis - cell division"
        assertEquals(
            listOf(
                d("Osmosis", "water across a membrane"),
                d("Diffusion spreading of particles", ""),
                d("Mitosis", "cell division"),
            ),
            CardParser.parse(text, SplitMode.SEPARATOR),
        )
    }

    @Test
    fun lowercaseLineStillContinuesTheDefinition() {
        val text = "Osmosis - water across\na membrane\nMitosis - cell division"
        assertEquals(
            listOf(d("Osmosis", "water across a membrane"), d("Mitosis", "cell division")),
            CardParser.parse(text, SplitMode.SEPARATOR),
        )
    }
}
