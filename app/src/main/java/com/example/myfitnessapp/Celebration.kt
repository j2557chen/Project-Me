package com.example.myfitnessapp

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Celebrating new records. A strength workout is checked once it's finished, so a typo in the
// middle of a workout never sets one off. A recorded run, walk or ride is checked as soon as
// it's saved, since its numbers come from the GPS, not from typing.
// ---------------------------------------------------------------------------

// One record that was just beaten: which exercise, what kind of record, and the new number.
data class NewRecord(val kind: ActivityKind, val exercise: String, val what: String, val value: String)

// What the pop-up shows: where the records came from ("From push, just now") and the list.
data class Celebration(val from: String, val records: List<NewRecord>)

// Every record a just-finished workout set. Recorded activities are left out: they were
// celebrated when they were saved. Typed-in runs and cardio count, like strength.
suspend fun workoutRecords(context: Context, workoutId: Long, unit: WeightUnit): List<NewRecord> =
    withContext(Dispatchers.IO) {
        val dao = WorkoutDatabase.getInstance(context).workoutDao()
        dao.getExercisesForWorkout(workoutId).flatMap { exercise ->
            when {
                exercise.type == ActivityType.STRENGTH -> {
                    val history = dao.observeStrengthHistory(exercise.name).first()
                    strengthRecords(exercise, strengthBeatenIn(history, workoutId), unit)
                }
                exercise.isRecorded -> emptyList()
                else -> activityRecords(dao, exercise)
            }
        }
    }

// The records a just-saved recording set.
suspend fun recordingRecords(context: Context, exerciseId: Long): List<NewRecord> =
    withContext(Dispatchers.IO) {
        val dao = WorkoutDatabase.getInstance(context).workoutDao()
        val exercise = dao.getExercise(exerciseId) ?: return@withContext emptyList()
        activityRecords(dao, exercise)
    }

// "run", "walk" or "ride", for wording like "Farthest run".
fun activityNoun(type: ActivityType): String = when (type) {
    ActivityType.RUN -> "run"
    ActivityType.WALK -> "walk"
    ActivityType.BIKE -> "ride"
    else -> "session"
}

private fun strengthRecords(exercise: Exercise, beaten: StrengthBeaten?, unit: WeightUnit): List<NewRecord> {
    if (beaten == null) return emptyList()
    fun weight(kg: Double) = formatNumber(unit.fromKg(kg)) + " " + unit.label
    return buildList {
        beaten.heaviestKg?.let { add(NewRecord(ActivityKind.STRENGTH, exercise.name, "Heaviest weight", weight(it))) }
        beaten.bestSet?.let {
            add(NewRecord(ActivityKind.STRENGTH, exercise.name, "Best set", weight(it.weightKg) + " × " + formatNumber(it.reps)))
        }
        beaten.sessionKg?.let {
            add(NewRecord(ActivityKind.STRENGTH, exercise.name, "Most volume in a workout",
                SummaryMetric.VOLUME.format(unit.fromKg(it), unit)))
        }
    }
}

private suspend fun activityRecords(dao: WorkoutDao, exercise: Exercise): List<NewRecord> {
    val withEfforts = tracksFastestTimes(exercise.name, exercise.type)
    val sessions = dao.observeActivityHistory(exercise.name).first()
        .map { BestEffortCache.session(dao, it, withEfforts) }
    val me = sessions.firstOrNull { it.exerciseId == exercise.id } ?: return emptyList()
    val flags = activityPrFlags(sessions, exercise.id)
    val kind = exercise.type.kind()
    val noun = activityNoun(exercise.type)
    val tracks = exercise.type.tracks
    return buildList {
        if (flags.distancePr && TrackedField.DISTANCE in tracks) me.distanceKm?.let {
            add(NewRecord(kind, exercise.name, "Farthest $noun", formatDistance((it * 1000).toFloat())))
        }
        if (flags.timePr && TrackedField.DURATION in tracks) me.durationSeconds?.let {
            add(NewRecord(kind, exercise.name, "Longest $noun", formatElapsed(it * 1000)))
        }
        flags.fastestKm.forEach { km ->
            me.efforts[km]?.let { add(NewRecord(kind, exercise.name, "Fastest $km km", formatSplitTime(it))) }
        }
    }
}

// ----- The pop-up -----

@Composable
fun RecordCelebration(celebration: Celebration, onDismiss: () -> Unit) {
    val count = celebration.records.size
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Tapping anywhere around the card closes it, as with any pop-up.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth(POPUP_WIDTH_FRACTION)
                    .widthIn(max = 440.dp)
                    // Taps on the card itself don't close it.
                    .clickable(interactionSource = null, indication = null) {}
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 8.dp)
                ) {
                    PoppingTrophy()
                    Text(
                        text = if (count == 1) "New record!" else "$count new records",
                        fontSize = AppText.screenTitle,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = celebration.from,
                        fontSize = AppText.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    // A long list scrolls, so the card never runs off the screen.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Records are grouped under their exercise, named once. Each line still slides
                        // in after the one before it, counting across all the groups.
                        var shownSoFar = 0
                        celebration.records.groupBy { it.kind to it.exercise }.forEach { (key, records) ->
                            val (kind, exercise) = key
                            ExerciseRecords(kind, exercise, records, firstIndex = shownSoFar)
                            shownSoFar += records.size
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Nice!", fontWeight = FontWeight.Bold)
                    }
                }
            }
            // The confetti is drawn over everything, and doesn't catch taps.
            Confetti(modifier = Modifier.fillMaxSize())
        }
    }
}

// The gold trophy, springing in from small as the pop-up opens.
@Composable
private fun PoppingTrophy() {
    val scale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(RecordGold.container(), CircleShape)
    ) {
        Icon(TrophyIcon, contentDescription = null, tint = RecordGold.color(), modifier = Modifier.size(36.dp))
    }
}

// One exercise's new records: its icon and name once at the top, then each record with its
// new number in gold. Each line slides up into place a moment after the one before.
@Composable
private fun ExerciseRecords(kind: ActivityKind, exercise: String, records: List<NewRecord>, firstIndex: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.slideIn(appearAfterMillis(firstIndex))
        ) {
            ActivityIcons(kinds = listOf(kind), ringColor = Color.Transparent, size = 32.dp)
            Text(
                text = exercise,
                fontSize = AppText.small,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        records.forEachIndexed { i, record ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // Lined up under the exercise's name, past its icon.
                    .padding(start = 42.dp)
                    .slideIn(appearAfterMillis(firstIndex + i))
            ) {
                Text(
                    text = record.what,
                    fontSize = AppText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(text = record.value, fontSize = AppText.body, fontWeight = FontWeight.SemiBold, color = RecordGold.color())
            }
        }
    }
}

// When the record at this place in the list starts sliding in, after the pop-up opens.
private fun appearAfterMillis(index: Int): Long = 250L + 150L * index

// Fades a line in while it slides up a little, starting after the given wait.
@Composable
private fun Modifier.slideIn(afterMillis: Long): Modifier {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(afterMillis)
        shown.animateTo(1f, tween(durationMillis = 450))
    }
    return graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * 10.dp.toPx()
    }
}

// A short burst of confetti in gold and the activity colours, thrown up from behind the trophy
// and falling away over about a second and a half. It plays once. With animations turned off
// in the phone's settings, it finishes at once, so nothing is drawn.
@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val colors = listOf(
        RecordGold.solid(), RecordGold.solid(), ActivityKind.STRENGTH.color(),
        ActivityKind.RUN.color(), ActivityKind.WALK.color(), ActivityKind.RIDE.color()
    )
    val pieces = remember { List(70) { ConfettiPiece.random(it) } }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1600, easing = LinearEasing))
    }
    Canvas(modifier = modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        // The pieces move as if at 60 frames a second, in density-independent units.
        val frames = t * 96f
        val unit = density
        val origin = Offset(size.width / 2f, size.height * 0.33f)
        val fade = 1f - t * t
        pieces.forEach { piece ->
            val x = origin.x + piece.vx * frames * unit
            val y = origin.y + (piece.vy * frames + 0.11f * frames * frames) * unit
            val side = piece.size * unit
            rotate(degrees = piece.spin * frames, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[piece.colorIndex % colors.size].copy(alpha = fade),
                    topLeft = Offset(x - side / 2f, y - side / 4f),
                    size = Size(side, side / 2f)
                )
            }
        }
    }
}

private class ConfettiPiece(val vx: Float, val vy: Float, val spin: Float, val size: Float, val colorIndex: Int) {
    companion object {
        fun random(index: Int): ConfettiPiece {
            // Thrown upwards in a wide fan, at different speeds.
            val angle = -Math.PI / 2 + (Random.nextDouble() - 0.5) * 1.9
            val speed = 4f + Random.nextFloat() * 6f
            return ConfettiPiece(
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat(),
                spin = (Random.nextFloat() - 0.5f) * 18f,
                size = 6f + Random.nextFloat() * 6f,
                colorIndex = index
            )
        }
    }
}
