package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetChecksTest {
    @Test
    fun bodyweightExercisesCanSkipTheWeight() {
        for (name in listOf("Pull Up", "Push Up", "Chin Up", "Chest Dip", "Plank", "Hanging Leg Raise",
            "Squat (Bodyweight)", "Walking Lunge", "Nordic Hamstrings Curls", "Muscle Up")) {
            assertTrue(name, allowsBodyweight(name))
        }
    }

    @Test
    fun loadedExercisesNeedAWeight() {
        for (name in listOf("Bench Press (Barbell)", "Pull Up (Weighted)", "Push Up (Weighted)",
            "Cable Crunch", "Lat Pulldown (Cable)", "Pull Up (Assisted)", "Squat (Barbell)",
            "Curtsy Lunge (Dumbbell)", "Decline Crunch (Weighted)", "My Custom Lift")) {
            assertFalse(name, allowsBodyweight(name))
        }
    }

    private fun exercise(id: Long, name: String, type: ActivityType = ActivityType.STRENGTH) =
        Exercise(id = id, workoutId = 1, name = name, type = type)

    @Test
    fun findsTheExercisesWithUnfinishedSets() {
        val exercises = listOf(
            exercise(1, "Bench Press (Barbell)"),
            exercise(2, "Pull Up"),
            exercise(3, "Squat (Barbell)"),
            exercise(4, "Running", ActivityType.RUN)
        )
        val sets = listOf(
            ExerciseSet(exerciseId = 1, setNumber = 1, weight = 60.0, reps = 5.0, done = true),
            ExerciseSet(exerciseId = 1, setNumber = 2, weight = null, reps = 5.0, done = true),   // no weight
            ExerciseSet(exerciseId = 2, setNumber = 1, weight = null, reps = 10.0, done = true),  // fine: bodyweight
            ExerciseSet(exerciseId = 3, setNumber = 1, weight = 100.0, reps = null, done = true) // no reps
        )
        assertEquals(listOf("Bench Press (Barbell)", "Squat (Barbell)"), exercisesWithIncompleteSets(exercises, sets))
    }

    @Test
    fun anUncheckedSetBlocksSavingEvenWhenFilledIn() {
        val exercises = listOf(exercise(1, "Bench Press (Barbell)"))
        val sets = listOf(ExerciseSet(exerciseId = 1, setNumber = 1, weight = 60.0, reps = 5.0, done = false))
        assertEquals(listOf("Bench Press (Barbell)"), exercisesWithIncompleteSets(exercises, sets))
        assertTrue(missingFields(exercises[0], sets[0]).notDone)
    }

    @Test
    fun bodyweightSetsStillNeedReps() {
        val pullUp = exercise(2, "Pull Up")
        val missing = missingFields(pullUp, ExerciseSet(exerciseId = 2, setNumber = 1, done = true))
        assertFalse(missing.weight)
        assertTrue(missing.reps)
        // Checked, with reps and no weight: fine.
        assertFalse(missingFields(pullUp, ExerciseSet(exerciseId = 2, setNumber = 1, reps = 8.0, done = true)).any)
    }

    @Test
    fun completeWorkoutsHaveNothingToFix() {
        val exercises = listOf(exercise(1, "Bench Press (Barbell)"))
        val sets = listOf(ExerciseSet(exerciseId = 1, setNumber = 1, weight = 60.0, reps = 5.0, done = true))
        assertEquals(emptyList<String>(), exercisesWithIncompleteSets(exercises, sets))
    }

    @Test
    fun filledInUntickedSetsCanAllBeTicked() {
        val exercises = listOf(exercise(1, "Bench Press (Barbell)"), exercise(2, "Pull Up"))
        val sets = listOf(
            ExerciseSet(id = 10, exerciseId = 1, setNumber = 1, weight = 60.0, reps = 5.0, done = true),
            ExerciseSet(id = 11, exerciseId = 1, setNumber = 2, weight = 60.0, reps = 5.0, done = false),
            ExerciseSet(id = 12, exerciseId = 2, setNumber = 1, reps = 8.0, done = false)  // bodyweight
        )
        val incomplete = incompleteSets(exercises, sets)
        assertEquals(listOf(11L, 12L), incomplete.setIds)
        assertEquals(listOf(1L, 2L), incomplete.byExercise.map { it.first.id })
        assertTrue(incomplete.onlyUnticked)
    }

    @Test
    fun aSetMissingNumbersCantBeTickedOff() {
        val exercises = listOf(exercise(1, "Bench Press (Barbell)"))
        val sets = listOf(
            ExerciseSet(id = 10, exerciseId = 1, setNumber = 1, weight = 60.0, reps = 5.0, done = false),
            ExerciseSet(id = 11, exerciseId = 1, setNumber = 2, reps = 5.0, done = false)  // no weight
        )
        assertFalse(incompleteSets(exercises, sets).onlyUnticked)
    }
}
