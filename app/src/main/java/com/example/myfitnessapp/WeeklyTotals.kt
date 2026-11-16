package com.example.myfitnessapp

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

fun weekStartOf(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

// A span of calendar time that chart points and divider lines are counted in.
enum class Period {
    DAY, WEEK, MONTH, YEAR;

    // The start of the period that holds this date.
    fun startOf(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate = when (this) {
        DAY -> date
        WEEK -> weekStartOf(date, firstDayOfWeek)
        MONTH -> date.withDayOfMonth(1)
        YEAR -> date.withDayOfYear(1)
    }

    fun next(start: LocalDate): LocalDate = when (this) {
        DAY -> start.plusDays(1)
        WEEK -> start.plusWeeks(1)
        MONTH -> start.plusMonths(1)
        YEAR -> start.plusYears(1)
    }
}

// The choices in the Total Time chart's menu.
enum class TimeRange(val label: String) {
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    LAST_YEAR("Last Year"),
    ALL_TIME("All Time")
}

// One point on the chart: the total of the chart's measure over [start, end).
data class Bucket(val start: LocalDate, val end: LocalDate, val total: Double)

// Everything the chart draws. Points come one per `bucketPeriod`, and a divider line marks the
// start of each `dividerPeriod`. The chart covers [start, end).
data class TimeSeries(
    val start: LocalDate,
    val end: LocalDate,
    val bucketPeriod: Period,
    val dividerPeriod: Period,
    val buckets: List<Bucket>,
    // The start of every divider period that touches the chart, the first one possibly
    // before `start` so its label still has a place.
    val dividers: List<LocalDate>
)

// Builds the chart's points for a range, adding up `value` for each workout. Workouts count in
// the period they started in, and unfinished ones don't count.
fun timeSeries(
    workouts: List<WorkoutStats>,
    range: TimeRange,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    zone: ZoneId,
    // How two amounts in the same period combine: added together by default.
    combine: (Double, Double) -> Double = Double::plus,
    value: (WorkoutStats) -> Double
): TimeSeries {
    val finished = workouts.mapNotNull { workout ->
        if (workout.endTime == null) return@mapNotNull null
        Instant.ofEpochMilli(workout.startTime).atZone(zone).toLocalDate() to value(workout)
    }

    val (bucketPeriod, dividerPeriod, earliest) = when (range) {
        TimeRange.LAST_MONTH -> Triple(Period.DAY, Period.WEEK, today.minusMonths(1))
        TimeRange.LAST_3_MONTHS -> Triple(Period.WEEK, Period.MONTH, today.minusMonths(3))
        TimeRange.LAST_YEAR -> Triple(Period.MONTH, Period.MONTH, today.minusMonths(11))
        TimeRange.ALL_TIME -> {
            // At least the last month, or back to the first workout if that's earlier.
            val first = minOf(finished.minOfOrNull { it.first } ?: today, today.minusMonths(1))
            val (bucket, divider) = allTimePeriods(ChronoUnit.DAYS.between(first, today))
            Triple(bucket, divider, first)
        }
    }

    val start = bucketPeriod.startOf(earliest, firstDayOfWeek)
    val end = bucketPeriod.next(bucketPeriod.startOf(today, firstDayOfWeek))

    val totals = mutableMapOf<LocalDate, Double>()
    for ((date, amount) in finished) {
        if (date < start || date >= end) continue
        val bucket = bucketPeriod.startOf(date, firstDayOfWeek)
        totals[bucket] = totals[bucket]?.let { combine(it, amount) } ?: amount
    }

    val buckets = generateSequence(start) { bucketPeriod.next(it) }
        .takeWhile { it < end }
        .map { Bucket(it, bucketPeriod.next(it), totals[it] ?: 0.0) }
        .toList()
    val dividers = generateSequence(dividerPeriod.startOf(start, firstDayOfWeek)) { dividerPeriod.next(it) }
        .takeWhile { it < end }
        .toList()

    return TimeSeries(start, end, bucketPeriod, dividerPeriod, buckets, dividers)
}

// How All Time groups the points, by how many days back the first workout was.
private fun allTimePeriods(days: Long): Pair<Period, Period> = when {
    days <= 45 -> Period.DAY to Period.WEEK
    days <= 200 -> Period.WEEK to Period.MONTH
    days <= 548 -> Period.MONTH to Period.MONTH
    days <= 3 * 365 -> Period.MONTH to Period.YEAR
    else -> Period.YEAR to Period.YEAR
}
