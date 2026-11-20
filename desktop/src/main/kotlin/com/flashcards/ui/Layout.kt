package com.flashcards.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Widths where the layout changes shape. A monitor is wide but eyes are not, so the
 * window is spent on putting more things side by side rather than on stretching one
 * column: lines of text and a flashcard stay the size they are comfortable to read.
 */
object Panes {
    /** At and above this, the deck list becomes a permanent sidebar instead of a screen. */
    val TWO_PANE = 900.dp

    /** The sidebar itself. */
    val SIDEBAR = 320.dp

    /** At and above this, forms split into side-by-side columns (input | preview). */
    val WIDE_FORM = 1000.dp

    /** The widest a single column of text or controls ever gets. */
    val READING = 1100.dp

    /** The widest the flashcard and its grading buttons get. */
    val CARD = 760.dp

    /** The widest a quiz question and its answers get. */
    val QUESTION = 900.dp
}

/** Centers [content] and stops it stretching past [maxWidth] on a wide window. */
@Composable
fun CenteredPane(maxWidth: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
