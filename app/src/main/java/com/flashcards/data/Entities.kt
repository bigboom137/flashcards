package com.flashcards.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Optional grouping shown as a section header in the deck list. */
    val folder: String? = null,
    /** Whether due cards in this deck trigger "time to review" notifications. */
    val notify: Boolean = true,
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Deck::class,
        parentColumns = ["id"],
        childColumns = ["deckId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("deckId")],
)
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    /** Leitner box: 0 = new / just failed, higher = longer review interval. */
    val box: Int = 0,
    /** Epoch millis when this card is next due. 0 = due now. */
    val dueAt: Long = 0,
    val reviews: Int = 0,
    val lapses: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One graded review, kept for stats (daily counts, streaks). */
@Entity(indices = [Index("at"), Index("deckId")])
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val deckId: Long,
    /** [Grade.ordinal] */
    val grade: Int,
    val at: Long,
)

data class DeckSummary(
    @Embedded val deck: Deck,
    val total: Int,
    val due: Int,
    /** Cards that have reached [Mastery.MASTERED_BOX] or above. */
    val mastered: Int,
)

data class DayCount(val day: Long, val count: Int)

object Mastery {
    /** Box from which a card counts as mastered (2+ day interval). */
    const val MASTERED_BOX = 3
    const val MAX_BOX = 5
}
