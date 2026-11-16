package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class WorkoutNamesTest {
    private fun at(hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 11, 3, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun timeOfDayBoundaries() {
        assertEquals("Evening Workout", defaultWorkoutName(at(4, 59), ZoneOffset.UTC))
        assertEquals("Morning Workout", defaultWorkoutName(at(5), ZoneOffset.UTC))
        assertEquals("Morning Workout", defaultWorkoutName(at(11, 59), ZoneOffset.UTC))
        assertEquals("Noon Workout", defaultWorkoutName(at(12), ZoneOffset.UTC))
        assertEquals("Afternoon Workout", defaultWorkoutName(at(14), ZoneOffset.UTC))
        assertEquals("Evening Workout", defaultWorkoutName(at(17), ZoneOffset.UTC))
        assertEquals("Evening Workout", defaultWorkoutName(at(23, 30), ZoneOffset.UTC))
    }

    @Test
    fun recordingsAreNamedForTheTimeAndActivity() {
        assertEquals("Afternoon Run", recordingWorkoutName(at(15), "Run", ZoneOffset.UTC))
        assertEquals("Morning Walk", recordingWorkoutName(at(7), RecordActivity.WALK.workoutName, ZoneOffset.UTC))
        assertEquals("Evening Bike Ride", recordingWorkoutName(at(19), RecordActivity.BIKE.workoutName, ZoneOffset.UTC))
    }

    @Test
    fun aGivenNameWinsAndBlankFallsBack() {
        assertEquals("Leg Day", Workout(startTime = 0, name = "Leg Day").displayName())
        assertEquals(defaultWorkoutName(0), Workout(startTime = 0, name = "  ").displayName())
        assertEquals(defaultWorkoutName(0), Workout(startTime = 0).displayName())
    }
}
