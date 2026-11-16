package com.example.myfitnessapp

import android.content.Context

// ---------------------------------------------------------------------------
// Splits: a recording divided into equal pieces, so each piece can be compared.
// ---------------------------------------------------------------------------

// The two ways to decide where a split ends.
enum class SplitMode {
    // A split ends every kilometer.
    DISTANCE,

    // A split ends every few minutes.
    TIME
}

// How long a split is, in each mode.
const val SPLIT_DISTANCE_METERS = 1000f
const val DEFAULT_SPLIT_MINUTES = 5
const val MAX_SPLIT_MINUTES = 180

// The user's choices from the Settings screen. "minutes" is only used in TIME mode.
data class SplitSettings(
    val mode: SplitMode = SplitMode.DISTANCE,
    val minutes: Int = DEFAULT_SPLIT_MINUTES
)

// One finished split.
data class Split(
    // 1 for the first split, 2 for the second, and so on.
    val number: Int,
    // How far the user went in this split. Always 1000 m in DISTANCE mode.
    val distanceMeters: Float,
    // How long the split took, as the activity timer counts it. Paused time isn't included.
    val durationMillis: Long
)

// Where the splits have got to: the finished ones, and where the one in progress began.
// The pieces are bundled together so advanceSplits can take them in and hand them back.
data class SplitProgress(
    val splits: List<Split> = emptyList(),
    // The total distance, in meters, at the moment the split in progress began.
    val startMeters: Float = 0f,
    // The activity time, in milliseconds, at the moment the split in progress began.
    val startElapsedMillis: Long = 0L
)

// ---------------------------------------------------------------------------
// Remembering the settings. They go in the same small preferences file that holds the
// dark mode setting, so they're still there after the app is closed.
// ---------------------------------------------------------------------------
object SplitPrefs {
    private const val FILE = SETTINGS_FILE
    private const val KEY_MODE = "split_mode"
    private const val KEY_MINUTES = "split_minutes"

    // Reads the saved settings, falling back to splits by distance and 5 minutes.
    fun load(context: Context): SplitSettings {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        // The mode is saved as its name, such as "TIME". If nothing valid is saved, use DISTANCE.
        val savedName = prefs.getString(KEY_MODE, null)
        val mode = SplitMode.entries.firstOrNull { it.name == savedName } ?: SplitMode.DISTANCE

        // coerceIn holds the number between the two limits, in case a bad value was saved.
        val minutes = prefs.getInt(KEY_MINUTES, DEFAULT_SPLIT_MINUTES)
            .coerceIn(1, MAX_SPLIT_MINUTES)

        return SplitSettings(mode, minutes)
    }

    fun saveMode(context: Context, mode: SplitMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun saveMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MINUTES, minutes)
            .apply()
    }
}

// ---------------------------------------------------------------------------
// Finding the split boundaries.
// ---------------------------------------------------------------------------

// Called every time a new point is kept. It looks at the stretch between the previous point
// and this one, and ends a split for each boundary that stretch crossed.
//
// The boundary almost never lands exactly on a point. A kilometer might be reached
// partway between two points, so the moment it was crossed is worked out by assuming
// the user moved at a steady speed across the stretch. That's called interpolation.
//
// Normally a stretch crosses at most one boundary. A long gap in the GPS signal could
// cross several, so this repeats until there are no more.
//
// previousMeters and previousElapsedMillis describe the previous point, and newMeters
// and newElapsedMillis describe the new one. The times are activity time (pauses
// left out), the same as the on-screen timer.
fun advanceSplits(
    progress: SplitProgress,
    settings: SplitSettings,
    previousMeters: Float,
    previousElapsedMillis: Long,
    newMeters: Float,
    newElapsedMillis: Long
): SplitProgress {
    var splits = progress.splits
    var startMeters = progress.startMeters
    var startElapsed = progress.startElapsedMillis

    when (settings.mode) {
        SplitMode.DISTANCE -> {
            // The distance at which the split in progress will end.
            var boundary = startMeters + SPLIT_DISTANCE_METERS

            while (newMeters >= boundary) {
                // How far along the stretch the boundary is: 0 means at the previous point,
                // 1 means at the new point.
                val stretch = newMeters - previousMeters
                val fraction = if (stretch > 0f) {
                    ((boundary - previousMeters) / stretch).coerceIn(0f, 1f)
                } else {
                    1f
                }

                // The time of the crossing: that same fraction of the way between the two times.
                val crossingElapsed = previousElapsedMillis +
                    ((newElapsedMillis - previousElapsedMillis) * fraction).toLong()

                splits = splits + Split(
                    number = splits.size + 1,
                    distanceMeters = SPLIT_DISTANCE_METERS,
                    durationMillis = crossingElapsed - startElapsed
                )

                // The next split starts exactly where this one ended.
                startMeters = boundary
                startElapsed = crossingElapsed
                boundary += SPLIT_DISTANCE_METERS
            }
        }

        SplitMode.TIME -> {
            val intervalMillis = settings.minutes * 60_000L
            // The activity time at which the split in progress will end.
            var boundaryElapsed = startElapsed + intervalMillis

            while (newElapsedMillis >= boundaryElapsed) {
                // How far along the stretch the boundary is, measured in time this time.
                val stretchMillis = newElapsedMillis - previousElapsedMillis
                val fraction = if (stretchMillis > 0L) {
                    ((boundaryElapsed - previousElapsedMillis).toFloat() / stretchMillis)
                        .coerceIn(0f, 1f)
                } else {
                    1f
                }

                // The distance at the boundary: that same fraction of the way between the two distances.
                val boundaryMeters = previousMeters + (newMeters - previousMeters) * fraction

                splits = splits + Split(
                    number = splits.size + 1,
                    distanceMeters = boundaryMeters - startMeters,
                    durationMillis = intervalMillis
                )

                startMeters = boundaryMeters
                startElapsed = boundaryElapsed
                boundaryElapsed += intervalMillis
            }
        }
    }

    return SplitProgress(splits, startMeters, startElapsed)
}
