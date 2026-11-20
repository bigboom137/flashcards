package com.flashcards.data

import java.text.Normalizer

/**
 * Lenient comparison for typed quiz answers: case, accents/macrons (é = e, ā = a),
 * punctuation and extra spaces are all ignored; anything in brackets is optional
 * ("to carry (across)" accepts either form); and any one of several alternatives in
 * the stored answer ("says, said" / "big or large") counts as correct.
 */
object AnswerMatcher {
    private val marks = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^\\p{L}\\p{N}]+")
    private val alternatives = Regex("[,;/]|\\bor\\b", RegexOption.IGNORE_CASE)
    private val leadingFiller = Regex("^(to|the|a|an) ")
    private val bracketed = Regex("[(\\[{][^)\\]}]*[)\\]}]")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase()
            .replace(nonAlnum, " ")
            .trim()

    /** Drops every bracketed aside: "to carry (across)" -> "to carry". */
    fun withoutBrackets(text: String): String = bracketed.replace(text, " ")

    /** Every spelling of [text] that should be accepted. */
    private fun variants(text: String): Set<String> {
        val forms = setOf(text, withoutBrackets(text))
        return forms
            .flatMap { form -> listOf(form) + form.split(alternatives) }
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .flatMap { listOf(it, it.replace(leadingFiller, "")) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun matches(typed: String, answer: String): Boolean {
        val typedForms = variants(typed)
        if (typedForms.isEmpty()) return false
        return variants(answer).any { it in typedForms }
    }
}
