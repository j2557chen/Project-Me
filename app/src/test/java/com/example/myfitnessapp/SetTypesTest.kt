package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Test

class SetTypesTest {
    @Test
    fun normalSetsAreNumberedAmongThemselves() {
        val types = listOf(SetType.WARM_UP, SetType.WARM_UP, SetType.NORMAL, SetType.NORMAL, SetType.DROP, SetType.NORMAL, SetType.FAILURE)
        assertEquals(listOf("W", "W", "1", "2", "D", "3", "F"), setLabels(types))
    }

    @Test
    fun hevySetTypesAreRead() {
        assertEquals(SetType.WARM_UP, hevySetType("warmup"))
        assertEquals(SetType.DROP, hevySetType("dropset"))
        assertEquals(SetType.FAILURE, hevySetType("failure"))
        assertEquals(SetType.NORMAL, hevySetType("normal"))
        assertEquals(SetType.NORMAL, hevySetType(""))
    }
}
