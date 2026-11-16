package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakTest {
    // A Tuesday. With Sunday as the first day, its week runs Nov 1 to Nov 7.
    private val today = LocalDate.of(2026, 11, 3)

    private fun streak(vararg dates: LocalDate) = weekStreak(dates.toList(), today, DayOfWeek.SUNDAY)

    @Test
    fun noWorkouts() = assertEquals(0, streak())

    @Test
    fun onlyThisWeek() = assertEquals(1, streak(today))

    @Test
    fun severalWorkoutsInOneWeekCountOnce() =
        assertEquals(1, streak(today, today.minusDays(1), today.minusDays(2)))

    @Test
    fun consecutiveWeeks() =
        assertEquals(3, streak(today, today.minusWeeks(1), today.minusWeeks(2)))

    @Test
    fun weekBoundaryFollowsFirstDayOfWeek() {
        // Saturday Oct 31 and Sunday Nov 1 are in different weeks when weeks start on Sunday.
        assertEquals(2, streak(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun noWorkoutYetThisWeekKeepsLastWeeksStreak() =
        assertEquals(2, streak(today.minusWeeks(1), today.minusWeeks(2)))

    @Test
    fun aMissedWeekEndsTheStreak() =
        assertEquals(1, streak(today, today.minusWeeks(2), today.minusWeeks(3)))

    @Test
    fun noWorkoutThisWeekOrLastWeek() = assertEquals(0, streak(today.minusWeeks(2)))
}
