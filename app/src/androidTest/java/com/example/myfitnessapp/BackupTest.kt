package com.example.myfitnessapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

// Backs up a database of its own (never the app's), changes it, restores the backup, and checks
// everything came back.
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "backup-test.db"

    private fun open() = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
        .addMigrations(*WorkoutDatabase.ALL_MIGRATIONS)
        .build()

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun aRestoredBackupBringsEverythingBack() = runBlocking {
        context.deleteDatabase(dbName)
        var db = open()
        val dao = db.workoutDao()
        val workoutId = dao.insertWorkout(Workout(startTime = 1_000, endTime = 2_000, name = "Push"))
        val exerciseId = dao.insertExercise(
            Exercise(workoutId = workoutId, name = "Bench Press (Barbell)", type = ActivityType.STRENGTH, supersetGroup = 7)
        )
        dao.insertSets(listOf(
            ExerciseSet(exerciseId = exerciseId, setNumber = 1, weight = 100.0, reps = 8.5, effort = 8.0, done = true, setType = SetType.WARM_UP)
        ))
        val programId = dao.insertProgram(Program(name = "Push Day"))
        dao.insertBodyWeight(BodyWeight(timeMillis = 5_000, weightKg = 80.0))

        val bytes = ByteArrayOutputStream().also { writeBackup(context, it, db) }.toByteArray()

        // Things change after the backup: a workout and the program are deleted.
        dao.deleteWorkout(workoutId)
        dao.deleteProgram(programId)
        db.close()

        val backup = unpackBackup(context, bytes.inputStream())
        assertEquals(1, backup.workoutCount)
        assertTrue(backup.createdAt != null)
        restoreBackupFiles(context, backup, context.getDatabasePath(dbName))

        db = open()
        val restored = db.workoutDao()
        val sets = restored.getSetsForWorkout(workoutId)
        assertEquals(1, sets.size)
        assertEquals(8.5, sets[0].reps!!, 0.0)
        assertEquals(SetType.WARM_UP, sets[0].setType)
        assertEquals(7L, restored.getExercisesForWorkout(workoutId).single().supersetGroup)
        assertEquals("Push Day", restored.getProgram(programId)?.name)
        assertEquals(1, restored.countBodyWeights())
        db.close()
    }

    @Test(expected = BackupException::class)
    fun aFileThatIsntABackupIsRefused() {
        unpackBackup(context, "not a zip".byteInputStream())
    }

    @Test
    fun backupFileNamesCarryTheirDate() {
        val name = backupFileName(java.time.LocalDate.of(2026, 11, 10))
        assertEquals("ProjectMe-backup-2026-11-10.zip", name)
        assertEquals(java.time.LocalDate.of(2026, 11, 10), backupDateOf(name))
        assertEquals(java.time.LocalDate.of(2026, 11, 10), backupDateOf("MyFitnessApp-backup-2026-11-10 (1).zip"))
        assertEquals(null, backupDateOf("photo.zip"))
        File(context.cacheDir, "restore").deleteRecursively()
    }
}
