package com.flashcards.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {
    private val hour = 60 * 60_000L
    private val day = 24 * hour
    private val card = Card(id = 1, deckId = 1, front = "a", back = "b")

    @Test
    fun easyMovesUpAndHardResets() {
        val now = 1_000_000L
        val easy = Scheduler.apply(card, Grade.EASY, now = now)
        assertEquals(1, easy.box)
        assertEquals(now + 6 * hour, easy.dueAt)
        val hard = Scheduler.apply(easy, Grade.HARD, now = now)
        assertEquals(0, hard.box)
        assertEquals(1, hard.lapses)
        assertEquals(now + 10 * 60_000L, hard.dueAt)
    }

    @Test
    fun okayKeepsTheRungAndRepeatStaysDue() {
        val now = 5_000L
        val okay = Scheduler.apply(card.copy(box = 3), Grade.OKAY, now = now)
        assertEquals(3, okay.box)
        assertEquals(now + 2 * day, okay.dueAt)
        val okayNew = Scheduler.apply(card, Grade.OKAY, now = now)
        assertEquals(1, okayNew.box)
        val repeat = Scheduler.apply(card.copy(box = 3), Grade.REPEAT, now = now)
        assertEquals(0, repeat.box)
        assertEquals(now, repeat.dueAt)
        assertEquals(1, repeat.lapses)
    }

    @Test
    fun oftenMissedWordsComeBackSooner() {
        val fresh = Scheduler.apply(card.copy(box = 2), Grade.EASY, now = 0L)
        val missedThrice = Scheduler.apply(card.copy(box = 2, lapses = 3), Grade.EASY, now = 0L)
        assertEquals(2 * day, fresh.dueAt)
        assertTrue(missedThrice.dueAt < fresh.dueAt)
        assertTrue(missedThrice.dueAt >= 2 * day * 0.35)
    }

    @Test
    fun neverWaitsMoreThanFourDays() {
        var c = card
        repeat(12) { c = Scheduler.apply(c, Grade.EASY, now = 0L) }
        assertEquals(4 * day, c.dueAt)
    }

    @Test
    fun describesDurations() {
        assertEquals("10 min", Scheduler.describe(10 * 60_000L))
        assertEquals("3 hours", Scheduler.describe(3 * hour))
        assertEquals("1 day", Scheduler.describe(day))
        assertEquals("12 days", Scheduler.describe(12 * day))
        assertEquals("2 months", Scheduler.describe(65 * day))
    }
}
