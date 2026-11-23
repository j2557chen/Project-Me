package com.example.myfitnessapp

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Shared helpers: used by both the Workout screen and the workout summary,
// so the add/edit/delete logic for exercises and sets exists in only one place.
// ---------------------------------------------------------------------------

// Adds an exercise picked from the library to a workout.
// Strength exercises also get their first, empty set. withTransaction makes the steps
// succeed or fail together, so an exercise can't end up missing its first set.
suspend fun addExerciseToWorkout(db: WorkoutDatabase, workoutId: Long, picked: LibraryExercise) {
    val dao = db.workoutDao()
    db.withTransaction {
        // A safety net: even if the pop-up is bypassed, never add the same exercise twice.
        // return@withTransaction means "stop here and leave the transaction".
        if (dao.countExercisesNamed(workoutId, picked.name) > 0) return@withTransaction

        // insertExercise returns the new exercise's id, which the set needs.
        val exerciseId = dao.insertExercise(
            Exercise(
                workoutId = workoutId,
                name = picked.name,
                type = picked.type,
                // New exercises go at the end of the list.
                position = dao.nextExercisePosition(workoutId)
            )
        )
        if (picked.type == ActivityType.STRENGTH) {
            dao.insertSet(ExerciseSet(exerciseId = exerciseId, setNumber = 1))
        }
    }
}

// Saves a new order: each exercise's position becomes its index in orderedIds.
suspend fun reorderExercises(db: WorkoutDatabase, orderedIds: List<Long>) {
    val dao = db.workoutDao()
    db.withTransaction {
        // A superset moves as one, so it's never split up by a drag.
        val exercises = orderedIds.mapNotNull { id -> dao.getExercise(id) }
        val groupOf = exercises.associate { it.id to it.supersetGroup }
        keepSupersetsTogether(orderedIds, groupOf).forEachIndexed { index, exerciseId ->
            dao.setExercisePosition(exerciseId, index)
        }
    }
}

// Ends a workout: removes sets with no weight and no reps, renumbers the sets that
// are left so there are no gaps (1, 2, 3), and records the end time.
// Unfinished sets can be ticked off first (tickSetIds), or removed (removeSets), along with any
// exercise that leaves with no sets.
// Everything happens in one transaction, so it either all happens or none of it does.
// A workout that would be left with nothing in it isn't saved: nothing changes, and it returns
// false.
suspend fun finishWorkout(
    db: WorkoutDatabase,
    workoutId: Long,
    tickSetIds: List<Long> = emptyList(),
    removeSets: List<ExerciseSet> = emptyList()
): Boolean {
    val dao = db.workoutDao()
    return db.withTransaction {
        if (!hasAnythingToSave(dao.getExercisesForWorkout(workoutId), dao.getSetsForWorkout(workoutId), removeSets)) {
            return@withTransaction false
        }
        if (tickSetIds.isNotEmpty()) dao.markSetsDone(tickSetIds)
        if (removeSets.isNotEmpty()) {
            dao.deleteSets(removeSets.map { it.id })
            dao.deleteExercisesWithoutSets(removeSets.map { it.exerciseId }.distinct())
        }
        dao.deleteEmptySets(workoutId)

        // Read what's left, group the sets by exercise, and number each group 1, 2, 3...
        // forEachIndexed gives us each item together with its position (index) in the list.
        dao.getSetsForWorkout(workoutId)
            .groupBy { it.exerciseId }
            .values
            .forEach { setsOfOneExercise ->
                setsOfOneExercise
                    .sortedBy { it.setNumber }
                    .forEachIndexed { index, exerciseSet ->
                        val correctNumber = index + 1
                        // Only touch the database for sets whose number actually changed.
                        if (exerciseSet.setNumber != correctNumber) {
                            dao.updateSet(exerciseSet.copy(setNumber = correctNumber))
                        }
                    }
            }

        dao.endWorkout(workoutId, System.currentTimeMillis())
        // Run, cardio, and other exercises with nothing filled in are removed too.
        dao.deleteEmptyOtherExercises(workoutId)
        true
    }
}

// Switches an exercise between RPE and RIR. A number of one means nothing as the other, so
// its sets' effort numbers are cleared.
suspend fun toggleEffortType(db: WorkoutDatabase, exercise: Exercise) {
    val dao = db.workoutDao()
    db.withTransaction {
        dao.updateExercise(exercise.copy(effortType = exercise.effortType.toggled()))
        dao.clearEffortForExercise(exercise.id)
    }
}

// The last time an exercise was done, and its sets.
data class PreviousExercise(val exercise: Exercise, val sets: List<ExerciseSet>)

// The latest earlier time this exercise (by name) was done, or null if it never was.
suspend fun loadPreviousExercise(dao: WorkoutDao, exercise: Exercise): PreviousExercise? {
    val earlier = dao.getPreviousExercise(exercise.name, exercise.workoutId) ?: return null
    return PreviousExercise(earlier, dao.getSetsForExercise(earlier.id))
}

// Adds one card per exercise to a LazyColumn, with all the buttons wired up
// (add set, edit set, delete set, delete exercise).
// "LazyListScope.exerciseItems" is an extension function: it adds exerciseItems(...) to
// the code block of any LazyColumn, so a LazyColumn can simply call exerciseItems(...).
fun LazyListScope.exerciseItems(
    exercises: List<Exercise>,
    sets: List<ExerciseSet>,
    db: WorkoutDatabase,
    scope: CoroutineScope,
    // Called when a recorded activity is tapped. Null makes them not tappable.
    onOpenRecorded: ((Exercise) -> Unit)? = null,
    // Called when a card's rearrange button is pressed. Null hides the button.
    onReorder: (() -> Unit)? = null,
    // How much of the screen's width each card takes.
    cardWidthFraction: Float = EXERCISE_CARD_WIDTH,
    // Outlines in red the empty boxes that stop the workout from being saved.
    highlightIncomplete: Boolean = false,
    // Offers Undo after a set or exercise is deleted. Null deletes without it.
    undoHost: UndoHost? = null,
    // Shows each strength exercise's rest timer button, and starts its countdown when a set is
    // ticked off. Only for the running workout.
    restTimer: Boolean = false,
    // Offers Substitute in each exercise's ⋮ menu. Only for the running workout: a past workout
    // records what was actually done.
    substitute: Boolean = false
) {
    val dao = db.workoutDao()
    val setsByExercise = sets.groupBy { it.exerciseId }
    // Supersets: which exercises are linked, and a colour for each superset.
    val groups = exercises.map { it.supersetGroup }
    val ids = exercises.map { it.id }
    val colors = supersetColors(groups)

    itemsIndexed(exercises, key = { _, it -> it.id }) { index, exercise ->
        val exerciseSets = setsByExercise[exercise.id].orEmpty()
        val context = LocalContext.current

        // Only recorded activities can be opened, and only where a handler was given.
        // "fun() { ... }" is a function written in place, which can be passed around like a value.
        val onOpen: (() -> Unit)? = if (exercise.isRecorded && onOpenRecorded != null) {
            fun() { onOpenRecorded(exercise) }
        } else {
            null
        }

        // Loaded in the background; null until it arrives.
        val previous by produceState<PreviousExercise?>(null, exercise.name, exercise.workoutId) {
            value = loadPreviousExercise(dao, exercise)
        }
        val prBadges = rememberPrBadges(dao, exercise)
        // Whether the "Delete this exercise?" pop-up is open.
        var confirmDelete by remember(exercise.id) { mutableStateOf(false) }
        // Whether the Substitute list is open.
        var substituting by remember(exercise.id) { mutableStateOf(false) }

        ExerciseCard(
            exercise = exercise,
            sets = exerciseSets,
            previous = previous,
            prBadges = prBadges,
            onToggleEffort = { scope.launch { toggleEffortType(db, exercise) } },
            onAddSet = {
                scope.launch {
                    // The new set is numbered one higher than the current highest.
                    val nextNumber = (exerciseSets.maxOfOrNull { it.setNumber } ?: 0) + 1
                    dao.insertSet(ExerciseSet(exerciseId = exercise.id, setNumber = nextNumber))
                }
            },
            onUpdateSet = { updated ->
                // Ticking a set off (not every save of a ticked one) starts this exercise's rest.
                val justTicked = updated.done && exerciseSets.firstOrNull { it.id == updated.id }?.done == false
                if (restTimer && justTicked) {
                    RestTimer.lengths[exercise.id]?.let { RestTimer.start(context, exercise.name, it) }
                }
                scope.launch { dao.updateSet(updated) }
            },
            onUpdateExercise = { updated ->
                scope.launch { dao.updateExercise(updated) }
            },
            // A deleted set can be brought back with Undo, for a moment.
            onDeleteSet = { removed ->
                scope.launch {
                    val undo = deleteSetUndoably(db, removed)
                    undoHost?.offer("Set deleted", undo)
                }
            },
            onClick = onOpen,
            onReorder = onReorder,
            supersetColor = colors[index],
            linkedBelow = isLinkedBelow(groups, index),
            // The last exercise has nothing below it to link with.
            onToggleLink = if (index < exercises.lastIndex) {
                { scope.launch { saveSupersets(db, exercises, toggleSupersetWithNext(groups, ids, index)) } }
            } else null,
            // The bottom of a superset can unlink itself too.
            onUnlinkAbove = if (isLinkedAbove(groups, index)) {
                { scope.launch { saveSupersets(db, exercises, toggleSupersetWithNext(groups, ids, index - 1)) } }
            } else null,
            widthFraction = cardWidthFraction,
            highlightIncomplete = highlightIncomplete,
            // The bin asks first, so an exercise isn't deleted by a stray tap.
            onDelete = { confirmDelete = true },
            restSeconds = RestTimer.lengths[exercise.id],
            onPickRest = if (restTimer && exercise.type == ActivityType.STRENGTH) {
                { seconds -> RestTimer.setLength(context, exercise.id, seconds) }
            } else null,
            // A recording's numbers came from the GPS, so it stays what it was.
            onSubstitute = if (substitute && !exercise.isRecorded) {
                { substituting = true }
            } else null
        )

        if (substituting) {
            SubstituteDialog(
                exerciseName = exercise.name,
                alreadyAdded = exercises.map { it.name }.toSet(),
                onDismiss = { substituting = false },
                onPick = { picked ->
                    substituting = false
                    scope.launch {
                        val swap = substituteExercise(db, exercise, picked)
                        // The rest timer carries across to whichever exercise now has the sets to do.
                        if (swap.newExerciseId != exercise.id) {
                            RestTimer.lengths[exercise.id]?.let { RestTimer.setLength(context, swap.newExerciseId, it) }
                        }
                        undoHost?.offer("Swapped ${exercise.name} for ${picked.name}", swap.undo)
                    }
                }
            )
        }

        if (confirmDelete) {
            ConfirmDestructiveDialog(
                title = "Delete ${exercise.name}?",
                message = "Its sets will be deleted too. You can undo this for a few seconds afterwards.",
                confirmLabel = "Delete",
                onConfirm = {
                    confirmDelete = false
                    scope.launch {
                        val deleted = deleteExerciseUndoably(db, exercise.id) ?: return@launch
                        // Undo is a failsafe after the confirmation. With nowhere to offer it,
                        // the route picture goes straight away.
                        if (undoHost != null) {
                            undoHost.offer("${exercise.name} deleted", deleted.undo, deleted.afterwards)
                        } else {
                            deleted.afterwards()
                        }
                    }
                },
                onDismiss = { confirmDelete = false }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// The workout summary: opened by tapping an entry in History.
// ---------------------------------------------------------------------------
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { WorkoutDatabase.getInstance(context) }
    val dao = remember { db.workoutDao() }
    val scope = rememberCoroutineScope()
    // The "deleted" message with its Undo button, at the bottom.
    val undoHost = rememberUndoHost()

    // The recorded activity being viewed, or null when the workout summary is showing.
    var openActivityId by remember { mutableStateOf<Long?>(null) }

    // Back closes an open activity first, and only then leaves the workout.
    BackHandler(onBack = {
        if (openActivityId != null) {
            openActivityId = null
        } else {
            onBack()
        }
    })

    // Listen to this workout, its exercises, and their sets. Each one updates the
    // screen by itself whenever the database changes.
    val workoutFlow = remember(workoutId) { dao.observeWorkout(workoutId) }
    val workout by workoutFlow.collectAsState(initial = null)

    // Null until loaded, so a workout that's a single recording opens straight on its activity
    // screen instead of flashing the summary first.
    val exercisesFlow = remember(workoutId) { dao.observeExercises(workoutId) }
    val loadedExercises by exercisesFlow.collectAsState(initial = null)

    val setsFlow = remember(workoutId) { dao.observeSetsForWorkout(workoutId) }
    val sets by setsFlow.collectAsState(initial = emptyList())

    // Which pop-ups are open.
    var showAddDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var showSaveAsProgram by remember { mutableStateOf(false) }

    // The workout is null for a moment while it loads. "?: return" means:
    // if it's null, stop here and draw nothing yet.
    val current = workout ?: return
    val exercises = loadedExercises ?: return
    // Copied into a local val so Kotlin can check it for null below.
    val endTime = current.endTime

    // A finished workout that's only one recorded activity opens on that activity, and its back
    // arrow goes straight back to the history (the phone's back does too, through BackHandler).
    val soleActivity = exercises.singleOrNull()?.takeIf { it.isRecorded && endTime != null }
    if (soleActivity != null) {
        RecordedActivityScreen(
            exercise = soleActivity,
            dateMillis = current.startTime,
            workoutEndMillis = endTime,
            onBack = onBack,
            modifier = modifier
        )
        return
    }

    // The workout summary, or a recorded activity opened from it, sliding between the two.
    AnimatedContent(
        targetState = openActivityId,
        transitionSpec = { screenSlideTransition(forward = initialState == null) },
        label = "activity",
        modifier = modifier
    ) { activityId ->
        val openActivity = exercises.firstOrNull { it.id == activityId }
        // A solid background, so the screen underneath never shows through mid-slide.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (openActivity != null) {
                RecordedActivityScreen(
                    exercise = openActivity,
                    dateMillis = current.startTime,
                    workoutEndMillis = endTime,
                    onBack = { openActivityId = null }
                )
            } else Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScreenHeader(
                    start = {
                        IconButton(onClick = onBack) {
                            // AutoMirrored means the arrow flips automatically for right-to-left languages.
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    end = {
                        // A menu of things to do with the workout. For now it has one: saving its
                        // exercises, order, reps and RPE as a new program.
                        if (exercises.isNotEmpty()) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Save as Program") },
                                        onClick = {
                                            menuOpen = false
                                            showSaveAsProgram = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) {
                    WorkoutNameTitle(workout = current, fontSize = HEADER_TITLE_SIZE)
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (showSaveAsProgram) {
                    RenameProgramDialog(
                        currentName = current.displayName(),
                        title = "Save as program",
                        confirmLabel = "Save",
                        onDismiss = { showSaveAsProgram = false },
                        onRename = { name ->
                            showSaveAsProgram = false
                            val draft = workoutAsProgramDraft(name.ifBlank { current.displayName() }, exercises, sets)
                            scope.launch {
                                saveProgram(db, null, draft)
                                Toast.makeText(context, "Saved as program", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Text(
                    text = formatDate(current.startTime),
                    fontSize = AppText.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (endTime == null) {
                    // Still in progress: show the live timer. The duration can only be edited
                    // once the workout has ended.
                    WorkoutTimer(startTime = current.startTime)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatElapsed(endTime - current.startTime),
                            // A little smaller than the workout's name above it.
                            fontSize = AppText.heading
                        )
                        // Smaller than a standard button, so the row adds less space above and below.
                        IconButton(onClick = { showDurationDialog = true }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit duration")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))

                // The scrolling list of exercises. Everything above stays in place.
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    exerciseItems(
                        exercises, sets, db, scope,
                        onOpenRecorded = { openActivityId = it.id },
                        onReorder = { showReorderDialog = true },
                        undoHost = undoHost
                    )
                    // Heart rate from a watch, through Health Connect, over the whole workout. Shows
                    // nothing when there isn't any.
                    if (endTime != null) {
                        item(key = "heartRate") {
                            HeartRateSection(
                                startMillis = current.startTime,
                                endMillis = endTime,
                                modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)
                            )
                        }
                    }
                    // At the end of the list, the same Add Exercise button as on a running workout.
                    item(key = "addExercise") {
                        AddExerciseButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.width(ADD_EXERCISE_BUTTON_WIDTH)
                        )
                    }
                }
            }
            if (openActivity == null) {
                SnackbarHost(undoHost.snackbar, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    if (showReorderDialog) {
        ReorderExercisesDialog(
            exercises = exercises,
            onDismiss = { showReorderDialog = false },
            onReorder = { orderedIds -> scope.launch { reorderExercises(db, orderedIds) } }
        )
    }

    // The exercise search pop-up, the same one used on the Workout screen.
    if (showAddDialog) {
        ExercisePickerDialog(
            // Exercises already in this workout show as unavailable in the list.
            alreadyAdded = exercises.map { it.name }.toSet(),
            onDismiss = { showAddDialog = false },
            onSelect = { picked ->
                // One after another, so each goes to the end of the list in the order picked.
                scope.launch { picked.forEach { addExerciseToWorkout(db, workoutId, it) } }
                showAddDialog = false
            }
        )
    }

    if (showDurationDialog && endTime != null) {
        EditDurationDialog(
            currentMillis = endTime - current.startTime,
            onDismiss = { showDurationDialog = false },
            onSave = { newMillis ->
                scope.launch {
                    // Changing the duration means moving the end time: the start time stays
                    // the same, and the end time becomes start + the new duration.
                    dao.endWorkout(workoutId, current.startTime + newMillis)
                }
                showDurationDialog = false
            }
        )
    }
}

// A pop-up for typing a new workout duration in hours and minutes.
// onSave receives the new duration in milliseconds.
@Composable
fun EditDurationDialog(
    currentMillis: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    // Start the boxes at the current duration. (Seconds are dropped, since this
    // dialog works in whole minutes.)
    val totalMinutes = currentMillis / 60_000L
    var hoursText by remember { mutableStateOf((totalMinutes / 60).toString()) }
    var minutesText by remember { mutableStateOf((totalMinutes % 60).toString()) }

    // The new duration in milliseconds. An empty box counts as 0.
    val newMillis = ((hoursText.toLongOrNull() ?: 0L) * 60L + (minutesText.toLongOrNull() ?: 0L)) * 60_000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Duration") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { input ->
                        // Digits only, up to two of them.
                        if (input.length <= 2 && input.all { it.isDigit() }) hoursText = input
                    },
                    label = { Text("Hours") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { input ->
                        // Digits only, and no higher than 59.
                        if (input.length <= 2 &&
                            input.all { it.isDigit() } &&
                            (input.toIntOrNull() ?: 0) <= 59
                        ) {
                            minutesText = input
                        }
                    },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(newMillis) },
                // A workout can't have a duration of zero.
                enabled = newMillis > 0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
