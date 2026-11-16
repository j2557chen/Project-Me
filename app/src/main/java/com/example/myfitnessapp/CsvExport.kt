package com.example.myfitnessapp

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

// ---------------------------------------------------------------------------
// Exporting workouts as a CSV file, one row per set, laid out like a Hevy export. It opens in
// Excel or Google Sheets, and can be brought back into this app (or Hevy) with Import Workouts.
// ---------------------------------------------------------------------------

private val EXPORT_COLUMNS = listOf(
    "title", "start_time", "end_time", "description", "exercise_title", "superset_id",
    "exercise_notes", "set_index", "set_type", "weight_lbs", "reps", "distance_km", "duration_seconds", "rpe"
)
private val EXPORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

private fun hevyName(type: SetType): String = when (type) {
    SetType.NORMAL -> "normal"
    SetType.WARM_UP -> "warmup"
    SetType.DROP -> "dropset"
    SetType.FAILURE -> "failure"
}

// A field, quoted when it holds a comma, quote mark or line break.
private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

private fun number(value: Double?, decimals: Int): String = value?.let {
    val scale = Math.pow(10.0, decimals.toDouble())
    formatNumber((it * scale).roundToLong() / scale)
} ?: ""

// Every finished workout, oldest first, as CSV text. Weights are in pounds and times in the
// phone's time zone, as Hevy's are. RIR is turned into RPE (10 minus RIR), since the layout only
// has RPE.
fun workoutsToCsv(
    workouts: List<Workout>,
    exercises: List<Exercise>,
    sets: List<ExerciseSet>,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val exercisesByWorkout = exercises.groupBy { it.workoutId }
    val setsByExercise = sets.groupBy { it.exerciseId }
    fun time(millis: Long) = EXPORT_DATE.format(Instant.ofEpochMilli(millis).atZone(zone))

    val out = StringBuilder()
    out.append(EXPORT_COLUMNS.joinToString(",")).append('\n')
    for (workout in workouts.filter { it.endTime != null }.sortedBy { it.startTime }) {
        val list = exercisesByWorkout[workout.id].orEmpty().sortedWith(compareBy({ it.position }, { it.id }))
        // Supersets numbered 0, 1, 2... within the workout, as Hevy does.
        val supersetNumbers = list.mapNotNull { it.supersetGroup }.distinct().withIndex().associate { (i, g) -> g to i }

        for (exercise in list) {
            fun row(index: Int, type: SetType, weightKg: Double?, reps: Double?, distanceKm: Double?, seconds: Long?, effort: Double?) {
                val rpe = effort?.let { if (exercise.effortType == EffortType.RIR) 10 - it else it }
                val fields = listOf(
                    workout.displayName(), time(workout.startTime), time(workout.endTime!!), "",
                    exercise.name, exercise.supersetGroup?.let { supersetNumbers[it]?.toString() } ?: "",
                    exercise.notes ?: "", index.toString(), hevyName(type),
                    number(weightKg?.let(WeightUnit.LBS::fromKg), 2), number(reps, 1),
                    number(distanceKm, 3), seconds?.toString() ?: "", number(rpe, 1)
                )
                out.append(fields.joinToString(",") { csvField(it) }).append('\n')
            }

            if (exercise.type == ActivityType.STRENGTH) {
                setsByExercise[exercise.id].orEmpty().sortedBy { it.setNumber }.forEachIndexed { i, s ->
                    row(i, s.setType, s.weight, s.reps, null, null, s.effort)
                }
            } else {
                row(0, SetType.NORMAL, null, null, exercise.distance, exercise.durationSeconds, null)
            }
        }
    }
    return out.toString()
}
