package com.flashcards.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flashcards.AppViewModel
import com.flashcards.data.CardParser
import com.flashcards.data.SplitMode
import com.flashcards.ocr.OcrCleaner
import com.flashcards.ocr.OcrResult
import com.flashcards.ocr.WindowsOcr
import java.awt.image.BufferedImage
import kotlinx.coroutines.launch

private enum class ScanTarget { PAGE, FRONT, BACK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(deckId: Long, vm: AppViewModel, onBack: () -> Unit) {
    val window = LocalWindow.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    // The recognizer tends to invent accents (u -> ü) on plain English text.
    var stripAccents by rememberSaveable { mutableStateOf(true) }

    // Page mode
    var pageText by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(SplitMode.AUTO) }
    // Parsed cards are editable in place; re-parsed whenever the text or split mode changes.
    var drafts by remember { mutableStateOf(listOf<CardParser.Draft>()) }
    var excluded by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(pageText, mode) {
        drafts = CardParser.parse(pageText, mode)
        excluded = emptySet()
    }
    var editingDraft by remember { mutableStateOf<Int?>(null) }

    // Flashcard mode
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var savedCount by rememberSaveable { mutableIntStateOf(0) }

    // Where the next OCR result should land.
    var target by rememberSaveable { mutableStateOf(ScanTarget.PAGE) }

    // What the recognizer actually saw, so bad scans are easy to diagnose.
    var lastImage by remember { mutableStateOf<ImageBitmap?>(null) }
    // Windows OCR does not report per-line confidence, so nothing is filtered out.
    val dropped = emptyList<String>()

    fun runOcr(recognize: suspend () -> OcrResult) {
        scope.launch {
            busy = true
            try {
                val result = recognize()
                lastImage = thumbnail(result.processed)
                val text = if (stripAccents) OcrCleaner.stripAccents(result.text) else result.text
                if (text.isBlank()) {
                    snackbar.showSnackbar("No text found in that image")
                } else when (target) {
                    ScanTarget.PAGE -> pageText = if (pageText.isBlank()) text else pageText.trimEnd() + "\n" + text
                    ScanTarget.FRONT -> front = text
                    ScanTarget.BACK -> back = text
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Could not read image: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    fun pick(t: ScanTarget) {
        val file = openFile(window, "Choose an image", "*.png;*.jpg;*.jpeg;*.bmp;*.gif") ?: return
        target = t
        runOcr { WindowsOcr.recognize(file) }
    }

    fun paste(t: ScanTarget) {
        val image = WindowsOcr.clipboardImage()
        if (image == null) {
            scope.launch { snackbar.showSnackbar("No image on the clipboard. Snip one with Win+Shift+S, then paste.") }
            return
        }
        target = t
        runOcr { WindowsOcr.recognize(image) }
    }

    editingDraft?.let { i ->
        drafts.getOrNull(i)?.let { d ->
            CardDialog(
                title = "Edit card",
                initialFront = d.front,
                initialBack = d.back,
                onDismiss = { editingDraft = null },
                onConfirm = { front, back ->
                    drafts = drafts.toMutableList().apply { set(i, CardParser.Draft(front.trim(), back.trim())) }
                    editingDraft = null
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add cards from text or image") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab, modifier = Modifier.widthIn(max = 460.dp)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Page / list") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Paper flashcard") })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.widthIn(max = 560.dp)) {
                    Text("Remove accents (ü → u)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Fixes stray dots and accents on English text. Turn off for accented languages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = stripAccents,
                    onCheckedChange = { on ->
                        stripAccents = on
                        if (on) {
                            pageText = OcrCleaner.stripAccents(pageText)
                            front = OcrCleaner.stripAccents(front)
                            back = OcrCleaner.stripAccents(back)
                        }
                    },
                )
            }
            HorizontalDivider()

            if (tab == 0) {
                PageTab(
                    preview = lastImage,
                    dropped = dropped,
                    text = pageText,
                    onTextChange = { pageText = it },
                    mode = mode,
                    onModeChange = { mode = it },
                    drafts = drafts,
                    excluded = excluded,
                    onToggle = { i -> excluded = if (i in excluded) excluded - i else excluded + i },
                    onEdit = { i -> editingDraft = i },
                    onMergeUp = { i ->
                        if (i > 0) {
                            val prev = drafts[i - 1]
                            val cur = drafts[i]
                            val merged = prev.copy(
                                front = listOf(prev.front, cur.front).filter { it.isNotBlank() }.joinToString(" "),
                                back = listOf(prev.back, cur.back).filter { it.isNotBlank() }.joinToString(" "),
                            )
                            drafts = drafts.toMutableList().apply { set(i - 1, merged); removeAt(i) }
                            excluded = emptySet()
                        }
                    },
                    onSwap = { i -> drafts = drafts.toMutableList().apply { set(i, drafts[i].let { d -> d.copy(front = d.back, back = d.front) }) } },
                    busy = busy,
                    onPaste = { paste(ScanTarget.PAGE) },
                    onOpen = { pick(ScanTarget.PAGE) },
                    onSave = {
                        val keep = drafts.filterIndexed { i, _ -> i !in excluded }
                        vm.addCards(deckId, keep) { onBack() }
                    },
                )
            } else {
                FlashcardTab(
                    preview = lastImage,
                    dropped = dropped,
                    front = front,
                    back = back,
                    onFrontChange = { front = it },
                    onBackChange = { back = it },
                    busy = busy,
                    savedCount = savedCount,
                    onPaste = { paste(it) },
                    onOpen = { pick(it) },
                    onSave = {
                        vm.addCard(deckId, front, back)
                        savedCount++
                        front = ""
                        back = ""
                        scope.launch { snackbar.showSnackbar("Card saved") }
                    },
                )
            }
        }
    }
}

/** Small copy of the processed image for the on-screen preview. */
private fun thumbnail(src: BufferedImage): ImageBitmap {
    val maxEdge = 900
    val scale = maxEdge.toFloat() / maxOf(src.width, src.height)
    if (scale >= 1f) return src.toComposeImageBitmap()
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    out.createGraphics().run {
        setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        drawImage(src, 0, 0, w, h, null)
        dispose()
    }
    return out.toComposeImageBitmap()
}

@Composable
private fun ScanPreview(preview: ImageBitmap?, dropped: List<String>) {
    if (preview == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("What the scanner saw", style = MaterialTheme.typography.labelLarge)
        Image(
            bitmap = preview,
            contentDescription = "Processed scan",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
        if (dropped.isNotEmpty()) {
            Text(
                "Ignored ${dropped.size} unreadable line${if (dropped.size == 1) "" else "s"}: " +
                    dropped.joinToString(" · ") { it.take(24) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScanButtons(
    enabled: Boolean,
    pasteLabel: String,
    onPaste: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPaste, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(pasteLabel)
        }
        OutlinedButton(onClick = onOpen, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open image file\u2026")
        }
    }
}

@Composable
private fun PageTab(
    preview: ImageBitmap?,
    dropped: List<String>,
    text: String,
    onTextChange: (String) -> Unit,
    mode: SplitMode,
    onModeChange: (SplitMode) -> Unit,
    drafts: List<CardParser.Draft>,
    excluded: Set<Int>,
    onToggle: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onMergeUp: (Int) -> Unit,
    onSwap: (Int) -> Unit,
    busy: Boolean,
    onPaste: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    val keepCount = drafts.size - excluded.count { it < drafts.size }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // With room to spare, the text being scanned and the cards it produces sit next to
        // each other, so an edit and its effect are visible at once.
        if (maxWidth >= Panes.WIDE_FORM) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PageInputs(
                        preview = preview,
                        dropped = dropped,
                        text = text,
                        onTextChange = onTextChange,
                        mode = mode,
                        onModeChange = onModeChange,
                        busy = busy,
                        onPaste = onPaste,
                        onOpen = onOpen,
                        textLines = 10,
                    )
                }
                VerticalDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (drafts.isEmpty()) {
                            Text(
                                "Cards will appear here as you type, paste or scan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            PagePreview(drafts, excluded, keepCount, onToggle, onEdit, onMergeUp, onSwap)
                        }
                    }
                    HorizontalDivider()
                    SaveCardsButton(keepCount, busy, onSave)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PageInputs(
                        preview = preview,
                        dropped = dropped,
                        text = text,
                        onTextChange = onTextChange,
                        mode = mode,
                        onModeChange = onModeChange,
                        busy = busy,
                        onPaste = onPaste,
                        onOpen = onOpen,
                        textLines = 6,
                    )
                    if (drafts.isNotEmpty()) {
                        HorizontalDivider()
                        PagePreview(drafts, excluded, keepCount, onToggle, onEdit, onMergeUp, onSwap)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                HorizontalDivider()
                SaveCardsButton(keepCount, busy, onSave)
            }
        }
    }
}

/** The text being turned into cards, plus where it can come from and how it is split. */
@Composable
private fun PageInputs(
    preview: ImageBitmap?,
    dropped: List<String>,
    text: String,
    onTextChange: (String) -> Unit,
    mode: SplitMode,
    onModeChange: (SplitMode) -> Unit,
    busy: Boolean,
    onPaste: () -> Unit,
    onOpen: () -> Unit,
    textLines: Int,
) {
    Text(
        "Type or paste a list below, or scan an image: snip part of the screen with Win+Shift+S and paste it here, or open a photo of a page. Text is recognized offline by Windows.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ScanButtons(enabled = !busy, pasteLabel = "Paste screenshot", onPaste = onPaste, onOpen = onOpen)
    ScanPreview(preview, dropped)

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text("Text to turn into cards") },
        placeholder = { Text("term - definition\nQ: question\nA: answer") },
        minLines = textLines,
        maxLines = textLines + 8,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = if (text.isNotEmpty()) {
            { IconButton(onClick = { onTextChange("") }) { Icon(Icons.Default.Close, "Clear") } }
        } else null,
    )

    Text("Split into cards by", style = MaterialTheme.typography.labelLarge)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SplitMode.entries.forEach { m ->
            FilterChip(selected = m == mode, onClick = { onModeChange(m) }, label = { Text(m.label) })
        }
    }
    Text(
        mode.hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The cards the text was split into, each one editable before it is saved. */
@Composable
private fun PagePreview(
    drafts: List<CardParser.Draft>,
    excluded: Set<Int>,
    keepCount: Int,
    onToggle: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onMergeUp: (Int) -> Unit,
    onSwap: (Int) -> Unit,
) {
    Text("Preview \u00B7 $keepCount of ${drafts.size} cards", style = MaterialTheme.typography.titleSmall)
    drafts.forEachIndexed { i, d ->
        val checked = i !in excluded
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { onToggle(i) })
                Column(Modifier.weight(1f).clickable { onEdit(i) }) {
                    Text(d.front, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        d.back.ifBlank { "(no back \u2014 click to edit)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Card options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(i) })
                    DropdownMenuItem(text = { Text("Swap front / back") }, onClick = { menu = false; onSwap(i) })
                    if (i > 0) DropdownMenuItem(
                        text = { Text("Merge into card above") },
                        onClick = { menu = false; onMergeUp(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveCardsButton(keepCount: Int, busy: Boolean, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
        Button(onClick = onSave, enabled = keepCount > 0 && !busy) {
            Text(if (keepCount == 1) "Add 1 card to deck" else "Add $keepCount cards to deck")
        }
    }
}

@Composable
private fun FlashcardTab(
    preview: ImageBitmap?,
    dropped: List<String>,
    front: String,
    back: String,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    busy: Boolean,
    savedCount: Int,
    onPaste: (ScanTarget) -> Unit,
    onOpen: (ScanTarget) -> Unit,
    onSave: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A paper card has two sides, so on a wide window they are edited as two sides.
        val sideBySide = maxWidth >= Panes.WIDE_FORM
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Scan each side of a paper flashcard from a screenshot or photo, or just type. You can fix any recognition mistakes before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ScanPreview(preview, dropped)

                if (sideBySide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CardSideFields("Front", front, onFrontChange, busy, ScanTarget.FRONT, onPaste, onOpen, lines = 6)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CardSideFields("Back", back, onBackChange, busy, ScanTarget.BACK, onPaste, onOpen, lines = 6)
                        }
                    }
                } else {
                    CardSideFields("Front", front, onFrontChange, busy, ScanTarget.FRONT, onPaste, onOpen, lines = 3)
                    Spacer(Modifier.height(4.dp))
                    CardSideFields("Back", back, onBackChange, busy, ScanTarget.BACK, onPaste, onOpen, lines = 3)
                }

                if (savedCount > 0) {
                    Text(
                        "$savedCount card${if (savedCount == 1) "" else "s"} saved this session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = onSave, enabled = front.isNotBlank() && !busy) { Text("Save card & add next") }
            }
        }
    }
}

/** Heading, scan buttons and text box for one side of a paper flashcard. */
@Composable
private fun CardSideFields(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    busy: Boolean,
    target: ScanTarget,
    onPaste: (ScanTarget) -> Unit,
    onOpen: (ScanTarget) -> Unit,
    lines: Int,
) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    ScanButtons(
        enabled = !busy,
        pasteLabel = "Paste ${label.lowercase()}",
        onPaste = { onPaste(target) },
        onOpen = { onOpen(target) },
    )
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text("$label text") },
        minLines = lines,
        modifier = Modifier.fillMaxWidth(),
    )
}
