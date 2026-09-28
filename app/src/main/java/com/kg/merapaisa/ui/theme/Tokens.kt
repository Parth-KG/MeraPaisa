package com.kg.merapaisa.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The measurements the whole app is built from.
 *
 * Before this there were sixteen text sizes and six corner radii, none of them named, each chosen
 * at the moment it was typed. That is what makes a screen read as a template: not any single
 * number, but the absence of a system behind them. A small fixed set forces a decision about
 * hierarchy, because the only way to make something stand out is to move it a whole step.
 */

/**
 * A four-based scale. Four is a hair, eight separates items, twelve pads a control, sixteen is the
 * screen gutter, twenty-four separates sections, thirty-two opens a screen.
 */
object Spacing {
    /** Between a label and the thing it labels. */
    val xs: Dp = 4.dp

    /** Between items in a row, and around dense controls. */
    val sm: Dp = 8.dp

    /** Inside a control: numpad keys, chips, sheet rows. */
    val md: Dp = 12.dp

    /** The screen gutter, and the default gap between unrelated things. */
    val lg: Dp = 16.dp

    /** Between sections of a screen. */
    val xl: Dp = 24.dp

    /** Above a screen's first element, and below its last. */
    val xxl: Dp = 32.dp
}

/**
 * Radius tied to the size of the thing, not applied uniformly.
 *
 * One radius on everything is the card kit look: a numpad key and a bottom sheet end up equally
 * round, and nothing reads as bigger or smaller than anything else. A nested corner is the outer
 * radius minus the padding, so the inner and outer curves stay concentric.
 */
object Shapes {
    /** Chips, small buttons, numpad keys. */
    val small = RoundedCornerShape(Spacing.sm)

    /** Containers that hold other things: an input, a grouped block, a dialog. */
    val medium = RoundedCornerShape(Spacing.md)

    /** Sheets and anything that reads as a surface arriving from an edge. */
    val large = RoundedCornerShape(Spacing.xl)

    /** Avatars only. */
    val circle = RoundedCornerShape(percent = 50)

    /** A sheet is only round at the top, because the bottom runs off the screen. */
    val sheet = RoundedCornerShape(topStart = Spacing.xl, topEnd = Spacing.xl, bottomStart = 0.dp, bottomEnd = 0.dp)

    /** The inner radius that stays concentric inside [outer] when inset by [padding]. */
    fun nested(outer: Dp, padding: Dp) = RoundedCornerShape((outer - padding).coerceAtLeast(0.dp))
}

/**
 * Durations and easings, for the few places where movement explains something.
 *
 * Nothing animates on load and nothing loops. An amount changing, shares redistributing after a
 * lock, a person moving to Settled: those are changes a person needs to follow, and motion is how
 * you follow them. Everything else is decoration that costs a frame.
 *
 * Material 3 1.4.0 has no MotionScheme, so the standard easing curves are written out here.
 */
object Motion {
    /** A small thing appearing or a colour changing. */
    const val quick = 150

    /** The default: an amount counting to a new value, a row reordering. */
    const val medium = 200

    /** A sheet or a screen arriving. */
    const val slow = 250

    /** The longest anything should take. Past this it reads as lag, not motion. */
    const val slowest = 300

    /** Movement that starts and ends on screen. */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Something entering: fast in, settling gently. */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Something leaving: gentle start, quick exit. */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}
