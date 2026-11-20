package com.flashcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.flashcards.ui.FlashcardsApp
import com.flashcards.ui.LocalWindow
import com.flashcards.ui.WindowKeyDispatcher
import com.flashcards.ui.theme.FlashcardsTheme
import kotlinx.coroutines.delay

fun main() = application {
    val vm = remember { AppViewModel() }
    // Wide enough to open on the two-pane layout (deck list beside the deck) on any
    // normal monitor; the window is still free to be dragged down to one column.
    val windowState = rememberWindowState(size = DpSize(1440.dp, 900.dp), position = WindowPosition.Aligned(Alignment.Center))
    val trayState = rememberTrayState()
    val icon = rememberVectorPainter(Icons.Default.Style)

    // Desktop stand-in for the phone's review reminders: a Windows notification when
    // cards in decks with reminders on become due while the app is open or minimized.
    LaunchedEffect(Unit) {
        var lastDue = vm.notifiableDueCount()
        while (true) {
            delay(60_000)
            val due = vm.notifiableDueCount()
            if (due > lastDue && focusedWindow?.isFocused != true) {
                trayState.sendNotification(
                    Notification("Time to review", "$due card${if (due == 1) " is" else "s are"} due in Flashcards"),
                )
            }
            lastDue = due
        }
    }

    Tray(icon = icon, state = trayState, tooltip = "Flashcards")

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Flashcards",
        icon = icon,
        onPreviewKeyEvent = WindowKeyDispatcher::dispatch,
    ) {
        LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(520, 600) }
        focusedWindow = window
        CompositionLocalProvider(LocalWindow provides window) {
            FlashcardsTheme {
                FlashcardsApp(vm)
            }
        }
    }
}

/** Notifications are skipped while the user is looking at the app. */
private var focusedWindow: java.awt.Window? = null
