package com.kg.merapaisa.ui.walkthrough

import androidx.compose.runtime.Composable

/**
 * One line on a walkthrough page, with an arrow to the part it names.
 *
 * The words always name the part as well, so nothing is said by the arrow alone. [spoken] is for
 * TalkBack where the words lean on a symbol it cannot say, such as "−".
 */
internal data class Callout(val target: TourId, val text: String, val spoken: String? = null)

/**
 * The callouts above or below a picture: one across the width, or one over each half, each over
 * the half its target is in so their arrows never cross.
 */
internal data class Band(val start: Callout? = null, val end: Callout? = null, val wide: Callout? = null) {
    val callouts: List<Callout> get() = listOfNotNull(wide, start, end)
}

internal class TourPage(
    val title: String,
    val paragraphs: List<String>,
    val above: Band? = null,
    val below: Band? = null,
    /** What TalkBack hears for the picture, which is otherwise one silent image. */
    val pictureDescription: String? = null,
    val picture: (@Composable (TourTargets) -> Unit)? = null
) {
    val callouts: List<Callout> get() = listOfNotNull(above, below).flatMap { it.callouts }
}

/** The pages before Set up, in order. Every word follows the app's copy rules. */
internal val TourPages: List<TourPage> = listOf(
    TourPage(
        title = "Welcome to Mera Paisa",
        paragraphs = listOf(
            "A record of who owes whom, one person at a time, each in their own currency.",
            "There's no account and no server. Your ledger is kept on this phone, and recording " +
                "works offline.",
            "The next pages show where everything is, drawn with a sample ledger."
        )
    ),
    TourPage(
        title = "Your balances",
        paragraphs = listOf(
            "Everyone you add has a line here. The total at the top is what everyone owes you, " +
                "less what you owe."
        ),
        above = Band(wide = Callout(TourId.EvenTab, "Even: everyone at exactly zero moves here, out of the way.")),
        below = Band(
            start = Callout(TourId.AddPerson, "Add a person, then tap their line to record an amount."),
            end = Callout(
                TourId.PersonFigure,
                "A − means you owe Bilal. No sign means they owe you.",
                spoken = "A minus sign means you owe Bilal. No sign means they owe you."
            )
        ),
        pictureDescription = "A sample of Balances. Asha owes you 1,200 rupees, and you owe Bilal 40 rupees.",
        picture = { BalancesPicture(it) }
    ),
    TourPage(
        title = "Recording an entry",
        paragraphs = listOf(
            "Tap someone's line and the keypad opens. Type the amount, then pick which way the " +
                "money went."
        ),
        above = Band(wide = Callout(TourId.KeypadNote, "Add a note to remember what it was for.")),
        below = Band(
            start = Callout(TourId.KeypadDirection, "Tap who paid. The entry is saved at once."),
            end = Callout(TourId.KeypadSettleUp, "Settle up records what makes you even.")
        ),
        pictureDescription = "A sample keypad for Asha, with 250 typed and the note Dinner.",
        picture = { KeypadPicture(it) }
    ),
    TourPage(
        title = "One person",
        paragraphs = listOf("Long-press anyone's line for everything you can do with them."),
        above = Band(end = Callout(TourId.PersonHistory, "Every entry with Asha. Long-press one to edit or delete it.")),
        below = Band(
            start = Callout(TourId.MenuReminder, "Send a reminder: a short message with the exact amount."),
            end = Callout(TourId.MenuShowQr, "Show QR: Asha scans it with their UPI app to pay you.")
        ),
        pictureDescription = "A sample of Asha's line with its menu open: Settle up, Send a reminder, " +
            "Show QR to pay you, and more.",
        picture = { PersonPicture(it) }
    ),
    TourPage(
        title = "Split an expense",
        paragraphs = listOf(
            "On Balances, Split an expense shares one cost between people, each in their own currency."
        ),
        above = Band(
            start = Callout(TourId.ShareFigure, "Change a share and the others adjust to keep the total."),
            end = Callout(TourId.ShareLock, "Lock a share to keep it where it is.")
        ),
        below = Band(end = Callout(TourId.SaveSplit, "Each share goes on that person's line.")),
        pictureDescription = "A sample split of 1,800 rupees three ways, 600 rupees each, with Asha's share locked.",
        picture = { SplitPicture(it) }
    ),
    TourPage(
        title = "Groups",
        paragraphs = listOf(
            "On the Groups tab, a trip or a flat keeps its own expenses, apart from Balances."
        ),
        above = Band(wide = Callout(TourId.WhoPaysWhom, "Who pays whom: the fewest payments that leave everyone even.")),
        below = Band(
            start = Callout(TourId.AddExpense, "Add an expense: who paid, and who shares it."),
            end = Callout(TourId.MoveToLedger, "Move to your ledger puts your part on Balances.")
        ),
        pictureDescription = "A sample group, Goa, October. Bilal pays you 2,500 rupees and Asha pays you 1,000 rupees.",
        picture = { GroupPicture(it) }
    )
)

/** The longest a callout may be, so two side by side still fit at double type. */
internal const val MAX_CALLOUT_CHARS = 70
