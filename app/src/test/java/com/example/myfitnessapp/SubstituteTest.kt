package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubstituteTest {
    private val library = listOf(
        LibraryExercise("Bench Press (Barbell)", "Chest", ActivityType.STRENGTH),
        LibraryExercise("Bench Press (Dumbbell)", "Chest", ActivityType.STRENGTH),
        LibraryExercise("Chest Fly (Cable)", "Chest", ActivityType.STRENGTH),
        LibraryExercise("Push Up", "Chest", ActivityType.STRENGTH),
        LibraryExercise("Squat (Barbell)", "Quads", ActivityType.STRENGTH),
        LibraryExercise("Rowing Machine", "Cardio", ActivityType.CARDIO),
        LibraryExercise("Running", "Cardio", ActivityType.RUN),
        LibraryExercise("Stationary Bike", "Cardio", ActivityType.CARDIO)
    )

    private fun done(name: String, workouts: Int, lastTime: Long) =
        ExerciseSummary(name, ActivityType.STRENGTH, workouts, lastTime)

    @Test
    fun sameGroupOnlyLeavingItselfOut() {
        val subs = substitutesFor("Bench Press (Barbell)", library, emptyList())!!
        assertEquals("Chest", subs.group)
        assertEquals(listOf("Bench Press (Dumbbell)", "Chest Fly (Cable)", "Push Up"), subs.others.map { it.name })
        assertEquals(emptyList<LibraryExercise>(), subs.doneBefore)
    }

    @Test
    fun doneBeforeComeFirstMostOftenFirst() {
        val history = listOf(
            done("Push Up", 3, 100),
            done("Chest Fly (Cable)", 7, 50),
            done("Squat (Barbell)", 20, 200),
            done("Bench Press (Barbell)", 30, 300)
        )
        val subs = substitutesFor("Bench Press (Barbell)", library, history)!!
        assertEquals(listOf("Chest Fly (Cable)", "Push Up"), subs.doneBefore.map { it.name })
        assertEquals(listOf("Bench Press (Dumbbell)"), subs.others.map { it.name })
    }

    @Test
    fun tiesGoToTheMostRecent() {
        val history = listOf(done("Push Up", 2, 100), done("Chest Fly (Cable)", 2, 500))
        val subs = substitutesFor("Bench Press (Barbell)", library, history)!!
        assertEquals(listOf("Chest Fly (Cable)", "Push Up"), subs.doneBefore.map { it.name })
    }

    @Test
    fun cardioOnlySwapsForTheSameKind() {
        // A run tracks pace and distance, so only another run could stand in; there's none here.
        assertEquals(emptyList<LibraryExercise>(), substitutesFor("Running", library, emptyList())!!.others)
        assertEquals(
            listOf("Stationary Bike"),
            substitutesFor("Rowing Machine", library, emptyList())!!.others.map { it.name }
        )
    }

    @Test
    fun nameIsMatchedIgnoringCapitals() {
        assertEquals("Quads", substitutesFor("squat (barbell)", library, emptyList())!!.group)
    }

    @Test
    fun unknownExerciseHasNoSubstitutes() {
        assertNull(substitutesFor("Something Made Up", library, emptyList()))
    }
}
