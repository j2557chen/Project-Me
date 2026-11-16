package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveAsProgramTest {
    @Test
    fun keepsExerciseOrderRepsAndEffort() {
        val exercises = listOf(
            Exercise(id = 2, workoutId = 1, name = "Squat", type = ActivityType.STRENGTH, position = 1, effortType = EffortType.RIR),
            Exercise(id = 1, workoutId = 1, name = "Bench", type = ActivityType.STRENGTH, position = 0),
            Exercise(id = 3, workoutId = 1, name = "Run", type = ActivityType.RUN, position = 2, distance = 5.0)
        )
        val sets = listOf(
            ExerciseSet(exerciseId = 1, setNumber = 2, weight = 100.0, reps = 6.5, effort = 9.0),
            ExerciseSet(exerciseId = 1, setNumber = 1, weight = 100.0, reps = 8.0, effort = 8.5),
            ExerciseSet(exerciseId = 2, setNumber = 1, weight = 140.0, reps = 5.0, effort = 2.0)
        )
        val draft = workoutAsProgramDraft("Push", exercises, sets)

        assertEquals("Push", draft.name)
        assertEquals(listOf("Bench", "Squat", "Run"), draft.exercises.map { it.name })
        assertEquals(listOf("8" to "8.5", "6.5" to "9"), draft.exercises[0].sets.map { it.repsText to it.effortText })
        assertEquals(EffortType.RIR, draft.exercises[1].effortType)
        assertEquals(listOf("5" to "2"), draft.exercises[1].sets.map { it.repsText to it.effortText })
        assertEquals(emptyList<ProgramSetDraft>(), draft.exercises[2].sets)
    }
}
