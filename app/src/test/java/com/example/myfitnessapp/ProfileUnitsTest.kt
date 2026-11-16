package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileUnitsTest {
    @Test
    fun feetAndInchesRoundTrip() {
        val cm = feetInchesToCm(5, 11)
        assertEquals(180.34, cm, 1e-9)
        assertEquals(5 to 11, cmToFeetInches(cm))
    }

    @Test
    fun centimetresRoundToTheNearestInch() {
        assertEquals(5 to 11, cmToFeetInches(180.0))
        assertEquals(6 to 0, cmToFeetInches(182.0))
    }
}
