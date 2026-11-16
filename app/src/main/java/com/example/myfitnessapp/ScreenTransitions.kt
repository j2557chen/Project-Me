package com.example.myfitnessapp

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private const val SLIDE_MILLIS = 250

// The animation between a list and the screen it opens, like History and a workout. Going
// forward, the new screen slides in a little from the right and fades in over the list, which
// shifts slightly left. Going back is the same in reverse, with the closing screen kept on top.
fun screenSlideTransition(forward: Boolean): ContentTransform {
    val slide = tween<androidx.compose.ui.unit.IntOffset>(SLIDE_MILLIS, easing = FastOutSlowInEasing)
    val fade = tween<Float>(SLIDE_MILLIS, easing = FastOutSlowInEasing)
    return if (forward) {
        ContentTransform(
            targetContentEnter = slideInHorizontally(slide) { it / 4 } + fadeIn(fade),
            initialContentExit = slideOutHorizontally(slide) { -it / 10 } + fadeOut(fade),
            targetContentZIndex = 1f
        )
    } else {
        ContentTransform(
            targetContentEnter = slideInHorizontally(slide) { -it / 10 } + fadeIn(fade),
            initialContentExit = slideOutHorizontally(slide) { it / 4 } + fadeOut(fade),
            targetContentZIndex = -1f
        )
    }
}
