package com.flashcards.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.flashcards.AppViewModel
import com.flashcards.StudySession
import com.flashcards.data.AnswerMatcher
import com.flashcards.data.Card
import com.flashcards.data.Cloze
import com.flashcards.data.Grade
import kotlinx.coroutines.delay

enum class QuizMode(val label: String, val description: String, val isGame: Boolean) {
    MULTIPLE_CHOICE("Multiple choice", "Pick the right answer out of four. Updates review timers.", false),
    TYPING("Typing", "Type the answer; accents and punctuation are ignored. Updates review timers.", false),
    TIMED("Timed round", "60 seconds, as many as you can, high score per deck. Just for fun \u2014 timers untouched.", true),
    MATCHING("Matching", "Match terms to definitions, six at a time. Just for fun \u2014 timers untouched.", true),
}

/** The side shown as the question. Cloze cards always ask for the blank. */
internal fun Card.prompt(reverse: Boolean): String = when {
    Cloze.isCloze(front) -> Cloze.hidden(front)
    reverse -> back
    else -> front
}

internal fun Card.answer(reverse: Boolean): String = when {
    Cloze.isCloze(front) -> Cloze.answerText(front)
    reverse -> front
    else -> back
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(deckId: Long, mode: QuizMode, reverse: Boolean, vm: AppViewModel, onBack: () -> Unit) {
    LaunchedEffect(deckId) { if (!mode.isGame) vm.ensureSession(deckId) }
    // End the session only when actually leaving, not on a screen recreation.
    val leave = {
        vm.endSession()
        onBack()
    }
    WindowKeys { e -> if (e.key == Key.Escape) { leave(); true } else false }

    val session = vm.session
    val allCards by remember(deckId) { vm.cards(deckId) }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Card?>(null) }
    editing?.let { card ->
        CardDialog(
            title = "Edit card",
            initialFront = card.front,
            initialBack = card.back,
            onDismiss = { editing = null },
            onConfirm = { front, back ->
                vm.updateCard(card.copy(front = front.trim(), back = back.trim()))
                editing = null
            },
        )
    }

    // Games use the whole deck once it has loaded; null until the first emission.
    var loadedCards by remember { mutableStateOf<List<Card>?>(null) }
    LaunchedEffect(allCards) { if (loadedCards == null && allCards.isNotEmpty()) loadedCards = allCards }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz · ${mode.label}") },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!mode.isGame) session?.current?.let { current ->
                        Text(
                            "${session.reviewed + 1} / ${session.reviewed + session.remaining + 1}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { editing = current }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit this card")
                        }
                    }
                },
            )
        },
    ) { padding ->
        CenteredPane(Panes.READING, Modifier.padding(padding)) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                mode == QuizMode.TIMED -> loadedCards?.let { TimedRound(deckId, it, reverse, vm, onBack = leave) }
                    ?: CircularProgressIndicator()

                mode == QuizMode.MATCHING -> loadedCards?.let { MatchingGame(it, reverse, onBack = leave) }
                    ?: CircularProgressIndicator()

                session == null || vm.sessionLoading -> CircularProgressIndicator()

                session.total == 0 -> NothingDue(
                    totalCards = allCards.size,
                    onStudyAll = { vm.startSession(deckId, onlyDue = false) },
                    onBack = leave,
                )

                session.current == null -> SessionComplete(
                    session = session,
                    onStudyAll = { vm.startSession(deckId, onlyDue = false) },
                    onBack = leave,
                )

                else -> QuizContent(session = session, allCards = allCards, mode = mode, reverse = reverse, vm = vm)
            }
          }
        }
    }
}

@Composable
private fun QuizContent(session: StudySession, allCards: List<Card>, mode: QuizMode, reverse: Boolean, vm: AppViewModel) {
    val card = session.current ?: return
    val prompt = card.prompt(reverse)
    val answer = card.answer(reverse)

    val onAnswer: (Boolean) -> Unit = { correct ->
        vm.answer(if (correct) Grade.EASY else Grade.HARD)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SessionProgressBar(
            session = session,
            total = session.reviewed + session.remaining + 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Wrong ${session.hardCount}", color = HardPurple)
            Text("${session.remaining + 1} to go", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Right ${session.easyCount}", color = EasyGreen)
        }
        // The question sits in the middle of what is left, rather than clinging to the
        // top of a tall window with nothing under it.
        Spacer(Modifier.weight(0.8f))

        key(session.reviewed) {
            Column(
                Modifier.widthIn(max = Panes.QUESTION).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PromptCard(prompt = prompt, repeat = session.currentIsRepeat)
                Spacer(Modifier.height(20.dp))
                when (mode) {
                    QuizMode.MULTIPLE_CHOICE -> MultipleChoice(
                        answer = answer,
                        pool = allCards.filter { it.id != card.id }.map { it.answer(reverse) },
                        onAnswer = onAnswer,
                    )
                    QuizMode.TYPING -> Typing(answer = answer, onAnswer = onAnswer)
                    QuizMode.TIMED, QuizMode.MATCHING -> Unit // handled in QuizScreen, never reach here
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PromptCard(prompt: String, repeat: Boolean) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            if (repeat) {
                Text(
                    "AGAIN",
                    modifier = Modifier.align(Alignment.TopEnd),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = HardPurple,
                )
            }
            Text(
                prompt,
                style = if (prompt.length > 120) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (repeat) 16.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun MultipleChoice(answer: String, pool: List<String>, onAnswer: (Boolean) -> Unit) {
    val options = remember { (pickDistractors(answer, pool, 3) + answer).shuffled() }
    var chosen by remember { mutableStateOf<Int?>(null) }
    val correctIndex = options.indexOf(answer)

    // Keys 1-4 pick an option.
    WindowKeys { e ->
        val i = listOf(Key.One, Key.Two, Key.Three, Key.Four).indexOf(e.key)
            .takeIf { it >= 0 } ?: listOf(Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4).indexOf(e.key)
        if (i in options.indices && chosen == null) { chosen = i; true } else false
    }

    LaunchedEffect(chosen) {
        val c = chosen ?: return@LaunchedEffect
        delay(if (c == correctIndex) 700 else 1500)
        onAnswer(c == correctIndex)
    }

    BoxWithConstraints {
        // Two answers per row once there is room, so four options do not become a
        // tall ladder the eye has to climb.
        val perRow = if (maxWidth >= 640.dp) 2 else 1
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (options.size < 2) {
                Text(
                    "Add a few more cards to this deck to get multiple-choice options.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (row in options.withIndex().chunked(perRow)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((i, option) in row) {
                        OptionCard(
                            index = i,
                            option = option,
                            state = when {
                                chosen == null -> null
                                i == correctIndex -> true
                                i == chosen -> false
                                else -> null
                            },
                            enabled = chosen == null,
                            onClick = { if (chosen == null) chosen = i },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keep a lone last option the same width as the others.
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One multiple-choice answer. [state] is null until answered, then true/false for this option. */
@Composable
private fun OptionCard(
    index: Int,
    option: String,
    state: Boolean?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = when (state) {
        true -> BorderStroke(2.dp, EasyGreen)
        false -> BorderStroke(2.dp, HardPurple)
        null -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val container = when (state) {
        true -> EasyGreen.copy(alpha = 0.12f)
        false -> HardPurple.copy(alpha = 0.12f)
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
        modifier = modifier,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
            Text(option, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            when (state) {
                true -> Icon(Icons.Default.Check, contentDescription = "Correct", tint = EasyGreen)
                false -> Icon(Icons.Default.Close, contentDescription = "Wrong", tint = HardPurple)
                null -> Unit
            }
        }
    }
}

@Composable
private fun Typing(answer: String, onAnswer: (Boolean) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(result) {
        if (result == true) {
            delay(800)
            onAnswer(true)
        }
    }

    fun check() {
        if (result == null && typed.isNotBlank()) result = AnswerMatcher.matches(typed, answer)
    }

    Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = typed,
            onValueChange = { if (result == null) typed = it },
            label = { Text("Your answer") },
            singleLine = true,
            readOnly = result != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { check() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || (e.key != Key.Enter && e.key != Key.NumPadEnter)) return@onPreviewKeyEvent false
                    when (result) {
                        null -> check()
                        false -> onAnswer(false)
                        true -> Unit
                    }
                    true
                },
        )
        when (result) {
            null -> Button(onClick = { check() }, enabled = typed.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Check")
            }
            true -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = EasyGreen)
                Spacer(Modifier.width(8.dp))
                Text("Correct!", color = EasyGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            false -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = HardPurple)
                    Spacer(Modifier.width(8.dp))
                    Text("Not quite", color = HardPurple, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Answer: $answer", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onAnswer(true) }, modifier = Modifier.weight(1f)) { Text("I was right") }
                    Button(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f)) { Text("Continue (Enter)") }
                }
            }
        }
        Text(
            "Accents, capitals and punctuation are ignored. Any one alternative (e.g. “says, said”) counts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
