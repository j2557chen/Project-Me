package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test

class PrDaysTest {
    private fun set(id: Long, workout: Long, kg: Double, reps: Double, number: Int = 1) =
        StrengthSet(id, workout, startTime = workout * 1000, setNumber = number, weightKg = kg, reps = reps)

    private fun session(exercise: Long, workout: Long, km: Double?, seconds: Long?, efforts: Map<Int, Long> = emptyMap()) =
        ActivitySession(exercise, workout, startTime = workout * 1000, distanceKm = km, durationSeconds = seconds, efforts = efforts)

    @Test
    fun firstWorkoutAndTiesAreNotRecords() {
        val bench = listOf(set(1, 1, 100.0, 5.0), set(2, 2, 100.0, 5.0))
        assertEquals(emptySet<Long>(), prWorkoutIds(mapOf("Bench" to bench), emptyMap()))
    }

    @Test
    fun heavierSetOrMoreVolumeIsARecordDay() {
        val bench = listOf(
            set(1, 1, 100.0, 5.0),
            set(2, 2, 105.0, 3.0),        // heaviest weight
            set(3, 3, 90.0, 5.0),         // nothing
            set(4, 4, 100.0, 5.5)         // more volume thanks to a half rep
        )
        assertEquals(setOf(2L, 4L), prWorkoutIds(mapOf("Bench" to bench), emptyMap()))
    }

    @Test
    fun matchesTheBadgesOnEachWorkout() {
        val squat = listOf(
            set(1, 1, 140.0, 5.0), set(2, 1, 140.0, 5.0, number = 2),
            set(3, 2, 130.0, 5.0), set(4, 2, 130.0, 5.0, number = 2), set(5, 2, 130.0, 5.0, number = 3),
            set(6, 3, 120.0, 3.0)
        )
        val expected = (1L..3L).filter {
            val flags = strengthPrFlags(squat, it)
            flags.prSetIds.isNotEmpty() || flags.sessionVolumePr
        }.toSet()
        assertEquals(expected, prWorkoutIds(mapOf("Squat" to squat), emptyMap()))
        assertEquals(setOf(2L), expected) // session volume only
    }

    @Test
    fun activityRecordsCountOnlyWhereTheTypeTracksThem() {
        val runs = listOf(
            session(10, 1, 5.0, 1800, mapOf(3 to 1000_000L, 5 to 1800_000L)),
            session(11, 2, 6.0, 2400),                                     // farthest and longest
            session(12, 3, 4.0, 1500, mapOf(3 to 990_000L)),               // fastest 3 km
            session(13, 4, 3.0, 1000)                                      // nothing
        )
        assertEquals(setOf(2L, 3L), prWorkoutIds(emptyMap(), mapOf("Run" to (ActivityType.RUN to runs))))
    }

    @Test
    fun countsEachRecordInAWorkout() {
        val bench = listOf(
            set(1, 1, 100.0, 5.0),
            set(2, 2, 105.0, 5.0), set(3, 2, 110.0, 5.0, number = 2)   // two heavier sets + session volume
        )
        val runs = listOf(
            session(10, 1, 5.0, 1800, mapOf(3 to 1000_000L, 5 to 1800_000L)),
            session(11, 2, 6.0, 2400, mapOf(3 to 990_000L, 5 to 1700_000L))  // farther, longer, two fastest
        )
        val counts = prCountsByWorkout(mapOf("Bench" to bench), mapOf("Run" to (ActivityType.RUN to runs)))
        assertEquals(mapOf(2L to 7), counts)
    }
}
