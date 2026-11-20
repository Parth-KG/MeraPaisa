package com.kg.merapaisa.ui.walkthrough

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the walkthrough's arrows run, worked out apart from any screen. */
class ArrowsTest {

    private val inset = 12f
    private val gap = 4f

    @Test
    fun aCalloutBelowPointsStraightUpWhenItSpansTheTarget() {
        val target = Rect(100f, 100f, 200f, 150f)
        val callout = Rect(0f, 300f, 400f, 340f)
        val arrow = arrowBetween(callout, target, inset, gap)!!
        assertEquals(Offset(150f, 296f), arrow.start)
        assertEquals(Offset(150f, 154f), arrow.end)
    }

    @Test
    fun aCalloutAbovePointsDown() {
        val target = Rect(100f, 300f, 200f, 350f)
        val callout = Rect(0f, 0f, 400f, 40f)
        val arrow = arrowBetween(callout, target, inset, gap)!!
        assertEquals(44f, arrow.start.y)
        assertEquals(296f, arrow.end.y)
    }

    @Test
    fun aCalloutBesideItsTargetSlantsOnlyAsFarAsItMust() {
        // The target sits in the right half; the callout over the left half leaves from its own
        // inner edge and meets the target at its nearest point.
        val target = Rect(300f, 100f, 380f, 140f)
        val callout = Rect(0f, 300f, 190f, 340f)
        val arrow = arrowBetween(callout, target, inset, gap)!!
        assertEquals(178f, arrow.start.x)
        assertEquals(312f, arrow.end.x)
    }

    @Test
    fun noArrowForACalloutLevelWithItsTarget() {
        assertNull(arrowBetween(Rect(0f, 100f, 100f, 140f), Rect(200f, 110f, 300f, 130f), inset, gap))
    }

    @Test
    fun twoCalloutsSideBySideNeverCross() {
        val width = 400f
        val left = Rect(0f, 500f, 190f, 560f)
        val right = Rect(210f, 500f, width, 560f)
        // Targets anywhere in the picture, each in the half its callout is over.
        val xs = listOf(0f, 40f, 120f, 180f)
        val ys = listOf(0f, 120f, 300f, 440f)
        for (lx in xs) for (ly in ys) for (rx in xs) for (ry in ys) {
            val leftTarget = Rect(lx, ly, lx + 20f, ly + 40f)
            val rightTarget = Rect(210f + rx, ry, 230f + rx, ry + 40f)
            val a = arrowBetween(left, leftTarget, inset, gap)!!
            val b = arrowBetween(right, rightTarget, inset, gap)!!
            assertFalse("$leftTarget and $rightTarget", a.crosses(b))
            assertFalse(a.crosses(right))
            assertFalse(b.crosses(left))
        }
    }

    @Test
    fun crossingIsFoundBothWays() {
        val a = Arrow(Offset(0f, 0f), Offset(100f, 100f))
        val b = Arrow(Offset(0f, 100f), Offset(100f, 0f))
        assertTrue(a.crosses(b))
        assertTrue(a.crosses(Rect(40f, 40f, 60f, 60f)))
        assertFalse(a.crosses(Rect(70f, 0f, 100f, 20f)))
    }

    @Test
    fun everyPageHasCalloutsThatFitAndNameTheirOwnTarget() {
        for (page in TourPages) {
            val ids = page.callouts.map { it.target }
            assertEquals("one callout per part on ${page.title}", ids.toSet().size, ids.size)
            assertTrue("at most three arrows on ${page.title}", ids.size <= 3)
            for (callout in page.callouts) {
                assertTrue(
                    "\"${callout.text}\" is ${callout.text.length} characters",
                    callout.text.length <= MAX_CALLOUT_CHARS
                )
                assertFalse(callout.text.contains('!'))
                assertFalse("a symbol TalkBack can't say needs words", callout.text.contains('−') && callout.spoken == null)
            }
            if (page.picture != null) assertNotNull("${page.title} says what its picture shows", page.pictureDescription)
            else assertTrue(page.callouts.isEmpty())
        }
    }
}
