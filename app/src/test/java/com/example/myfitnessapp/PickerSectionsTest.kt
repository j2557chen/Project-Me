package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test

class PickerSectionsTest {
    private fun done(name: String, workouts: Int, lastTime: Long) =
        ExerciseSummary(name, ActivityType.STRENGTH, workouts, lastTime)

    private val history = listOf(
        done("Bench Press (Barbell)", 40, 100),
        done("Squat (Barbell)", 30, 90),
        done("Deadlift (Barbell)", 25, 80),
        done("Pull Up", 20, 70),
        done("Lat Pulldown (Cable)", 15, 60),
        done("Push Up", 10, 500),            // done most recently, but rarely
        done("Not In The Library", 99, 999)  // ignored
    )

    private fun names(section: Pair<String?, List<LibraryExercise>>) = section.second.map { it.name }

    @Test
    fun emptySearchShowsRecentThenFrequentThenAll() {
        val sections = ExerciseLibrary.pickerSections("", history)
        assertEquals(listOf("Recent", "Frequent", "All Exercises"), sections.map { it.first })
        assertEquals(
            listOf("Push Up", "Bench Press (Barbell)", "Squat (Barbell)", "Deadlift (Barbell)", "Pull Up"),
            names(sections[0])
        )
        // The most often done, leaving out the ones already under Recent.
        assertEquals(listOf("Lat Pulldown (Cable)"), names(sections[1]))
        assertEquals(ExerciseLibrary.all.size, sections[2].second.size)
    }

    @Test
    fun noHistoryShowsOnlyAll() {
        assertEquals(listOf("All Exercises"), ExerciseLibrary.pickerSections("", emptyList()).map { it.first })
    }

    @Test
    fun searchPutsExercisesDoneMostOftenFirst() {
        val results = names(ExerciseLibrary.pickerSections("press", history).single())
        assertEquals("Bench Press (Barbell)", results.first())
        assertEquals(ExerciseLibrary.search("press").size, results.size)
    }

    @Test
    fun noMatchesGivesNoSections() {
        assertEquals(emptyList<Pair<String?, List<LibraryExercise>>>(), ExerciseLibrary.pickerSections("zzqx", history))
    }
}
