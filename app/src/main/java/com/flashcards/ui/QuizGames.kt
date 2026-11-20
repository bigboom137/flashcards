package com.flashcards.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flashcards.AppViewModel
import com.flashcards.data.AnswerMatcher
import com.flashcards.data.Card
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/**
 * Picks wrong answers that resemble the right one (similar length, same first letter)
 * so multiple choice cannot be solved by elimination.
 */
internal fun pickDistractors(answer: String, pool: List<String>, count: Int): List<String> {
    val key = AnswerMatcher.normalize(answer)
    val candidates = pool
        .filter { it.isNotBlank() && AnswerMatcher.normalize(it) != key }
        .distinctBy { AnswerMatcher.normalize(it) }
    if (candidates.isEmpty()) return emptyList()
    val rnd = Random(System.nanoTime())
    return candidates
        .map { c ->
            var score = rnd.nextDouble() // tie-breaker so it is not the same set every time
            val lenRatio = abs(c.length - answer.length) / answer.length.coerceAtLeast(1).toDouble()
            if (lenRatio < 0.4) score += 1.0
            if (c.firstOrNull()?.lowercaseChar() == answer.firstOrNull()?.lowercaseChar()) score += 1.5
            if (c.count { it == ' ' } == answer.count { it == ' ' }) score += 0.5
            c to score
        }
        .sortedByDescending { it.second }
        .take(count)
        .map { it.first }
}

// ---- Timed round --------------------------------------------------------------

private const val ROUND_SECONDS = 60

/**
 * Centered end-of-game summary. The confetti is a sibling overlay inside a Box: as a
 * child of the Column it would take the full height and push the content off screen.
 */
@Composable
private fun GameSummary(celebrate: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (celebrate) {
            Confetti(
                modifier = Modifier.fillMaxSize(),
                count = 80,
                origin = Offset(0.5f, 0.08f),
                angleFromDeg = 20f,
                angleToDeg = 160f,
                speed = 0.9f,
                durationMs = 2400,
            )
        }
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun TimedRound(deckId: Long, cards: List<Card>, reverse: Boolean, vm: AppViewModel, onBack: () -> Unit) {
    if (cards.size < 2) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Add at least 2 cards to play a timed round.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        return
    }
    var round by remember { mutableIntStateOf(0) }
    key(round) {
        TimedRoundBody(deckId, cards, reverse, vm, onBack, onPlayAgain = { round++ })
    }
}

@Composable
private fun TimedRoundBody(
    deckId: Long, cards: List<Card>, reverse: Boolean, vm: AppViewModel,
    onBack: () -> Unit, onPlayAgain: () -> Unit,
) {
    val order = remember { cards.shuffled() }
    var index by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(ROUND_SECONDS) }
    var score by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        finished = true
        newRecord = vm.recordScore(deckId, score)
    }

    if (finished) {
        GameSummary(celebrate = newRecord) {
            Text(if (newRecord) "New high score! 🏆" else "Time's up!", style = MaterialTheme.typography.headlineMedium)
            Text("$score", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = EasyGreen)
            Text("correct in $ROUND_SECONDS seconds", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$wrong wrong · best streak $best · high score ${vm.highScore(deckId)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPlayAgain) { Text("Play again") }
            OutlinedButton(onClick = onBack) { Text("Done") }
        }
        return
    }

    val card = order[index % order.size]
    val answer = card.answer(reverse)
    val options = remember(index) {
        val pool = cards.filter { it.id != card.id }.map { it.answer(reverse) }
        (pickDistractors(answer, pool, 3) + answer).shuffled()
    }
    var chosen by remember(index) { mutableStateOf<Int?>(null) }

    LaunchedEffect(chosen) {
        val c = chosen ?: return@LaunchedEffect
        val correct = options[c] == answer
        if (correct) {
            score++
            streak++
            if (streak > best) best = streak
        } else {
            wrong++
            streak = 0
        }
        delay(if (correct) 250 else 700)
        index++
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LinearProgressIndicator(
            progress = { secondsLeft / ROUND_SECONDS.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = if (secondsLeft <= 10) RepeatOrange else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Score $score", color = EasyGreen, fontWeight = FontWeight.Bold)
            Text("${secondsLeft}s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (secondsLeft <= 10) RepeatOrange else MaterialTheme.colorScheme.onSurface)
            Text(if (streak >= 3) "🔥 $streak" else "best ${vm.highScore(deckId)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(
                card.prompt(reverse),
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, option ->
                val state = when {
                    chosen == null -> null
                    option == answer -> true
                    i == chosen -> false
                    else -> null
                }
                OptionCard(option, state, enabled = chosen == null) { chosen = i }
            }
        }
    }
}

@Composable
internal fun OptionCard(text: String, state: Boolean?, enabled: Boolean, onClick: () -> Unit) {
    val border = when (state) {
        true -> BorderStroke(2.dp, EasyGreen)
        false -> BorderStroke(2.dp, HardPurple)
        null -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val container = when (state) {
        true -> EasyGreen.copy(alpha = 0.15f)
        false -> HardPurple.copy(alpha = 0.15f)
        null -> MaterialTheme.colorScheme.surface
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = container,
            disabledContainerColor = container,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

// ---- Matching game ------------------------------------------------------------

private const val PAIRS_PER_ROUND = 6

private class Tile(val cardId: Long, val text: String)

@Composable
internal fun MatchingGame(cards: List<Card>, reverse: Boolean, onBack: () -> Unit) {
    if (cards.size < 2) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Add at least 2 cards to play matching.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        return
    }
    var playAgain by remember { mutableIntStateOf(0) }
    val rounds = remember(playAgain) { cards.shuffled().chunked(PAIRS_PER_ROUND) }
    var roundIndex by remember(playAgain) { mutableIntStateOf(0) }
    var mistakes by remember(playAgain) { mutableIntStateOf(0) }
    val startedAt = remember(playAgain) { System.currentTimeMillis() }
    var finishedAt by remember(playAgain) { mutableStateOf<Long?>(null) }

    val done = finishedAt
    if (done != null) {
        val seconds = (done - startedAt) / 1000
        GameSummary(celebrate = true) {
            Text("All matched! 🎉", style = MaterialTheme.typography.headlineMedium)
            Text("${cards.size} pairs in ${seconds}s", style = MaterialTheme.typography.titleLarge)
            Text(
                if (mistakes == 0) "No mistakes — flawless." else "$mistakes mistake${if (mistakes == 1) "" else "s"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { playAgain++ }) { Text("Play again") }
            OutlinedButton(onClick = onBack) { Text("Done") }
        }
        return
    }

    key(playAgain, roundIndex) {
        MatchingRound(
            roundCards = rounds[roundIndex],
            reverse = reverse,
            header = "Round ${roundIndex + 1} of ${rounds.size}",
            onMistake = { mistakes++ },
            onRoundDone = {
                if (roundIndex + 1 < rounds.size) roundIndex++ else finishedAt = System.currentTimeMillis()
            },
        )
    }
}

@Composable
private fun MatchingRound(
    roundCards: List<Card>,
    reverse: Boolean,
    header: String,
    onMistake: () -> Unit,
    onRoundDone: () -> Unit,
) {
    val left = remember { roundCards.map { Tile(it.id, it.prompt(reverse)) }.shuffled() }
    val right = remember { roundCards.map { Tile(it.id, it.answer(reverse)) }.shuffled() }
    var matched by remember { mutableStateOf(setOf<Long>()) }
    var pickedLeft by remember { mutableStateOf<Long?>(null) }
    var pickedRight by remember { mutableStateOf<Long?>(null) }
    var wrongFlash by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    LaunchedEffect(pickedLeft, pickedRight) {
        val l = pickedLeft ?: return@LaunchedEffect
        val r = pickedRight ?: return@LaunchedEffect
        if (l == r) {
            matched = matched + l
        } else {
            onMistake()
            wrongFlash = l to r
            delay(500)
            wrongFlash = null
        }
        // Clearing the picks cancels this effect, so it must come last.
        pickedLeft = null
        pickedRight = null
    }

    // Keyed on the matched set, not the picks, so clearing the picks cannot cancel it.
    LaunchedEffect(matched) {
        if (matched.size == roundCards.size) {
            delay(400)
            onRoundDone()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(header, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Tap a term, then its match.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                left.forEach { tile ->
                    MatchTile(
                        text = tile.text,
                        matched = tile.cardId in matched,
                        selected = pickedLeft == tile.cardId,
                        wrong = wrongFlash?.first == tile.cardId,
                        onClick = { if (wrongFlash == null) pickedLeft = tile.cardId },
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                right.forEach { tile ->
                    MatchTile(
                        text = tile.text,
                        matched = tile.cardId in matched,
                        selected = pickedRight == tile.cardId,
                        wrong = wrongFlash?.second == tile.cardId,
                        onClick = { if (wrongFlash == null) pickedRight = tile.cardId },
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchTile(text: String, matched: Boolean, selected: Boolean, wrong: Boolean, onClick: () -> Unit) {
    val target = when {
        matched -> EasyGreen.copy(alpha = 0.18f)
        wrong -> RepeatOrange.copy(alpha = 0.25f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val container by animateColorAsState(target, label = "tile")
    val border = when {
        matched -> BorderStroke(1.dp, EasyGreen)
        wrong -> BorderStroke(2.dp, RepeatOrange)
        selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Card(
        onClick = onClick,
        enabled = !matched,
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = container,
            disabledContainerColor = container,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (matched) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
        )
    }
}
