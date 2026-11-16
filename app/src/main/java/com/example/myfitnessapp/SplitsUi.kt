package com.example.myfitnessapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ---------------------------------------------------------------------------
// The splits screen, which slides in over the map on the Record screen.
// ---------------------------------------------------------------------------
// bottomSpace is empty room left at the bottom of the list, so the last rows can scroll up
// clear of the floating controls that sit over it. Raise it if they still overlap.
@Composable
fun SplitsScreen(
    state: RecordingState,
    modifier: Modifier = Modifier,
    // Empty room at the top, for the title pill and the activity card that sit over it.
    topSpace: Dp = 150.dp,
    bottomSpace: Dp = 220.dp
) {
    // A solid background, so the map doesn't show through the list. It's a shade lighter
    // than the plain screen background, so the panel stands out when it slides in.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        if (!state.activity.usesGps) {
            // The Timer has no GPS, so there's no distance to divide into splits.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Splits need GPS, so they aren't available for the Timer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val settings = state.splitSettings

            // The splits are worked out from the recording's track points, the same list that
            // is saved with the activity. This only runs again when a new point arrives.
            val result = remember(state.trackPoints, settings) {
                computeSplits(state.trackPoints, settings)
            }

            // Says which setting this recording is using.
            val description = settings.describe()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Keeps the list clear of the title pill and the activity card.
                Spacer(modifier = Modifier.height(topSpace))

                Text(
                    text = description,
                    fontSize = AppText.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // The column headings.
                SplitRow(
                    split = "Split",
                    distance = "Distance",
                    time = "Time",
                    pace = "Pace",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(contentPadding = PaddingValues(bottom = bottomSpace)) {
                    // One row for each finished split.
                    items(result.splits, key = { it.number }) { split ->
                        SplitRow(
                            split = split.number.toString(),
                            distance = formatDistance(split.distanceMeters),
                            time = formatSplitTime(split.durationMillis),
                            pace = formatSecondsPerKm(paceFor(split.distanceMeters, split.durationMillis)),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // A last row for the split in progress, shown dimmed so it's clear it
                    // isn't finished. After the activity is stopped, it's the final, partial split.
                    // "?.let" only runs its block if there is a split in progress.
                    result.partial?.let { partial ->
                        item {
                            SplitRow(
                                split = if (state.isStopped) "Last" else "Now",
                                distance = formatDistance(partial.distanceMeters),
                                time = formatSplitTime(partial.durationMillis),
                                pace = formatSecondsPerKm(
                                    paceFor(partial.distanceMeters, partial.durationMillis)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// Slides the splits screen in from the right, and out again. It's a function of its own
// because AnimatedVisibility comes in different versions for different layouts, and Kotlin
// picks one using the layout the call is written inside. Written inside a Box that sits within
// a Column, it picks the Column version and refuses it. Here there's no layout around the
// call, so the plain version is always chosen.
@Composable
fun SplitsOverlay(visible: Boolean, state: RecordingState) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = Modifier.fillMaxSize()
    ) {
        SplitsScreen(state = state)
    }
}

// One line of the splits table. The weights make the four columns line up in every row.
@Composable
fun SplitRow(
    split: String,
    distance: String,
    time: String,
    pace: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = split, color = color, modifier = Modifier.weight(0.8f))
        Text(text = distance, color = color, modifier = Modifier.weight(1.2f))
        Text(text = time, color = color, modifier = Modifier.weight(1f))
        Text(text = pace, color = color, modifier = Modifier.weight(1.4f))
    }
}

// The pace of a stretch, in seconds per kilometer, or null if it's too short to mean anything.
fun paceFor(meters: Float, millis: Long): Double? {
    if (meters < 10f) return null
    return (millis / 1000.0) / (meters / 1000.0)
}

// Turns milliseconds into text like "5:32", or "1:05:09" for an hour or more.
// "%02d" means a whole number padded with a zero to at least 2 digits.
fun formatSplitTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

// ---------------------------------------------------------------------------
// The splits section of the Settings screen.
// ---------------------------------------------------------------------------
// Two choices, shown as radio buttons (only one can be selected). The choice is saved as soon
// as it's made. It applies to the next recording, not one already in progress.
@Composable
fun SplitSettingsSection() {
    val context = LocalContext.current

    // Read the saved settings once, and keep what's on screen in step with them.
    val saved = remember { SplitPrefs.load(context) }
    var mode by remember { mutableStateOf(saved.mode) }
    var minutesText by remember { mutableStateOf(saved.minutes.toString()) }

    // The minutes box is valid if it holds a whole number from 1 to the maximum.
    val minutesValid = (minutesText.toIntOrNull() ?: 0) in 1..MAX_SPLIT_MINUTES

    // selectableGroup tells screen readers that these rows are one group of choices.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .selectableGroup()
    ) {
        Text(text = "Splits", fontSize = AppText.heading)

        Spacer(modifier = Modifier.height(8.dp))

        // Choice 1: splits by distance. The whole row can be tapped.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(
                    selected = mode == SplitMode.DISTANCE,
                    onClick = {
                        mode = SplitMode.DISTANCE
                        SplitPrefs.saveMode(context, SplitMode.DISTANCE)
                    },
                    role = Role.RadioButton
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The row handles the tap, so the button itself doesn't need to.
            RadioButton(selected = mode == SplitMode.DISTANCE, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Splits by distance (every 1 km)")
        }

        // Choice 2: splits by time, with a box for the number of minutes.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(
                    selected = mode == SplitMode.TIME,
                    onClick = {
                        mode = SplitMode.TIME
                        SplitPrefs.saveMode(context, SplitMode.TIME)
                    },
                    role = Role.RadioButton
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = mode == SplitMode.TIME, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Splits by time, every ")

            OutlinedTextField(
                value = minutesText,
                onValueChange = { input ->
                    // Digits only, up to three of them.
                    if (input.length <= 3 && input.all { it.isDigit() }) {
                        minutesText = input
                        // Only a valid number is saved. If the box is empty or out of range,
                        // the last good value stays in use.
                        val minutes = input.toIntOrNull()
                        if (minutes != null && minutes in 1..MAX_SPLIT_MINUTES) {
                            SplitPrefs.saveMinutes(context, minutes)
                        }
                    }
                },
                // Greyed out unless this choice is selected.
                enabled = mode == SplitMode.TIME,
                isError = !minutesValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp)
            )

            Text(text = " min")
        }

        // Explains a red box.
        if (!minutesValid) {
            Text(
                text = "Enter a whole number of minutes, from 1 to $MAX_SPLIT_MINUTES.",
                fontSize = AppText.caption,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
