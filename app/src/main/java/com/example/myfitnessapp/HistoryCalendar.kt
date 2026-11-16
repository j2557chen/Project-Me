package com.example.myfitnessapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// The calendar's shown month and the days picked on it. It's kept above the History screen,
// so opening a workout and coming back keeps the selection.
class CalendarSelection(
    monthOffset: Int = 0,
    selectedDays: Set<LocalDate> = emptySet(),
    multiSelect: Boolean = false
) {
    // Months away from the current one (0 = this month, -1 = last month).
    var monthOffset by mutableIntStateOf(monthOffset)
    var selectedDays by mutableStateOf(selectedDays)
        private set
    // True after a press and hold: taps then add or remove days instead of replacing the day.
    var multiSelect by mutableStateOf(multiSelect)
        private set

    val month: YearMonth get() = YearMonth.now().plusMonths(monthOffset.toLong())

    // A tap. Normally it picks just that day (or unpicks it if it was the only one). While
    // picking several, it adds or removes the day, and removing the last one stops picking several.
    fun tap(date: LocalDate) {
        if (multiSelect) {
            selectedDays = if (date in selectedDays) selectedDays - date else selectedDays + date
            if (selectedDays.isEmpty()) multiSelect = false
        } else {
            selectedDays = if (selectedDays == setOf(date)) emptySet() else setOf(date)
        }
    }

    // A press and hold starts picking several days, keeping any day already picked.
    fun longPress(date: LocalDate) {
        multiSelect = true
        selectedDays = selectedDays + date
    }

    fun clear() {
        selectedDays = emptySet()
        multiSelect = false
    }

    companion object {
        // Saved as one list: the month offset, 1 or 0 for picking several, then each selected
        // day as a day number.
        val Saver = listSaver<CalendarSelection, Long>(
            save = {
                listOf(it.monthOffset.toLong(), if (it.multiSelect) 1L else 0L) +
                        it.selectedDays.map(LocalDate::toEpochDay)
            },
            restore = { saved ->
                CalendarSelection(
                    monthOffset = saved[0].toInt(),
                    multiSelect = saved[1] == 1L,
                    selectedDays = saved.drop(2).map(LocalDate::ofEpochDay).toSet()
                )
            }
        )
    }
}

// The ring around a picked day: a circle for one day, a rounded square while picking several.
private val SELECTION_BLUE = Color(0xFF2F8CFF)
private val MULTI_SELECT_SHAPE = RoundedCornerShape(12.dp)

// A month of days. Days with a workout are filled purple. Tapping a day picks it; pressing and
// holding one starts picking several, each outlined in a blue rounded square.
@Composable
fun MonthCalendar(
    selection: CalendarSelection,
    workoutDates: Set<LocalDate>,
    modifier: Modifier = Modifier,
    // Days with a workout that set a record get a gold mark.
    prDates: Set<LocalDate> = emptySet(),
    // The kinds of activity done on each day, shown as small coloured dots.
    dayKinds: Map<LocalDate, List<ActivityKind>> = emptyMap()
) {
    val month = selection.month
    val haptics = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { selection.monthOffset-- }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous month")
            }
            Text(
                text = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                fontSize = AppText.heading
            )
            IconButton(onClick = { selection.monthOffset++ }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month")
            }
        }

        // The week starts on the region's first day (Sunday in Canada, Monday in much of Europe).
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

        Row(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    text = firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                    fontSize = AppText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
            }
        }

        // Swiping the days sideways moves a month, like the arrows: left for the next month,
        // right for the one before. Taps still reach the days, since a swipe only takes over once
        // the finger moves sideways.
        val swipeThreshold = with(LocalDensity.current) { 60.dp.toPx() }
        // The new month slides in from the side it was swiped towards.
        AnimatedContent(
            targetState = selection.monthOffset,
            transitionSpec = { monthSlide(forward = targetState > initialState) },
            label = "month",
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(Unit) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragged += amount
                        },
                        onDragEnd = {
                            if (dragged < -swipeThreshold) selection.monthOffset++
                            else if (dragged > swipeThreshold) selection.monthOffset--
                        }
                    )
                }
        ) { offset ->
        val month = YearMonth.now().plusMonths(offset.toLong())
        // Empty cells before the 1st, so it lands under the right weekday.
        val leadingBlanks = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val rows = (leadingBlanks + month.lengthOfMonth() + 6) / 7
        val today = LocalDate.now()

        // Tapping anywhere in the grid that isn't a day (a blank cell or the gap between days)
        // clears the selection. Days handle their own taps.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = null) { selection.clear() }
        ) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (column in 0 until 7) {
                        val dayNumber = row * 7 + column - leadingBlanks + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNumber in 1..month.lengthOfMonth()) {
                                val date = month.atDay(dayNumber)
                                DayCell(
                                    day = dayNumber,
                                    hasWorkout = date in workoutDates,
                                    hasMedal = date in prDates,
                                    kinds = dayKinds[date].orEmpty(),
                                    isSelected = date in selection.selectedDays,
                                    multiSelect = selection.multiSelect,
                                    isToday = date == today,
                                    onClick = { selection.tap(date) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selection.longPress(date)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

// A whole month sliding across: forward brings the next month in from the right.
private fun monthSlide(forward: Boolean): ContentTransform {
    val slide = tween<IntOffset>(250, easing = FastOutSlowInEasing)
    val fade = tween<Float>(250)
    val sign = if (forward) 1 else -1
    return ContentTransform(
        targetContentEnter = slideInHorizontally(slide) { sign * it } + fadeIn(fade),
        initialContentExit = slideOutHorizontally(slide) { -sign * it } + fadeOut(fade)
    )
}

// A selected day gets a bold blue ring around it, with a gap so it stands out from the purple
// fill of a workout day. While picking several days, the ring is a rounded square instead.
// Today gets a thin outline.
@Composable
private fun DayCell(
    day: Int,
    hasWorkout: Boolean,
    hasMedal: Boolean,
    kinds: List<ActivityKind>,
    isSelected: Boolean,
    multiSelect: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val outerShape = if (multiSelect) MULTI_SELECT_SHAPE else CircleShape
    val ringColor = SELECTION_BLUE
    // The calendar card's own colour, as a thin ring that keeps the dots clear of the day's fill.
    val ringColorBehind = CardDefaults.cardColors().containerColor

    // The medal sits outside the day's clipped shape, so its edges aren't cut off.
    Box(modifier = Modifier.fillMaxSize()) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            // Keeps the tap ripple inside the ring's shape.
            .clip(outerShape)
            .then(if (isSelected) Modifier.border(3.dp, ringColor, outerShape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // Room for the ring and a small gap, kept whether or not the day is selected, so the
            // day's circle stays the same size and the ring goes around it.
            .padding(5.dp)
    ) {
        // A softer purple than the app's buttons, so the activity dots and gold stand out on it.
        Surface(
            color = if (hasWorkout) colors.primaryContainer else Color.Transparent,
            shape = CircleShape,
            border = if (isToday && !isSelected) BorderStroke(1.dp, colors.outline) else null,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = day.toString(),
                    fontSize = AppText.subheading,
                    color = if (hasWorkout) colors.onPrimaryContainer else colors.onSurface
                )
            }
        }
    }
    // A dot for each kind of activity done that day, along the bottom of the circle.
    if (kinds.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 1.dp)
        ) {
            kinds.forEach { kind ->
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .border(1.5.dp, ringColorBehind, CircleShape)
                        .background(kind.color(), CircleShape)
                )
            }
        }
    }
    // A gold mark in the top-right of a day when a record was set, clear of the activity dots
    // along the bottom (there can be up to three of those).
    if (hasMedal) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-1).dp, y = 1.dp)
                .size(15.dp)
                .border(2.dp, ringColorBehind, CircleShape)
                .padding(2.dp)
                .background(RecordGold.solid(), CircleShape)
        )
    }
    }
}
