package com.flashcards.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.flashcards.AppViewModel
import com.flashcards.data.CardParser
import com.flashcards.data.SplitMode
import com.flashcards.ocr.OcrCleaner
import com.flashcards.ocr.OcrEngine
import kotlinx.coroutines.launch
import java.io.File

private enum class ScanTarget { PAGE, FRONT, BACK }

private fun cropOptions() = CropImageOptions(
    imageSourceIncludeCamera = false,
    imageSourceIncludeGallery = false,
    guidelines = CropImageView.Guidelines.ON,
    activityTitle = "Crop to the text you want",
    cropMenuCropButtonTitle = "Scan",
    outputCompressFormat = Bitmap.CompressFormat.JPEG,
    outputCompressQuality = 95,
)

private fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(deckId: Long, vm: AppViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // What the recognizer actually saw, so bad scans are easy to diagnose.
    var lastImage by remember { mutableStateOf<Bitmap?>(null) }
    var dropped by remember { mutableStateOf<List<String>>(emptyList()) }

    fun runOcr(uri: Uri) {
        scope.launch {
            busy = true
            try {
                val result = OcrEngine.recognize(context, uri)
                lastImage = thumbnail(result.processed)
                dropped = result.dropped
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

    val cropper = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val uri = result.uriContent
        if (result.isSuccessful && uri != null) {
            runOcr(uri)
        } else if (result.error != null) {
            scope.launch { snackbar.showSnackbar("Crop failed: ${result.error?.message}") }
        }
    }
    fun crop(uri: Uri) = cropper.launch(CropImageContractOptions(uri, cropOptions()))

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) crop(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) crop(uri)
    }

    fun capture(t: ScanTarget) {
        target = t
        val uri = newCaptureUri(context)
        pendingUri = uri
        takePicture.launch(uri)
    }

    fun pick(t: ScanTarget) {
        target = t
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                title = { Text("Scan cards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Page / list") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Paper flashcard") })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
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
                    onCamera = { capture(ScanTarget.PAGE) },
                    onGallery = { pick(ScanTarget.PAGE) },
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
                    onCamera = { capture(it) },
                    onGallery = { pick(it) },
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
private fun thumbnail(src: Bitmap): Bitmap {
    val maxEdge = 900
    val scale = maxEdge.toFloat() / maxOf(src.width, src.height)
    if (scale >= 1f) return src
    return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
}

@Composable
private fun ScanPreview(preview: Bitmap?, dropped: List<String>) {
    if (preview == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("What the scanner saw", style = MaterialTheme.typography.labelLarge)
        Image(
            bitmap = preview.asImageBitmap(),
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
    cameraLabel: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCamera, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(cameraLabel)
        }
        OutlinedButton(onClick = onGallery, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Gallery")
        }
    }
}

@Composable
private fun PageTab(
    preview: Bitmap?,
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
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSave: () -> Unit,
) {
    val keepCount = drafts.size - excluded.count { it < drafts.size }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Photograph a textbook page, vocabulary list, or notes, then crop to the part you want. Bigger text scans better: half a page at a time beats a whole page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScanButtons(enabled = !busy, cameraLabel = "Scan page", onCamera = onCamera, onGallery = onGallery)
            ScanPreview(preview, dropped)

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Recognized text") },
                placeholder = { Text("term - definition\nQ: question\nA: answer") },
                minLines = 6,
                maxLines = 14,
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

            if (drafts.isNotEmpty()) {
                HorizontalDivider()
                Text("Preview · $keepCount of ${drafts.size} cards", style = MaterialTheme.typography.titleSmall)
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
                                    d.back.ifBlank { "(no back \u2014 tap to edit)" },
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
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider()
        Button(
            onClick = onSave,
            enabled = keepCount > 0 && !busy,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(if (keepCount == 1) "Add 1 card to deck" else "Add $keepCount cards to deck")
        }
    }
}

@Composable
private fun FlashcardTab(
    preview: Bitmap?,
    dropped: List<String>,
    front: String,
    back: String,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    busy: Boolean,
    savedCount: Int,
    onCamera: (ScanTarget) -> Unit,
    onGallery: (ScanTarget) -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Scan each side of a paper flashcard. You can fix any recognition mistakes before saving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ScanPreview(preview, dropped)
            Text("Front", style = MaterialTheme.typography.titleSmall)
            ScanButtons(
                enabled = !busy,
                cameraLabel = "Scan front",
                onCamera = { onCamera(ScanTarget.FRONT) },
                onGallery = { onGallery(ScanTarget.FRONT) },
            )
            OutlinedTextField(
                value = front,
                onValueChange = onFrontChange,
                label = { Text("Front text") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Text("Back", style = MaterialTheme.typography.titleSmall)
            ScanButtons(
                enabled = !busy,
                cameraLabel = "Scan back",
                onCamera = { onCamera(ScanTarget.BACK) },
                onGallery = { onGallery(ScanTarget.BACK) },
            )
            OutlinedTextField(
                value = back,
                onValueChange = onBackChange,
                label = { Text("Back text") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            if (savedCount > 0) {
                Text(
                    "$savedCount card${if (savedCount == 1) "" else "s"} saved this session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        Button(
            onClick = onSave,
            enabled = front.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Save card & scan next") }
    }
}
