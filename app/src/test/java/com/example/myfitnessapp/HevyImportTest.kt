package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class HevyImportTest {
    private val header =
        "\"title\",\"start_time\",\"end_time\",\"description\",\"exercise_title\",\"superset_id\",\"exercise_notes\",\"set_index\",\"set_type\",\"weight_lbs\",\"reps\",\"distance_km\",\"duration_seconds\",\"rpe\"\n"

    // Made-up rows in the same form as a Hevy export.
    private val export = header + """
"Upper Body","12 Mar 2026, 09:05","12 Mar 2026, 10:10","","Bench Press (Barbell)",,"Paused, felt good",0,"warmup",135,10,,,
"Upper Body","12 Mar 2026, 09:05","12 Mar 2026, 10:10","","Bench Press (Barbell)",,"Paused, felt good",1,"normal",225.5,5,,,8.5
"Upper Body","12 Mar 2026, 09:05","12 Mar 2026, 10:10","","Pull Up",,"",0,"normal",,12,,,
"Upper Body","12 Mar 2026, 09:05","12 Mar 2026, 10:10","","rowing Machine",,"",0,"normal",,,,217,
"Upper Body","12 Mar 2026, 09:05","12 Mar 2026, 10:10","","rowing Machine",,"",1,"normal",,,0.4,236,
"Upper Body","12 Mar 2026, 09:05","12 Mar 2026, 10:10","","Butterfly (Pec Deck)",,"",0,"normal",100,12,,,
"Upper Body","12 Mar 2026, 09:05","12 Mar 2026, 10:10","","Bench Press (Barbell)",,"",0,"normal",185,8,,,
"Push","14 Jun 2024, 18:00","14 Jun 2024, 19:15","","Dead Hang",,"",0,"normal",,,,50,
"Push","14 Jun 2024, 18:00","14 Jun 2024, 19:15","","Running",,"",0,"normal",,,5.2,1800,
""".trimStart()

    private val workouts = parseHevyExport(export, ZoneOffset.UTC)

    @Test
    fun workoutsAreGroupedAndOldestFirst() {
        assertEquals(listOf("Push", "Upper Body"), workouts.map { it.name })
        val push = workouts[0]
        assertEquals(LocalDateTime.of(2024, 6, 14, 18, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), push.startTime)
        assertEquals(75 * 60_000L, push.endTime - push.startTime)
    }

    @Test
    fun strengthSetsKeepWarmupsAndConvertPoundsToKilograms() {
        val bench = workouts[1].exercises[0]
        assertEquals("Bench Press (Barbell)", bench.name)
        assertEquals(ActivityType.STRENGTH, bench.type)
        assertEquals(2, bench.sets.size)
        assertEquals(135 * 0.45359237, bench.sets[0].weightKg!!, 1e-9)
        assertEquals(225.5 * 0.45359237, bench.sets[1].weightKg!!, 1e-9)
        assertEquals(5.0, bench.sets[1].reps!!, 1e-9)
        assertEquals(8.5, bench.sets[1].effort!!, 1e-9)
        assertEquals("Paused, felt good", bench.notes)
    }

    @Test
    fun bodyweightSetsHaveNoWeight() {
        val pullUp = workouts[1].exercises[1]
        assertNull(pullUp.sets.single().weightKg)
        assertEquals(12.0, pullUp.sets.single().reps!!, 1e-9)
    }

    @Test
    fun cardioRowsAreAddedTogetherUnderTheAppsName() {
        val rowing = workouts[1].exercises[2]
        assertEquals("Rowing Machine", rowing.name)
        assertEquals(ActivityType.CARDIO, rowing.type)
        assertEquals(453L, rowing.durationSeconds)
        assertEquals(0.4, rowing.distanceKm!!, 1e-9)
    }

    @Test
    fun aliasesAndRepeatedExercises() {
        val names = workouts[1].exercises.map { it.name }
        assertEquals(listOf("Bench Press (Barbell)", "Pull Up", "Rowing Machine", "Pec Deck Fly", "Bench Press (Barbell)"), names)
    }

    @Test
    fun runsAndTimedHolds() {
        val run = workouts[0].exercises[1]
        assertEquals(ActivityType.RUN, run.type)
        assertEquals(5.2, run.distanceKm!!, 1e-9)
        assertEquals(1800L, run.durationSeconds)
        // Dead Hang is a strength exercise; a set with only a time has nothing to keep.
        assertEquals(0, workouts[0].exercises[0].sets.size)
    }

    @Test(expected = ImportException::class)
    fun otherFilesAreRefused() {
        parseHevyExport("name,date\nSquat,today\n")
    }

    @Test
    fun csvQuotesAndCommas() {
        assertEquals(listOf(listOf("a", "b, c", "say \"hi\"", "")), parseCsv("a,\"b, c\",\"say \"\"hi\"\"\",\n"))
    }
}
