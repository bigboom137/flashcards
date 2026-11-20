package com.flashcards.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF4F5BD5)

private val LightScheme = lightColorScheme(primary = Indigo)
private val DarkScheme = darkColorScheme(primary = Color(0xFFB9C0FF))

/** Follows the Windows light / dark app mode. */
@Composable
fun FlashcardsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)
}
