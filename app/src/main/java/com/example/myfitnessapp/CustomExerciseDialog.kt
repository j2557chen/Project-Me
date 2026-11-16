package com.example.myfitnessapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private const val MAX_CUSTOM_NAME = 50

// Makes a new exercise, or changes one the user made before (existing). onSaved gets the saved
// exercise; onDeleted runs if it's deleted.
@Composable
fun CustomExerciseDialog(
    existing: CustomExercise?,
    onDismiss: () -> Unit,
    onSaved: (CustomExercise) -> Unit,
    onDeleted: () -> Unit = {},
    // The name to start with, such as what was typed into the search.
    initialName: String = ""
) {
    val context = LocalContext.current
    val db = remember { WorkoutDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: initialName.trim()) }
    var type by remember { mutableStateOf(existing?.type ?: ActivityType.STRENGTH) }
    var mainMuscle by remember { mutableStateOf(existing?.mainMuscle) }
    var secondary by remember { mutableStateOf(existing?.secondaryMuscleList()?.toSet() ?: emptySet()) }
    var bodyweight by remember { mutableStateOf(existing?.bodyweight ?: false) }
    // Problems are only shown once Save has been tried, so the empty form isn't covered in red.
    var triedSaving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val strength = type == ActivityType.STRENGTH
    val nameProblem = customNameProblem(name, existing?.id)
    val muscleProblem = if (strength && mainMuscle == null) "Choose the main muscle" else null

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(POPUP_WIDTH_FRACTION),
        title = { Text(if (existing == null) "New Exercise" else "Edit Exercise") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_CUSTOM_NAME) name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = triedSaving && nameProblem != null,
                    supportingText = if (triedSaving && nameProblem != null) {
                        { Text(nameProblem) }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                Section("Type")
                ChipRow {
                    CUSTOM_EXERCISE_TYPES.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.displayName()) }
                        )
                    }
                }

                // Muscles and bodyweight only apply to strength exercises.
                if (strength) {
                    Section("Main muscle")
                    if (triedSaving && muscleProblem != null) {
                        Text(muscleProblem, fontSize = AppText.caption, color = MaterialTheme.colorScheme.error)
                    }
                    ChipRow {
                        Muscle.entries.forEach { muscle ->
                            FilterChip(
                                selected = mainMuscle == muscle,
                                onClick = {
                                    mainMuscle = muscle
                                    secondary = secondary - muscle
                                },
                                label = { Text(muscle.label) }
                            )
                        }
                    }

                    // Each counts half a set on the muscle map.
                    Section("Secondary Muscles (Optional)")
                    ChipRow {
                        Muscle.entries.filter { it != mainMuscle }.forEach { muscle ->
                            FilterChip(
                                selected = muscle in secondary,
                                onClick = { secondary = if (muscle in secondary) secondary - muscle else secondary + muscle },
                                label = { Text(muscle.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bodyweight", fontSize = AppText.body)
                            Text(
                                "Sets can be saved with reps and no weight",
                                fontSize = AppText.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = bodyweight, onCheckedChange = { bodyweight = it })
                    }
                }
            }
        },
        // Delete on the left when editing; Cancel and Save on the right.
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = {
                    triedSaving = true
                    if (nameProblem == null && muscleProblem == null) {
                        val exercise = CustomExercise(
                            name = name.trim(),
                            type = type,
                            mainMuscle = if (strength) mainMuscle else null,
                            secondaryMuscles = if (strength) secondaryMusclesText(secondary) else "",
                            bodyweight = strength && bodyweight
                        )
                        scope.launch { onSaved(saveCustomExercise(db, existing, exercise)) }
                    }
                }) { Text("Save") }
            }
        }
    )

    if (confirmDelete && existing != null) {
        ConfirmDestructiveDialog(
            title = "Delete ${existing.name}?",
            message = "It won't be offered when adding exercises. Workouts that used it keep it.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    deleteCustomExercise(db, existing)
                    onDeleted()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(title, fontSize = AppText.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}
