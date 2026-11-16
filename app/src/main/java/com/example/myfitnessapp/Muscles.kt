package com.example.myfitnessapp

// The muscles shown on the Home heat map.
enum class Muscle(val label: String) {
    CHEST("Chest"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    ABS("Core"),
    UPPER_BACK("Upper Back"),
    TRAPS("Traps"),
    LOWER_BACK("Lower Back"),
    GLUTES("Glutes"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    CALVES("Calves")
}

// How a set of each library exercise counts toward each muscle: 1 for the main muscle, and 0.5
// for each muscle that assists.
object MuscleMap {
    private const val ASSIST = 0.5

    private fun works(main: Muscle, vararg assisting: Muscle): Map<Muscle, Double> =
        mapOf(main to 1.0) + assisting.associateWith { ASSIST }

    // The main muscle of each library group, for exercises with no entry below.
    private val groupMuscle = mapOf(
        "Chest" to Muscle.CHEST,
        "Back" to Muscle.UPPER_BACK,
        "Shoulders" to Muscle.SHOULDERS,
        "Biceps" to Muscle.BICEPS,
        "Triceps" to Muscle.TRICEPS,
        "Forearms" to Muscle.FOREARMS,
        "Core" to Muscle.ABS,
        "Quads" to Muscle.QUADS,
        "Hamstrings" to Muscle.HAMSTRINGS,
        "Glutes" to Muscle.GLUTES,
        "Calves" to Muscle.CALVES
    )

    // Exercises that work more than their group's main muscle. Every Full Body exercise is here,
    // since that group has no single main muscle.
    val explicit: Map<String, Map<Muscle, Double>> = buildMap {
        fun all(muscles: Map<Muscle, Double>, vararg names: String) = names.forEach { put(it, muscles) }

        all(works(Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS),
            "Bench Press (Barbell)", "Incline Bench Press (Barbell)", "Decline Bench Press (Barbell)",
            "Bench Press (Dumbbell)", "Incline Bench Press (Dumbbell)", "Decline Bench Press (Dumbbell)",
            "Chest Press (Machine)", "Push Up", "Incline Push Ups", "Decline Push Up", "Chest Dip")
        all(works(Muscle.TRICEPS, Muscle.CHEST, Muscle.SHOULDERS),
            "Diamond Push Up", "Bench Press - Close Grip (Barbell)", "Bench Dip", "Triceps Dip")

        all(works(Muscle.SHOULDERS, Muscle.TRICEPS),
            "Overhead Press (Barbell)", "Shoulder Press (Dumbbell)", "Overhead Press (Dumbbell)",
            "Arnold Press (Dumbbell)", "Seated Shoulder Press (Machine)", "Push Press", "Single Arm Landmine Press (Barbell)",
            "Pike Pushup", "Handstand Push Up")
        all(works(Muscle.SHOULDERS, Muscle.UPPER_BACK), "Rear Delt Reverse Fly (Dumbbell)", "Rear Delt Reverse Fly (Machine)")
        all(works(Muscle.SHOULDERS, Muscle.TRAPS, Muscle.UPPER_BACK), "Face Pull")
        all(works(Muscle.SHOULDERS, Muscle.TRAPS), "Upright Row (Barbell)")

        all(works(Muscle.UPPER_BACK, Muscle.BICEPS, Muscle.FOREARMS),
            "Pull Up", "Chin Up", "Wide Pull Up", "Neutral-Grip Pull-Up", "Lat Pulldown (Cable)",
            "Lat Pulldown - Close Grip (Cable)", "Bent Over Row (Barbell)", "Pendlay Row (Barbell)", "Dumbbell Row",
            "Seated Cable Row - V Grip (Cable)", "T Bar Row", "Chest Supported Incline Row (Dumbbell)", "Seated Row (Machine)", "Inverted Row")
        all(works(Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.TRAPS, Muscle.FOREARMS),
            "Deadlift (Barbell)", "Rack Pull")
        all(works(Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.HAMSTRINGS), "Back Extension (Weighted Hyperextension)", "Good Morning (Barbell)")
        all(works(Muscle.TRAPS), "Shrug (Barbell)", "Shrug (Dumbbell)")

        all(works(Muscle.BICEPS, Muscle.FOREARMS), "Hammer Curl (Dumbbell)")
        all(works(Muscle.FOREARMS, Muscle.BICEPS), "Reverse Curl (Barbell)")
        all(works(Muscle.FOREARMS, Muscle.TRAPS, Muscle.ABS), "Farmers Walk")
        all(works(Muscle.FOREARMS, Muscle.UPPER_BACK), "Dead Hang")

        all(works(Muscle.QUADS, Muscle.GLUTES),
            "Squat (Barbell)", "Front Squat", "Goblet Squat", "Box Squat (Barbell)", "Squat (Smith Machine)",
            "Bulgarian Split Squat", "Leg Press (Machine)", "Hack Squat", "Walking Lunge", "Reverse Lunge",
            "Step Up", "Pistol Squat")
        all(works(Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.LOWER_BACK),
            "Romanian Deadlift (Barbell)", "Straight Leg Deadlift", "Single Leg Romanian Deadlift (Dumbbell)")
        all(works(Muscle.HAMSTRINGS, Muscle.GLUTES), "Glute Ham Raise")
        all(works(Muscle.GLUTES, Muscle.HAMSTRINGS), "Hip Thrust", "Glute Bridge", "Frog Pump")
        all(works(Muscle.GLUTES, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.LOWER_BACK), "Sumo Deadlift")
        all(works(Muscle.GLUTES, Muscle.QUADS), "Curtsy Lunge (Dumbbell)")

        all(works(Muscle.QUADS, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.TRAPS),
            "Clean", "Power Clean", "Hang Clean")
        all(works(Muscle.QUADS, Muscle.GLUTES, Muscle.TRAPS, Muscle.SHOULDERS, Muscle.TRICEPS),
            "Clean and Jerk", "Snatch")
        all(works(Muscle.QUADS, Muscle.GLUTES, Muscle.SHOULDERS, Muscle.TRICEPS), "Thruster (Barbell)")
        all(works(Muscle.QUADS, Muscle.GLUTES, Muscle.SHOULDERS), "Wall Ball")
        all(works(Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES), "Sled Push")
        all(works(Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.UPPER_BACK), "Sled Pull")
        all(works(Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.LOWER_BACK), "Kettlebell Swing")
        all(works(Muscle.SHOULDERS, Muscle.ABS, Muscle.GLUTES), "Kettlebell Turkish Get Up")
        all(works(Muscle.ABS, Muscle.FOREARMS, Muscle.UPPER_BACK), "Sandbag Carry")
        all(works(Muscle.UPPER_BACK, Muscle.CHEST, Muscle.TRICEPS, Muscle.BICEPS), "Muscle Up")

        all(works(Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS),
            "Larsen Press", "Incline Bench Press (Smith Machine)", "Incline Chest Press (Machine)",
            "Push Up (Weighted)")
        all(works(Muscle.SHOULDERS, Muscle.TRICEPS), "Overhead Press (Smith Machine)", "Shoulder Press (Machine Plates)")
        all(works(Muscle.SHOULDERS, Muscle.UPPER_BACK), "Reverse Fly Single Arm (Cable)")
        all(works(Muscle.UPPER_BACK, Muscle.BICEPS, Muscle.FOREARMS),
            "Pull Up (Assisted)", "Pull Up (Weighted)", "Seated Cable Row - Bar Grip", "Seated Cable Row - Bar Wide Grip",
            "Iso-Lateral Row (Machine)", "Single Arm Cable Row", "Single Arm Lat Pulldown")
        all(works(Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.HAMSTRINGS), "Flexion Deadlift", "Zercher Deadlift", "Seated Good Morning")
        all(works(Muscle.LOWER_BACK), "QL Extension", "Back Flexion Stretch")
        all(works(Muscle.QUADS, Muscle.GLUTES), "Squat (Dumbbell)", "Squat (Bodyweight)", "Zercher Squat", "Split Squat (Dumbbell)")
    }

    private val libraryGroups = ExerciseLibrary.builtIn.associate { it.name to it.group }

    // What a set of this exercise counts toward. Empty for names outside the library and for
    // groups that aren't strength training.
    fun musclesFor(exerciseName: String): Map<Muscle, Double> =
        customExerciseNamed(exerciseName)?.muscles()
            ?: explicit[exerciseName]
            ?: HEVY_MUSCLES[exerciseName]
            ?: libraryGroups[exerciseName]?.let(groupMuscle::get)?.let { works(it) }
            ?: emptyMap()
}

// The sets each muscle got from these exercises, with assisting muscles counting half.
fun muscleSets(counts: List<ExerciseSetCount>): Map<Muscle, Double> {
    val totals = mutableMapOf<Muscle, Double>()
    for (count in counts) {
        for ((muscle, share) in MuscleMap.musclesFor(count.name)) {
            totals[muscle] = (totals[muscle] ?: 0.0) + count.sets * share
        }
    }
    return totals
}
