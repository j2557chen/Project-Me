package com.example.myfitnessapp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private const val MAX_NAME_LENGTH = 40

// The part of the day a workout started in: morning from 5 am, noon from 12 pm, afternoon from
// 2 pm, and evening from 5 pm until 5 the next morning.
fun timeOfDayName(hour: Int): String = when (hour) {
    in 5..11 -> "Morning"
    in 12..13 -> "Noon"
    in 14..16 -> "Afternoon"
    else -> "Evening"
}

fun defaultWorkoutName(startTime: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    timeOfDayName(Instant.ofEpochMilli(startTime).atZone(zone).hour) + " Workout"

// The name of a recording saved as its own workout, like "Afternoon Run".
fun recordingWorkoutName(startTime: Long, activityLabel: String, zone: ZoneId = ZoneId.systemDefault()): String =
    timeOfDayName(Instant.ofEpochMilli(startTime).atZone(zone).hour) + " " + activityLabel

// The workout's name: the one it was given, or one from the time it started.
fun Workout.displayName(): String = name?.takeIf { it.isNotBlank() } ?: defaultWorkoutName(startTime)

// The day a workout was on, like "Tue, Nov 3, 2026".
fun formatDay(millis: Long): String =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))

// A workout's name as a title, with a pencil that opens a pop-up to rename it.
@Composable
fun WorkoutNameTitle(workout: Workout, modifier: Modifier = Modifier, fontSize: TextUnit = 30.sp) {
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        // A long name is shown smaller, then on two lines, before it's ever cut short.
        FittedTitle(
            text = workout.displayName(),
            maxSize = fontSize,
            modifier = Modifier.weight(1f, fill = false)
        )
        // A little narrower than a standard button, leaving the name more room.
        IconButton(onClick = { renaming = true }, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Rename workout",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (renaming) {
        RenameWorkoutDialog(
            currentName = workout.displayName(),
            onDismiss = { renaming = false },
            onRename = { text ->
                renaming = false
                // A blank name goes back to the one from the time of day.
                val name = text.trim().ifEmpty { null }
                scope.launch { dao.renameWorkout(workout.id, name) }
            }
        )
    }
}

@Composable
private fun RenameWorkoutDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    // The whole name starts selected, so typing replaces it.
    var text by remember { mutableStateOf(TextFieldValue(currentName, TextRange(0, currentName.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workout name") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.text.length <= MAX_NAME_LENGTH) text = it },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
                placeholder = { Text("Leave blank for the time of day") }
            )
        },
        confirmButton = { TextButton(onClick = { onRename(text.text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
