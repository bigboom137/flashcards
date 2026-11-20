package com.flashcards.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {
    @Test
    fun ignoresCaseAccentsAndPunctuation() {
        assertTrue(AnswerMatcher.matches("resume", "Résumé!"))
        assertTrue(AnswerMatcher.matches("amo, amare", "amō amāre"))
        assertTrue(AnswerMatcher.matches("  seize   the day ", "Seize the day."))
        assertTrue(AnswerMatcher.matches("uber", "Über"))
    }

    @Test
    fun anyAlternativeCounts() {
        assertTrue(AnswerMatcher.matches("said", "says, said"))
        assertTrue(AnswerMatcher.matches("says", "says, said"))
        assertTrue(AnswerMatcher.matches("large", "big or large"))
        assertTrue(AnswerMatcher.matches("freedom", "freedom / liberty"))
    }

    @Test
    fun leadingToOrArticleIsOptional() {
        assertTrue(AnswerMatcher.matches("love", "to love"))
        assertTrue(AnswerMatcher.matches("the cell", "cell"))
    }

    @Test
    fun bracketedAsidesAreOptional() {
        assertTrue(AnswerMatcher.matches("says", "says, said (3rd person)"))
        assertTrue(AnswerMatcher.matches("said", "says, said (3rd person)"))
        assertTrue(AnswerMatcher.matches("to carry", "to carry (across)"))
        assertTrue(AnswerMatcher.matches("carry", "to carry (across)"))
        // Typing the aside as well still counts.
        assertTrue(AnswerMatcher.matches("to carry across", "to carry (across)"))
        assertTrue(AnswerMatcher.matches("love", "(to) love"))
        // Brackets the user types are ignored too.
        assertTrue(AnswerMatcher.matches("friend (close)", "friend"))
        assertTrue(AnswerMatcher.matches("war [noun]", "war"))
    }

    @Test
    fun wrongAnswersFail() {
        assertFalse(AnswerMatcher.matches("friend", "enemy"))
        assertFalse(AnswerMatcher.matches("", "anything"))
        assertFalse(AnswerMatcher.matches("say", "says, said"))
        assertFalse(AnswerMatcher.matches("across", "to carry (across)"))
        assertFalse(AnswerMatcher.matches("(across)", "to carry (across)"))
    }
}
