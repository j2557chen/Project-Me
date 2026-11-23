package com.example.myfitnessapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// One entry in the built-in list of exercises the user can pick from.
// This is not a database table: it's a fixed list that ships with the app.
// When the user picks one, its name and type are copied into an Exercise
// row in the database (see WorkoutDatabase.kt) for the current workout.
data class LibraryExercise(
    val name: String,
    // The main muscle group, or a category like "Cardio". It's shown under the name
    // and is also searchable, so typing "chest" finds every chest exercise.
    val group: String,
    val type: ActivityType,
    // True for one the user made themselves.
    val custom: Boolean = false
)

// "object" creates a single shared instance, with no need to construct it.
// You use it directly: ExerciseLibrary.search("squat")
object ExerciseLibrary {

    // The exercises the user has made, loaded from the database when the app starts (see
    // FitnessApp). It's Compose state, so screens listing exercises update when one is added.
    var custom: List<CustomExercise> by mutableStateOf(emptyList())

    private var cachedCustom: List<CustomExercise>? = null
    private var cachedAll: List<LibraryExercise> = emptyList()

    // Every exercise: the built-in ones and the user's own, sorted alphabetically.
    val all: List<LibraryExercise>
        get() {
            val current = custom
            if (current !== cachedCustom) {
                cachedAll = (builtIn + current.map { it.toLibraryExercise() })
                    .distinctBy { it.name.lowercase() }
                    .sortedBy { it.name }
                cachedCustom = current
            }
            return cachedAll
        }

    // The built-in list, with duplicates removed and sorted alphabetically.
    // "+" joins lists together. Each line below adds one muscle group's exercises.
    val builtIn: List<LibraryExercise> = (
        HEVY_CATALOG +
        group("Chest", ActivityType.STRENGTH,
            "Bench Press (Barbell)", "Incline Bench Press (Barbell)", "Decline Bench Press (Barbell)",
            "Bench Press (Dumbbell)", "Incline Bench Press (Dumbbell)", "Decline Bench Press (Dumbbell)",
            "Chest Fly (Dumbbell)", "Incline Chest Fly (Dumbbell)", "Cable Fly Crossovers", "Low Cable Fly Crossovers",
            "Pec Deck Fly", "Chest Press (Machine)", "Push Up", "Incline Push Ups", "Decline Push Up",
            "Diamond Push Up", "Chest Dip", "Plate Squeeze (Svend Press)",
            "Larsen Press", "Incline Bench Press (Smith Machine)", "Chest Fly (Machine)", "Incline Chest Press (Machine)", "Push Up (Weighted)"
        ) + group("Back", ActivityType.STRENGTH,
            "Deadlift (Barbell)", "Rack Pull", "Pull Up", "Chin Up", "Wide Pull Up",
            "Neutral-Grip Pull-Up", "Lat Pulldown (Cable)", "Lat Pulldown - Close Grip (Cable)",
            "Straight Arm Lat Pulldown (Cable)", "Bent Over Row (Barbell)", "Pendlay Row (Barbell)", "Dumbbell Row",
            "Seated Cable Row - V Grip (Cable)", "T Bar Row", "Chest Supported Incline Row (Dumbbell)", "Seated Row (Machine)",
            "Inverted Row", "Back Extension (Weighted Hyperextension)", "Good Morning (Barbell)", "Shrug (Barbell)", "Shrug (Dumbbell)",
            "Pull Up (Assisted)", "Pull Up (Weighted)", "Seated Cable Row - Bar Grip", "Seated Cable Row - Bar Wide Grip", "Iso-Lateral Row (Machine)", "Single Arm Cable Row", "Single Arm Lat Pulldown", "Rope Straight Arm Pulldown", "Flexion Deadlift", "Zercher Deadlift", "Seated Good Morning", "QL Extension", "Back Flexion Stretch"
        ) + group("Shoulders", ActivityType.STRENGTH,
            "Overhead Press (Barbell)", "Shoulder Press (Dumbbell)", "Overhead Press (Dumbbell)",
            "Arnold Press (Dumbbell)", "Seated Shoulder Press (Machine)", "Push Press", "Single Arm Landmine Press (Barbell)",
            "Lateral Raise (Dumbbell)", "Lateral Raise (Cable)", "Front Raise (Barbell)", "Rear Delt Reverse Fly (Dumbbell)",
            "Rear Delt Reverse Fly (Machine)", "Face Pull", "Upright Row (Barbell)", "Pike Pushup", "Handstand Push Up",
            "Overhead Press (Smith Machine)", "Shoulder Press (Machine Plates)", "Lateral Raise (Machine)", "Seated Lateral Raise (Dumbbell)", "Single Arm Lateral Raise (Cable)", "Front Raise (Cable)", "Plate Front Raise", "Reverse Fly Single Arm (Cable)", "Shoulder External Rotation"
        ) + group("Biceps", ActivityType.STRENGTH,
            "Bicep Curl (Barbell)", "EZ Bar Biceps Curl", "Bicep Curl (Dumbbell)", "Hammer Curl (Dumbbell)", "Seated Incline Curl (Dumbbell)",
            "Preacher Curl (Barbell)", "Concentration Curl", "Bicep Curl (Cable)", "Spider Curl (Dumbbell)",
            "Preacher Curl (Dumbbell)"
        ) + group("Triceps", ActivityType.STRENGTH,
            "Bench Press - Close Grip (Barbell)", "Triceps Pushdown", "Triceps Rope Pushdown",
            "Triceps Extension (Dumbbell)", "Overhead Triceps Extension (Cable)", "Skullcrusher (Barbell)",
            "Triceps Kickback (Dumbbell)", "Bench Dip", "Triceps Dip",
            "Triceps Pressdown", "Triceps Extension (Cable)", "Triceps Extension (Barbell)", "Triceps Kickback (Cable)", "Single Arm Triceps Pushdown (Cable)", "Single Arm Tricep Extension (Dumbbell)"
        ) + group("Forearms", ActivityType.STRENGTH,
            "Seated Palms Up Wrist Curl", "Reverse Wrist Curl (Dumbbell)", "Reverse Curl (Barbell)", "Farmers Walk", "Dead Hang"
        ) + group("Core", ActivityType.STRENGTH,
            "Plank", "Side Plank", "Crunch", "Sit Up", "Bicycle Crunch", "Russian Twist (Bodyweight)",
            "Hanging Leg Raise", "Hanging Knee Raise", "Lying Leg Raise", "Toes to Bar", "Cable Crunch",
            "Ab Wheel", "Mountain Climber", "Dead Bug", "Bird Dog", "Hollow Hold",
            "V Up", "Flutter Kicks", "Cable Core Pallof Press", "Woodchopper",
            "Decline Crunch (Weighted)"
        ) + group("Quads", ActivityType.STRENGTH,
            "Squat (Barbell)", "Front Squat", "Goblet Squat", "Box Squat (Barbell)", "Squat (Smith Machine)",
            "Bulgarian Split Squat", "Leg Press (Machine)", "Hack Squat", "Leg Extension (Machine)", "Walking Lunge",
            "Reverse Lunge", "Step Up", "Sissy Squat", "Wall Sit", "Pistol Squat",
            "Squat (Dumbbell)", "Squat (Bodyweight)", "Zercher Squat", "Split Squat (Dumbbell)", "Single Leg Extensions", "Reverse Nordic"
        ) + group("Hamstrings", ActivityType.STRENGTH,
            "Romanian Deadlift (Barbell)", "Straight Leg Deadlift", "Single Leg Romanian Deadlift (Dumbbell)",
            "Lying Leg Curl (Machine)", "Seated Leg Curl (Machine)", "Nordic Hamstrings Curls", "Glute Ham Raise"
        ) + group("Glutes", ActivityType.STRENGTH,
            "Hip Thrust", "Glute Bridge", "Sumo Deadlift", "Standing Cable Glute Kickbacks",
            "Hip Abduction (Machine)", "Frog Pump", "Curtsy Lunge (Dumbbell)", "Lateral Band Walks",
            "Hip Adduction (Machine)"
        ) + group("Calves", ActivityType.STRENGTH,
            "Standing Calf Raise", "Seated Calf Raise", "Donkey Calf Raise", "Single-Leg Calf Raise",
            "Single Leg Standing Calf Raise (Dumbbell)", "Calf Extension (Machine)", "Calf Press (Machine)"
        ) + group("Full Body", ActivityType.STRENGTH,
            "Clean", "Power Clean", "Hang Clean", "Clean and Jerk", "Snatch", "Thruster (Barbell)",
            "Kettlebell Swing", "Kettlebell Turkish Get Up", "Wall Ball", "Sled Push", "Sled Pull",
            "Sandbag Carry", "Muscle Up"
        ) + group("Running", ActivityType.RUN,
            "Running", "Treadmill", "Trail Run", "Interval Run", "Tempo Run",
            "Long Run", "Sprints", "Hill Repeats"
        ) + group("Cardio", ActivityType.CARDIO,
            "Walking", "Incline Treadmill Walk", "Hiking", "Cycling", "Stationary Bike",
            "Spinning", "Air Bike", "Rowing Machine", "Ski Erg", "Elliptical Trainer",
            "Stair Master", "Swimming", "Jump Rope",
            "Sled Treadmill"
        ) + group("Conditioning", ActivityType.CARDIO,
            "Burpee", "Jumping Jack", "High Knees", "Box Jump", "Jump Squat",
            "Battle Ropes", "Ball Slams", "Bear Crawl"
        ) + group("Mobility", ActivityType.OTHER,
            "Warm Up", "Foam Rolling", "Yoga", "Hamstring Stretch", "Quad Stretch",
            "Hip Flexor Stretch", "Shoulder Stretch", "Child's Pose", "Cat-Cow",
            "Downward Dog", "Pigeon Pose"
        )
    ).distinctBy { it.name }.sortedBy { it.name }

    // Library names that changed to match the Hevy app's, old to new. The database upgrade
    // uses this to rename exercises already saved (see MIGRATION_12_13).
    val LIBRARY_RENAMES: Map<String, String> = linkedMapOf(
        "Barbell Bench Press" to "Bench Press (Barbell)",
        "Incline Barbell Bench Press" to "Incline Bench Press (Barbell)",
        "Dumbbell Bench Press" to "Bench Press (Dumbbell)",
        "Incline Dumbbell Press" to "Incline Bench Press (Dumbbell)",
        "Dumbbell Fly" to "Chest Fly (Dumbbell)",
        "Incline Dumbbell Fly" to "Incline Chest Fly (Dumbbell)",
        "Pec Deck" to "Pec Deck Fly",
        "Chest Press Machine" to "Chest Press (Machine)",
        "Push-Up" to "Push Up",
        "Deadlift" to "Deadlift (Barbell)",
        "Pull-Up" to "Pull Up",
        "Lat Pulldown" to "Lat Pulldown (Cable)",
        "Close-Grip Lat Pulldown" to "Lat Pulldown - Close Grip (Cable)",
        "Straight-Arm Pulldown" to "Straight Arm Lat Pulldown (Cable)",
        "Barbell Row" to "Bent Over Row (Barbell)",
        "Seated Cable Row" to "Seated Cable Row - V Grip (Cable)",
        "Machine Row" to "Seated Row (Machine)",
        "Back Extension" to "Back Extension (Weighted Hyperextension)",
        "Good Morning" to "Good Morning (Barbell)",
        "Barbell Shrug" to "Shrug (Barbell)",
        "Dumbbell Shrug" to "Shrug (Dumbbell)",
        "Overhead Press" to "Overhead Press (Barbell)",
        "Seated Dumbbell Shoulder Press" to "Shoulder Press (Dumbbell)",
        "Arnold Press" to "Arnold Press (Dumbbell)",
        "Machine Shoulder Press" to "Seated Shoulder Press (Machine)",
        "Dumbbell Lateral Raise" to "Lateral Raise (Dumbbell)",
        "Cable Lateral Raise" to "Lateral Raise (Cable)",
        "Front Raise" to "Front Raise (Barbell)",
        "Reverse Pec Deck" to "Rear Delt Reverse Fly (Machine)",
        "Barbell Curl" to "Bicep Curl (Barbell)",
        "EZ-Bar Curl" to "EZ Bar Biceps Curl",
        "Dumbbell Curl" to "Bicep Curl (Dumbbell)",
        "Hammer Curl" to "Hammer Curl (Dumbbell)",
        "Incline Dumbbell Curl" to "Seated Incline Curl (Dumbbell)",
        "Preacher Curl" to "Preacher Curl (Barbell)",
        "Rope Pushdown" to "Triceps Rope Pushdown",
        "Cable Overhead Extension" to "Overhead Triceps Extension (Cable)",
        "Overhead Triceps Extension" to "Triceps Extension (Dumbbell)",
        "Skull Crusher" to "Skullcrusher (Barbell)",
        "Wrist Curl" to "Seated Palms Up Wrist Curl",
        "Reverse Curl" to "Reverse Curl (Barbell)",
        "Barbell Back Squat" to "Squat (Barbell)",
        "Leg Press" to "Leg Press (Machine)",
        "Leg Extension" to "Leg Extension (Machine)",
        "Romanian Deadlift" to "Romanian Deadlift (Barbell)",
        "Lying Leg Curl" to "Lying Leg Curl (Machine)",
        "Seated Leg Curl" to "Seated Leg Curl (Machine)",
        "Nordic Hamstring Curl" to "Nordic Hamstrings Curls",
        "Hip Abduction Machine" to "Hip Abduction (Machine)",
        "Muscle-Up" to "Muscle Up",
        "Outdoor Run" to "Running",
        "Treadmill Run" to "Treadmill",
        "Outdoor Cycling" to "Cycling",
        "Stair Climber" to "Stair Master"
    )

    // The second set of renames to the Hevy app's names, made after reading its whole exercise
    // list. Used by MIGRATION_13_14.
    val CATALOG_RENAMES: Map<String, String> = linkedMapOf(
        "Decline Barbell Bench Press" to "Decline Bench Press (Barbell)",
        "Decline Dumbbell Press" to "Decline Bench Press (Dumbbell)",
        "Cable Fly" to "Cable Fly Crossovers",
        "Low-to-High Cable Fly" to "Low Cable Fly Crossovers",
        "Incline Push-Up" to "Incline Push Ups",
        "Decline Push-Up" to "Decline Push Up",
        "Diamond Push-Up" to "Diamond Push Up",
        "Svend Press" to "Plate Squeeze (Svend Press)",
        "Chin-Up" to "Chin Up",
        "Wide-Grip Pull-Up" to "Wide Pull Up",
        "Pendlay Row" to "Pendlay Row (Barbell)",
        "T-Bar Row" to "T Bar Row",
        "Chest-Supported Row" to "Chest Supported Incline Row (Dumbbell)",
        "Standing Dumbbell Shoulder Press" to "Overhead Press (Dumbbell)",
        "Landmine Press" to "Single Arm Landmine Press (Barbell)",
        "Rear Delt Fly" to "Rear Delt Reverse Fly (Dumbbell)",
        "Upright Row" to "Upright Row (Barbell)",
        "Pike Push-Up" to "Pike Pushup",
        "Handstand Push-Up" to "Handstand Push Up",
        "Cable Curl" to "Bicep Curl (Cable)",
        "Spider Curl" to "Spider Curl (Dumbbell)",
        "Close-Grip Bench Press" to "Bench Press - Close Grip (Barbell)",
        "Dumbbell Kickback" to "Triceps Kickback (Dumbbell)",
        "Reverse Wrist Curl" to "Reverse Wrist Curl (Dumbbell)",
        "Farmer's Carry" to "Farmers Walk",
        "Sit-Up" to "Sit Up",
        "Russian Twist" to "Russian Twist (Bodyweight)",
        "Leg Raise" to "Lying Leg Raise",
        "Toes-to-Bar" to "Toes to Bar",
        "Ab Wheel Rollout" to "Ab Wheel",
        "V-Up" to "V Up",
        "Pallof Press" to "Cable Core Pallof Press",
        "Box Squat" to "Box Squat (Barbell)",
        "Smith Machine Squat" to "Squat (Smith Machine)",
        "Step-Up" to "Step Up",
        "Stiff-Leg Deadlift" to "Straight Leg Deadlift",
        "Single-Leg Romanian Deadlift" to "Single Leg Romanian Deadlift (Dumbbell)",
        "Glute-Ham Raise" to "Glute Ham Raise",
        "Cable Kickback" to "Standing Cable Glute Kickbacks",
        "Curtsy Lunge" to "Curtsy Lunge (Dumbbell)",
        "Banded Lateral Walk" to "Lateral Band Walks",
        "Barbell Clean" to "Clean",
        "Thruster" to "Thruster (Barbell)",
        "Turkish Get-Up" to "Kettlebell Turkish Get Up",
        "Brisk Walk" to "Walking",
        "Spin Class" to "Spinning",
        "Assault Bike" to "Air Bike",
        "Elliptical" to "Elliptical Trainer",
        "Jumping Jacks" to "Jumping Jack",
        "Medicine Ball Slam" to "Ball Slams",
        "Dynamic Warm-Up" to "Warm Up",
        "Yoga Flow" to "Yoga"
    )

    // Hevy names that import as a different library exercise, by the user's choice.
    val HEVY_ALIASES: Map<String, String> = mapOf(
        "Butterfly (Pec Deck)" to "Pec Deck Fly",
        "Feet Up Bench Press (Barbell)" to "Larsen Press",
        "Stair Machine (Floors)" to "Stair Master"
    )

    // How many exercises the Recent and Frequent sections of the picker show.
    private const val SHORTLIST_SIZE = 5

    // The picker's list, in sections: a title (null for none) and its exercises.
    // With nothing typed: the exercises done most recently, then the most often (leaving out any
    // already under Recent), then every exercise. With a search: the matches, with ones the user
    // has done first (most often done first), then the rest in the usual order.
    // Exercises already in the workout (alreadyAdded) are kept out of all that and listed last,
    // under "Already added", so they don't take up room but a search still finds them.
    fun pickerSections(
        query: String,
        history: List<ExerciseSummary>,
        alreadyAdded: Set<String> = emptySet()
    ): List<Pair<String?, List<LibraryExercise>>> {
        val sections = pickerSectionsLeavingOut(query, history, alreadyAdded)
        val added = search(query).filter { it.name in alreadyAdded }
        return if (added.isEmpty()) sections else sections + ("Already added" to added)
    }

    private fun pickerSectionsLeavingOut(
        query: String,
        history: List<ExerciseSummary>,
        leaveOut: Set<String>
    ): List<Pair<String?, List<LibraryExercise>>> {
        val byName = all.associateBy { it.name }
        val done = history.filter { it.name in byName && it.name !in leaveOut }
        if (query.isBlank()) {
            val recent = done.sortedByDescending { it.lastTime }.take(SHORTLIST_SIZE).map { it.name }
            val frequent = done.filter { it.name !in recent }
                .sortedWith(compareByDescending<ExerciseSummary> { it.workouts }.thenByDescending { it.lastTime })
                .take(SHORTLIST_SIZE).map { it.name }
            return listOf(
                "Recent" to recent.map(byName::getValue),
                "Frequent" to frequent.map(byName::getValue),
                "All Exercises" to all.filter { it.name !in leaveOut }
            ).filter { it.second.isNotEmpty() }
        }
        val timesDone = done.associate { it.name to it.workouts }
        // sortedBy keeps search's own order among exercises done the same number of times.
        val results = search(query).filter { it.name !in leaveOut }.sortedByDescending { timesDone[it.name] ?: 0 }
        return listOf<Pair<String?, List<LibraryExercise>>>(null to results).filter { it.second.isNotEmpty() }
    }

    // Finds exercises matching what the user typed.
    // Every word they typed must appear in the exercise's name or muscle group,
    // in any order, so "dumbbell press" and "press dumbbell" give the same results.
    // An empty search returns the whole list.
    fun search(query: String): List<LibraryExercise> {
        // trim() removes spaces at the ends; split(" ") breaks the text into words;
        // filter drops empty pieces left by double spaces.
        val words = query.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return all

        return all
            .filter { exercise ->
                val text = "${exercise.name} ${exercise.group}".lowercase()
                // "it in text" checks whether the word appears anywhere in the text.
                words.all { it in text }
            }
            // sortedBy keeps the existing order for ties, so results stay alphabetical.
            // This just moves names that START with the first typed word to the top.
            .sortedBy { if (it.name.lowercase().startsWith(words[0])) 0 else 1 }
    }

    // A small helper that builds LibraryExercise entries for one group.
    // "vararg" lets us pass any number of names, separated by commas.
    private fun group(
        group: String,
        type: ActivityType,
        vararg names: String
    ): List<LibraryExercise> {
        return names.map { LibraryExercise(name = it, group = group, type = type) }
    }
}
