package com.flashcards.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val SUMMARY_QUERY = """
    SELECT d.*,
        (SELECT COUNT(*) FROM Card c WHERE c.deckId = d.id) AS total,
        (SELECT COUNT(*) FROM Card c WHERE c.deckId = d.id AND c.dueAt <= :now) AS due,
        (SELECT COUNT(*) FROM Card c WHERE c.deckId = d.id AND c.box >= ${Mastery.MASTERED_BOX}) AS mastered
    FROM Deck d ORDER BY d.folder, d.createdAt DESC
"""

@Dao
interface DeckDao {
    @Query(SUMMARY_QUERY)
    fun observeSummaries(now: Long): Flow<List<DeckSummary>>

    @Query(SUMMARY_QUERY)
    suspend fun summaries(now: Long): List<DeckSummary>

    @Query("SELECT * FROM Deck WHERE id = :id")
    fun observeDeck(id: Long): Flow<Deck?>

    @Query("SELECT * FROM Deck ORDER BY name")
    suspend fun getAll(): List<Deck>

    @Query("SELECT DISTINCT folder FROM Deck WHERE folder IS NOT NULL ORDER BY folder")
    fun observeFolders(): Flow<List<String>>

    @Insert
    suspend fun insert(deck: Deck): Long

    @Update
    suspend fun update(deck: Deck)

    @Delete
    suspend fun delete(deck: Deck)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM Card WHERE deckId = :deckId ORDER BY createdAt, id")
    fun observeCards(deckId: Long): Flow<List<Card>>

    @Query("SELECT * FROM Card WHERE deckId = :deckId ORDER BY dueAt, id")
    suspend fun getCards(deckId: Long): List<Card>

    @Query("SELECT * FROM Card ORDER BY deckId, id")
    suspend fun getAll(): List<Card>

    @Query("SELECT * FROM Card WHERE deckId = :deckId AND dueAt <= :now ORDER BY dueAt, id")
    suspend fun getDueCards(deckId: Long, now: Long): List<Card>

    @Query("SELECT COUNT(*) FROM Card WHERE dueAt <= :now")
    suspend fun countDue(now: Long): Int

    /** Next due time among decks that have notifications switched on. */
    @Query(
        """
        SELECT MIN(c.dueAt) FROM Card c JOIN Deck d ON d.id = c.deckId
        WHERE c.dueAt > :now AND d.notify = 1
        """
    )
    suspend fun nextNotifiableDueAfter(now: Long): Long?

    @Insert
    suspend fun insertAll(cards: List<Card>): List<Long>

    @Update
    suspend fun update(card: Card)

    @Update
    suspend fun updateAll(cards: List<Card>)

    @Delete
    suspend fun delete(card: Card)

    @Query("DELETE FROM Card WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE Card SET deckId = :deckId WHERE id IN (:ids)")
    suspend fun moveToDeck(ids: List<Long>, deckId: Long)
}

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLog)

    @Insert
    suspend fun insertAll(logs: List<ReviewLog>)

    @Query("SELECT * FROM ReviewLog ORDER BY at")
    suspend fun getAll(): List<ReviewLog>

    /** Reviews per local day (day index = floor((at + tzOffset) / 86400000)). */
    @Query(
        """
        SELECT ((at + :tzOffset) / 86400000) AS day, COUNT(*) AS count
        FROM ReviewLog WHERE at >= :since GROUP BY day ORDER BY day
        """
    )
    fun observeDaily(since: Long, tzOffset: Long): Flow<List<DayCount>>

    @Query("SELECT DISTINCT ((at + :tzOffset) / 86400000) AS day FROM ReviewLog ORDER BY day DESC")
    fun observeActiveDays(tzOffset: Long): Flow<List<Long>>

    @Query("DELETE FROM ReviewLog")
    suspend fun clear()
}
