package com.flashcards.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.flashcards.AppViewModel
import com.flashcards.StudyStats
import com.flashcards.data.Deck
import com.flashcards.data.DeckSummary
import kotlinx.coroutines.launch

/**
 * The deck list. As a [sidebar] it is a narrow permanent pane next to the deck being
 * worked on, so it drops to a tighter row and an inline "New deck" button; on its own it
 * is a full screen with a floating button, like the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    vm: AppViewModel,
    sidebar: Boolean = false,
    selectedDeckId: Long? = null,
    onOpenDeck: (Long) -> Unit,
) {
    val decksFlow = remember { vm.deckSummaries() }
    val decks by decksFlow.collectAsState(initial = emptyList())
    val stats by remember { vm.stats() }.collectAsState(initial = null)
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Deck?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<String?>(null) }
    var showSharing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val window = LocalWindow.current

    // Backups are the same JSON format as the Android app's, so they move between phone and PC.
    fun backUp() {
        val file = saveFile(window, "Back up everything", "flashcards-backup.json") ?: return
        val json = vm.backupJson()
        scope.launch {
            runCatching { file.writeText(json) }
                .onSuccess { snackbar.showSnackbar("Backup saved to ${file.name}") }
                .onFailure { snackbar.showSnackbar("Backup failed: ${it.message}") }
        }
    }

    fun pickBackup() {
        val file = openFile(window, "Restore from backup", "*.json") ?: return
        runCatching { file.readText() }
            .onSuccess { confirmRestore = it }
            .onFailure { scope.launch { snackbar.showSnackbar("Could not read file: ${it.message}") } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashcards") },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (vm.sharing) "Sharing over Wi-Fi…" else "Share over Wi-Fi…") },
                            onClick = {
                                showMenu = false
                                showSharing = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Back up everything…") },
                            onClick = {
                                showMenu = false
                                backUp()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restore from backup…") },
                            onClick = {
                                showMenu = false
                                pickBackup()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!sidebar) {
                ExtendedFloatingActionButton(
                    onClick = { showNew = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New deck") },
                )
            }
        },
    ) { padding ->
        if (decks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(if (sidebar) 16.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Style,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text("No decks yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create a deck, then type cards, paste a list, scan an image, or restore a backup from the phone app.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sidebar) {
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = { showNew = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New deck")
                    }
                }
            }
        } else {
            val grouped = decks.groupBy { it.deck.folder }
            val folders = grouped.keys.sortedWith(compareBy<String?> { it == null }.thenBy { it })
            LazyColumn(contentPadding = padding) {
                if (sidebar) {
                    item {
                        FilledTonalButton(
                            onClick = { showNew = true },
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("New deck")
                        }
                    }
                }
                item { stats?.let { StatsStrip(it, compact = sidebar) } }
                for (folder in folders) {
                    if (folders.size > 1 || folder != null) {
                        item(key = "folder-${folder ?: ""}") {
                            Text(
                                folder ?: "Other",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    start = if (sidebar) 16.dp else 24.dp,
                                    top = 12.dp,
                                    bottom = 4.dp,
                                ),
                            )
                        }
                    }
                    items(grouped.getValue(folder), key = { it.deck.id }) { summary ->
                        if (sidebar) {
                            SidebarDeckRow(
                                summary = summary,
                                selected = summary.deck.id == selectedDeckId,
                                onOpen = { onOpenDeck(summary.deck.id) },
                                onDelete = { toDelete = summary.deck },
                            )
                        } else {
                            DeckRow(
                                summary = summary,
                                onOpen = { onOpenDeck(summary.deck.id) },
                                onDelete = { toDelete = summary.deck },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(if (sidebar) 16.dp else 88.dp)) }
            }
        }
    }

    if (showSharing) {
        SharingDialog(vm = vm, onDismiss = { showSharing = false })
    }

    if (showNew) {
        TextDialog(
            title = "New deck",
            label = "Deck name",
            confirmText = "Create",
            onDismiss = { showNew = false },
            onConfirm = { name ->
                showNew = false
                vm.createDeck(name) { onOpenDeck(it) }
            },
        )
    }

    toDelete?.let { deck ->
        ConfirmDialog(
            title = "Delete \"${deck.name}\"?",
            message = "All cards in this deck will be deleted. This cannot be undone.",
            onDismiss = { toDelete = null },
            onConfirm = {
                vm.deleteDeck(deck)
                toDelete = null
            },
        )
    }

    confirmRestore?.let { json ->
        ConfirmDialog(
            title = "Restore backup?",
            message = "This replaces every deck, card and review on this computer with the contents of the backup file.",
            confirmText = "Replace everything",
            onDismiss = { confirmRestore = null },
            onConfirm = {
                confirmRestore = null
                vm.restore(json) { result ->
                    scope.launch {
                        result.onSuccess { snackbar.showSnackbar("Restored $it cards") }
                            .onFailure { snackbar.showSnackbar("Restore failed: ${it.message}") }
                    }
                }
            },
        )
    }
}

@Composable
private fun DeckRow(summary: DeckSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    val mastery = if (summary.total == 0) 0 else summary.mastered * 100 / summary.total
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(summary.deck.name)
                if (!summary.deck.notify) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Reminders muted",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        supportingContent = {
            Column {
                Text("${summary.total} card${if (summary.total == 1) "" else "s"} · $mastery% mastered")
                Spacer(Modifier.height(4.dp))
                MasteryBar(fraction = if (summary.total == 0) 0f else summary.mastered / summary.total.toFloat())
            }
        },
        leadingContent = {
            Badge(
                containerColor = if (summary.due > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (summary.due > 0) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ) { Text("${summary.due} due") }
        },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete deck") }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    }
}

/**
 * Tighter row for the sidebar: the selected deck stays highlighted while its cards are on
 * the right, and the delete button only appears under the pointer so the row stays readable.
 */
@Composable
private fun SidebarDeckRow(summary: DeckSummary, selected: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val mastery = if (summary.total == 0) 0 else summary.mastered * 100 / summary.total

    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .hoverable(interaction)
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.deck.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!summary.deck.notify) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Reminders muted",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (summary.due > 0) {
                    Text(
                        "${summary.due}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (hovered) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete deck",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(28.dp))
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "${summary.total} card${if (summary.total == 1) "" else "s"} · $mastery% mastered",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            MasteryBar(
                fraction = if (summary.total == 0) 0f else summary.mastered / summary.total.toFloat(),
                modifier = Modifier.fillMaxWidth().padding(end = 32.dp),
            )
        }
    }
}

@Composable
private fun MasteryBar(fraction: Float, modifier: Modifier = Modifier.fillMaxWidth(0.6f)) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.height(5.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(3.dp.toPx()))
        if (fraction > 0f) {
            drawRoundRect(EasyGreen, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
        }
    }
}

/** Today's reviews, current streak, and a 7-day bar chart. */
@Composable
private fun StatsStrip(stats: StudyStats, compact: Boolean) {
    Card(
        Modifier.fillMaxWidth().padding(
            start = if (compact) 8.dp else 16.dp,
            end = if (compact) 8.dp else 16.dp,
            top = 8.dp,
            bottom = 5.dp,
        ),
    ) {
        if (compact) {
            // Too narrow for three things in a row, so the week chart goes underneath.
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TodayCount(stats)
                    StreakCount(stats, compact = true)
                }
                WeekBars(stats.last7, Modifier.fillMaxWidth().height(32.dp))
            }
        } else {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) { TodayCount(stats) }
                Row(Modifier.weight(1f)) { StreakCount(stats, compact = false) }
                WeekBars(stats.last7, Modifier.width(84.dp).height(40.dp))
            }
        }
    }
}

@Composable
private fun TodayCount(stats: StudyStats) {
    Column {
        Text("${stats.today}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("reviews today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StreakCount(stats: StudyStats, compact: Boolean) {
    Column {
        Text(
            "${stats.streakDays} 🔥",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (stats.streakAlive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when {
                stats.streakDays == 0 -> "start a streak"
                stats.streakAlive -> "day streak"
                compact -> "day streak · study today"
                else -> "day streak · study today to keep it"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekBars(counts: List<Int>, modifier: Modifier) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (counts.size - 1)) / counts.size
        counts.forEachIndexed { i, c ->
            val h = if (c == 0) 3.dp.toPx() else (size.height * c / max).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = if (c == 0) empty else bar,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}
