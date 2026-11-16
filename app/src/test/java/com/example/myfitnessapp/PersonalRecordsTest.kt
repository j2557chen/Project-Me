package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordsTest {
    // ----- Record distances -----

    @Test
    fun recordDistancesStepByFiveAfterFifteen() {
        assertEquals(listOf(3, 5, 10, 12, 15, 20, 25), effortDistancesKm(27.0))
        assertEquals(listOf(3, 5), effortDistancesKm(9.9))
        assertEquals(emptyList<Int>(), effortDistancesKm(2.5))
    }

    // ----- Fastest times from GPS readings -----

    // A reading every 100 m, at a steady 30 s per 100 m except for a faster stretch.
    private fun track(totalMeters: Int, fastFrom: Int, fastTo: Int, fastMillisPer100: Long): List<TrackPoint> {
        var time = 0L
        return (0..totalMeters step 100).map { d ->
            if (d > 0) time += if (d > fastFrom && d <= fastTo) fastMillisPer100 else 30_000L
            TrackPoint(0.0, 0.0, null, d.toFloat(), time)
        }
    }

    @Test
    fun bestEffortFindsTheFastStretchInTheMiddle() {
        // 8 km, with km 2 to 5 run at 20 s per 100 m instead of 30.
        val efforts = bestEfforts(track(8000, 2000, 5000, 20_000L), listOf(3, 5))
        assertEquals(30 * 20_000L, efforts[3])                 // exactly the fast 3 km
        assertEquals(30 * 20_000L + 20 * 30_000L, efforts[5])  // the fast 3 km plus 2 km at the steady pace
    }

    @Test
    fun bestEffortStartsBetweenReadings() {
        // Readings 1 km apart at 5 min per km: 3 km takes 15 min wherever it starts.
        val points = (0..4).map { TrackPoint(0.0, 0.0, null, it * 1000f, it * 300_000L) }
        assertEquals(900_000L, bestEfforts(points, listOf(3))[3])
    }

    @Test
    fun bestEffortSkipsDistancesNotCovered() {
        assertEquals(emptyMap<Int, Long>(), bestEfforts(track(2000, 0, 0, 0), listOf(3)))
    }

    // ----- Fastest times from logged activities -----

    @Test
    fun loggedActivityCountsOnlyWhenItMatchesTheDistance() {
        assertEquals(mapOf(5 to 1_500_000L), manualEfforts(5.05, 1500))
        assertEquals(emptyMap<Int, Long>(), manualEfforts(5.2, 1500))
        assertEquals(emptyMap<Int, Long>(), manualEfforts(5.0, null))
    }

    @Test
    fun walksAndRidesGetFastestTimes() {
        assertTrue(tracksFastestTimes("Run", ActivityType.RUN))
        assertTrue(tracksFastestTimes("Cycling", ActivityType.CARDIO))
        assertFalse(tracksFastestTimes("Rowing Machine", ActivityType.CARDIO))
    }

    // ----- Strength -----

    private fun set(id: Long, workout: Long, number: Int, kg: Double, reps: Int) =
        StrengthSet(id, workout, startTime = workout * 1000, setNumber = number, weightKg = kg, reps = reps.toDouble())

    private val history = listOf(
        set(1, 1, 1, 100.0, 5),  // 500
        set(2, 1, 2, 100.0, 5),  // 500, workout 1 = 1000
        set(3, 2, 1, 105.0, 3),  // heavier: 315
        set(4, 2, 2, 90.0, 8),   // more volume: 720, workout 2 = 1035
        set(5, 3, 1, 105.0, 3)   // ties the heaviest
    )

    @Test
    fun strengthRecordsKeepTheFirstToReachEachValue() {
        val records = strengthRecords(history)
        assertEquals(3L, records.heaviest?.setId)
        assertEquals(4L, records.bestSet?.setId)
        assertEquals(2L, records.bestSession?.workoutId)
        assertEquals(1035.0, records.bestSession!!.volumeKg, 1e-9)
    }

    @Test
    fun setsThatBeatEverythingBeforeThemAreRecords() {
        val flags = strengthPrFlags(history, workoutId = 2)
        assertEquals(setOf(3L, 4L), flags.prSetIds)
        assertTrue(flags.sessionVolumePr)
    }

    @Test
    fun aTieIsNotARecord() {
        val flags = strengthPrFlags(history, workoutId = 3)
        assertEquals(emptySet<Long>(), flags.prSetIds)
        assertFalse(flags.sessionVolumePr)
    }

    @Test
    fun theFirstWorkoutHasNothingToBeat() {
        assertEquals(StrengthPrFlags(), strengthPrFlags(history, workoutId = 1))
    }

    // ----- Distance and time -----

    private fun session(id: Long, km: Double?, seconds: Long?, efforts: Map<Int, Long> = emptyMap()) =
        ActivitySession(id, workoutId = id, startTime = id * 1000, distanceKm = km, durationSeconds = seconds, efforts = efforts)

    @Test
    fun activityRecordsAndFlags() {
        val runs = listOf(
            session(1, 5.0, 1500, mapOf(3 to 900_000L, 5 to 1_500_000L)),
            session(2, 10.0, 3300, mapOf(3 to 880_000L, 5 to 1_520_000L, 10 to 3_300_000L)),
            session(3, 4.0, 1200, mapOf(3 to 890_000L))
        )
        val records = activityRecords(runs)
        assertEquals(2L, records.longestDistance?.exerciseId)
        assertEquals(2L, records.longestTime?.exerciseId)
        assertEquals(listOf(3 to 880_000L, 5 to 1_500_000L, 10 to 3_300_000L),
            records.fastestTimes.map { it.distanceKm to it.millis })

        val second = activityPrFlags(runs, exerciseId = 2)
        assertTrue(second.distancePr)
        assertTrue(second.timePr)
        // Faster at 3 km; slower at 5 km; 10 km had nothing before it to beat.
        assertEquals(listOf(3), second.fastestKm)

        assertEquals(ActivityPrFlags(), activityPrFlags(runs, exerciseId = 3))
        assertEquals(ActivityPrFlags(), activityPrFlags(runs, exerciseId = 1))
    }

    // ----- What a finished workout beat, for the celebration -----

    private fun set(id: Long, workout: Long, kg: Double, reps: Double) =
        StrengthSet(id, workout, startTime = workout * 1000, setNumber = id.toInt(), weightKg = kg, reps = reps)

    @Test
    fun beatenListsOnlyWhatWasBeaten() {
        val history = listOf(
            set(1, 1, 100.0, 5.0), set(2, 1, 100.0, 5.0),   // earlier: 100 heaviest, 500 best set, 1000 session
            set(3, 2, 105.0, 3.0), set(4, 2, 90.0, 3.0)     // now: heavier, but a smaller best set and session
        )
        val beaten = strengthBeatenIn(history, 2)!!
        assertEquals(105.0, beaten.heaviestKg!!, 0.0)
        assertEquals(null, beaten.bestSet)
        assertEquals(null, beaten.sessionKg)
    }

    @Test
    fun beatenIsNullTheFirstTimeOrWhenNothingIsBeaten() {
        assertEquals(null, strengthBeatenIn(listOf(set(1, 1, 100.0, 5.0)), 1))
        val history = listOf(set(1, 1, 100.0, 5.0), set(2, 2, 80.0, 5.0))
        assertEquals(null, strengthBeatenIn(history, 2))
    }

    @Test
    fun beatenIgnoresLaterWorkouts() {
        // Editing an old workout doesn't count a record against workouts that came after it.
        val history = listOf(set(1, 1, 100.0, 5.0), set(2, 2, 110.0, 5.0), set(3, 3, 200.0, 5.0))
        val beaten = strengthBeatenIn(history, 2)!!
        assertEquals(110.0, beaten.heaviestKg!!, 0.0)
        assertEquals(550.0, beaten.bestSet!!.volumeKg, 0.0)
        assertEquals(550.0, beaten.sessionKg!!, 0.0)
    }
}
