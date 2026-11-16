package com.example.myfitnessapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepWatcherTest {
    private val now = 1_000_000L

    @Test
    fun aRecentStepMeansMoving() {
        assertTrue(isMovingBySteps(now, lastStepMillis = now - 1_000, gpsSpeed = 0f, speedIsTrusted = true))
        // Even when the GPS has no idea of the speed.
        assertTrue(isMovingBySteps(now, lastStepMillis = now - 3_000, gpsSpeed = null, speedIsTrusted = false))
    }

    @Test
    fun noStepsAndSlowGpsMeansStill() {
        // GPS drift while standing: a small speed, and no steps for a while.
        assertFalse(isMovingBySteps(now, lastStepMillis = now - 4_000, gpsSpeed = 0.4f, speedIsTrusted = true))
        assertFalse(isMovingBySteps(now, lastStepMillis = now - 60_000, gpsSpeed = null, speedIsTrusted = false))
    }

    @Test
    fun aFastTrustedGpsSpeedMeansMovingWithoutSteps() {
        // A phone on a pushchair: no steps felt, but clearly going somewhere.
        assertTrue(isMovingBySteps(now, lastStepMillis = now - 60_000, gpsSpeed = 1.4f, speedIsTrusted = true))
        // The same speed isn't believed when the GPS isn't sure of it.
        assertFalse(isMovingBySteps(now, lastStepMillis = now - 60_000, gpsSpeed = 1.4f, speedIsTrusted = false))
    }

    @Test
    fun threeQuickStepsEndAnAutoPause() {
        assertTrue(stepsSayResume(now, listOf(now - 2_000, now - 1_200, now - 400)))
    }

    @Test
    fun aStrayStepDoesNotEndAnAutoPause() {
        assertFalse(stepsSayResume(now, listOf(now - 400)))
        // Steps from before the stop don't count.
        assertFalse(stepsSayResume(now, listOf(now - 30_000, now - 29_000, now - 400)))
        assertFalse(stepsSayResume(now, emptyList()))
    }
}
