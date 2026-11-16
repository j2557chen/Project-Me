package com.example.myfitnessapp

import androidx.room.withTransaction
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// ---------------------------------------------------------------------------
// Reading a Hevy export (Settings > Export Data in Hevy, a .csv file). Each row is one set; the
// rows of one workout share its title and start time, in order.
// ---------------------------------------------------------------------------

// What a file couldn't be read as, shown to the user.
class ImportException(message: String) : Exception(message)

data class ImportedSet(
    val weightKg: Double?,
    val reps: Double?,
    val effort: Double?,
    val setType: SetType = SetType.NORMAL
)

data class ImportedExercise(
    val name: String,
    val type: ActivityType,
    val sets: List<ImportedSet>,
    // For the types other than strength, the rows added together.
    val distanceKm: Double?,
    val durationSeconds: Long?,
    val notes: String?,
    // Hevy's superset number; exercises sharing one are a superset.
    val supersetId: String? = null
)

data class ImportedWorkout(
    val name: String?,
    val startTime: Long,
    val endTime: Long,
    val exercises: List<ImportedExercise>
)

private val HEVY_COLUMNS = listOf(
    "title", "start_time", "end_time", "exercise_title", "set_index",
    "weight_lbs", "reps", "distance_km", "duration_seconds", "rpe", "exercise_notes"
)
private val HEVY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

// Splits CSV text into rows of fields. Fields in double quotes may hold commas, line breaks, and
// "" for a quote mark.
fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    // A byte order mark at the very start isn't part of the first column's name.
    if (text.startsWith("﻿")) i = 1
    while (i < text.length) {
        val c = text[i]
        if (quoted) {
            if (c == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
            } else field.append(c)
        } else when (c) {
            '"' -> quoted = true
            ',' -> { row.add(field.toString()); field.clear() }
            '\r' -> {}
            '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
    return rows
}

// The library exercise a Hevy name imports as: one of the user's chosen app names, the library
// name it matches ignoring capitals, or the Hevy name itself if the library doesn't have it.
fun libraryExerciseFor(hevyName: String): LibraryExercise? {
    val name = ExerciseLibrary.HEVY_ALIASES[hevyName] ?: hevyName
    return ExerciseLibrary.all.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

// Reads a Hevy export into workouts, oldest first. Hevy's times have no time zone, so they're
// read in the phone's.
fun parseHevyExport(text: String, zone: ZoneId = ZoneId.systemDefault()): List<ImportedWorkout> {
    val rows = parseCsv(text).filter { r -> r.any { it.isNotBlank() } }
    val header = rows.firstOrNull() ?: throw ImportException("The file is empty.")
    val column = header.withIndex().associate { (i, name) -> name.trim() to i }
    val missing = HEVY_COLUMNS.filter { it !in column }
    if (missing.isNotEmpty()) {
        throw ImportException("This doesn't look like a Hevy export. It's missing: ${missing.joinToString()}.")
    }
    fun List<String>.at(name: String) = getOrNull(column.getValue(name))?.trim().orEmpty()
    // For columns an older export may not have.
    fun List<String>.optional(name: String) = column[name]?.let { getOrNull(it) }?.trim().orEmpty()
    fun time(value: String): Long = try {
        LocalDateTime.parse(value, HEVY_DATE).atZone(zone).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        throw ImportException("Couldn't read the date \"$value\".")
    }

    // A workout's rows, then each run of rows for the same exercise in it.
    val byWorkout = rows.drop(1).groupBy { it.at("title") to it.at("start_time") }
    return byWorkout.map { (key, workoutRows) ->
        val blocks = mutableListOf<MutableList<List<String>>>()
        for (r in workoutRows) {
            if (blocks.lastOrNull()?.first()?.at("exercise_title") == r.at("exercise_title")) blocks.last().add(r)
            else blocks.add(mutableListOf(r))
        }
        ImportedWorkout(
            name = key.first.ifBlank { null },
            startTime = time(key.second),
            endTime = time(workoutRows.first().at("end_time")),
            exercises = blocks.map { block -> importedExercise(block.first().at("exercise_title"), block.map { r ->
                mapOf(
                    "weight" to r.at("weight_lbs"), "reps" to r.at("reps"), "rpe" to r.at("rpe"),
                    "distance" to r.at("distance_km"), "duration" to r.at("duration_seconds"),
                    "notes" to r.at("exercise_notes"), "index" to r.at("set_index"),
                    "type" to r.optional("set_type"), "superset" to r.optional("superset_id")
                )
            }) }
        )
    }.sortedBy { it.startTime }
}

// Hevy's set types: "normal", "warmup", "dropset" and "failure".
fun hevySetType(text: String): SetType = when (text.lowercase()) {
    "warmup" -> SetType.WARM_UP
    "dropset" -> SetType.DROP
    "failure" -> SetType.FAILURE
    else -> SetType.NORMAL
}

private fun importedExercise(hevyName: String, rows: List<Map<String, String>>): ImportedExercise {
    val library = libraryExerciseFor(hevyName)
    val hasDistanceOrTime = rows.any { it["distance"]!!.isNotEmpty() || it["duration"]!!.isNotEmpty() }
    val hasReps = rows.any { it["reps"]!!.isNotEmpty() }
    // Hevy doesn't say what kind an exercise is, so the library decides, or how it was logged.
    val type = library?.type ?: if (hasDistanceOrTime && !hasReps) ActivityType.CARDIO else ActivityType.STRENGTH
    val notes = rows.map { it["notes"]!! }.firstOrNull { it.isNotEmpty() }
    val supersetId = rows.map { it["superset"].orEmpty() }.firstOrNull { it.isNotEmpty() }
    val sorted = rows.sortedBy { it["index"]!!.toIntOrNull() ?: 0 }

    return if (type == ActivityType.STRENGTH) {
        ImportedExercise(
            name = library?.name ?: hevyName,
            type = type,
            sets = sorted.map {
                ImportedSet(
                    weightKg = it["weight"]!!.toDoubleOrNull()?.let(WeightUnit.LBS::toKg),
                    reps = it["reps"]!!.toDoubleOrNull(),
                    effort = it["rpe"]!!.toDoubleOrNull(),
                    setType = hevySetType(it["type"].orEmpty())
                )
            // A strength set with neither a weight nor reps (a timed hold) has nothing to keep.
            }.filter { it.weightKg != null || it.reps != null },
            distanceKm = null,
            durationSeconds = null,
            notes = notes,
            supersetId = supersetId
        )
    } else {
        val distances = rows.mapNotNull { it["distance"]!!.toDoubleOrNull() }
        val durations = rows.mapNotNull { it["duration"]!!.toDoubleOrNull() }
        ImportedExercise(
            name = library?.name ?: hevyName,
            type = type,
            sets = emptyList(),
            distanceKm = distances.takeIf { it.isNotEmpty() }?.sum(),
            durationSeconds = durations.takeIf { it.isNotEmpty() }?.sum()?.toLong(),
            notes = notes,
            supersetId = supersetId
        )
    }
}

// How an import went.
data class ImportResult(val imported: Int, val alreadyThere: Int)

// Saves the workouts, all together or not at all. A workout starting at the same minute as one
// already saved is left out, so importing the same file twice doesn't double anything.
suspend fun saveImportedWorkouts(db: WorkoutDatabase, workouts: List<ImportedWorkout>): ImportResult {
    val dao = db.workoutDao()
    var imported = 0
    var skipped = 0
    db.withTransaction {
        val existing = dao.getWorkoutStartTimes().map { it / 60_000 }.toMutableSet()
        for (w in workouts) {
            if (!existing.add(w.startTime / 60_000)) { skipped++; continue }
            val workoutId = dao.insertWorkout(Workout(startTime = w.startTime, endTime = w.endTime, name = w.name))
            val exerciseIds = mutableListOf<Long>()
            w.exercises.forEachIndexed { position, e ->
                val exerciseId = dao.insertExercise(
                    Exercise(
                        workoutId = workoutId,
                        name = e.name,
                        type = e.type,
                        distance = e.distanceKm,
                        durationSeconds = e.durationSeconds,
                        notes = e.notes,
                        position = position
                    )
                )
                exerciseIds += exerciseId
                dao.insertSets(e.sets.mapIndexed { i, s ->
                    ExerciseSet(exerciseId = exerciseId, setNumber = i + 1, weight = s.weightKg, reps = s.reps, effort = s.effort, done = true, setType = s.setType)
                })
            }
            // Hevy's supersets: neighbouring exercises with the same superset number.
            val hevyGroups = w.exercises.map { e -> e.supersetId?.let { it.hashCode().toLong() } }
            tidySupersets(hevyGroups, exerciseIds).forEachIndexed { i, group ->
                if (group != null) dao.setSupersetGroup(exerciseIds[i], group)
            }
            imported++
        }
    }
    return ImportResult(imported, skipped)
}
