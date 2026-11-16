package com.example.myfitnessapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat

// ---------------------------------------------------------------------------
// Telling whether the user is walking or standing still, from their steps.
// ---------------------------------------------------------------------------
// GPS is poor at this: a phone standing still drifts about, which can look like slow walking.
// The phone's step sensor feels each footfall, so it knows straight away when the user has
// stopped. It's used for walks and runs. A bike ride has no steps, so it keeps using GPS speed.

// A step this recent means the user is on the move.
private const val STEP_RECENT_MS = 3_000L

// With no steps felt, a GPS speed this fast (in meters per second, a slow walk) still counts
// as moving. It covers a phone the sensor can't feel steps on, such as one on a pushchair.
const val MOVING_WITHOUT_STEPS_SPEED = 1.0f

// How long without a step before a walk or run pauses itself.
const val STEP_AUTO_PAUSE_AFTER_MS = 10_000L

// To end an auto-pause: this many steps, all within this long. One stray step (shifting your
// weight at a crossing) isn't enough.
private const val RESUME_STEPS = 3
private const val RESUME_WINDOW_MS = 5_000L

// Whether the user is moving right now.
// lastStepMillis is when the last step was felt, or when watching began if there hasn't been one.
// gpsSpeed is in meters per second, and speedIsTrusted says whether the GPS is sure of it.
fun isMovingBySteps(nowMillis: Long, lastStepMillis: Long, gpsSpeed: Float?, speedIsTrusted: Boolean): Boolean {
    if (nowMillis - lastStepMillis <= STEP_RECENT_MS) return true
    return speedIsTrusted && gpsSpeed != null && gpsSpeed >= MOVING_WITHOUT_STEPS_SPEED
}

// Whether enough steps have just been taken to end an auto-pause.
fun stepsSayResume(nowMillis: Long, stepTimes: List<Long>): Boolean =
    stepTimes.count { nowMillis - it <= RESUME_WINDOW_MS } >= RESUME_STEPS

// Listens to the step sensor while a walk or run is recording.
object StepWatcher : SensorEventListener {
    private var sensorManager: SensorManager? = null

    // When each recent step was felt, oldest first, as clock times in milliseconds.
    private val stepTimes = ArrayDeque<Long>()
    private var watchingSince = 0L

    // True while steps are being listened for. When false, the recording uses GPS speed.
    val isWatching: Boolean get() = sensorManager != null

    // When the last step was felt, or when watching began if there hasn't been one yet.
    val lastStepMillis: Long get() = stepTimes.lastOrNull() ?: watchingSince

    val recentStepTimes: List<Long> get() = stepTimes.toList()

    // Starts listening. Does nothing if the phone has no step sensor, or the user hasn't
    // allowed "Physical activity": the recording then carries on with GPS speed alone.
    fun start(context: Context) {
        if (isWatching) return
        val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted) return
        val manager = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return

        stepTimes.clear()
        watchingSince = System.currentTimeMillis()
        // The 0 asks for each step as it happens, not saved up and delivered in batches.
        if (manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0)) {
            sensorManager = manager
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        stepTimes.clear()
    }

    // Android calls this once for each step.
    override fun onSensorChanged(event: SensorEvent) {
        stepTimes.addLast(System.currentTimeMillis())
        // Only the last few are ever looked at.
        while (stepTimes.size > 10) stepTimes.removeFirst()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
