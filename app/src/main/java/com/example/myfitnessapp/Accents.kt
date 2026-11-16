package com.example.myfitnessapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Accent colours with a meaning. Purple stays the app's own colour, for buttons and selections.
// On top of it: gold for records, and a colour and icon for each kind of activity, so History
// and the calendar show at a glance what was done. Each has a softer version for dark mode and
// a deeper one for light mode, so it reads well on either background.
// ---------------------------------------------------------------------------

// The kinds of activity that get their own colour. Cardio machines and anything else share one.
enum class ActivityKind(val label: String) {
    STRENGTH("Strength"), RUN("Run"), WALK("Walk"), RIDE("Bike ride"), CARDIO("Cardio")
}

fun ActivityType.kind(): ActivityKind = when (this) {
    ActivityType.STRENGTH -> ActivityKind.STRENGTH
    ActivityType.RUN -> ActivityKind.RUN
    ActivityType.WALK -> ActivityKind.WALK
    ActivityType.BIKE -> ActivityKind.RIDE
    ActivityType.CARDIO, ActivityType.OTHER -> ActivityKind.CARDIO
}

// The kinds of activity in a workout, each once, always in the same order.
fun kindsOf(exercises: List<Exercise>): List<ActivityKind> =
    exercises.map { it.type.kind() }.distinct().sortedBy { it.ordinal }

// Whether the app is showing its dark colours (it can be set apart from the phone's).
@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun ActivityKind.color(): Color {
    val dark = isDark()
    return when (this) {
        ActivityKind.STRENGTH -> if (dark) Color(0xFFD0BCFF) else Color(0xFF7E57C2)
        ActivityKind.RUN -> if (dark) Color(0xFFFF9E80) else Color(0xFFD9583A)
        ActivityKind.WALK -> if (dark) Color(0xFF7FD8BE) else Color(0xFF2A957A)
        ActivityKind.RIDE -> if (dark) Color(0xFF8CC8FF) else Color(0xFF2F7FD1)
        ActivityKind.CARDIO -> if (dark) Color(0xFFF2A6D8) else Color(0xFFC2549A)
    }
}

// The icon drawn on an activity's colour: dark on the soft dark-mode colours, white on the
// deeper light-mode ones.
@Composable
private fun onActivityColor(): Color = if (isDark()) Color(0xFF1B1A1F) else Color.White

// Gold, for records: the colour of the text and icon, and a quiet background to put them on.
object RecordGold {
    @Composable fun color(): Color = if (isDark()) Color(0xFFF6C453) else Color(0xFF7A5600)
    @Composable fun container(): Color = if (isDark()) Color(0xFF4A3B12) else Color(0xFFFFE3A3)
    // A solid gold, for small marks like a calendar dot or the "PR" tag on a set.
    @Composable fun solid(): Color = if (isDark()) Color(0xFFF6C453) else Color(0xFFE3A812)
    @Composable fun onSolid(): Color = Color(0xFF3D2E00)
}

// ----- Icons -----
// The app only bundles Android's basic icon set, which has no runner, walker or bike, so these
// few are drawn here from Material's own icon shapes rather than adding the whole large set.

private fun materialIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

private val StrengthIcon = materialIcon(
    "Strength",
    "M20.57 14.86L22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z"
)
private val RunIcon = materialIcon(
    "Run",
    "M13.49 5.48c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm-3.6 13.9l1-4.4 2.1 2v6h2v-7.5l-2.1-2 .6-3c1.3 1.5 3.3 2.5 5.5 2.5v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1l-5.2 2.2v4.7h2v-3.4l1.8-.7-1.6 8.1-4.9-1-.4 2 7 1.4z"
)
private val WalkIcon = materialIcon(
    "Walk",
    "M13.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM9.8 8.9L7 23h2.1l1.8-8 2.1 2v6h2v-7.5l-2.1-2 .6-3C14.8 12 16.8 13 19 13v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1L6 8.3V13h2V9.6l1.8-.7"
)
private val RideIcon = materialIcon(
    "Bike ride",
    "M15.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM5 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm5.8-10l2.4-2.4.8.8c1.3 1.3 3 2.1 5.1 2.1V9c-1.5 0-2.7-.6-3.6-1.5l-1.9-1.9c-.5-.4-1-.6-1.6-.6s-1.1.2-1.4.6L7.8 8.4c-.4.4-.6.9-.6 1.4 0 .6.2 1.1.6 1.4L11 14v5h2v-6.2l-2.2-2.3zM19 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5z"
)
private val CardioIcon = materialIcon(
    "Cardio",
    "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
)
val TrophyIcon = materialIcon(
    "Records",
    "M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94.63 1.5 1.98 2.63 3.61 2.96V19H7v2h10v-2h-4v-3.1c1.63-.33 2.98-1.46 3.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z"
)

val ActivityKind.icon: ImageVector
    get() = when (this) {
        ActivityKind.STRENGTH -> StrengthIcon
        ActivityKind.RUN -> RunIcon
        ActivityKind.WALK -> WalkIcon
        ActivityKind.RIDE -> RideIcon
        ActivityKind.CARDIO -> CardioIcon
    }

// ----- Pieces used on more than one screen -----

// A workout's kinds of activity as round coloured icons, overlapping a little when there are
// several (a strength session with a walk, say). ringColor is the colour behind them, which
// shows as a thin ring that keeps overlapping icons apart.
@Composable
fun ActivityIcons(kinds: List<ActivityKind>, ringColor: Color, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    if (kinds.isEmpty()) return
    val glyph = onActivityColor()
    Row(
        horizontalArrangement = Arrangement.spacedBy(-(size * 0.3f)),
        modifier = modifier.semantics { contentDescription = kinds.joinToString(" and ") { it.label } }
    ) {
        kinds.forEach { kind ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .border(2.dp, ringColor, CircleShape)
                    .padding(2.dp)
                    .background(kind.color(), CircleShape)
            ) {
                Icon(kind.icon, contentDescription = null, tint = glyph, modifier = Modifier.size(size * 0.53f))
            }
        }
    }
}

// A small gold tag with a trophy: "1 record", "4 records".
@Composable
fun RecordsTag(count: Int, modifier: Modifier = Modifier) {
    val gold = RecordGold.color()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(RecordGold.container(), RoundedCornerShape(50))
            .padding(start = 6.dp, end = 8.dp, top = 1.dp, bottom = 1.dp)
    ) {
        Icon(TrophyIcon, contentDescription = null, tint = gold, modifier = Modifier.size(13.dp))
        Text(
            text = if (count == 1) "1 record" else "$count records",
            fontSize = AppText.caption,
            fontWeight = FontWeight.SemiBold,
            color = gold,
            maxLines = 1,
            modifier = Modifier.offset(x = 3.dp)
        )
    }
}
