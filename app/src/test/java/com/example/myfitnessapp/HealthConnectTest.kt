package com.example.myfitnessapp

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthConnectTest {
    private val start = 1_700_000_000_000L

    private fun session(type: Int, title: String? = null, minutes: Int = 30, km: Double? = 5.0) =
        WatchSession(type, title, start, start + minutes * 60_000L, km)

    @Test
    fun runsBecomeTheLibrarysRunning() {
        val workout = watchSessionToWorkout(session(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))!!
        val run = workout.exercises.single()
        assertEquals("Running", run.name)
        assertEquals(ActivityType.RUN, run.type)
        assertEquals(5.0, run.distanceKm!!, 1e-9)
        assertEquals(1800L, run.durationSeconds)
        assertEquals(start, workout.startTime)
        assertEquals(start + 30 * 60_000L, workout.endTime)
    }

    @Test
    fun otherKindsUseTheirLibraryNames() {
        val names = listOf(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to "Treadmill",
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "Walking",
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "Cycling",
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to "Swimming",
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE to "Stair Master"
        )
        for ((type, name) in names) {
            val exercise = watchSessionToWorkout(session(type))!!.exercises.single()
            assertEquals(name, exercise.name)
            // Every name is one the library has, so it gets the library's type.
            assertEquals(libraryExerciseFor(name)!!.type, exercise.type)
        }
    }

    @Test
    fun titlesAreKeptAndBlankOnesDropped() {
        assertEquals("Morning run", watchSessionToWorkout(session(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, " Morning run "))!!.name)
        assertNull(watchSessionToWorkout(session(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, "  "))!!.name)
    }

    @Test
    fun strengthSessionsAreNotImported() {
        assertNull(watchSessionToWorkout(session(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING)))
        assertNull(watchSessionToWorkout(session(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING)))
    }

    @Test
    fun noDistanceStaysEmpty() {
        assertNull(watchSessionToWorkout(session(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, km = 0.0))!!.exercises.single().distanceKm)
    }

    @Test
    fun heartRateSeriesBreaksAtGaps() {
        val samples = listOf(
            HeartRateSample(start, 100),
            HeartRateSample(start + 60_000, 120),
            // Ten minutes with nothing: the watch was off.
            HeartRateSample(start + 11 * 60_000, 140)
        )
        val series = heartRateSeries(samples, start)
        assertEquals(listOf(0f, 60f, 60f, 660f), series.map { it.x })
        assertEquals(listOf(100f, 120f, null, 140f), series.map { it.y })
    }

    @Test
    fun heartRateSummaryAveragesAndFindsTheMax() {
        val summary = heartRateSummary(listOf(HeartRateSample(0, 100), HeartRateSample(1, 121), HeartRateSample(2, 170)))!!
        assertEquals(130, summary.averageBpm)
        assertEquals(170, summary.maxBpm)
    }
}
