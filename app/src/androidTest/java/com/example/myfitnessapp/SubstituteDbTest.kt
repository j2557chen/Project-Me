package com.example.myfitnessapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Substituting an exercise in a database of its own (never the app's), and undoing it.
@RunWith(AndroidJUnit4::class)
class SubstituteDbTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: WorkoutDatabase
    private val dao get() = db.workoutDao()
    private val dumbbell = LibraryExercise("Bench Press (Dumbbell)", "Chest", ActivityType.STRENGTH)

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    // A running workout: bench press (with the sets given), then squat.
    private suspend fun workout(vararg sets: ExerciseSet): Pair<Exercise, Exercise> {
        val workoutId = dao.insertWorkout(Workout(startTime = 1_000))
        val benchId = dao.insertExercise(
            Exercise(workoutId = workoutId, name = "Bench Press (Barbell)", type = ActivityType.STRENGTH,
                notes = "Grip on the rings", position = 0, supersetGroup = 4)
        )
        val squatId = dao.insertExercise(
            Exercise(workoutId = workoutId, name = "Squat (Barbell)", type = ActivityType.STRENGTH, position = 1)
        )
        dao.insertSets(sets.map { it.copy(exerciseId = benchId) })
        return dao.getExercise(benchId)!! to dao.getExercise(squatId)!!
    }

    @Test
    fun withNothingDoneTheExerciseItselfIsSwapped() = runBlocking {
        val (bench, _) = workout(
            ExerciseSet(exerciseId = 0, setNumber = 1, weight = 60.0, reps = 8.0, effort = 7.0, setType = SetType.WARM_UP),
            ExerciseSet(exerciseId = 0, setNumber = 2, weight = 80.0, reps = 5.0)
        )
        val swap = substituteExercise(db, bench, dumbbell)

        assertEquals(bench.id, swap.newExerciseId)
        val swapped = dao.getExercise(bench.id)!!
        assertEquals("Bench Press (Dumbbell)", swapped.name)
        assertNull(swapped.notes)
        assertEquals(4L, swapped.supersetGroup)
        val sets = dao.getSetsForExercise(bench.id).sortedBy { it.setNumber }
        assertEquals(listOf(SetType.WARM_UP, SetType.NORMAL), sets.map { it.setType })
        assertEquals(listOf(null, null), sets.map { it.weight })
        assertEquals(listOf(null, null), sets.map { it.reps })
        assertEquals(listOf(null, null), sets.map { it.effort })

        swap.undo()
        assertEquals(bench, dao.getExercise(bench.id))
        assertEquals(listOf(60.0, 80.0), dao.getSetsForExercise(bench.id).sortedBy { it.setNumber }.map { it.weight })
    }

    @Test
    fun doneSetsStayAndTheRestMoveToTheSubstituteBelow() = runBlocking {
        val (bench, squat) = workout(
            ExerciseSet(exerciseId = 0, setNumber = 1, weight = 80.0, reps = 5.0, done = true),
            ExerciseSet(exerciseId = 0, setNumber = 2, weight = 80.0, reps = 5.0),
            ExerciseSet(exerciseId = 0, setNumber = 3, setType = SetType.FAILURE)
        )
        val swap = substituteExercise(db, bench, dumbbell)

        val order = dao.getExercisesForWorkout(bench.workoutId)
        assertEquals(listOf(bench.id, swap.newExerciseId, squat.id), order.map { it.id })
        // The original keeps only what was done, as it was.
        val kept = dao.getSetsForExercise(bench.id)
        assertEquals(listOf(1), kept.map { it.setNumber })
        assertEquals(listOf(80.0), kept.map { it.weight })
        // The substitute takes the two still to do, numbered from 1, empty, keeping set types.
        val substitute = order[1]
        assertEquals("Bench Press (Dumbbell)", substitute.name)
        assertEquals(4L, substitute.supersetGroup)
        val moved = dao.getSetsForExercise(substitute.id).sortedBy { it.setNumber }
        assertEquals(listOf(1, 2), moved.map { it.setNumber })
        assertEquals(listOf(SetType.NORMAL, SetType.FAILURE), moved.map { it.setType })
        assertEquals(listOf(null, null), moved.map { it.weight })

        swap.undo()
        assertNull(dao.getExercise(substitute.id))
        assertEquals(listOf(bench.id, squat.id), dao.getExercisesForWorkout(bench.workoutId).map { it.id })
        assertEquals(listOf(1, 2, 3), dao.getSetsForExercise(bench.id).map { it.setNumber }.sorted())
    }

    @Test
    fun withEverythingDoneTheSubstituteStartsWithOneSet() = runBlocking {
        val (bench, _) = workout(ExerciseSet(exerciseId = 0, setNumber = 1, weight = 80.0, reps = 5.0, done = true))
        val swap = substituteExercise(db, bench, dumbbell)
        assertEquals(1, dao.getSetsForExercise(swap.newExerciseId).size)
        assertEquals(1, dao.getSetsForExercise(bench.id).size)
    }
}
