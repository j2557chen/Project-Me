package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

// Exports a workout to CSV, reads it back with the Hevy importer, and checks it all survives.
class CsvExportTest {
    private val start = LocalDateTime.of(2026, 11, 9, 17, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val workout = Workout(id = 1, startTime = start, endTime = start + 3_600_000, name = "Push, \"heavy\" day")

    private val exercises = listOf(
        Exercise(id = 1, workoutId = 1, name = "Bench Press (Barbell)", type = ActivityType.STRENGTH, position = 0,
            notes = "Paused, felt good", supersetGroup = 5),
        Exercise(id = 2, workoutId = 1, name = "Pull Up", type = ActivityType.STRENGTH, position = 1,
            effortType = EffortType.RIR, supersetGroup = 5),
        Exercise(id = 3, workoutId = 1, name = "Running", type = ActivityType.RUN, position = 2,
            distance = 5.25, durationSeconds = 1_800)
    )
    private val sets = listOf(
        ExerciseSet(exerciseId = 1, setNumber = 1, weight = WeightUnit.LBS.toKg(135.0), reps = 10.0, setType = SetType.WARM_UP),
        ExerciseSet(exerciseId = 1, setNumber = 2, weight = WeightUnit.LBS.toKg(225.0), reps = 5.5, effort = 8.5),
        ExerciseSet(exerciseId = 2, setNumber = 1, weight = null, reps = 12.0, effort = 2.0, setType = SetType.FAILURE)
    )

    private val back = parseHevyExport(workoutsToCsv(listOf(workout), exercises, sets, ZoneOffset.UTC), ZoneOffset.UTC).single()

    @Test
    fun theWorkoutComesBack() {
        assertEquals("Push, \"heavy\" day", back.name)
        assertEquals(workout.startTime, back.startTime)
        assertEquals(workout.endTime, back.endTime)
        assertEquals(listOf("Bench Press (Barbell)", "Pull Up", "Running"), back.exercises.map { it.name })
    }

    @Test
    fun setsComeBackWithWeightsRepsRpeAndTypes() {
        val bench = back.exercises[0]
        assertEquals("Paused, felt good", bench.notes)
        assertEquals(listOf(135.0, 225.0), bench.sets.map { WeightUnit.LBS.fromKg(it.weightKg!!) })
        assertEquals(listOf(10.0, 5.5), bench.sets.map { it.reps })
        assertEquals(listOf(SetType.WARM_UP, SetType.NORMAL), bench.sets.map { it.setType })
        assertEquals(8.5, bench.sets[1].effort!!, 0.0)

        val pullUp = back.exercises[1].sets.single()
        assertNull(pullUp.weightKg)
        assertEquals(SetType.FAILURE, pullUp.setType)
        // RIR 2 is written as RPE 8.
        assertEquals(8.0, pullUp.effort!!, 0.0)
    }

    @Test
    fun supersetsAndActivitiesComeBack() {
        assertEquals(back.exercises[0].supersetId, back.exercises[1].supersetId)
        assertNull(back.exercises[2].supersetId)
        assertEquals(5.25, back.exercises[2].distanceKm!!, 1e-9)
        assertEquals(1_800L, back.exercises[2].durationSeconds)
    }

    @Test
    fun workoutsStillInProgressAreLeftOut() {
        val csv = workoutsToCsv(listOf(workout.copy(endTime = null)), exercises, sets, ZoneOffset.UTC)
        assertEquals(1, csv.trim().lines().size)
    }
}
