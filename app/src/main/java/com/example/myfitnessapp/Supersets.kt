package com.example.myfitnessapp

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction

// ---------------------------------------------------------------------------
// Supersets: exercises done back to back, linked on their cards. Exercises in a superset share
// a group number, and always sit next to each other in the list. The functions here work on a
// list of group numbers in the exercises' order (null for an exercise on its own), alongside
// the exercises' ids.
// ---------------------------------------------------------------------------

// Puts groups in a standard form: each run of neighbours with the same number is one superset,
// numbered by its first exercise's id, and a "superset" of one is no superset at all. Numbers
// that come back further down the list are a separate superset, since a superset can't have gaps.
fun tidySupersets(groups: List<Long?>, ids: List<Long>): List<Long?> {
    val out = MutableList<Long?>(groups.size) { null }
    var i = 0
    while (i < groups.size) {
        val group = groups[i]
        var end = i
        if (group != null) while (end + 1 < groups.size && groups[end + 1] == group) end++
        if (end > i) for (k in i..end) out[k] = ids[i]
        i = end + 1
    }
    return out
}

// Whether the exercise at `index` is linked with the one below it.
fun isLinkedBelow(groups: List<Long?>, index: Int): Boolean =
    index + 1 < groups.size && groups[index] != null && groups[index] == groups[index + 1]

// Whether the exercise at `index` is linked with the one above it.
fun isLinkedAbove(groups: List<Long?>, index: Int): Boolean = index > 0 && isLinkedBelow(groups, index - 1)

// Links the exercise at `index` with the one below it, or unlinks them if they're linked. Linking
// joins their supersets into one; unlinking splits the superset between them.
fun toggleSupersetWithNext(groups: List<Long?>, ids: List<Long>, index: Int): List<Long?> {
    if (index !in 0 until groups.lastIndex) return groups
    val g = groups.toMutableList()
    val above = g[index]
    val below = g[index + 1]
    if (isLinkedBelow(groups, index)) {
        // Everything below the split gets a number of its own, which tidying then renumbers.
        val marker = -ids[index + 1] - 1
        var j = index + 1
        while (j < g.size && g[j] == above) g[j++] = marker
    } else {
        val target = above ?: below ?: ids[index]
        g[index] = target
        if (below != null) {
            var j = index + 1
            while (j < g.size && g[j] == below) g[j++] = target
        } else {
            g[index + 1] = target
        }
    }
    return tidySupersets(g, ids)
}

// A new order for the exercises, with each superset kept together: its exercises move as one,
// to where the first of them now is, in the order they now appear.
fun keepSupersetsTogether(order: List<Long>, groupOf: Map<Long, Long?>): List<Long> {
    val result = mutableListOf<Long>()
    val placed = mutableSetOf<Long>()
    for (id in order) {
        val group = groupOf[id]
        when {
            group == null -> result += id
            group !in placed -> {
                placed += group
                result += order.filter { groupOf[it] == group }
            }
        }
    }
    return result
}

// A colour for each superset, so neighbouring ones are told apart. Null for exercises on their own.
private val SUPERSET_COLORS = listOf(
    Color(0xFF26A69A), Color(0xFFFFA726), Color(0xFFEC407A), Color(0xFF42A5F5)
)

fun supersetColors(groups: List<Long?>): List<Color?> {
    val order = groups.filterNotNull().distinct()
    return groups.map { g -> g?.let { SUPERSET_COLORS[order.indexOf(it) % SUPERSET_COLORS.size] } }
}

// Saves new group numbers for a workout's exercises (in their order), changing only those that differ.
suspend fun saveSupersets(db: WorkoutDatabase, exercises: List<Exercise>, groups: List<Long?>) {
    val dao = db.workoutDao()
    db.withTransaction {
        exercises.forEachIndexed { i, e ->
            if (e.supersetGroup != groups[i]) dao.setSupersetGroup(e.id, groups[i])
        }
    }
}

// Tidies a workout's supersets after an exercise is removed or moved.
suspend fun tidyWorkoutSupersets(db: WorkoutDatabase, workoutId: Long) {
    val exercises = db.workoutDao().getExercisesForWorkout(workoutId)
    saveSupersets(db, exercises, tidySupersets(exercises.map { it.supersetGroup }, exercises.map { it.id }))
}

// Two chain links at an angle: the "link" symbol. Drawn by hand, because the built-in icon set
// doesn't include one.
@Composable
fun ChainLinkIcon(modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val linkWidth = size.width * 0.55f
        val linkHeight = size.height * 0.32f
        val radius = CornerRadius(linkHeight / 2, linkHeight / 2)
        rotate(-45f) {
            // Two rounded links overlapping in the middle.
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.05f, (size.height - linkHeight) / 2),
                size = Size(linkWidth, linkHeight),
                cornerRadius = radius,
                style = Stroke(stroke)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.4f, (size.height - linkHeight) / 2),
                size = Size(linkWidth, linkHeight),
                cornerRadius = radius,
                style = Stroke(stroke)
            )
        }
    }
}
