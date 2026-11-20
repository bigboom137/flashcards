package com.flashcards.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.flashcards.MainActivity
import com.flashcards.data.AppDatabase

/** Home-screen widget: "N cards due", tap to open the app. */
class DueWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val due = db.cardDao().countDue(now)
        val decks = db.deckDao().summaries(now).filter { it.due > 0 }.map { it.deck.name }
        provideContent {
            GlanceTheme {
                Content(due = due, decks = decks)
            }
        }
    }

    @Composable
    private fun Content(due: Int, decks: List<String>) {
        val onBg = GlanceTheme.colors.onSurface
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$due",
                    style = TextStyle(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (due > 0) GlanceTheme.colors.primary else onBg,
                    ),
                )
                Spacer(GlanceModifier.height(0.dp).padding(start = 6.dp))
                Text(
                    if (due == 1) "card due" else "cards due",
                    style = TextStyle(fontSize = 14.sp, color = onBg),
                    modifier = GlanceModifier.padding(bottom = 6.dp),
                )
            }
            Text(
                when {
                    due == 0 -> "All caught up"
                    decks.size <= 2 -> decks.joinToString(", ")
                    else -> "${decks.take(2).joinToString(", ")} +${decks.size - 2}"
                },
                style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.Gray)),
                maxLines = 1,
            )
        }
    }

    companion object {
        suspend fun update(context: Context) {
            runCatching { DueWidget().updateAll(context) }
        }
    }
}

class DueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DueWidget()
}
