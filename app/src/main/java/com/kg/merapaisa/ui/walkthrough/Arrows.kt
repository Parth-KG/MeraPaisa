package com.kg.merapaisa.ui.walkthrough

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max

/** One arrow on a walkthrough page, from its callout to the part it names. */
internal data class Arrow(val start: Offset, val end: Offset)

/**
 * The arrow from a callout to its target, or null when the callout is neither above nor below it.
 *
 * Callouts sit in a band above or below the picture, so an arrow runs up or down. It leaves the
 * callout's facing edge as close above or below the target's centre as the callout allows, and
 * meets the target's facing edge as close under that as the target allows: straight down where the
 * two overlap, a short slant where they don't. Two callouts side by side, each over its own half of
 * the picture, so get arrows that never cross.
 *
 * [inset] keeps both ends off the corners; [gap] leaves air between an arrow and what it joins.
 */
internal fun arrowBetween(callout: Rect, target: Rect, inset: Float, gap: Float): Arrow? {
    val calloutBelow = callout.top >= target.bottom
    val calloutAbove = callout.bottom <= target.top
    if (!calloutBelow && !calloutAbove) return null
    val startX = target.center.x.coerceIn(callout.left + inset, max(callout.left + inset, callout.right - inset))
    val endX = startX.coerceIn(target.left + inset, max(target.left + inset, target.right - inset))
    return if (calloutBelow) {
        Arrow(Offset(startX, callout.top - gap), Offset(endX, target.bottom + gap))
    } else {
        Arrow(Offset(startX, callout.bottom + gap), Offset(endX, target.top - gap))
    }
}

/** True when the two arrows cross or touch. */
internal fun Arrow.crosses(other: Arrow): Boolean = segmentsMeet(start, end, other.start, other.end)

/** True when the arrow runs into or through [rect], as over another callout's words. */
internal fun Arrow.crosses(rect: Rect): Boolean {
    if (rect.contains(start) || rect.contains(end)) return true
    val corners = listOf(rect.topLeft, rect.topRight, rect.bottomRight, rect.bottomLeft)
    return corners.indices.any { i -> segmentsMeet(start, end, corners[i], corners[(i + 1) % 4]) }
}

private fun segmentsMeet(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
    val d1 = side(c, d, a)
    val d2 = side(c, d, b)
    val d3 = side(a, b, c)
    val d4 = side(a, b, d)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
    return (d1 == 0f && onSegment(c, d, a)) || (d2 == 0f && onSegment(c, d, b)) ||
        (d3 == 0f && onSegment(a, b, c)) || (d4 == 0f && onSegment(a, b, d))
}

private fun side(p: Offset, q: Offset, r: Offset): Float = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)

private fun onSegment(p: Offset, q: Offset, r: Offset): Boolean =
    r.x in minOf(p.x, q.x)..maxOf(p.x, q.x) && r.y in minOf(p.y, q.y)..maxOf(p.y, q.y)
