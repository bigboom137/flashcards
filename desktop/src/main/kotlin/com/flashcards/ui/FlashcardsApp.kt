package com.flashcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flashcards.AppViewModel

private sealed interface Screen {
    data object Decks : Screen
    data class Deck(val id: Long) : Screen
    data class Scan(val deckId: Long) : Screen
    data class Study(val deckId: Long, val reverse: Boolean) : Screen
    data class Quiz(val deckId: Long, val mode: QuizMode, val reverse: Boolean) : Screen
}

/** Studying, quizzing and scanning take the whole window; browsing shares it with the sidebar. */
private val Screen.isFocused: Boolean
    get() = this is Screen.Study || this is Screen.Quiz || this is Screen.Scan

/** Where each screen can send the user next, so the screens stay unaware of the layout. */
private class Nav(
    val twoPane: Boolean,
    val push: (Screen) -> Unit,
    val pop: () -> Unit,
    val openDeck: (Long) -> Unit,
    val closeDeck: () -> Unit,
)

/**
 * Same screens as the Android app, with a simple back stack in place of Navigation Compose.
 * On a desktop-sized window the deck list stays on the left and the deck being worked on
 * fills the rest; narrow windows fall back to one screen at a time, like the phone.
 */
@Composable
fun FlashcardsApp(vm: AppViewModel) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Decks) }
    val saveable = rememberSaveableStateHolder()

    fun popToDecks() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoPane = maxWidth >= Panes.TWO_PANE
            val screen = stack.last()
            val here = Nav(
                twoPane = twoPane,
                push = { stack += it },
                pop = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                // The sidebar never moves, so picking a deck swaps the detail pane instead
                // of stacking a second deck screen behind it.
                openDeck = { id -> popToDecks(); stack += Screen.Deck(id) },
                closeDeck = ::popToDecks,
            )

            if (twoPane && !screen.isFocused) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(Panes.SIDEBAR).fillMaxHeight()) {
                        DeckListScreen(
                            vm = vm,
                            sidebar = true,
                            selectedDeckId = (screen as? Screen.Deck)?.id,
                            onOpenDeck = here.openDeck,
                        )
                    }
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (screen is Screen.Deck) ScreenContent(screen, vm, saveable, here)
                        else NoDeckSelected()
                    }
                }
            } else {
                ScreenContent(screen, vm, saveable, here)
            }
        }
    }
}

@Composable
private fun ScreenContent(screen: Screen, vm: AppViewModel, saveable: SaveableStateHolder, nav: Nav) {
    saveable.SaveableStateProvider(screen.toString()) {
        when (screen) {
            Screen.Decks -> DeckListScreen(vm = vm, onOpenDeck = nav.openDeck)
            is Screen.Deck -> DeckDetailScreen(
                deckId = screen.id,
                vm = vm,
                // With the sidebar on screen there is nothing to go back to.
                onBack = if (nav.twoPane) null else nav.pop,
                onDeleted = nav.closeDeck,
                onScan = { nav.push(Screen.Scan(screen.id)) },
                onStudy = { reverse -> nav.push(Screen.Study(screen.id, reverse)) },
                onQuiz = { mode, reverse -> nav.push(Screen.Quiz(screen.id, mode, reverse)) },
            )
            is Screen.Scan -> ScanScreen(deckId = screen.deckId, vm = vm, onBack = nav.pop)
            is Screen.Study -> StudyScreen(deckId = screen.deckId, reverse = screen.reverse, vm = vm, onBack = nav.pop)
            is Screen.Quiz -> QuizScreen(
                deckId = screen.deckId,
                mode = screen.mode,
                reverse = screen.reverse,
                vm = vm,
                onBack = nav.pop,
            )
        }
    }
}

/** The right-hand pane before a deck has been picked. */
@Composable
private fun NoDeckSelected() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Style,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("Pick a deck", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose a deck on the left to see its cards, study it, or quiz yourself.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
