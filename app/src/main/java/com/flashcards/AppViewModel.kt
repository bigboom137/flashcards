package com.flashcards

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.flashcards.data.AppDatabase
import com.flashcards.data.Card
import com.flashcards.data.CardFiles
import com.flashcards.data.CardParser
import com.flashcards.data.DayCount
import com.flashcards.data.Deck
import com.flashcards.data.DeckSummary
import com.flashcards.data.Grade
import com.flashcards.data.ReviewLog
import com.flashcards.data.Scheduler
import com.flashcards.data.SyncMerge
import com.flashcards.sync.SyncClient
import com.flashcards.sync.SyncProtocol
import com.flashcards.notifications.ReviewReminders
import com.flashcards.widget.DueWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.TimeZone

/** In-memory state for one study session. Cards graded Repeat come back after a few other cards. */
class StudySession(val deckId: Long, cards: List<Card>, val onlyDue: Boolean) {
    /** How many other cards are shown before a repeated card comes back around. */
    private val requeueGap = 3

    private val queue = ArrayDeque(cards)
    val total = cards.size

    var current by mutableStateOf(queue.removeFirstOrNull())
        private set
    var remaining by mutableIntStateOf(queue.size)
        private set
    var reviewed by mutableIntStateOf(0)
        private set
    var easyCount by mutableIntStateOf(0)
        private set
    var okayCount by mutableIntStateOf(0)
        private set
    var hardCount by mutableIntStateOf(0)
        private set
    var repeatCount by mutableIntStateOf(0)
        private set

    /** Reviews that were known (easy or okay). */
    val knownCount: Int get() = easyCount + okayCount

    /** Consecutive known answers right now, and the best run this session. */
    var streak by mutableIntStateOf(0)
        private set
    var bestStreak by mutableIntStateOf(0)
        private set

    /** True when the current card was already shown this session (it came back as a repeat). */
    var currentIsRepeat by mutableStateOf(false)
        private set

    private val seen = HashSet<Long>()

    init {
        current?.let { seen += it.id }
    }

    /** Swaps in an edited version of the card being shown (same id). */
    fun replaceCurrent(card: Card) {
        if (current?.id == card.id) current = card
    }

    fun advance(grade: Grade, requeue: Card?) {
        when (grade) {
            Grade.EASY -> easyCount++
            Grade.OKAY -> okayCount++
            Grade.HARD -> hardCount++
            Grade.REPEAT -> repeatCount++
        }
        if (grade == Grade.EASY || grade == Grade.OKAY) {
            streak++
            if (streak > bestStreak) bestStreak = streak
        } else {
            streak = 0
        }
        reviewed++
        requeue?.let { queue.add(minOf(requeueGap, queue.size), it) }
        current = queue.removeFirstOrNull()
        currentIsRepeat = current?.let { !seen.add(it.id) } ?: false
        remaining = queue.size
    }
}

/** Daily study stats for the home screen. */
class StudyStats(val today: Int, val streakDays: Int, val streakAlive: Boolean, val last7: List<Int>)

/** A batch of cards that was just added and can still be undone. */
class UndoableAdd(val deckId: Long, val ids: List<Long>, val token: Long = System.nanoTime())

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val deckDao = db.deckDao()
    private val cardDao = db.cardDao()
    private val logDao = db.reviewLogDao()

    init {
        onDataChanged()
    }

    /** Re-arms reminders and refreshes the home-screen widget after anything that changes due counts. */
    private fun onDataChanged() {
        viewModelScope.launch {
            ReviewReminders.scheduleNext(getApplication())
            DueWidget.update(getApplication())
        }
    }

    // ---- Stats -------------------------------------------------------------------

    private val dayMs = 86_400_000L
    private fun tzOffset() = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
    private fun todayIndex() = (System.currentTimeMillis() + tzOffset()) / dayMs

    fun stats(): Flow<StudyStats> {
        val offset = tzOffset()
        val today = todayIndex()
        val daily = logDao.observeDaily(since = (today - 6) * dayMs - offset, tzOffset = offset)
        val active = logDao.observeActiveDays(offset)
        return combine(daily, active) { counts, days ->
            val byDay = counts.associate { it.day to it.count }
            val last7 = (today - 6..today).map { byDay[it] ?: 0 }
            // Streak: consecutive days ending today, or ending yesterday (still alive until midnight).
            val set = days.toHashSet()
            val start = when {
                today in set -> today
                (today - 1) in set -> today - 1
                else -> null
            }
            var streak = 0
            if (start != null) {
                var d = start
                while (d in set) { streak++; d-- }
            }
            StudyStats(today = byDay[today] ?: 0, streakDays = streak, streakAlive = today in set, last7 = last7)
        }
    }

    fun highScore(deckId: Long): Int = prefs.getInt("highscore_$deckId", 0)

    /** Returns true if this is a new record. */
    fun recordScore(deckId: Long, score: Int): Boolean {
        if (score <= highScore(deckId)) return false
        prefs.edit().putInt("highscore_$deckId", score).apply()
        return true
    }

    // ---- Remembered choices from the study / quiz setup dialogs ---------------

    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    var studyReverse: Boolean
        get() = prefs.getBoolean("study_reverse", false)
        set(value) = prefs.edit().putBoolean("study_reverse", value).apply()

    var quizReverse: Boolean
        get() = prefs.getBoolean("quiz_reverse", false)
        set(value) = prefs.edit().putBoolean("quiz_reverse", value).apply()

    var quizModeName: String
        get() = prefs.getString("quiz_mode", null) ?: "MULTIPLE_CHOICE"
        set(value) = prefs.edit().putString("quiz_mode", value).apply()

    fun deckSummaries(): Flow<List<DeckSummary>> = deckDao.observeSummaries(System.currentTimeMillis())
    fun deck(id: Long): Flow<Deck?> = deckDao.observeDeck(id)
    fun cards(deckId: Long): Flow<List<Card>> = cardDao.observeCards(deckId)
    fun folders(): Flow<List<String>> = deckDao.observeFolders()
    fun allDecks(): Flow<List<Deck>> = deckDao.observeSummaries(0).map { list -> list.map { it.deck } }

    fun setDeckNotify(deck: Deck, notify: Boolean) {
        viewModelScope.launch {
            deckDao.update(deck.copy(notify = notify))
            onDataChanged()
        }
    }

    fun setDeckFolder(deck: Deck, folder: String?) {
        viewModelScope.launch { deckDao.update(deck.copy(folder = folder?.trim()?.ifBlank { null })) }
    }

    fun createDeck(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch { onCreated(deckDao.insert(Deck(name = name.trim()))) }
    }

    fun renameDeck(deck: Deck, name: String) {
        viewModelScope.launch { deckDao.update(deck.copy(name = name.trim())) }
    }

    fun deleteDeck(deck: Deck) {
        viewModelScope.launch {
            deckDao.delete(deck)
            onDataChanged()
        }
    }

    fun addCard(deckId: Long, front: String, back: String) {
        viewModelScope.launch { cardDao.insertAll(listOf(Card(deckId = deckId, front = front.trim(), back = back.trim()))) }
    }

    /** Set after a batch add; the deck screen offers to undo it. */
    var undoableAdd by mutableStateOf<UndoableAdd?>(null)
        private set

    fun addCards(deckId: Long, drafts: List<CardParser.Draft>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val ids = cardDao.insertAll(drafts.map { Card(deckId = deckId, front = it.front, back = it.back) })
            undoableAdd = UndoableAdd(deckId, ids)
            onDataChanged()
            onDone()
        }
    }

    fun undoLastAdd() {
        val u = undoableAdd ?: return
        undoableAdd = null
        viewModelScope.launch {
            cardDao.deleteByIds(u.ids)
            onDataChanged()
        }
    }

    fun dismissUndo() {
        undoableAdd = null
    }

    // ---- Bulk edits ---------------------------------------------------------------

    fun deleteCards(cards: List<Card>) {
        viewModelScope.launch {
            cardDao.deleteByIds(cards.map { it.id })
            onDataChanged()
        }
    }

    /** Combines several cards into the first one, joining fronts and backs. */
    fun mergeCards(cards: List<Card>) {
        if (cards.size < 2) return
        viewModelScope.launch {
            val first = cards.first()
            val merged = first.copy(
                front = cards.map { it.front.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" / "),
                back = cards.map { it.back.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" / "),
            )
            cardDao.update(merged)
            cardDao.deleteByIds(cards.drop(1).map { it.id })
            onDataChanged()
        }
    }

    fun swapSides(cards: List<Card>) {
        viewModelScope.launch { cardDao.updateAll(cards.map { it.copy(front = it.back, back = it.front) }) }
    }

    fun moveCards(cards: List<Card>, toDeckId: Long) {
        viewModelScope.launch {
            cardDao.moveToDeck(cards.map { it.id }, toDeckId)
            onDataChanged()
        }
    }

    // ---- Import / export / backup ------------------------------------------------

    suspend fun exportDeckTsv(deckId: Long): String = CardFiles.exportTsv(cardDao.getCards(deckId))

    fun importText(deckId: Long, text: String, onDone: (Int) -> Unit) {
        val drafts = CardFiles.importText(text)
        if (drafts.isEmpty()) { onDone(0); return }
        addCards(deckId, drafts) { onDone(drafts.size) }
    }

    suspend fun backupJson(): String =
        CardFiles.toJson(CardFiles.Backup(deckDao.getAll(), cardDao.getAll(), logDao.getAll()))

    /** Replaces everything with the backup's contents. */
    fun restore(json: String, onDone: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val b = CardFiles.fromJson(json)
                db.clearAllTables()
                b.decks.forEach { deckDao.insert(it) }
                cardDao.insertAll(b.cards)
                logDao.insertAll(b.logs)
                b.cards.size
            }
            session = null
            onDataChanged()
            onDone(result)
        }
    }

    // ---- Wi-Fi sync with the desktop app ------------------------------------------

    /** What the sync dialog is doing right now. */
    sealed interface SyncState {
        data object Idle : SyncState
        data object Searching : SyncState
        class Found(val hosts: List<SyncProtocol.HostInfo>) : SyncState
        class Working(val step: String) : SyncState
        class Done(val summary: String) : SyncState
        class Failed(val message: String) : SyncState
    }

    var syncState by mutableStateOf<SyncState>(SyncState.Idle)
        private set

    fun resetSync() {
        syncState = SyncState.Idle
    }

    fun discoverDesktops() {
        syncState = SyncState.Searching
        viewModelScope.launch {
            val hosts = SyncClient.discover()
            syncState = SyncState.Found(hosts)
        }
    }

    /**
     * Merges this phone's library with the desktop's and sends the result back, so
     * both ends hold the union. Nothing is deleted by a sync.
     */
    fun syncWith(host: String, port: Int, code: String) {
        syncState = SyncState.Working("Connecting\u2026")
        viewModelScope.launch {
            val result = runCatching {
                val remoteJson = SyncClient.fetch(host, port, code)
                syncState = SyncState.Working("Merging\u2026")
                val remote = CardFiles.fromJson(remoteJson)
                val local = CardFiles.Backup(deckDao.getAll(), cardDao.getAll(), logDao.getAll())
                val merged = SyncMerge.merge(local, remote)

                db.withTransaction {
                    db.clearAllTables()
                    merged.backup.decks.forEach { deckDao.insert(it) }
                    cardDao.insertAll(merged.backup.cards)
                    logDao.insertAll(merged.backup.logs)
                }

                syncState = SyncState.Working("Sending to the computer\u2026")
                SyncClient.push(host, port, code, CardFiles.toJson(merged.backup))
                merged.stats.describe()
            }
            session = null
            onDataChanged()
            syncState = result.fold(
                onSuccess = { SyncState.Done(it) },
                onFailure = { SyncState.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    /** The last answer's result, for feedback like "back in 3 days". */
    var lastAnswered by mutableStateOf<Card?>(null)
        private set

    fun updateCard(card: Card) {
        session?.replaceCurrent(card)
        viewModelScope.launch { cardDao.update(card) }
    }

    fun deleteCard(card: Card) {
        viewModelScope.launch { cardDao.delete(card) }
    }

    fun resetProgress(deckId: Long) {
        viewModelScope.launch {
            cardDao.updateAll(cardDao.getCards(deckId).map { it.copy(box = 0, dueAt = 0) })
            onDataChanged()
        }
    }

    // ---- Study session -------------------------------------------------------

    var session by mutableStateOf<StudySession?>(null)
        private set

    /** True while a session is being loaded from the database. */
    var sessionLoading by mutableStateOf(false)
        private set

    fun startSession(deckId: Long, onlyDue: Boolean) {
        sessionLoading = true
        viewModelScope.launch {
            val cards = if (onlyDue) cardDao.getDueCards(deckId, System.currentTimeMillis())
            else cardDao.getCards(deckId)
            session = StudySession(deckId, cards.shuffled(), onlyDue)
            sessionLoading = false
        }
    }

    /**
     * Starts a session for [deckId] unless one is already running for it. The screen
     * can be recreated mid-session (rotation, keyboard, backgrounding); restarting
     * then would drop cards that were just graded and no longer count as due.
     */
    fun ensureSession(deckId: Long) {
        val s = session
        if (sessionLoading || (s != null && s.deckId == deckId)) return
        startSession(deckId, onlyDue = true)
    }

    fun endSession() {
        session = null
        onDataChanged()
    }

    /** Records the grade for the current card. Only [Grade.REPEAT] puts it back into this session. */
    fun answer(grade: Grade) {
        val s = session ?: return
        val card = s.current ?: return
        val updated = Scheduler.apply(card, grade)
        lastAnswered = updated
        viewModelScope.launch {
            cardDao.update(updated)
            logDao.insert(ReviewLog(cardId = card.id, deckId = card.deckId, grade = grade.ordinal, at = System.currentTimeMillis()))
        }
        s.advance(grade, requeue = if (grade == Grade.REPEAT) updated else null)
    }
}
