package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class WeeklyTotalsTest {
    private val zone = ZoneOffset.UTC
    // A Tuesday. With Sunday as the first day, its week starts Nov 1.
    private val today = LocalDate.of(2026, 11, 3)
    private val minute = 60_000L

    private fun workout(date: LocalDate, minutes: Long?, hour: Int = 12): WorkoutStats {
        val start = date.atTime(LocalTime.of(hour, 0)).toInstant(zone).toEpochMilli()
        return WorkoutStats(startTime = start, endTime = minutes?.let { start + it * minute }, volumeKg = null, distanceKm = null)
    }

    // Totals are in milliseconds, so they compare directly with `minute` multiples.
    private fun series(range: TimeRange, vararg workouts: WorkoutStats) =
        timeSeries(workouts.toList(), range, today, DayOfWeek.SUNDAY, zone) { (it.endTime!! - it.startTime).toDouble() }

    private val Bucket.totalMillis get() = total.toLong()
    private fun TimeSeries.total() = buckets.sumOf { it.totalMillis }

    // --- Last 3 months: weekly points ---

    @Test
    fun threeMonthsCoversWeeksThroughThisWeek() {
        val s = series(TimeRange.LAST_3_MONTHS)
        // Aug 3, 2026 is a Monday, so the first week starts Sunday Aug 2.
        assertEquals(LocalDate.of(2026, 8, 2), s.buckets.first().start)
        assertEquals(LocalDate.of(2026, 11, 1), s.buckets.last().start)
        assertEquals(14, s.buckets.size)
        assertEquals(Period.MONTH, s.dividerPeriod)
        assertEquals(LocalDate.of(2026, 8, 1), s.dividers.first())
        assertEquals(0L, s.total())
    }

    @Test
    fun sumsWorkoutsInTheSameWeek() {
        val s = series(TimeRange.LAST_3_MONTHS, workout(today, 30), workout(today.minusDays(2), 45))
        assertEquals(75 * minute, s.buckets.last().totalMillis)
    }

    @Test
    fun separatesWeeks() {
        val b = series(TimeRange.LAST_3_MONTHS, workout(today, 30), workout(today.minusWeeks(1), 20)).buckets
        assertEquals(30 * minute, b[b.lastIndex].totalMillis)
        assertEquals(20 * minute, b[b.lastIndex - 1].totalMillis)
    }

    @Test
    fun skipsUnfinishedWorkouts() {
        assertEquals(0L, series(TimeRange.LAST_3_MONTHS, workout(today, null)).total())
    }

    @Test
    fun ignoresWorkoutsBeforeTheRange() {
        assertEquals(0L, series(TimeRange.LAST_3_MONTHS, workout(LocalDate.of(2026, 7, 1), 60)).total())
    }

    @Test
    fun countsAWorkoutInTheWeekItStarted() {
        // Starts late Saturday Oct 31 and runs past midnight into the next week.
        val b = series(TimeRange.LAST_3_MONTHS, workout(LocalDate.of(2026, 10, 31), 120, hour = 23)).buckets
        assertEquals(0L, b.last().totalMillis)
        assertEquals(120 * minute, b[b.lastIndex - 1].totalMillis)
    }

    @Test
    fun bestOfCombinesSessionsWithMax() {
        val workouts = listOf(workout(today, 30), workout(today.minusDays(1), 45))
        val s = timeSeries(workouts, TimeRange.LAST_3_MONTHS, today, DayOfWeek.SUNDAY, zone, combine = ::maxOf) {
            (it.endTime!! - it.startTime).toDouble()
        }
        assertEquals(45 * minute, s.buckets.last().totalMillis)
    }

    // --- Last month: daily points, divided by week ---

    @Test
    fun lastMonthHasAPointForEveryDay() {
        val s = series(TimeRange.LAST_MONTH, workout(today, 30), workout(today.minusDays(1), 10))
        assertEquals(LocalDate.of(2026, 10, 3), s.start)
        assertEquals(today.plusDays(1), s.end)
        assertEquals(32, s.buckets.size)
        assertEquals(30 * minute, s.buckets.last().totalMillis)
        assertEquals(10 * minute, s.buckets[s.buckets.lastIndex - 1].totalMillis)
        assertEquals(Period.WEEK, s.dividerPeriod)
        // The week holding Oct 3 started Sunday Sep 27.
        assertEquals(LocalDate.of(2026, 9, 27), s.dividers.first())
        assertEquals(LocalDate.of(2026, 11, 1), s.dividers.last())
    }

    // --- Last year: monthly totals ---

    @Test
    fun lastYearHasTwelveMonthlyTotals() {
        val s = series(TimeRange.LAST_YEAR, workout(today, 30), workout(LocalDate.of(2026, 10, 15), 40),
            workout(LocalDate.of(2026, 10, 2), 5))
        assertEquals(12, s.buckets.size)
        assertEquals(LocalDate.of(2025, 12, 1), s.buckets.first().start)
        assertEquals(LocalDate.of(2026, 11, 1), s.buckets.last().start)
        assertEquals(30 * minute, s.buckets.last().totalMillis)
        assertEquals(45 * minute, s.buckets[s.buckets.lastIndex - 1].totalMillis)
    }

    // --- All time: grouping follows the span ---

    @Test
    fun allTimeShowsAtLeastTheLastMonth() {
        val s = series(TimeRange.ALL_TIME, workout(today, 30))
        assertEquals(Period.DAY, s.bucketPeriod)
        assertEquals(today.minusMonths(1), s.start)
        assertEquals(30 * minute, s.total())
    }

    @Test
    fun allTimeWithNoWorkoutsShowsTheLastMonth() {
        val s = series(TimeRange.ALL_TIME)
        assertEquals(Period.DAY, s.bucketPeriod)
        assertEquals(today.minusMonths(1), s.start)
    }

    @Test
    fun allTimeUsesWeeksForAFewMonths() {
        val s = series(TimeRange.ALL_TIME, workout(today.minusMonths(4), 60), workout(today, 30))
        assertEquals(Period.WEEK, s.bucketPeriod)
        assertEquals(Period.MONTH, s.dividerPeriod)
        assertEquals(90 * minute, s.total())
    }

    @Test
    fun allTimeUsesMonthsForAboutAYear() {
        val s = series(TimeRange.ALL_TIME, workout(today.minusMonths(14), 60))
        assertEquals(Period.MONTH, s.bucketPeriod)
        assertEquals(Period.MONTH, s.dividerPeriod)
        assertEquals(60 * minute, s.total())
    }

    @Test
    fun allTimeUsesYearDividersForTwoYears() {
        val s = series(TimeRange.ALL_TIME, workout(today.minusYears(2), 60))
        assertEquals(Period.MONTH, s.bucketPeriod)
        assertEquals(Period.YEAR, s.dividerPeriod)
    }

    @Test
    fun allTimeUsesYearsForLongSpans() {
        val s = series(TimeRange.ALL_TIME, workout(LocalDate.of(2020, 5, 1), 60), workout(today, 30))
        assertEquals(Period.YEAR, s.bucketPeriod)
        assertEquals(LocalDate.of(2020, 1, 1), s.start)
        assertEquals(7, s.buckets.size)
        assertEquals(90 * minute, s.total())
    }
}
