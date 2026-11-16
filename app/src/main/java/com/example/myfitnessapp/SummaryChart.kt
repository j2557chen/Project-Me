package com.example.myfitnessapp

import com.example.myfitnessapp.ui.theme.APP_FONT_FEATURES
import com.example.myfitnessapp.ui.theme.AppFont
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

private val CHART_HEIGHT = 180.dp
// The gap between the axis values and the plot, and room right of the plot so the last point
// isn't cut off.
private val AXIS_LABEL_GAP = 6.dp
private val PLOT_RIGHT = 8.dp

// How a point is named in the readout above the chart.
private fun bucketLabel(bucket: Bucket, period: Period): String = when (period) {
    Period.DAY -> bucket.start.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    Period.WEEK -> "Week of " + bucket.start.format(DateTimeFormatter.ofPattern("MMM d"))
    Period.MONTH -> bucket.start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    Period.YEAR -> bucket.start.year.toString()
}

// Labels for a divider's span, longest first. The chart uses the first that fits.
private fun dividerLabels(date: LocalDate, period: Period): List<String> = when (period) {
    Period.DAY -> listOf(date.dayOfMonth.toString())
    Period.WEEK -> listOf(date.format(DateTimeFormatter.ofPattern("MMM d")))
    Period.MONTH -> date.format(DateTimeFormatter.ofPattern("MMM")).let { listOf(it, it.take(1)) }
    Period.YEAR -> listOf(date.year.toString(), date.format(DateTimeFormatter.ofPattern("''yy")))
}

// A label with a small arrow that opens a menu of options.
@Composable
fun <T> DropdownLabel(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    fontSize: TextUnit,
    color: Color,
    contentDescription: String
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(0.dp)) {
            Text(text = label(selected), fontSize = fontSize, color = color)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = contentDescription,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        open = false
                    }
                )
            }
        }
    }
}

// The Home summary chart: total time, volume, or distance per day, week, month, or year.
@Composable
fun SummaryChartCard(
    workouts: List<WorkoutStats>,
    modifier: Modifier = Modifier,
    // What the chart can show, the first being the default. With only one, its name is shown
    // without a menu.
    metrics: List<SummaryMetric> = listOf(SummaryMetric.TIME, SummaryMetric.VOLUME, SummaryMetric.DISTANCE),
    label: (SummaryMetric) -> String = { it.label }
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val unit = LocalWeightUnit.current
    var chosen by rememberSaveable { mutableStateOf(metrics.first()) }
    val metric = if (chosen in metrics) chosen else metrics.first()
    var range by rememberSaveable { mutableStateOf(TimeRange.LAST_3_MONTHS) }

    val series = remember(workouts, range, metric, unit) {
        val zone = ZoneId.systemDefault()
        timeSeries(
            workouts, range, LocalDate.now(zone), WeekFields.of(Locale.getDefault()).firstDayOfWeek, zone,
            combine = if (metric.addsUp) Double::plus else ::maxOf
        ) { metric.amount(it, unit) }
    }
    var selectedIndex by remember(series) { mutableStateOf<Int?>(null) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (metrics.size == 1) {
                        Text(text = label(metric), fontSize = AppText.heading, color = labelColor)
                    } else DropdownLabel(
                        selected = metric,
                        options = metrics,
                        label = label,
                        onSelect = { chosen = it },
                        fontSize = AppText.heading,
                        color = labelColor,
                        contentDescription = "Choose what to total"
                    )
                }
                DropdownLabel(
                    selected = range,
                    options = TimeRange.entries,
                    label = { it.label },
                    onSelect = { range = it },
                    fontSize = AppText.small,
                    color = labelColor,
                    contentDescription = "Choose time range"
                )
            }

            // The selected point's total, blank until one is picked. A fixed height keeps the
            // chart from jumping.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val selected = selectedIndex?.let(series.buckets::getOrNull)
                if (selected != null) {
                    Text(
                        text = bucketLabel(selected, series.bucketPeriod) + ": " + metric.format(selected.total, unit),
                        fontSize = AppText.small
                    )
                }
            }

            SummaryChart(
                series = series,
                metric = metric,
                unit = unit,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it }
            )
        }
    }
}

@Composable
private fun SummaryChart(
    series: TimeSeries,
    metric: SummaryMetric,
    unit: WeightUnit,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    val buckets = series.buckets
    if (buckets.isEmpty()) return
    // The periods drawn as points: all of them, or for a best-of measure only those with a session.
    val shown = remember(series, metric) { buckets.indices.filter { metric.addsUp || buckets[it].total > 0 } }

    val colors = MaterialTheme.colorScheme
    val lineColor = colors.primary
    val surfaceColor = colors.surfaceContainerHighest
    val gridColor = colors.outlineVariant.copy(alpha = 0.6f)
    val dividerColor = colors.outlineVariant
    val baselineColor = colors.outline
    val cursorColor = colors.onSurface.copy(alpha = 0.5f)
    val labelStyle = TextStyle(
        fontSize = AppText.caption,
        color = colors.onSurfaceVariant,
        fontFamily = AppFont,
        fontFeatureSettings = APP_FONT_FEATURES
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // The axis top: the smallest gridline step that fits the biggest total in 4 lines.
    val max = buckets.maxOf { it.total }
    val step = metric.gridStep(max)
    val gridLines = ceil(max / step - 1e-9).toInt().coerceAtLeast(1)
    val axisMax = step * gridLines

    // The gridline values, the top one with its unit, and the room they need on the left.
    val axisLabels = remember(metric, unit, step, gridLines, labelStyle) {
        (0..gridLines).map { i ->
            val text = metric.axisLabel(step * i)
            val withUnit = if (i == gridLines) metric.axisUnit(unit)?.let { "$text $it" } ?: text else text
            textMeasurer.measure(withUnit, labelStyle)
        }
    }
    val plotLeft = axisLabels.maxOf { it.size.width } + with(density) { AXIS_LABEL_GAP.toPx() }

    // Positions along the x axis, as a fraction of the way from the chart's start to its end.
    val totalDays = ChronoUnit.DAYS.between(series.start, series.end).toFloat()
    fun fractionOf(date: LocalDate) = (ChronoUnit.DAYS.between(series.start, date) / totalDays).coerceIn(0f, 1f)
    val centers = remember(series) {
        buckets.map { (fractionOf(it.start) + fractionOf(it.end)) / 2f }
    }
    val dividerFractions = remember(series) { series.dividers.map(::fractionOf) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            // Tapping or dragging sideways selects the nearest point, like the other graphs.
            // Only sideways drags are taken, so the page still scrolls up and down.
            .pointerInput(series, plotLeft) {
                detectTapGestures { touch -> nearestPoint(touch.x, plotLeft, centers, shown)?.let(onSelect) }
            }
            .pointerInput(series, plotLeft) {
                detectHorizontalDragGestures(
                    onDragStart = { touch -> nearestPoint(touch.x, plotLeft, centers, shown)?.let(onSelect) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        nearestPoint(change.position.x, plotLeft, centers, shown)?.let(onSelect)
                    }
                )
            }
    ) {
        val left = plotLeft
        val right = size.width - PLOT_RIGHT.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val plotWidth = right - left

        fun xFor(fraction: Float) = left + fraction * plotWidth
        fun yFor(value: Double) = bottom - (value / axisMax).toFloat() * (bottom - top)

        // Horizontal gridlines, with their values on the left.
        axisLabels.forEachIndexed { i, label ->
            val y = yFor(step * i)
            drawLine(
                color = if (i == 0) baselineColor else gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx()
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(left - AXIS_LABEL_GAP.toPx() - label.size.width, y - label.size.height / 2f)
            )
        }

        // Each divider span's room for a label, and its labels (longest first) measured.
        val spans = dividerFractions.indices.map { index ->
            val spanStart = xFor(dividerFractions[index])
            val spanEnd = xFor(dividerFractions.getOrNull(index + 1) ?: 1f)
            val labels = dividerLabels(series.dividers[index], series.dividerPeriod)
                .map { textMeasurer.measure(it, labelStyle) }
            Triple(spanStart, spanEnd, labels)
        }
        fun levelThatFits(index: Int): Int? {
            val (spanStart, spanEnd, labels) = spans[index]
            val room = spanEnd - spanStart - 4.dp.toPx()
            return labels.indexOfFirst { it.size.width <= room }.takeIf { it >= 0 }
        }
        // Whole periods all use the same label length (so not "Jan, F, Mar"). Partial ones at the
        // edges may need shorter labels, and don't shorten the rest.
        val wholeSpans = spans.indices.filter { index ->
            series.dividers[index] >= series.start &&
                    (series.dividers.getOrNull(index + 1) != null || series.dividerPeriod.next(series.dividers[index]) <= series.end)
        }
        val sharedLevel = wholeSpans.mapNotNull(::levelThatFits).maxOrNull() ?: 0

        // A faint vertical line where each divider period begins, with its name centered under
        // its span, or no name if none fits.
        dividerFractions.forEachIndexed { index, fraction ->
            if (fraction > 0f) {
                drawLine(
                    color = dividerColor,
                    start = Offset(xFor(fraction), top),
                    end = Offset(xFor(fraction), bottom),
                    strokeWidth = 1.dp.toPx()
                )
            }
            val (spanStart, spanEnd, labels) = spans[index]
            val level = levelThatFits(index)?.let { if (index in wholeSpans) maxOf(it, sharedLevel) else it }
            val label = level?.let(labels::getOrNull)
            if (label != null) {
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset((spanStart + spanEnd - label.size.width) / 2f, bottom + 4.dp.toPx())
                )
            }
        }

        val points = buckets.mapIndexed { i, bucket -> Offset(xFor(centers[i]), yFor(bucket.total)) }

        selectedIndex?.takeIf { it in shown }?.let { points.getOrNull(it) }?.let { point ->
            drawLine(color = cursorColor, start = Offset(point.x, top), end = Offset(point.x, bottom), strokeWidth = 1.dp.toPx())
        }

        // A smooth curve through every point, rather than straight lines between them.
        val line = smoothPath(shown.map { points[it] })
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Each point, with a ring in the card's color so it stands off the line. Points shrink
        // when they're close together, so a month of days doesn't run into one blob.
        val spacing = plotWidth / buckets.size
        val radius = (spacing * 0.3f).coerceIn(2.dp.toPx(), 4.dp.toPx())
        val ring = (radius / 2f).coerceIn(1.dp.toPx(), 2.dp.toPx())
        shown.forEach { i ->
            val p = points[i]
            val r = if (i == selectedIndex) radius + 2.dp.toPx() else radius
            drawCircle(color = surfaceColor, radius = r + ring, center = p)
            drawCircle(color = lineColor, radius = r, center = p)
        }
    }
}

// The shown point nearest a touch at x pixels across, on a plot starting at `plotLeft`.
private fun PointerInputScope.nearestPoint(x: Float, plotLeft: Float, centers: List<Float>, shown: List<Int>): Int? {
    val fraction = (x - plotLeft) / (size.width - plotLeft - PLOT_RIGHT.toPx())
    return shown.minByOrNull { abs(centers[it] - fraction) }
}

// A smooth curve through the points, left to right (a monotone cubic, Fritsch-Carlson). It
// passes through every point exactly, and between two points it never rises above or dips below
// them, so it doesn't invent highs or lows that aren't in the data.
fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    val n = points.size
    // The slope of each straight piece, then the slope the curve takes at each point.
    val secants = FloatArray(n - 1) { i -> (points[i + 1].y - points[i].y) / (points[i + 1].x - points[i].x) }
    val tangents = FloatArray(n) { i ->
        when (i) {
            0 -> secants[0]
            n - 1 -> secants[n - 2]
            // Flat at a peak or valley, otherwise the average of the two pieces either side.
            else -> if (secants[i - 1] * secants[i] <= 0f) 0f else (secants[i - 1] + secants[i]) / 2f
        }
    }
    // Keeps each piece from overshooting.
    for (i in 0 until n - 1) {
        if (secants[i] == 0f) {
            tangents[i] = 0f
            tangents[i + 1] = 0f
            continue
        }
        val a = tangents[i] / secants[i]
        val b = tangents[i + 1] / secants[i]
        val h = a * a + b * b
        if (h > 9f) {
            val t = 3f / kotlin.math.sqrt(h)
            tangents[i] = t * a * secants[i]
            tangents[i + 1] = t * b * secants[i]
        }
    }
    for (i in 0 until n - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val third = (p1.x - p0.x) / 3f
        path.cubicTo(
            p0.x + third, p0.y + tangents[i] * third,
            p1.x - third, p1.y - tangents[i + 1] * third,
            p1.x, p1.y
        )
    }
    return path
}
