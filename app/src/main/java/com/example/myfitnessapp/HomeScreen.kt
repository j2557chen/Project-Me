package com.example.myfitnessapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

// How much of the screen's width a card takes, on every screen: Home, Profile, History, an
// exercise's records, Settings, and the exercise cards on workouts and programs.
const val CARD_WIDTH_FRACTION = 0.92f

@Composable
fun HomeScreen(
    // Go to the Workout and Record tabs, from the card shown before the first workout.
    onStartWorkout: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val workoutStatsFlow = remember { dao.observeWorkoutStats() }
    // Null until the database answers, so the cards don't flash empty values while loading.
    val workoutStats by workoutStatsFlow.collectAsState(initial = null)

    val zone = ZoneId.systemDefault()
    // Weeks start on the region's first day, as in the History calendar.
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    val streak = remember(workoutStats) {
        val stats = workoutStats ?: return@remember null
        weekStreak(
            workoutDates = stats.map { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() },
            today = LocalDate.now(zone),
            firstDayOfWeek = firstDayOfWeek
        )
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader(title = Screen.Home.label)

        // The header stays put while the cards scroll under it.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = CARD_GAP),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Before the first workout: the two ways to begin. The cards below stay, at zero, so
            // nothing jumps around once there's something to show.
            if (workoutStats?.isEmpty() == true) {
                FirstWorkoutCard(
                    onStartWorkout = onStartWorkout,
                    onRecord = onRecord,
                    modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)
                )
                Spacer(modifier = Modifier.height(CARD_GAP))
            }

            SummaryCard(streak = streak)

            // With Track Weight on in Settings, a card for logging body weight.
            if (TrackWeight.enabled) {
                Spacer(modifier = Modifier.height(CARD_GAP))
                BodyWeightCard(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION))
            }

            Spacer(modifier = Modifier.height(CARD_GAP))
            LifetimeCard(stats = workoutStats)

            Spacer(modifier = Modifier.height(CARD_GAP))
            MuscleHeatMapCard(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION))

            workoutStats?.let { stats ->
                Spacer(modifier = Modifier.height(CARD_GAP))
                SummaryChartCard(workouts = stats, modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION))
            }

            Spacer(modifier = Modifier.height(CARD_GAP))
        }
    }
}

// The distance and volume of every finished workout added up.
@Composable
private fun LifetimeCard(stats: List<WorkoutStats>?) {
    val unit = LocalWeightUnit.current
    val finished = stats?.filter { it.endTime != null }
    val distance = finished?.sumOf { it.distanceKm ?: 0.0 }
    val volume = finished?.sumOf { it.volumeKg ?: 0.0 }

    Card(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Lifetime Stats", fontSize = AppText.heading, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LifetimeRow("Distance", distance?.let { SummaryMetric.DISTANCE.format(it, unit) })
            LifetimeRow("Volume", volume?.let { SummaryMetric.VOLUME.format(unit.fromKg(it), unit) })
        }
    }
}

@Composable
private fun LifetimeRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = AppText.subheading, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Blank until the database answers.
        Text(text = value ?: "", fontSize = AppText.heading)
    }
}

@Composable
private fun SummaryCard(streak: Int?) {
    Card(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Streak",
                fontSize = AppText.heading,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (streak) {
                    null -> ""
                    1 -> "1 Week"
                    // A flame once the streak is past 3 weeks.
                    else -> if (streak > 3) "$streak Weeks 🔥" else "$streak Weeks"
                },
                fontSize = AppText.stat
            )
        }
    }
}
