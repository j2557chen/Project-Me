package com.example.myfitnessapp

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomExercisesTest {
    private val sledDrag = CustomExercise(
        id = 7,
        name = "Sled Drag Backwards",
        type = ActivityType.STRENGTH,
        mainMuscle = Muscle.QUADS,
        secondaryMuscles = secondaryMusclesText(listOf(Muscle.GLUTES, Muscle.CALVES)),
        bodyweight = false
    )

    @After
    fun clear() {
        ExerciseLibrary.custom = emptyList()
    }

    @Test
    fun customExercisesJoinTheLibrary() {
        ExerciseLibrary.custom = listOf(sledDrag)
        val entry = ExerciseLibrary.all.single { it.name == "Sled Drag Backwards" }
        assertTrue(entry.custom)
        assertEquals("Quads", entry.group)
        assertEquals(listOf(entry), ExerciseLibrary.search("sled drag back"))
        // Searching by muscle finds it too.
        assertTrue(entry in ExerciseLibrary.search("quads"))
    }

    @Test
    fun theyCountOnTheMuscleMap() {
        ExerciseLibrary.custom = listOf(sledDrag)
        assertEquals(
            mapOf(Muscle.QUADS to 1.0, Muscle.GLUTES to 0.5, Muscle.CALVES to 0.5),
            MuscleMap.musclesFor("Sled Drag Backwards")
        )
    }

    @Test
    fun theBodyweightSwitchDecidesTheSaveCheck() {
        ExerciseLibrary.custom = listOf(sledDrag, sledDrag.copy(id = 8, name = "Towel Row", bodyweight = true))
        assertFalse(allowsBodyweight("Sled Drag Backwards"))
        assertTrue(allowsBodyweight("Towel Row"))
    }

    @Test
    fun namesMustBeNewAndNotBlank() {
        ExerciseLibrary.custom = listOf(sledDrag)
        assertEquals("Enter a name", customNameProblem("   ", null))
        assertEquals("There's already an exercise called Bench Press (Barbell)", customNameProblem("bench press (barbell)", null))
        assertEquals("There's already an exercise called Sled Drag Backwards", customNameProblem("Sled Drag Backwards", null))
        // Editing it can keep its own name.
        assertNull(customNameProblem("Sled Drag Backwards", 7))
        assertNull(customNameProblem("Something New", null))
    }

    @Test
    fun cardioHasNoMuscles() {
        val swim = CustomExercise(name = "Lake Swim", type = ActivityType.CARDIO)
        assertEquals(emptyMap<Muscle, Double>(), swim.muscles())
        assertEquals("Cardio", swim.toLibraryExercise().group)
    }
}
