package com.example.myfitnessapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.util.Locale

// ---------------------------------------------------------------------------
// The pace graph: pace (up the side) against time (along the bottom).
// ---------------------------------------------------------------------------

// A point's pace is worked out from the readings within this long on either side of it, so
// the line is steady instead of jumping with every GPS wobble.
private const val HALF_WINDOW_MILLIS = 20_000L

// The stretch of readings used must cover at least this much time and distance, or there's
// not enough movement to say what the pace was, and the point is left blank.
private const val MIN_SPAN_MILLIS = 5_000L
private const val MIN_SPAN_METERS = 5f

// A pace slower than this, in seconds per kilometer (one hour per kilometer), is treated as
// standing still, and left blank.
private const val SLOWEST_PACE_SECONDS = 3_600f

// Works out the pace at every point of an activity, from its stored track points.
// Each pace is the time taken divided by the distance covered over a window of readings
// centered on that point. Where the user was standing still, the pace is blank (null),
// which breaks the line. The x of each result is the time since the first reading, in seconds.
// This only reads the points and builds a new list.
fun computePaceSeries(points: List<TrackPoint>): List<ChartPoint> {
    if (points.size < 2) return emptyList()

    val startMillis = points.first().elapsedMillis
    val series = mutableListOf<ChartPoint>()

    // "low" and "high" mark the first and last reading inside the window. Both only ever
    // move forward as we go along, so the whole list is gone through once.
    var low = 0
    var high = 0

    for (i in points.indices) {
        val now = points[i].elapsedMillis

        // Move "low" up to the first reading no earlier than 20 seconds before this one.
        while (points[low].elapsedMillis < now - HALF_WINDOW_MILLIS) low++

        // Move "high" up to the last reading no later than 20 seconds after this one.
        if (high < i) high = i
        while (high + 1 < points.size && points[high + 1].elapsedMillis <= now + HALF_WINDOW_MILLIS) {
            high++
        }

        val millis = points[high].elapsedMillis - points[low].elapsedMillis
        val meters = points[high].distanceMeters - points[low].distanceMeters

        // Seconds per kilometer: the seconds taken, divided by the kilometers covered.
        val pace = if (millis >= MIN_SPAN_MILLIS && meters >= MIN_SPAN_METERS) {
            (millis / 1000f) / (meters / 1000f)
        } else {
            null
        }

        series.add(
            ChartPoint(
                x = (now - startMillis) / 1000f,
                // "takeIf" gives null if the pace is too slow to count.
                y = pace?.takeIf { it <= SLOWEST_PACE_SECONDS }
            )
        )
    }

    return thinForChart(series)
}

// Draws the pace graph from the result of computePaceSeries. Faster is higher up the graph.
@Composable
fun PaceChart(series: List<ChartPoint>, modifier: Modifier = Modifier) {
    // The paces that have a value, put in order, so the vertical range can be picked.
    val values = remember(series) { series.mapNotNull { it.y }.sorted() }
    if (values.size < 2) return

    // The range comes from the 3rd to the 97th percentile, so a few wild readings (a GPS jump,
    // or a long pause before a restart) don't squash the rest of the graph into a flat line.
    // Values outside the range are drawn at the edge.
    val low = values[((values.size - 1) * 0.03f).toInt()]
    val high = values[((values.size - 1) * 0.97f).toInt()]

    // At least 30 seconds per kilometer tall, so a very even pace doesn't look wild, with a
    // little extra room above and below. The pace can't go below zero.
    val span = maxOf(high - low, 30f)
    val middle = (high + low) / 2f

    LineChart(
        points = series,
        xMax = series.last().x.coerceAtLeast(1f),
        axisMin = maxOf(middle - span * 0.6f, 0f),
        axisMax = middle + span * 0.6f,
        yLabel = { formatPaceValue(it) },
        xLabel = { formatSplitTime((it * 1000).toLong()) },
        // The line above the graph when a point is touched, such as "5:32 /km at 12:30".
        // A point with no pace is where the user was standing still.
        readout = { point ->
            val pace = point.y
            val time = formatSplitTime((point.x * 1000).toLong())
            if (pace != null) {
                formatPaceValue(pace) + " /km at " + time
            } else {
                "Stopped at " + time
            }
        },
        modifier = modifier,
        // A smaller pace is a faster one, so the smaller numbers go at the top.
        invertY = true
    )
}

// Turns a pace in seconds per kilometer into text like "5:32".
private fun formatPaceValue(secondsPerKm: Float): String {
    val total = secondsPerKm.toInt()
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}
