package com.example.myfitnessapp

// ---------------------------------------------------------------------------
// Checking a workout's sets before it's saved. Every strength set must be checked off, with its
// reps, and a weight unless the exercise can be done with bodyweight alone (a pull up, say).
// ---------------------------------------------------------------------------

// Words in a name that mean the exercise is normally done with no added weight.
private val BODYWEIGHT_WORDS = listOf(
    "push up", "pushup", "push-up", "pull up", "pull-up", "pullup", "chin up", "chin-up",
    "dip", "muscle up", "dead hang", "plank", "crunch", "sit up", "sit-up", "leg raise",
    "knee raise", "toes to bar", "ab wheel", "mountain climber", "dead bug", "bird dog",
    "hollow", "v up", "flutter kick", "russian twist", "inverted row", "lunge", "step up",
    "sissy squat", "wall sit", "pistol squat", "nordic", "glute ham raise", "glute bridge",
    "frog pump", "calf raise", "handstand", "pike", "burpee", "jumping jack", "box jump",
    "jump squat", "bear crawl", "superman", "bodyweight"
)

// Words that mean weight is added after all, like "Push Up (Weighted)" or "Cable Crunch".
private val LOADED_WORDS = listOf(
    "weighted", "barbell", "dumbbell", "cable", "machine", "smith", "kettlebell", "plate",
    "assisted", "sled"
)

// Whether a set of this exercise can be saved with reps but no weight.
fun allowsBodyweight(exerciseName: String): Boolean {
    // The user said so when making it.
    customExerciseNamed(exerciseName)?.let { return it.bodyweight }
    val name = exerciseName.lowercase()
    if (LOADED_WORDS.any { it in name }) return false
    return BODYWEIGHT_WORDS.any { it in name }
}

// What a set is missing before it can be saved: a value, or being checked off as done.
data class MissingFields(val weight: Boolean, val reps: Boolean, val notDone: Boolean = false) {
    val any: Boolean get() = weight || reps || notDone

    companion object {
        val NONE = MissingFields(weight = false, reps = false)
    }
}

fun missingFields(exercise: Exercise, set: ExerciseSet): MissingFields {
    if (exercise.type != ActivityType.STRENGTH) return MissingFields.NONE
    return MissingFields(
        weight = set.weight == null && !allowsBodyweight(exercise.name),
        reps = set.reps == null,
        notDone = !set.done
    )
}

// The names of the exercises with at least one set that can't be saved yet, in workout order.
fun exercisesWithIncompleteSets(exercises: List<Exercise>, sets: List<ExerciseSet>): List<String> =
    incompleteSets(exercises, sets).byExercise.map { (exercise, _) -> exercise.name }

// The sets stopping a workout from being saved, grouped by exercise in workout order.
data class IncompleteSets(val byExercise: List<Pair<Exercise, List<ExerciseSet>>>) {
    val setIds: List<Long> get() = byExercise.flatMap { (_, sets) -> sets.map { it.id } }

    // True when every one of them has what it needs and only wants ticking off, so they can all
    // be ticked at once.
    val onlyUnticked: Boolean
        get() = byExercise.all { (exercise, sets) ->
            sets.all { missingFields(exercise, it).let { m -> !m.weight && !m.reps } }
        }
}

fun incompleteSets(exercises: List<Exercise>, sets: List<ExerciseSet>): IncompleteSets {
    val byExercise = sets.groupBy { it.exerciseId }
    return IncompleteSets(
        exercises.mapNotNull { exercise ->
            byExercise[exercise.id].orEmpty()
                .filter { missingFields(exercise, it).any }
                .takeIf { it.isNotEmpty() }
                ?.let { exercise to it }
        }
    )
}
