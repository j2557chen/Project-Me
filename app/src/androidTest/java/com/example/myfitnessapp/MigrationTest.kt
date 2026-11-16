package com.example.myfitnessapp

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Checks that each database upgrade keeps the saved data and leaves the tables exactly as Room
// expects. The descriptions of each version are in app/schemas. Versions before 16 aren't covered:
// every install is already past them.
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WorkoutDatabase::class.java)

    // A version 16 database with one finished workout (a strength exercise with two sets and a
    // recorded run) and one program.
    private fun createVersion16() {
        helper.createDatabase(dbName, 16).apply {
            execSQL("INSERT INTO workouts (id, startTime, endTime, name) VALUES (1, 1000, 2000, 'Push')")
            execSQL(
                "INSERT INTO exercises (id, workoutId, name, type, isRecorded, position, effortType) " +
                    "VALUES (1, 1, 'Bench Press (Barbell)', 'STRENGTH', 0, 0, 'RPE')"
            )
            execSQL(
                "INSERT INTO exercises (id, workoutId, name, type, distance, durationSeconds, isRecorded, position, effortType) " +
                    "VALUES (2, 1, 'Run', 'RUN', 5.0, 1800, 1, 1, 'RPE')"
            )
            execSQL(
                "INSERT INTO exercise_sets (id, exerciseId, setNumber, weight, reps, effort, done) " +
                    "VALUES (1, 1, 1, 100.0, 8, 8.5, 1), (2, 1, 2, 100.0, 6, 9.0, 1)"
            )
            execSQL("INSERT INTO programs (id, name) VALUES (1, 'Push Day')")
            execSQL(
                "INSERT INTO program_exercises (id, programId, name, type, position, effortType) " +
                    "VALUES (1, 1, 'Bench Press (Barbell)', 'STRENGTH', 0, 'RIR')"
            )
            execSQL("INSERT INTO program_sets (id, programExerciseId, setNumber, reps, effort) VALUES (1, 1, 1, 10, 2.0)")
            close()
        }
    }

    @Test
    fun version16To17KeepsSetsAndStoresRepsAsDecimals() {
        createVersion16()
        val db = helper.runMigrationsAndValidate(dbName, 17, true, WorkoutDatabase.MIGRATION_16_17)

        db.query("SELECT id, weight, reps, typeof(reps), effort, done FROM exercise_sets ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(100.0, c.getDouble(1), 0.0)
            assertEquals(8.0, c.getDouble(2), 0.0)
            assertEquals("real", c.getString(3))
            assertEquals(8.5, c.getDouble(4), 0.0)
            assertEquals(1, c.getInt(5))
        }
        db.query("SELECT reps, typeof(reps), effort FROM program_sets").use { c ->
            c.moveToFirst()
            assertEquals(10.0, c.getDouble(0), 0.0)
            assertEquals("real", c.getString(1))
            assertEquals(2.0, c.getDouble(2), 0.0)
        }
        // A half rep can now be saved.
        db.execSQL("UPDATE exercise_sets SET reps = 8.5 WHERE id = 1")
        db.query("SELECT reps FROM exercise_sets WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(8.5, c.getDouble(0), 0.0)
        }
    }

    @Test
    fun version17To18AddsFastestTimesStillToBeWorkedOut() {
        createVersion16()
        helper.runMigrationsAndValidate(dbName, 17, true, WorkoutDatabase.MIGRATION_16_17).close()
        val db = helper.runMigrationsAndValidate(dbName, 18, true, WorkoutDatabase.MIGRATION_17_18)

        db.query("SELECT id, bestEffortsSaved FROM exercises ORDER BY id").use { c ->
            assertEquals(2, c.count)
            while (c.moveToNext()) assertEquals(0, c.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM best_efforts").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun version18To19MarksEverySetNormal() {
        createVersion16()
        helper.runMigrationsAndValidate(dbName, 18, true, WorkoutDatabase.MIGRATION_16_17, WorkoutDatabase.MIGRATION_17_18).close()
        val db = helper.runMigrationsAndValidate(dbName, 19, true, WorkoutDatabase.MIGRATION_18_19)

        db.query("SELECT setType FROM exercise_sets UNION ALL SELECT setType FROM program_sets").use { c ->
            assertEquals(3, c.count)
            while (c.moveToNext()) assertEquals("NORMAL", c.getString(0))
        }
    }

    @Test
    fun version19To20AddsAnEmptyBodyWeightLog() {
        createVersion16()
        helper.runMigrationsAndValidate(
            dbName, 19, true,
            WorkoutDatabase.MIGRATION_16_17, WorkoutDatabase.MIGRATION_17_18, WorkoutDatabase.MIGRATION_18_19
        ).close()
        val db = helper.runMigrationsAndValidate(dbName, 20, true, WorkoutDatabase.MIGRATION_19_20)

        db.query("SELECT COUNT(*) FROM body_weights").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM workouts").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun version20To21LeavesEveryExerciseOnItsOwn() {
        createVersion16()
        helper.runMigrationsAndValidate(
            dbName, 20, true,
            WorkoutDatabase.MIGRATION_16_17, WorkoutDatabase.MIGRATION_17_18,
            WorkoutDatabase.MIGRATION_18_19, WorkoutDatabase.MIGRATION_19_20
        ).close()
        val db = helper.runMigrationsAndValidate(dbName, 21, true, WorkoutDatabase.MIGRATION_20_21)

        db.query("SELECT supersetGroup FROM exercises UNION ALL SELECT supersetGroup FROM program_exercises").use { c ->
            assertEquals(3, c.count)
            while (c.moveToNext()) assertEquals(true, c.isNull(0))
        }
    }

    // Every upgrade in a row, then the app's own database opens the result and reads it back.
    @Test
    fun version16ToNewestOpensInTheApp() {
        createVersion16()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
            .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
            .build()
        helper.closeWhenFinished(db)

        runBlocking {
            val dao = db.workoutDao()
            val sets = dao.getSetsForWorkout(1).sortedBy { it.setNumber }
            assertEquals(listOf(8.0, 6.0), sets.map { it.reps })
            assertEquals(listOf(true, true), sets.map { it.done })
            assertEquals(2, dao.getProgramExercises(1).size + dao.getSetsForProgram(1).size)
            assertEquals(listOf(2L), dao.getRecordingsWithoutBestEfforts())
            assertFalse(dao.areBestEffortsSaved(2) ?: true)
        }
    }
}
