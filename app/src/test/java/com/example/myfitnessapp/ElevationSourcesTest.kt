package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElevationSourcesTest {
    private fun point(elapsedMillis: Long, gps: Float?, pressure: Float?, distance: Float = 0f) =
        TrackPoint(0.0, 0.0, gps, distance, elapsedMillis, pressure)

    @Test
    fun barometerShapeIsPinnedToTheGpsLevel() {
        // The barometer climbs steadily from 50 to 54. The GPS says about 100 more, with one
        // wild reading that the median ignores.
        val points = listOf(
            point(0, gps = 150f, pressure = 50f),
            point(1_000, gps = 151f, pressure = 51f),
            point(2_000, gps = 190f, pressure = 52f),
            point(3_000, gps = 153f, pressure = 53f),
            point(4_000, gps = 154f, pressure = 54f)
        )
        assertEquals(listOf(150f, 151f, 152f, 153f, 154f), barometerElevations(points))
    }

    @Test
    fun readingsFromBeforeTheBarometerStartedKeepTheirGpsHeight() {
        val points = listOf(
            point(0, gps = 120f, pressure = null),
            point(1_000, gps = 100f, pressure = 10f),
            point(2_000, gps = 101f, pressure = 11f)
        )
        assertEquals(listOf(120f, 100f, 101f), barometerElevations(points))
    }

    @Test
    fun noBarometerMeansNoAnswer() {
        val points = listOf(point(0, 100f, null), point(1_000, 101f, null))
        assertNull(barometerElevations(points))
        // Nor when it was only on for a small part of the recording.
        assertNull(barometerElevations(listOf(point(0, 1f, null), point(1, 1f, null), point(2, 1f, 5f))))
    }

    @Test
    fun weatherDriftIsTakenOutOfALongRecording() {
        // Flat ground at 100 m for an hour. The pressure drifts, so the barometer's height
        // falsely climbs 12 m. The GPS stays at 100.
        val points = (0..60).map { minute ->
            point(minute * 60_000L, gps = 100f, pressure = 40f + minute * 0.2f)
        }
        val elevations = barometerElevations(points)!!
        elevations.forEach { assertEquals(100f, it!!, 0.01f) }
    }

    @Test
    fun readsTheTerrainServicesAnswer() {
        assertEquals(listOf(38f, 41.5f, -2f), parseTerrainElevations("""{"elevation":[38.0, 41.5,-2.0]}"""))
        assertNull(parseTerrainElevations("""{"error":true,"reason":"too many"}"""))
        assertNull(parseTerrainElevations("""{"elevation":[38.0,null]}"""))
        assertNull(parseTerrainElevations("""{"elevation":[]}"""))
    }

    @Test
    fun fillsInTheHeightsBetweenTheOnesLookedUp() {
        val heights = fillInByDistance(
            knownAt = listOf(0f, 100f, 300f),
            heights = listOf(10f, 20f, 0f),
            askedAt = listOf(0f, 50f, 100f, 200f, 300f)
        )
        assertEquals(listOf(10f, 15f, 20f, 10f, 0f), heights)
    }

    @Test
    fun spreadsThePlacesAskedAboutAlongTheWholeRoute() {
        val picked = spreadEvenly((0..999).toList(), 100)
        assertEquals(100, picked.size)
        assertEquals(0, picked.first())
        assertEquals(999, picked.last())
        assertEquals(listOf(1, 2, 3), spreadEvenly(listOf(1, 2, 3), 100))
    }
}
