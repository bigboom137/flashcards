package com.flashcards.ocr

import java.text.Normalizer

/** Post-processing for recognized text to undo common OCR artifacts. */
object OcrCleaner {
    // Combining marks left over after NFD decomposition (the two dots of an ü, the acute of an é, ...).
    private val combiningMarks = Regex("\\p{Mn}+")

    // Characters the recognizer often produces that never appear in typed English.
    private val replacements = mapOf(
        'ﬁ' to "fi", 'ﬂ' to "fl", 'ﬀ' to "ff", 'ﬃ' to "ffi", 'ﬄ' to "ffl",
        '‘' to "'", '’' to "'", '‚' to "'",
        '“' to "\"", '”' to "\"", '„' to "\"",
        'ı' to "i", 'ł' to "l", 'ø' to "o", 'đ' to "d",
        '¡' to "i", '¦' to "|",
    )

    /**
     * Removes accents and other diacritics, e.g. "ü" -> "u", "é" -> "e", "ñ" -> "n".
     * Useful when scanning English text, where the recognizer tends to invent
     * diacritics from specks and paper texture. Not for accented languages.
     */
    fun stripAccents(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val stripped = combiningMarks.replace(decomposed, "")
        return buildString(stripped.length) {
            for (c in stripped) append(replacements[c] ?: c)
        }
    }
}
