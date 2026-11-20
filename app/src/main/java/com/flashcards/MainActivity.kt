package com.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.flashcards.notifications.ReviewReminders
import com.flashcards.ui.FlashcardsApp
import com.flashcards.ui.theme.FlashcardsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReviewReminders.ensureChannel(this)
        ReviewReminders.schedulePeriodic(this)
        setContent {
            FlashcardsTheme {
                FlashcardsApp()
            }
        }
    }
}
