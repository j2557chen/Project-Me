package com.example.myfitnessapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// ---------------------------------------------------------------------------
// Elevation from a map of the terrain.
// ---------------------------------------------------------------------------
// For a phone with no barometer. The ground's height at a place is known from surveys of the
// earth, and Open-Meteo (open-meteo.com) answers "how high is the ground here?" for free. Its
// map is made of squares about 90 meters across, so it's steadier than GPS altitude but can't
// show a small dip or a bridge.
//
// PRIVACY: this sends positions along the route to Open-Meteo over the internet. It's only
// done on a phone with no barometer, and only when a recording is saved.

// The service takes at most this many places in one request.
private const val MAX_LOOKUP_POINTS = 100
private const val TIMEOUT_MS = 5_000

// The elevation of each reading, working down the sources from best to worst:
//  1. The barometer, if the phone has one and it was on.
//  2. The terrain map, on a phone with no barometer.
//  3. The GPS's own altitude, if the terrain map couldn't be reached.
suspend fun finalElevations(context: Context, points: List<TrackPoint>): List<Float?> {
    barometerElevations(points)?.let { return it }
    if (!Barometer.phoneHasOne(context)) {
        terrainElevations(points)?.let { return it }
    }
    return points.map { it.elevationMeters }
}

// Looks up the ground's height along the route. Returns null if it can't (no internet, the
// service is down, or an answer that doesn't make sense), and never throws.
suspend fun terrainElevations(points: List<TrackPoint>): List<Float?>? {
    if (points.size < 2) return null
    // A long route is asked about at up to 100 places spread evenly along it, and the
    // readings between them are filled in.
    val asked = spreadEvenly(points.indices.toList(), MAX_LOOKUP_POINTS).map { points[it] }
    val heights = withContext(Dispatchers.IO) {
        try {
            // Locale.US keeps the decimal point a point, whatever the phone's language.
            val latitudes = asked.joinToString(",") { String.format(Locale.US, "%.5f", it.latitude) }
            val longitudes = asked.joinToString(",") { String.format(Locale.US, "%.5f", it.longitude) }
            val url = URL("https://api.open-meteo.com/v1/elevation?latitude=$latitudes&longitude=$longitudes")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                if (connection.responseCode != 200) return@withContext null
                parseTerrainElevations(connection.inputStream.bufferedReader().readText())
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            // No internet, a timeout, and so on: the caller falls back to GPS altitude.
            null
        }
    } ?: return null
    if (heights.size != asked.size) return null

    val elevations = fillInByDistance(asked.map { it.distanceMeters }, heights, points.map { it.distanceMeters })
    return elevations
}

// Picks at most "most" of the items, spread evenly, always with the first and the last.
fun <T> spreadEvenly(items: List<T>, most: Int): List<T> {
    if (items.size <= most) return items
    return (0 until most).map { i -> items[(i.toLong() * (items.size - 1) / (most - 1)).toInt()] }
}

// Reads the heights out of the service's answer, which looks like {"elevation":[38.0,41.5]}.
// Returns null if it isn't in that shape.
fun parseTerrainElevations(body: String): List<Float>? {
    val list = Regex("\"elevation\"\\s*:\\s*\\[([^\\]]*)]").find(body)?.groupValues?.get(1) ?: return null
    val heights = list.split(",").map { it.trim().toFloatOrNull() ?: return null }
    return heights.takeIf { it.isNotEmpty() }
}

// Given heights known at some distances along the route (in order), works out the height at
// every distance asked for, on a straight line between the two known heights around it.
fun fillInByDistance(knownAt: List<Float>, heights: List<Float>, askedAt: List<Float>): List<Float> {
    var next = 0
    return askedAt.map { distance ->
        // Move along to the first known place at or past this distance.
        while (next < knownAt.lastIndex && knownAt[next] < distance) next++
        val before = (next - 1).coerceAtLeast(0)
        val span = knownAt[next] - knownAt[before]
        if (span <= 0f || distance >= knownAt[next]) {
            heights[next]
        } else {
            val along = ((distance - knownAt[before]) / span).coerceIn(0f, 1f)
            heights[before] + (heights[next] - heights[before]) * along
        }
    }
}
