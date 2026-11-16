package com.example.myfitnessapp

import android.content.Context
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------------------
// Health Connect is Android's shared store of health data. Watches (Pixel Watch and Fitbit,
// Galaxy Watch through Samsung Health, Garmin through Garmin Connect, and others) sync their
// workouts and heart rate into it, and this app reads them from there. It only reads; nothing
// is written back.
// ---------------------------------------------------------------------------

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val HC_PREFS = "health_connect"
private const val KEY_LAST_SYNC = "last_sync"

// What the app asks Health Connect for.
val HC_PERMISSIONS: Set<String> = setOf(
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class)
)

enum class HealthConnectStatus {
    // The phone can't run Health Connect at all.
    NOT_SUPPORTED,
    // Android 12 or 13 without the Health Connect app, or with one too old.
    NEEDS_INSTALL,
    // Available, but this app hasn't been given permission yet.
    NOT_CONNECTED,
    CONNECTED
}

suspend fun healthConnectStatus(context: Context): HealthConnectStatus {
    return when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> {
            val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            if (granted.containsAll(HC_PERMISSIONS)) HealthConnectStatus.CONNECTED else HealthConnectStatus.NOT_CONNECTED
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.NEEDS_INSTALL
        else -> HealthConnectStatus.NOT_SUPPORTED
    }
}

// When the last sync finished, or null if there hasn't been one.
fun lastWatchSync(context: Context): Long? {
    val prefs = context.getSharedPreferences(HC_PREFS, Context.MODE_PRIVATE)
    return if (prefs.contains(KEY_LAST_SYNC)) prefs.getLong(KEY_LAST_SYNC, 0) else null
}

// ---------------------------------------------------------------------------
// Watch workouts
// ---------------------------------------------------------------------------

// A workout as a watch recorded it, without anything from Health Connect's own classes, so the
// mapping below can be tested on its own.
data class WatchSession(
    val exerciseType: Int,
    val title: String?,
    val startMillis: Long,
    val endMillis: Long,
    val distanceKm: Double?
)

// The library exercise each kind of watch workout is saved as. Kinds not listed here (strength
// training and weightlifting among them) aren't imported: a watch doesn't know the sets, and
// those are logged in the app. Their heart rate still shows on the app's own workout.
private val WATCH_EXERCISE_NAMES: Map<Int, String> = mapOf(
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "Running",
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to "Treadmill",
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "Walking",
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING to "Hiking",
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "Cycling",
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to "Stationary Bike",
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to "Swimming",
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to "Swimming",
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE to "Rowing Machine",
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL to "Elliptical Trainer",
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE to "Stair Master",
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA to "Yoga"
)

// The workout a watch session is saved as: one exercise holding its distance and time. Null for
// a kind of session that isn't imported.
fun watchSessionToWorkout(session: WatchSession): ImportedWorkout? {
    val name = WATCH_EXERCISE_NAMES[session.exerciseType] ?: return null
    val exercise = ImportedExercise(
        name = name,
        type = libraryExerciseFor(name)?.type ?: ActivityType.CARDIO,
        sets = emptyList(),
        distanceKm = session.distanceKm?.takeIf { it > 0 },
        durationSeconds = ((session.endMillis - session.startMillis) / 1000).coerceAtLeast(0),
        notes = null
    )
    return ImportedWorkout(
        // A blank title falls back to the time-of-day name, like the app's own workouts.
        name = session.title?.trim()?.ifEmpty { null },
        startTime = session.startMillis,
        endTime = session.endMillis,
        exercises = listOf(exercise)
    )
}

// Brings in the watch workouts Health Connect has. It reads from 30 days before the last sync (two
// years back the first time), so a workout a watch synced days late is still caught. A workout starting at
// the same minute as one already saved is skipped (see saveImportedWorkouts), so syncing again
// doesn't double anything.
suspend fun syncWatchWorkouts(context: Context, db: WorkoutDatabase): ImportResult {
    val client = HealthConnectClient.getOrCreate(context)
    val now = Instant.now()
    val from = lastWatchSync(context)?.let { Instant.ofEpochMilli(it).minus(30, ChronoUnit.DAYS) }
        ?: now.minus(730, ChronoUnit.DAYS)

    val sessions = mutableListOf<ExerciseSessionRecord>()
    var pageToken: String? = null
    do {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, now),
                pageToken = pageToken
            )
        )
        sessions += response.records
        pageToken = response.pageToken
    } while (pageToken != null)

    val workouts = sessions
        .filter { it.exerciseType in WATCH_EXERCISE_NAMES }
        .mapNotNull { record ->
            val distance = sessionDistanceKm(client, record)
            watchSessionToWorkout(
                WatchSession(
                    exerciseType = record.exerciseType,
                    title = record.title,
                    startMillis = record.startTime.toEpochMilli(),
                    endMillis = record.endTime.toEpochMilli(),
                    distanceKm = distance
                )
            )
        }
        .sortedBy { it.startTime }

    val result = saveImportedWorkouts(db, workouts)
    context.getSharedPreferences(HC_PREFS, Context.MODE_PRIVATE).edit {
        putLong(KEY_LAST_SYNC, now.toEpochMilli())
    }
    return result
}

// How far a session went: the distance records the same app (the watch's) saved during it,
// added up. Using one app's records keeps a phone's and a watch's distance for the same run
// from being counted twice. If that app saved none, Health Connect's own total is used.
private suspend fun sessionDistanceKm(client: HealthConnectClient, session: ExerciseSessionRecord): Double? {
    val window = TimeRangeFilter.between(session.startTime, session.endTime)
    val records = mutableListOf<DistanceRecord>()
    var pageToken: String? = null
    do {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = DistanceRecord::class,
                timeRangeFilter = window,
                dataOriginFilter = setOf(session.metadata.dataOrigin),
                pageToken = pageToken
            )
        )
        records += response.records
        pageToken = response.pageToken
    } while (pageToken != null)
    if (records.isNotEmpty()) return records.sumOf { it.distance.inKilometers }

    return client.aggregate(
        AggregateRequest(metrics = setOf(DistanceRecord.DISTANCE_TOTAL), timeRangeFilter = window)
    )[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
}

// ---------------------------------------------------------------------------
// Heart rate
// ---------------------------------------------------------------------------

data class HeartRateSample(val timeMillis: Long, val bpm: Int)

data class HeartRateSummary(val averageBpm: Int, val maxBpm: Int)

// Every heart rate reading between start and end, in time order. Null when Health Connect isn't
// connected, or there are too few readings to draw.
suspend fun heartRateSamples(context: Context, startMillis: Long, endMillis: Long): List<HeartRateSample>? {
    if (healthConnectStatus(context) != HealthConnectStatus.CONNECTED) return null
    val client = HealthConnectClient.getOrCreate(context)
    val samples = mutableListOf<HeartRateSample>()
    var pageToken: String? = null
    do {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis)
                ),
                pageToken = pageToken
            )
        )
        for (record in response.records) {
            for (sample in record.samples) {
                val time = sample.time.toEpochMilli()
                if (time in startMillis..endMillis) samples += HeartRateSample(time, sample.beatsPerMinute.toInt())
            }
        }
        pageToken = response.pageToken
    } while (pageToken != null)
    return samples.sortedBy { it.timeMillis }.takeIf { it.size >= 2 }
}

// A gap longer than this between readings (the watch was off, or not worn) breaks the line.
private const val HEART_RATE_GAP_MILLIS = 5 * 60_000L

// The readings as graph points: x is seconds since the start, and a null point marks each gap.
// Thinned to about 300 readings first, so the gaps added after are never thinned away.
fun heartRateSeries(samples: List<HeartRateSample>, startMillis: Long): List<ChartPoint> {
    val step = (samples.size / 300).coerceAtLeast(1)
    val kept = samples.filterIndexed { i, _ -> i % step == 0 || i == samples.lastIndex }
    val points = mutableListOf<ChartPoint>()
    var previous: Long? = null
    for (sample in kept) {
        val x = (sample.timeMillis - startMillis) / 1000f
        if (previous != null && sample.timeMillis - previous > HEART_RATE_GAP_MILLIS) {
            points += ChartPoint(x = (previous - startMillis) / 1000f, y = null)
        }
        points += ChartPoint(x = x, y = sample.bpm.toFloat())
        previous = sample.timeMillis
    }
    return points
}

fun heartRateSummary(samples: List<HeartRateSample>): HeartRateSummary? {
    if (samples.isEmpty()) return null
    return HeartRateSummary(
        averageBpm = Math.round(samples.map { it.bpm }.average()).toInt(),
        maxBpm = samples.maxOf { it.bpm }
    )
}
