package com.example.myfitnessapp

import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// PR badges for an exercise card, and the screens listing each exercise's records.
// ---------------------------------------------------------------------------

// The records an exercise card shows: which of its sets set one, and labels for the card.
data class PrBadges(val setIds: Set<Long> = emptySet(), val labels: List<String> = emptyList())

// Works out the badges for one exercise in its workout, against everything done before it.
// The history is watched, so a badge appears as soon as a record is beaten.
@Composable
fun rememberPrBadges(dao: WorkoutDao, exercise: Exercise): PrBadges {
    if (exercise.type == ActivityType.STRENGTH) {
        val historyFlow = remember(exercise.name) { dao.observeStrengthHistory(exercise.name) }
        val history by historyFlow.collectAsState(initial = emptyList())
        return remember(history, exercise.workoutId) {
            val flags = strengthPrFlags(history, exercise.workoutId)
            PrBadges(flags.prSetIds, if (flags.sessionVolumePr) listOf("Session Volume PR") else emptyList())
        }
    }

    val sessions = rememberActivitySessions(dao, exercise.name, exercise.type)
    return remember(sessions, exercise.id) {
        val flags = activityPrFlags(sessions, exercise.id)
        val tracks = exercise.type.tracks
        PrBadges(labels = buildList {
            if (flags.distancePr && TrackedField.DISTANCE in tracks) add("Distance PR")
            if (flags.timePr && TrackedField.DURATION in tracks) add("Time PR")
            flags.fastestKm.forEach { add("$it km PR") }
        })
    }
}

// Every session of a distance or time exercise, with fastest times where it has them.
@Composable
private fun rememberActivitySessions(dao: WorkoutDao, name: String, type: ActivityType): List<ActivitySession> {
    val rowsFlow = remember(name) { dao.observeActivityHistory(name) }
    val rows by rowsFlow.collectAsState(initial = emptyList())
    val withEfforts = tracksFastestTimes(name, type)
    val sessions by produceState(emptyList<ActivitySession>(), rows) {
        value = withContext(Dispatchers.IO) { rows.map { BestEffortCache.session(dao, it, withEfforts) } }
    }
    return sessions
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun recordDate(startTime: Long): String =
    Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)

// Under a lifetime total: how many workouts it covers, like "Over 42 workouts".
private fun lifetimeSpan(workoutStarts: List<Long>): String {
    val count = workoutStarts.distinct().size
    return if (count == 1) "Over 1 workout" else "Over $count workouts"
}

private fun formatKm(km: Double): String = formatNumber((km * 100).roundToLong() / 100.0) + " km"

// The header of both records screens: a back arrow and the title, with a pencil on the right
// when there's something to edit.
@Composable
private fun RecordsHeader(title: String, onBack: () -> Unit, onEdit: (() -> Unit)? = null) {
    ScreenHeader(
        title = title,
        start = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        end = {
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit exercise")
                }
            }
        }
    )
}

// Whether an exercise matches every word typed, in its name, muscle group or kind (like
// "strength" or "run"). The kind isn't shown in the list, but can still be searched for.
private fun matchesSearch(name: String, group: String, type: ActivityType?, words: List<String>): Boolean {
    val text = "$name $group ${type?.displayName().orEmpty()}".lowercase()
    return words.all { it in text }
}

// Every exercise: the ones the user has done first, then the rest of the library, with a
// search bar above. Tapping one opens its records.
@Composable
fun ExerciseListScreen(onOpen: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val summariesFlow = remember { dao.observeExerciseSummaries() }
    val summaries by summariesFlow.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }

    val customList = ExerciseLibrary.custom
    val groups = remember(customList) { ExerciseLibrary.all.associate { it.name to it.group } }
    val words = query.trim().lowercase().split(" ").filter { it.isNotEmpty() }
    val done = summaries.orEmpty().filter { matchesSearch(it.name, groups[it.name].orEmpty(), it.type, words) }
    val doneNames = summaries.orEmpty().map { it.name }.toSet()
    val others = ExerciseLibrary.all.filter { it.name !in doneNames && matchesSearch(it.name, it.group, it.type, words) }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        RecordsHeader(title = "Exercises", onBack = onBack)
        Spacer(modifier = Modifier.height(CARD_GAP))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)
        )
        Spacer(modifier = Modifier.height(CARD_GAP))

        // Wait for the user's exercises, so the list doesn't open on the library and then
        // keep its place as their exercises arrive above it.
        if (summaries == null) return@Column

        LazyColumn(
            modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP)
        ) {
            if (done.isNotEmpty()) item(key = "done") { SectionLabel("Your Exercises") }
            items(done, key = { "done:" + it.name }) { summary ->
                ExerciseRow(
                    name = summary.name,
                    detail = "Last on " + recordDate(summary.lastTime),
                    onClick = { onOpen(summary.name) }
                )
            }
            if (others.isNotEmpty()) item(key = "others") { SectionLabel("Other Exercises") }
            items(others, key = { "other:" + it.name }) { exercise ->
                ExerciseRow(
                    name = exercise.name,
                    detail = if (exercise.custom) "Custom · ${exercise.group}" else exercise.group,
                    onClick = { onOpen(exercise.name) }
                )
            }
            if (done.isEmpty() && others.isEmpty() && summaries != null) item(key = "none") {
                Text(
                    text = "No exercises found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }
            item { Spacer(modifier = Modifier.height(CARD_GAP)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = AppText.small,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@Composable
// The exercise's kind ([Strength] and so on) isn't shown here; it can still be searched for.
private fun ExerciseRow(name: String, detail: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(start = 13.dp, end = 13.dp, top = 8.dp, bottom = 4.dp)) {
            Text(text = name, fontSize = AppText.body)
            Text(text = detail, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// One record: what it is on the left, and its value and date on the right.
@Composable
private fun RecordRow(label: String, value: String, date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = AppText.body, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(text = value, fontSize = AppText.body)
            Text(text = date, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecordsCard(title: String?, rows: List<Triple<String, String, String>>) {
    Card(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    fontSize = AppText.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            rows.forEachIndexed { i, (label, value, date) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RecordRow(label, value, date)
            }
        }
    }
}

// One exercise's records.
@Composable
fun ExerciseRecordsScreen(
    name: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Called with the new name when a custom exercise is renamed here.
    onRenamed: (String) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    // An exercise the user made can be edited or deleted from here, with the pencil.
    val custom = customExerciseNamed(name)
    var editing by remember { mutableStateOf(false) }
    if (editing && custom != null) {
        CustomExerciseDialog(
            existing = custom,
            onDismiss = { editing = false },
            onSaved = { saved ->
                editing = false
                if (saved.name != name) onRenamed(saved.name)
            },
            onDeleted = {
                editing = false
                onBack()
            }
        )
    }
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val unit = LocalWeightUnit.current
    val summariesFlow = remember { dao.observeExerciseSummaries() }
    val summaries by summariesFlow.collectAsState(initial = null)
    // An exercise not done yet takes its type from the library.
    val type = summaries?.let { list ->
        list.firstOrNull { it.name == name }?.type ?: ExerciseLibrary.all.firstOrNull { it.name == name }?.type
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        RecordsHeader(title = name, onBack = onBack, onEdit = if (custom != null) ({ editing = true }) else null)

        // The header stays put while the records scroll under it.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = CARD_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CARD_GAP)
        ) {
            when (type) {
                null -> {}
                ActivityType.STRENGTH -> {
                    MusclesWorkedCard(name)
                    StrengthRecordsSection(dao, name, unit)
                }
                else -> ActivityRecordsSection(dao, name, type)
            }
            Spacer(modifier = Modifier.height(CARD_GAP))
        }
    }
}

@Composable
private fun NoRecords() {
    Text(
        text = "No records yet",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp)
    )
}

@Composable
private fun StrengthRecordsSection(dao: WorkoutDao, name: String, unit: WeightUnit) {
    val historyFlow = remember(name) { dao.observeStrengthHistory(name) }
    val history by historyFlow.collectAsState(initial = null)
    val records = remember(history) { history?.let(::strengthRecords) } ?: return

    fun weight(kg: Double) = formatNumber(unit.fromKg(kg))
    fun volume(kg: Double) = SummaryMetric.VOLUME.format(unit.fromKg(kg), unit)

    val rows = buildList {
        records.heaviest?.let {
            add(Triple("Heaviest Weight", "${weight(it.weightKg)} ${unit.label} × ${formatNumber(it.reps)}", recordDate(it.startTime)))
        }
        records.bestSet?.let {
            add(Triple("Best Set Volume", "${volume(it.volumeKg)} (${weight(it.weightKg)} × ${formatNumber(it.reps)})", recordDate(it.startTime)))
        }
        records.bestSession?.let {
            add(Triple("Best Session Volume", volume(it.volumeKg), recordDate(it.startTime)))
        }
    }
    if (rows.isEmpty()) {
        NoRecords()
        return
    }
    // Everything lifted in this exercise, added up: weight × reps over every set.
    val sets = history.orEmpty()
    RecordsCard(
        title = "Lifetime",
        rows = listOf(Triple("Total Volume", volume(sets.sumOf { it.volumeKg }), lifetimeSpan(sets.map { it.startTime })))
    )
    RecordsCard(title = "Personal Records", rows = rows)

    // Progress over time: the heaviest set of each workout, or its volume, charted like the
    // Home summary.
    val workouts = remember(history) {
        history.orEmpty().groupBy { it.workoutId }.map { (_, sets) ->
            val start = sets.first().startTime
            WorkoutStats(
                startTime = start,
                endTime = start,
                volumeKg = sets.sumOf { it.volumeKg },
                distanceKm = null,
                heaviestKg = sets.maxOf { it.weightKg }
            )
        }
    }
    SummaryChartCard(
        workouts = workouts,
        metrics = listOf(SummaryMetric.HEAVIEST_SET, SummaryMetric.VOLUME),
        label = { it.shortLabel },
        modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)
    )
}

@Composable
private fun ActivityRecordsSection(dao: WorkoutDao, name: String, type: ActivityType) {
    val sessions = rememberActivitySessions(dao, name, type)
    val records = remember(sessions) { activityRecords(sessions) }

    val rows = buildList {
        if (TrackedField.DISTANCE in type.tracks) records.longestDistance?.let {
            add(Triple("Longest Distance", formatKm(it.distanceKm!!), recordDate(it.startTime)))
        }
        if (TrackedField.DURATION in type.tracks) records.longestTime?.let {
            add(Triple("Longest Time", formatSplitTime(it.durationSeconds!! * 1000), recordDate(it.startTime)))
        }
    }
    if (rows.isEmpty() && records.fastestTimes.isEmpty()) {
        NoRecords()
        return
    }
    // Every session's distance added up, for exercises that track distance.
    val withDistance = sessions.filter { (it.distanceKm ?: 0.0) > 0 }
    if (TrackedField.DISTANCE in type.tracks && withDistance.isNotEmpty()) {
        RecordsCard(
            title = "Lifetime",
            rows = listOf(
                Triple("Total Distance", formatKm(withDistance.sumOf { it.distanceKm!! }), lifetimeSpan(withDistance.map { it.startTime }))
            )
        )
    }
    if (rows.isNotEmpty()) RecordsCard(title = "Personal Records", rows = rows)
    if (tracksFastestTimes(name, type) && records.fastestTimes.isNotEmpty()) {
        RecordsCard(
            title = "Fastest Times",
            rows = records.fastestTimes.map {
                Triple("${it.distanceKm} km", formatSplitTime(it.millis), recordDate(it.session.startTime))
            }
        )
    }

    // Distance over time, charted like the Home summary; time for exercises without a distance.
    // A session's time is its own duration, given as the span from its start.
    val metrics = when {
        TrackedField.DISTANCE in type.tracks -> listOf(SummaryMetric.DISTANCE)
        TrackedField.DURATION in type.tracks -> listOf(SummaryMetric.TIME)
        else -> emptyList()
    }
    if (metrics.isNotEmpty()) {
        val workouts = remember(sessions) {
            sessions.map {
                WorkoutStats(
                    startTime = it.startTime,
                    endTime = it.startTime + (it.durationSeconds ?: 0) * 1000,
                    volumeKg = null,
                    distanceKm = it.distanceKm
                )
            }
        }
        SummaryChartCard(workouts = workouts, metrics = metrics, label = { it.shortLabel }, modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION))
    }
}

// Which muscles an exercise works: its main muscle in full color and the ones that assist in a
// lighter shade, on the same figures as the Home muscle map.
@Composable
private fun MusclesWorkedCard(name: String) {
    val muscles = remember(name) { MuscleMap.musclesFor(name) }
    if (muscles.isEmpty()) return
    val skin = skinColor()
    val main = MaterialTheme.colorScheme.primary
    val assisting = lerp(skin, main, 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Muscles Worked", fontSize = AppText.body, color = labelColor)
            Spacer(modifier = Modifier.height(8.dp))
            BodyFigures(
                muscleColor = { muscle ->
                    when (muscles[muscle]) {
                        null -> skin
                        1.0 -> main
                        else -> assisting
                    }
                },
                height = 200.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Main" to main, "Assisting" to assisting).forEach { (label, color) ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color, RoundedCornerShape(3.dp))
                    )
                    Text(text = label, fontSize = AppText.caption, color = labelColor, modifier = Modifier.padding(start = 4.dp, end = 12.dp))
                }
            }
        }
    }
}
