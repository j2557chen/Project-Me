package com.example.myfitnessapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The screen for looking at a recorded activity after it's finished. From top to bottom:
// a small bar, the route picture, the totals, and the splits.
// dateMillis is the date to show, and onBack is called when the back button is pressed.
@Composable
fun RecordedActivityScreen(
    exercise: Exercise,
    dateMillis: Long,
    // When the activity's workout ended, or null while it's still going.
    workoutEndMillis: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }

    // Every GPS reading saved with this activity: position, elevation, distance, and time.
    // They are only ever read here, never changed. Everything below is worked out from them.
    val pointsFlow = remember(exercise.id) { dao.observeTrackPoints(exercise.id) }
    // Null until they've loaded, so "No elevation…" and an empty splits list don't flash first.
    val loadedPoints by pointsFlow.collectAsState(initial = null)
    val storedPoints = loadedPoints ?: emptyList()

    // The splits are divided up using the Splits setting in the Settings tab, as it is now,
    // not as it was when the activity was recorded. Changing the setting changes how every
    // activity with track points is shown, and the stored readings are never touched.
    val settings = remember { SplitPrefs.load(context) }
    val trackPoints = remember(storedPoints) { storedPoints.mapNotNull { it.toTrackPointOrNull() } }
    val computed = remember(trackPoints, settings) { computeSplits(trackPoints, settings) }

    // The pace over time, worked out from the same stored points.
    val paceSeries = remember(trackPoints) { computePaceSeries(trackPoints) }
    val hasPace = paceSeries.count { it.y != null } >= 2

    // Whether there is anything to show: a finished split, or a leftover one.
    val hasSplits = computed.splits.isNotEmpty() || computed.partial != null

    Column(modifier = modifier.fillMaxSize()) {
        // 1. The bar stays at the top while everything below it scrolls.
        ActivityTopBar(title = exercise.name, dateMillis = dateMillis, onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // 2. The route picture, edge to edge. Activities with no picture (a Timer, or a
            // recording with too few points) skip this. "?.let" only runs its block if
            // there is a picture.
            exercise.routeImagePath?.let { path ->
                item {
                    RouteImage(path = path, cornerRadius = 0.dp)
                }
            }

            // 3. Total time, distance, and overall pace.
            item {
                ActivityTotals(exercise)
            }

            // The graphs and splits below come from the GPS readings, so they wait for them.
            if (loadedPoints == null) return@LazyColumn

            // The elevation graph, under the totals. Only GPS activities have a distance to plot
            // it against. Older activities, saved before elevation was recorded, have no readings.
            if (exercise.distance != null) {
                item {
                    Text(
                        text = "Elevation",
                        fontSize = AppText.heading,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                item {
                    if (storedPoints.count { it.elevationMeters != null } < 2) {
                        Text(
                            text = "No elevation was recorded for this activity.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        ElevationChart(
                            samples = storedPoints,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // The pace graph, under the elevation graph. It's drawn against time rather than
            // distance, and only GPS activities have a pace.
            if (exercise.distance != null) {
                item {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        Text(text = "Pace over time", fontSize = AppText.heading)
                        Text(
                            text = "Minutes per kilometer. Higher on the graph is faster.",
                            fontSize = AppText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    if (!hasPace) {
                        Text(
                            text = "Not enough movement was recorded to draw a pace graph.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        PaceChart(
                            series = paceSeries,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // Heart rate from a watch, through Health Connect, over the workout this activity is
            // in. For a recording saved on its own, that's the recording itself. Shows nothing
            // when there isn't any.
            if (workoutEndMillis != null) {
                item {
                    HeartRateSection(
                        startMillis = dateMillis,
                        endMillis = workoutEndMillis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // 4. The splits.
            item {
                Text(
                    text = "Splits",
                    fontSize = AppText.heading,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            if (!hasSplits) {
                item {
                    Text(
                        text = "No splits were recorded for this activity.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                // A line saying how the splits are divided, and where to change that.
                item {
                    Text(
                        text = settings.describe() + ". You can change this in Settings.",
                        fontSize = AppText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // The column headings.
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SplitRow(
                            split = "Split",
                            distance = "Distance",
                            time = "Time",
                            pace = "Pace",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // One row for each finished split.
                items(computed.splits, key = { it.number }) { split ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SplitRow(
                            split = split.number.toString(),
                            distance = formatDistance(split.distanceMeters),
                            time = formatSplitTime(split.durationMillis),
                            pace = formatSecondsPerKm(paceFor(split.distanceMeters, split.durationMillis)),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // The leftover split, where the activity ended partway through one. It's dimmed
                // and labeled "Last". "?.let" only runs its block if there is one.
                computed.partial?.let { partial ->
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SplitRow(
                                split = "Last",
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

// The header: a back button on the left, the activity in the middle, and the date, in smaller
// text, on the right.
@Composable
private fun ActivityTopBar(title: String, dateMillis: Long, onBack: () -> Unit) {
    ScreenHeader(
        title = title,
        // Room on the right for the date.
        titleEndRoom = 96.dp,
        start = {
            IconButton(onClick = onBack) {
                // AutoMirrored means the arrow flips automatically for right-to-left languages.
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        end = {
            Text(
                text = formatShortDate(dateMillis),
                fontSize = AppText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    )
}

// The activity's total time, distance, and overall pace, side by side.
// Distance and pace are left out for activities with no GPS, like the Timer.
@Composable
private fun ActivityTotals(exercise: Exercise) {
    val distance = exercise.distance

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TotalBlock(
            label = "Time",
            value = exercise.durationSeconds?.let { formatElapsed(it * 1000) } ?: "-"
        )

        if (distance != null) {
            TotalBlock(
                label = "Distance",
                value = String.format(Locale.getDefault(), "%.2f km", distance)
            )
            // The overall pace is the whole activity's time divided by its distance.
            TotalBlock(
                label = "Pace",
                value = formatPace(exercise.durationSeconds, distance)
            )
        }
    }
}

// One total: a small label above a larger value.
@Composable
private fun TotalBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = AppText.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, fontSize = AppText.heading)
    }
}

// Turns a time into a date like "21/10/26" (day, month, year).
private fun formatShortDate(millis: Long): String {
    return DateTimeFormatter.ofPattern("dd/MM/yy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}
