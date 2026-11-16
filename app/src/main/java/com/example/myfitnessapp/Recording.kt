package com.example.myfitnessapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.SmallFloatingActionButton
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Delete

// ---------------------------------------------------------------------------
// The things that can be recorded.
// ---------------------------------------------------------------------------
// usesGps says whether the activity tracks location. A timer is just a stopwatch,
// so it never asks for location permission.
// workoutName is what follows the time of day when a recording is saved as its own workout,
// like "Afternoon Bike Ride".
enum class RecordActivity(
    val label: String,
    val usesGps: Boolean,
    val type: ActivityType,
    val workoutName: String = label
) {
    RUN("Run", true, ActivityType.RUN),
    WALK("Walk", true, ActivityType.WALK),
    BIKE("Bike", true, ActivityType.BIKE, workoutName = "Bike Ride"),
    TIMER("Timer", false, ActivityType.OTHER)
}

// A position on the earth, as latitude and longitude in degrees.
// We keep our own simple type here so the tracker doesn't depend on the map library.
data class LatLon(val latitude: Double, val longitude: Double)

// How fast the user was moving at one moment, in meters per second. The live pace is the
// average of a short list of these. timeMillis is time spent moving, not clock time: it
// only advances while the user is actually moving, so standing still never ages a
// reading out of the list.
data class PaceSample(val timeMillis: Long, val speedMetersPerSecond: Float)

// Everything the Record screen needs to show, bundled into one object.
// "data class" gives us copy(), which makes an updated version of the state.
data class RecordingState(
    val activity: RecordActivity = RecordActivity.RUN,
    val isRecording: Boolean = false,
    // When the recording started, in milliseconds. The timer is worked out from this.
    val startTimeMillis: Long = 0L,
    // The most recent GPS point. Null until the first one arrives.
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Meters moved between the last two points. Null until there are two points.
    val segmentMeters: Float? = null,
    // Running total of meters moved since the recording started.
    val totalMeters: Float = 0f,
    // Every point kept so far, in order. The map draws the route from this.
    val route: List<LatLon> = emptyList(),
    // The most recent points' times and distances, used for the live pace.
    val paceSamples: List<PaceSample> = emptyList(),
    // The live pace, worked out each time a point is kept, in seconds per kilometer.
    // Null when there isn't one.
    val paceSecondsPerKm: Double? = null,
    // Total time spent actually moving, in milliseconds. It leaves out standing still.
    val movingMillis: Long = 0L,
    // Pausing. pausedAtMillis is when the current pause began, or null if not paused.
    // pausedTotalMillis is the time spent in earlier pauses, which the timer leaves out.
    val pausedAtMillis: Long? = null,
    val pausedTotalMillis: Long = 0L,
    // True after the user presses stop. A stopped recording is frozen like a paused one,
    // but it's waiting to be saved or carried on.
    val isStopped: Boolean = false,
    // True when the recording paused itself because the user stopped moving. Unlike a
    // manual pause, it resumes by itself when the user starts moving again.
    val isAutoPaused: Boolean = false,
    // The split settings this recording started with. Changing the settings in the middle of
    // a recording only affects the next one.
    val splitSettings: SplitSettings = SplitSettings(),
    // Every kept GPS reading, with position, elevation, distance, and time together. The splits
    // and the elevation graph are worked out from this list, and it's what gets saved.
    val trackPoints: List<TrackPoint> = emptyList()
) {
    // True while the recording is paused.
    val isPaused: Boolean get() = pausedAtMillis != null

    // How long the recording has actually been running at the time "now", in milliseconds:
    // the time since the start, minus any time spent paused. While paused, the clock is
    // frozen at the moment the pause began.
    fun elapsedMillis(now: Long): Long = (pausedAtMillis ?: now) - startTimeMillis - pausedTotalMillis
}

// What happened when we tried to start a recording.
enum class StartResult { STARTED, NO_PERMISSION, GPS_OFF }

// ---------------------------------------------------------------------------
// The tracker: does the actual recording.
// ---------------------------------------------------------------------------
// "object" makes a single shared instance. It lives as long as the app's process does,
// so a recording keeps going while you switch to another tab. (A screen's own variables
// are thrown away when you leave it, which would have stopped the recording.)
//
// While recording with GPS, a foreground service (RecordingService) runs alongside it.
// That's what lets Android keep delivering GPS points while the screen is locked.
object RecordingTracker {

    // How often we ask for a GPS point.
    private const val INTERVAL_MS = 1_000L

    // A GPS point reports how accurate it is, as a radius in meters. Points less
    // accurate than this are thrown away.
    private const val MAX_ACCURACY_METERS = 10f

    // Movement smaller than this is treated as GPS wobble, even if the point
    // claims to be very accurate.
    private const val MIN_MOVE_METERS = 5f

    // Below this speed, in meters per second, the user counts as standing still.
    // 0.5 m/s is a very slow shuffle, well under a normal walking pace of about 1.4 m/s.
    private const val STANDING_STILL_SPEED = 0.3f

    // How long without movement before the recording pauses itself, in milliseconds.
    private const val AUTO_PAUSE_AFTER_MS = 30_000L

    // A reading counts as "moving" for the pace if its speed is at least this, in meters per
    // second. Anything slower is treated as standing still.
    private const val PACE_MIN_SPEED = 0.5f

    // The most time one gap between readings can add to the moving time, in milliseconds.
    // Standing still means readings stop arriving, so after a long stop the gap to the next
    // reading is long. Capping it keeps that stop from being counted as moving.
    private const val PACE_MAX_GAP_MS = 5_000L

    // MutableStateFlow holds the current state and tells everyone watching when it changes.
    // The underscore version is private and changeable; the public version is read-only,
    // so only this object can change the state.
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var locationManager: LocationManager? = null
    private var previousLocation: Location? = null

    // Where the readings come from. Google's fused location blends GPS with the phone's motion
    // sensors and Wi-Fi, so it holds a position better between buildings and under trees than
    // the GPS chip alone. It's part of Google Play services; on a phone without them, the
    // GPS chip is listened to directly instead. Both hand each reading to handleLocation.
    private var fusedClient: FusedLocationProviderClient? = null
    // The app's own context (never a screen's), kept while recording so a resume can switch
    // the readings back on.
    private var locationContext: Context? = null
    private val fusedListener = com.google.android.gms.location.LocationListener { handleLocation(it) }
    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = handleLocation(location)
    }

    // Asks for a reading about every second. Returns false if location permission is missing.
    private fun startLocationUpdates(context: Context, manager: LocationManager): Boolean {
        val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted) return false

        val hasPlayServices = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        try {
            if (hasPlayServices) {
                // High accuracy means GPS is always part of the blend. There's no minimum
                // distance: a reading is reported even if you haven't moved.
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                    .setMinUpdateIntervalMillis(INTERVAL_MS)
                    .build()
                val client = LocationServices.getFusedLocationProviderClient(context)
                client.requestLocationUpdates(request, fusedListener, Looper.getMainLooper())
                fusedClient = client
            } else {
                // The 0f means there's no minimum distance.
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, gpsListener)
            }
        } catch (e: SecurityException) {
            return false
        }
        Barometer.start(context)
        // Steps are only felt on foot.
        val activity = _state.value.activity
        if (activity == RecordActivity.RUN || activity == RecordActivity.WALK) {
            StepWatcher.start(context)
        }
        return true
    }

    private fun stopLocationUpdates() {
        StepWatcher.stop()
        Barometer.stop()
        fusedClient?.removeLocationUpdates(fusedListener)
        fusedClient = null
        locationManager?.removeUpdates(gpsListener)
    }

    // Called each time a new reading arrives.
    private fun handleLocation(location: Location) {
        // A manual pause or a stop ignores every point. An auto-pause keeps listening,
        // because it has to notice when the user starts moving again.
        val stateNow = _state.value
        if (stateNow.isPaused && !stateNow.isAutoPaused) return

        // The accuracy is the radius, in meters, within which the true position
        // probably lies. Some points don't report one, so it can be null.
        val accuracy = if (location.hasAccuracy()) location.accuracy else null

        // Filter 1: throw away points that are too inaccurate to trust.
        // "return" here means "do nothing with this point".
        if (accuracy != null && accuracy > MAX_ACCURACY_METERS) return

        // Whether the GPS can tell how fast the user is going. The speed is only trusted
        // if the GPS reports one, and if its own margin of error isn't bigger than the
        // threshold (a very uncertain speed could wrongly call a walker "still").
        val speedIsTrusted = location.hasSpeed() &&
                (!location.hasSpeedAccuracy() ||
                        location.speedAccuracyMetersPerSecond <= STANDING_STILL_SPEED)

        // A walk or run goes by the user's steps when the phone can feel them, which is far
        // quicker and surer than GPS at noticing a stop. A bike ride, or a phone with no
        // step sensor, goes by GPS speed.
        val now = System.currentTimeMillis()
        val bySteps = StepWatcher.isWatching
        val lastStep = StepWatcher.lastStepMillis

        // True if this reading says the user is standing still.
        val readingIsStill = if (bySteps) {
            !isMovingBySteps(now, lastStep, if (location.hasSpeed()) location.speed else null, speedIsTrusted)
        } else {
            speedIsTrusted && location.speed < STANDING_STILL_SPEED
        }

        // The time since the last kept point, in milliseconds. A point is only kept when
        // the user has really moved, so this is also how long the user has been still.
        // It's 0 when there's no last point, such as at the start or just after a pause.
        val gapMillis = previousLocation?.let { location.time - it.time } ?: 0L

        // AUTO-RESUME: while auto-paused, a reading showing real movement ends the pause.
        // Any other reading is ignored. resume() does the work, and since an auto-pause
        // never switched GPS off, there's nothing else to restart.
        if (stateNow.isAutoPaused) {
            val movingAgain = if (bySteps) {
                // A few steps in a row, or a GPS speed too fast to be standing.
                stepsSayResume(now, StepWatcher.recentStepTimes) ||
                    (speedIsTrusted && location.speed >= MOVING_WITHOUT_STEPS_SPEED)
            } else {
                speedIsTrusted && location.speed >= PACE_MIN_SPEED
            }
            if (movingAgain) {
                resume()
            } else {
                return
            }
        }

        // AUTO-PAUSE: the user has been still for too long, and this reading agrees they
        // still are. Requiring this reading to say "still" stops a long GPS gap (like a
        // tunnel) from pausing someone who is actually moving. The pause is dated to the
        // last movement, so the waiting time isn't counted in the activity's time.
        // Going by steps, it's 10 seconds since the last step, and the pause is dated to
        // that step.
        val lastKept = previousLocation
        if (lastKept != null && readingIsStill) {
            if (bySteps && now - lastStep > STEP_AUTO_PAUSE_AFTER_MS) {
                pause(atMillis = minOf(maxOf(lastStep, lastKept.time), now), automatic = true)
                return
            }
            if (!bySteps && gapMillis > AUTO_PAUSE_AFTER_MS) {
                pause(atMillis = minOf(lastKept.time, now), automatic = true)
                return
            }
        }

        // Filter 2: standing still. The GPS measures speed directly from the satellite
        // signals, which is far more reliable than working it out from two noisy
        // positions, so a reading that says "still" is ignored. The very first point
        // after starting or resuming is always kept, so there's a position to measure from.
        if (previousLocation != null && readingIsStill) {
            return
        }

        // distanceTo gives the straight-line distance in meters between two points.
        // "?." means "only do this if previousLocation isn't null", so the very first
        // point has nothing to measure from and gives null.
        val moved = previousLocation?.distanceTo(location)

        // Filter 3: ignore wobble. A standing-still phone drifts around inside its
        // accuracy radius, so a move has to be bigger than that radius (and bigger
        // than the minimum) before it counts. maxOf picks the larger of two numbers.
        // The comparison is against the last point we kept, so slow real movement
        // still adds up: it just counts once it has gone far enough.
        if (moved != null && moved < maxOf(MIN_MOVE_METERS, accuracy ?: 0f)) return

        // The speed at this point, in meters per second, for the live pace. The GPS's own
        // measured speed is used when there is one, because it's smoother than working
        // speed out from two positions. If there isn't one, it's worked out from the
        // distance and time since the last kept point.
        val speed: Float? = when {
            location.hasSpeed() -> location.speed
            moved != null && gapMillis > 0L -> moved / (gapMillis / 1000f)
            else -> null
        }

        // Only a reading where the user was really moving counts toward the pace.
        // takeIf gives null if the speed is below the minimum (or unknown).
        val movingSpeed: Float? = speed?.takeIf { it >= PACE_MIN_SPEED }

        previousLocation = location

        // The time on the activity timer right now, with paused time left out. It's stored
        // with the point, so splits by time can be worked out later.
        val nowElapsed = _state.value.elapsedMillis(System.currentTimeMillis())

        // The height above sea level at this point, or null if the GPS didn't report one.
        val altitude = altitudeAboveSeaLevel(location)
        // And the height from the barometer, on a phone that has one.
        val pressureAltitude = Barometer.altitudeMeters

        _state.update { current ->
            val newTotal = current.totalMeters + (moved ?: 0f)

            // Moving time only advances for a moving reading, and a long gap before it
            // (a stop) only counts as a short one.
            val movingMillis = if (movingSpeed != null) {
                current.movingMillis + gapMillis.coerceIn(0L, PACE_MAX_GAP_MS)
            } else {
                current.movingMillis
            }

            // A moving reading joins the list, and readings older than the window,
            // counted in moving time, are dropped. Other readings change nothing.
            val samples = if (movingSpeed != null) {
                (current.paceSamples + PaceSample(movingMillis, movingSpeed))
                    .filter { it.timeMillis > movingMillis - PACE_WINDOW_MS }
            } else {
                current.paceSamples
            }

            current.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                segmentMeters = moved,
                totalMeters = newTotal,
                // "+" on a list makes a new list with the point added at the end.
                route = current.route + LatLon(location.latitude, location.longitude),
                movingMillis = movingMillis,
                paceSamples = samples,
                // A new pace is only worked out from a moving reading. Otherwise the
                // last pace stays, so standing still doesn't change it.
                paceSecondsPerKm = if (movingSpeed != null) {
                    averagePace(samples)
                } else {
                    current.paceSecondsPerKm
                },
                // This reading, in full, joins the recording.
                trackPoints = current.trackPoints + TrackPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    elevationMeters = altitude?.toFloat(),
                    distanceMeters = newTotal,
                    elapsedMillis = nowElapsed,
                    pressureAltitudeMeters = pressureAltitude
                )
            )
        }
    }

    // Changes the chosen activity. It can't be changed in the middle of a recording.
    fun selectActivity(activity: RecordActivity) {
        _state.update { if (it.isRecording) it else it.copy(activity = activity) }
    }

    // The phone's last known position, used to point the map at the right area before
    // any recording has started. It's null if there isn't one, or if location
    // permission hasn't been granted yet.
    fun lastKnownPosition(context: Context): LatLon? {
        val manager = context.applicationContext.getSystemService(LocationManager::class.java)
        return try {
            // "?:" means: use the left side, unless it's null, then try the right side.
            val location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            location?.let { LatLon(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            // No permission yet. That's fine: the map just starts zoomed out.
            null
        }
    }

    // Starts recording. Returns what happened, so the screen can explain any problem.
    fun start(context: Context): StartResult {
        // applicationContext lives as long as the app, so we never hold on to a screen.
        val appContext = context.applicationContext
        val activity = _state.value.activity

        if (activity.usesGps) {
            val manager = appContext.getSystemService(LocationManager::class.java)

            // Location can be switched off for the whole phone.
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return StartResult.GPS_OFF
            }

            if (!startLocationUpdates(appContext, manager)) {
                return StartResult.NO_PERMISSION
            }
            locationManager = manager
            locationContext = appContext
        }

        previousLocation = null
        // The split settings are read once, when the recording starts.
        val splitSettings = SplitPrefs.load(appContext)
        _state.update {
            RecordingState(
                activity = it.activity,
                isRecording = true,
                startTimeMillis = System.currentTimeMillis(),
                splitSettings = splitSettings
            )
        }

        if (activity.usesGps) {
            // Starts the foreground service, which shows a notification and keeps
            // Android from pausing GPS when the screen locks.
            appContext.startForegroundService(Intent(appContext, RecordingService::class.java))
        }
        return StartResult.STARTED
    }

    // Pauses the recording. The clock stops, and the foreground service keeps running so
    // Android doesn't end the app.
    // atMillis is the moment the pause is dated from, and automatic says whether the app
    // paused itself because the user stopped moving.
    fun pause(atMillis: Long = System.currentTimeMillis(), automatic: Boolean = false) {
        val current = _state.value
        if (!current.isRecording || current.isPaused) return

        // A manual pause switches GPS off to save battery. An automatic pause leaves it on,
        // because the listener has to keep watching for the user to start moving again.
        if (!automatic) {
            stopLocationUpdates()
        }
        // The user may move while paused, so the first point after resuming shouldn't be
        // measured from the last point before the pause. Clearing this makes that point
        // a fresh starting point, so the distance covered while paused isn't counted.
        previousLocation = null
        _state.update {
            it.copy(
                pausedAtMillis = atMillis,
                isAutoPaused = automatic,
                paceSamples = emptyList(),
                paceSecondsPerKm = null
            )
        }
    }

    // Stops the recording. It freezes just like a pause, but it's marked as stopped,
    // waiting to be saved. Pressing play (resume) carries on with it.
    fun stop() {
        val current = _state.value
        if (!current.isRecording || current.isStopped) return

        stopLocationUpdates()
        previousLocation = null
        _state.update {
            it.copy(
                isStopped = true,
                isAutoPaused = false,
                // If it was already paused, keep the original pause time, so the timer
                // stays frozen at the same moment.
                pausedAtMillis = it.pausedAtMillis ?: System.currentTimeMillis(),
                paceSamples = emptyList(),
                paceSecondsPerKm = null
            )
        }
    }

    // Resumes a paused recording. Returns what happened, since turning GPS back on
    // can fail (for example, if location was switched off during the pause).
    fun resume(): StartResult {
        val current = _state.value
        val pausedAt = current.pausedAtMillis ?: return StartResult.STARTED

        // The location manager is only set for activities that use GPS.
        val manager = locationManager
        // An auto-pause never switched GPS off, so there's nothing to turn back on.
        if (manager != null && !current.isAutoPaused) {
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return StartResult.GPS_OFF
            }
            val context = locationContext
            if (context == null || !startLocationUpdates(context, manager)) {
                return StartResult.NO_PERMISSION
            }
        }

        // Add the length of this pause to the total, so the timer leaves it out.
        _state.update {
            it.copy(
                pausedAtMillis = null,
                pausedTotalMillis = it.pausedTotalMillis + (System.currentTimeMillis() - pausedAt),
                isStopped = false,
                isAutoPaused = false
            )
        }
        return StartResult.STARTED
    }

    // Ends the recording completely and goes back to the "ready" state. Used after
    // saving, and when the service is shut down.
    fun finish(context: Context) {
        // If nothing is recording, there's nothing to stop. This also keeps the
        // service and the tracker from stopping each other in a loop.
        if (!_state.value.isRecording) return

        // Stop the GPS updates, so the phone isn't tracking location anymore.
        stopLocationUpdates()
        locationManager = null
        locationContext = null
        previousLocation = null
        _state.update { RecordingState(activity = it.activity) }

        // Shut down the foreground service, which removes its notification.
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, RecordingService::class.java))
    }
}

// Turns meters into text: "523 m" under a kilometer, "1.25 km" above.
// Locale.getDefault() shows numbers the way the phone's language does.
fun formatDistance(meters: Float): String {
    return if (meters < 1000f) {
        String.format(Locale.getDefault(), "%.0f m", meters)
    } else {
        String.format(Locale.getDefault(), "%.2f km", meters / 1000f)
    }
}

// The pace is the average of the speeds over this many milliseconds of recent movement.
private const val PACE_WINDOW_MS = 30_000L

// No pace is shown until the speeds on hand cover at least this much time. Before that
// there are too few readings for a steady average.
private const val MIN_PACE_SPAN_MS = 15_000L

// A speed below this, in meters per second, is slower than a pace of one hour per
// kilometer, which isn't really moving, so no pace is shown.
private const val MIN_PACE_SPEED = 1000.0 / 3600.0

// The user's pace in seconds per kilometer: the average of the speeds in the list, turned
// into a pace. Returns null if there isn't enough yet, or if the average is too slow to count.
fun averagePace(samples: List<PaceSample>): Double? {
    if (samples.isEmpty()) return null

    // The time between the oldest and newest speed in the list. Until it reaches 15
    // seconds, there isn't enough to give a steady reading.
    val spanMillis = samples.last().timeMillis - samples.first().timeMillis
    if (spanMillis < MIN_PACE_SPAN_MS) return null

    // "average()" adds the numbers up and divides by how many there are.
    val averageSpeed = samples.map { it.speedMetersPerSecond.toDouble() }.average()
    if (averageSpeed < MIN_PACE_SPEED) return null

    // A speed of 3 meters per second covers a kilometer in 1000 / 3 seconds.
    return 1000.0 / averageSpeed
}
// Turns a pace into text like "5:32 /km", or a dash if there isn't one.
fun formatSecondsPerKm(secondsPerKm: Double?): String {
    if (secondsPerKm == null) return "---"
    val total = secondsPerKm.roundToInt()
    // "%d:%02d" means a whole number, a colon, then a whole number padded with a zero
    // to at least 2 digits (so 5 minutes 6 seconds shows as 5:06).
    return String.format(Locale.getDefault(), "%d:%02d /km", total / 60, total % 60)
}

// The pace to show, as text. It's worked out in the tracker whenever a moving reading
// arrives, and it holds its last value while the user is standing still.
fun livePaceText(state: RecordingState): String {
    return formatSecondsPerKm(state.paceSecondsPerKm)
}

// The message to show the user for a start or resume attempt, or null if it worked.
// An "extension function" like this adds a function to an existing type without changing it.
private fun StartResult.toUserMessage(): String? = when (this) {
    StartResult.STARTED -> null
    StartResult.NO_PERMISSION ->
        "Precise location permission is needed to record. You can allow it in the app's settings."
    StartResult.GPS_OFF ->
        "Location is turned off on this phone. Turn it on and try again."
}

// The timer for a recording. Unlike WorkoutTimer, it understands pausing: time spent
// paused isn't counted, and the display holds still while paused.
@Composable
fun RecordingTimer(state: RecordingState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Runs while this is on screen, and starts over whenever the start time or the pause
    // state changes. While paused the loop doesn't run at all, so the display holds still.
    LaunchedEffect(state.startTimeMillis, state.pausedAtMillis, state.pausedTotalMillis) {
        while (!state.isPaused) {
            now = System.currentTimeMillis()
            // Waits until the next whole second of elapsed time, so the display
            // doesn't slowly drift or skip a second.
            delay((1000 - (state.elapsedMillis(now) % 1000)).milliseconds)
        }
    }

    Text(
        text = formatElapsed(state.elapsedMillis(now)),
        fontSize = AppText.heading,
        // Dimmed while paused, so it's clear the clock is stopped.
        color = if (state.isPaused) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    )
}

// ---------------------------------------------------------------------------
// The map. It uses osmdroid, which draws OpenStreetMap maps. It's free and needs
// no account or API key.
// ---------------------------------------------------------------------------

// The map view and the things drawn on it, kept together so they're created once.
// A map view is a classic Android View, not a Compose function, so it's wrapped
// in AndroidView below.
private class MapParts(
    val view: MapView,
    // The line drawn along the route.
    val line: Polyline,
    // The dot showing where the user is.
    val marker: Marker,
    // The "OpenStreetMap contributors" credit drawn in the corner of the map.
    val copyright: CopyrightOverlay
) {
    // Remembers whether we've already zoomed in on the route. A plain variable is
    // enough here, because nothing on screen depends on it.
    var zoomedToRoute = false
}

// The name this app sends to the map server with every tile request. OpenStreetMap's
// tile rules require a real, specific name, and the server refuses generic ones,
// including anything starting with "com.example". Their rules also recommend adding a way
// to contact you, such as an email address or the link to your GitHub page, so you could
// make it look like "ProjectMe/1.0 (https://github.com/yourname/yourrepo)".
private const val MAP_USER_AGENT = "ProjectMe/1.0 (personal fitness tracker)"

// The OpenStreetMap tile server, described by hand. osmdroid has a ready-made version of
// this, but that one ignores the name above and sends the app's package name instead
// ("com.example.myfitnessapp/1"), which OpenStreetMap blocks. Describing it ourselves, without
// that setting, means our own name is sent. The policy values follow OpenStreetMap's
// rules: at most 2 downloads at once, no bulk downloading, and a meaningful name.
val appTileSource = XYTileSource(
    "OpenStreetMap",
    0,
    19,
    256,
    ".png",
    arrayOf("https://tile.openstreetmap.org/"),
    "\u00A9 OpenStreetMap contributors",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or
            TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
    )
)

// Sets up osmdroid. This has to happen before the first map is created.
fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    // osmdroid keeps a few of its settings in a small preferences file.
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    // The name the map servers see, so they know which app is asking.
    config.userAgentValue = MAP_USER_AGENT
    // Downloaded map tiles are saved in the app's private cache folder, which needs no
    // storage permission. (The cache is also what lets a map you've viewed work with a
    // weak signal.) This folder is new on purpose: the old one may still hold the
    // "Access blocked" pictures the server sent earlier.
    val base = File(context.cacheDir, "map-tiles")
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles")
}

// Shows the map. It draws the route as a line, a dot at the latest point, and follows
// the user as they move.
@Composable
fun RecordingMap(
    route: List<LatLon>,
    initialCenter: LatLon?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()

    // Build the map once, and keep it for as long as this screen is showing.
    val parts = remember {
        configureOsmdroid(context)

        // A blue dot with a white edge, drawn in code so we need no image files.
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF1E88E5.toInt())
            setStroke(6, android.graphics.Color.WHITE)
            setSize(44, 44)
        }

        val line = Polyline().apply {
            outlinePaint.color = 0xFF1E88E5.toInt()
            outlinePaint.strokeWidth = 12f
        }

        val view = MapView(context).apply {
            setTileSource(appTileSource)
            // Pinch to zoom and drag to move around.
            setMultiTouchControls(true)
            // Draws the map tiles bigger on high-resolution screens, so labels are readable.
            isTilesScaledToDpi = true
            // Hides the built-in + and - buttons. Pinching does the same job.
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            if (initialCenter != null) {
                // Start on the user's area, zoomed to about neighborhood level.
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(initialCenter.latitude, initialCenter.longitude))
            } else {
                // Nowhere to point yet, so start zoomed right out.
                controller.setZoom(3.0)
            }
        }

        val marker = Marker(view).apply {
            icon = dot
            // Centers the dot on the position, instead of putting its bottom edge there.
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            // Tapping the dot does nothing. (Returning true means "handled".)
            setOnMarkerClickListener { _, _ -> true }
            // Hidden until the first GPS point arrives.
            isEnabled = false
        }

        // OpenStreetMap's data license requires a visible credit on the map.
        // The overlay reads the credit text from the tile source above.
        val copyright = CopyrightOverlay(context)

        view.overlays.add(line)
        view.overlays.add(marker)
        view.overlays.add(copyright)

        MapParts(view, line, marker, copyright)
    }

    // AndroidView puts a classic Android View into a Compose screen.
    // "factory" supplies the view, and "update" runs again whenever the values it
    // uses change, which here means every time a new point is added to the route.
    AndroidView(
        factory = { parts.view },
        modifier = modifier,
        update = { view ->
            // In dark mode, invert the map's colors so it isn't a bright white slab.
            view.overlayManager.tilesOverlay.setColorFilter(
                if (darkTheme) TilesOverlay.INVERT_COLORS else null
            )
            // Keep the credit readable on both the light and the inverted map.
            parts.copyright.setTextColor(
                if (darkTheme) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
            )

            val points = route.map { GeoPoint(it.latitude, it.longitude) }
            parts.line.setPoints(points)

            val latest = points.lastOrNull()
            parts.marker.isEnabled = latest != null

            if (latest != null) {
                parts.marker.position = latest
                if (!parts.zoomedToRoute) {
                    // The first point of a recording: jump to street level.
                    view.controller.setZoom(17.0)
                    view.controller.setCenter(latest)
                    parts.zoomedToRoute = true
                } else {
                    // After that, glide to follow the user.
                    view.controller.animateTo(latest)
                }
            } else {
                // No route (not recording), so the next recording zooms in again.
                parts.zoomedToRoute = false
            }

            // Redraw the map with the new line and dot.
            view.invalidate()
        }
    )
}

// ---------------------------------------------------------------------------
// The Record screen.
// ---------------------------------------------------------------------------
// The size of the floating bar and its gap above the bottom of the screen.
// Things placed over the map keep clear of this much space at the bottom.
private val FLOATING_BAR_HEIGHT = 72.dp
private val FLOATING_BAR_GAP = 12.dp

@Composable
fun RecordScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Watches the tracker. The screen redraws whenever the state changes.
    val state by RecordingTracker.state.collectAsState()

    // Whether the splits screen is showing instead of the map. Only used while recording.
    var showSplits by remember { mutableStateOf(false) }

    // The title text shrinks from the page title's size to the header's when a recording starts,
    // to make room for "| Splits". animateFloatAsState glides between the two instead of jumping.
    val titleSize by animateFloatAsState(
        targetValue = if (state.isRecording) AppText.screenTitle.value else AppText.pageTitle.value,
        label = "titleSize"
    )

    // Back to the map screen when the recording ends.
    LaunchedEffect(state.isRecording) {
        if (!state.isRecording) showSplits = false
    }

    // Where to point the map before any recording has started. Looked up once.
    val initialCenter = remember { RecordingTracker.lastKnownPosition(context) }

    // Whether the activity list is open, and any problem to show the user.
    var menuOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // The permissions to ask for. Android 13 and newer also need permission to show
    // notifications, which is how the recording notification appears. Recording still
    // works if that one is refused.
    // "buildList" makes a list from the add(...) calls inside it.
    // A walk or run also asks for "Physical activity", to feel steps and so know when
    // you've stopped. Refusing it is fine too: GPS speed is used instead.
    val permissionsToRequest = remember(state.activity) {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (state.activity == RecordActivity.RUN || state.activity == RecordActivity.WALK) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    // Runs the save in the background, and disables the buttons while it works.
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    // A friendly note shown after saving. (message is for problems.)
    var notice by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    // The pop-up for records the saved activity beat (null when there's none to show).
    var celebration by remember { mutableStateOf<Celebration?>(null) }

    // Checks a just-saved activity for new records, and shows them if there are any.
    suspend fun celebrate(exerciseId: Long, activity: RecordActivity) {
        val records = recordingRecords(context, exerciseId)
        if (records.isNotEmpty()) celebration = Celebration("From your ${activityNoun(activity.type)}, just now", records)
    }

    fun startRecording() {
        notice = null
        message = RecordingTracker.start(context).toUserMessage()
    }

    // Saves the stopped recording to the history. If that works, the recording ends and
    // the screen goes back to ready. If not, it stays stopped so you can try again.
    fun saveActivity() {
        if (saving) return
        saving = true
        message = null
        // The recording is frozen, so this copy is exactly what gets saved.
        val recording = state
        scope.launch {
            val savedId = saveRecording(context, recording)
            saving = false
            if (savedId != null) {
                RecordingTracker.finish(context)
                notice = "Saved to History."
                celebrate(savedId, recording.activity)
            } else {
                message = "Couldn't save the activity. Please try again."
            }
        }
    }

    // Nothing is written to the database until the user saves, so ending the recording is all
    // it takes to discard it.
    fun discardActivity() {
        showDiscardDialog = false
        message = null
        RecordingTracker.finish(context)
        notice = "Activity discarded."
    }

    // Adds the stopped recording to the workout in progress, or starts a new workout
    // for it if there isn't one. If it works, the recording ends and the screen goes back
    // to ready. If not, it stays stopped so you can try again.
    fun addActivityToWorkout() {
        if (saving) return
        saving = true
        message = null
        // The recording is frozen, so this copy is exactly what gets added.
        val recording = state
        scope.launch {
            val added = addRecordingToWorkout(context, recording)
            saving = false
            added.exerciseId?.let { celebrate(it, recording.activity) }
            when (added.result) {
                AddToWorkoutResult.ADDED -> {
                    RecordingTracker.finish(context)
                    notice = "Added to your current workout."
                }
                AddToWorkoutResult.STARTED_NEW -> {
                    RecordingTracker.finish(context)
                    notice = "Started a new workout with this activity."
                }
                AddToWorkoutResult.FAILED ->
                    message = "Couldn't add the activity. Please try again."
            }
        }
    }

    // Shows Android's permission pop-up. The result arrives in the block below.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Only precise location is required. A refused notification permission
        // just means the notification won't be shown.
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startRecording()
        } else {
            message = StartResult.NO_PERMISSION.toUserMessage()
        }
    }

    // The explanation shown before Android's permission prompts, the first time.
    var showPermissionsInfo by remember { mutableStateOf(false) }
    if (showPermissionsInfo) {
        RecordPermissionsDialog(
            askActivity = Manifest.permission.ACTIVITY_RECOGNITION in permissionsToRequest,
            onContinue = {
                showPermissionsInfo = false
                FirstRun.markPermissionsExplained(context)
                permissionLauncher.launch(permissionsToRequest)
            },
            onDismiss = { showPermissionsInfo = false }
        )
    }

    // Copied into plain variables so Kotlin can check them for null below.
    val latitude = state.latitude
    val longitude = state.longitude

    // A Box stacks its children on top of each other. The screen's content is the first
    // layer, and the floating bar is the second layer drawn over it.
    Box(modifier = modifier.fillMaxSize()) {
        // Top aligned column for the Map, "Record" title, selected activity, and timer
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The main body of the screen: the map, with a few things floating over it.
            // weight(1f) makes it take all the remaining space, which runs underneath
            // the floating bar at the bottom.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                RecordingMap(
                    route = state.route,
                    initialCenter = initialCenter,
                    modifier = Modifier.fillMaxSize()
                )

                // The splits screen, a layer over the whole map. It slides in from the right.
                SplitsOverlay(visible = showSplits && state.isRecording, state = state)

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The title pill. Before recording, it just says "Record". Once recording
                    // starts, the text shrinks and the pill stretches to fit "Record | Splits",
                    // and the two halves become buttons that switch between the map and the splits.
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        // Slightly see-through, so the map is still visible behind it.
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 4.dp,
                        // animateContentSize makes the pill glide to its new width.
                        modifier = Modifier
                            .padding(12.dp)
                            .animateContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // The "Record" half: shows the map. The one that's showing is
                            // bright and the other is dimmed.
                            Text(
                                text = "Record",
                                fontSize = titleSize.sp,
                                color = if (showSplits) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .clickable(enabled = state.isRecording) { showSplits = false }
                                    .padding(horizontal = 4.dp)
                            )

                            // The " | Splits" half slides and fades in once recording starts.
                            AnimatedVisibility(
                                visible = state.isRecording,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "|",
                                        fontSize = titleSize.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Splits",
                                        fontSize = titleSize.sp,
                                        color = if (showSplits) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier
                                            .clickable { showSplits = true }
                                            .padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    // The activity type and timer
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        // Slightly see-through, so the map is still visible behind it.
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 4.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // The chosen activity, and the timer once recording has started.
                            Text(
                                text = state.activity.label,
                                fontSize = AppText.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.isRecording) {
                                Spacer(modifier = Modifier.height(8.dp))
                                RecordingTimer(state)
                            }
                        }
                    }
                }

            }
        }

        // Bottom aligned Column holding the message text, total distance, and record control pill
        Column (
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // The status, in a small card at the bottom of the map. While recording normally
            // there's nothing to say, so the card is left out and the map stays clear.
            val errorColor = MaterialTheme.colorScheme.error
            val statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            // "when" without a subject checks each condition in order and uses the first true one.
            val status: Pair<String, Color>? = when {
                saving -> "Saving your activity..." to statusColor
                message != null -> (message ?: "") to errorColor
                !state.isRecording -> "Choose an activity, then press play to start recording." to statusColor
                state.isStopped -> "Stopped. Press play to continue, the check mark to save, + to add it to a workout, or the trash bin to discard it." to statusColor
                state.isAutoPaused -> "Auto-paused because you stopped moving. It resumes when you move again, or tap resume." to statusColor
                state.isPaused -> "Paused. Tap the resume button to continue." to statusColor
                !state.activity.usesGps -> "Timer running. This activity doesn't use GPS." to statusColor
                latitude == null || longitude == null -> "Waiting for an accurate GPS signal..." to statusColor
                else -> null
            }
            if (status != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    // Slightly see-through, so the map is still visible behind it.
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .padding(FLOATING_BAR_GAP)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = status.first,
                        color = status.second,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            // The running total and the live pace, in two small rounded boxes side by side,
            // floating just above the bar.
            if (state.isRecording && state.activity.usesGps) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = "Total distance",
                                fontSize = AppText.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDistance(state.totalMeters),
                                fontSize = AppText.heading
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = "Pace (last 30 s)",
                                fontSize = AppText.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = livePaceText(state),
                                fontSize = AppText.heading
                            )
                        }
                    }
                }
            }
            // A Box holds the pill and the circle button side by side. Each is placed on its
            // own inside it, so the button can sit at the right without moving the pill.
            Box(modifier = Modifier.fillMaxWidth()) {
                // THE FLOATING BAR: a compact pill hovering above the bottom of the screen.
                // It's a second layer in the Box, so it sits on top of the content instead of
                // taking up a row of its own. align(BottomCenter) pins it to the bottom middle.
                Surface(
                    // RoundedCornerShape(50) rounds the corners by 50% of the height,
                    // which turns the bar into a pill with semicircle ends.
                    shape = RoundedCornerShape(50),
                    // A tinted color, so the bar stands out from the screen behind it.
                    color = MaterialTheme.colorScheme.primaryContainer,
                    // The shadow beneath is what makes it look like it's hovering.
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(240.dp)
                        .height(FLOATING_BAR_HEIGHT)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // The small circular button that opens the list of activities.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp)
                        ) {
                            // Chooses the activity; once stopped, it becomes a trash bin that
                            // discards the recording (after asking). It can't switch activity
                            // in the middle of a recording.
                            val stopped = state.isStopped
                            FilledIconButton(
                                onClick = { if (stopped) showDiscardDialog = true else menuOpen = true },
                                enabled = if (stopped) !saving else !state.isRecording,
                                // Plain surface colors, so the button stands out against the tinted bar.
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = if (stopped) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (stopped) Icons.Filled.Delete else Icons.AutoMirrored.Filled.List,
                                    contentDescription = if (stopped) "Discard activity" else "Choose activity"
                                )
                            }

                            // The list itself. It opens next to the button above.
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                // One entry for each activity in the RecordActivity enum.
                                RecordActivity.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            RecordingTracker.selectActivity(option)
                                            message = null
                                            menuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        // The small circular pause button, opposite the activity button.
                        // While paused, it becomes a resume (play) button.
                        FilledIconButton(
                            onClick = {
                                when {
                                    // Stopped: this button Saves.
                                    state.isStopped -> saveActivity()
                                    // Paused: this button is Resume. Resuming can fail, so show any problem.
                                    state.isPaused -> message =
                                        RecordingTracker.resume().toUserMessage()
                                    // Recording: this button is Pause.
                                    else -> RecordingTracker.pause()
                                }
                            },
                            enabled = state.isRecording && !saving,
                            // Plain surface colors, matching the activity button on the other side.
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                                .size(40.dp)
                        ) {
                            when {
                                state.isStopped -> Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Save activity"
                                )

                                state.isPaused -> Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Resume recording"
                                )

                                else -> {
                                    // Two bars are the usual "pause" symbol, drawn by hand.
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        repeat(2) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 4.dp, height = 14.dp)
                                                    .background(
                                                        color = LocalContentColor.current,
                                                        shape = RoundedCornerShape(1.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // The circle in the middle. Play to start, and a square to stop.
                        FilledIconButton(
                            onClick = {
                                when {
                                    // Stopped: play carries on with the same recording.
                                    state.isStopped -> message =
                                        RecordingTracker.resume().toUserMessage()
                                    // Recording: stop freezes it, ready to save or continue.
                                    state.isRecording -> {
                                        RecordingTracker.stop()
                                        message = null
                                    }
                                    // Not recording yet: start, if the permissions allow it.
                                    !state.activity.usesGps ||
                                            permissionsToRequest.all {
                                                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                                            } -> startRecording()
                                    // Otherwise ask for permission first, the first time with a
                                    // word on what each permission is for.
                                    else -> if (FirstRun.permissionsExplained(context)) {
                                        permissionLauncher.launch(permissionsToRequest)
                                    } else {
                                        showPermissionsInfo = true
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp)
                        ) {
                            if (state.isRecording && !state.isStopped) {
                                // A filled square is the usual "stop" symbol. We draw it ourselves
                                // because the built-in icon set doesn't include one.
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isStopped) "Continue recording" else "Start recording",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                } // Pill surface end

                // A floating circle button beside the pill, shown once the activity is stopped.
                // It adds the activity to the workout in progress, or starts a new workout.
                // align(CenterEnd) works here because we're directly inside the new Box.
                if (state.isStopped && !saving) {
                    SmallFloatingActionButton(
                        onClick = { addActivityToWorkout() },
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add to workout")
                    }
                }
            } // end of box holding pill and button beside it
        }
    }

    if (showDiscardDialog && state.isStopped) {
        ConfirmDestructiveDialog(
            title = "Discard activity?",
            message = "This can't be undone.",
            confirmLabel = "Discard",
            onConfirm = ::discardActivity,
            onDismiss = { showDiscardDialog = false }
        )
    }

    celebration?.let { RecordCelebration(it, onDismiss = { celebration = null }) }
}
