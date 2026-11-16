package com.example.myfitnessapp

import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

// Gridline steps for time, in minutes.
private val TIME_STEPS_MINUTES = listOf(
    15.0, 30.0, 60.0, 120.0, 180.0, 300.0, 600.0, 1200.0, 1800.0, 3000.0, 6000.0, 12000.0, 30000.0
)

// Turns minutes into "45m", "2h", or "1h 30m".
fun formatMinutes(minutes: Long): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "${rest}m"
        rest == 0L -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

private fun roundTo(value: Double, decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return (value * factor).roundToLong() / factor
}

// The smallest 1, 2, 2.5, or 5 times a power of ten that's at least `minimum`.
fun niceStep(minimum: Double): Double {
    if (minimum <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(minimum)))
    return listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= minimum * (1 - 1e-9) }
}

// What a summary chart shows. Amounts are in minutes, the user's weight unit, or km. `label` is
// the Home chart's name for it, and `shortLabel` an exercise page's.
enum class SummaryMetric(val label: String, val shortLabel: String) {
    TIME("Total Time", "Time"),
    VOLUME("Total Volume", "Volume"),
    DISTANCE("Total Distance", "Distance"),
    // The heaviest weight lifted for reps.
    HEAVIEST_SET("Heaviest Set", "Heaviest Set");

    // Whether a period's value is its sessions added together. Otherwise it's the best of them,
    // and a period with no sessions has no point on the chart rather than a 0.
    val addsUp: Boolean get() = this != HEAVIEST_SET

    fun amount(workout: WorkoutStats, unit: WeightUnit): Double = when (this) {
        TIME -> ((workout.endTime ?: workout.startTime) - workout.startTime).coerceAtLeast(0L) / 60_000.0
        VOLUME -> unit.fromKg(workout.volumeKg ?: 0.0)
        DISTANCE -> workout.distanceKm ?: 0.0
        HEAVIEST_SET -> unit.fromKg(workout.heaviestKg ?: 0.0)
    }

    // The gap between gridlines for a chart whose biggest total is `max`, so it needs 4 or fewer.
    fun gridStep(max: Double): Double = when (this) {
        TIME -> TIME_STEPS_MINUTES.firstOrNull { it * 4 >= max } ?: TIME_STEPS_MINUTES.last()
        VOLUME, DISTANCE, HEAVIEST_SET -> niceStep(max / 4)
    }

    // A total as shown above the chart, such as "1h 30m", "12,500 lbs", or "5.2 km".
    fun format(value: Double, unit: WeightUnit): String = when (this) {
        TIME -> formatMinutes(value.roundToLong())
        VOLUME -> String.format(Locale.getDefault(), "%,d %s", value.roundToLong(), unit.label)
        DISTANCE -> formatNumber(roundTo(value, 1)) + " km"
        HEAVIEST_SET -> formatNumber(roundTo(value, 1)) + " " + unit.label
    }

    // A gridline value beside the chart, kept short: "45m", "12.5k", "2.5".
    fun axisLabel(value: Double): String = when (this) {
        TIME -> if (value == 0.0) "0" else formatMinutes(value.roundToLong())
        VOLUME -> if (value >= 1000) formatNumber(roundTo(value / 1000, 1)) + "k" else formatNumber(roundTo(value, 1))
        DISTANCE, HEAVIEST_SET -> formatNumber(roundTo(value, 2))
    }

    // The unit written after the top gridline value, when the values don't say it themselves.
    fun axisUnit(unit: WeightUnit): String? = when (this) {
        TIME -> null
        VOLUME, HEAVIEST_SET -> unit.label
        DISTANCE -> "km"
    }
}
