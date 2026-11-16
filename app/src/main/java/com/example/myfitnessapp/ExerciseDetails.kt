package com.example.myfitnessapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// A duration split into the three boxes it's typed in.
private data class DurationParts(val hours: Long, val minutes: Long, val seconds: Long)

private fun splitDuration(totalSeconds: Long) =
    DurationParts(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)

// Two digits, at most 59.
private fun isMinutesOrSecondsInput(input: String) =
    isWholeNumberInput(input, 2) && (input.toIntOrNull() ?: 0) <= 59

// The details section for run, cardio, and other exercises. A recorded activity gets a
// read-only version, since its numbers came from the recording. Anything else is editable.
// previous is the last time this exercise was done; its numbers show faintly in empty boxes.
@Composable
fun ExerciseDetails(exercise: Exercise, onUpdate: (Exercise) -> Unit, previous: Exercise? = null) {
    if (exercise.isRecorded) {
        RecordedDetails(exercise)
    } else {
        EditableExerciseDetails(exercise, onUpdate, previous)
    }
}

// The details of a recorded activity, as plain text that can't be changed.
@Composable
private fun RecordedDetails(exercise: Exercise) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        exercise.type.tracks.forEach { field ->
            // "when" must cover every TrackedField. Only distance and duration are
            // recorded, so the others show nothing.
            when (field) {
                TrackedField.DISTANCE -> exercise.distance?.let { km ->
                    DetailRow("Distance") {
                        Text(text = formatNumber(km) + " km", fontSize = AppText.body)
                    }
                }

                TrackedField.DURATION -> exercise.durationSeconds?.let { seconds ->
                    DetailRow("Duration") {
                        Text(text = formatElapsed(seconds * 1000), fontSize = AppText.body)
                    }
                }

                TrackedField.NOTES, TrackedField.WEIGHT, TrackedField.REPS -> {}
            }
        }

        // Pace is worked out from distance and duration, as in the editable version.
        if (exercise.type == ActivityType.RUN ||
            exercise.type == ActivityType.WALK ||
            exercise.type == ActivityType.BIKE
        ) {
            DetailRow("Pace") {
                Text(
                    text = formatPace(exercise.durationSeconds, exercise.distance),
                    fontSize = AppText.body
                )
            }
        }
    }
}

// The details section for run, cardio, and other exercises. Unlike strength, which has a
// row per set, these have one set of results per exercise.
// Which boxes appear comes from the activity type's "tracks" list (see ActivityType),
// so changing what a type tracks only needs a change there.
@Composable
private fun EditableExerciseDetails(
    exercise: Exercise,
    onUpdate: (Exercise) -> Unit,
    previous: Exercise?
) {
    val saved = exercise.durationSeconds?.let(::splitDuration)
    val last = previous?.durationSeconds?.let(::splitDuration)

    // What's typed in each box is kept as text, started from the saved values.
    // remember(exercise.id) keeps the text stable while the database updates behind it.
    var distanceText by remember(exercise.id) {
        mutableStateOf(exercise.distance?.let(::formatNumber) ?: "")
    }
    var hoursText by remember(exercise.id) { mutableStateOf(saved?.hours?.toString() ?: "") }
    var minutesText by remember(exercise.id) { mutableStateOf(saved?.minutes?.toString() ?: "") }
    var secondsText by remember(exercise.id) { mutableStateOf(saved?.seconds?.toString() ?: "") }

    // Adds the three boxes up into one total in seconds, or null if all three are empty.
    // A function declared inside another function (a "local function") can use that
    // function's variables, which is why it can read the text states above.
    fun currentSeconds(): Long? {
        if (hoursText.isEmpty() && minutesText.isEmpty() && secondsText.isEmpty()) return null
        return (hoursText.toLongOrNull() ?: 0L) * 3600L +
            (minutesText.toLongOrNull() ?: 0L) * 60L +
            (secondsText.toLongOrNull() ?: 0L)
    }

    // Saves everything currently typed. Every box is included each time, so one box
    // can never overwrite another with an old value.
    fun save() {
        onUpdate(
            exercise.copy(
                distance = distanceText.toDoubleOrNull(),
                durationSeconds = currentSeconds()
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // One section for each field this activity type tracks, in the order it lists them.
        exercise.type.tracks.forEach { field ->
            // "when" must cover every TrackedField. Weight and reps belong to strength
            // sets, so they do nothing here.
            when (field) {
                TrackedField.DISTANCE -> DetailRow("Distance") {
                    CompactNumberField(
                        value = distanceText,
                        onValueChange = { input ->
                            if (isDecimalInput(input, 7)) {
                                distanceText = input
                                save()
                            }
                        },
                        keyboardType = KeyboardType.Decimal,
                        placeholder = previous?.distance?.let(::formatNumber),
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "km", fontSize = AppText.small)
                }

                TrackedField.DURATION -> DetailRow("Duration") {
                    TimePart(hoursText, "h", placeholder = last?.hours?.toString()) { input ->
                        if (isWholeNumberInput(input, 2)) {
                            hoursText = input
                            save()
                        }
                    }
                    TimePart(minutesText, "m", placeholder = last?.minutes?.toString()) { input ->
                        if (isMinutesOrSecondsInput(input)) {
                            minutesText = input
                            save()
                        }
                    }
                    TimePart(secondsText, "s", placeholder = last?.seconds?.toString()) { input ->
                        if (isMinutesOrSecondsInput(input)) {
                            secondsText = input
                            save()
                        }
                    }
                }

                // Notes are typed under the exercise's name, on every card (see ExerciseNote).
                TrackedField.NOTES, TrackedField.WEIGHT, TrackedField.REPS -> {}
            }
        }

        // Pace is worked out from distance and duration, so it's shown for GPS activities
        // but never stored.
        if (exercise.type == ActivityType.RUN ||
            exercise.type == ActivityType.WALK ||
            exercise.type == ActivityType.BIKE
        ) {
            DetailRow("Pace") {
                Text(
                    text = formatPace(currentSeconds(), distanceText.toDoubleOrNull()),
                    fontSize = AppText.small
                )
            }
        }
    }
}

// A labeled row, the same height as a set row, so the details line up with strength exercises.
// "content" is a block of code the caller supplies, placed to the right of the label.
// "RowScope.() -> Unit" means that block can use row features like weight(...).
@Composable
private fun DetailRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SET_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Wide enough for "Distance" and "Duration" with a gap after, so every value starts
        // at the same place. A label that's wider still, at a large font size, pushes its
        // value along rather than running into it.
        Text(
            text = label,
            fontSize = AppText.small,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 88.dp)
                .padding(end = 8.dp)
        )
        content()
    }
}

// One part of a time: a small number box followed by its unit letter ("h", "m", or "s").
@Composable
private fun TimePart(
    value: String,
    unit: String,
    placeholder: String?,
    onValueChange: (String) -> Unit
) {
    CompactNumberField(
        value = value,
        onValueChange = onValueChange,
        keyboardType = KeyboardType.Number,
        placeholder = placeholder,
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
    )
    Spacer(modifier = Modifier.width(2.dp))
    Text(text = unit, fontSize = AppText.small)
    Spacer(modifier = Modifier.width(6.dp))
}

// Turns a duration and a distance into a pace like "5:06 /km".
// Shows a dash until both have been filled in.
fun formatPace(seconds: Long?, km: Double?): String {
    if (seconds == null || km == null || km <= 0.0 || seconds <= 0L) return "-"

    // Seconds per kilometer, rounded to a whole number.
    val secondsPerKm = (seconds / km).roundToInt()

    // "%d:%02d" means a whole number, a colon, then a whole number padded with a
    // zero to at least 2 digits (so 5 minutes 6 seconds shows as 5:06).
    return "%d:%02d /km".format(secondsPerKm / 60, secondsPerKm % 60)
}
