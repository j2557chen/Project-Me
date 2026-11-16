package com.example.myfitnessapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.border
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// The set table, shared by workout exercise cards and the program editor:
//   Set | RPE/RIR | [Weight] | Reps | X
// Headings and boxes line up because both use the same columns: fixed widths for Set,
// Weight, and the X, with RPE/RIR and Reps sharing the rest equally.
// ---------------------------------------------------------------------------

// Every set row, and the Add Set button, share this height.
val SET_ROW_HEIGHT = 30.dp
private val SET_NUMBER_WIDTH = 48.dp
private val DELETE_COLUMN_WIDTH = 40.dp
private val DELETE_ICON_SIZE = 18.dp
// The space between the drawn X and the edge of its column: the icon's margin in the
// column, plus the blank border inside the icon (5 of its 24 units on each side).
private val DELETE_ICON_MARGIN = (DELETE_COLUMN_WIDTH - DELETE_ICON_SIZE) / 2 + DELETE_ICON_SIZE * 5 / 24
private val UNIT_GAP = 4.dp

val SET_BOX_WIDTH = 42.dp
// The weight box's usual width, the same as the other boxes: room for 3 digits, like 225 or 22.5.
// A longer weight widens it to the left (see growLeftToFit).
val WEIGHT_BOX_WIDTH = SET_BOX_WIDTH
// How far the weight column sits to the right of where it would otherwise be, so a 5-digit weight's
// box has room to widen without covering the RPE/RIR box, even at a large font size.
private val WEIGHT_COLUMN_SHIFT = 44.dp
// Padding on the left of the RPE/RIR column. The box and heading are centred in what's left, so
// they sit half this distance to the right of the column's middle.
private val RPE_SHIFT = 12.dp
// A weight can have up to 5 digits, plus a decimal point.
const val WEIGHT_MAX_DIGITS = 5

// Text that may go in a weight box: a number of up to 5 digits, optionally with a decimal point.
fun isWeightInput(input: String): Boolean =
    isDecimalInput(input, WEIGHT_MAX_DIGITS + 1) && input.count(Char::isDigit) <= WEIGHT_MAX_DIGITS

// The room a box's text needs besides the text itself: its padding on both sides, plus the cursor.
private val BOX_TEXT_ROOM = 12.dp

// A box that takes up `baseWidth` in its row, but widens to the left when `text` needs more room,
// drawing over the empty space beside it. Nothing next to it moves, and it narrows again as the
// text gets shorter. The width is measured from the text, so it also works at large font sizes.
@Composable
fun Modifier.growLeftToFit(text: String, baseWidth: Dp): Modifier {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val needed = with(LocalDensity.current) { measurer.measure(text, style).size.width.toDp() } + BOX_TEXT_ROOM
    val width by animateDpAsState(maxOf(baseWidth, needed), label = "box width")
    return layout { measurable, constraints ->
        val basePx = baseWidth.roundToPx()
        val widthPx = width.roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
        // The row only sees the usual width. The box is placed so its right edge stays put and
        // any extra width sticks out on the left.
        layout(basePx, placeable.height) { placeable.place(basePx - placeable.width, 0) }
    }
}

val SetBoxModifier = Modifier
    .width(SET_BOX_WIDTH)
    .fillMaxHeight()

// Text that may go in a box of decimals, such as 22.5.
fun isDecimalInput(input: String, maxLength: Int): Boolean =
    input.length <= maxLength && input.all { it.isDigit() || it == '.' } && input.count { it == '.' } <= 1

// Text that may go in a box of whole numbers.
fun isWholeNumberInput(input: String, maxLength: Int): Boolean =
    input.length <= maxLength && input.all(Char::isDigit)

// Reps: up to three digits, optionally a half (8.5). "8." is allowed on the way to "8.5".
private val REPS_INPUT = Regex("""\d{0,3}(\.5?)?""")

fun isRepsInput(input: String): Boolean = REPS_INPUT.matches(input)

// RPE allows half points like 7.5; RIR is a whole number of reps.
fun isValidEffortInput(type: EffortType, input: String): Boolean = when (type) {
    EffortType.RPE -> isDecimalInput(input, 4)
    EffortType.RIR -> isWholeNumberInput(input, 2)
}

val EffortType.keyboardType: KeyboardType
    get() = if (this == EffortType.RPE) KeyboardType.Decimal else KeyboardType.Number

// The heading row: "Set", the RPE/RIR toggle, "Weight" (if shown), and "Reps".
@Composable
fun SetTableHeader(effortType: EffortType, onToggleEffort: () -> Unit, showWeight: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Set",
            fontSize = AppText.caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(SET_NUMBER_WIDTH)
        )
        EffortHeading(
            type = effortType,
            onToggle = onToggleEffort,
            // Centred over the boxes, and allowed to spill a little past its column at a large
            // font size rather than squeezing out the info button.
            modifier = Modifier.weight(1f).padding(start = RPE_SHIFT).wrapContentWidth(unbounded = true)
        )
        if (showWeight) {
            WeightColumn(showUnit = false) {
                ColumnHeading("Weight", Modifier.width(WEIGHT_BOX_WIDTH).wrapContentWidth(unbounded = true))
            }
        }
        RepsCell {
            ColumnHeading("Reps", Modifier.width(SET_BOX_WIDTH))
        }
        Spacer(modifier = Modifier.width(DELETE_COLUMN_WIDTH))
    }
}

// One set row, at the shared height.
@Composable
fun SetTableRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SET_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// The labels down the Set column: normal sets numbered 1, 2, 3 among themselves, and the other
// kinds by their letter (W, D, F).
fun setLabels(types: List<SetType>): List<String> {
    var number = 0
    return types.map { it.letter ?: (++number).toString() }
}

private val WARM_UP_ORANGE = Color(0xFFFFA726)
private val DROP_BLUE = Color(0xFF42A5F5)

@Composable
fun SetType.color(): Color = when (this) {
    SetType.NORMAL -> LocalContentColor.current
    SetType.WARM_UP -> WARM_UP_ORANGE
    SetType.DROP -> DROP_BLUE
    SetType.FAILURE -> MaterialTheme.colorScheme.error
}

// The set's number, or its letter for a warm-up, drop or failure set. Tapping it opens a menu to
// change the kind of set, when onTypeChange is given.
@Composable
fun SetNumberCell(
    label: String,
    type: SetType = SetType.NORMAL,
    isPr: Boolean = false,
    onTypeChange: ((SetType) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(SET_NUMBER_WIDTH)
            .fillMaxHeight()
            .then(if (onTypeChange != null) Modifier.clickable { menuOpen = true } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = AppText.subheading,
            color = type.color(),
            fontWeight = if (type == SetType.NORMAL) null else FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        // A record-setting set gets a small tag in the column's corner.
        if (isPr) PrBadge(text = "PR", fontSize = AppText.badge, solid = true, modifier = Modifier.align(Alignment.TopEnd))

        if (onTypeChange != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SetType.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, color = option.color()) },
                        leadingIcon = { Text(option.letter ?: "1", color = option.color(), fontWeight = FontWeight.Bold) },
                        trailingIcon = if (option == type) {
                            { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                        } else null,
                        onClick = {
                            menuOpen = false
                            if (option != type) onTypeChange(option)
                        }
                    )
                }
            }
        }
    }
}

// A small gold pill marking a personal record. solid is for the tiny "PR" tag on a set, which
// needs a strong colour to show up on a purple ticked-off row.
@Composable
fun PrBadge(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = AppText.caption, solid: Boolean = false) {
    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = if (solid) FontWeight.Bold else FontWeight.SemiBold,
        color = if (solid) RecordGold.onSolid() else RecordGold.color(),
        modifier = modifier
            .background(if (solid) RecordGold.solid() else RecordGold.container(), RoundedCornerShape(50))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

// The records an exercise set, as pills in a row.
@Composable
fun PrBadgeRow(labels: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { PrBadge(text = it) }
    }
}

@Composable
private fun ColumnHeading(text: String, modifier: Modifier) {
    Text(text = text, fontSize = AppText.caption, textAlign = TextAlign.Center, maxLines = 1, modifier = modifier)
}

// The RPE/RIR column, with its content centered.
@Composable
fun RowScope.SetCell(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(start = RPE_SHIFT)
    ) {
        content()
    }
}

// The weight box with its unit on the right. The heading row uses this too, with the unit
// hidden, so "Weight" sits exactly over the boxes. The column is pushed right by
// WEIGHT_COLUMN_SHIFT, leaving empty space on its left for a long weight's box to grow into.
@Composable
fun WeightColumn(showUnit: Boolean = true, box: @Composable () -> Unit) {
    val unitLabel = LocalWeightUnit.current.label
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = WEIGHT_COLUMN_SHIFT)
    ) {
        box()
        UnitLabel(unitLabel, visible = showUnit)
    }
}

@Composable
private fun UnitLabel(text: String, visible: Boolean) {
    Text(
        text = text,
        fontSize = AppText.caption,
        color = if (visible) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = UNIT_GAP)
    )
}

// The Reps column, which fills the space up to the X. Its box is centered between
// the unit text before it and the X icon after it.
@Composable
fun RowScope.RepsCell(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(start = DELETE_ICON_MARGIN - UNIT_GAP)
    ) {
        content()
    }
}

@Composable
fun RemoveSetButton(onClick: () -> Unit, contentDescription: String) {
    IconButton(onClick = onClick, modifier = Modifier.size(DELETE_COLUMN_WIDTH)) {
        Icon(Icons.Filled.Close, contentDescription = contentDescription, modifier = Modifier.size(DELETE_ICON_SIZE))
    }
}

// The box at the end of a set's row for ticking it off as done. It's outlined in red when an
// unticked set has stopped the workout from being saved.
@Composable
fun SetDoneCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, highlight: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DELETE_COLUMN_WIDTH)
            .then(
                if (highlight) Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// How much of the screen's width an exercise card takes: the same as every other card.
const val EXERCISE_CARD_WIDTH = CARD_WIDTH_FRACTION

// The space between cards in a list, the same on every screen.
val CARD_GAP = 12.dp

// How much of the screen's width a wider-than-standard pop-up takes, the same for every one.
const val POPUP_WIDTH_FRACTION = 0.9f

// The width of the Add Exercise button when it's on its own (on a running workout it spans the
// Discard and Finish buttons under it, which comes to about the same).
val ADD_EXERCISE_BUTTON_WIDTH = 288.dp

// The purple Add Exercise button under a list of exercises. It looks the same on a running
// workout, a past workout and in the program editor.
@Composable
fun AddExerciseButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val purple = MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        // The same purple as its border, with text in the screen's background colour.
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = purple,
            contentColor = MaterialTheme.colorScheme.background
        ),
        border = BorderStroke(1.dp, purple),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = modifier.height(36.dp)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add Exercise", fontSize = AppText.small, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun AddSetButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        // The default vertical padding leaves no room for the text in a button this short.
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(SET_ROW_HEIGHT)
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Add Set", fontSize = AppText.body)
    }
}

// "RPE ⇄" or "RIR ⇄", which switches to the other when tapped, and an "i" that explains it.
@Composable
fun EffortHeading(type: EffortType, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    var showInfo by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${type.label} ⇄",
            fontSize = AppText.caption,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(2.dp)
        )
        IconButton(onClick = { showInfo = true }, modifier = Modifier.size(20.dp)) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "What ${type.label} means",
                modifier = Modifier.size(14.dp)
            )
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            text = { Text("${type.label}: ${type.meaning}") },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } }
        )
    }
}

// ---------------------------------------------------------------------------
// Exercise cards.
// ---------------------------------------------------------------------------

// An exercise's name at the top of its card. Its type ([Strength] and so on) isn't shown.
@Composable
fun ExerciseTitle(name: String, modifier: Modifier = Modifier) {
    Text(text = name, fontSize = AppText.subheading, modifier = modifier)
}

// The size of each small button in a card's top-right corner, padding included.
val CORNER_BUTTON_SPACE = 32.dp

// A small icon button for a card's corner. Place it with align(TopEnd) and, for the second
// one, end padding of CORNER_BUTTON_SPACE.
@Composable
fun CornerIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(4.dp)
            .size(28.dp),
        content = content
    )
}

@Composable
fun CornerIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    CornerIconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------
// Dialogs.
// ---------------------------------------------------------------------------

// Asks before something is deleted. Cancel, or tapping outside, keeps it.
// Built like Android's standard pop-up, at the same width, but with less empty space above,
// between and below the lines.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp)
        ) {
            // IntrinsicSize.Max keeps the box as wide as its text needs (as before), so the
            // full-width button row below doesn't stretch it.
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Cancel in the bottom left corner, the delete button in the bottom right.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = onConfirm) {
                        Text(confirmLabel, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// Tapping anywhere that doesn't use the tap itself (not a button, box, check box or scroll)
// takes the cursor out of the box being typed in, which closes the keyboard. It looks at each
// touch after everything inside has had its turn, so it only acts on taps nothing else took.
fun Modifier.clearFocusOnBackgroundTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (down.isConsumed) return@awaitEachGesture
            val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
            if (up != null && !up.isConsumed) focusManager.clearFocus()
        }
    }
}

// When the user leaves the app, the cursor is taken out of the box being typed in and the
// keyboard is closed, so it isn't still open when they come back.
@Composable
fun ClearFocusWhenAppStops() {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
