package com.flashcards.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.flashcards.AppViewModel
import com.flashcards.StudySession
import com.flashcards.data.Card
import com.flashcards.data.Cloze
import com.flashcards.data.Grade
import com.flashcards.data.Scheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal val EasyGreen = Color(0xFF4CAF50)
internal val OkayBlue = Color(0xFF42A5F5)
internal val HardPurple = Color(0xFFAB47BC)
internal val RepeatOrange = Color(0xFFFF9800)

internal val Grade.color: Color
    get() = when (this) {
        Grade.EASY -> EasyGreen
        Grade.OKAY -> OkayBlue
        Grade.HARD -> HardPurple
        Grade.REPEAT -> RepeatOrange
    }

private val Grade.icon: ImageVector
    get() = when (this) {
        Grade.EASY -> Icons.Default.Check
        Grade.OKAY -> Icons.Default.ThumbUp
        Grade.HARD -> Icons.Default.QuestionMark
        Grade.REPEAT -> Icons.Default.Replay
    }

/** Fly-out direction when a grade is chosen with a button (a swipe follows its own drag). */
private fun Grade.direction(): Offset = when (this) {
    Grade.HARD -> Offset(-1f, -0.8f)
    Grade.OKAY -> Offset(1f, -0.8f)
    Grade.REPEAT -> Offset(-1f, 0.6f)
    Grade.EASY -> Offset(1f, 0.35f)
}

/**
 * On the right side the Easy zone is taller than Okay: a drag has to point clearly
 * upward (more than ~25 degrees above horizontal) to count as Okay, so a plain
 * rightward swipe is Easy.
 */
private const val OKAY_SLOPE = 0.47f // tan(25 degrees)

private fun gradeFor(dx: Float, dy: Float): Grade = when {
    dx < 0 && dy < 0 -> Grade.HARD
    dx < 0 -> Grade.REPEAT
    dy < -dx * OKAY_SLOPE -> Grade.OKAY
    else -> Grade.EASY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(deckId: Long, reverse: Boolean, vm: AppViewModel, onBack: () -> Unit) {
    LaunchedEffect(deckId) { vm.ensureSession(deckId) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study") },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    session?.current?.let { current ->
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
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
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

                else -> StudyContent(session = session, reverse = reverse, vm = vm)
            }
        }
    }
}

/** Summary screen with a confetti shower, shared by study and quiz modes. */
@Composable
internal fun SessionComplete(session: StudySession, onStudyAll: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Confetti(
            modifier = Modifier.fillMaxSize(),
            count = 90,
            origin = Offset(0.5f, 0.05f),
            angleFromDeg = 15f,
            angleToDeg = 165f,
            speed = 0.9f,
            durationMs = 2600,
        )
        SessionSummary(session = session, onStudyAll = onStudyAll, onBack = onBack)
    }
}

@Composable
internal fun NothingDue(totalCards: Int, onStudyAll: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("All caught up!", style = MaterialTheme.typography.headlineSmall)
        Text(
            "No cards are due for review right now. Cards you know will come back on a spaced schedule.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (totalCards > 0) {
            Button(onClick = onStudyAll) { Text("Study all $totalCards cards anyway") }
        }
        OutlinedButton(onClick = onBack) { Text("Back to deck") }
    }
}

@Composable
internal fun SessionSummary(session: StudySession, onStudyAll: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (headline, subtitle) = when {
            session.hardCount + session.repeatCount == 0 -> "Perfect session! 🎉" to "You knew every card."
            session.knownCount >= session.reviewed * 0.75 -> "Great work! 🌟" to "You powered through the tricky ones."
            else -> "Session complete 💪" to "The tough ones will be back soon for another go."
        }
        Text(headline, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(subtitle, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${session.total} card${if (session.total == 1) "" else "s"} · ${session.reviewed} review${if (session.reviewed == 1) "" else "s"}" +
                if (session.bestStreak >= 3) " · best streak ${session.bestStreak} 🔥" else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(
                Grade.REPEAT to session.repeatCount,
                Grade.HARD to session.hardCount,
                Grade.OKAY to session.okayCount,
                Grade.EASY to session.easyCount,
            ).forEach { (grade, count) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", style = MaterialTheme.typography.headlineMedium, color = grade.color)
                    Text(grade.label.lowercase(), color = grade.color, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack) { Text("Done") }
        OutlinedButton(onClick = onStudyAll) { Text("Study all cards again") }
    }
}

private class AnswerFlashRequest(val grade: Grade, val id: Int, val streak: Int, val nextIn: String?)

@Composable
private fun StudyContent(session: StudySession, reverse: Boolean, vm: AppViewModel) {
    val card = session.current ?: return
    val totalInSession = session.reviewed + session.remaining + 1

    var flash by remember { mutableStateOf<AnswerFlashRequest?>(null) }
    var flashId by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        StudyColumn(
            session = session,
            card = card,
            reverse = reverse,
            totalInSession = totalInSession,
            onAnswer = { grade ->
                vm.answer(grade)
                val nextIn = vm.lastAnswered?.let { Scheduler.describe(it.dueAt - System.currentTimeMillis()) }
                flash = AnswerFlashRequest(grade, ++flashId, streak = session.streak, nextIn = nextIn)
            },
        )
        flash?.let { request ->
            key(request.id) {
                AnswerFlash(
                    grade = request.grade,
                    streak = request.streak,
                    nextIn = request.nextIn,
                    onDone = { if (flash?.id == request.id) flash = null },
                )
            }
        }
    }
}

/** Session progress as colored segments per grade, out of all planned reviews. */
@Composable
internal fun SessionProgressBar(session: StudySession, total: Int, modifier: Modifier = Modifier) {
    fun frac(n: Int) = if (total == 0) 0f else n / total.toFloat()
    val repeat by animateFloatAsState(frac(session.repeatCount), label = "repeat")
    val hard by animateFloatAsState(frac(session.hardCount), label = "hard")
    val okay by animateFloatAsState(frac(session.okayCount), label = "okay")
    val easy by animateFloatAsState(frac(session.easyCount), label = "easy")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.height(8.dp).clip(RoundedCornerShape(4.dp))) {
        drawRect(track)
        var x = 0f
        for ((f, color) in listOf(repeat to RepeatOrange, hard to HardPurple, okay to OkayBlue, easy to EasyGreen)) {
            val w = size.width * f
            if (w > 0f) drawRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w
        }
    }
}

private val easyPraise = listOf("Nice!", "Got it!", "Great!", "Yes!", "Nailed it!")
private val okayPraise = listOf("Good!", "Solid.", "Getting there!", "Well done.")

/** Big icon that pops in the middle of the screen after an answer. */
@Composable
private fun AnswerFlash(grade: Grade, streak: Int, nextIn: String?, onDone: () -> Unit) {
    val color = grade.color
    val known = grade == Grade.EASY || grade == Grade.OKAY
    val message = remember {
        when {
            grade == Grade.REPEAT -> "Coming right back"
            grade == Grade.HARD -> "Back in 10 min"
            streak >= 10 -> "🔥 $streak in a row — unstoppable!"
            streak >= 5 -> "🔥 $streak in a row!"
            streak >= 3 -> "$streak in a row!"
            grade == Grade.OKAY -> okayPraise[Random.nextInt(okayPraise.size)]
            else -> easyPraise[Random.nextInt(easyPraise.size)]
        }
    }
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        alpha.animateTo(1f, tween(120))
        delay(if (known && nextIn != null) 620 else 380)
        alpha.animateTo(0f, tween(320))
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.graphicsLayer { this.alpha = alpha.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .drawBehind {
                        val r = size.minDimension
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to color.copy(alpha = 0.55f),
                                0.45f to color.copy(alpha = 0.22f),
                                1f to color.copy(alpha = 0f),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = grade.icon,
                    contentDescription = grade.label,
                    tint = color,
                    modifier = Modifier.size(104.dp),
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
            )
            if (known && nextIn != null) {
                Text(
                    "Back in $nextIn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private class Particle(
    val angle: Float, val speed: Float, val color: Color,
    val w: Float, val h: Float, val spin: Float, val drift: Float,
)

/**
 * One-shot confetti burst. [origin] is a fraction of the canvas size; particles leave
 * between [angleFromDeg] and [angleToDeg] (screen angles: 0 = right, 90 = down),
 * decelerate, fall under gravity and fade out.
 */
@Composable
internal fun Confetti(
    modifier: Modifier,
    count: Int,
    origin: Offset,
    angleFromDeg: Float,
    angleToDeg: Float,
    speed: Float,
    durationMs: Int,
) {
    val palette = listOf(
        EasyGreen, Color(0xFF66BB6A), Color(0xFFFFD54F), Color(0xFF4FC3F7),
        Color(0xFFBA68C8), Color(0xFFFF8A65), Color(0xFFF06292),
    )
    val particles = remember {
        val r = Random(System.nanoTime())
        List(count) {
            Particle(
                angle = Math.toRadians(r.nextDouble(angleFromDeg.toDouble(), angleToDeg.toDouble())).toFloat(),
                speed = speed * (0.4f + r.nextFloat() * 0.8f),
                color = palette[r.nextInt(palette.size)],
                w = 5f + r.nextFloat() * 6f,
                h = 3f + r.nextFloat() * 5f,
                spin = (r.nextFloat() - 0.5f) * 1440f,
                drift = (r.nextFloat() - 0.5f) * 0.25f,
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMs, easing = LinearEasing)) }

    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val base = size.minDimension
        val ox = size.width * origin.x
        val oy = size.height * origin.y
        val gravity = 1.4f * base
        val eased = t * (2f - t) // fast start, slows down
        val alpha = 1f - ((t - 0.65f) / 0.35f).coerceIn(0f, 1f)
        for (p in particles) {
            val x = ox + cos(p.angle) * p.speed * base * eased + p.drift * base * t
            val y = oy + sin(p.angle) * p.speed * base * eased + gravity * t * t
            val w = p.w * density
            val h = p.h * density
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h))
            }
        }
    }
}

@Composable
private fun StudyColumn(session: StudySession, card: Card, reverse: Boolean, totalInSession: Int, onAnswer: (Grade) -> Unit) {
    // Centered and capped: a flashcard spread across a 27" monitor is unreadable, and the
    // corner grading zones still get the whole window to aim at.
    CenteredPane(Panes.READING, Modifier.padding(16.dp)) {
    Column(Modifier.fillMaxSize()) {
        SessionProgressBar(session = session, total = totalInSession, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Missed ${session.hardCount + session.repeatCount}", color = HardPurple)
            Text("${session.remaining + 1} to go", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Known ${session.knownCount}", color = EasyGreen)
        }
        Spacer(Modifier.height(8.dp))

        // Re-create gesture/flip state for every new card shown (the same card can
        // come back later in the session after being graded Repeat).
        key(session.reviewed) {
            SwipeArea(card = card, reverse = reverse, repeat = session.currentIsRepeat, modifier = Modifier.weight(1f), onAnswer = onAnswer)
        }
    }
    }
}

@Composable
private fun SwipeArea(card: Card, reverse: Boolean, repeat: Boolean, modifier: Modifier, onAnswer: (Grade) -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var flipped by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val threshold = with(density) { 120.dp.toPx() }
    val flyDistance = with(density) { 900.dp.toPx() }

    /**
     * Sends the card off screen. A swipe keeps flying the way it was dragged; a button
     * press uses the grade's default direction.
     */
    fun swipeOut(grade: Grade, along: Offset? = null) {
        if (answered) return
        answered = true
        val raw = along ?: grade.direction()
        val len = hypot(raw.x, raw.y).coerceAtLeast(0.001f)
        val ux = raw.x / len
        val uy = raw.y / len
        scope.launch {
            launch { offsetX.animateTo(offsetX.value + ux * flyDistance, tween(240)) }
            offsetY.animateTo(offsetY.value + uy * flyDistance, tween(240))
            onAnswer(grade)
        }
    }

    fun snapBack() {
        scope.launch {
            launch { offsetX.animateTo(0f, spring()) }
            launch { offsetY.animateTo(0f, spring()) }
        }
    }

    // Space / Enter flips, 1-4 grade (Repeat, Hard, Okay, Easy), arrow keys grade too.
    WindowKeys { e ->
        when (e.key) {
            Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> { flipped = !flipped; true }
            Key.One, Key.NumPad1 -> { swipeOut(Grade.REPEAT); true }
            Key.Two, Key.NumPad2, Key.DirectionLeft -> { swipeOut(Grade.HARD); true }
            Key.Three, Key.NumPad3 -> { swipeOut(Grade.OKAY); true }
            Key.Four, Key.NumPad4, Key.DirectionRight -> { swipeOut(Grade.EASY); true }
            else -> false
        }
    }

    val dx = offsetX.value
    val dy = offsetY.value
    val strength = (hypot(dx, dy) / threshold).coerceIn(0f, 1f)
    val activeGrade = if (strength > 0.03f) gradeFor(dx, dy) else null

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            CornerLabel(Grade.HARD, Alignment.TopStart, activeGrade, strength)
            CornerLabel(Grade.OKAY, Alignment.TopEnd, activeGrade, strength)
            CornerLabel(Grade.REPEAT, Alignment.BottomStart, activeGrade, strength)
            CornerLabel(Grade.EASY, Alignment.BottomEnd, activeGrade, strength)

            FlashCard(
                card = card,
                flipped = flipped,
                reverse = reverse,
                repeat = repeat,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(
                        width = (maxWidth * 0.86f).coerceAtMost(Panes.CARD),
                        height = (maxHeight * 0.82f).coerceAtMost(560.dp),
                    )
                    .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                    .graphicsLayer { rotationZ = (dx / 60f).coerceIn(-15f, 15f) }
                    .swipeGlow(activeGrade, strength, dx, dy)
                    .pointerInput(Unit) { detectTapGestures { flipped = !flipped } }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + drag.x)
                                    offsetY.snapTo(offsetY.value + drag.y)
                                }
                            },
                            onDragEnd = {
                                val x = offsetX.value
                                val y = offsetY.value
                                if (hypot(x, y) > threshold) {
                                    swipeOut(gradeFor(x, y), along = Offset(x, y))
                                } else {
                                    snapBack()
                                }
                            },
                            onDragCancel = { snapBack() },
                        )
                    },
            )
        }

        Text(
            "Click or Space to flip  ·  drag toward a corner, or press 1–4 to grade  ·  Esc to leave",
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.widthIn(max = Panes.CARD).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((i, grade) in listOf(Grade.REPEAT, Grade.HARD, Grade.OKAY, Grade.EASY).withIndex()) {
                OutlinedButton(
                    onClick = { swipeOut(grade) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = grade.color),
                ) {
                    Text("${grade.label}  ${i + 1}", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CornerLabel(grade: Grade, alignment: Alignment, active: Grade?, strength: Float) {
    val alpha = if (active == grade) 0.35f + 0.65f * strength else 0.28f
    Row(
        Modifier.align(alignment).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(grade.icon, contentDescription = null, tint = grade.color.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        Text(
            grade.label.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = grade.color.copy(alpha = alpha),
        )
    }
}

/**
 * Colored glow around the card that grows with the drag, bleeding in from the side
 * of the corner being dragged toward.
 */
private fun Modifier.swipeGlow(grade: Grade?, strength: Float, dx: Float, dy: Float): Modifier = drawBehind {
    if (grade == null || strength < 0.03f) return@drawBehind
    val color = grade.color
    val spread = 32.dp.toPx() * strength
    val corner = 12.dp.toPx()
    val layers = 12
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    // Gradient runs along the drag direction: transparent behind, brightest toward the corner.
    val start = Offset(size.width / 2 - ux * size.width, size.height / 2 - uy * size.height)
    val end = Offset(size.width / 2 + ux * size.width * 0.6f, size.height / 2 + uy * size.height * 0.6f)
    // Stack of expanding rounded rects, each faint, fakes a soft blurred glow.
    for (i in layers downTo 1) {
        val f = i / layers.toFloat()
        val inset = -spread * f
        val alpha = 0.10f * strength
        val brush = Brush.linearGradient(
            0f to color.copy(alpha = 0f),
            0.55f to color.copy(alpha = alpha * 0.3f),
            1f to color.copy(alpha = alpha),
            start = start,
            end = end,
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = CornerRadius(corner + spread * f),
        )
    }
}

@Composable
private fun FlashCard(card: Card, flipped: Boolean, reverse: Boolean, repeat: Boolean, modifier: Modifier) {
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(350),
        label = "flip",
    )
    val showBack = rotation > 90f
    val cloze = Cloze.isCloze(card.front)
    // With reverse, the card's back is the side shown first (cloze cards always start hidden).
    val showingCardBack = if (cloze) showBack else showBack != reverse
    val text: AnnotatedString = when {
        cloze && !showingCardBack -> AnnotatedString(Cloze.hidden(card.front))
        cloze -> buildAnnotatedString {
            val revealed = Cloze.revealed(card.front)
            append(revealed)
            for (r in Cloze.revealedRanges(card.front)) {
                addStyle(SpanStyle(color = EasyGreen, fontWeight = FontWeight.Bold), r.first, r.last + 1)
            }
            if (card.back.isNotBlank()) {
                append("\n\n")
                withStyle(SpanStyle(color = onSurfaceVariantColor)) { append(card.back) }
            }
        }
        showingCardBack -> AnnotatedString(card.back.ifBlank { "(no back side)" })
        else -> AnnotatedString(card.front.ifBlank { "(no front side)" })
    }

    ElevatedCard(
        modifier = modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 12f * density
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // Un-mirror the back face so its text reads normally.
                .graphicsLayer { if (showBack) rotationY = 180f }
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (cloze) (if (showingCardBack) "ANSWER" else "FILL IN THE BLANK") else if (showingCardBack) "BACK" else "FRONT",
                modifier = Modifier.align(Alignment.TopCenter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (repeat) {
                Text(
                    "AGAIN",
                    modifier = Modifier.align(Alignment.TopEnd),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = RepeatOrange,
                )
            }
            Text(
                text,
                style = if (text.length > 160) MaterialTheme.typography.bodyLarge
                else MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 14,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
