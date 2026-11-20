package com.flashcards.data

enum class SplitMode(val label: String, val hint: String) {
    AUTO("Auto-detect", "Picks the best of the modes below for this text"),
    QA("Q: / A:", "Lines starting with Q: and A: (or Question: / Answer:)"),
    SEPARATOR("Term – definition", "One card per line, split at the first  –  -  :  =  or tab"),
    ALTERNATE("Alternating lines", "Odd lines are fronts, even lines are backs"),
    BLOCKS("Blank-line blocks", "Paragraphs separated by blank lines; first line = front, rest = back"),
}

/** Turns free text (typed or scanned) into front/back card drafts. */
object CardParser {
    data class Draft(val front: String, val back: String)

    private val bulletRe = Regex("""^\s*(?:[-•*▪●■□○]|\d{1,3}[.)]|[a-zA-Z][.)])\s+""")
    private val qRe = Regex("""^\s*(?:Q|Question)\s*\d*\s*[:.)\-–]\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val aRe = Regex("""^\s*(?:A|Answer)\s*\d*\s*[:.)\-–]\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val sepRe = Regex("""\t+|\s+[–—]\s+|\s+-\s+|\s*:\s+|\s+=\s+|\s+->\s+|\s+→\s+""")

    fun parse(text: String, mode: SplitMode): List<Draft> {
        val lines = text.lines().map { it.trim() }
        val effective = if (mode == SplitMode.AUTO) detect(lines) else mode
        val drafts = when (effective) {
            SplitMode.QA -> parseQA(lines)
            SplitMode.SEPARATOR -> parseSeparator(lines)
            SplitMode.ALTERNATE -> parseAlternate(lines)
            SplitMode.BLOCKS -> parseBlocks(lines)
            SplitMode.AUTO -> emptyList()
        }
        return drafts
            .map { Draft(it.front.trim(), it.back.trim()) }
            .filter { it.front.isNotEmpty() }
    }

    fun detect(lines: List<String>): SplitMode {
        val nonEmpty = lines.filter { it.isNotBlank() }
        if (nonEmpty.isEmpty()) return SplitMode.ALTERNATE
        if (nonEmpty.any { qRe.matches(it) } && nonEmpty.any { aRe.matches(it) }) return SplitMode.QA
        val withSep = nonEmpty.count { sepRe.containsMatchIn(stripBullet(it)) }
        if (withSep >= nonEmpty.size * 0.6) return SplitMode.SEPARATOR
        val blocks = splitBlocks(lines)
        if (blocks.size >= 2 && blocks.all { it.size >= 2 }) return SplitMode.BLOCKS
        return SplitMode.ALTERNATE
    }

    private fun stripBullet(line: String) = bulletRe.replace(line, "")

    private fun parseQA(lines: List<String>): List<Draft> {
        val out = mutableListOf<Draft>()
        var front = StringBuilder()
        var back = StringBuilder()
        var inBack = false
        fun flush() {
            if (front.isNotBlank()) out += Draft(front.toString(), back.toString())
            front = StringBuilder()
            back = StringBuilder()
            inBack = false
        }
        for (raw in lines) {
            val line = raw.trim()
            val q = qRe.matchEntire(line)
            val a = aRe.matchEntire(line)
            when {
                q != null -> { flush(); front.append(q.groupValues[1]) }
                a != null -> {
                    inBack = true
                    if (back.isNotEmpty()) back.append('\n')
                    back.append(a.groupValues[1])
                }
                line.isEmpty() -> Unit
                inBack -> back.append('\n').append(line)
                else -> { if (front.isNotEmpty()) front.append('\n'); front.append(line) }
            }
        }
        flush()
        return out
    }

    private fun parseSeparator(lines: List<String>): List<Draft> {
        val out = mutableListOf<Draft>()
        for (raw in lines) {
            val line = stripBullet(raw.trim())
            if (line.isEmpty()) continue
            val m = sepRe.find(line)
            if (m != null && m.range.first > 0) {
                out += Draft(line.substring(0, m.range.first), line.substring(m.range.last + 1))
            } else if (out.isNotEmpty() && out.last().back.isNotEmpty() && looksLikeContinuation(line)) {
                // A wrapped line: continuation of the previous definition.
                val last = out.removeAt(out.lastIndex)
                out += last.copy(back = (last.back + " " + line).trim())
            } else {
                // Probably an entry whose separator the scan missed; keep it as its own
                // card so it shows up in the preview rather than vanishing into the
                // previous definition.
                out += Draft(line, "")
            }
        }
        return out
    }

    /** Wrapped definition lines start mid-sentence: lowercase, a digit, or punctuation. */
    private fun looksLikeContinuation(line: String): Boolean {
        val first = line.firstOrNull() ?: return false
        return !first.isUpperCase()
    }

    private fun parseAlternate(lines: List<String>): List<Draft> {
        val nonEmpty = lines.map { stripBullet(it.trim()) }.filter { it.isNotEmpty() }
        return nonEmpty.chunked(2).map { Draft(it[0], it.getOrElse(1) { "" }) }
    }

    private fun splitBlocks(lines: List<String>): List<List<String>> {
        val blocks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    blocks += current
                    current = mutableListOf()
                }
            } else {
                current += line
            }
        }
        if (current.isNotEmpty()) blocks += current
        return blocks
    }

    private fun parseBlocks(lines: List<String>): List<Draft> =
        splitBlocks(lines).map { block ->
            Draft(stripBullet(block.first()), block.drop(1).joinToString("\n"))
        }
}
