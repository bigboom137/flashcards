package com.flashcards.data

/**
 * Two-way merge of two libraries (phone and desktop), used by Wi-Fi sync.
 *
 * Both sides end up with the union of their decks and cards. Nothing is ever deleted
 * by a sync: a card missing on one side is treated as "not yet seen there", not as a
 * deletion, because the two devices have no shared history to tell those apart.
 *
 * Ids are local to each device, so matching is by content: decks by name, cards by
 * their front and back text within a deck. The merged library is renumbered from 1,
 * which means a sync replaces the whole library on both sides.
 */
object SyncMerge {
    class Result(val backup: CardFiles.Backup, val stats: Stats)

    /** What a merge changed, relative to the side the counts are reported to. */
    class Stats(val decksAdded: Int, val cardsAdded: Int, val cardsUpdated: Int) {
        val isEmpty: Boolean get() = decksAdded == 0 && cardsAdded == 0 && cardsUpdated == 0

        fun describe(): String = when {
            isEmpty -> "Already up to date"
            else -> buildList {
                if (decksAdded > 0) add("$decksAdded deck${if (decksAdded == 1) "" else "s"}")
                if (cardsAdded > 0) add("$cardsAdded new card${if (cardsAdded == 1) "" else "s"}")
                if (cardsUpdated > 0) add("$cardsUpdated updated")
            }.joinToString(", ")
        }
    }

    private fun key(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun deckKey(deck: Deck) = key(deck.name)
    private fun cardKey(card: Card) = key(card.front) to key(card.back)

    /**
     * Which of two versions of the same card to keep: the one that has been studied
     * more, then the one further up the Leitner ladder, then the one scheduled later.
     */
    private fun newer(a: Card, b: Card): Card = when {
        a.reviews != b.reviews -> if (a.reviews > b.reviews) a else b
        a.box != b.box -> if (a.box > b.box) a else b
        else -> if (a.dueAt >= b.dueAt) a else b
    }

    fun merge(local: CardFiles.Backup, remote: CardFiles.Backup): Result {
        var decksAdded = 0
        var cardsAdded = 0
        var cardsUpdated = 0

        // ---- Decks: local first, then any deck only the remote side has.
        val mergedDecks = mutableListOf<Deck>()
        val deckIdByKey = mutableMapOf<String, Long>()
        var nextDeckId = 1L
        for (deck in local.decks) {
            val k = deckKey(deck)
            if (k in deckIdByKey) continue
            deckIdByKey[k] = nextDeckId
            mergedDecks += deck.copy(id = nextDeckId)
            nextDeckId++
        }
        for (deck in remote.decks) {
            val k = deckKey(deck)
            val existingId = deckIdByKey[k]
            if (existingId == null) {
                deckIdByKey[k] = nextDeckId
                mergedDecks += deck.copy(id = nextDeckId)
                nextDeckId++
                decksAdded++
            } else {
                // Keep local settings, but adopt a folder if this side has none.
                val index = mergedDecks.indexOfFirst { it.id == existingId }
                val mine = mergedDecks[index]
                if (mine.folder == null && deck.folder != null) mergedDecks[index] = mine.copy(folder = deck.folder)
            }
        }

        // ---- Cards: union per deck, best scheduling state wins.
        val localDeckKeyById = local.decks.associate { it.id to deckKey(it) }
        val remoteDeckKeyById = remote.decks.associate { it.id to deckKey(it) }

        // Keyed by (deck key, front key, back key) so the same card in two decks stays separate.
        val merged = LinkedHashMap<Triple<String, String, String>, Card>()
        val fromLocal = HashSet<Triple<String, String, String>>()
        for (card in local.cards) {
            val dk = localDeckKeyById[card.deckId] ?: continue
            val (f, b) = cardKey(card)
            val k = Triple(dk, f, b)
            fromLocal += k
            merged[k] = merged[k]?.let { newer(it, card) } ?: card
        }
        for (card in remote.cards) {
            val dk = remoteDeckKeyById[card.deckId] ?: continue
            val (f, b) = cardKey(card)
            val k = Triple(dk, f, b)
            val mine = merged[k]
            if (mine == null) {
                merged[k] = card
                cardsAdded++
            } else {
                val winner = newer(mine, card)
                if (winner !== mine && k in fromLocal) cardsUpdated++
                merged[k] = winner
            }
        }

        val mergedCards = mutableListOf<Card>()
        val cardIdByKey = mutableMapOf<Triple<String, String, String>, Long>()
        var nextCardId = 1L
        for ((k, card) in merged) {
            val deckId = deckIdByKey[k.first] ?: continue
            cardIdByKey[k] = nextCardId
            mergedCards += card.copy(id = nextCardId, deckId = deckId)
            nextCardId++
        }

        // ---- Review logs: union, de-duplicated by card, time and grade.
        val seen = HashSet<Triple<Long, Int, Long>>()
        val mergedLogs = mutableListOf<ReviewLog>()
        var nextLogId = 1L
        for ((logs, deckKeyById, cards) in listOf(
            Triple(local.logs, localDeckKeyById, local.cards),
            Triple(remote.logs, remoteDeckKeyById, remote.cards),
        )) {
            val cardById = cards.associateBy { it.id }
            for (log in logs.sortedBy { it.at }) {
                val card = cardById[log.cardId]
                val dk = card?.let { deckKeyById[it.deckId] } ?: deckKeyById[log.deckId] ?: continue
                val cardId = card?.let { cardIdByKey[Triple(dk, key(it.front), key(it.back))] } ?: 0L
                val deckId = deckIdByKey[dk] ?: continue
                if (!seen.add(Triple(log.at, log.grade, cardId))) continue
                mergedLogs += ReviewLog(id = nextLogId++, cardId = cardId, deckId = deckId, grade = log.grade, at = log.at)
            }
        }

        return Result(
            CardFiles.Backup(mergedDecks, mergedCards, mergedLogs.sortedBy { it.at }),
            Stats(decksAdded, cardsAdded, cardsUpdated),
        )
    }
}
