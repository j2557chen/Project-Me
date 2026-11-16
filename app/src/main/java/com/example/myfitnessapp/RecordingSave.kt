package com.example.myfitnessapp

import android.content.Context
import android.database.SQLException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.drawing.MapSnapshot
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.graphics.createBitmap

// The size of the saved route picture, in pixels. 900 x 600 is a 3:2 shape.
private const val IMAGE_WIDTH = 900
private const val IMAGE_HEIGHT = 600

// ---------------------------------------------------------------------------
// Saving a recording to the history.
// ---------------------------------------------------------------------------

// What happened when we tried to add a recording to a workout.
// ADDED means it went into the workout that was already in progress, and STARTED_NEW
// means there wasn't one, so a new workout was started for it.
enum class AddToWorkoutResult { ADDED, STARTED_NEW, FAILED }

// What adding a recording to a workout did, and the saved activity's id (null if it failed),
// for checking it for new records.
data class AddedRecording(val result: AddToWorkoutResult, val exerciseId: Long?)

// The parts of a recording that are worked out before anything is stored.
// elevations has the elevation to store for each track point, in the same order.
private class PreparedRecording(val durationMillis: Long, val imagePath: String?, val elevations: List<Float?>)

// Works out how long the recording ran, and makes the route picture. Call this from the
// main thread (a Compose coroutine is fine), because making the map picture has to
// happen there.
private suspend fun prepareRecording(context: Context, state: RecordingState): PreparedRecording {
    // How long the recording actually ran. Paused time and the time since stopping
    // are left out.
    val durationMillis = state.elapsedMillis(System.currentTimeMillis())

    // A picture of the route, for GPS activities with at least two points to join up.
    var imagePath: String? = null
    if (state.activity.usesGps && state.route.size >= 2) {
        val folder = File(context.filesDir, "route-images")
        folder.mkdirs()
        val file = File(folder, "route_${state.startTimeMillis}.jpg")
        if (createRouteImage(context, state.route, file)) {
            imagePath = file.absolutePath
        }
    }
    // The best elevation there is for each reading: from the barometer, a terrain map, or
    // failing those the GPS.
    val elevations = finalElevations(context, state.trackPoints)
    return PreparedRecording(durationMillis, imagePath, elevations)
}

// Builds the exercise that holds a recorded activity's distance, duration, and picture.
private fun recordedExercise(
    workoutId: Long,
    state: RecordingState,
    prepared: PreparedRecording
): Exercise {
    return Exercise(
        workoutId = workoutId,
        name = state.activity.label,
        type = state.activity.type,
        // Distance is kept in kilometers, rounded to the nearest meter.
        // A Timer has no GPS, so it has no distance.
        distance = if (state.activity.usesGps) {
            state.totalMeters.roundToLong() / 1000.0
        } else {
            null
        },
        durationSeconds = prepared.durationMillis / 1000,
        routeImagePath = prepared.imagePath,
        // Marks it as recorded, so its details can't be edited later.
        isRecorded = true
    )
}

// Stores a recorded activity: its exercise row, and every track point of the recording.
// Call this inside a transaction, so the exercise and its points are saved together.
// Returns the new exercise's id.
private suspend fun insertRecordedActivity(
    dao: WorkoutDao,
    workoutId: Long,
    state: RecordingState,
    prepared: PreparedRecording
): Long {
    // insertExercise returns the new exercise's id, which the points need.
    // It goes at the end of the workout's list of exercises.
    val exerciseId = dao.insertExercise(
        recordedExercise(workoutId, state, prepared)
            .copy(position = dao.nextExercisePosition(workoutId))
    )

    // Every reading is stored with its position, elevation, distance, and time together.
    // Nothing is smoothed or thinned beforehand: the splits, the elevation graph, and the
    // route can all be rebuilt from these later.
    val points = state.trackPoints.mapIndexed { index, it ->
        ExerciseTrackPoint(
            exerciseId = exerciseId,
            latitude = it.latitude,
            longitude = it.longitude,
            elevationMeters = prepared.elevations.getOrNull(index) ?: it.elevationMeters,
            distanceMeters = it.distanceMeters,
            elapsedMillis = it.elapsedMillis
        )
    }
    if (points.isNotEmpty()) {
        dao.insertTrackPoints(points)
    }
    // Its fastest times over each record distance, worked out now and kept, so records and the
    // calendar's medals never have to go through the readings again.
    BestEffortCache.saveBestEfforts(dao, exerciseId, points.mapNotNull { it.toTrackPointOrNull() })
    return exerciseId
}

// Saves a finished recording as its own workout in the history, with one exercise holding
// the activity's distance, duration, and a picture of the route on the map.
// Returns the saved activity's id, or null if it didn't work.
suspend fun saveRecording(context: Context, state: RecordingState): Long? {
    val db = WorkoutDatabase.getInstance(context)
    val dao = db.workoutDao()
    val prepared = prepareRecording(context, state)

    return try {
        // withTransaction makes both rows save together or not at all.
        val exerciseId = db.withTransaction {
            val workoutId = dao.insertWorkout(
                Workout(
                    startTime = state.startTimeMillis,
                    // The end time is the start plus the time actually spent recording,
                    // so the history's duration matches the timer you saw.
                    endTime = state.startTimeMillis + prepared.durationMillis,
                    // Named for the time of day and the activity, like "Afternoon Run".
                    name = recordingWorkoutName(state.startTimeMillis, state.activity.workoutName)
                )
            )
            insertRecordedActivity(dao, workoutId, state, prepared)
        }
        // The new activity goes into the backup folder shortly.
        AutoBackup.backupSoon(context)
        exerciseId
    } catch (e: SQLException) {
        // The picture file is no use without its database row, so remove it.
        prepared.imagePath?.let { File(it).delete() }
        null
    }
}

// Adds a finished recording to the workout that's in progress. If there isn't one, a new
// workout is started for it. The new workout begins when the activity began, and stays
// in progress, so more exercises can be added to it and it can be ended from the
// Workout tab like any other.
suspend fun addRecordingToWorkout(context: Context, state: RecordingState): AddedRecording {
    val db = WorkoutDatabase.getInstance(context)
    val dao = db.workoutDao()
    val prepared = prepareRecording(context, state)

    return try {
        // The whole thing is one transaction, so another change can't slip in between
        // looking for a workout and adding to it.
        db.withTransaction {
            val active = dao.getActiveWorkout()
            // "?:" means: use the workout in progress, or if there isn't one (null),
            // start a new workout (no end time, so it counts as in progress).
            val workoutId = active?.id ?: dao.insertWorkout(Workout(startTime = state.startTimeMillis))
            val exerciseId = insertRecordedActivity(dao, workoutId, state, prepared)

            AddedRecording(if (active != null) AddToWorkoutResult.ADDED else AddToWorkoutResult.STARTED_NEW, exerciseId)
        }
    } catch (e: SQLException) {
        prepared.imagePath?.let { File(it).delete() }
        AddedRecording(AddToWorkoutResult.FAILED, null)
    }
}

// ---------------------------------------------------------------------------
// Deleting history entries together with their route pictures.
// ---------------------------------------------------------------------------

// Deletes a workout, and the route picture files that belonged to it.
suspend fun deleteWorkoutAndImages(dao: WorkoutDao, workoutId: Long) {
    // The paths have to be read first, because deleting the workout deletes the rows
    // that hold them.
    val paths = dao.getRouteImagePaths(workoutId)
    dao.deleteWorkout(workoutId)
    deleteImageFiles(paths)
}

// File work is done on the IO thread, so the screen never freezes while it happens.
suspend fun deleteImageFiles(paths: List<String>) {
    withContext(Dispatchers.IO) {
        paths.forEach { File(it).delete() }
    }
}

// ---------------------------------------------------------------------------
// Showing a saved route picture.
// ---------------------------------------------------------------------------

// Loads a route picture from its file and shows it. Nothing is drawn if the file is
// missing, for example if it was deleted.
@Composable
fun RouteImage(path: String, modifier: Modifier = Modifier, cornerRadius: Dp = 12.dp) {
    // produceState runs the block in a coroutine and gives back the result as state.
    // Loading the picture happens on the IO thread so it can't freeze the screen,
    // and it starts over if the path changes.
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }

    // The picture's space is kept while it loads, in its own shape, so nothing below it jumps
    // down when it appears. It fades in over a plain block.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(IMAGE_WIDTH.toFloat() / IMAGE_HEIGHT)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Crossfade(targetState = bitmap, label = "route") { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Route map",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Making the route picture.
// ---------------------------------------------------------------------------

// Draws the route to a picture and saves it as a file. It tries the real map first, and
// falls back to the bare route line if the map can't be loaded (for example, with no
// signal), so there's always a picture.
private suspend fun createRouteImage(context: Context, route: List<LatLon>, file: File): Boolean {
    val bitmap = renderMapSnapshot(context, route) ?: drawRouteOnly(route)

    // Writing the file is done on the IO thread.
    return withContext(Dispatchers.IO) {
        try {
            // "use" closes the file when it's done, even if something goes wrong.
            // compress returns true if the picture was written.
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: IOException) {
            false
        }
    }
}

// Takes a picture of the map zoomed to fit the whole route, with the route drawn on it.
// It builds a map that is never put on screen, points it at the route, and uses
// osmdroid's MapSnapshot to wait for the map tiles to load and then draw everything
// into a picture. Returns null if that takes longer than 20 seconds, which is what
// happens when the tiles can't be downloaded.
private suspend fun renderMapSnapshot(context: Context, route: List<LatLon>): Bitmap? {
    configureOsmdroid(context)

    // A map that isn't on screen still needs a size, so we give it one by hand.
    val mapView = MapView(context)
    mapView.setTileSource(appTileSource)
    mapView.measure(
        View.MeasureSpec.makeMeasureSpec(IMAGE_WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(IMAGE_HEIGHT, View.MeasureSpec.EXACTLY)
    )
    mapView.layout(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT)

    val points = route.map { GeoPoint(it.latitude, it.longitude) }

    // Zoom so the whole route fits, with 80 pixels of space around it, but never closer
    // than zoom level 18 (about building level) for a very short route.
    // The zoom is set first, because the line's width and how much it's simplified both
    // depend on how far zoomed out the picture is.
    val box = BoundingBox.fromGeoPoints(points)
    mapView.zoomToBoundingBox(box, false, 80)
    if (mapView.zoomLevelDouble > 18.0) {
        mapView.controller.setZoom(18.0)
    }
    // A moment for the map to settle on its new view before anything else is done.
    delay(100.milliseconds)

    val zoom = mapView.zoomLevelDouble

    // Thin out the route for the picture. When the whole route is squeezed into a
    // 900 pixel wide picture, neighboring points land less than a pixel apart, and a line
    // through that many points looks jagged. Points that lie within about 1.5 pixels
    // of the straight line between their neighbors are dropped, which can't be seen.
    // Only this copy for the picture is thinned. The saved distance uses the full route.
    val toleranceMeters = metersPerPixel(box.centerLatitude, zoom) * 1.5
    val simplified = simplifyRoute(route, toleranceMeters)

    // The line is thin when zoomed out and thicker when zoomed in. A fixed width looked
    // far too heavy next to points that are only a pixel or two apart.
    val lineWidth = (3.0 + (zoom - 12.0) * 1.2).coerceIn(3.0, 10.0).toFloat()

    // The route line, with a green dot at the start and a red dot at the end.
    val line = Polyline().apply {
        setPoints(simplified.map { GeoPoint(it.latitude, it.longitude) })
        outlinePaint.color = 0xFF1E88E5.toInt()
        outlinePaint.strokeWidth = lineWidth
        // Round corners and ends. Pointed corners poke out as spikes on a thick line.
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.isAntiAlias = true
    }
    mapView.overlays.add(line)
    mapView.overlays.add(dotMarker(mapView, points.first(), 0xFF2E7D32.toInt()))
    mapView.overlays.add(dotMarker(mapView, points.last(), 0xFFC62828.toInt()))

    var snapshot: MapSnapshot? = null
    try {
        val bitmap = withTimeoutOrNull(20_000L.milliseconds) {
            // suspendCancellableCoroutine turns a "call me back when it's done" style
            // of code into something a coroutine can simply wait for.
            suspendCancellableCoroutine<Bitmap?> { continuation ->
                val created = MapSnapshot(
                    { finished ->
                        if (continuation.isActive) {
                            continuation.resume(finished.bitmap)
                        }
                    },
                    // Only accept tiles that are fully loaded, so the picture isn't
                    // blurry or blank. The cost is that it waits for the download.
                    MapSnapshot.INCLUDE_FLAG_UPTODATE or MapSnapshot.INCLUDE_FLAG_EXPIRED,
                    mapView
                )
                snapshot = created
                // Starts the snapshot.
                created.run()
            }
        }
        // OpenStreetMap's license requires a credit wherever its map appears.
        bitmap?.let { drawAttribution(it) }
        return bitmap
    } finally {
        // Releases the map's tile downloader, whether it worked or not.
        val finishedSnapshot = snapshot
        if (finishedSnapshot != null) {
            finishedSnapshot.onDetach()
        } else {
            mapView.onDetach()
        }
    }
}

// How many meters one pixel covers on the map at a given zoom level and latitude.
// A zoom level halves the size of a pixel each time it goes up by one, and pixels cover
// less ground the farther the map is from the equator.
private fun metersPerPixel(latitude: Double, zoom: Double): Double {
    return 156543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom)
}

// Thins out a route using the Ramer-Douglas-Peucker method, which keeps the shape of the
// route and drops points that don't add anything to it.
// It works like this: draw a straight line from the first point to the last. Find the
// point farthest from that line. If it's closer than the tolerance, every point between
// them can be dropped. If not, keep it, and repeat for each of the two halves.
// The first and last points are always kept.
private fun simplifyRoute(points: List<LatLon>, toleranceMeters: Double): List<LatLon> {
    if (points.size < 3) return points

    // keep[i] says whether point number i survives.
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true

    // The stretches of the route still to be checked, as (start, end) pairs of positions.
    // A list we add to and take from is used instead of a function calling itself,
    // so a very long route can never run out of room.
    val stretches = ArrayDeque<Pair<Int, Int>>()
    stretches.addLast(0 to points.lastIndex)

    while (stretches.isNotEmpty()) {
        val (start, end) = stretches.removeLast()

        // Find the point in this stretch that sticks out the farthest.
        var farthestIndex = -1
        var farthestDistance = 0.0
        for (i in start + 1 until end) {
            val distance = distanceToSegmentMeters(points[i], points[start], points[end])
            if (distance > farthestDistance) {
                farthestDistance = distance
                farthestIndex = i
            }
        }

        // If it sticks out far enough to matter, keep it and check each side of it.
        if (farthestIndex != -1 && farthestDistance > toleranceMeters) {
            keep[farthestIndex] = true
            stretches.addLast(start to farthestIndex)
            stretches.addLast(farthestIndex to end)
        }
    }

    // Only the points marked to keep are returned.
    return points.filterIndexed { index, _ -> keep[index] }
}

// How far a point is from the straight line between two others, in meters.
private fun distanceToSegmentMeters(point: LatLon, start: LatLon, end: LatLon): Double {
    // Positions are converted to flat meters, measured from the start of the line. That's
    // accurate enough over the short distances involved here. A degree of longitude is
    // shorter than a degree of latitude away from the equator, hence the cos().
    val metersPerDegree = 111_320.0
    val lonScale = cos(Math.toRadians(start.latitude)) * metersPerDegree

    val endX = (end.longitude - start.longitude) * lonScale
    val endY = (end.latitude - start.latitude) * metersPerDegree
    val pointX = (point.longitude - start.longitude) * lonScale
    val pointY = (point.latitude - start.latitude) * metersPerDegree

    val lengthSquared = endX * endX + endY * endY
    // The two ends are the same spot, so it's just the distance to that spot.
    if (lengthSquared == 0.0) return hypot(pointX, pointY)

    // How far along the line the closest spot is: 0 is the start, 1 is the end.
    // Holding it between 0 and 1 stops it from measuring to the line's extension.
    val along = ((pointX * endX + pointY * endY) / lengthSquared).coerceIn(0.0, 1.0)

    return hypot(pointX - along * endX, pointY - along * endY)
}

// A colored dot with a white edge, used for the start and end of the route.
private fun dotMarker(mapView: MapView, point: GeoPoint, color: Int): Marker {
    val dot = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(6, android.graphics.Color.WHITE)
        setSize(44, 44)
    }
    return Marker(mapView).apply {
        icon = dot
        // Centers the dot on the position, instead of putting its bottom edge there.
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        position = point
    }
}

// Writes "© OpenStreetMap contributors" in the bottom right corner of the picture.
private fun drawAttribution(bitmap: Bitmap) {
    val canvas = Canvas(bitmap)
    val text = "\u00A9 OpenStreetMap contributors"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF222222.toInt()
        textSize = 22f
    }
    // A mostly white strip behind the text, so it's readable on any map colors.
    val backgroundPaint = Paint().apply { color = 0xCCFFFFFF.toInt() }

    val padding = 8f
    val textWidth = textPaint.measureText(text)
    val left = bitmap.width - textWidth - 2 * padding
    val top = bitmap.height - 22f - 2 * padding

    canvas.drawRect(left, top, bitmap.width.toFloat(), bitmap.height.toFloat(), backgroundPaint)
    canvas.drawText(text, left + padding, bitmap.height - padding - 4f, textPaint)
}

// A simple picture of just the route: a line on a plain background. It's used when the
// map can't be loaded. It scales the route to fit the picture, keeping its shape.
private fun drawRouteOnly(route: List<LatLon>): Bitmap {
    val bitmap = createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFFE8EAED.toInt())

    val minLat = route.minOf { it.latitude }
    val maxLat = route.maxOf { it.latitude }
    val minLon = route.minOf { it.longitude }
    val maxLon = route.maxOf { it.longitude }

    // A degree of longitude is shorter than a degree of latitude, except at the equator.
    // Scaling by cos(latitude) stops the route being stretched sideways.
    val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
    val spanX = ((maxLon - minLon) * lonScale).coerceAtLeast(0.000001)
    val spanY = (maxLat - minLat).coerceAtLeast(0.000001)

    val margin = 60f
    val scale = minOf(
        (IMAGE_WIDTH - 2 * margin) / spanX,
        (IMAGE_HEIGHT - 2 * margin) / spanY
    )
    // Offsets that center the route in the picture.
    val offsetX = (IMAGE_WIDTH - spanX * scale) / 2
    val offsetY = (IMAGE_HEIGHT - spanY * scale) / 2

    // Converts a position to a spot in the picture. North is up, so y runs upwards.
    fun toX(lon: Double): Float = (offsetX + (lon - minLon) * lonScale * scale).toFloat()
    fun toY(lat: Double): Float = (IMAGE_HEIGHT - offsetY - (lat - minLat) * scale).toFloat()

    val path = Path()
    route.forEachIndexed { index, point ->
        if (index == 0) {
            path.moveTo(toX(point.longitude), toY(point.latitude))
        } else {
            path.lineTo(toX(point.longitude), toY(point.latitude))
        }
    }

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E88E5.toInt()
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawPath(path, linePaint)

    // Green dot at the start, red dot at the end.
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val first = route.first()
    val last = route.last()
    dotPaint.color = 0xFF2E7D32.toInt()
    canvas.drawCircle(toX(first.longitude), toY(first.latitude), 16f, dotPaint)
    dotPaint.color = 0xFFC62828.toInt()
    canvas.drawCircle(toX(last.longitude), toY(last.latitude), 16f, dotPaint)

    return bitmap
}
