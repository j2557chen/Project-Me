package com.example.myfitnessapp

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The outlines of the front and back figures on the Home muscle map.
//
// Coordinates are in a 600 x 1000 box, with the body's midline at x = 300. Each outline is a
// list of points going around the shape; the drawing smooths it into curves unless `smooth` is
// false. Shapes on the viewer's left are written once and mirrored for the right.

const val FIGURE_BOX_WIDTH = 600f
// The part of the box the figure fills, so the drawing isn't padded with empty space.
const val FIGURE_LEFT = 70f
const val FIGURE_RIGHT = 530f
const val FIGURE_TOP = 25f
const val FIGURE_BOTTOM = 950f

enum class BodyPart { MUSCLE, SKIN }

// One shape on a figure. `muscle` is set only for muscles.
data class FigureShape(
    val part: BodyPart,
    val muscle: Muscle?,
    val points: List<Offset>,
    val smooth: Boolean = true
) {
    // Whether a point is inside the outline, using the points as a polygon. Close enough to the
    // smoothed curve for picking which muscle was tapped.
    fun contains(x: Float, y: Float): Boolean {
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}

private fun pts(vararg xy: Int): List<Offset> = xy.toList().chunked(2) { (x, y) -> Offset(x.toFloat(), y.toFloat()) }

private fun mirrored(points: List<Offset>) = points.map { Offset(FIGURE_BOX_WIDTH - it.x, it.y) }.reversed()

private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, count: Int = 16): List<Offset> =
    (0 until count).map { i ->
        val angle = 2 * PI * i / count
        Offset(cx + rx * cos(angle).toFloat(), cy + ry * sin(angle).toFloat())
    }

// A rectangle with its corners cut, which smooths into a rounded rectangle.
private fun roundedBox(left: Int, top: Int, right: Int, bottom: Int, inset: Int = 6): List<Offset> = pts(
    left + inset, top, right - inset, top, right, top + inset, right, bottom - inset,
    right - inset, bottom, left + inset, bottom, left, bottom - inset, left, top + inset
)

// Reshapes a long shape along the line through its top and bottom points: `thickness` scales it
// across that line, and `length` along it. The length changes from the top end, or from the
// bottom end when `keepBottom` is true, so that end stays put.
private fun reshaped(
    points: List<Offset>,
    thickness: Float = 1f,
    length: Float = 1f,
    keepBottom: Boolean = false
): List<Offset> {
    val top = points.minBy { it.y }
    val bottom = points.maxBy { it.y }
    val axis = bottom - top
    val along = axis / axis.getDistance()
    val anchor = if (keepBottom) bottom else top
    return points.map { p ->
        val d = p - anchor
        val t = d.x * along.x + d.y * along.y
        val onAxis = anchor + along * t
        anchor + along * (t * length) + (p - onAxis) * thickness
    }
}

// How much thicker the arm muscles are drawn than their outlines below.
private const val ARM_THICKNESS = 1.35f

private fun muscle(muscle: Muscle, points: List<Offset>) =
    listOf(FigureShape(BodyPart.MUSCLE, muscle, points), FigureShape(BodyPart.MUSCLE, muscle, mirrored(points)))

private fun skinPair(points: List<Offset>, smooth: Boolean = true) =
    listOf(FigureShape(BodyPart.SKIN, null, points, smooth), FigureShape(BodyPart.SKIN, null, mirrored(points), smooth))

private fun skin(points: List<Offset>, smooth: Boolean = true) = listOf(FigureShape(BodyPart.SKIN, null, points, smooth))

// An open hand, palm forward: the palm, the thumb out to the side, and four fingers.
private val HAND: List<List<Offset>> = listOf(
    pts(108, 462, 140, 462, 148, 486, 144, 512, 106, 512, 100, 488),
    pts(102, 478, 84, 490, 74, 506, 82, 510, 104, 500),
    pts(102, 512, 111, 512, 108, 548, 99, 546),
    pts(113, 514, 122, 514, 121, 558, 112, 557),
    pts(124, 514, 133, 514, 134, 556, 125, 557),
    pts(135, 512, 144, 510, 149, 542, 141, 545)
)
private fun hands() = HAND.flatMap { skinPair(it) }

// The forearm, shortened so the biceps have more of the arm. The back view uses it too.
private val FRONT_FOREARM = reshaped(
    pts(176, 330, 160, 355, 142, 400, 126, 456, 142, 460, 166, 410, 186, 362, 190, 336),
    ARM_THICKNESS, length = 0.75f, keepBottom = true
)

// Drawn in order, so later shapes sit on top of earlier ones.
val FRONT_FIGURE: List<FigureShape> =
    skinPair(ellipse(262f, 92f, 7f, 13f)) +                                          // ears
            skin(ellipse(300f, 86f, 37f, 50f)) +                                     // face
            skin(pts(281, 128, 319, 128, 330, 172, 270, 172), smooth = false) +      // neck
            skinPair(pts(284, 134, 293, 134, 301, 172, 290, 172), smooth = false) +  // neck muscles
            skinPair(pts(270, 158, 246, 176, 228, 186, 256, 191, 288, 182)) +        // slope of the traps
            skin(pts(236, 440, 258, 448, 285, 460, 300, 470, 315, 460, 342, 448, 364, 440, 372, 468, 335, 520, 300, 535, 265, 520, 228, 468)) + // hips
            skinPair(pts(234, 676, 266, 676, 264, 690, 236, 690)) +                  // knees, a thin strip like the back's
            skinPair(pts(232, 694, 222, 740, 222, 800, 235, 850, 250, 858, 262, 830, 270, 760, 270, 705, 252, 696)) + // shins
            skinPair(pts(236, 862, 226, 900, 212, 934, 240, 944, 262, 940, 262, 900, 258, 864)) + // feet
            hands() +
            muscle(Muscle.SHOULDERS, pts(236, 188, 210, 182, 186, 192, 175, 215, 178, 240, 192, 252, 208, 240, 222, 212)) +
            muscle(Muscle.CHEST, pts(297, 196, 262, 190, 238, 196, 224, 214, 222, 246, 236, 268, 262, 280, 297, 278)) +
            // The biceps fill the upper arm out to the torso.
            muscle(Muscle.BICEPS, pts(200, 254, 178, 272, 162, 312, 158, 349, 178, 350, 198, 332, 212, 318, 222, 296, 224, 264, 214, 252)) +
            muscle(Muscle.FOREARMS, FRONT_FOREARM) +
            // Serratus strips along the ribs, then the obliques below them.
            muscle(Muscle.ABS, pts(234, 276, 254, 282, 252, 294, 232, 288)) +
            muscle(Muscle.ABS, pts(233, 296, 254, 302, 252, 314, 231, 308)) +
            muscle(Muscle.ABS, pts(233, 316, 254, 322, 252, 334, 232, 328)) +
            muscle(Muscle.ABS, pts(254, 340, 236, 344, 232, 382, 236, 420, 248, 442, 257, 438, 256, 384)) +
            muscle(Muscle.ABS, roundedBox(259, 284, 297, 315)) +
            muscle(Muscle.ABS, roundedBox(259, 320, 297, 352)) +
            muscle(Muscle.ABS, roundedBox(259, 357, 297, 381)) +
            muscle(Muscle.ABS, pts(263, 386, 297, 386, 297, 456, 287, 452, 268, 422, 258, 399)) +
            // The whole thigh, inner side included.
            muscle(Muscle.QUADS, pts(242, 452, 226, 468, 214, 515, 214, 585, 222, 640, 238, 670, 262, 670, 278, 628, 290, 572, 296, 512, 284, 480, 258, 464))

val BACK_FIGURE: List<FigureShape> =
    skinPair(ellipse(259f, 94f, 7f, 13f)) +                                          // ears
            skin(ellipse(300f, 84f, 40f, 52f)) +
            skin(pts(282, 120, 318, 120, 324, 176, 276, 176), smooth = false) +      // neck
            hands() +
            skinPair(pts(232, 686, 266, 686, 264, 702, 236, 702)) +                  // backs of the knees
            skinPair(pts(232, 908, 214, 936, 258, 942, 258, 910)) +                  // heels
            // The lats, then the shoulder-blade muscles above them.
            muscle(Muscle.UPPER_BACK, pts(212, 262, 222, 310, 240, 360, 258, 392, 270, 368, 286, 320, 276, 286, 262, 266, 236, 272)) +
            muscle(Muscle.UPPER_BACK, pts(246, 196, 222, 204, 208, 222, 210, 248, 232, 262, 262, 256, 258, 226)) +
            muscle(Muscle.TRAPS, pts(298, 176, 272, 178, 246, 182, 240, 193, 258, 214, 276, 250, 290, 290, 298, 306)) +
            muscle(Muscle.SHOULDERS, pts(232, 190, 206, 190, 186, 200, 176, 226, 180, 250, 196, 244, 214, 220)) +
            // As full as the biceps on the front, stopping at the edge of the lats.
            muscle(Muscle.TRICEPS, pts(200, 252, 178, 270, 162, 310, 158, 346, 178, 349, 196, 334, 208, 316, 214, 294, 214, 266, 208, 254)) +
            muscle(Muscle.FOREARMS, FRONT_FOREARM) +
            muscle(Muscle.LOWER_BACK, pts(298, 312, 288, 330, 274, 368, 262, 404, 270, 424, 298, 426)) +
            muscle(Muscle.LOWER_BACK, pts(238, 378, 234, 400, 242, 420, 256, 414, 254, 390)) +
            // The hamstrings, then the glutes over them.
            muscle(Muscle.HAMSTRINGS, pts(222, 512, 212, 550, 214, 610, 222, 665, 234, 688, 256, 684, 272, 676, 284, 620, 290, 560, 286, 536, 256, 532)) +
            muscle(Muscle.GLUTES, pts(298, 435, 270, 430, 244, 437, 228, 456, 222, 491, 228, 515, 256, 530, 296, 525)) +
            // The calf, tapering down to the heel.
            muscle(Muscle.CALVES, pts(232, 704, 220, 740, 220, 780, 230, 812, 233, 860, 238, 904, 252, 904, 257, 860, 259, 814, 266, 790, 270, 750, 264, 714, 248, 704))
