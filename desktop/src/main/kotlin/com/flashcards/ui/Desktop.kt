package com.flashcards.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The main window, as parent for native file dialogs. */
val LocalWindow = staticCompositionLocalOf<Frame?> { null }

/** Native Windows "Open" dialog. [pattern] like "*.json;*.txt" filters the list. */
fun openFile(parent: Frame?, title: String, pattern: String? = null): File? {
    val dialog = FileDialog(parent, title, FileDialog.LOAD)
    if (pattern != null) dialog.file = pattern
    dialog.isVisible = true
    return dialog.files.firstOrNull()
}

/** Native Windows "Save as" dialog. */
fun saveFile(parent: Frame?, title: String, suggestedName: String): File? {
    val dialog = FileDialog(parent, title, FileDialog.SAVE)
    dialog.file = suggestedName.replace(Regex("""[\\/:*?"<>|]"""), "_")
    dialog.isVisible = true
    return dialog.files.firstOrNull()
}

/**
 * Keyboard shortcuts that work anywhere in the window (Space to flip, 1-4 to grade, ...).
 * Screens register handlers with [WindowKeys]; the newest one gets each key first. While
 * any dialog is open the handlers are skipped, so typing in a text field never grades a card.
 */
object WindowKeyDispatcher {
    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()
    private var openDialogs = 0

    fun dispatch(event: KeyEvent): Boolean {
        if (openDialogs > 0 || event.type != KeyEventType.KeyDown) return false
        return handlers.asReversed().any { it(event) }
    }

    internal fun add(h: (KeyEvent) -> Boolean) { handlers += h }
    internal fun remove(h: (KeyEvent) -> Boolean) { handlers -= h }
    internal fun dialogOpened() { openDialogs++ }
    internal fun dialogClosed() { openDialogs-- }
}

/** Handles key presses (KeyDown only) while this composable is on screen. Return true to consume. */
@Composable
fun WindowKeys(onKey: (KeyEvent) -> Boolean) {
    val current = rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        val h: (KeyEvent) -> Boolean = { current.value(it) }
        WindowKeyDispatcher.add(h)
        onDispose { WindowKeyDispatcher.remove(h) }
    }
}

/** Call inside a dialog to pause window-wide shortcuts while it is open. */
@Composable
fun PauseWindowKeys() {
    DisposableEffect(Unit) {
        WindowKeyDispatcher.dialogOpened()
        onDispose { WindowKeyDispatcher.dialogClosed() }
    }
}
