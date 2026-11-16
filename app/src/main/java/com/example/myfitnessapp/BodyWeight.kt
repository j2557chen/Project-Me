package com.example.myfitnessapp

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

// ---------------------------------------------------------------------------
// The body weight log. It's only used when Track Weight is turned on in Settings: Home then has
// a card for logging today's weight, and Profile a chart of it over time.
// ---------------------------------------------------------------------------

// One logged body weight, in kilograms. At most one is kept per day: logging again the same day
// replaces it.
@Entity(tableName = "body_weights")
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val weightKg: Double
)

// Whether Track Weight is on. Off until the user turns it on, since not everyone wants it.
object TrackWeight {
    private const val KEY = "track_weight"

    var enabled by mutableStateOf(false)
        private set

    fun load(context: Context) {
        enabled = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        enabled = on
        context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
    }
}

// The start and end of the day `millis` falls on, in the phone's time zone.
private fun dayBounds(millis: Long, zone: ZoneId): Pair<Long, Long> {
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return day.atStartOfDay(zone).toInstant().toEpochMilli() to
        day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

// Logs a body weight for today, replacing any already logged today, and keeps the weight on
// Profile the same.
suspend fun logBodyWeight(context: Context, weightKg: Double, now: Long = System.currentTimeMillis()) {
    val (start, end) = dayBounds(now, ZoneId.systemDefault())
    WorkoutDatabase.getInstance(context).workoutDao()
        .replaceBodyWeightForDay(start, end, BodyWeight(timeMillis = now, weightKg = weightKg))
    ProfilePrefs(context).weightKg = weightKg
}

// The first time Track Weight is on, the weight already on Profile becomes the log's first entry.
suspend fun startBodyWeightLog(context: Context) {
    val dao = WorkoutDatabase.getInstance(context).workoutDao()
    val weight = ProfilePrefs(context).weightKg ?: return
    if (dao.countBodyWeights() == 0) dao.insertBodyWeight(BodyWeight(timeMillis = System.currentTimeMillis(), weightKg = weight))
}

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

// ----- Home ------------------------------------------------------------------------------------

// The latest weight and when it was logged, with a box and button to log today's.
@Composable
fun BodyWeightCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val entriesFlow = remember { dao.observeBodyWeights() }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    val latest = entries.lastOrNull()
    val unit = LocalWeightUnit.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf("") }
    val typedKg = text.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toKg)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Body Weight", fontSize = AppText.heading, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = latest?.let { "${formatNumber(unit.fromKg(it.weightKg))} ${unit.label}" } ?: "Not logged yet",
                        fontSize = if (latest != null) AppText.stat else AppText.subheading
                    )
                    latest?.let {
                        val day = dateOf(it.timeMillis)
                        Text(
                            text = if (day == LocalDate.now()) "Today" else day.format(LONG_DATE),
                            fontSize = AppText.small,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                CompactNumberField(
                    value = text,
                    onValueChange = { if (isDecimalInput(it, 5)) text = it },
                    keyboardType = KeyboardType.Decimal,
                    placeholder = unit.label,
                    modifier = Modifier
                        .width(72.dp)
                        .height(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val kg = typedKg ?: return@Button
                        scope.launch { logBodyWeight(context, kg) }
                        text = ""
                        focusManager.clearFocus()
                    },
                    enabled = typedKg != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Update")
                }
            }
        }
    }
}

// ----- Profile ---------------------------------------------------------------------------------

enum class WeightPeriod(val label: String, val months: Long?) {
    MONTH("1 Month", 1),
    THREE_MONTHS("3 Months", 3),
    SIX_MONTHS("6 Months", 6),
    YEAR("1 Year", 12),
    ALL("All Time", null)
}

// The entries a period shows, oldest first, and the day its chart starts on.
fun weightsInPeriod(entries: List<BodyWeight>, period: WeightPeriod, today: LocalDate): Pair<LocalDate, List<BodyWeight>> {
    val sorted = entries.sortedBy { it.timeMillis }
    val start = period.months?.let { today.minusMonths(it) } ?: sorted.firstOrNull()?.let { dateOf(it.timeMillis) } ?: today
    return start to sorted.filter { !dateOf(it.timeMillis).isBefore(start) }
}

// A "Weight Over Time" row that drops open a chart of the log, with a choice of period.
@Composable
fun WeightOverTimeCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val entriesFlow = remember { dao.observeBodyWeights() }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    val unit = LocalWeightUnit.current
    var open by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(WeightPeriod.THREE_MONTHS) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Weight Over Time", fontSize = AppText.heading, color = labelColor, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Hide chart" else "Show chart",
                    tint = labelColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            AnimatedVisibility(visible = open) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        DropdownLabel(
                            selected = period,
                            options = WeightPeriod.entries,
                            label = { it.label },
                            onSelect = { period = it },
                            fontSize = AppText.body,
                            color = labelColor,
                            contentDescription = "Choose period"
                        )
                    }
                    val today = LocalDate.now()
                    val (start, shown) = remember(entries, period, today) { weightsInPeriod(entries, period, today) }
                    if (shown.isEmpty()) {
                        Text(
                            text = "No weights logged in this period. Log your weight on Home.",
                            fontSize = AppText.small,
                            color = labelColor,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        WeightChart(start = start, today = today, entries = shown, unit = unit)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightChart(start: LocalDate, today: LocalDate, entries: List<BodyWeight>, unit: WeightUnit) {
    val days = (today.toEpochDay() - start.toEpochDay()).coerceAtLeast(1).toFloat()
    val points = entries.map {
        ChartPoint(x = (dateOf(it.timeMillis).toEpochDay() - start.toEpochDay()).toFloat(), y = unit.fromKg(it.weightKg).toFloat())
    }
    val values = points.mapNotNull { it.y }
    // A little room above and below, in whole units.
    val axisMin = floor(values.min() - 2f)
    val axisMax = ceil(values.max() + 2f)
    LineChart(
        points = points,
        xMax = days,
        axisMin = axisMin,
        axisMax = axisMax,
        yLabel = { formatNumber(Math.round(it * 10) / 10.0) },
        xLabel = { start.plusDays(it.toLong()).format(SHORT_DATE) },
        readout = { p -> "${formatNumber(p.y!!.toDouble())} ${unit.label} · ${start.plusDays(p.x.toLong()).format(LONG_DATE)}" },
        modifier = Modifier.padding(top = 4.dp)
    )
}
