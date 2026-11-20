package com.flashcards.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flashcards.AppViewModel
import com.flashcards.StudyStats
import com.flashcards.data.Deck
import com.flashcards.data.DeckSummary
import com.flashcards.notifications.ReviewReminders
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(vm: AppViewModel, onOpenDeck: (Long) -> Unit) {
    val decksFlow = remember { vm.deckSummaries() }
    val decks by decksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val stats by remember { vm.stats() }.collectAsStateWithLifecycle(initialValue = null)
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Deck?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<String?>(null) }
    var showSync by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Android 13+ needs runtime permission before "time to review" notifications can show.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ReviewReminders.canPost(context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Backup / restore go through the system file picker, so Google Drive is an option.
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = vm.backupJson()
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                .onSuccess { snackbar.showSnackbar("Backup saved") }
                .onFailure { snackbar.showSnackbar("Backup failed: ${it.message}") }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
                .onSuccess { json -> if (json != null) confirmRestore = json }
                .onFailure { snackbar.showSnackbar("Could not read file: ${it.message}") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashcards") },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Sync with computer…") },
                            onClick = {
                                showMenu = false
                                vm.resetSync()
                                showSync = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Back up everything…") },
                            onClick = {
                                showMenu = false
                                createBackup.launch("flashcards-backup.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restore from backup…") },
                            onClick = {
                                showMenu = false
                                openBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New deck") },
            )
        },
    ) { padding ->
        if (decks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
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
                    "Create a deck, then scan a page or your paper flashcards to fill it.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val grouped = decks.groupBy { it.deck.folder }
            val folders = grouped.keys.sortedWith(compareBy<String?> { it == null }.thenBy { it })
            LazyColumn(contentPadding = padding) {
                item { stats?.let { StatsStrip(it) } }
                for (folder in folders) {
                    if (folders.size > 1 || folder != null) {
                        item(key = "folder-${folder ?: ""}") {
                            Text(
                                folder ?: "Other",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(grouped.getValue(folder), key = { it.deck.id }) { summary ->
                        DeckRow(summary, onOpen = { onOpenDeck(summary.deck.id) }, onDelete = { toDelete = summary.deck })
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    if (showSync) {
        SyncDialog(vm = vm, onDismiss = { showSync = false; vm.resetSync() })
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
            message = "This replaces every deck, card and review on this phone with the contents of the backup file.",
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

@Composable
private fun MasteryBar(fraction: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth(0.6f).height(5.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(3.dp.toPx()))
        if (fraction > 0f) {
            drawRoundRect(EasyGreen, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
        }
    }
}

/** Today's reviews, current streak, and a 7-day bar chart. */
@Composable
private fun StatsStrip(stats: StudyStats) {
    Card(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 5.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${stats.today}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("reviews today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
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
                        else -> "day streak · study today to keep it"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WeekBars(stats.last7)
        }
    }
}

@Composable
private fun WeekBars(counts: List<Int>) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.width(84.dp).height(40.dp)) {
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
