package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleMapTest {
    @Test
    fun everyStrengthExerciseHasOneMainMuscle() {
        ExerciseLibrary.all.filter { it.type == ActivityType.STRENGTH }.forEach { exercise ->
            val muscles = MuscleMap.musclesFor(exercise.name)
            assertEquals("${exercise.name} main muscles", 1, muscles.values.count { it == 1.0 })
        }
    }

    @Test
    fun everyExplicitNameIsInTheLibrary() {
        val names = ExerciseLibrary.all.map { it.name }.toSet()
        MuscleMap.explicit.keys.forEach { assertTrue("$it isn't in the library", it in names) }
    }

    @Test
    fun benchPressAlsoWorksTricepsAndShoulders() = assertEquals(
        mapOf(Muscle.CHEST to 1.0, Muscle.TRICEPS to 0.5, Muscle.SHOULDERS to 0.5),
        MuscleMap.musclesFor("Bench Press (Barbell)")
    )

    @Test
    fun backExtensionIsLowerBack() = assertEquals(1.0, MuscleMap.musclesFor("Back Extension (Weighted Hyperextension)")[Muscle.LOWER_BACK])

    @Test
    fun groupIsTheFallback() = assertEquals(mapOf(Muscle.CALVES to 1.0), MuscleMap.musclesFor("Seated Calf Raise"))

    @Test
    fun cardioAndUnknownNamesWorkNothing() {
        assertEquals(emptyMap<Muscle, Double>(), MuscleMap.musclesFor("Running"))
        assertEquals(emptyMap<Muscle, Double>(), MuscleMap.musclesFor("Not An Exercise"))
    }

    @Test
    fun setsAddUpAcrossExercises() {
        val totals = muscleSets(
            listOf(ExerciseSetCount("Bench Press (Barbell)", 3), ExerciseSetCount("Triceps Pushdown", 2))
        )
        assertEquals(3.0, totals[Muscle.CHEST])
        assertEquals(1.5 + 2.0, totals[Muscle.TRICEPS])
        assertEquals(1.5, totals[Muscle.SHOULDERS])
        assertEquals(null, totals[Muscle.QUADS])
    }
}
