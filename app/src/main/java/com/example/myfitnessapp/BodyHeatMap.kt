package com.example.myfitnessapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min

private val MAP_HEIGHT = 320.dp
// The space between the two figures, in figure units.
private const val FIGURE_GAP = 20f
// The gap drawn around every shape, in figure units, like the white lines between muscles.
private const val SHAPE_GAP = 5f

// The span of time the muscle map covers. A month holds about four weeks of sets, so its color
// scale is four times as long.
enum class HeatMapRange(val label: String, val scale: Int) {
    WEEK("This Week", 1),
    MONTH("This Month", 4);

    fun start(today: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate = when (this) {
        WEEK -> weekStartOf(today, firstDayOfWeek)
        MONTH -> today.withDayOfMonth(1)
    }

    // The sets where the color reaches yellow, green, and then blue: 5, 12, and 20 for a week.
    val yellowAt: Int get() = 5 * scale
    val greenAt: Int get() = 12 * scale
    val blueAt: Int get() = 20 * scale
}

private val HEAT_YELLOW = Color(0xFFF2C230)
private val HEAT_GREEN = Color(0xFF3DBE5C)
private val HEAT_BLUE = Color(0xFF1E90FF)
// Even one set starts this far toward yellow, so a worked muscle never looks unworked.
private const val FIRST_SET_STEP = 0.3f

// How far `sets` is from `from` to `to`, from 0 to 1.
private fun between(sets: Double, from: Int, to: Int): Float = ((sets - from) / (to - from)).toFloat().coerceIn(0f, 1f)

// The color for a number of sets: gray for none, then a smooth blend through yellow and green to
// an electric blue.
private fun heatColor(sets: Double, range: HeatMapRange, gray: Color): Color = when {
    sets <= 0.0 -> gray
    sets < range.yellowAt -> lerp(gray, HEAT_YELLOW, FIRST_SET_STEP + (1 - FIRST_SET_STEP) * between(sets, 0, range.yellowAt))
    sets < range.greenAt -> lerp(HEAT_YELLOW, HEAT_GREEN, between(sets, range.yellowAt, range.greenAt))
    else -> lerp(HEAT_GREEN, HEAT_BLUE, between(sets, range.greenAt, range.blueAt))
}

// "Muscles Worked": how many sets each muscle got this week or month, shaded on front and back
// figures.
@Composable
fun MuscleHeatMapCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    var range by rememberSaveable { mutableStateOf(HeatMapRange.WEEK) }

    // A week starts on the region's first day, like the Streak and the calendar.
    val zone = ZoneId.systemDefault()
    val startMillis = range.start(LocalDate.now(zone), WeekFields.of(Locale.getDefault()).firstDayOfWeek)
        .atStartOfDay(zone).toInstant().toEpochMilli()
    val countsFlow = remember(startMillis) { dao.observeSetCountsSince(startMillis) }
    val counts by countsFlow.collectAsState(initial = emptyList())
    val sets = remember(counts) { muscleSets(counts) }

    var selected by remember { mutableStateOf<Muscle?>(null) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Muscles Worked", fontSize = AppText.heading, color = labelColor, modifier = Modifier.weight(1f))
                DropdownLabel(
                    selected = range,
                    options = HeatMapRange.entries,
                    label = { it.label },
                    onSelect = { range = it },
                    fontSize = AppText.small,
                    color = labelColor,
                    contentDescription = "Choose time span"
                )
            }

            // The tapped muscle's sets, blank until one is tapped.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                selected?.let { muscle ->
                    val count = sets[muscle] ?: 0.0
                    Text(
                        text = "${muscle.label}: ${formatNumber(count)} " + if (count == 1.0) "set" else "sets",
                        fontSize = AppText.small
                    )
                }
            }

            val gray = skinColor()
            BodyFigures(
                muscleColor = { heatColor(sets[it] ?: 0.0, range, gray) },
                selected = selected,
                onSelect = { selected = it }
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Front", fontSize = AppText.caption, color = labelColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Back", fontSize = AppText.caption, color = labelColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Legend(range)
        }
    }
}

// The figure's gray: the card's color nudged toward the text color, so it shows in either theme.
// Unworked muscles share it with the rest of the body.
@Composable
fun skinColor(): Color {
    val colors = MaterialTheme.colorScheme
    return lerp(colors.surfaceContainerHighest, colors.onSurface, 0.2f)
}

private val LEGEND_BAR_HEIGHT = 10.dp
private val LEGEND_NUMBER_GAP = 4.dp

// The color key: a bar blending gray through yellow and green to blue, with set counts beneath it.
@Composable
private fun Legend(range: HeatMapRange) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gray = skinColor()
    // The bar's colors, sampled across the scale so it matches the figures exactly.
    val steps = 40
    val barColors = (0..steps).map { heatColor(range.blueAt * it / steps.toDouble(), range, gray) }
    // Each label sits under its point on the bar.
    val marks = listOf(
        0 to "0",
        range.yellowAt to "${range.yellowAt}",
        range.greenAt to "${range.greenAt}",
        range.blueAt to "${range.blueAt}+"
    )

    // The bar with its numbers beneath it, and "Sets" to the left, level with the numbers.
    Row(
        modifier = Modifier.fillMaxWidth(0.9f),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "Sets",
            fontSize = AppText.caption,
            color = labelColor,
            modifier = Modifier.padding(top = LEGEND_BAR_HEIGHT + LEGEND_NUMBER_GAP, end = 10.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LEGEND_BAR_HEIGHT)
                    .background(Brush.horizontalGradient(barColors), RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.height(LEGEND_NUMBER_GAP))
            Box(modifier = Modifier.fillMaxWidth()) {
                marks.forEach { (sets, text) ->
                    val fraction = sets.toFloat() / range.blueAt
                    Text(
                        text = text,
                        fontSize = AppText.caption,
                        color = labelColor,
                        // Centered under its point on the bar, kept inside the bar's ends.
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0))
                            val width = constraints.maxWidth
                            val x = (fraction * width - placeable.width / 2f).toInt()
                                .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                            layout(width, placeable.height) { placeable.place(x, 0) }
                        }
                    )
                }
            }
        }
    }
}

// The front and back figures, each muscle filled with `muscleColor`. With `onSelect`, tapping a
// muscle selects it and the selected one is outlined.
@Composable
fun BodyFigures(
    muscleColor: (Muscle) -> Color,
    selected: Muscle? = null,
    onSelect: ((Muscle?) -> Unit)? = null,
    height: Dp = MAP_HEIGHT
) {
    val colors = MaterialTheme.colorScheme
    val skinColor = skinColor()
    val gapColor = colors.surfaceContainerHighest
    val selectedColor = colors.onSurface
    val figures = listOf(FRONT_FIGURE, BACK_FIGURE)
    val paths = remember { figures.map { shapes -> shapes.map(::outlinePath) } }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (onSelect == null) Modifier else Modifier.pointerInput(selected) {
                detectTapGestures { touch ->
                    val layout = figureLayout(size.width.toFloat(), size.height.toFloat())
                    val muscle = figures.withIndex().firstNotNullOfOrNull { (index, shapes) ->
                        val (x, y) = layout.toFigure(index, touch)
                        // The last shape drawn is on top, so it's checked first.
                        shapes.lastOrNull { it.muscle != null && it.contains(x, y) }?.muscle
                    }
                    // Tapping outside a muscle, or the selected one again, clears the selection.
                    onSelect(if (muscle == selected) null else muscle)
                }
            })
    ) {
        val layout = figureLayout(size.width, size.height)
        val gap = Stroke(width = SHAPE_GAP, join = StrokeJoin.Round)
        val outline = Stroke(width = 2.dp.toPx() / layout.scale, join = StrokeJoin.Round)

        figures.forEachIndexed { index, shapes ->
            val origin = layout.origin(index)
            // Draw in figure units, moved and scaled onto the canvas.
            drawContext.transform.translate(origin.x, origin.y)
            drawContext.transform.scale(layout.scale, layout.scale, Offset.Zero)

            shapes.forEachIndexed { i, shape ->
                val color = when (shape.part) {
                    BodyPart.MUSCLE -> muscleColor(shape.muscle!!)
                    BodyPart.SKIN -> skinColor
                }
                drawPath(paths[index][i], color)
                drawPath(paths[index][i], gapColor, style = gap)
            }
            shapes.forEachIndexed { i, shape ->
                if (shape.muscle != null && shape.muscle == selected) {
                    drawPath(paths[index][i], selectedColor, style = outline)
                }
            }

            drawContext.transform.scale(1 / layout.scale, 1 / layout.scale, Offset.Zero)
            drawContext.transform.translate(-origin.x, -origin.y)
        }
    }
}

// A shape's outline: smoothed into curves through its points, or straight lines between them.
private fun outlinePath(shape: FigureShape): Path = Path().apply {
    val p = shape.points
    if (!shape.smooth) {
        p.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
        close()
        return@apply
    }
    // A closed Catmull-Rom curve, turned into cubic Bézier pieces between each pair of points.
    fun at(i: Int) = p[(i + p.size) % p.size]
    moveTo(p[0].x, p[0].y)
    for (i in p.indices) {
        val p0 = at(i - 1); val p1 = at(i); val p2 = at(i + 1); val p3 = at(i + 2)
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y
        )
    }
    close()
}

// Where the two figures sit on the canvas, and how much they're scaled.
private data class FigureLayout(val scale: Float, val left: Float, val top: Float) {
    // Where figure point (0, 0) lands on the canvas for figure `index`.
    fun origin(index: Int) = Offset(
        left + (index * (FIGURE_RIGHT - FIGURE_LEFT + FIGURE_GAP) - FIGURE_LEFT) * scale,
        top - FIGURE_TOP * scale
    )

    fun toFigure(index: Int, canvas: Offset): Pair<Float, Float> {
        val o = origin(index)
        return (canvas.x - o.x) / scale to (canvas.y - o.y) / scale
    }
}

private fun figureLayout(width: Float, height: Float): FigureLayout {
    val figureWidth = FIGURE_RIGHT - FIGURE_LEFT
    val figureHeight = FIGURE_BOTTOM - FIGURE_TOP
    val totalWidth = figureWidth * 2 + FIGURE_GAP
    val scale = min(width / totalWidth, height / figureHeight)
    return FigureLayout(
        scale = scale,
        left = (width - totalWidth * scale) / 2f,
        top = (height - figureHeight * scale) / 2f
    )
}
