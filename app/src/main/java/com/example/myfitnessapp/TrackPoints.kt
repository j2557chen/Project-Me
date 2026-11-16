package com.example.myfitnessapp

// ---------------------------------------------------------------------------
// Track points: everything known about one GPS reading, kept together.
// ---------------------------------------------------------------------------
// A recording is a list of these. The route, the elevation graph, and the splits are all
// worked out from the same list, so a recorded activity can be looked at in different ways
// without being tied to the way it was recorded.
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    // Height above sea level in meters. Null if there isn't one for this reading. While
    // recording, this is the GPS's altitude. A saved reading has the best elevation there
    // was: see finalElevations.
    val elevationMeters: Float?,
    // How far the user had gone by this reading, in meters.
    val distanceMeters: Float,
    // The activity time at this reading, in milliseconds. It's the same time the on-screen
    // timer shows, so time spent paused isn't included.
    val elapsedMillis: Long,
    // While recording on a phone with a barometer: the height that goes with the air pressure
    // at this reading (see Barometer). It isn't stored. It's combined with the GPS's altitude
    // into elevationMeters when the recording is saved.
    val pressureAltitudeMeters: Float? = null
)

// Turns a point as it's stored in the database into a TrackPoint. Activities recorded before
// positions were stored have readings with no position or time, and those can't be used
// to work out splits, so null is returned for them.
fun ExerciseTrackPoint.toTrackPointOrNull(): TrackPoint? {
    // "?: return null" means: use the value, or if it's null, give up and return null.
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    val time = elapsedMillis ?: return null
    return TrackPoint(lat, lon, elevationMeters, distanceMeters, time)
}

// ---------------------------------------------------------------------------
// Working out splits from the points.
// ---------------------------------------------------------------------------

// The splits found in a list of points: the finished ones, and what's left over after the
// last boundary. The leftover is null if it's too small to be worth showing.
data class SplitResult(val splits: List<Split>, val partial: Split?)

// Divides a list of points into splits, using the given settings (every 1 km, or every
// N minutes). This is the one place splits are calculated, for both the live Splits screen
// and for looking at a saved activity. It only reads the points and never changes them.
//
// It goes through the points two at a time, and hands each stretch between them to
// advanceSplits, which ends a split whenever the stretch crosses a boundary.
//
// minPartialMeters is how far the leftover split has to go to be shown.
fun computeSplits(
    points: List<TrackPoint>,
    settings: SplitSettings,
    minPartialMeters: Float = 10f
): SplitResult {
    if (points.isEmpty()) return SplitResult(emptyList(), null)

    // The first split starts at the first point: no distance covered yet, and the time at
    // that moment.
    var progress = SplitProgress(
        splits = emptyList(),
        startMeters = points.first().distanceMeters,
        startElapsedMillis = points.first().elapsedMillis
    )

    // "1 until points.size" counts from 1 up to the last position. Each time round, the
    // stretch runs from the point before to this one.
    for (i in 1 until points.size) {
        val before = points[i - 1]
        val now = points[i]
        progress = advanceSplits(
            progress,
            settings,
            before.distanceMeters,
            before.elapsedMillis,
            now.distanceMeters,
            now.elapsedMillis
        )
    }

    // What's left after the last boundary: how far and how long since it.
    val last = points.last()
    val partialMeters = last.distanceMeters - progress.startMeters
    val partialMillis = last.elapsedMillis - progress.startElapsedMillis

    val partial = if (partialMeters >= minPartialMeters && partialMillis > 0L) {
        Split(
            number = progress.splits.size + 1,
            distanceMeters = partialMeters,
            durationMillis = partialMillis
        )
    } else {
        null
    }

    return SplitResult(progress.splits, partial)
}

// A short description of how splits are being divided, for showing above the table.
fun SplitSettings.describe(): String {
    return when (mode) {
        SplitMode.DISTANCE -> "Splits by distance: every 1 km"
        SplitMode.TIME -> "Splits by time: every $minutes min"
    }
}
