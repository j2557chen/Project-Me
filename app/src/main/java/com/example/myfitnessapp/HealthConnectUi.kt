package com.example.myfitnessapp

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// What a sync is up to, for the Settings bar's pop-ups.
private sealed interface SyncState {
    data object Idle : SyncState
    data object Explaining : SyncState
    data object Working : SyncState
    data class Done(val result: ImportResult) : SyncState
    data class Failed(val message: String) : SyncState
}

// The "Health Connect" bar in Settings: connects the app, then shows when it last synced with a
// Sync Now button.
@Composable
fun HealthConnectBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { WorkoutDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SyncState>(SyncState.Idle) }
    // Bumped to check the status again, after permissions change or a sync finishes.
    var refresh by remember { mutableIntStateOf(0) }
    val status by produceState<HealthConnectStatus?>(null, refresh) { value = healthConnectStatus(context) }
    val lastSync = remember(refresh) { lastWatchSync(context) }

    fun sync() {
        state = SyncState.Working
        scope.launch {
            state = try {
                SyncState.Done(syncWatchWorkouts(context, db))
            } catch (e: Exception) {
                SyncState.Failed("Couldn't read from Health Connect, so nothing was imported.")
            }
            refresh++
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        refresh++
        // Sync straight away once everything has been allowed.
        if (granted.containsAll(HC_PERMISSIONS)) sync()
    }

    val subtitle = when (status) {
        null -> ""
        HealthConnectStatus.NOT_SUPPORTED -> "Not available on this phone"
        HealthConnectStatus.NEEDS_INSTALL -> "Install Health Connect to use your watch's data"
        HealthConnectStatus.NOT_CONNECTED -> "Bring in workouts and heart rate from your watch"
        HealthConnectStatus.CONNECTED -> lastSync?.let { "Synced " + syncTime(it) } ?: "Connected"
    }

    val shape = RoundedCornerShape(12.dp)
    Card(
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = status != HealthConnectStatus.NOT_SUPPORTED && status != null) {
                when (status) {
                    HealthConnectStatus.NOT_CONNECTED -> state = SyncState.Explaining
                    HealthConnectStatus.NEEDS_INSTALL -> context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri()
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    HealthConnectStatus.CONNECTED -> sync()
                    else -> {}
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Health Connect", fontSize = AppText.heading)
                if (subtitle.isNotEmpty()) {
                    Text(text = subtitle, fontSize = AppText.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (status == HealthConnectStatus.CONNECTED) {
                Text(text = "Sync Now", fontSize = AppText.small, color = MaterialTheme.colorScheme.primary)
            } else if (status != HealthConnectStatus.NOT_SUPPORTED) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }

    when (val s = state) {
        SyncState.Idle -> {}
        SyncState.Explaining -> AlertDialog(
            onDismissRequest = { state = SyncState.Idle },
            title = { Text("Connect your watch") },
            text = {
                Text(
                    "Your watch's app (Fitbit, Samsung Health, Garmin Connect and others) saves your " +
                            "workouts and heart rate to Health Connect. This app reads them from there: " +
                            "runs, walks, rides and other cardio are added to your history, and your " +
                            "heart rate is graphed on each workout. Nothing is written back."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state = SyncState.Idle
                    permissionLauncher.launch(HC_PERMISSIONS)
                }) { Text("Connect") }
            },
            dismissButton = { TextButton(onClick = { state = SyncState.Idle }) { Text("Cancel") } }
        )
        SyncState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Syncing") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Reading your watch's workouts…")
                }
            },
            confirmButton = {}
        )
        is SyncState.Done -> AlertDialog(
            onDismissRequest = { state = SyncState.Idle },
            title = { Text("Sync finished") },
            text = {
                // Workouts already brought in are skipped quietly; each sync looks back a month.
                val imported = s.result.imported
                Text(
                    when (imported) {
                        0 -> "No new watch workouts."
                        1 -> "Imported 1 workout from your watch."
                        else -> "Imported $imported workouts from your watch."
                    }
                )
            },
            confirmButton = { TextButton(onClick = { state = SyncState.Idle }) { Text("OK") } }
        )
        is SyncState.Failed -> AlertDialog(
            onDismissRequest = { state = SyncState.Idle },
            title = { Text("Couldn't sync") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { state = SyncState.Idle }) { Text("OK") } }
        )
    }
}

// "Today 3:41 PM" or "Nov 2, 3:41 PM".
private fun syncTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    val sameDay = time.toLocalDate() == java.time.LocalDate.now(zone)
    return if (sameDay) "today " + time.format(DateTimeFormatter.ofPattern("h:mm a"))
    else time.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
}

// The heart rate graph for a stretch of time, on a card. Shows nothing while loading, or when
// there's no heart rate (no watch, or Health Connect not connected), so screens look as before.
@Composable
fun HeartRateSection(startMillis: Long, endMillis: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val samples by produceState<List<HeartRateSample>?>(null, startMillis, endMillis) {
        value = try { heartRateSamples(context, startMillis, endMillis) } catch (e: Exception) { null }
    }
    val readings = samples ?: return
    val summary = heartRateSummary(readings) ?: return
    val series = remember(readings, startMillis) { heartRateSeries(readings, startMillis) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Heart Rate", fontSize = AppText.heading)
            Text(
                text = "Avg ${summary.averageBpm} · Max ${summary.maxBpm} bpm",
                fontSize = AppText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            HeartRateChart(series)
        }
    }
}

// Heart rate over time, drawn the same way as the pace and elevation graphs.
@Composable
fun HeartRateChart(series: List<ChartPoint>, modifier: Modifier = Modifier) {
    val values = remember(series) { series.mapNotNull { it.y } }
    if (values.size < 2) return
    LineChart(
        points = series,
        xMax = series.last().x.coerceAtLeast(1f),
        axisMin = (values.min() - 10f).coerceAtLeast(0f),
        axisMax = values.max() + 10f,
        yLabel = { "${it.toInt()}" },
        xLabel = { formatSplitTime((it * 1000).toLong()) },
        readout = { point ->
            val time = formatSplitTime((point.x * 1000).toLong())
            point.y?.let { "${it.toInt()} bpm at $time" } ?: "No reading at $time"
        },
        modifier = modifier
    )
}

// Shown when Health Connect's settings ask how this app uses health data.
@Composable
fun HealthDataUseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How this app uses your health data") },
        text = {
            Text(
                "The app reads your exercise sessions, distance and heart rate from Health Connect. " +
                        "Watch workouts are added to your workout history, and heart rate is shown as a " +
                        "graph on your workouts. The data stays on your phone, isn't shared, and nothing " +
                        "is written back to Health Connect."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
