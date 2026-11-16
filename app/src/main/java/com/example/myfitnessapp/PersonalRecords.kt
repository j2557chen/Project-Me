package com.example.myfitnessapp

import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// Personal records. Everything here is worked out from an exercise's history, so nothing extra
// is stored. A set or activity "sets a record" when it beats everything done before it, in
// earlier workouts and earlier in the same workout, and an earlier workout exists to beat.
// Ties don't count: the first to reach a value holds the record.
// ---------------------------------------------------------------------------

// One strength set with a weight and reps both entered. Weights are in kilograms.
data class StrengthSet(
    val setId: Long,
    val workoutId: Long,
    val startTime: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Double
) {
    val volumeKg: Double get() = weightKg * reps
}

// One session of a distance or time exercise. `efforts` holds the fastest time, in
// milliseconds, for each record distance in kilometers that the session covered.
data class ActivitySession(
    val exerciseId: Long,
    val workoutId: Long,
    val startTime: Long,
    val distanceKm: Double?,
    val durationSeconds: Long?,
    val efforts: Map<Int, Long> = emptyMap()
)

// Oldest first: by workout start, then the order within the workout.
private val strengthOrder = compareBy<StrengthSet>({ it.startTime }, { it.workoutId }, { it.setNumber })
private val activityOrder = compareBy<ActivitySession>({ it.startTime }, { it.workoutId }, { it.exerciseId })

// ----- Fastest times -------------------------------------------------------------------------

// Library walks and rides filed under Cardio, which get fastest times like the Run, Walk and
// Bike types do.
private val WALKS_AND_RIDES = setOf(
    "Walking", "Incline Treadmill Walk", "Hiking",
    "Cycling", "Stationary Bike", "Recumbent Bike", "Spinning", "Air Bike"
)

fun tracksFastestTimes(name: String, type: ActivityType): Boolean =
    type == ActivityType.RUN || type == ActivityType.WALK || type == ActivityType.BIKE || name in WALKS_AND_RIDES

// The record distances up to `maxKm`: 3, 5, 10, 12 and 15 km, then every 5 km after that.
fun effortDistancesKm(maxKm: Double): List<Int> =
    (listOf(3, 5, 10, 12) + generateSequence(15) { it + 5 }.takeWhile { it <= maxKm + 1e-9 })
        .filter { it <= maxKm + 1e-9 }

// The fastest time over each distance, anywhere within a GPS recording. Each stretch is
// exactly the distance: its start time is worked out between the two readings it falls in.
fun bestEfforts(points: List<TrackPoint>, distancesKm: List<Int>): Map<Int, Long> {
    // Keep readings that move forward in both distance and time.
    val track = mutableListOf<TrackPoint>()
    for (p in points) {
        val last = track.lastOrNull()
        if (last == null || (p.distanceMeters >= last.distanceMeters && p.elapsedMillis >= last.elapsedMillis)) track += p
    }
    if (track.size < 2) return emptyMap()

    val results = mutableMapOf<Int, Long>()
    for (km in distancesKm) {
        val target = km * 1000.0
        var best = Long.MAX_VALUE
        var i = 0
        for (j in track.indices) {
            val startDistance = track[j].distanceMeters - target
            if (startDistance < track[0].distanceMeters) continue
            // Move i to the last reading at or before where this stretch starts.
            while (i + 1 < j && track[i + 1].distanceMeters <= startDistance) i++
            val a = track[i]
            val b = track[i + 1]
            val span = b.distanceMeters - a.distanceMeters
            val startTime = if (span <= 0f) b.elapsedMillis.toDouble()
            else a.elapsedMillis + (b.elapsedMillis - a.elapsedMillis) * ((startDistance - a.distanceMeters) / span)
            val time = (track[j].elapsedMillis - startTime).toLong()
            if (time in 1 until best) best = time
        }
        if (best != Long.MAX_VALUE) results[km] = best
    }
    return results
}

// A logged activity has no readings, so its time only counts for a record distance that it
// matches: no more than 2% over it, such as 5.0 to 5.1 km for 5 km.
fun manualEfforts(distanceKm: Double?, durationSeconds: Long?): Map<Int, Long> {
    if (distanceKm == null || durationSeconds == null || durationSeconds <= 0) return emptyMap()
    return effortDistancesKm(distanceKm)
        .filter { distanceKm <= it * 1.02 }
        .associateWith { durationSeconds * 1000 }
}

// The fastest times of one session. A recording's come from its GPS readings: they're worked
// out once, when it's saved, and kept in the database (recordings saved before that are done
// the first time they're needed). A logged activity's come from its distance and duration,
// which can be edited, so they're worked out each time.
object BestEffortCache {
    private val recorded = ConcurrentHashMap<Long, Map<Int, Long>>()

    suspend fun efforts(dao: WorkoutDao, row: ActivityRow): Map<Int, Long> {
        if (!row.isRecorded) return manualEfforts(row.distanceKm, row.durationSeconds)
        return recorded[row.exerciseId] ?: savedOrWorkedOut(dao, row.exerciseId).also { recorded[row.exerciseId] = it }
    }

    private suspend fun savedOrWorkedOut(dao: WorkoutDao, exerciseId: Long): Map<Int, Long> =
        if (dao.areBestEffortsSaved(exerciseId) == true) {
            dao.getBestEfforts(exerciseId).associate { it.distanceKm to it.millis }
        } else {
            saveBestEfforts(dao, exerciseId, dao.getTrackPoints(exerciseId).mapNotNull { it.toTrackPointOrNull() })
        }

    // Works out a recording's fastest times from its readings and stores them.
    suspend fun saveBestEfforts(dao: WorkoutDao, exerciseId: Long, points: List<TrackPoint>): Map<Int, Long> {
        val maxKm = (points.lastOrNull()?.distanceMeters ?: 0f) / 1000.0
        val efforts = bestEfforts(points, effortDistancesKm(maxKm))
        dao.saveBestEfforts(exerciseId, efforts.map { (km, ms) -> BestEffort(exerciseId, km, ms) })
        return efforts
    }

    // Stores the fastest times of recordings saved before they were kept in the database.
    suspend fun catchUp(dao: WorkoutDao) {
        for (id in dao.getRecordingsWithoutBestEfforts()) {
            recorded[id] = savedOrWorkedOut(dao, id)
        }
    }

    suspend fun session(dao: WorkoutDao, row: ActivityRow, withEfforts: Boolean) = ActivitySession(
        exerciseId = row.exerciseId,
        workoutId = row.workoutId,
        startTime = row.startTime,
        distanceKm = row.distanceKm,
        durationSeconds = row.durationSeconds,
        efforts = if (withEfforts) efforts(dao, row) else emptyMap()
    )
}

// ----- Strength ------------------------------------------------------------------------------

// The volume of one exercise in one workout.
data class SessionVolume(val workoutId: Long, val startTime: Long, val volumeKg: Double)

data class StrengthRecords(
    val heaviest: StrengthSet?,
    val bestSet: StrengthSet?,
    val bestSession: SessionVolume?
)

private fun sessionVolumes(history: List<StrengthSet>): List<SessionVolume> =
    history.groupBy { it.workoutId }
        .map { (workoutId, sets) -> SessionVolume(workoutId, sets.first().startTime, sets.sumOf { it.volumeKg }) }
        .sortedWith(compareBy({ it.startTime }, { it.workoutId }))

// The first set or session to reach the top value holds the record.
private fun <T> firstBest(items: List<T>, value: (T) -> Double): T? {
    var best: T? = null
    for (item in items) if (best == null || value(item) > value(best)) best = item
    return best
}

fun strengthRecords(history: List<StrengthSet>): StrengthRecords {
    val sets = history.sortedWith(strengthOrder)
    return StrengthRecords(
        heaviest = firstBest(sets) { it.weightKg },
        bestSet = firstBest(sets) { it.volumeKg },
        bestSession = firstBest(sessionVolumes(sets)) { it.volumeKg }
    )
}

// Which sets of one workout set a record (heaviest weight or best set volume), and whether
// the workout's volume for the exercise did.
data class StrengthPrFlags(val prSetIds: Set<Long> = emptySet(), val sessionVolumePr: Boolean = false)

fun strengthPrFlags(history: List<StrengthSet>, workoutId: Long): StrengthPrFlags {
    val sets = history.sortedWith(strengthOrder)
    val mine = sets.filter { it.workoutId == workoutId }
    val first = mine.firstOrNull() ?: return StrengthPrFlags()
    val earlier = sets.filter { strengthOrder.compare(it, first) < 0 && it.workoutId != workoutId }
    if (earlier.isEmpty()) return StrengthPrFlags()

    var bestWeight = earlier.maxOf { it.weightKg }
    var bestVolume = earlier.maxOf { it.volumeKg }
    val prSets = mutableSetOf<Long>()
    for (set in mine) {
        if (set.weightKg > bestWeight || set.volumeKg > bestVolume) prSets += set.setId
        bestWeight = maxOf(bestWeight, set.weightKg)
        bestVolume = maxOf(bestVolume, set.volumeKg)
    }

    val earlierWorkouts = earlier.map { it.workoutId }.toSet()
    val sessions = sessionVolumes(sets)
    val bestEarlierSession = sessions.filter { it.workoutId in earlierWorkouts }.maxOf { it.volumeKg }
    val thisSession = sessions.first { it.workoutId == workoutId }.volumeKg
    return StrengthPrFlags(prSets, thisSession > bestEarlierSession)
}

// What one workout beat for one strength exercise, compared with every earlier workout: the
// heaviest weight, the best single set (weight times reps), and the most volume in a workout.
// Each is null when it wasn't beaten. Used for the celebration when a workout is finished.
data class StrengthBeaten(val heaviestKg: Double?, val bestSet: StrengthSet?, val sessionKg: Double?)

// Null when nothing was beaten, or when this is the first time the exercise was done (there's
// nothing to beat yet).
fun strengthBeatenIn(history: List<StrengthSet>, workoutId: Long): StrengthBeaten? {
    val sets = history.sortedWith(strengthOrder)
    val mine = sets.filter { it.workoutId == workoutId }
    val first = mine.firstOrNull() ?: return null
    val earlier = sets.filter { strengthOrder.compare(it, first) < 0 && it.workoutId != workoutId }
    if (earlier.isEmpty()) return null

    val heaviest = mine.maxOf { it.weightKg }.takeIf { it > earlier.maxOf { s -> s.weightKg } }
    val bestSet = mine.maxBy { it.volumeKg }.takeIf { it.volumeKg > earlier.maxOf { s -> s.volumeKg } }
    val session = mine.sumOf { it.volumeKg }.takeIf { it > sessionVolumes(earlier).maxOf { s -> s.volumeKg } }
    if (heaviest == null && bestSet == null && session == null) return null
    return StrengthBeaten(heaviest, bestSet, session)
}

// ----- Distance and time ---------------------------------------------------------------------

// A fastest time over one distance, and the session it came from.
data class FastestTime(val distanceKm: Int, val millis: Long, val session: ActivitySession)

data class ActivityRecords(
    val longestDistance: ActivitySession?,
    val longestTime: ActivitySession?,
    val fastestTimes: List<FastestTime>
)

fun activityRecords(history: List<ActivitySession>): ActivityRecords {
    val sessions = history.sortedWith(activityOrder)
    val fastest = sessions.flatMap { s -> s.efforts.map { (km, ms) -> FastestTime(km, ms, s) } }
        .groupBy { it.distanceKm }
        // The quickest; the earliest wins a tie, since the list is oldest first.
        .map { (_, times) -> times.minBy { it.millis } }
        .sortedBy { it.distanceKm }
    return ActivityRecords(
        longestDistance = firstBest(sessions.filter { (it.distanceKm ?: 0.0) > 0 }) { it.distanceKm!! },
        longestTime = firstBest(sessions.filter { (it.durationSeconds ?: 0) > 0 }) { it.durationSeconds!!.toDouble() },
        fastestTimes = fastest
    )
}

// The records one session set: farthest, longest, and the distances it was fastest over.
data class ActivityPrFlags(
    val distancePr: Boolean = false,
    val timePr: Boolean = false,
    val fastestKm: List<Int> = emptyList()
)

fun activityPrFlags(history: List<ActivitySession>, exerciseId: Long): ActivityPrFlags {
    val sessions = history.sortedWith(activityOrder)
    val me = sessions.firstOrNull { it.exerciseId == exerciseId } ?: return ActivityPrFlags()
    val earlier = sessions.filter { activityOrder.compare(it, me) < 0 }
    if (earlier.isEmpty()) return ActivityPrFlags()

    fun beats(mine: Double?, theirs: List<Double>) =
        mine != null && mine > 0 && theirs.isNotEmpty() && mine > theirs.max()

    return ActivityPrFlags(
        distancePr = beats(me.distanceKm, earlier.mapNotNull { it.distanceKm }.filter { it > 0 }),
        timePr = beats(me.durationSeconds?.toDouble(), earlier.mapNotNull { it.durationSeconds?.toDouble() }.filter { it > 0 }),
        fastestKm = me.efforts.filter { (km, ms) ->
            val previous = earlier.mapNotNull { it.efforts[km] }
            previous.isNotEmpty() && ms < previous.min()
        }.keys.sorted()
    )
}

// ----- Record days ---------------------------------------------------------------------------

// How many records each workout set, by the same rules as the badges on its exercises: one
// for each set that beat the heaviest weight or best set volume, and one for a best session
// volume, for strength; one each for farthest, longest and every fastest distance for the
// others (distance and time only count for types that track them). Workouts with none are left
// out.
fun prCountsByWorkout(
    strength: Map<String, List<StrengthSet>>,
    activities: Map<String, Pair<ActivityType, List<ActivitySession>>>
): Map<Long, Int> {
    val counts = mutableMapOf<Long, Int>()
    fun add(workoutId: Long, records: Int) {
        if (records > 0) counts[workoutId] = (counts[workoutId] ?: 0) + records
    }
    for (history in strength.values) {
        for (workoutId in history.map { it.workoutId }.toSet()) {
            val flags = strengthPrFlags(history, workoutId)
            add(workoutId, flags.prSetIds.size + if (flags.sessionVolumePr) 1 else 0)
        }
    }
    for ((type, history) in activities.values) {
        for (session in history) {
            val flags = activityPrFlags(history, session.exerciseId)
            add(
                session.workoutId,
                (if (flags.distancePr && TrackedField.DISTANCE in type.tracks) 1 else 0) +
                    (if (flags.timePr && TrackedField.DURATION in type.tracks) 1 else 0) +
                    flags.fastestKm.size
            )
        }
    }
    return counts
}

// The workouts in which any record was set.
fun prWorkoutIds(
    strength: Map<String, List<StrengthSet>>,
    activities: Map<String, Pair<ActivityType, List<ActivitySession>>>
): Set<Long> = prCountsByWorkout(strength, activities).keys
