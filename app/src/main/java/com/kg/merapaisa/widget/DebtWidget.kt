package com.kg.merapaisa.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.MainActivity
import com.kg.merapaisa.SecurityStore
import com.kg.merapaisa.ThemeStore
import com.kg.merapaisa.data.AppDatabase
import com.kg.merapaisa.data.CurrencyTotal
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.netTotalsByCurrency
import com.kg.merapaisa.getThemeByName
import com.kg.merapaisa.repository.LedgerChangeNotifier
import com.kg.merapaisa.repository.PersonRepository
import com.kg.merapaisa.ui.format.SignStyle
import com.kg.merapaisa.ui.format.amountSpoken
import com.kg.merapaisa.ui.AVATAR_WASH
import com.kg.merapaisa.ui.avatarInk
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.flow.first

/**
 * The widget sits on the home screen, which has its own light/dark mode independent of the
 * app. So it takes the user's chosen palette for whichever mode matches that palette, and a
 * legible counterpart for the other, instead of being dark-only.
 */
data class WidgetPalette(val day: AppTheme, val night: AppTheme) {
    fun of(pick: (AppTheme) -> Color) = ColorProvider(day = pick(day), night = pick(night))

    /**
     * The same three-way ink AmountText uses on screen. A figure of zero runs in neither
     * direction, and drawing it in the negative ink says you owe when you do not.
     */
    fun ink(amountMinor: Long) = when {
        amountMinor > 0L -> of { it.positive }
        amountMinor < 0L -> of { it.negative }
        else -> of { it.textSecondary }
    }
}

private fun paletteFor(themeName: String): WidgetPalette {
    val selected = getThemeByName(themeName)
    return if (selected.isDark) {
        WidgetPalette(day = getThemeByName("Paper"), night = selected)
    } else {
        WidgetPalette(day = selected, night = getThemeByName("Midnight"))
    }
}

/** Three cells by one: room for where you stand overall and nothing else. */
/** Four rows is what the taller widget holds, including the line that says what is left out. */
private const val MAX_WIDGET_ROWS = 4

private val NetOnly = DpSize(180.dp, 40.dp)

/** Four cells by two, the smallest size that can hold the net position and a name under it. */
private val WithRows = DpSize(250.dp, 110.dp)

/**
 * The initials block. Smaller than the 40dp one in PersonRow, because a widget has to fit three
 * rows in the height the main screen gives to one.
 */
private val AvatarSize = Spacing.xl + Spacing.xs

class DebtWidget : GlanceAppWidget() {

    /**
     * Two layouts, because the widget is resizeable and was only drawing one.
     *
     * With no sizeMode it inherited Single, so a widget dragged down to a strip still composed
     * the whole list and left the launcher to crop it: what survived was whichever row happened
     * to be on top, cut off mid-name. Now the strip states the net position and the taller sizes
     * open out into the people behind it.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(NetOnly, WithRows))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = paletteFor(ThemeStore.getTheme(context).first())

        // The app puts the ledger behind the device's own authentication when the lock is on.
        // A widget still listing names and amounts on the home screen would hand over exactly
        // what that lock exists to withhold, so it shows nothing and the ledger is not read.
        if (SecurityStore.isAppLockEnabled(context).first()) {
            provideContent { LockedWidgetContent(palette) }
            return
        }

        val repository = PersonRepository(AppDatabase.getDatabase(context).personDao())
        val persons = repository.personsWithBalances().first()
            .filter { !it.isSettled && it.balanceMinor != 0L }

        provideContent {
            WidgetContent(
                persons = persons,
                totals = netTotalsByCurrency(persons),
                palette = palette
            )
        }
    }
}

/** Says the app is locked and nothing more: no name, no figure, not even a count. */
@Composable
fun LockedWidgetContent(palette: WidgetPalette) {
    if (isCompact()) {
        Row(
            modifier = GlanceModifier.widgetSurface(palette, compact = true),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mera Paisa",
                style = labelStyle(palette),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Text("Locked", style = nameStyle(palette), maxLines = 1)
        }
    } else {
        Column(
            modifier = GlanceModifier.widgetSurface(palette, compact = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Mera Paisa", style = nameStyle(palette), maxLines = 1)
            Spacer(GlanceModifier.height(Spacing.xs))
            Text(
                "Locked. Open the app to see your ledger",
                style = labelStyle(palette),
                maxLines = 2
            )
        }
    }
}

@Composable
fun WidgetContent(
    persons: List<PersonWithBalance>,
    totals: List<CurrencyTotal>,
    palette: WidgetPalette
) {
    if (isCompact()) {
        NetPositionRow(
            persons = persons,
            totals = totals,
            palette = palette,
            modifier = GlanceModifier.widgetSurface(palette, compact = true)
        )
        return
    }

    Column(modifier = GlanceModifier.widgetSurface(palette, compact = false)) {
        NetPositionRow(
            persons = persons,
            totals = totals,
            palette = palette,
            modifier = GlanceModifier.fillMaxWidth()
        )
        Spacer(GlanceModifier.height(Spacing.sm))
        DoubleRule(palette)
        if (persons.isNotEmpty()) {
            Spacer(GlanceModifier.height(Spacing.sm))
            PersonList(persons, palette)
        }
    }
}

/** True in the strip-sized layout, where only the net position fits. */
@Composable
private fun isCompact(): Boolean = LocalSize.current.height < WithRows.height

/**
 * Where you stand overall: the label on the left, one figure per currency on the right.
 *
 * Never a sum across currencies, on the widget any more than on the main screen. Two figures is
 * what the width holds, and a third currency is worth the tap it takes to open the app.
 */
@Composable
private fun NetPositionRow(
    persons: List<PersonWithBalance>,
    totals: List<CurrencyTotal>,
    palette: WidgetPalette,
    modifier: GlanceModifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            overallLabel(persons, totals),
            style = labelStyle(palette),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        // Two figures is what a strip can hold. A third silently vanishing is the one truncation
        // that cannot be allowed to pass without a word: the line says "Overall", and somebody
        // reading two currencies has no way to know a third exists. Rows can be cut because the
        // list is plainly a list; a total that is quietly incomplete just reads as wrong.
        val shown = totals.take(2)
        shown.forEach { total ->
            Text(
                amountString(total.amountMinor, total.currency, SignStyle.Always),
                style = amountStyle(palette, total.amountMinor),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = Spacing.sm)
            )
        }
        if (totals.size > shown.size) {
            Text(
                "+${totals.size - shown.size}",
                style = labelStyle(palette),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = Spacing.xs)
            )
        }
    }
}

/**
 * The people you are not even with, in their own container.
 *
 * Glance generates a layout per child count and stops at ten, so the rows and the hairlines
 * between them are kept off the root column, where the net position and its rule already sit.
 */
@Composable
private fun PersonList(persons: List<PersonWithBalance>, palette: WidgetPalette) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        // Three rows when there are more to come, so the line saying so is on screen rather than
        // just off the bottom edge. A disclosure the widget crops away is no disclosure.
        val shown = if (persons.size > MAX_WIDGET_ROWS) persons.take(MAX_WIDGET_ROWS - 1)
        else persons.take(MAX_WIDGET_ROWS)
        shown.forEachIndexed { index, person ->
            if (index > 0) RowHairline(palette)
            PersonWidgetRow(person, palette)
        }
        // Says what it left out. A list that stops at four looks complete on a home screen, and
        // somebody owed money by a fifth person would never learn it from here.
        if (persons.size > shown.size) {
            Text(
                "and ${persons.size - shown.size} more",
                style = labelStyle(palette),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().padding(top = Spacing.sm)
            )
        }
    }
}

/** One person, built like PersonRow: the name gives way, the figure never does. */
@Composable
private fun PersonWidgetRow(person: PersonWithBalance, palette: WidgetPalette) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            // One description for the whole row, so TalkBack reads "Asha owes you 1,200 rupees"
            // rather than stopping at a name, then a pair of initials, then a figure.
            .semantics {
                contentDescription =
                    amountSpoken(person.balanceMinor, person.currency, person.name)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(AvatarSize)
                .background(avatarTint(person, palette))
                .roundedSquare(),
            contentAlignment = Alignment.Center
        ) {
            Text(person.name.take(2).uppercase(), style = initialsStyle(palette), maxLines = 1)
        }
        Spacer(GlanceModifier.width(Spacing.md))
        Text(
            person.name,
            style = nameStyle(palette),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            amountString(person.balanceMinor, person.currency, SignStyle.Always),
            style = amountStyle(palette, person.balanceMinor),
            maxLines = 1,
            modifier = GlanceModifier.padding(start = Spacing.sm)
        )
    }
}

/**
 * Two hairlines with a thread of surface between them, the mark NetPosition closes a total with.
 *
 * The one piece of ornament this design allows itself, and it earns its four dp here: it is what
 * separates the figure you are being told from the people it was added up from.
 */
@Composable
private fun DoubleRule(palette: WidgetPalette) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Hairline(palette) { it.textSecondary }
        Spacer(GlanceModifier.height(2.dp))
        Hairline(palette) { it.textSecondary }
    }
}

/**
 * A hairline between rows, inset to where the name begins, as RowDivider does on screen. A rule
 * that runs under the initials cuts that column in half and the list starts reading as a table.
 */
@Composable
private fun RowHairline(palette: WidgetPalette) {
    Box(modifier = GlanceModifier.fillMaxWidth().padding(start = AvatarSize + Spacing.md)) {
        Hairline(palette) { it.outline }
    }
}

/** Drawn as a spacer with a background, because Glance has no divider to take one from. */
@Composable
private fun Hairline(palette: WidgetPalette, pick: (AppTheme) -> Color) {
    Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(palette.of(pick)))
}

/**
 * The root of every layout here, labelled as the widget background.
 *
 * The launcher needs that label to know which view to round off and to grow the app out of when
 * it is tapped; without it the widget keeps square corners inside a rounded slot.
 */
private fun GlanceModifier.widgetSurface(
    palette: WidgetPalette,
    compact: Boolean
): GlanceModifier {
    val base = fillMaxSize()
        .appWidgetBackground()
        .background(palette.of { it.surface })
        // A strip cannot spare the screen gutter on all four sides.
        .padding(if (compact) Spacing.sm else Spacing.md)
        .clickable(onClick = actionStartActivity<MainActivity>())
    // The launcher's own radius only became readable in Android 12, and the outline it feeds
    // arrived with it, so below that there is nothing here to match and nothing to set.
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        base
    }
}

/** PfpView's rounded square, wherever the platform will draw one. */
private fun GlanceModifier.roundedSquare(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(Spacing.md) else this

/**
 * The words NetPosition puts above the figure, kept short enough for a strip to hold one.
 *
 * The screen has a line to itself and says "You're even with everyone"; here the label shares
 * 164dp with a figure that never gives way, so the even case is written as a label like the rest.
 *
 * A currency that nets out is not reported at all, so no totals with people still in the ledger
 * means every currency cancelled while the individual balances did not.
 */
private fun overallLabel(persons: List<PersonWithBalance>, totals: List<CurrencyTotal>): String =
    when {
        totals.size == 1 && totals.single().amountMinor > 0 -> "Owed to you"
        totals.size == 1 -> "You owe"
        totals.isNotEmpty() -> "Overall"
        persons.isEmpty() -> "Even with everyone"
        else -> "Even overall"
    }

/**
 * The person's own colour, washed back so the initials stay legible over it.
 *
 * A colour the ledger cannot parse falls back to the theme's accent, which is what PfpView does
 * on screen. Picked per mode rather than once, so the day and night palettes each get their own.
 */
private fun avatarTint(person: PersonWithBalance, palette: WidgetPalette) =
    // Through avatarInk, exactly as the app does it. Reading the stored hex straight gave every
    // avatar the same Material green on a home screen while the ledger behind it had already
    // re-lit them per theme, so the widget looked like a different app's.
    palette.of { theme ->
        avatarInk(person.person.pfpColor, theme.isDark).copy(alpha = AVATAR_WASH * 2.5f)
    }

/**
 * The type the widget can actually set.
 *
 * A RemoteViews text can only name a font family the system already has, so Anek and Figtree are
 * out of reach here and nothing bundled in res/font will load. The sizes still come from the
 * app's own scale, and only Medium and Bold are used, so the figures stay the one bold thing.
 */
private fun labelStyle(palette: WidgetPalette) = TextStyle(
    color = palette.of { it.textSecondary },
    fontSize = MeraPaisaType.label.fontSize,
    fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.SansSerif
)

private fun nameStyle(palette: WidgetPalette) = TextStyle(
    color = palette.of { it.textPrimary },
    fontSize = MeraPaisaType.label.fontSize,
    fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.SansSerif
)

private fun amountStyle(palette: WidgetPalette, amountMinor: Long) = TextStyle(
    color = palette.ink(amountMinor),
    fontSize = MeraPaisaType.amountSmall.fontSize,
    fontWeight = FontWeight.Bold,
    fontFamily = FontFamily.SansSerif
)

private fun initialsStyle(palette: WidgetPalette) = TextStyle(
    color = palette.of { it.textPrimary },
    fontSize = MeraPaisaType.label.fontSize,
    fontWeight = FontWeight.Bold,
    fontFamily = FontFamily.SansSerif
)

class DebtWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DebtWidget()
}

/**
 * Pushes the widget when the ledger changes, replacing the hand-rolled
 * ACTION_APPWIDGET_UPDATE broadcast the ViewModel used to send from an Activity Context.
 */
class WidgetLedgerNotifier(private val appContext: Context) : LedgerChangeNotifier {
    override suspend fun onLedgerChanged() {
        DebtWidget().updateAll(appContext)
    }
}
