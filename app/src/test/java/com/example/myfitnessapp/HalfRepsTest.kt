package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HalfRepsTest {
    @Test
    fun repsBoxTakesWholeNumbersAndHalves() {
        listOf("", "8", "12", "100", "8.", "8.5", ".5").forEach { assertTrue(it, isRepsInput(it)) }
        listOf("8.4", "8.55", "1000", "8..", "a", "8.5.").forEach { assertFalse(it, isRepsInput(it)) }
    }

    @Test
    fun halfRepCountsHalfTheWeightTowardsVolume() {
        val set = StrengthSet(1, 1, 0, 1, weightKg = 100.0, reps = 8.5)
        assertEquals(850.0, set.volumeKg, 1e-9)
    }
}
