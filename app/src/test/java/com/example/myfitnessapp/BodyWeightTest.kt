package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BodyWeightTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 10)
    private fun on(day: LocalDate, kg: Double) =
        BodyWeight(timeMillis = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), weightKg = kg)

    private val log = listOf(
        on(today, 80.0),
        on(today.minusDays(20), 81.0),
        on(today.minusMonths(2), 82.0),
        on(today.minusMonths(8), 84.0)
    )

    @Test
    fun aMonthShowsTheLastMonthOldestFirst() {
        val (start, shown) = weightsInPeriod(log, WeightPeriod.MONTH, today)
        assertEquals(today.minusMonths(1), start)
        assertEquals(listOf(81.0, 80.0), shown.map { it.weightKg })
    }

    @Test
    fun longerPeriodsReachFurtherBack() {
        assertEquals(3, weightsInPeriod(log, WeightPeriod.THREE_MONTHS, today).second.size)
        assertEquals(3, weightsInPeriod(log, WeightPeriod.SIX_MONTHS, today).second.size)
        assertEquals(4, weightsInPeriod(log, WeightPeriod.YEAR, today).second.size)
    }

    @Test
    fun allTimeStartsAtTheFirstEntry() {
        val (start, shown) = weightsInPeriod(log, WeightPeriod.ALL, today)
        assertEquals(today.minusMonths(8), start)
        assertEquals(4, shown.size)
    }
}
