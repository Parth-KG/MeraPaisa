package com.kg.merapaisa.ui.walkthrough

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.ButtonLabel
import com.kg.merapaisa.ui.FootActions
import com.kg.merapaisa.ui.Paragraph
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.coversLedger
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * How Mera Paisa works: a few pages, each a real part of the app drawn with a sample ledger and
 * arrows to its buttons, then a page to set things up.
 *
 * Shown once on a first run, after the app lock, and again from Settings. Swipe, or Back and Next
 * at the foot; Skip at the top leaves at any page but the last, and system back goes back a page
 * (leaving from the first). Nothing on these pages is saved but what the last page sets.
 *
 * [chooseTheme] is a first run's own first page, the themes (`WalkthroughThemes`); a replay has
 * Settings' Theme instead, so goes without. [setUp] is the last page's own rows; without it the
 * pages end with Groups. [initialPage] and [scrolledToEnd] are for the gallery, which photographs
 * each page and the foot of the tall ones.
 */
@Composable
fun WalkthroughScreen(
    finishLabel: String,
    onFinish: () -> Unit,
    chooseTheme: (@Composable () -> Unit)? = null,
    setUp: (@Composable () -> Unit)? = null,
    initialPage: Int = 0,
    scrolledToEnd: Boolean = false
) {
    val theme = LocalAppTheme.current
    val first = if (chooseTheme != null) 1 else 0
    val count = first + TourPages.size + if (setUp != null) 1 else 0
    val pager = rememberPagerState(initialPage = initialPage) { count }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val onLast = page == count - 1
    fun goTo(target: Int) {
        scope.launch { pager.animateScrollToPage(target) }
    }

    BackHandler(enabled = true) { if (page > 0) goTo(page - 1) else onFinish() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .coversLedger()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = Spacing.lg, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PageDots(current = page, count = count, modifier = Modifier.weight(1f))
            if (!onLast) {
                TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                    ButtonLabel("Skip", color = theme.textPrimary)
                }
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) { index ->
            val tour = index - first
            when {
                index < first -> chooseTheme?.invoke()
                tour < TourPages.size -> TourPageContent(TourPages[tour], scrolledToEnd)
                else -> setUp?.invoke()
            }
        }

        FootActions {
            if (page > 0) SecondaryAction("Back", enabled = true) { goTo(page - 1) }
            if (onLast) {
                PrimaryAction(finishLabel, enabled = true, onClick = onFinish)
            } else {
                PrimaryAction("Next", enabled = true) { goTo(page + 1) }
            }
        }
    }
}

/** Where the walkthrough is, as dots. TalkBack hears it as "Page 2 of 7". */
@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(Shapes.circle)
                    .background(if (i == current) theme.textPrimary else theme.border)
            )
        }
    }
}

/** The page's own place, read when its arrows are drawn. */
private class PagePlace {
    var coordinates: LayoutCoordinates? = null
}

/**
 * One page: its title and words, then its callouts and pictures in order. It scrolls when it is taller than the screen, at large type, and the arrows
 * are drawn in the scrolled content, so they move with it.
 */
@Composable
internal fun TourPageContent(
    page: TourPage,
    scrolledToEnd: Boolean = false,
    /** Where the page's parts are recorded; a test passes its own to read them. */
    targets: TourTargets = remember(page) { TourTargets() }
) {
    val theme = LocalAppTheme.current
    val place = remember(page) { PagePlace() }
    val scroll = rememberScrollState()
    if (scrolledToEnd) LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    val ink = theme.textSecondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .onGloballyPositioned { place.coordinates = it }
            .drawWithContent {
                drawContent()
                drawArrows(page, targets, place, ink)
            }
            .padding(bottom = Spacing.lg)
    ) {
        Text(
            page.title,
            style = MeraPaisaType.screenTitle,
            color = theme.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .semantics { heading() }
        )
        page.paragraphs.forEach {
            Spacer(Modifier.height(Spacing.sm))
            Paragraph(it)
        }
        if (page.sections.isNotEmpty()) Spacer(Modifier.height(Spacing.xl))
        var seenPicture = false
        page.sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(Spacing.xxl))
            when (section) {
                // Callouts above the first picture sit on its edge; any later ones hang from the top.
                is CalloutSection -> CalloutBand(section.band, targets, above = !seenPicture)
                is PictureSection -> {
                    seenPicture = true
                    Picture(section.description) { section.content(targets) }
                }
            }
        }
    }
}

/**
 * The real components, shown and not used. Taps stop at it (the pages still swipe), keyboard
 * focus passes over it, and TalkBack hears one line saying what it shows. A rule above and below
 * sets it apart from the page's own words.
 */
@Composable
private fun Picture(description: String, content: @Composable () -> Unit) {
    val edge = LocalAppTheme.current.border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .focusProperties { onEnter = { cancelFocusChange() } }
            .focusGroup()
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(edge))
            content()
            Box(Modifier.fillMaxWidth().height(1.dp).background(edge))
        }
        // On top of the picture and taking its touches, without consuming them: a long press
        // can't open a row's menu, and a swipe across the picture still turns the page.
        Box(Modifier.matchParentSize().coversLedger())
    }
}

@Composable
private fun CalloutBand(band: Band, targets: TourTargets, above: Boolean) {
    CompositionLocalProvider(LocalTourTargets provides targets) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = if (above) Alignment.Bottom else Alignment.Top
        ) {
            val wide = band.wide
            if (wide != null) {
                CalloutText(wide, TextAlign.Start, Modifier.weight(1f))
            } else {
                band.start?.let { CalloutText(it, TextAlign.Start, Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
                band.end?.let { CalloutText(it, TextAlign.End, Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CalloutText(callout: Callout, align: TextAlign, modifier: Modifier) {
    val spoken = callout.spoken
    Text(
        callout.text,
        style = MeraPaisaType.body,
        color = LocalAppTheme.current.textPrimary,
        textAlign = align,
        modifier = modifier
            .calloutPlace(callout.target)
            .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
    )
}

/**
 * Each callout's arrow, and a ring round the part it names. Thin lines in the grey ink, which
 * clears 7:1 on every page, with nothing animated.
 */
private fun DrawScope.drawArrows(page: TourPage, targets: TourTargets, place: PagePlace, ink: Color) {
    // Read so that a part laid out again draws its arrow again.
    targets.revision.intValue
    val coordinates = place.coordinates ?: return
    val stroke = 1.5.dp.toPx()
    val ringPad = Spacing.xs.toPx()
    val head = Spacing.sm.toPx()
    val inset = Spacing.md.toPx()
    val gap = Spacing.xs.toPx()
    val radius = CornerRadius(Spacing.sm.toPx())
    for (callout in page.callouts) {
        val target = targets.boundsIn(coordinates, callout.target) ?: continue
        val from = targets.boundsIn(coordinates, CalloutKey(callout.target)) ?: continue
        val ring = target.inflate(ringPad)
        drawRoundRect(ink, ring.topLeft, ring.size, radius, style = Stroke(stroke))
        val arrow = arrowBetween(from, ring, inset, gap) ?: continue
        drawLine(ink, arrow.start, arrow.end, stroke, StrokeCap.Round)
        val along = arrow.end - arrow.start
        val length = along.getDistance()
        if (length < 1f) continue
        val back = Offset(-along.x / length, -along.y / length)
        for (angle in listOf(ARROWHEAD_RADIANS, -ARROWHEAD_RADIANS)) {
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            val wing = Offset(back.x * c - back.y * s, back.x * s + back.y * c) * head
            drawLine(ink, arrow.end, arrow.end + wing, stroke, StrokeCap.Round)
        }
    }
}

private const val ARROWHEAD_RADIANS = 0.5
