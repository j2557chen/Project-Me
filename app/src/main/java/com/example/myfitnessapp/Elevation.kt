package com.example.myfitnessapp

import android.location.Location
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.util.Locale

// ---------------------------------------------------------------------------
// Reading elevation while recording.
// ---------------------------------------------------------------------------

// The height above sea level, in meters, of a GPS reading. It's null if the reading has none.
//
// There are two kinds of altitude. A phone's GPS measures height above a smooth mathematical
// model of the earth (the "WGS84 ellipsoid"), and sea level doesn't follow that model exactly:
// the gap between the two is tens of meters in many places. Android 14 and newer can give the
// altitude above mean sea level directly, so it's used whenever it's available. On older
// versions only the ellipsoid height exists, so it's used instead. Its climbs and descents
// are still right, and only the absolute numbers on the axis can be off by that gap.
fun altitudeAboveSeaLevel(location: Location): Double? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude()) {
        return location.mslAltitudeMeters
    }
    return if (location.hasAltitude()) location.altitude else null
}

// ---------------------------------------------------------------------------
// The elevation graph.
// ---------------------------------------------------------------------------

// Draws elevation (up the side) against distance (along the bottom) for a recorded activity.
//
// IMPORTANT: this only ever reads the stored readings. The smoothing and thinning below
// happen on a copy made just for drawing, so the saved elevation data is never changed
// or deleted by looking at it.
@Composable
fun ElevationChart(samples: List<ExerciseTrackPoint>, modifier: Modifier = Modifier) {
    // Prepare the copy once, and again only if the stored readings change.
    val points = remember(samples) { prepareElevationPoints(samples) }
    if (points.size < 2) return

    // Every point here has an elevation, so mapNotNull just turns them into a plain list.
    val elevations = points.mapNotNull { it.y }
    val lowest = elevations.min()
    val highest = elevations.max()

    // The vertical range is at least 10 meters tall, so a nearly flat route doesn't turn
    // a meter of GPS wobble into a mountain. It's centered on the data, with a little
    // extra room above and below.
    val span = maxOf(highest - lowest, 10f)
    val middle = (highest + lowest) / 2f

    LineChart(
        points = points,
        xMax = points.last().x.coerceAtLeast(1f),
        axisMin = middle - span * 0.55f,
        axisMax = middle + span * 0.55f,
        yLabel = { formatElevation(it) },
        xLabel = { formatDistance(it) },
        // The line above the graph when a point is touched, such as "132 m at 1.24 km".
        readout = { point ->
            val elevation = point.y
            if (elevation != null) {
                formatElevation(elevation) + " at " + formatDistance(point.x)
            } else {
                "No elevation at " + formatDistance(point.x)
            }
        },
        modifier = modifier
    )
}

// Turns an elevation into text like "132 m".
private fun formatElevation(meters: Float): String {
    return String.format(Locale.getDefault(), "%.0f m", meters)
}

// Makes the copy of the readings that the graph is drawn from. Elevation that came from GPS
// altitude is much noisier than GPS position, and plotted as it is, it looks like a jagged
// saw. (Elevation from a barometer or a terrain map is far steadier.) So the readings are
// smoothed, and thinned out so the graph isn't asked to draw thousands of points.
// This builds new lists and never changes the stored readings.
private fun prepareElevationPoints(samples: List<ExerciseTrackPoint>): List<ChartPoint> {
    // Only readings that have an elevation can go on the graph. mapNotNull skips the others.
    // sortedBy makes a new, ordered list. The stored one isn't touched.
    val sorted = samples
        .mapNotNull { point ->
            point.elevationMeters?.let { ChartPoint(point.distanceMeters, it) }
        }
        .sortedBy { it.x }

    if (sorted.isEmpty()) return emptyList()

    // How many neighbors on each side are looked at. It grows with the length of the
    // activity, between 1 and 15.
    val halfWindow = (sorted.size / 40).coerceIn(1, 15)

    // First a "median" pass, which replaces each reading with the middle value of its
    // neighbors and so removes sudden spikes. Then an "average" pass, which smooths what's left.
    val elevations = sorted.mapNotNull { it.y }
    val smoothed = movingAverage(movingMedian(elevations, halfWindow), halfWindow)

    val all = sorted.mapIndexed { index, point -> ChartPoint(point.x, smoothed[index]) }
    return thinForChart(all)
}
