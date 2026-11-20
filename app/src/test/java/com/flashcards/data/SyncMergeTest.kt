package com.flashcards.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun deck(id: Long, name: String, folder: String? = null) = Deck(id = id, name = name, folder = folder)

    private fun card(
        id: Long, deckId: Long, front: String, back: String,
        box: Int = 0, reviews: Int = 0, dueAt: Long = 0,
    ) = Card(id = id, deckId = deckId, front = front, back = back, box = box, reviews = reviews, dueAt = dueAt)

    private fun backupOf(decks: List<Deck>, cards: List<Card>, logs: List<ReviewLog> = emptyList()) =
        CardFiles.Backup(decks, cards, logs)

    @Test
    fun keepsCardsFromBothSides() {
        val local = backupOf(
            listOf(deck(1, "Latin")),
            listOf(card(1, 1, "amicus", "friend"), card(2, 1, "bellum", "war")),
        )
        // The phone numbers its rows differently; matching is by text, not id.
        val remote = backupOf(
            listOf(deck(7, "Latin")),
            listOf(card(50, 7, "bellum", "war"), card(51, 7, "nihil", "nothing")),
        )

        val merged = SyncMerge.merge(local, remote)
        val fronts = merged.backup.cards.map { it.front }.sorted()
        assertEquals(listOf("amicus", "bellum", "nihil"), fronts)
        assertEquals(1, merged.backup.decks.size)
        assertEquals(1, merged.stats.cardsAdded)
        assertEquals(0, merged.stats.decksAdded)
    }

    @Test
    fun matchingIgnoresCaseAndSpacing() {
        val local = backupOf(listOf(deck(1, "Latin")), listOf(card(1, 1, "Carpe  Diem", "seize the day")))
        val remote = backupOf(listOf(deck(2, "latin")), listOf(card(9, 2, "carpe diem", "Seize The Day")))

        val merged = SyncMerge.merge(local, remote)
        assertEquals(1, merged.backup.decks.size)
        assertEquals(1, merged.backup.cards.size)
        assertEquals(0, merged.stats.cardsAdded)
    }

    @Test
    fun theMoreStudiedCopyWins() {
        val local = backupOf(listOf(deck(1, "Latin")), listOf(card(1, 1, "nihil", "nothing", box = 1, reviews = 2, dueAt = 100)))
        val remote = backupOf(listOf(deck(1, "Latin")), listOf(card(1, 1, "nihil", "nothing", box = 4, reviews = 9, dueAt = 900)))

        val merged = SyncMerge.merge(local, remote)
        val card = merged.backup.cards.single()
        assertEquals(4, card.box)
        assertEquals(9, card.reviews)
        assertEquals(900, card.dueAt)
        assertEquals(1, merged.stats.cardsUpdated)
    }

    @Test
    fun localProgressSurvivesWhenItIsAhead() {
        val local = backupOf(listOf(deck(1, "Latin")), listOf(card(1, 1, "nihil", "nothing", box = 5, reviews = 12)))
        val remote = backupOf(listOf(deck(1, "Latin")), listOf(card(1, 1, "nihil", "nothing", box = 0, reviews = 0)))

        val merged = SyncMerge.merge(local, remote)
        assertEquals(5, merged.backup.cards.single().box)
        assertEquals(0, merged.stats.cardsUpdated)
    }

    @Test
    fun newDecksAndFoldersComeAcross() {
        val local = backupOf(listOf(deck(1, "Latin")), listOf(card(1, 1, "a", "b")))
        val remote = backupOf(
            listOf(deck(1, "Latin", folder = "School"), deck(2, "Biology", folder = "School")),
            listOf(card(1, 1, "a", "b"), card(2, 2, "osmosis", "water")),
        )

        val merged = SyncMerge.merge(local, remote)
        assertEquals(2, merged.backup.decks.size)
        assertEquals(1, merged.stats.decksAdded)
        // A deck with no folder here adopts the other side's.
        assertEquals("School", merged.backup.decks.first { it.name == "Latin" }.folder)
        val bio = merged.backup.decks.first { it.name == "Biology" }
        assertEquals(1, merged.backup.cards.count { it.deckId == bio.id })
    }

    @Test
    fun identicalCardsInDifferentDecksStaySeparate() {
        val local = backupOf(
            listOf(deck(1, "Latin"), deck(2, "Spanish")),
            listOf(card(1, 1, "casa", "house"), card(2, 2, "casa", "house")),
        )
        val merged = SyncMerge.merge(local, backupOf(emptyList(), emptyList()))
        assertEquals(2, merged.backup.cards.size)
    }

    @Test
    fun idsAreRenumberedAndCardsStayWithTheirDeck() {
        val local = backupOf(listOf(deck(40, "Latin")), listOf(card(70, 40, "a", "b")))
        val remote = backupOf(listOf(deck(3, "Bio")), listOf(card(5, 3, "c", "d")))

        val merged = SyncMerge.merge(local, remote)
        assertEquals(listOf(1L, 2L), merged.backup.decks.map { it.id })
        assertEquals(listOf(1L, 2L), merged.backup.cards.map { it.id })
        for (card in merged.backup.cards) {
            assertNotNull(merged.backup.decks.firstOrNull { it.id == card.deckId })
        }
        val latin = merged.backup.decks.first { it.name == "Latin" }
        assertEquals(latin.id, merged.backup.cards.first { it.front == "a" }.deckId)
    }

    @Test
    fun reviewLogsAreUnionedWithoutDuplicates() {
        val shared = ReviewLog(id = 1, cardId = 1, deckId = 1, grade = Grade.EASY.ordinal, at = 1000)
        val local = backupOf(
            listOf(deck(1, "Latin")),
            listOf(card(1, 1, "a", "b")),
            listOf(shared, ReviewLog(id = 2, cardId = 1, deckId = 1, grade = Grade.HARD.ordinal, at = 2000)),
        )
        val remote = backupOf(
            listOf(deck(9, "Latin")),
            listOf(card(4, 9, "a", "b")),
            // Same review as the local one, plus one the phone has not seen.
            listOf(
                ReviewLog(id = 1, cardId = 4, deckId = 9, grade = Grade.EASY.ordinal, at = 1000),
                ReviewLog(id = 2, cardId = 4, deckId = 9, grade = Grade.OKAY.ordinal, at = 3000),
            ),
        )

        val merged = SyncMerge.merge(local, remote)
        assertEquals(listOf(1000L, 2000L, 3000L), merged.backup.logs.map { it.at })
        assertEquals(listOf(1L, 2L, 3L), merged.backup.logs.map { it.id })
    }

    @Test
    fun mergingIsSymmetricAndStable() {
        val a = backupOf(
            listOf(deck(1, "Latin", folder = "School")),
            listOf(card(1, 1, "amicus", "friend", reviews = 3), card(2, 1, "bellum", "war")),
        )
        val b = backupOf(
            listOf(deck(1, "Latin"), deck(2, "Bio")),
            listOf(card(1, 1, "amicus", "friend", reviews = 1), card(2, 2, "osmosis", "water")),
        )

        val ab = SyncMerge.merge(a, b).backup
        val ba = SyncMerge.merge(b, a).backup
        assertEquals(
            ab.cards.map { it.front to it.back }.toSet(),
            ba.cards.map { it.front to it.back }.toSet(),
        )
        assertEquals(ab.decks.map { it.name }.toSet(), ba.decks.map { it.name }.toSet())
        // The better-studied copy wins no matter which side it came from.
        assertEquals(3, ba.cards.first { it.front == "amicus" }.reviews)

        // Syncing again changes nothing.
        val again = SyncMerge.merge(ab, ab)
        assertEquals(ab.cards.size, again.backup.cards.size)
        assertTrue(again.stats.isEmpty)
    }

    @Test
    fun emptyLibrarySyncCopiesEverything() {
        val remote = backupOf(
            listOf(deck(1, "Latin")),
            listOf(card(1, 1, "a", "b"), card(2, 1, "c", "d")),
        )
        val merged = SyncMerge.merge(backupOf(emptyList(), emptyList()), remote)
        assertEquals(2, merged.backup.cards.size)
        assertEquals(1, merged.stats.decksAdded)
        assertEquals(2, merged.stats.cardsAdded)
        assertEquals("1 deck, 2 new cards", merged.stats.describe())
    }

    @Test
    fun surviveRoundTripThroughJson() {
        val local = backupOf(listOf(deck(1, "Latin", folder = "School")), listOf(card(1, 1, "a", "b", box = 2, reviews = 4)))
        val remote = CardFiles.fromJson(CardFiles.toJson(local.let { backupOf(listOf(deck(5, "Bio")), listOf(card(8, 5, "x", "y"))) }))
        val merged = SyncMerge.merge(local, remote)
        val json = CardFiles.toJson(merged.backup)
        val reloaded = CardFiles.fromJson(json)
        assertEquals(merged.backup.cards.size, reloaded.cards.size)
        assertEquals(merged.backup.decks.map { it.name }, reloaded.decks.map { it.name })
    }
}
