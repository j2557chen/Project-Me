package com.example.myfitnessapp

import androidx.room.withTransaction

// ---------------------------------------------------------------------------
// Exercises the user makes themselves: how they join the library, the muscle map and the save
// check, and how they're saved, renamed and deleted.
// ---------------------------------------------------------------------------

// The types a custom exercise can be, in the order they're offered.
val CUSTOM_EXERCISE_TYPES = listOf(
    ActivityType.STRENGTH, ActivityType.CARDIO, ActivityType.RUN,
    ActivityType.WALK, ActivityType.BIKE, ActivityType.OTHER
)

fun CustomExercise.secondaryMuscleList(): List<Muscle> =
    secondaryMuscles.split(",").mapNotNull { name -> Muscle.entries.firstOrNull { it.name == name.trim() } }

fun secondaryMusclesText(muscles: Collection<Muscle>): String = muscles.joinToString(",") { it.name }

// How it appears in the picker and lists: under its main muscle, or its type if it has none.
fun CustomExercise.toLibraryExercise() = LibraryExercise(
    name = name,
    group = mainMuscle?.label ?: type.displayName(),
    type = type,
    custom = true
)

// What a set counts toward on the muscle map: 1 for the main muscle, half for each that assists.
// Only strength exercises count, like the library's.
fun CustomExercise.muscles(): Map<Muscle, Double> {
    if (type != ActivityType.STRENGTH) return emptyMap()
    val main = mainMuscle ?: return emptyMap()
    return mapOf(main to 1.0) + secondaryMuscleList().filter { it != main }.associateWith { 0.5 }
}

fun customExerciseNamed(name: String): CustomExercise? =
    ExerciseLibrary.custom.firstOrNull { it.name.equals(name, ignoreCase = true) }

// What's wrong with a name, or null if it's fine. A name can't be blank, or match any other
// exercise, ignoring capitals; editingId is the exercise being changed, which may keep its own.
fun customNameProblem(name: String, editingId: Long?): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "Enter a name"
    val clash = ExerciseLibrary.all.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    if (clash != null && (!clash.custom || customExerciseNamed(clash.name)?.id != editingId)) {
        return "There's already an exercise called ${clash.name}"
    }
    return null
}

// Adds a new exercise, or changes one. A new name is carried into every workout and program
// that used the old one, so its history and records stay with it.
suspend fun saveCustomExercise(db: WorkoutDatabase, previous: CustomExercise?, exercise: CustomExercise): CustomExercise {
    val dao = db.workoutDao()
    return db.withTransaction {
        if (previous == null) {
            exercise.copy(id = dao.insertCustomExercise(exercise))
        } else {
            dao.updateCustomExercise(exercise.copy(id = previous.id))
            if (previous.name != exercise.name) {
                dao.renameWorkoutExercises(previous.name, exercise.name)
                dao.renameProgramExercises(previous.name, exercise.name)
            }
            exercise.copy(id = previous.id)
        }
    }
}

// Removes it from the list of exercises. Workouts that used it keep it, under the same name.
suspend fun deleteCustomExercise(db: WorkoutDatabase, exercise: CustomExercise) {
    db.workoutDao().deleteCustomExercise(exercise.id)
}
