package com.flashcards.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain-text card lists (tab/comma/semicolon separated, Anki-compatible) and the
 * whole-app JSON backup format.
 */
object CardFiles {
    // ---- Deck export / import (TSV) --------------------------------------------

    /** Tab-separated "front<TAB>back" lines, the format Anki imports directly. */
    fun exportTsv(cards: List<Card>): String = buildString {
        for (c in cards) {
            append(escapeCell(c.front)).append('\t').append(escapeCell(c.back)).append('\n')
        }
    }

    private fun escapeCell(s: String): String = s.replace("\t", "    ").replace("\r\n", "\n").replace("\n", "<br>")

    /**
     * Reads a text file of cards. Detects the delimiter (tab, comma or semicolon),
     * handles quoted CSV cells, skips a header row like "front,back", and ignores
     * Anki's "#..." directive lines.
     */
    fun importText(text: String): List<CardParser.Draft> {
        val lines = text.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) return emptyList()
        val sample = lines.take(20)
        val delimiter = listOf('\t', ',', ';').maxByOrNull { d -> sample.count { it.contains(d) } }
            ?.takeIf { d -> sample.count { it.contains(d) } >= sample.size / 2 }
        val rows = lines.map { if (delimiter == null) listOf(it) else splitDelimited(it, delimiter) }
        val body = if (looksLikeHeader(rows.first())) rows.drop(1) else rows
        return body.mapNotNull { cells ->
            val front = cells.getOrNull(0)?.replace("<br>", "\n")?.trim().orEmpty()
            val back = cells.getOrNull(1)?.replace("<br>", "\n")?.trim().orEmpty()
            if (front.isEmpty()) null else CardParser.Draft(front, back)
        }
    }

    private fun looksLikeHeader(cells: List<String>): Boolean {
        val a = cells.getOrNull(0)?.trim()?.lowercase() ?: return false
        val b = cells.getOrNull(1)?.trim()?.lowercase() ?: return false
        return a in setOf("front", "term", "question", "word") && b in setOf("back", "definition", "answer", "meaning")
    }

    /** Splits one line on [delimiter], honouring double-quoted cells with "" escapes. */
    fun splitDelimited(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                quoted && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> { cells += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        cells += cur.toString()
        return cells
    }

    // ---- Full backup (JSON) -------------------------------------------------------

    const val BACKUP_VERSION = 1

    class Backup(val decks: List<Deck>, val cards: List<Card>, val logs: List<ReviewLog>)

    fun toJson(b: Backup): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("decks", JSONArray().apply {
            b.decks.forEach { d ->
                put(
                    JSONObject().put("id", d.id).put("name", d.name).put("createdAt", d.createdAt)
                        .put("folder", d.folder ?: JSONObject.NULL).put("notify", d.notify)
                )
            }
        })
        root.put("cards", JSONArray().apply {
            b.cards.forEach { c ->
                put(
                    JSONObject().put("id", c.id).put("deckId", c.deckId).put("front", c.front).put("back", c.back)
                        .put("box", c.box).put("dueAt", c.dueAt).put("reviews", c.reviews).put("lapses", c.lapses)
                        .put("createdAt", c.createdAt)
                )
            }
        })
        root.put("logs", JSONArray().apply {
            b.logs.forEach { l ->
                put(JSONObject().put("cardId", l.cardId).put("deckId", l.deckId).put("grade", l.grade).put("at", l.at))
            }
        })
        return root.toString(2)
    }

    fun fromJson(json: String): Backup {
        val root = JSONObject(json)
        val decks = root.getJSONArray("decks").let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Deck(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    folder = if (o.isNull("folder")) null else o.optString("folder"),
                    notify = o.optBoolean("notify", true),
                )
            }
        }
        val cards = root.getJSONArray("cards").let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Card(
                    id = o.getLong("id"),
                    deckId = o.getLong("deckId"),
                    front = o.getString("front"),
                    back = o.optString("back"),
                    box = o.optInt("box"),
                    dueAt = o.optLong("dueAt"),
                    reviews = o.optInt("reviews"),
                    lapses = o.optInt("lapses"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        }
        val logs = root.optJSONArray("logs")?.let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ReviewLog(cardId = o.getLong("cardId"), deckId = o.getLong("deckId"), grade = o.getInt("grade"), at = o.getLong("at"))
            }
        } ?: emptyList()
        return Backup(decks, cards, logs)
    }
}
