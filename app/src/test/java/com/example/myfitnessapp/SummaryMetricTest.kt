package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryMetricTest {
    private val workout = WorkoutStats(startTime = 0L, endTime = 90 * 60_000L, volumeKg = 1000.0, distanceKm = 5.25)
    private val empty = WorkoutStats(startTime = 0L, endTime = 60_000L, volumeKg = null, distanceKm = null)

    @Test
    fun timeIsTheWorkoutLengthInMinutes() = assertEquals(90.0, SummaryMetric.TIME.amount(workout, WeightUnit.KG), 1e-9)

    @Test
    fun volumeFollowsTheWeightUnit() {
        assertEquals(1000.0, SummaryMetric.VOLUME.amount(workout, WeightUnit.KG), 1e-9)
        assertEquals(2204.6, SummaryMetric.VOLUME.amount(workout, WeightUnit.LBS), 0.1)
    }

    @Test
    fun distanceIsInKilometers() = assertEquals(5.25, SummaryMetric.DISTANCE.amount(workout, WeightUnit.LBS), 1e-9)

    @Test
    fun missingVolumeAndDistanceCountAsZero() {
        assertEquals(0.0, SummaryMetric.VOLUME.amount(empty, WeightUnit.LBS), 1e-9)
        assertEquals(0.0, SummaryMetric.DISTANCE.amount(empty, WeightUnit.LBS), 1e-9)
    }

    @Test
    fun heaviestSetFollowsTheWeightUnitAndIsABestOf() {
        val lift = WorkoutStats(startTime = 0L, endTime = 0L, volumeKg = null, distanceKm = null, heaviestKg = 100.0)
        assertEquals(100.0, SummaryMetric.HEAVIEST_SET.amount(lift, WeightUnit.KG), 1e-9)
        assertEquals(220.5, SummaryMetric.HEAVIEST_SET.amount(lift, WeightUnit.LBS), 0.1)
        assertEquals("220.5 lbs", SummaryMetric.HEAVIEST_SET.format(220.46, WeightUnit.LBS))
        assertEquals(false, SummaryMetric.HEAVIEST_SET.addsUp)
        assertEquals(true, SummaryMetric.VOLUME.addsUp)
    }

    @Test
    fun niceStepsRoundUp() {
        assertEquals(1.0, niceStep(0.0), 1e-9)
        assertEquals(1.0, niceStep(0.8), 1e-9)
        assertEquals(2.5, niceStep(2.1), 1e-9)
        assertEquals(5.0, niceStep(5.0), 1e-9)
        assertEquals(5000.0, niceStep(3100.0), 1e-9)
    }

    @Test
    fun gridStepsKeepFourOrFewerLines() {
        assertEquals(15.0, SummaryMetric.TIME.gridStep(32.0), 1e-9)
        assertEquals(5000.0, SummaryMetric.VOLUME.gridStep(12_500.0), 1e-9)
        assertEquals(2.0, SummaryMetric.DISTANCE.gridStep(7.5), 1e-9)
    }

    @Test
    fun formats() {
        assertEquals("1h 30m", SummaryMetric.TIME.format(90.0, WeightUnit.LBS))
        assertEquals("5.3 km", SummaryMetric.DISTANCE.format(5.25, WeightUnit.LBS))
        assertEquals("12.5k", SummaryMetric.VOLUME.axisLabel(12_500.0))
        assertEquals("750", SummaryMetric.VOLUME.axisLabel(750.0))
        assertEquals("0.3", SummaryMetric.DISTANCE.axisLabel(0.1 * 3))
    }
}
