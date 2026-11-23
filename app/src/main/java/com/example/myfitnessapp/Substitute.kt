package com.example.myfitnessapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.room.withTransaction

// ---------------------------------------------------------------------------
// Substituting an exercise: swapping it for another that works the same main muscle group, for
// when the machine is taken or something doesn't feel right. Offered from the ⋮ menu on a
// running workout's exercises and in the program editor.
// ---------------------------------------------------------------------------

// What can stand in for an exercise: the others in its main muscle group, of the same kind
// (strength for strength, cardio for cardio), so its sets or details still make sense.
// The ones the user has done come first, most often done first, then the rest in A-Z order.
// Null when the exercise isn't in the library, so there's no group to go by.
class Substitutes(val group: String, val doneBefore: List<LibraryExercise>, val others: List<LibraryExercise>)

fun substitutesFor(name: String, library: List<LibraryExercise>, history: List<ExerciseSummary>): Substitutes? {
    val current = library.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
    val candidates = library.filter {
        it.group == current.group && it.type == current.type && !it.name.equals(name, ignoreCase = true)
    }
    val timesDone = history.associateBy { it.name }
    val (done, notDone) = candidates.partition { it.name in timesDone }
    return Substitutes(
        group = current.group,
        doneBefore = done.sortedWith(
            compareByDescending<LibraryExercise> { timesDone.getValue(it.name).workouts }
                .thenByDescending { timesDone.getValue(it.name).lastTime }
        ),
        others = notDone
    )
}

// Swaps an exercise in a workout for another, and hands back how to undo it and the id of the
// exercise that now holds the substitute (for carrying its rest timer across).
//  - With no sets ticked off yet, the exercise itself becomes the substitute. Its sets stay,
//    with their weights, reps and effort cleared, so last time's numbers for the new exercise
//    show in their place. Its note was about the old exercise, so it goes.
//  - With sets already done, those stay with the original, as they were really done on it. The
//    substitute goes right below it, taking over the sets still to do (at least one).
class Substitution(val newExerciseId: Long, val undo: suspend () -> Unit)

suspend fun substituteExercise(db: WorkoutDatabase, exercise: Exercise, picked: LibraryExercise): Substitution {
    val dao = db.workoutDao()
    val sets = dao.getSetsForExercise(exercise.id).sortedBy { it.setNumber }
    val (done, toDo) = sets.partition { it.done }

    if (done.isEmpty()) {
        db.withTransaction {
            dao.updateExercise(exercise.copy(name = picked.name, type = picked.type, notes = null))
            sets.forEach { dao.updateSet(it.copy(weight = null, reps = null, effort = null)) }
        }
        return Substitution(exercise.id) {
            db.withTransaction {
                dao.updateExercise(exercise)
                sets.forEach { dao.updateSet(it) }
            }
        }
    }

    val orderBefore = dao.getExercisesForWorkout(exercise.workoutId)
    val newId = db.withTransaction {
        val id = dao.insertExercise(
            Exercise(
                workoutId = exercise.workoutId,
                name = picked.name,
                type = picked.type,
                effortType = exercise.effortType,
                // It stays in the original's superset, if it was in one.
                supersetGroup = exercise.supersetGroup
            )
        )
        // The sets still to do move across, numbered from 1, keeping their set types.
        if (toDo.isNotEmpty()) dao.deleteSets(toDo.map { it.id })
        val moved = toDo.ifEmpty { listOf(ExerciseSet(exerciseId = id, setNumber = 1)) }
        dao.insertSets(moved.mapIndexed { i, set -> ExerciseSet(exerciseId = id, setNumber = i + 1, setType = set.setType) })
        // Right below the original, with everything after it moving down one.
        val order = orderBefore.flatMap { if (it.id == exercise.id) listOf(it.id, id) else listOf(it.id) }
        order.forEachIndexed { index, exerciseId -> dao.setExercisePosition(exerciseId, index) }
        id
    }
    return Substitution(newId) {
        db.withTransaction {
            dao.deleteExercise(newId)
            if (toDo.isNotEmpty()) dao.insertSets(toDo)
            orderBefore.forEach { dao.setExercisePosition(it.id, it.position) }
        }
    }
}

// The pop-up listing the substitutes. One tap swaps; exercises already in the workout or program
// are shown faded and can't be picked. A search box narrows a long list.
@Composable
fun SubstituteDialog(
    exerciseName: String,
    alreadyAdded: Set<String>,
    onPick: (LibraryExercise) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val historyFlow = remember { WorkoutDatabase.getInstance(context).workoutDao().observeExerciseSummaries() }
    val history by historyFlow.collectAsState(initial = null)
    val custom = ExerciseLibrary.custom
    val substitutes = remember(exerciseName, history, custom) {
        history?.let { substitutesFor(exerciseName, ExerciseLibrary.all, it) }
    }
    var query by remember { mutableStateOf("") }
    fun List<LibraryExercise>.matching(): List<LibraryExercise> {
        val words = query.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        return filter { exercise -> words.all { it in exercise.name.lowercase() } }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth(POPUP_WIDTH_FRACTION)
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Substitute", fontSize = AppText.heading)
                Text(
                    text = "For $exerciseName",
                    fontSize = AppText.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Not focused at first, so the keyboard doesn't cover the list.
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Exercises already added go last, under their own heading, out of the way.
                val doneBefore = substitutes?.doneBefore.orEmpty().matching().filter { it.name !in alreadyAdded }
                val others = substitutes?.others.orEmpty().matching().filter { it.name !in alreadyAdded }
                val added = (substitutes?.doneBefore.orEmpty() + substitutes?.others.orEmpty())
                    .matching().filter { it.name in alreadyAdded }
                when {
                    // Still loading: nothing yet, rather than a list that reshuffles.
                    history == null -> Spacer(modifier = Modifier.height(48.dp))
                    substitutes == null -> EmptyNote("There's no muscle group to match for this exercise.")
                    doneBefore.isEmpty() && others.isEmpty() && added.isEmpty() -> EmptyNote(
                        if (query.isBlank()) "No other ${substitutes.group.lowercase()} exercises." else "No matches."
                    )
                    else -> LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        listOf(
                            "Done before" to doneBefore,
                            "More ${substitutes.group.lowercase()} exercises" to others,
                            "Already added" to added
                        )
                            .filter { it.second.isNotEmpty() }
                            .forEach { (title, list) ->
                                item(key = "title:$title") {
                                    Text(
                                        text = title,
                                        fontSize = AppText.small,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
                                    )
                                }
                                items(list, key = { "$title:${it.name}" }) { exercise ->
                                    val added = exercise.name in alreadyAdded
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = !added) { onPick(exercise) }
                                            .alpha(if (added) 0.4f else 1f)
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Text(text = exercise.name, fontSize = AppText.body)
                                        val detail = if (exercise.custom) "Custom" else null
                                        if (detail != null) {
                                            Text(
                                                text = detail,
                                                fontSize = AppText.caption,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        fontSize = AppText.body,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
    )
}

// Two arrows passing each other, one pointing right and one left: the Substitute menu icon.
@Composable
fun SwapArrowsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val left = size.width * 0.12f
        val right = size.width * 0.88f
        val head = size.height * 0.18f

        // The right-pointing arrow on top, with its head on the right.
        val topY = size.height * 0.3f
        drawLine(color, Offset(left, topY), Offset(right, topY), stroke, StrokeCap.Round)
        drawLine(color, Offset(right, topY), Offset(right - head, topY - head), stroke, StrokeCap.Round)
        drawLine(color, Offset(right, topY), Offset(right - head, topY + head), stroke, StrokeCap.Round)

        // The left-pointing arrow below, with its head on the left.
        val bottomY = size.height * 0.7f
        drawLine(color, Offset(right, bottomY), Offset(left, bottomY), stroke, StrokeCap.Round)
        drawLine(color, Offset(left, bottomY), Offset(left + head, bottomY - head), stroke, StrokeCap.Round)
        drawLine(color, Offset(left, bottomY), Offset(left + head, bottomY + head), stroke, StrokeCap.Round)
    }
}
