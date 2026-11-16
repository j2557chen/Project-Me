package com.example.myfitnessapp

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.room.withTransaction
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Saving, loading, and starting programs.
// ---------------------------------------------------------------------------

private const val DEFAULT_PROGRAM_NAME = "New Program"

// A program while it's being edited, kept in memory until Save. Numbers are kept as text so
// the user can type freely. key identifies an item on screen; it isn't a database id.
data class ProgramSetDraft(
    val key: Long,
    val repsText: String = "",
    val effortText: String = "",
    val setType: SetType = SetType.NORMAL
)

data class ProgramExerciseDraft(
    val key: Long,
    val name: String,
    val type: ActivityType,
    val effortType: EffortType = EffortType.RPE,
    // Strength exercises use sets; the other types use the details below.
    val sets: List<ProgramSetDraft> = emptyList(),
    val distance: Double? = null,
    val durationSeconds: Long? = null,
    val notes: String? = null,
    // Exercises with the same number are a superset.
    val supersetGroup: Long? = null
)

data class ProgramDraft(val name: String, val exercises: List<ProgramExerciseDraft>)

private val draftKeys = AtomicLong(1)
private fun newDraftKey(): Long = draftKeys.getAndIncrement()

// Reads a saved program into the editor, or null if it doesn't exist.
suspend fun loadProgramDraft(db: WorkoutDatabase, programId: Long): ProgramDraft? {
    val dao = db.workoutDao()
    val program = dao.getProgram(programId) ?: return null
    val setsByExercise = dao.getSetsForProgram(programId).groupBy { it.programExerciseId }
    val exercises = dao.getProgramExercises(programId).map { saved ->
        ProgramExerciseDraft(
            key = newDraftKey(),
            name = saved.name,
            type = saved.type,
            effortType = saved.effortType,
            sets = setsByExercise[saved.id].orEmpty().map { set ->
                ProgramSetDraft(
                    key = newDraftKey(),
                    repsText = set.reps?.let(::formatNumber) ?: "",
                    effortText = set.effort?.let(::formatNumber) ?: "",
                    setType = set.setType
                )
            },
            distance = saved.distance,
            durationSeconds = saved.durationSeconds,
            notes = saved.notes,
            supersetGroup = saved.supersetGroup
        )
    }
    return ProgramDraft(program.name, exercises)
}

// Saves a new program (programId = null), or replaces a saved one's name and exercises.
suspend fun saveProgram(db: WorkoutDatabase, programId: Long?, draft: ProgramDraft) {
    val dao = db.workoutDao()
    // Supersets numbered 1, 2, 3... by the place of their first exercise.
    val groups = tidySupersets(draft.exercises.map { it.supersetGroup }, draft.exercises.indices.map { it.toLong() + 1 })
    db.withTransaction {
        val id = if (programId == null) {
            dao.insertProgram(Program(name = draft.name))
        } else {
            dao.updateProgram(Program(id = programId, name = draft.name))
            // Cascades to their sets.
            dao.deleteProgramExercises(programId)
            programId
        }

        draft.exercises.forEachIndexed { index, exercise ->
            val exerciseId = dao.insertProgramExercise(
                ProgramExercise(
                    programId = id,
                    name = exercise.name,
                    type = exercise.type,
                    position = index,
                    effortType = exercise.effortType,
                    distance = exercise.distance,
                    durationSeconds = exercise.durationSeconds,
                    notes = exercise.notes,
                    supersetGroup = groups[index]
                )
            )
            if (exercise.type == ActivityType.STRENGTH && exercise.sets.isNotEmpty()) {
                dao.insertProgramSets(
                    exercise.sets.mapIndexed { setIndex, set ->
                        ProgramSet(
                            programExerciseId = exerciseId,
                            setNumber = setIndex + 1,
                            reps = set.repsText.toDoubleOrNull(),
                            effort = set.effortText.toDoubleOrNull(),
                            setType = set.setType
                        )
                    }
                )
            }
        }
    }
}

// A finished workout as a program: its exercises in order, with each strength set's reps and
// effort. Weights aren't kept, since programs don't plan them.
fun workoutAsProgramDraft(name: String, exercises: List<Exercise>, sets: List<ExerciseSet>): ProgramDraft {
    val setsByExercise = sets.groupBy { it.exerciseId }
    return ProgramDraft(
        name = name,
        exercises = exercises.sortedBy { it.position }.map { exercise ->
            ProgramExerciseDraft(
                key = newDraftKey(),
                name = exercise.name,
                type = exercise.type,
                effortType = exercise.effortType,
                supersetGroup = exercise.supersetGroup,
                sets = if (exercise.type != ActivityType.STRENGTH) emptyList()
                else setsByExercise[exercise.id].orEmpty().sortedBy { it.setNumber }.map { set ->
                    ProgramSetDraft(
                        key = newDraftKey(),
                        repsText = set.reps?.let(::formatNumber) ?: "",
                        effortText = set.effort?.let(::formatNumber) ?: "",
                        setType = set.setType
                    )
                }
            )
        }
    )
}

// Starts a workout with the program's exercises, in order, with their planned sets or details.
suspend fun startWorkoutFromProgram(db: WorkoutDatabase, programId: Long) {
    val dao = db.workoutDao()
    db.withTransaction {
        // The workout takes the program's name, which the user can still change.
        val workoutId = dao.insertWorkout(
            Workout(startTime = System.currentTimeMillis(), name = dao.getProgram(programId)?.name)
        )
        val setsByExercise = dao.getSetsForProgram(programId).groupBy { it.programExerciseId }
        val planned = dao.getProgramExercises(programId)
        val newIds = mutableListOf<Long>()

        planned.forEachIndexed { index, planned ->
            val exerciseId = dao.insertExercise(
                Exercise(
                    workoutId = workoutId,
                    name = planned.name,
                    type = planned.type,
                    position = index,
                    effortType = planned.effortType,
                    distance = planned.distance,
                    durationSeconds = planned.durationSeconds,
                    notes = planned.notes
                )
            )
            newIds += exerciseId
            if (planned.type == ActivityType.STRENGTH) {
                val sets = setsByExercise[planned.id].orEmpty().map {
                    ExerciseSet(exerciseId = exerciseId, setNumber = it.setNumber, reps = it.reps, effort = it.effort, setType = it.setType)
                }
                // Like adding one by hand, a strength exercise starts with at least one set.
                dao.insertSets(sets.ifEmpty { listOf(ExerciseSet(exerciseId = exerciseId, setNumber = 1)) })
            }
        }

        // The program's supersets, numbered by their first exercise in the new workout.
        tidySupersets(planned.map { it.supersetGroup }, newIds).forEachIndexed { i, group ->
            if (group != null) dao.setSupersetGroup(newIds[i], group)
        }
    }
}

// ---------------------------------------------------------------------------
// The program editor.
// ---------------------------------------------------------------------------

private val SAVE_BUTTON_COLOR = Color(0xFF7B1FA2)

// Makes a new program (programId = null) or edits a saved one. Nothing is saved until Save;
// back leaves without saving.
@Composable
fun ProgramEditorScreen(
    programId: Long?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { WorkoutDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(DEFAULT_PROGRAM_NAME) }
    val exercises = remember { mutableStateListOf<ProgramExerciseDraft>() }
    var loaded by remember { mutableStateOf(programId == null) }

    var showPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    LaunchedEffect(programId) {
        if (programId != null) {
            loadProgramDraft(db, programId)?.let { saved ->
                name = saved.name
                exercises.clear()
                exercises.addAll(saved.exercises)
            }
            loaded = true
        }
    }

    fun replaceExercise(changed: ProgramExerciseDraft) {
        val index = exercises.indexOfFirst { it.key == changed.key }
        if (index >= 0) exercises[index] = changed
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader(
            // Room on the right for the Save button.
            titleEndRoom = 76.dp,
            start = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back without saving")
                }
            },
            end = {
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            saveProgram(db, programId, ProgramDraft(name, exercises.toList()))
                            onClose()
                        }
                    },
                    enabled = loaded && exercises.isNotEmpty() && !saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SAVE_BUTTON_COLOR,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text("Save")
                }
            }
        ) {
            // The program's name, with a pencil to rename it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                FittedTitle(
                    text = name,
                    maxSize = HEADER_TITLE_SIZE,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(onClick = { showRename = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Rename program",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CARD_GAP))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CARD_GAP),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Supersets work as on a workout: link an exercise with the one below.
            val groups = exercises.map { it.supersetGroup }
            val keys = exercises.map { it.key }
            val colors = supersetColors(groups)
            fun applyGroups(newGroups: List<Long?>) {
                newGroups.forEachIndexed { i, g ->
                    if (exercises[i].supersetGroup != g) exercises[i] = exercises[i].copy(supersetGroup = g)
                }
            }
            itemsIndexed(exercises, key = { _, it -> it.key }) { index, exercise ->
                ProgramExerciseCard(
                    exercise = exercise,
                    onChange = ::replaceExercise,
                    onRemove = {
                        exercises.removeAll { it.key == exercise.key }
                        applyGroups(tidySupersets(exercises.map { it.supersetGroup }, exercises.map { it.key }))
                    },
                    supersetColor = colors[index],
                    linkedBelow = isLinkedBelow(groups, index),
                    onToggleLink = if (index < exercises.lastIndex) {
                        { applyGroups(toggleSupersetWithNext(groups, keys, index)) }
                    } else null,
                    onUnlinkAbove = if (isLinkedAbove(groups, index)) {
                        { applyGroups(toggleSupersetWithNext(groups, keys, index - 1)) }
                    } else null
                )
            }
            // At the end of the list, the same Add Exercise button as on a workout.
            item(key = "addExercise") {
                AddExerciseButton(
                    onClick = { showPicker = true },
                    enabled = loaded,
                    modifier = Modifier.width(ADD_EXERCISE_BUTTON_WIDTH)
                )
            }
        }
    }

    if (showPicker) {
        ExercisePickerDialog(
            alreadyAdded = exercises.map { it.name }.toSet(),
            onDismiss = { showPicker = false },
            onSelect = { pickedList ->
                pickedList.forEach { picked ->
                    exercises.add(
                        ProgramExerciseDraft(
                            key = newDraftKey(),
                            name = picked.name,
                            type = picked.type,
                            sets = if (picked.type == ActivityType.STRENGTH) {
                                listOf(ProgramSetDraft(key = newDraftKey()))
                            } else {
                                emptyList()
                            }
                        )
                    )
                }
                showPicker = false
            }
        )
    }

    if (showRename) {
        RenameProgramDialog(
            currentName = name,
            onDismiss = { showRename = false },
            onRename = { newName ->
                name = newName.trim().ifBlank { DEFAULT_PROGRAM_NAME }
                showRename = false
            }
        )
    }
}

@Composable
fun RenameProgramDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    title: String = "Program name",
    confirmLabel: String = "OK"
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 40) text = it },
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onRename(text) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// One exercise in the editor: set rows for strength, the usual details for the other types.
@Composable
private fun ProgramExerciseCard(
    exercise: ProgramExerciseDraft,
    onChange: (ProgramExerciseDraft) -> Unit,
    onRemove: () -> Unit,
    supersetColor: Color? = null,
    linkedBelow: Boolean = false,
    onToggleLink: (() -> Unit)? = null,
    onUnlinkAbove: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(EXERCISE_CARD_WIDTH)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // The name has the whole line, apart from the ⋮ menu in the corner.
                ExerciseTitle(
                    name = exercise.name,
                    modifier = Modifier.padding(end = CORNER_BUTTON_SPACE)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (exercise.type == ActivityType.STRENGTH) {
                    ProgramSets(exercise = exercise, onChange = onChange)
                } else {
                    // Reuses the workout's details boxes on a stand-in exercise. The negative id
                    // never matches a real one, and keeps each card's typed text separate.
                    ExerciseDetails(
                        exercise = Exercise(
                            id = -exercise.key,
                            workoutId = 0,
                            name = exercise.name,
                            type = exercise.type,
                            distance = exercise.distance,
                            durationSeconds = exercise.durationSeconds,
                            notes = exercise.notes
                        ),
                        onUpdate = { edited ->
                            onChange(
                                exercise.copy(
                                    distance = edited.distance,
                                    durationSeconds = edited.durationSeconds,
                                    notes = edited.notes
                                )
                            )
                        }
                    )
                }
            }

            if (supersetColor != null) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .background(supersetColor)
                    )
                }
            }

            // The ⋮ menu, as on a workout's cards: superset, and Remove (which asks first, so an
            // exercise isn't removed by a stray tap).
            var confirmRemove by remember { mutableStateOf(false) }
            ExerciseMenu(
                linkedBelow = linkedBelow,
                onToggleLink = onToggleLink,
                onUnlinkAbove = onUnlinkAbove,
                onReorder = null,
                onDelete = { confirmRemove = true },
                deleteLabel = "Remove exercise",
                modifier = Modifier.align(Alignment.TopEnd)
            )
            if (confirmRemove) {
                ConfirmDestructiveDialog(
                    title = "Remove ${exercise.name}?",
                    message = "Its sets will be removed from the program too.",
                    confirmLabel = "Remove",
                    onConfirm = {
                        confirmRemove = false
                        onRemove()
                    },
                    onDismiss = { confirmRemove = false }
                )
            }
        }
    }
}

// Set | RPE (or RIR) | Reps
@Composable
private fun ProgramSets(exercise: ProgramExerciseDraft, onChange: (ProgramExerciseDraft) -> Unit) {
    fun replaceSet(changed: ProgramSetDraft) {
        onChange(exercise.copy(sets = exercise.sets.map { if (it.key == changed.key) changed else it }))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SetTableHeader(
            effortType = exercise.effortType,
            // A number of one type means nothing as the other, so switching empties the boxes.
            onToggleEffort = {
                onChange(
                    exercise.copy(
                        effortType = exercise.effortType.toggled(),
                        sets = exercise.sets.map { it.copy(effortText = "") }
                    )
                )
            },
            showWeight = false
        )

        val labels = setLabels(exercise.sets.map { it.setType })
        exercise.sets.forEachIndexed { index, set ->
            SetTableRow {
                SetNumberCell(
                    label = labels[index],
                    type = set.setType,
                    onTypeChange = { replaceSet(set.copy(setType = it)) }
                )
                SetCell {
                    CompactNumberField(
                        value = set.effortText,
                        onValueChange = { input ->
                            if (isValidEffortInput(exercise.effortType, input)) {
                                replaceSet(set.copy(effortText = input))
                            }
                        },
                        keyboardType = exercise.effortType.keyboardType,
                        modifier = SetBoxModifier
                    )
                }
                RepsCell {
                    CompactNumberField(
                        value = set.repsText,
                        onValueChange = { input ->
                            if (isRepsInput(input)) {
                                replaceSet(set.copy(repsText = input))
                            }
                        },
                        keyboardType = KeyboardType.Decimal,
                        modifier = SetBoxModifier
                    )
                }
                RemoveSetButton(
                    onClick = { onChange(exercise.copy(sets = exercise.sets.filterNot { it.key == set.key })) },
                    contentDescription = "Remove set"
                )
            }
        }

        AddSetButton(onClick = {
            onChange(exercise.copy(sets = exercise.sets + ProgramSetDraft(key = newDraftKey())))
        })
    }
}

// ---------------------------------------------------------------------------
// The list of saved programs.
// ---------------------------------------------------------------------------

// Tapping a program starts a workout from it. Each also has edit and delete buttons.
@Composable
fun ProgramPickerDialog(
    onDismiss: () -> Unit,
    onStart: (Long) -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val scope = rememberCoroutineScope()

    val programsFlow = remember { dao.observePrograms() }
    val programs by programsFlow.collectAsState(initial = emptyList())

    var programToDelete by remember { mutableStateOf<ProgramSummary?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Choose a Program",
                        fontSize = AppText.heading,
                        modifier = Modifier.padding(end = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (programs.isEmpty()) {
                        Text(
                            text = "No programs yet. Use Create New Program to make one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        programs.forEach { program ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStart(program.id) }
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = program.name,
                                        fontSize = AppText.body,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${program.exerciseCount} " +
                                            if (program.exerciseCount == 1) "exercise" else "exercises",
                                        fontSize = AppText.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onEdit(program.id) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit program")
                                }
                                IconButton(onClick = { programToDelete = program }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete program")
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        }
    }

    programToDelete?.let { program ->
        ConfirmDestructiveDialog(
            title = "Delete program?",
            message = "\"${program.name}\" will be deleted. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch { dao.deleteProgram(program.id) }
                programToDelete = null
            },
            onDismiss = { programToDelete = null }
        )
    }
}
