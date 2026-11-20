package com.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.flashcards.AppViewModel
import com.flashcards.data.Card
import com.flashcards.data.Cloze
import com.flashcards.data.Deck
import com.flashcards.data.Mastery
import kotlinx.coroutines.launch
import com.flashcards.data.Scheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    deckId: Long,
    vm: AppViewModel,
    onBack: (() -> Unit)?,
    onDeleted: () -> Unit,
    onScan: () -> Unit,
    onStudy: (reverse: Boolean) -> Unit,
    onQuiz: (QuizMode, Boolean) -> Unit,
) {
    val deck by remember(deckId) { vm.deck(deckId) }.collectAsState(initial = null)
    val cards by remember(deckId) { vm.cards(deckId) }.collectAsState(initial = emptyList())
    val now = remember { System.currentTimeMillis() }
    val due = cards.count { it.dueAt <= now }
    val mastered = cards.count { it.box >= Mastery.MASTERED_BOX }

    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Card?>(null) }
    var deleting by remember { mutableStateOf<Card?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var showQuizSetup by remember { mutableStateOf(false) }
    var showStudySetup by remember { mutableStateOf(false) }
    var showFolder by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var showMove by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var confirmDeckDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val selectedCards = cards.filter { it.id in selected }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val window = LocalWindow.current

    WindowKeys { e ->
        if (selecting && e.key == Key.Escape) { selected = emptySet(); true } else false
    }

    // Offer to undo a batch add (scan / import) that just happened for this deck.
    val undo = vm.undoableAdd
    LaunchedEffect(undo?.token) {
        val u = undo ?: return@LaunchedEffect
        if (u.deckId != deckId) return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "Added ${u.ids.size} card${if (u.ids.size == 1) "" else "s"}",
            actionLabel = "Undo",
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoLastAdd() else vm.dismissUndo()
    }

    fun exportDeck() {
        val file = saveFile(window, "Export deck", "${deck?.name ?: "deck"}.txt") ?: return
        val tsv = vm.exportDeckTsv(deckId)
        scope.launch {
            runCatching { file.writeText(tsv) }
                .onSuccess { snackbar.showSnackbar("Exported ${cards.size} cards") }
                .onFailure { snackbar.showSnackbar("Export failed: ${it.message}") }
        }
    }

    fun importFile() {
        val file = openFile(window, "Import cards", "*.txt;*.tsv;*.csv") ?: return
        val text = runCatching { file.readText() }.getOrNull()
        if (text == null) scope.launch { snackbar.showSnackbar("Could not read that file") }
        else vm.importText(deckId, text) { n -> if (n == 0) scope.launch { snackbar.showSnackbar("No cards found in that file") } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                    },
                    actions = {
                        IconButton(onClick = { selected = cards.map { it.id }.toSet() }) {
                            Icon(Icons.Default.Check, contentDescription = "Select all")
                        }
                        IconButton(onClick = { vm.swapSides(selectedCards); selected = emptySet() }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Swap front and back")
                        }
                        IconButton(onClick = { vm.mergeCards(selectedCards); selected = emptySet() }, enabled = selected.size >= 2) {
                            Icon(Icons.Default.CallMerge, contentDescription = "Merge into one card")
                        }
                        IconButton(onClick = { showMove = true }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move to another deck")
                        }
                        IconButton(onClick = { confirmBulkDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    },
                )
            } else TopAppBar(
                title = { Text(deck?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    // Nothing to go back to while the deck list is a sidebar.
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onScan) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = "Add from text or image")
                    }
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add card")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename deck") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { showMenu = false; showRename = true },
                        )
                        deck?.let { d ->
                            DropdownMenuItem(
                                text = { Text(if (d.notify) "Mute review reminders" else "Unmute review reminders") },
                                leadingIcon = {
                                    Icon(
                                        if (d.notify) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    vm.setDeckNotify(d, !d.notify)
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            if (d.notify) "Reminders muted for this deck" else "Reminders on for this deck"
                                        )
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Move to folder\u2026") },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                            onClick = { showMenu = false; showFolder = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Import cards from file\u2026") },
                            onClick = { showMenu = false; importFile() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export deck (Anki / TSV)\u2026") },
                            onClick = { showMenu = false; exportDeck() },
                        )
                        DropdownMenuItem(
                            text = { Text("Reset progress") },
                            onClick = { showMenu = false; confirmReset = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete deck…") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { showMenu = false; confirmDeckDelete = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (cards.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("This deck is empty", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paste a vocabulary list, scan a screenshot or photo of your notes, import a file, or type cards in by hand.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onScan) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Paste a list or scan an image")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showAdd = true }) { Text("Type a card") }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                DeckActions(
                    cardCount = cards.size,
                    due = due,
                    mastered = mastered,
                    onStudy = { showStudySetup = true },
                    onQuiz = { showQuizSetup = true },
                )
                HorizontalDivider()
                // Cards are tiles in as many columns as the window has room for, so a wide
                // window shows a deck at a glance instead of one row at a time.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 360.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Click a card to edit it, tick the boxes to select several. Write {{like this}} on the front for a fill-in-the-blank card.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(cards, key = { it.id }) { card ->
                        CardTile(
                            card = card,
                            now = now,
                            selected = card.id in selected,
                            selecting = selecting,
                            onToggle = {
                                selected = if (card.id in selected) selected - card.id else selected + card.id
                            },
                            onEdit = { editing = card },
                            onDelete = { deleting = card },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        CardDialog(
            title = "New card",
            onDismiss = { showAdd = false },
            onConfirm = { front, back ->
                vm.addCard(deckId, front, back)
                showAdd = false
            },
        )
    }

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

    deleting?.let { card ->
        ConfirmDialog(
            title = "Delete card?",
            message = card.front,
            onDismiss = { deleting = null },
            onConfirm = {
                vm.deleteCard(card)
                deleting = null
            },
        )
    }

    if (showRename) {
        deck?.let { d ->
            TextDialog(
                title = "Rename deck",
                label = "Deck name",
                initial = d.name,
                onDismiss = { showRename = false },
                onConfirm = { name ->
                    vm.renameDeck(d, name)
                    showRename = false
                },
            )
        }
    }

    if (showFolder) {
        deck?.let { d ->
            FolderDialog(
                vm = vm,
                deck = d,
                onDismiss = { showFolder = false },
            )
        }
    }

    if (showMove) {
        MoveToDeckDialog(
            vm = vm,
            currentDeckId = deckId,
            onDismiss = { showMove = false },
            onMove = { target ->
                vm.moveCards(selectedCards, target)
                selected = emptySet()
                showMove = false
            },
        )
    }

    if (confirmBulkDelete) {
        ConfirmDialog(
            title = "Delete ${selected.size} card${if (selected.size == 1) "" else "s"}?",
            message = "This cannot be undone.",
            onDismiss = { confirmBulkDelete = false },
            onConfirm = {
                vm.deleteCards(selectedCards)
                selected = emptySet()
                confirmBulkDelete = false
            },
        )
    }

    if (showStudySetup) {
        StudySetupDialog(
            initialReverse = vm.studyReverse,
            onDismiss = { showStudySetup = false },
            onStart = { reverse ->
                showStudySetup = false
                vm.studyReverse = reverse
                onStudy(reverse)
            },
        )
    }

    if (showQuizSetup) {
        QuizSetupDialog(
            initialMode = runCatching { QuizMode.valueOf(vm.quizModeName) }.getOrDefault(QuizMode.MULTIPLE_CHOICE),
            initialReverse = vm.quizReverse,
            onDismiss = { showQuizSetup = false },
            onStart = { mode, reverse ->
                showQuizSetup = false
                vm.quizModeName = mode.name
                vm.quizReverse = reverse
                onQuiz(mode, reverse)
            },
        )
    }

    if (confirmDeckDelete) {
        deck?.let { d ->
            ConfirmDialog(
                title = "Delete \"${d.name}\"?",
                message = "All ${cards.size} card${if (cards.size == 1) "" else "s"} in this deck will be deleted. This cannot be undone.",
                onDismiss = { confirmDeckDelete = false },
                onConfirm = {
                    confirmDeckDelete = false
                    vm.deleteDeck(d)
                    onDeleted()
                },
            )
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset progress?",
            message = "Every card in this deck will be marked as new and due now.",
            confirmText = "Reset",
            onDismiss = { confirmReset = false },
            onConfirm = {
                vm.resetProgress(deckId)
                confirmReset = false
            },
        )
    }
}

/**
 * Study and Quiz sit just under the title bar, at their natural size: stretching two
 * buttons across a whole monitor, the way the phone's bottom bar does, looks absurd.
 */
@Composable
private fun DeckActions(
    cardCount: Int,
    due: Int,
    mastered: Int,
    onStudy: () -> Unit,
    onQuiz: () -> Unit,
) {
    val percent = if (cardCount == 0) 0 else mastered * 100 / cardCount
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onStudy,
            modifier = Modifier.widthIn(min = 136.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Study", maxLines = 1)
            if (due > 0) {
                Spacer(Modifier.width(8.dp))
                // Due count as a compact pill so the label never wraps.
                Text(
                    "$due",
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        FilledTonalButton(
            onClick = onQuiz,
            modifier = Modifier.widthIn(min = 120.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Default.Quiz, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Quiz", maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "$cardCount card${if (cardCount == 1) "" else "s"} · $due due · $percent% mastered",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** One card in the deck grid. */
@Composable
private fun CardTile(
    card: Card,
    now: Long,
    selected: Boolean,
    selecting: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth().clickable { if (selecting) onToggle() else onEdit() },
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(
                    buildString {
                        append(if (card.dueAt <= now) "Due now" else "Due in ${Scheduler.describe(card.dueAt - now)}")
                        if (card.lapses > 0) append(" · missed ${card.lapses}×")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (card.dueAt <= now) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    Cloze.revealed(card.front),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (Cloze.isCloze(card.front)) "Blank: ${Cloze.answerText(card.front)}" +
                        (if (card.back.isNotBlank()) " · ${card.back}" else "")
                    else card.back.ifBlank { "(no back)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                BoxDots(card.box)
            }
            if (!selecting) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit card") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete card") }
            }
        }
    }
}

/** Front -> Back / Back -> Front toggle shared by the study and quiz setup dialogs. */
@Composable
private fun DirectionPicker(reverse: Boolean, onChange: (Boolean) -> Unit) {
    Text("Starting side", style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !reverse,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Front \u2192 Back") }
        SegmentedButton(
            selected = reverse,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Back \u2192 Front") }
    }
}

@Composable
private fun StudySetupDialog(initialReverse: Boolean, onDismiss: () -> Unit, onStart: (Boolean) -> Unit) {
    var reverse by remember { mutableStateOf(initialReverse) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Study") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DirectionPicker(reverse) { reverse = it }
                Text(
                    "Which side of each card is shown first. Click the card or press Space to flip it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onStart(reverse) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun QuizSetupDialog(
    initialMode: QuizMode,
    initialReverse: Boolean,
    onDismiss: () -> Unit,
    onStart: (QuizMode, Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var reverse by remember { mutableStateOf(initialReverse) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiz") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mode", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuizMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                    }
                }
                Text(
                    mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DirectionPicker(reverse) { reverse = it }
                if (!mode.isGame) Text(
                    "Only cards that are due are quizzed. Right = Easy, wrong = Hard (back in 10 min), just like studying.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onStart(mode, reverse) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Leitner box as a row of dots: filled up to the card's box, green once mastered. */
@Composable
internal fun BoxDots(box: Int) {
    val on = if (box >= Mastery.MASTERED_BOX) EasyGreen else MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.size(width = ((Mastery.MAX_BOX + 1) * 12).dp, height = 8.dp)) {
        val r = 3.dp.toPx()
        val step = 12.dp.toPx()
        for (i in 0..Mastery.MAX_BOX) {
            drawCircle(if (i <= box && box > 0) on else off, radius = r, center = Offset(i * step + r, size.height / 2))
        }
    }
}

@Composable
private fun FolderDialog(vm: AppViewModel, deck: Deck, onDismiss: () -> Unit) {
    val folders by remember { vm.folders() }.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf(deck.folder ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (folders.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        folders.forEach { f ->
                            FilterChip(selected = name == f, onClick = { name = f }, label = { Text(f) })
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    placeholder = { Text("e.g. Latin, Biology") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Leave empty to remove the deck from its folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.setDeckFolder(deck, name); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoveToDeckDialog(vm: AppViewModel, currentDeckId: Long, onDismiss: () -> Unit, onMove: (Long) -> Unit) {
    val decks by remember { vm.allDecks() }.collectAsState(initial = emptyList())
    val targets = decks.filter { it.id != currentDeckId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to deck") },
        text = {
            if (targets.isEmpty()) Text("Create another deck first.")
            else Column {
                targets.forEach { d ->
                    ListItem(
                        headlineContent = { Text(d.name) },
                        supportingContent = d.folder?.let { { Text(it) } },
                        modifier = Modifier.clickable { onMove(d.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
