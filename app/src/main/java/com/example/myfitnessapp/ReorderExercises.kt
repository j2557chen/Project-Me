package com.example.myfitnessapp

import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex

// The height of each name in the rearrange list. Every row is the same height, which is what
// lets a drag work out where the dragged exercise has moved to.
private val REORDER_ROW_HEIGHT = 48.dp

// Moves one item to another index.
private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

// Lists the workout's exercises by name; dragging a handle moves that exercise. Every drop in a
// new place reports the new order (ids, top to bottom), so closing never loses a change.
@Composable
fun ReorderExercisesDialog(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onReorder: (List<Long>) -> Unit
) {
    // Updated as the user drags, ahead of the database.
    var order by remember { mutableStateOf(exercises) }

    var draggedId by remember { mutableStateOf<Long?>(null) }
    // How far, in pixels, the dragged row is from its place in the list.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // So a drop back in the same place saves nothing.
    var orderAtDragStart by remember { mutableStateOf(emptyList<Long>()) }

    // Picks up database changes, except mid-drag, when the list on screen is newer.
    LaunchedEffect(exercises) {
        if (draggedId == null) order = exercises
    }

    val rowHeightPx = with(LocalDensity.current) { REORDER_ROW_HEIGHT.toPx() }

    fun endDrag() {
        draggedId = null
        dragOffset = 0f
        // A superset moves as one: its exercises close up around wherever the first of them is.
        val byId = order.associateBy { it.id }
        order = keepSupersetsTogether(order.map { it.id }, order.associate { it.id to it.supersetGroup })
            .map { byId.getValue(it) }
        val newOrder = order.map { it.id }
        if (newOrder != orderAtDragStart) onReorder(newOrder)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Rearrange Exercises",
                        fontSize = AppText.heading,
                        modifier = Modifier.padding(end = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (order.isEmpty()) {
                        Text(
                            text = "No exercises yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // A superset's exercises are marked in its colour.
                        val colors = supersetColors(order.map { it.supersetGroup })
                        order.forEachIndexed { index, exercise ->
                            // key keeps the dragged row's gesture alive as it swaps places.
                            key(exercise.id) {
                                val isDragged = exercise.id == draggedId

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(REORDER_ROW_HEIGHT)
                                        .zIndex(if (isDragged) 1f else 0f)
                                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                                        .background(if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                ) {
                                    // Only the handle starts a drag, so swiping a name still scrolls.
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(REORDER_ROW_HEIGHT)
                                            .pointerInput(exercise.id) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggedId = exercise.id
                                                        dragOffset = 0f
                                                        orderAtDragStart = order.map { it.id }
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragOffset += amount.y
                                                        // Past half a row, swap with the neighbor. The
                                                        // loops handle a fast drag past several rows.
                                                        var index = order.indexOfFirst { it.id == exercise.id }
                                                        while (dragOffset > rowHeightPx / 2 && index < order.lastIndex) {
                                                            order = order.moved(index, index + 1)
                                                            index++
                                                            dragOffset -= rowHeightPx
                                                        }
                                                        while (dragOffset < -rowHeightPx / 2 && index > 0) {
                                                            order = order.moved(index, index - 1)
                                                            index--
                                                            dragOffset += rowHeightPx
                                                        }
                                                    },
                                                    onDragEnd = ::endDrag,
                                                    onDragCancel = ::endDrag
                                                )
                                            }
                                    ) {
                                        DragHandleIcon()
                                    }

                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(width = 4.dp, height = 28.dp)
                                            .background(colors[index] ?: Color.Transparent, RoundedCornerShape(2.dp))
                                    )
                                    // One line, so every row stays the same height for
                                    // dragging. A long name is shown a little smaller to fit.
                                    BasicText(
                                        text = exercise.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = LocalTextStyle.current.copy(color = LocalContentColor.current),
                                        autoSize = TextAutoSize.StepBased(
                                            minFontSize = AppText.caption,
                                            maxFontSize = AppText.body,
                                            stepSize = 1.sp
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        }
    }
}

// Two horizontal bars, the usual "drag me" symbol. Drawn by hand, because the built-in icon
// set doesn't include one.
@Composable
private fun DragHandleIcon() {
    val color = LocalContentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 2.dp)
                    .background(color = color, shape = RoundedCornerShape(1.dp))
            )
        }
    }
}

// Two arrows side by side, one pointing up and one pointing down: the "rearrange" symbol.
// Drawn by hand, because the built-in icon set doesn't include one.
@Composable
fun UpDownArrowsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val top = size.height * 0.12f
        val bottom = size.height * 0.88f
        // How far the arrowheads reach out to each side, and back along the shaft.
        val head = size.width * 0.18f

        // The up arrow, on the left: a shaft, then two short lines making the head at the top.
        val upX = size.width * 0.3f
        drawLine(color, Offset(upX, bottom), Offset(upX, top), stroke, StrokeCap.Round)
        drawLine(color, Offset(upX, top), Offset(upX - head, top + head), stroke, StrokeCap.Round)
        drawLine(color, Offset(upX, top), Offset(upX + head, top + head), stroke, StrokeCap.Round)

        // The down arrow, on the right, with its head at the bottom.
        val downX = size.width * 0.7f
        drawLine(color, Offset(downX, top), Offset(downX, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(downX, bottom), Offset(downX - head, bottom - head), stroke, StrokeCap.Round)
        drawLine(color, Offset(downX, bottom), Offset(downX + head, bottom - head), stroke, StrokeCap.Round)
    }
}
