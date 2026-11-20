package com.kg.merapaisa.ui.walkthrough

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo

/**
 * The parts of the app a walkthrough arrow can point at.
 *
 * Each id is set inside the real component that draws the part ([tourTarget]), not worked out from
 * where the part happens to sit. So an arrow follows its target through any theme, text size or
 * change of layout, and a component that loses its id fails a test instead of leaving an arrow
 * pointing at nothing.
 */
enum class TourId {
    Total, PersonFigure, EvenTab, AddPerson,
    KeypadNote, KeypadDirection, KeypadSettleUp,
    PersonHistory, MenuReminder, MenuShowQr,
    ShareFigure, ShareLock, SaveSplit,
    WhoPaysWhom, MoveToLedger, AddExpense
}

/**
 * Where each named part, and each callout, was last laid out on a walkthrough page.
 *
 * Coordinates are kept as they come, and turned into rectangles against the page only when the
 * arrows are drawn, so a page that scrolls carries its arrows with it.
 */
class TourTargets {
    private val placed = mutableMapOf<Any, LayoutCoordinates>()

    /** Bumped on every placement, so whatever draws the arrows draws again. */
    internal val revision = mutableIntStateOf(0)

    internal fun place(key: Any, coordinates: LayoutCoordinates) {
        placed[key] = coordinates
        revision.intValue++
    }

    /** Every key placed so far, for tests. */
    val keys: Set<Any> get() = placed.keys

    /** The part's bounds in [page]'s coordinates, or null if it isn't on the page. */
    fun boundsIn(page: LayoutCoordinates, key: Any): Rect? {
        val coordinates = placed[key] ?: return null
        if (!coordinates.isAttached || !page.isAttached) return null
        return page.localBoundingBoxOf(coordinates, clipBounds = false)
    }
}

/** Null everywhere but inside a walkthrough page, where the parts are being looked for. */
val LocalTourTargets = staticCompositionLocalOf<TourTargets?> { null }

/**
 * Names this part for the walkthrough's arrows. Outside a walkthrough page it does nothing at all.
 */
fun Modifier.tourTarget(id: TourId): Modifier = this then TourTargetElement(id)

/** A callout's own place on the page, so its arrow can start at its edge. */
internal fun Modifier.calloutPlace(id: TourId): Modifier = this then TourTargetElement(CalloutKey(id))

internal data class CalloutKey(val id: TourId)

private data class TourTargetElement(val key: Any) : ModifierNodeElement<TourTargetNode>() {
    override fun create() = TourTargetNode(key)
    override fun update(node: TourTargetNode) {
        node.key = key
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "tourTarget"
        value = key
    }
}

private class TourTargetNode(var key: Any) :
    Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        currentValueOf(LocalTourTargets)?.place(key, coordinates)
    }
}
