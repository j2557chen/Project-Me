package com.example.myfitnessapp

import java.time.DayOfWeek
import java.time.LocalDate

// The number of weeks in a row, up to now, with at least one workout. A week with no workout
// yet this week doesn't break the streak until it's over, so the count then runs from last week.
fun weekStreak(workoutDates: Collection<LocalDate>, today: LocalDate, firstDayOfWeek: DayOfWeek): Int {
    val weeksWithWorkouts = workoutDates.map { weekStartOf(it, firstDayOfWeek) }.toSet()
    var week = weekStartOf(today, firstDayOfWeek)
    if (week !in weeksWithWorkouts) week = week.minusWeeks(1)

    var streak = 0
    while (week in weeksWithWorkouts) {
        streak++
        week = week.minusWeeks(1)
    }
    return streak
}
