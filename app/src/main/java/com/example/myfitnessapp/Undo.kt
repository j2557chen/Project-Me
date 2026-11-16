package com.example.myfitnessapp

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Undo after deleting a set or an exercise: a message at the bottom of the screen with an Undo
// button, a failsafe for a stray swipe or tap.
// ---------------------------------------------------------------------------

// Shows the "deleted" messages for one screen. Each screen with exercise cards makes one, hands
// it to its cards, and shows its messages with a SnackbarHost.
class UndoHost(private val scope: CoroutineScope) {
    val snackbar = SnackbarHostState()

    // Shows message with an Undo button. Tapping it runs undo. Otherwise, once the message goes
    // (it times out, another replaces it, or the screen closes), afterwards runs: the place for
    // anything that can't be taken back, like deleting a picture file.
    fun offer(message: String, undo: suspend () -> Unit, afterwards: suspend () -> Unit = {}) {
        scope.launch {
            // One message at a time: a newer deletion replaces the older one's message.
            snackbar.currentSnackbarData?.dismiss()
            var undone = false
            try {
                undone = snackbar.showSnackbar(
                    message = message,
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long
                ) == SnackbarResult.ActionPerformed
                // The thing deleted might be gone for good by now (its workout deleted, say), so
                // a failed undo does nothing.
                if (undone) runCatching { undo() }
            } finally {
                // NonCancellable lets this finish even when the screen is closing.
                if (!undone) withContext(NonCancellable) { afterwards() }
            }
        }
    }

    // Closes any message without undoing, used when the workout is saved.
    fun dismiss() {
        snackbar.currentSnackbarData?.dismiss()
    }
}

@Composable
fun rememberUndoHost(): UndoHost {
    val scope = rememberCoroutineScope()
    return remember { UndoHost(scope) }
}

// Deletes a set and renumbers the sets after it. Returns how to put it back: back in its place,
// with its number, and the sets after it moved up again.
suspend fun deleteSetUndoably(db: WorkoutDatabase, removed: ExerciseSet): suspend () -> Unit {
    val dao = db.workoutDao()
    // Both steps succeed or fail together, so the numbering can never be left with a gap.
    db.withTransaction {
        dao.deleteSet(removed.id)
        dao.shiftSetNumbersDown(removed.exerciseId, removed.setNumber)
    }
    return {
        db.withTransaction {
            dao.shiftSetNumbersUp(removed.exerciseId, removed.setNumber)
            dao.insertSet(removed)
        }
    }
}

// What deleteExerciseUndoably hands back: how to put the exercise back, and what to do once it
// can no longer come back.
class DeletedExercise(val undo: suspend () -> Unit, val afterwards: suspend () -> Unit)

// Deletes an exercise with its sets, keeping a copy of everything that goes with it: its sets, a
// recording's route and fastest times, and the workout's supersets as they were. Its route
// picture file is only deleted afterwards, since a file can't come back.
suspend fun deleteExerciseUndoably(db: WorkoutDatabase, exerciseId: Long): DeletedExercise? {
    val dao = db.workoutDao()
    val exercise = dao.getExercise(exerciseId) ?: return null
    val sets = dao.getSetsForExercise(exerciseId)
    val trackPoints = dao.getTrackPoints(exerciseId)
    val bestEfforts = dao.getBestEfforts(exerciseId)
    val workoutBefore = dao.getExercisesForWorkout(exercise.workoutId)

    // Deleting the exercise deletes its sets, route, and fastest times too (the foreign keys cascade).
    dao.deleteExercise(exerciseId)
    // A superset left with one exercise is no superset.
    tidyWorkoutSupersets(db, exercise.workoutId)

    return DeletedExercise(
        undo = {
            db.withTransaction {
                // Put back with the same id and position, so it returns to the same place.
                dao.insertExercise(exercise)
                if (sets.isNotEmpty()) dao.insertSets(sets)
                if (trackPoints.isNotEmpty()) dao.insertTrackPoints(trackPoints)
                if (bestEfforts.isNotEmpty()) dao.insertBestEfforts(bestEfforts)
                // Re-link any superset that deleting it broke up.
                workoutBefore.forEach { dao.setSupersetGroup(it.id, it.supersetGroup) }
            }
        },
        afterwards = { exercise.routeImagePath?.let { deleteImageFiles(listOf(it)) } }
    )
}
