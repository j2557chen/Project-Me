package com.example.myfitnessapp

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// ENTITIES: each one becomes a table in the database. Each property becomes a column.
// ---------------------------------------------------------------------------

// One row per workout session.
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Times are stored as a Long: the number of milliseconds since January 1, 1970 (UTC).
    val startTime: Long,

    // "Long?" means this can be null. A workout that is still in progress has no
    // end time yet, so it stays null until the user ends the workout.
    val endTime: Long? = null,

    // The name the user gave it, or the program it came from. Null shows a name from the time
    // it started, like "Morning Workout" (see displayName in WorkoutNames.kt).
    val name: String? = null
)

// The things an exercise can measure. Each one is a label shown above its column.
enum class TrackedField(val label: String) {
    WEIGHT("Weight"),
    REPS("Reps"),
    DISTANCE("Distance"),
    DURATION("Duration"),
    NOTES("Notes")
}

// A workout's totals for the Home summary. endTime is null while it's running. The volume
// (weight x reps over every strength set, in kg), distance (in km), and heaviest weight lifted
// (in kg) are null when it has none.
data class WorkoutStats(
    val startTime: Long,
    val endTime: Long?,
    val volumeKg: Double?,
    val distanceKm: Double?,
    val heaviestKg: Double? = null
)

// How many sets of one exercise were done, for the Home muscle map.
data class ExerciseSetCount(val name: String, val sets: Int)

// One session of an exercise as stored, for its distance and time records.
data class ActivityRow(
    val exerciseId: Long,
    val workoutId: Long,
    val startTime: Long,
    val distanceKm: Double?,
    val durationSeconds: Long?,
    val isRecorded: Boolean
)

// An exercise the user has done: how many workouts it's in, and when it was last done.
data class ExerciseSummary(val name: String, val type: ActivityType, val workouts: Int, val lastTime: Long)

// The kinds of activity. Each type lists what it tracks, so this is the single place
// that says "strength is measured in weight and reps, a run in distance and duration".
// Room saves an enum in the database as its name (for example "STRENGTH"),
// so adding a property here doesn't change the database.
enum class ActivityType(val tracks: List<TrackedField>) {
    STRENGTH(listOf(TrackedField.WEIGHT, TrackedField.REPS)),
    CARDIO(listOf(TrackedField.DURATION, TrackedField.DISTANCE)),
    RUN(listOf(TrackedField.DISTANCE, TrackedField.DURATION)),
    WALK(listOf(TrackedField.DISTANCE, TrackedField.DURATION)),
    BIKE(listOf(TrackedField.DISTANCE, TrackedField.DURATION)),
    OTHER(listOf(TrackedField.DURATION, TrackedField.NOTES))
}

// One row per exercise/activity done during a workout.
@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId")]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val name: String,
    val type: ActivityType,

    // The details below are for the activity types that have ONE set of results per
    // exercise (runs, cardio, and other). Strength exercises don't use them: their
    // results are stored per set, in the exercise_sets table.
    // Each one is null until the user fills it in, and which ones are used depends
    // on the type's "tracks" list.

    // Distance in kilometers.
    val distance: Double? = null,
    // Total time in seconds.
    val durationSeconds: Long? = null,
    // Free text.
    val notes: String? = null,

    // For activities recorded with GPS: the path of the saved picture of the route on the
    // map. The picture itself is a file in the app's private storage; only its path is
    // kept here, because databases aren't a good place for large images.
    val routeImagePath: String? = null,

    // True for an activity that was recorded on the Record screen. Its distance and
    // duration came from the recording, so they are shown but can't be edited.
    // The default value is what the column holds for rows that existed before it was added.
    @ColumnInfo(defaultValue = "0")
    val isRecorded: Boolean = false,

    // Where this exercise sits in its workout's list: lower numbers come first. The user can
    // change it by rearranging the exercises. New exercises go to the end of the list.
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,

    // Whether this exercise's ExerciseSet.effort values are RPE or RIR.
    val effortType: EffortType = EffortType.RPE,

    // For a recording: whether its fastest times (in best_efforts) have been worked out. They're
    // worked out once, when it's saved, since its readings never change after that.
    @ColumnInfo(defaultValue = "0")
    val bestEffortsSaved: Boolean = false,

    // Exercises with the same number are a superset, done back to back. Null for one on its own.
    val supersetGroup: Long? = null
)

// The fastest time a recording took over one record distance (3 km, 5 km, ...), worked out
// from its GPS readings when it was saved.
@Entity(
    tableName = "best_efforts",
    primaryKeys = ["exerciseId", "distanceKm"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BestEffort(
    val exerciseId: Long,
    val distanceKm: Int,
    val millis: Long
)

// Not a table: one strength set with its exercise's name, for finding record days.
data class NamedStrengthSet(
    val name: String,
    val setId: Long,
    val workoutId: Long,
    val startTime: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Double
) {
    fun toStrengthSet() = StrengthSet(setId, workoutId, startTime, setNumber, weightKg, reps)
}

// Not a table: one distance or time exercise with its name and type, for finding record days.
data class NamedActivityRow(
    val name: String,
    val type: ActivityType,
    val exerciseId: Long,
    val workoutId: Long,
    val startTime: Long,
    val distanceKm: Double?,
    val durationSeconds: Long?,
    val isRecorded: Boolean
) {
    fun toActivityRow() = ActivityRow(exerciseId, workoutId, startTime, distanceKm, durationSeconds, isRecorded)
}

// What kind of set it is. Warm-ups don't count towards records, volume or muscle sets; the
// others all count as normal sets do.
enum class SetType(val label: String, val letter: String?) {
    NORMAL("Normal", null),
    WARM_UP("Warm-up", "W"),
    DROP("Drop Set", "D"),
    FAILURE("Failure", "F")
}

// The two ways of rating how hard a set was.
enum class EffortType(val label: String, val meaning: String) {
    RPE("RPE", "Rate of Perceived Exertion"),
    RIR("RIR", "Reps In Reserve");

    fun toggled(): EffortType = if (this == RPE) RIR else RPE
}

// One row per set of a strength exercise (set 1, set 2, ...).
// A set belongs to exactly one exercise, and deleting the exercise deletes its sets.
@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId")]
)
data class ExerciseSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    // 1 for the first set, 2 for the second, and so on.
    val setNumber: Int,
    // Both can be null: a new set starts empty until the user fills it in.
    // Weight is a Double so it can hold decimals like 22.5.
    val weight: Double? = null,
    // Can end in .5: a half rep counts as half a rep towards volume.
    val reps: Double? = null,
    // How hard the set was, as an RPE or RIR number (the exercise's effortType says which).
    // Empty until filled in, or until a program fills it in.
    val effort: Double? = null,
    // Ticked off by the user. A workout can't be saved until every set is.
    @ColumnInfo(defaultValue = "0")
    val done: Boolean = false,
    @ColumnInfo(defaultValue = "NORMAL")
    val setType: SetType = SetType.NORMAL
)

// ---------------------------------------------------------------------------
// Workout programs: saved lists of exercises that can be started as a workout.
// ---------------------------------------------------------------------------

// One row per saved program.
@Entity(tableName = "programs")
data class Program(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

// One row per exercise in a program, in the order given by position. Deleting the program
// deletes its exercises (the foreign key cascades).
@Entity(
    tableName = "program_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Program::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("programId")]
)
data class ProgramExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val name: String,
    val type: ActivityType,
    val position: Int,
    // For strength exercises: whether the sets' effort numbers are RPE or RIR.
    val effortType: EffortType = EffortType.RPE,
    // For the other types: the planned details, the same ones a workout exercise has.
    val distance: Double? = null,
    val durationSeconds: Long? = null,
    val notes: String? = null,
    // Exercises with the same number are a superset.
    val supersetGroup: Long? = null
)

// One row per planned set of a strength exercise in a program.
@Entity(
    tableName = "program_sets",
    foreignKeys = [
        ForeignKey(
            entity = ProgramExercise::class,
            parentColumns = ["id"],
            childColumns = ["programExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("programExerciseId")]
)
data class ProgramSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programExerciseId: Long,
    val setNumber: Int,
    val reps: Double? = null,
    val effort: Double? = null,
    @ColumnInfo(defaultValue = "NORMAL")
    val setType: SetType = SetType.NORMAL
)

// Not a table: a program's id and name, with how many exercises it has, for the program list.
data class ProgramSummary(
    val id: Long,
    val name: String,
    val exerciseCount: Int
)

// One row per GPS reading of a recorded activity: where the user was, how high up, how far
// they had gone, and the activity time, all kept together. The route, the elevation graph, and
// the splits are all worked out from these. They are stored exactly as the phone reported
// them. Looking at them never changes them, and they're only ever removed when the activity
// itself is deleted (the foreign key cascades).
//
// Latitude, longitude, and elapsedMillis can be empty only for readings that were saved
// before positions were recorded. Those readings came from the old elevation table and have
// just a distance and an elevation.
@Entity(
    tableName = "exercise_track_points",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId")]
)
data class ExerciseTrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    // Latitude and longitude, in degrees.
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Height above sea level in meters. Empty if the GPS didn't report one.
    val elevationMeters: Float? = null,
    // How far along the activity this reading was taken, in meters.
    val distanceMeters: Float,
    // The activity time at this reading, in milliseconds, with paused time left out.
    val elapsedMillis: Long? = null
)

// An exercise the user made themselves. It's offered alongside the built-in library (see
// ExerciseLibrary.custom), and workouts store its name like any other exercise.
@Entity(tableName = "custom_exercises", indices = [Index(value = ["name"], unique = true)])
data class CustomExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ActivityType,
    // For strength exercises: the main muscle, and the ones that assist, as Muscle names
    // separated by commas (empty for none). Both count toward the Home muscle map.
    val mainMuscle: Muscle? = null,
    val secondaryMuscles: String = "",
    // Whether sets can be saved with reps and no weight, like a pull up.
    val bodyweight: Boolean = false
)

// Not a table: this class describes a workout together with its exercises.
data class WorkoutWithExercises(
    @Embedded val workout: Workout,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<Exercise>
)

// ---------------------------------------------------------------------------
// DAO (Data Access Object): the list of things we can ask the database to do.
// ---------------------------------------------------------------------------
@Dao
interface WorkoutDao {

    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Insert
    suspend fun insertExercise(exercise: Exercise): Long

    // Saves changes to an existing exercise (used when its details are edited).
    // Room finds the row by its id.
    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Query("UPDATE workouts SET endTime = :endTime WHERE id = :workoutId")
    suspend fun endWorkout(workoutId: Long, endTime: Long)

    @Query("SELECT startTime FROM workouts")
    suspend fun getWorkoutStartTimes(): List<Long>

    // Null goes back to the name from the time of day.
    @Query("UPDATE workouts SET name = :name WHERE id = :workoutId")
    suspend fun renameWorkout(workoutId: Long, name: String?)

    // Removes one workout by id. Its exercises and sets are deleted too (the foreign keys cascade).
    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkout(workoutId: Long)

    // Removes one exercise from a workout. Its sets are deleted too (the foreign key cascades).
    @Query("DELETE FROM exercises WHERE id = :exerciseId")
    suspend fun deleteExercise(exerciseId: Long)

    @Query("SELECT * FROM workouts WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeActiveWorkout(): Flow<Workout?>

    // The same lookup as above, but a one-time read instead of a live Flow.
    @Query("SELECT * FROM workouts WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveWorkout(): Workout?

    // One workout by id, updating whenever it changes (for example when its duration is edited).
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    fun observeWorkout(workoutId: Long): Flow<Workout?>

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun observeAllWorkouts(): Flow<List<WorkoutWithExercises>>

    // All exercises for one workout, in the order the user arranged them. The id breaks any
    // ties, so exercises with the same position stay in the order they were added.
    @Query("SELECT * FROM exercises WHERE workoutId = :workoutId ORDER BY position, id")
    fun observeExercises(workoutId: Long): Flow<List<Exercise>>

    // The position for an exercise added to the end of this workout's list: one past the
    // current last position, or 0 for an empty workout.
    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM exercises WHERE workoutId = :workoutId")
    suspend fun nextExercisePosition(workoutId: Long): Int

    @Query("SELECT * FROM exercises WHERE id = :exerciseId")
    suspend fun getExercise(exerciseId: Long): Exercise?

    @Query("UPDATE exercises SET supersetGroup = :group WHERE id = :exerciseId")
    suspend fun setSupersetGroup(exerciseId: Long, group: Long?)

    @Query("SELECT * FROM exercises WHERE workoutId = :workoutId ORDER BY position, id")
    suspend fun getExercisesForWorkout(workoutId: Long): List<Exercise>

    // Moves one exercise to a new place in its workout's list.
    @Query("UPDATE exercises SET position = :position WHERE id = :exerciseId")
    suspend fun setExercisePosition(exerciseId: Long, position: Int)

    // How many exercises in this workout have this name. Used to block duplicates.
    @Query("SELECT COUNT(*) FROM exercises WHERE workoutId = :workoutId AND name = :name")
    suspend fun countExercisesNamed(workoutId: Long, name: String): Int

    // Adds a set. Returns the new row's id.
    @Insert
    suspend fun insertSet(exerciseSet: ExerciseSet): Long

    @Insert
    suspend fun insertSets(sets: List<ExerciseSet>)

    // Saves changes to an existing set. Room finds the row by its id.
    @Update
    suspend fun updateSet(exerciseSet: ExerciseSet)

    // Removes one set.
    @Query("DELETE FROM exercise_sets WHERE id = :setId")
    suspend fun deleteSet(setId: Long)

    // Ticks these sets off as done, when finishing a workout with "Tick all".
    @Query("UPDATE exercise_sets SET done = 1 WHERE id IN (:setIds)")
    suspend fun markSetsDone(setIds: List<Long>)

    // Removes these sets, when finishing a workout without its unfinished sets.
    @Query("DELETE FROM exercise_sets WHERE id IN (:setIds)")
    suspend fun deleteSets(setIds: List<Long>)

    // Removes any of these exercises that have no sets left.
    @Query(
        "DELETE FROM exercises WHERE id IN (:exerciseIds) " +
        "AND NOT EXISTS (SELECT 1 FROM exercise_sets s WHERE s.exerciseId = exercises.id)"
    )
    suspend fun deleteExercisesWithoutSets(exerciseIds: List<Long>)

    // After a set is deleted, moves every later set of the same exercise down by one,
    // so the numbers stay 1, 2, 3 with no gaps.
    @Query(
        "UPDATE exercise_sets SET setNumber = setNumber - 1 " +
        "WHERE exerciseId = :exerciseId AND setNumber > :deletedNumber"
    )
    suspend fun shiftSetNumbersDown(exerciseId: Long, deletedNumber: Int)

    // Makes room for a set put back by Undo: moves it and every later set of the exercise up one.
    @Query(
        "UPDATE exercise_sets SET setNumber = setNumber + 1 " +
        "WHERE exerciseId = :exerciseId AND setNumber >= :number"
    )
    suspend fun shiftSetNumbersUp(exerciseId: Long, number: Int)

    // Every set of every exercise in one workout. The inner SELECT finds the ids of
    // the workout's exercises, and ORDER BY keeps each exercise's sets in order.
    @Query(
        "SELECT * FROM exercise_sets " +
        "WHERE exerciseId IN (SELECT id FROM exercises WHERE workoutId = :workoutId) " +
        "ORDER BY exerciseId, setNumber"
    )
    fun observeSetsForWorkout(workoutId: Long): Flow<List<ExerciseSet>>

    // A one-time read of every set in a workout, rather than a live Flow.
    @Query(
        "SELECT * FROM exercise_sets " +
        "WHERE exerciseId IN (SELECT id FROM exercises WHERE workoutId = :workoutId) " +
        "ORDER BY exerciseId, setNumber"
    )
    suspend fun getSetsForWorkout(workoutId: Long): List<ExerciseSet>

    // Deletes every set that has neither weight nor reps, across one workout's exercises.
    // "IS NULL" is how SQL checks for "no value".
    @Query(
        "DELETE FROM exercise_sets " +
        "WHERE weight IS NULL AND reps IS NULL " +
        "AND exerciseId IN (SELECT id FROM exercises WHERE workoutId = :workoutId)"
    )
    suspend fun deleteEmptySets(workoutId: Long)

    // Deletes strength exercises in a workout that have no sets left.
    // Their empty sets are already gone by the time this runs, so "no sets left"
    // means every set was empty.
    @Query(
        "DELETE FROM exercises " +
        "WHERE workoutId = :workoutId AND type = 'STRENGTH' " +
        "AND id NOT IN (SELECT exerciseId FROM exercise_sets)"
    )
    suspend fun deleteStrengthExercisesWithoutSets(workoutId: Long)

    // Deletes run, cardio, and other exercises in a workout where nothing was filled in
    // (no distance, no duration, and no notes).
    @Query(
        "DELETE FROM exercises " +
        "WHERE workoutId = :workoutId AND type != 'STRENGTH' " +
        "AND distance IS NULL AND durationSeconds IS NULL AND notes IS NULL"
    )
    suspend fun deleteEmptyOtherExercises(workoutId: Long)

    // The route picture paths for every exercise in a workout. Used to delete the picture
    // files when the workout is deleted.
    @Query("SELECT routeImagePath FROM exercises WHERE workoutId = :workoutId AND routeImagePath IS NOT NULL")
    suspend fun getRouteImagePaths(workoutId: Long): List<String>

    // The route picture path for one exercise, or null if it has none.
    @Query("SELECT routeImagePath FROM exercises WHERE id = :exerciseId")
    suspend fun getRouteImagePath(exerciseId: Long): String?

    // The track points of one recorded activity, in the order they were taken.
    @Query("SELECT * FROM exercise_track_points WHERE exerciseId = :exerciseId ORDER BY id")
    fun observeTrackPoints(exerciseId: Long): Flow<List<ExerciseTrackPoint>>

    // Every workout's totals, for the Home summary. A set missing its weight or reps adds
    // nothing to the volume. Only the types that track distance ever have one.
    @Query(
        """
        SELECT w.startTime, w.endTime,
            (SELECT SUM(s.weight * s.reps) FROM exercises e
                JOIN exercise_sets s ON s.exerciseId = e.id
                WHERE e.workoutId = w.id AND e.type = 'STRENGTH' AND s.setType != 'WARM_UP') AS volumeKg,
            (SELECT SUM(e.distance) FROM exercises e WHERE e.workoutId = w.id) AS distanceKm,
            (SELECT MAX(s.weight) FROM exercises e
                JOIN exercise_sets s ON s.exerciseId = e.id
                WHERE e.workoutId = w.id AND e.type = 'STRENGTH' AND s.reps > 0 AND s.setType != 'WARM_UP') AS heaviestKg
        FROM workouts w
        """
    )
    fun observeWorkoutStats(): Flow<List<WorkoutStats>>

    // For each strength exercise name, how many sets with a weight or reps entered were done in
    // workouts started since `startMillis`, including one still in progress.
    @Query(
        """
        SELECT e.name AS name, COUNT(s.id) AS sets
        FROM exercise_sets s
        JOIN exercises e ON s.exerciseId = e.id
        JOIN workouts w ON e.workoutId = w.id
        WHERE w.startTime >= :startMillis AND e.type = 'STRENGTH'
            AND (s.weight IS NOT NULL OR s.reps IS NOT NULL) AND s.setType != 'WARM_UP'
        GROUP BY e.name
        """
    )
    fun observeSetCountsSince(startMillis: Long): Flow<List<ExerciseSetCount>>

    // Every set of one exercise with both a weight and reps entered, for its records.
    @Query(
        """
        SELECT s.id AS setId, e.workoutId AS workoutId, w.startTime AS startTime,
            s.setNumber AS setNumber, s.weight AS weightKg, s.reps AS reps
        FROM exercise_sets s
        JOIN exercises e ON s.exerciseId = e.id
        JOIN workouts w ON e.workoutId = w.id
        WHERE e.name = :name AND s.weight > 0 AND s.reps > 0 AND s.setType != 'WARM_UP'
        """
    )
    fun observeStrengthHistory(name: String): Flow<List<StrengthSet>>

    // Every session of one exercise, with its distance and duration, for its records.
    @Query(
        """
        SELECT e.id AS exerciseId, e.workoutId AS workoutId, w.startTime AS startTime,
            e.distance AS distanceKm, e.durationSeconds AS durationSeconds, e.isRecorded AS isRecorded
        FROM exercises e
        JOIN workouts w ON e.workoutId = w.id
        WHERE e.name = :name
        """
    )
    fun observeActivityHistory(name: String): Flow<List<ActivityRow>>

    // Each exercise the user has done, for the list of exercises with records.
    @Query(
        """
        SELECT e.name AS name, e.type AS type, COUNT(DISTINCT e.workoutId) AS workouts,
            MAX(w.startTime) AS lastTime
        FROM exercises e
        JOIN workouts w ON e.workoutId = w.id
        GROUP BY e.name
        ORDER BY e.name COLLATE NOCASE
        """
    )
    fun observeExerciseSummaries(): Flow<List<ExerciseSummary>>

    @Query("SELECT * FROM exercise_track_points WHERE exerciseId = :exerciseId ORDER BY id")
    suspend fun getTrackPoints(exerciseId: Long): List<ExerciseTrackPoint>

    // Adds a list of track points in one go.
    @Insert
    suspend fun insertTrackPoints(points: List<ExerciseTrackPoint>)

    // ----- Everything, for the CSV export -----

    @Query("SELECT * FROM workouts WHERE endTime IS NOT NULL ORDER BY startTime")
    suspend fun getFinishedWorkouts(): List<Workout>

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercises(): List<Exercise>

    @Query("SELECT * FROM exercise_sets")
    suspend fun getAllSets(): List<ExerciseSet>

    // ----- Body weight log -----

    @Query("SELECT * FROM body_weights ORDER BY timeMillis")
    fun observeBodyWeights(): Flow<List<BodyWeight>>

    @Query("SELECT COUNT(*) FROM body_weights")
    suspend fun countBodyWeights(): Int

    @Insert
    suspend fun insertBodyWeight(entry: BodyWeight)

    @Query("DELETE FROM body_weights WHERE timeMillis >= :start AND timeMillis < :end")
    suspend fun deleteBodyWeightsBetween(start: Long, end: Long)

    // Logs a weight, replacing any logged between start and end (the same day).
    @Transaction
    suspend fun replaceBodyWeightForDay(start: Long, end: Long, entry: BodyWeight) {
        deleteBodyWeightsBetween(start, end)
        insertBodyWeight(entry)
    }

    // ----- Fastest times of recordings -----

    @Query("SELECT * FROM best_efforts WHERE exerciseId = :exerciseId")
    suspend fun getBestEfforts(exerciseId: Long): List<BestEffort>

    @Query("SELECT bestEffortsSaved FROM exercises WHERE id = :exerciseId")
    suspend fun areBestEffortsSaved(exerciseId: Long): Boolean?

    // Saves a recording's fastest times and marks them worked out, together.
    @Transaction
    suspend fun saveBestEfforts(exerciseId: Long, efforts: List<BestEffort>) {
        insertBestEfforts(efforts)
        markBestEffortsSaved(exerciseId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBestEfforts(efforts: List<BestEffort>)

    @Query("UPDATE exercises SET bestEffortsSaved = 1 WHERE id = :exerciseId")
    suspend fun markBestEffortsSaved(exerciseId: Long)

    // Recordings saved before fastest times were stored, still to be worked out.
    @Query("SELECT id FROM exercises WHERE isRecorded = 1 AND bestEffortsSaved = 0")
    suspend fun getRecordingsWithoutBestEfforts(): List<Long>

    // ----- Record days -----

    // Every strength set with a weight and reps, with its exercise's name.
    @Query(
        """
        SELECT e.name AS name, s.id AS setId, e.workoutId AS workoutId, w.startTime AS startTime,
            s.setNumber AS setNumber, s.weight AS weightKg, s.reps AS reps
        FROM exercise_sets s
        JOIN exercises e ON s.exerciseId = e.id
        JOIN workouts w ON e.workoutId = w.id
        WHERE e.type = 'STRENGTH' AND s.weight > 0 AND s.reps > 0 AND s.setType != 'WARM_UP'
        """
    )
    fun observeAllStrengthSets(): Flow<List<NamedStrengthSet>>

    // Every distance or time exercise, with its name and type.
    @Query(
        """
        SELECT e.name AS name, e.type AS type, e.id AS exerciseId, e.workoutId AS workoutId,
            w.startTime AS startTime, e.distance AS distanceKm, e.durationSeconds AS durationSeconds,
            e.isRecorded AS isRecorded
        FROM exercises e
        JOIN workouts w ON e.workoutId = w.id
        WHERE e.type != 'STRENGTH'
        """
    )
    fun observeAllActivityRows(): Flow<List<NamedActivityRow>>

    // The latest exercise with this name in a workout that started before this one, or null.
    // Its numbers are shown faintly in empty boxes.
    @Query(
        "SELECT e.* FROM exercises e JOIN workouts w ON w.id = e.workoutId " +
        "WHERE e.name = :name AND e.workoutId != :workoutId " +
        "AND w.startTime < (SELECT startTime FROM workouts WHERE id = :workoutId) " +
        "ORDER BY w.startTime DESC LIMIT 1"
    )
    suspend fun getPreviousExercise(name: String, workoutId: Long): Exercise?

    @Query("SELECT * FROM exercise_sets WHERE exerciseId = :exerciseId ORDER BY setNumber")
    suspend fun getSetsForExercise(exerciseId: Long): List<ExerciseSet>

    // Used when an exercise switches between RPE and RIR.
    @Query("UPDATE exercise_sets SET effort = NULL WHERE exerciseId = :exerciseId")
    suspend fun clearEffortForExercise(exerciseId: Long)

    // --- Programs ---

    @Insert
    suspend fun insertProgram(program: Program): Long

    @Update
    suspend fun updateProgram(program: Program)

    // Cascades to the program's exercises and sets.
    @Query("DELETE FROM programs WHERE id = :programId")
    suspend fun deleteProgram(programId: Long)

    // Every program with its exercise count, alphabetically.
    @Query(
        "SELECT p.id, p.name, " +
        "(SELECT COUNT(*) FROM program_exercises pe WHERE pe.programId = p.id) AS exerciseCount " +
        "FROM programs p ORDER BY p.name COLLATE NOCASE"
    )
    fun observePrograms(): Flow<List<ProgramSummary>>

    @Query("SELECT * FROM programs WHERE id = :programId")
    suspend fun getProgram(programId: Long): Program?

    @Query("SELECT * FROM program_exercises WHERE programId = :programId ORDER BY position, id")
    suspend fun getProgramExercises(programId: Long): List<ProgramExercise>

    // Every set of every exercise in one program.
    @Query(
        "SELECT ps.* FROM program_sets ps " +
        "JOIN program_exercises pe ON pe.id = ps.programExerciseId " +
        "WHERE pe.programId = :programId ORDER BY ps.setNumber"
    )
    suspend fun getSetsForProgram(programId: Long): List<ProgramSet>

    @Insert
    suspend fun insertProgramExercise(exercise: ProgramExercise): Long

    @Insert
    suspend fun insertProgramSets(sets: List<ProgramSet>)

    // Cascades to their sets.
    @Query("DELETE FROM program_exercises WHERE programId = :programId")
    suspend fun deleteProgramExercises(programId: Long)

    // ---- Custom exercises ----

    @Query("SELECT * FROM custom_exercises ORDER BY name")
    fun observeCustomExercises(): Flow<List<CustomExercise>>

    @Insert
    suspend fun insertCustomExercise(exercise: CustomExercise): Long

    @Update
    suspend fun updateCustomExercise(exercise: CustomExercise)

    @Query("DELETE FROM custom_exercises WHERE id = :id")
    suspend fun deleteCustomExercise(id: Long)

    // Renames an exercise in every workout and program that has it.
    @Query("UPDATE exercises SET name = :newName WHERE name = :oldName")
    suspend fun renameWorkoutExercises(oldName: String, newName: String)

    @Query("UPDATE program_exercises SET name = :newName WHERE name = :oldName")
    suspend fun renameProgramExercises(oldName: String, newName: String)
}

// ---------------------------------------------------------------------------
// DATABASE: ties the tables and the DAO together.
// ---------------------------------------------------------------------------
// The database's version. Raise it, and add a migration below, whenever the tables change.
const val DB_VERSION = 21
// The oldest version a backup can be restored from: the oldest the upgrade tests cover.
const val MIN_RESTORABLE_DB_VERSION = 16

// Whenever the tables change, the version goes up, and a Migration (below) tells Room how to
// upgrade existing data.
@Database(
    entities = [
        Workout::class,
        Exercise::class,
        ExerciseSet::class,
        ExerciseTrackPoint::class,
        Program::class,
        ProgramExercise::class,
        ProgramSet::class,
        CustomExercise::class,
        BestEffort::class,
        BodyWeight::class
    ],
    version = DB_VERSION,
    exportSchema = true
)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var instance: WorkoutDatabase? = null

        // Closes the database so its file can be replaced (by a restore). The next getInstance
        // opens it again.
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        // Upgrades a version 1 database (no sets table) to version 2, keeping all saved data.
        // This SQL must describe the table exactly as Room expects, or Room will refuse to open it.
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_sets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, " +
                        "`setNumber` INTEGER NOT NULL, " +
                        "`weight` REAL, " +
                        "`reps` INTEGER, " +
                        "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exercise_sets_exerciseId` " +
                        "ON `exercise_sets` (`exerciseId`)"
                )
                // Give every strength exercise that was already saved a first, empty set.
                db.execSQL(
                    "INSERT INTO exercise_sets (exerciseId, setNumber) " +
                        "SELECT id, 1 FROM exercises WHERE type = 'STRENGTH'"
                )
            }
        }

        // Upgrades version 2 to version 3 for the run, cardio, and other details.
        // The old "details" column (which was never used) becomes "notes", and two new
        // columns are added. Existing exercises keep all their data.
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises RENAME COLUMN details TO notes")
                db.execSQL("ALTER TABLE exercises ADD COLUMN distance REAL")
                db.execSQL("ALTER TABLE exercises ADD COLUMN durationSeconds INTEGER")
            }
        }

        // Upgrades version 3 to version 4: adds the column that holds the path of a recorded
        // activity's route picture. Existing exercises simply have no picture.
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN routeImagePath TEXT")
            }
        }

        // Upgrades version 4 to version 5: adds the flag that marks recorded activities, and
        // sets it for the recorded activities that were saved before the flag existed.
        // Those are the ones with a route picture, plus the ones named after a recording
        // activity ("Run", "Walk", "Bike", "Timer"). None of the exercises you can pick from the
        // search list have those exact names.
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN isRecorded INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE exercises SET isRecorded = 1 WHERE routeImagePath IS NOT NULL " +
                        "OR (name = 'Run' AND type = 'RUN') " +
                        "OR (name = 'Walk' AND type = 'WALK') " +
                        "OR (name = 'Bike' AND type = 'BIKE') " +
                        "OR (name = 'Timer' AND type = 'OTHER')"
                )
            }
        }

        // Upgrades version 5 to version 6: adds the table that used to hold each recorded activity's
        // splits. The app no longer uses that table, but this step has to stay so that a database
        // on an older version can still be upgraded one step at a time.
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_splits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, " +
                        "`number` INTEGER NOT NULL, " +
                        "`distanceMeters` REAL NOT NULL, " +
                        "`durationMillis` INTEGER NOT NULL, " +
                        "`isPartial` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exercise_splits_exerciseId` " +
                        "ON `exercise_splits` (`exerciseId`)"
                )
            }
        }

        // Upgrades version 6 to version 7: adds the table that holds each recorded activity's
        // elevation readings. Nothing is removed or changed. Existing activities simply have
        // no elevation readings.
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_elevation` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, " +
                        "`distanceMeters` REAL NOT NULL, " +
                        "`elevationMeters` REAL NOT NULL, " +
                        "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exercise_elevation_exerciseId` " +
                        "ON `exercise_elevation` (`exerciseId`)"
                )
            }
        }

        // Upgrades version 7 to version 8: elevation and position are now stored together, in one
        // table of track points. Every reading in the old elevation table is COPIED into the new
        // one, with its distance and elevation. The old table is left exactly as it was, and
        // nothing is deleted. Copied readings have no position or time, because those weren't
        // recorded back then.
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_track_points` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, " +
                        "`latitude` REAL, " +
                        "`longitude` REAL, " +
                        "`elevationMeters` REAL, " +
                        "`distanceMeters` REAL NOT NULL, " +
                        "`elapsedMillis` INTEGER, " +
                        "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exercise_track_points_exerciseId` " +
                        "ON `exercise_track_points` (`exerciseId`)"
                )
                // ORDER BY id keeps the readings in the order they were taken.
                db.execSQL(
                    "INSERT INTO exercise_track_points (exerciseId, elevationMeters, distanceMeters) " +
                        "SELECT exerciseId, elevationMeters, distanceMeters " +
                        "FROM exercise_elevation ORDER BY id"
                )
            }
        }

        // Upgrades version 8 to version 9. The old way of storing splits has been removed from the
        // app, and this version only records that. NOTHING in the database is changed or deleted:
        // the old "exercise_splits" table and its rows stay in the file, unused. Room needs the
        // version number to go up whenever the list of tables it manages changes, even when no data
        // has to move, and this empty migration is how it's told there's nothing to do.
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Intentionally empty.
            }
        }

        // Upgrades version 9 to version 10: adds the column that holds each exercise's place in
        // its workout's list. Every existing exercise gets its own id as its position, which
        // keeps the order they were added in, the same order they were shown in before.
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE exercises SET position = id")
            }
        }

        // Upgrades version 10 to version 11 for workout programs. Adds the three program tables,
        // plus an effort type for each exercise and an effort number for each set. Nothing
        // existing is changed: every existing exercise is marked RPE and its sets have no effort.
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `programs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `program_exercises` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`programId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`effortType` TEXT NOT NULL, " +
                        "`distance` REAL, " +
                        "`durationSeconds` INTEGER, " +
                        "`notes` TEXT, " +
                        "FOREIGN KEY(`programId`) REFERENCES `programs`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_program_exercises_programId` " +
                        "ON `program_exercises` (`programId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `program_sets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`programExerciseId` INTEGER NOT NULL, " +
                        "`setNumber` INTEGER NOT NULL, " +
                        "`reps` INTEGER, " +
                        "`effort` REAL, " +
                        "FOREIGN KEY(`programExerciseId`) REFERENCES `program_exercises`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_program_sets_programExerciseId` " +
                        "ON `program_sets` (`programExerciseId`)"
                )
                // A new NOT NULL column needs a value for the rows already there.
                db.execSQL("ALTER TABLE exercises ADD COLUMN effortType TEXT NOT NULL DEFAULT 'RPE'")
                db.execSQL("ALTER TABLE exercise_sets ADD COLUMN effort REAL")
            }
        }

        // Upgrades version 11 to version 12 for workout names. Existing workouts get none, so
        // they show the name from the time of day they started.
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN name TEXT")
            }
        }

        // Upgrades version 12 to version 13 when the exercise library took the Hevy app's names.
        // Exercises already saved, in workouts and programs, are renamed to match, so they keep
        // their history and records.
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for ((old, new) in ExerciseLibrary.LIBRARY_RENAMES) {
                    db.execSQL("UPDATE exercises SET name = ? WHERE name = ?", arrayOf(new, old))
                    db.execSQL("UPDATE program_exercises SET name = ? WHERE name = ?", arrayOf(new, old))
                }
            }
        }

        // Upgrades version 13 to version 14, for the second set of renames to the Hevy app's
        // names. Saved exercises in workouts and programs are renamed to match.
        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for ((old, new) in ExerciseLibrary.CATALOG_RENAMES) {
                    db.execSQL("UPDATE exercises SET name = ? WHERE name = ?", arrayOf(new, old))
                    db.execSQL("UPDATE program_exercises SET name = ? WHERE name = ?", arrayOf(new, old))
                }
            }
        }

        // Adds the table of exercises the user makes themselves.
        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_exercises` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, " +
                            "`mainMuscle` TEXT, " +
                            "`secondaryMuscles` TEXT NOT NULL, " +
                            "`bodyweight` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_exercises_name` ON `custom_exercises` (`name`)"
                )
            }
        }

        // Adds the check box to each set. Sets in workouts already saved count as done.
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_sets ADD COLUMN done INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE exercise_sets SET done = 1 WHERE exerciseId IN " +
                            "(SELECT e.id FROM exercises e JOIN workouts w ON w.id = e.workoutId WHERE w.endTime IS NOT NULL)"
                )
            }
        }

        // Reps can be halves (8.5). SQLite can't change a column's type, so the two set tables are
        // rebuilt with reps as REAL and every row copied across unchanged.
        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `exercise_sets_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`exerciseId` INTEGER NOT NULL, " +
                            "`setNumber` INTEGER NOT NULL, " +
                            "`weight` REAL, " +
                            "`reps` REAL, " +
                            "`effort` REAL, " +
                            "`done` INTEGER NOT NULL DEFAULT 0, " +
                            "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO exercise_sets_new (id, exerciseId, setNumber, weight, reps, effort, done) " +
                            "SELECT id, exerciseId, setNumber, weight, reps, effort, done FROM exercise_sets"
                )
                db.execSQL("DROP TABLE exercise_sets")
                db.execSQL("ALTER TABLE exercise_sets_new RENAME TO exercise_sets")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exercise_sets_exerciseId` ON `exercise_sets` (`exerciseId`)"
                )

                db.execSQL(
                    "CREATE TABLE `program_sets_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`programExerciseId` INTEGER NOT NULL, " +
                            "`setNumber` INTEGER NOT NULL, " +
                            "`reps` REAL, " +
                            "`effort` REAL, " +
                            "FOREIGN KEY(`programExerciseId`) REFERENCES `program_exercises`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO program_sets_new (id, programExerciseId, setNumber, reps, effort) " +
                            "SELECT id, programExerciseId, setNumber, reps, effort FROM program_sets"
                )
                db.execSQL("DROP TABLE program_sets")
                db.execSQL("ALTER TABLE program_sets_new RENAME TO program_sets")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_program_sets_programExerciseId` " +
                            "ON `program_sets` (`programExerciseId`)"
                )
            }
        }

        // Adds the table of each recording's fastest times, and the flag saying they've been worked
        // out. Recordings already saved get theirs worked out once, the next time the app opens.
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN bestEffortsSaved INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `best_efforts` (" +
                            "`exerciseId` INTEGER NOT NULL, " +
                            "`distanceKm` INTEGER NOT NULL, " +
                            "`millis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`exerciseId`, `distanceKm`), " +
                            "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
            }
        }

        // Adds the kind of each set (normal, warm-up, drop, failure). Every set saved so far is normal.
        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_sets ADD COLUMN setType TEXT NOT NULL DEFAULT 'NORMAL'")
                db.execSQL("ALTER TABLE program_sets ADD COLUMN setType TEXT NOT NULL DEFAULT 'NORMAL'")
            }
        }

        // Adds the body weight log. It starts empty; the weight on Profile is added to it the first
        // time Track Weight is on.
        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_weights` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`timeMillis` INTEGER NOT NULL, " +
                            "`weightKg` REAL NOT NULL)"
                )
            }
        }

        // Adds supersets to workout and program exercises. Every exercise so far is on its own.
        internal val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN supersetGroup INTEGER")
                db.execSQL("ALTER TABLE program_exercises ADD COLUMN supersetGroup INTEGER")
            }
        }

        // Every upgrade, oldest first. The upgrade tests run these too.
        internal val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21
        )

        fun getInstance(context: Context): WorkoutDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkoutDatabase::class.java,
                    "fitness.db"
                )
                    // Tells Room how to upgrade from each older version.
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
