package com.example.myfitnessapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupersetsTest {
    private val ids = listOf(10L, 11L, 12L, 13L)

    @Test
    fun linkingTwoLoneExercisesMakesASuperset() {
        val groups = toggleSupersetWithNext(listOf(null, null, null, null), ids, 1)
        assertEquals(listOf(null, 11L, 11L, null), groups)
        assertTrue(isLinkedBelow(groups, 1))
        assertFalse(isLinkedBelow(groups, 2))
    }

    @Test
    fun linkingOntoASupersetJoinsIt() {
        val pair = listOf(null, 11L, 11L, null)
        assertEquals(listOf(10L, 10L, 10L, null), toggleSupersetWithNext(pair, ids, 0))
        assertEquals(listOf(null, 11L, 11L, 11L), toggleSupersetWithNext(pair, ids, 2))
    }

    @Test
    fun linkingTwoSupersetsMergesThem() {
        val two = listOf(10L, 10L, 12L, 12L)
        assertEquals(listOf(10L, 10L, 10L, 10L), toggleSupersetWithNext(two, ids, 1))
    }

    @Test
    fun unlinkingSplitsASuperset() {
        val all = listOf(10L, 10L, 10L, 10L)
        assertEquals(listOf(10L, 10L, 12L, 12L), toggleSupersetWithNext(all, ids, 1))
        // Splitting off the last one leaves it on its own.
        assertEquals(listOf(10L, 10L, 10L, null), toggleSupersetWithNext(all, ids, 2))
        // A superset started by the lower exercise still splits.
        val startedBelow = listOf(11L, 11L, null, null)
        assertEquals(listOf(null, null, null, null), toggleSupersetWithNext(startedBelow, ids, 0))
    }

    @Test
    fun tidyingDropsLoneExercisesAndGaps() {
        assertEquals(listOf(null, null, null, null), tidySupersets(listOf(5L, null, 5L, null), ids))
        assertEquals(listOf(10L, 10L, null, null), tidySupersets(listOf(5L, 5L, null, 7L), ids))
    }

    @Test
    fun reorderingKeepsSupersetsTogether() {
        val groupOf = mapOf(10L to null, 11L to 11L, 12L to 11L, 13L to null)
        // Dragging the superset's second exercise away pulls it back beside the first.
        assertEquals(listOf(10L, 11L, 12L, 13L), keepSupersetsTogether(listOf(10L, 11L, 13L, 12L), groupOf))
        // Swapping the two within the superset keeps the swap.
        assertEquals(listOf(10L, 12L, 11L, 13L), keepSupersetsTogether(listOf(10L, 12L, 13L, 11L), groupOf))
        assertEquals(listOf(13L, 11L, 12L, 10L), keepSupersetsTogether(listOf(13L, 11L, 10L, 12L), groupOf))
    }

    @Test
    fun eachSupersetGetsItsOwnColour() {
        val colors = supersetColors(listOf(10L, 10L, null, 13L))
        assertEquals(colors[0], colors[1])
        assertEquals(null, colors[2])
        assertTrue(colors[0] != colors[3])
    }

    @Test
    fun theBottomOfASupersetCanUnlinkItself() {
        val three = listOf(10L, 10L, 10L, null)
        assertTrue(isLinkedAbove(three, 2))
        assertFalse(isLinkedAbove(three, 0))
        assertFalse(isLinkedAbove(three, 3))
        // Unlinking the bottom one from above is the toggle on the exercise above it.
        assertEquals(listOf(10L, 10L, null, null), toggleSupersetWithNext(three, ids, 1))
        // A pair falls apart completely.
        assertEquals(listOf(null, null, null, null), toggleSupersetWithNext(listOf(10L, 10L, null, null), ids, 0))
    }
}
