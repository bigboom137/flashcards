package com.flashcards.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClozeTest {
    private val text = "The {{mitochondria}} is the powerhouse of the {{cell}}."

    @Test
    fun detectsHidesAndReveals() {
        assertTrue(Cloze.isCloze(text))
        assertFalse(Cloze.isCloze("plain card"))
        assertEquals("The ______ is the powerhouse of the ______.", Cloze.hidden(text))
        assertEquals("The mitochondria is the powerhouse of the cell.", Cloze.revealed(text))
        assertEquals(listOf("mitochondria", "cell"), Cloze.answers(text))
        assertEquals("mitochondria, cell", Cloze.answerText(text))
    }

    @Test
    fun revealedRangesPointAtTheAnswers() {
        val revealed = Cloze.revealed(text)
        val ranges = Cloze.revealedRanges(text)
        assertEquals("mitochondria", revealed.substring(ranges[0]))
        assertEquals("cell", revealed.substring(ranges[1]))
    }
}

class CardFilesTest {
    @Test
    fun importsTabCommaAndSemicolonFiles() {
        val tsv = "amicus\tfriend\nbellum\twar\n"
        assertEquals(listOf(CardParser.Draft("amicus", "friend"), CardParser.Draft("bellum", "war")), CardFiles.importText(tsv))

        val csv = "front,back\n\"carpe, diem\",\"seize the day\"\nnihil,nothing\n"
        assertEquals(listOf(CardParser.Draft("carpe, diem", "seize the day"), CardParser.Draft("nihil", "nothing")), CardFiles.importText(csv))

        val anki = "#separator:tab\n#html:true\ngaudium\tjoy<br>gladness\n"
        assertEquals(listOf(CardParser.Draft("gaudium", "joy\ngladness")), CardFiles.importText(anki))
    }

    @Test
    fun exportRoundTripsThroughImport() {
        val cards = listOf(
            Card(deckId = 1, front = "quoque", back = "also"),
            Card(deckId = 1, front = "libertas", back = "freedom,\nliberty"),
        )
        val drafts = CardFiles.importText(CardFiles.exportTsv(cards))
        assertEquals(cards.map { CardParser.Draft(it.front, it.back) }, drafts)
    }

    @Test
    fun backupRoundTrips() {
        val backup = CardFiles.Backup(
            decks = listOf(
                Deck(id = 3, name = "Latin", createdAt = 5, folder = "School"),
                Deck(id = 4, name = "Bio", createdAt = 6, notify = false),
            ),
            cards = listOf(Card(id = 10, deckId = 3, front = "inquit", back = "says", box = 2, dueAt = 99, reviews = 4, lapses = 1, createdAt = 7)),
            logs = listOf(ReviewLog(cardId = 10, deckId = 3, grade = Grade.EASY.ordinal, at = 123)),
        )
        val restored = CardFiles.fromJson(CardFiles.toJson(backup))
        assertEquals(backup.decks, restored.decks)
        assertEquals(backup.cards, restored.cards)
        assertEquals(backup.logs.map { it.copy(id = 0) }, restored.logs.map { it.copy(id = 0) })
    }
}
