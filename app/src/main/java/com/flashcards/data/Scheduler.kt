package com.flashcards.data

import kotlin.math.max
import kotlin.math.min

/** How well a card was known when reviewed. */
enum class Grade(val label: String) {
    /** Did not know it; show it again in this session. */
    REPEAT("Repeat"),
    /** Struggled; back to the start of the ladder, due again in 10 minutes. */
    HARD("Hard"),
    /** Knew it with effort; stay on the current rung. */
    OKAY("Okay"),
    /** Knew it cold; move up a rung. */
    EASY("Easy"),
}

/**
 * Leitner-style spaced repetition with a per-card timer that adapts to how often the
 * card has been missed: a word missed 3 times waits ~half as long as a word never
 * missed, so it gets more practice. Intervals are capped at 4 days.
 */
object Scheduler {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    private val intervals = longArrayOf(
        10 * MINUTE, // box 0
        6 * HOUR,    // box 1
        1 * DAY,     // box 2
        2 * DAY,     // box 3
        3 * DAY,     // box 4
        4 * DAY,     // box 5
    )

    /** Fraction of the normal interval a card waits, given how many times it was marked hard. */
    fun lapsePenalty(lapses: Int): Double = (1.0 / (1.0 + 0.3 * lapses)).coerceAtLeast(0.35)

    fun apply(card: Card, grade: Grade, now: Long = System.currentTimeMillis()): Card {
        val reviews = card.reviews + 1
        return when (grade) {
            Grade.REPEAT -> card.copy(box = 0, dueAt = now, reviews = reviews, lapses = card.lapses + 1)
            Grade.HARD -> card.copy(box = 0, dueAt = now + intervals[0], reviews = reviews, lapses = card.lapses + 1)
            Grade.OKAY -> {
                val box = max(card.box, 1)
                card.copy(box = box, dueAt = now + wait(box, card.lapses), reviews = reviews)
            }
            Grade.EASY -> {
                val box = min(card.box + 1, intervals.lastIndex)
                card.copy(box = box, dueAt = now + wait(box, card.lapses), reviews = reviews)
            }
        }
    }

    private fun wait(box: Int, lapses: Int): Long = (intervals[box] * lapsePenalty(lapses)).toLong()

    /** Human-readable duration, e.g. "10 min", "3 hours", "2 days". */
    fun describe(durationMs: Long): String {
        val ms = durationMs.coerceAtLeast(0)
        return when {
            ms < HOUR -> "${(ms / MINUTE).coerceAtLeast(1)} min"
            ms < DAY -> plural(ms / HOUR, "hour")
            ms < 30 * DAY -> plural(ms / DAY, "day")
            else -> plural(ms / (30 * DAY), "month")
        }
    }

    private fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"}"
}
