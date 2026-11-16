package com.example.myfitnessapp

import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// The app's text sizes, all in one place.
// ---------------------------------------------------------------------------
// Every piece of text picks one of these by name (fontSize = AppText.body), so the same kind
// of text is the same size on every screen, and a size can be changed for the whole app here.
// "sp" is like dp but also grows with the font size set in the phone's settings.
object AppText {
    // The big title on a page of its own: the welcome page's app name, and "Workout" and
    // "Record" on those screens' start pages.
    val pageTitle = 32.sp

    // A single big number worth a glance, like the streak on Home.
    val stat = 28.sp

    // The title in a screen's header.
    val screenTitle = 24.sp

    // The heading of a card or a section, and the main value beside it.
    val heading = 20.sp

    // A step below a heading: an exercise's name on its card, a set's number.
    val subheading = 18.sp

    // Ordinary text.
    val body = 16.sp

    // Secondary text: labels, dates, and explanations under a heading.
    val small = 14.sp

    // The smallest text meant for reading: chart axes, footnotes, compact buttons.
    val caption = 12.sp

    // The tiny "PR" badge on a set.
    val badge = 8.sp
}
