package com.flashcards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flashcards.data.Card
import com.flashcards.data.CardFiles
import com.flashcards.data.CardParser
import com.flashcards.data.Deck
import com.flashcards.data.DeckSummary
import com.flashcards.data.Grade
import com.flashcards.data.Mastery
import com.flashcards.data.ReviewLog
import com.flashcards.data.Scheduler
import com.flashcards.data.Settings
import com.flashcards.data.Store
import com.flashcards.sync.SyncServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

/**
 * Desktop counterpart of the Android AppViewModel, with the same API so the screens
 * read the same. Everything lives in [Store], so reads and writes are synchronous.
 */
class AppViewModel(
    private val store: Store = Store(),
    private val prefs: Settings = Settings(),
) {
    private val data get() = store.data

    /** Emits the current time every [periodMs], so due counts refresh while the app stays open. */
    private fun clock(periodMs: Long = 30_000): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(periodMs)
        }
    }

    // ---- Stats -------------------------------------------------------------------

    private val dayMs = 86_400_000L
    private fun tzOffset() = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()

    fun stats(): Flow<StudyStats> = combine(data.map { it.logs }.distinctUntilChanged(), clock(60_000)) { logs, now ->
        val offset = tzOffset()
        val today = (now + offset) / dayMs
        val byDay = logs.groupingBy { (it.at + offset) / dayMs }.eachCount()
        val last7 = (today - 6..today).map { byDay[it] ?: 0 }
        // Streak: consecutive days ending today, or ending yesterday (still alive until midnight).
        val set = byDay.keys
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
    }.distinctUntilChanged { a, b ->
        a.today == b.today && a.streakDays == b.streakDays && a.streakAlive == b.streakAlive && a.last7 == b.last7
    }

    fun highScore(deckId: Long): Int = prefs.getInt("highscore_$deckId", 0)

    /** Returns true if this is a new record. */
    fun recordScore(deckId: Long, score: Int): Boolean {
        if (score <= highScore(deckId)) return false
        prefs.put("highscore_$deckId", score)
        return true
    }

    // ---- Remembered choices from the study / quiz setup dialogs ---------------

    var studyReverse: Boolean
        get() = prefs.getBoolean("study_reverse", false)
        set(value) = prefs.put("study_reverse", value)

    var quizReverse: Boolean
        get() = prefs.getBoolean("quiz_reverse", false)
        set(value) = prefs.put("quiz_reverse", value)

    var quizModeName: String
        get() = prefs.getString("quiz_mode") ?: "MULTIPLE_CHOICE"
        set(value) = prefs.put("quiz_mode", value)

    // ---- Queries -----------------------------------------------------------------

    fun deckSummaries(): Flow<List<DeckSummary>> = combine(data, clock()) { d, now ->
        val byDeck = d.cards.groupBy { it.deckId }
        d.decks
            .sortedWith(compareBy<Deck, String?>(nullsFirst()) { it.folder }.thenByDescending { it.createdAt })
            .map { deck ->
                val cards = byDeck[deck.id].orEmpty()
                DeckSummary(
                    deck = deck,
                    total = cards.size,
                    due = cards.count { it.dueAt <= now },
                    mastered = cards.count { it.box >= Mastery.MASTERED_BOX },
                )
            }
    }.distinctUntilChanged()

    fun deck(id: Long): Flow<Deck?> = data.map { d -> d.decks.firstOrNull { it.id == id } }.distinctUntilChanged()

    fun cards(deckId: Long): Flow<List<Card>> =
        data.map { d -> d.cards.filter { it.deckId == deckId }.sortedWith(compareBy({ it.createdAt }, { it.id })) }
            .distinctUntilChanged()

    fun folders(): Flow<List<String>> =
        data.map { d -> d.decks.mapNotNull { it.folder }.distinct().sorted() }.distinctUntilChanged()

    fun allDecks(): Flow<List<Deck>> = deckSummaries().map { list -> list.map { it.deck } }

    /** Cards due now in decks with reminders switched on (for the tray notification). */
    fun notifiableDueCount(now: Long = System.currentTimeMillis()): Int {
        val d = data.value
        val notify = d.decks.filter { it.notify }.map { it.id }.toHashSet()
        return d.cards.count { it.deckId in notify && it.dueAt <= now }
    }

    // ---- Decks ---------------------------------------------------------------------

    fun setDeckNotify(deck: Deck, notify: Boolean) = updateDeck(deck.id) { it.copy(notify = notify) }

    fun setDeckFolder(deck: Deck, folder: String?) =
        updateDeck(deck.id) { it.copy(folder = folder?.trim()?.ifBlank { null }) }

    fun createDeck(name: String, onCreated: (Long) -> Unit = {}) {
        var id = 0L
        store.update { d ->
            id = store.nextDeckId(d)
            d.copy(decks = d.decks + Deck(id = id, name = name.trim()))
        }
        onCreated(id)
    }

    fun renameDeck(deck: Deck, name: String) = updateDeck(deck.id) { it.copy(name = name.trim()) }

    fun deleteDeck(deck: Deck) = store.update { d ->
        d.copy(decks = d.decks.filter { it.id != deck.id }, cards = d.cards.filter { it.deckId != deck.id })
    }

    private fun updateDeck(id: Long, transform: (Deck) -> Deck) = store.update { d ->
        d.copy(decks = d.decks.map { if (it.id == id) transform(it) else it })
    }

    // ---- Cards ---------------------------------------------------------------------

    private fun insertCards(cards: List<Card>): List<Long> {
        var ids = emptyList<Long>()
        store.update { d ->
            val first = store.nextCardId(d)
            val withIds = cards.mapIndexed { i, c -> c.copy(id = first + i) }
            ids = withIds.map { it.id }
            d.copy(cards = d.cards + withIds)
        }
        return ids
    }

    private fun updateCards(changed: List<Card>) {
        val byId = changed.associateBy { it.id }
        store.update { d -> d.copy(cards = d.cards.map { byId[it.id] ?: it }) }
    }

    private fun deleteCardIds(ids: Collection<Long>) {
        val set = ids.toHashSet()
        store.update { d -> d.copy(cards = d.cards.filter { it.id !in set }) }
    }

    fun addCard(deckId: Long, front: String, back: String) {
        insertCards(listOf(Card(deckId = deckId, front = front.trim(), back = back.trim())))
    }

    /** Set after a batch add; the deck screen offers to undo it. */
    var undoableAdd by mutableStateOf<UndoableAdd?>(null)
        private set

    fun addCards(deckId: Long, drafts: List<CardParser.Draft>, onDone: () -> Unit = {}) {
        val ids = insertCards(drafts.map { Card(deckId = deckId, front = it.front, back = it.back) })
        undoableAdd = UndoableAdd(deckId, ids)
        onDone()
    }

    fun undoLastAdd() {
        val u = undoableAdd ?: return
        undoableAdd = null
        deleteCardIds(u.ids)
    }

    fun dismissUndo() {
        undoableAdd = null
    }

    // ---- Bulk edits ---------------------------------------------------------------

    fun deleteCards(cards: List<Card>) = deleteCardIds(cards.map { it.id })

    /** Combines several cards into the first one, joining fronts and backs. */
    fun mergeCards(cards: List<Card>) {
        if (cards.size < 2) return
        val first = cards.first()
        val merged = first.copy(
            front = cards.map { it.front.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" / "),
            back = cards.map { it.back.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" / "),
        )
        val drop = cards.drop(1).map { it.id }.toHashSet()
        store.update { d ->
            d.copy(cards = d.cards.filter { it.id !in drop }.map { if (it.id == merged.id) merged else it })
        }
    }

    fun swapSides(cards: List<Card>) = updateCards(cards.map { it.copy(front = it.back, back = it.front) })

    fun moveCards(cards: List<Card>, toDeckId: Long) = updateCards(cards.map { it.copy(deckId = toDeckId) })

    // ---- Import / export / backup ------------------------------------------------

    fun exportDeckTsv(deckId: Long): String =
        CardFiles.exportTsv(data.value.cards.filter { it.deckId == deckId }.sortedWith(compareBy({ it.dueAt }, { it.id })))

    fun importText(deckId: Long, text: String, onDone: (Int) -> Unit) {
        val drafts = CardFiles.importText(text)
        if (drafts.isEmpty()) { onDone(0); return }
        addCards(deckId, drafts) { onDone(drafts.size) }
    }

    fun backupJson(): String = data.value.let { CardFiles.toJson(CardFiles.Backup(it.decks, it.cards, it.logs)) }

    // ---- Wi-Fi sharing with the phone ----------------------------------------------

    /** Replaces the library with what the phone merged and sent back. */
    private fun acceptMergedLibrary(json: String) {
        val b = CardFiles.fromJson(json)
        store.update { Store.Data(b.decks, b.cards, b.logs.mapIndexed { i, l -> l.copy(id = i + 1L) }) }
        session = null
        syncActivity = "Library updated from the phone"
    }

    private val syncServer = SyncServer(
        readLibrary = { backupJson() },
        writeLibrary = { acceptMergedLibrary(it) },
        onActivity = { syncActivity = it },
    )

    var sharing by mutableStateOf(false)
        private set

    /** The last thing the sharing server did, shown under the switch. */
    var syncActivity by mutableStateOf<String?>(null)
        private set

    val syncCode: String get() = syncServer.code
    val syncAddress: String get() = "${syncServer.localAddress()}:${syncServer.port}"
    val syncHostName: String get() = syncServer.hostName

    fun shareOverWifi(on: Boolean) {
        if (on) {
            syncActivity = null
            sharing = syncServer.start()
            if (!sharing) syncActivity = "Could not start sharing"
        } else {
            syncServer.stop()
            sharing = false
            syncActivity = null
        }
    }

    /** Replaces everything with the backup's contents. */
    fun restore(json: String, onDone: (Result<Int>) -> Unit) {
        val result = runCatching {
            val b = CardFiles.fromJson(json)
            store.update { Store.Data(b.decks, b.cards, b.logs.mapIndexed { i, l -> l.copy(id = i + 1L) }) }
            b.cards.size
        }
        session = null
        onDone(result)
    }

    /** The last answer's result, for feedback like "back in 3 days". */
    var lastAnswered by mutableStateOf<Card?>(null)
        private set

    fun updateCard(card: Card) {
        session?.replaceCurrent(card)
        updateCards(listOf(card))
    }

    fun deleteCard(card: Card) = deleteCardIds(listOf(card.id))

    fun resetProgress(deckId: Long) = store.update { d ->
        d.copy(cards = d.cards.map { if (it.deckId == deckId) it.copy(box = 0, dueAt = 0) else it })
    }

    // ---- Study session -------------------------------------------------------

    var session by mutableStateOf<StudySession?>(null)
        private set

    /** Kept for API parity with Android, where sessions load from the database asynchronously. */
    var sessionLoading by mutableStateOf(false)
        private set

    fun startSession(deckId: Long, onlyDue: Boolean) {
        val now = System.currentTimeMillis()
        val cards = data.value.cards
            .filter { it.deckId == deckId && (!onlyDue || it.dueAt <= now) }
            .sortedWith(compareBy({ it.dueAt }, { it.id }))
        session = StudySession(deckId, cards.shuffled(), onlyDue)
    }

    /** Starts a session for [deckId] unless one is already running for it. */
    fun ensureSession(deckId: Long) {
        val s = session
        if (s != null && s.deckId == deckId) return
        startSession(deckId, onlyDue = true)
    }

    fun endSession() {
        session = null
    }

    /** Records the grade for the current card. Only [Grade.REPEAT] puts it back into this session. */
    fun answer(grade: Grade) {
        val s = session ?: return
        val card = s.current ?: return
        val now = System.currentTimeMillis()
        val updated = Scheduler.apply(card, grade, now)
        lastAnswered = updated
        store.update { d ->
            d.copy(
                cards = d.cards.map { if (it.id == card.id) updated else it },
                logs = d.logs + ReviewLog(id = store.nextLogId(d), cardId = card.id, deckId = card.deckId, grade = grade.ordinal, at = now),
            )
        }
        s.advance(grade, requeue = if (grade == Grade.REPEAT) updated else null)
    }
}
