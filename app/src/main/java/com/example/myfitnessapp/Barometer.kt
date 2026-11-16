package com.example.myfitnessapp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.io.File
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Elevation from air pressure.
// ---------------------------------------------------------------------------
// Many phones have a barometer, a sensor that measures air pressure. Pressure falls steadily as
// you climb, so it shows climbs and descents to within about a meter, where GPS altitude jumps
// about by 10 meters or more. What it can't tell is the height above sea level: the pressure
// also depends on the weather. So while recording, each reading keeps both the pressure's
// height and the GPS's, and when the recording is saved the two are put together
// (see barometerElevations): the shape from the barometer, the level from the GPS.

// Listens to the barometer while a GPS activity is recording.
object Barometer : SensorEventListener {
    // How many pressure readings are averaged, to smooth out blips from wind or a squeezed
    // pocket. The sensor reports about 5 a second, so this is about 2 seconds' worth.
    private const val AVERAGED_READINGS = 10

    // A pressure older than this isn't used: the sensor has stopped reporting.
    private const val STALE_AFTER_MS = 10_000L

    private var sensorManager: SensorManager? = null
    private val recent = ArrayDeque<Float>()
    private var lastReadingMillis = 0L

    // Whether this phone has a barometer. In a test build, a file named "no_barometer" in the
    // app's files folder makes it act as though it hasn't, so the other elevation sources
    // can be tried on a phone that has one.
    fun phoneHasOne(context: Context): Boolean {
        val testBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (testBuild && File(context.filesDir, "no_barometer").exists()) return false
        return context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }

    fun start(context: Context) {
        if (sensorManager != null || !phoneHasOne(context)) return
        val manager = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
        recent.clear()
        if (manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            sensorManager = manager
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        recent.clear()
    }

    // The height that goes with the current pressure, in meters, or null if there's no fresh
    // pressure. It assumes standard weather, so it's only good for comparing with itself.
    val altitudeMeters: Float?
        get() {
            if (recent.isEmpty() || System.currentTimeMillis() - lastReadingMillis > STALE_AFTER_MS) return null
            return SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, recent.average().toFloat())
        }

    // Android calls this with each pressure reading, in hectopascals.
    override fun onSensorChanged(event: SensorEvent) {
        recent.addLast(event.values[0])
        while (recent.size > AVERAGED_READINGS) recent.removeFirst()
        lastReadingMillis = System.currentTimeMillis()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// Up to this long, the weather is taken not to have changed during the recording.
private const val STEADY_WEATHER_MS = 20 * 60_000L

// The elevation of each reading, from the barometer's heights pinned to the GPS's level.
// Returns null when the barometer wasn't on for most of the recording.
//
// The gap between the GPS's height and the barometer's is found at every reading that has
// both. For a short recording the middle value of those gaps (the median, which ignores wild
// GPS readings) is added to every barometer height. For a long one the weather may have
// drifted, so a straight line is fitted through the gaps over time and that's added instead.
// A reading from before the barometer started keeps its GPS height.
fun barometerElevations(points: List<TrackPoint>): List<Float?>? {
    val withPressure = points.count { it.pressureAltitudeMeters != null }
    if (withPressure < 2 || withPressure * 2 < points.size) return null

    // Each gap, with the activity time it was measured at.
    val gaps = points.mapNotNull { point ->
        val gps = point.elevationMeters
        val pressure = point.pressureAltitudeMeters
        if (gps != null && pressure != null) point.elapsedMillis to (gps - pressure) else null
    }
    // With no GPS heights at all, the barometer's own heights are the best there is.
    val gapAt: (Long) -> Float = when {
        gaps.isEmpty() -> { _ -> 0f }
        gaps.last().first - gaps.first().first <= STEADY_WEATHER_MS -> {
            val middle = median(gaps.map { it.second })
            ({ _ -> middle })
        }
        else -> fittedLine(gaps)
    }
    return points.map { point ->
        point.pressureAltitudeMeters?.let { it + gapAt(point.elapsedMillis) } ?: point.elevationMeters
    }
}

private fun median(values: List<Float>): Float {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
}

// The straight line that best fits the gaps over time ("least squares"), as a function from a
// time to the gap there.
private fun fittedLine(gaps: List<Pair<Long, Float>>): (Long) -> Float {
    val meanTime = gaps.map { it.first.toDouble() }.average()
    val meanGap = gaps.map { it.second.toDouble() }.average()
    var above = 0.0
    var below = 0.0
    for ((time, gap) in gaps) {
        above += (time - meanTime) * (gap - meanGap)
        below += (time - meanTime) * (time - meanTime)
    }
    val slope = if (abs(below) < 1e-9) 0.0 else above / below
    return { time -> (meanGap + slope * (time - meanTime)).toFloat() }
}
