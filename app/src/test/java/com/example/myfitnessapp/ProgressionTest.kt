package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {
    private fun lbs(value: Double) = WeightUnit.LBS.toKg(value)

    private fun set(lb: Double, reps: Double, effort: Double?, type: SetType = SetType.NORMAL) =
        ExerciseSet(exerciseId = 1, setNumber = 1, weight = lbs(lb), reps = reps, effort = effort, setType = type)

    @Test
    fun easyHeaviestSetForMoreThan12RepsGoesUp5Lbs() {
        val increase = suggestedIncrease(listOf(set(80.0, 15.0, 8.0), set(100.0, 13.0, 7.0)), EffortType.RPE, WeightUnit.LBS)!!
        assertEquals(lbs(100.0), increase.fromKg, 1e-9)
        assertEquals(105.0, WeightUnit.LBS.fromKg(increase.toKg), 1e-9)
    }

    @Test
    fun twelveRepsOrRpe8IsNotEnough() {
        assertNull(suggestedIncrease(listOf(set(100.0, 12.0, 7.0)), EffortType.RPE, WeightUnit.LBS))
        assertNull(suggestedIncrease(listOf(set(100.0, 15.0, 8.0)), EffortType.RPE, WeightUnit.LBS))
        assertNull(suggestedIncrease(listOf(set(100.0, 15.0, null)), EffortType.RPE, WeightUnit.LBS))
    }

    @Test
    fun rirOver2CountsAsEasy() {
        assertNull(suggestedIncrease(listOf(set(100.0, 13.0, 2.0)), EffortType.RIR, WeightUnit.LBS))
        val increase = suggestedIncrease(listOf(set(100.0, 13.0, 3.0)), EffortType.RIR, WeightUnit.LBS)
        assertEquals(105.0, WeightUnit.LBS.fromKg(increase!!.toKg), 1e-9)
    }

    @Test
    fun kilogramsGoUp2AndAHalf() {
        val sets = listOf(ExerciseSet(exerciseId = 1, setNumber = 1, weight = 60.0, reps = 14.0, effort = 6.0))
        assertEquals(62.5, suggestedIncrease(sets, EffortType.RPE, WeightUnit.KG)!!.toKg, 1e-9)
    }

    @Test
    fun warmUpsAreIgnored() {
        // The heaviest counted set is 100 lbs at RPE 9, so nothing is suggested.
        val sets = listOf(set(150.0, 15.0, 5.0, SetType.WARM_UP), set(100.0, 13.0, 9.0))
        assertNull(suggestedIncrease(sets, EffortType.RPE, WeightUnit.LBS))
    }
}
