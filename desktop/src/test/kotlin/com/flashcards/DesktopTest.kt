package com.flashcards

import com.flashcards.data.CardParser
import com.flashcards.data.Grade
import com.flashcards.data.Settings
import com.flashcards.data.Store
import com.flashcards.ocr.WindowsOcr
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files

class StoreTest {
    private val dir: File = Files.createTempDirectory("flashcards-test").toFile()
    private fun vm() = AppViewModel(Store(File(dir, "data.json")), Settings(File(dir, "settings.properties")))

    @Test
    fun cardsAndReviewsSurviveRestart() {
        val vm = vm()
        var deckId = 0L
        vm.createDeck("Latin") { deckId = it }
        vm.addCards(deckId, listOf(CardParser.Draft("amo", "I love"), CardParser.Draft("video", "I see")))
        vm.startSession(deckId, onlyDue = true)
        assertEquals(2, vm.session!!.total)
        vm.answer(Grade.EASY)
        vm.answer(Grade.REPEAT)
        vm.studyReverse = true

        val reopened = vm()
        val backup = org.json.JSONObject(reopened.backupJson())
        assertEquals(1, backup.getJSONArray("decks").length())
        assertEquals(2, backup.getJSONArray("cards").length())
        assertEquals(2, backup.getJSONArray("logs").length())
        assertTrue(reopened.studyReverse)
        // Only the card graded Repeat is still due.
        reopened.startSession(deckId, onlyDue = true)
        assertEquals(1, reopened.session!!.total)
    }

    @Test
    fun undoRemovesTheBatchAndRestoreReplacesEverything() {
        val vm = vm()
        var deckId = 0L
        vm.createDeck("Bio") { deckId = it }
        vm.addCard(deckId, "cell", "unit of life")
        val backup = vm.backupJson()
        vm.addCards(deckId, listOf(CardParser.Draft("a", "b"), CardParser.Draft("c", "d")))
        vm.undoLastAdd()
        assertNull(vm.undoableAdd)
        assertEquals(1, org.json.JSONObject(vm.backupJson()).getJSONArray("cards").length())

        vm.createDeck("Other")
        var restored = -1
        vm.restore(backup) { restored = it.getOrThrow() }
        assertEquals(1, restored)
        assertEquals(1, org.json.JSONObject(vm.backupJson()).getJSONArray("decks").length())
    }
}

class WindowsOcrTest {
    @Test
    fun readsRenderedText() = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val img = BufferedImage(900, 260, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().run {
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            color = Color.WHITE
            fillRect(0, 0, img.width, img.height)
            color = Color.BLACK
            font = Font("Arial", Font.PLAIN, 40)
            drawString("photosynthesis - making food from light", 30, 80)
            drawString("osmosis - water moving through a membrane", 30, 180)
            dispose()
        }
        val text = WindowsOcr.recognize(img).text
        val cards = CardParser.parse(text, com.flashcards.data.SplitMode.AUTO)
        assertEquals(text, 2, cards.size)
        assertEquals("photosynthesis", cards[0].front.lowercase())
        assertEquals("osmosis", cards[1].front.lowercase())
    }
}
