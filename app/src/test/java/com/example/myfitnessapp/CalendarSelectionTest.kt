package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarSelectionTest {
    private val nov2 = LocalDate.of(2026, 11, 2)
    private val nov3 = LocalDate.of(2026, 11, 3)
    private val nov4 = LocalDate.of(2026, 11, 4)

    @Test
    fun aTapPicksJustThatDay() {
        val s = CalendarSelection()
        s.tap(nov2)
        s.tap(nov3)
        assertEquals(setOf(nov3), s.selectedDays)
        assertFalse(s.multiSelect)
        // Tapping the picked day again unpicks it.
        s.tap(nov3)
        assertEquals(emptySet<LocalDate>(), s.selectedDays)
    }

    @Test
    fun pressAndHoldPicksSeveral() {
        val s = CalendarSelection()
        s.tap(nov2)
        s.longPress(nov3)
        assertTrue(s.multiSelect)
        s.tap(nov4)
        assertEquals(setOf(nov2, nov3, nov4), s.selectedDays)
        s.tap(nov3)
        assertEquals(setOf(nov2, nov4), s.selectedDays)
    }

    @Test
    fun unpickingTheLastDayOrClearingStopsPickingSeveral() {
        val s = CalendarSelection()
        s.longPress(nov2)
        s.tap(nov2)
        assertFalse(s.multiSelect)

        s.longPress(nov3)
        s.tap(nov4)
        s.clear()
        assertFalse(s.multiSelect)
        assertEquals(emptySet<LocalDate>(), s.selectedDays)
        // Back to normal: a tap picks just one day.
        s.tap(nov2)
        s.tap(nov4)
        assertEquals(setOf(nov4), s.selectedDays)
    }
}
