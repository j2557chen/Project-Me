package com.example.myfitnessapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// ---------------------------------------------------------------------------
// A line chart that the elevation graph and the pace graph both use. Each of them works out
// its own points and axis range, then hands them to this to be drawn.
// ---------------------------------------------------------------------------

// One point on a chart. x is the position along the bottom. y is the value up the side, or
// null where there's no value (for example, while the user was standing still). A null
// breaks the line, so it's drawn as separate pieces instead of joining across the gap.
data class ChartPoint(val x: Float, val y: Float?)

// The height of the graph itself, and the width reserved for the numbers beside it.
private val CHART_HEIGHT = 160.dp
private val LABEL_WIDTH = 52.dp
private val LABEL_GAP = 8.dp

// Draws the chart: numbers up the side, the line with a shaded area under it, and numbers
// along the bottom.
//
// points: what to draw, with x running from 0 to xMax.
// axisMin and axisMax: the range of values the vertical axis covers.
// yLabel and xLabel: turn a value into the text shown beside the axis.
// readout: turns the point the user has touched into the text shown above the chart.
// invertY: normally a bigger value is drawn higher up. Setting this to true flips that, so
//   a smaller value is higher. The pace graph uses it, because a smaller pace is a faster one.
@Composable
fun LineChart(
    points: List<ChartPoint>,
    xMax: Float,
    axisMin: Float,
    axisMax: Float,
    yLabel: (Float) -> String,
    xLabel: (Float) -> String,
    readout: (ChartPoint) -> String,
    modifier: Modifier = Modifier,
    invertY: Boolean = false
) {
    // The colors have to be read here, since the drawing code below can't look them up.
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    // The points, cut into unbroken pieces at every missing value. Made again only if the
    // points change.
    val pieces = remember(points) { splitIntoPieces(points) }

    // Where the user is touching the graph, as a position along the bottom (0 to xMax), or
    // null if they haven't touched it yet. It starts over if the points change.
    var selectedX by remember(points) { mutableStateOf<Float?>(null) }

    // The point nearest to where the user is touching. minByOrNull goes through the points and
    // picks the one whose position is the smallest distance away.
    val selectedPoint: ChartPoint? = selectedX?.let { target ->
        points.minByOrNull { abs(it.x - target) }
    }

    // The values written at the top, middle, and bottom of the axis.
    val topValue = if (invertY) axisMin else axisMax
    val bottomValue = if (invertY) axisMax else axisMin
    val middleValue = (axisMin + axisMax) / 2f

    Column(modifier = modifier.fillMaxWidth()) {
        // The line above the chart: the value at the touched point, or a hint until the graph
        // is touched. It has a fixed height, so the chart doesn't jump when the text changes.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(start = LABEL_WIDTH + LABEL_GAP),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selectedPoint != null) {
                Text(text = readout(selectedPoint), fontSize = AppText.small)
            } else {
                Text(
                    text = "Touch and drag the graph to see values",
                    fontSize = AppText.caption,
                    color = labelColor
                )
            }
        }

        Row {
            // The numbers up the side. SpaceBetween puts one at the top, one in the middle,
            // and one at the bottom.
            Column(
                modifier = Modifier
                    .width(LABEL_WIDTH)
                    .height(CHART_HEIGHT),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(text = yLabel(topValue), fontSize = AppText.caption, color = labelColor)
                Text(text = yLabel(middleValue), fontSize = AppText.caption, color = labelColor)
                Text(text = yLabel(bottomValue), fontSize = AppText.caption, color = labelColor)
            }

            Spacer(modifier = Modifier.width(LABEL_GAP))

            // The graph. Everything in this block is drawn onto a canvas, which works in
            // pixels. "size" is the canvas's width and height.
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(CHART_HEIGHT)
                    // A tap selects the spot that was touched. "size" is the canvas's size in
                    // pixels, so dividing by its width turns a touch into a fraction of the way
                    // across, and multiplying by xMax turns that into a position on the graph.
                    .pointerInput(xMax) {
                        detectTapGestures { touch ->
                            selectedX = (touch.x / size.width).coerceIn(0f, 1f) * xMax
                        }
                    }
                    // Dragging sideways moves the selection along with the finger. Only sideways
                    // movement is taken, so scrolling the page up and down still works.
                    .pointerInput(xMax) {
                        detectHorizontalDragGestures(
                            onDragStart = { touch ->
                                selectedX = (touch.x / size.width).coerceIn(0f, 1f) * xMax
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                selectedX = (change.position.x / size.width).coerceIn(0f, 1f) * xMax
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height

                // Turn a position along the bottom into how far across the canvas it is, and a
                // value into how far down. Values outside the axis range are held at the edge.
                fun xFor(x: Float): Float = (x / xMax).coerceIn(0f, 1f) * width
                fun yFor(value: Float): Float {
                    val fraction = ((value - axisMin) / (axisMax - axisMin)).coerceIn(0f, 1f)
                    return if (invertY) fraction * height else height - fraction * height
                }

                // Three faint lines across, matching the numbers at the side.
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, height * fraction),
                        end = Offset(width, height * fraction),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                pieces.forEach { piece ->
                    if (piece.size == 1) {
                        // A piece with a single point can't be a line, so it's drawn as a dot.
                        drawCircle(
                            color = lineColor,
                            radius = 2.dp.toPx(),
                            center = Offset(xFor(piece[0].x), yFor(piece[0].y))
                        )
                    } else {
                        // The line itself, joining the points in order.
                        val line = Path()
                        piece.forEachIndexed { index, dot ->
                            if (index == 0) {
                                line.moveTo(xFor(dot.x), yFor(dot.y))
                            } else {
                                line.lineTo(xFor(dot.x), yFor(dot.y))
                            }
                        }

                        // The shaded area under the line: the same path, carried down to the
                        // bottom corners and closed.
                        val area = Path()
                        area.addPath(line)
                        area.lineTo(xFor(piece.last().x), height)
                        area.lineTo(xFor(piece.first().x), height)
                        area.close()

                        drawPath(path = area, color = fillColor)
                        drawPath(
                            path = line,
                            color = lineColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // The selection: a vertical line at the touched point, with a dot on the graph
                // where the value is. (There's no dot where the value is missing.)
                if (selectedPoint != null) {
                    val cursorX = xFor(selectedPoint.x)
                    drawLine(
                        color = cursorColor,
                        start = Offset(cursorX, 0f),
                        end = Offset(cursorX, height),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    val cursorY = selectedPoint.y
                    if (cursorY != null) {
                        val center = Offset(cursorX, yFor(cursorY))
                        drawCircle(color = lineColor, radius = 6.dp.toPx(), center = center)
                        drawCircle(color = surfaceColor, radius = 2.5.dp.toPx(), center = center)
                    }
                }
            }
        }

        // The numbers along the bottom: the start, halfway, and the end. The space on the
        // left lines them up with the graph, not with the numbers at the side.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = LABEL_WIDTH + LABEL_GAP, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = xLabel(0f), fontSize = AppText.caption, color = labelColor)
            Text(text = xLabel(xMax / 2f), fontSize = AppText.caption, color = labelColor)
            Text(text = xLabel(xMax), fontSize = AppText.caption, color = labelColor)
        }
    }
}

// A point that definitely has a value, used for drawing.
private data class Dot(val x: Float, val y: Float)

// Cuts a list of points into unbroken pieces. Every point with no value ends the piece before
// it, and the next point with a value starts a new one.
private fun splitIntoPieces(points: List<ChartPoint>): List<List<Dot>> {
    val pieces = mutableListOf<List<Dot>>()
    var current = mutableListOf<Dot>()

    for (point in points) {
        val y = point.y
        if (y == null) {
            // A gap: close off the piece being built, if it has anything in it.
            if (current.isNotEmpty()) {
                pieces.add(current)
                current = mutableListOf()
            }
        } else {
            current.add(Dot(point.x, y))
        }
    }
    if (current.isNotEmpty()) pieces.add(current)

    return pieces
}

// ---------------------------------------------------------------------------
// Smoothing helpers, shared by the graphs. They build new lists and never change the one
// they're given.
// ---------------------------------------------------------------------------

// Replaces each value with the middle one of the values around it, which removes sudden spikes.
fun movingMedian(values: List<Float>, halfWindow: Int): List<Float> {
    return values.indices.map { i ->
        val from = maxOf(0, i - halfWindow)
        val to = minOf(values.lastIndex, i + halfWindow)
        val window = values.subList(from, to + 1).sorted()
        window[window.size / 2]
    }
}

// Replaces each value with the average of the values around it.
fun movingAverage(values: List<Float>, halfWindow: Int): List<Float> {
    return values.indices.map { i ->
        val from = maxOf(0, i - halfWindow)
        val to = minOf(values.lastIndex, i + halfWindow)
        values.subList(from, to + 1).average().toFloat()
    }
}

// Keeps about maxPoints of the points: every nth one, always including the last. A graph
// doesn't need thousands of points, and drawing fewer is quicker.
fun thinForChart(points: List<ChartPoint>, maxPoints: Int = 300): List<ChartPoint> {
    if (points.isEmpty()) return points
    val step = (points.size / maxPoints).coerceAtLeast(1)
    val thinned = points.filterIndexed { index, _ -> index % step == 0 }
    return if (thinned.last() != points.last()) thinned + points.last() else thinned
}
