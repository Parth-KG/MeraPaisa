package com.kg.merapaisa.ui.walkthrough

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.netTotalsByCurrency
import com.kg.merapaisa.data.sidesByCurrency
import com.kg.merapaisa.ui.FootActions
import com.kg.merapaisa.ui.LedgerTabs
import com.kg.merapaisa.ui.NetPosition
import com.kg.merapaisa.ui.NumPad
import com.kg.merapaisa.ui.PersonMenuItems
import com.kg.merapaisa.ui.PersonRow
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SectionHeading
import com.kg.merapaisa.ui.SplitParticipant
import com.kg.merapaisa.ui.SplitPrimaryButton
import com.kg.merapaisa.ui.SplitShareRow
import com.kg.merapaisa.ui.SplitStepHeading
import com.kg.merapaisa.ui.Tab
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.groups.MemberBalanceRow
import com.kg.merapaisa.ui.groups.MemberNames
import com.kg.merapaisa.ui.groups.MoveToLedgerButton
import com.kg.merapaisa.ui.groups.TransferRow
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing

/*
 * The pictures on the walkthrough's pages: the app's own components, drawn with the sample ledger
 * at the screen's full width and the phone's own text size, so each one is what that part of the
 * app really looks like in the theme in use. Only the parts inside [Named] are looked for by the
 * arrows; the same component drawn outside it names nothing.
 */

/** Lets the arrows find the parts named inside [content]. */
@Composable
private fun Named(targets: TourTargets, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalTourTargets provides targets, content = content)

@Composable
private fun SampleRow(person: PersonWithBalance, isSelected: Boolean = false) = PersonRow(
    person = person,
    isSelected = isSelected,
    onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {}, onEditClick = {},
    onSettleUp = {}, onShareSummary = {}, onShareLedger = {}, onMoveDebt = {}
)

/** The top of Balances, Asha and Bilal, and its two buttons. */
@Composable
internal fun BalancesPicture(targets: TourTargets) {
    val theme = LocalAppTheme.current
    val people = WalkthroughSamples.people
    Column(Modifier.fillMaxWidth().background(theme.background)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tabs = Modifier.weight(1f)
            Named(targets) { LedgerTabs(selected = Tab.Active, onSelect = {}, modifier = tabs) }
            IconButton(onClick = {}, modifier = Modifier.padding(end = Spacing.xs)) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = theme.textSecondary)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        NetPosition(
            totals = netTotalsByCurrency(people),
            sides = sidesByCurrency(people),
            onToggle = {}
        )
        Spacer(Modifier.height(Spacing.md))
        SampleRow(WalkthroughSamples.asha)
        RowDivider()
        Named(targets) { SampleRow(WalkthroughSamples.bilal) }
        Named(targets) {
            FootActions {
                PrimaryAction("Add a person", enabled = true, modifier = Modifier.tourTarget(TourId.AddPerson)) {}
                SecondaryAction("Split an expense", enabled = true) {}
            }
        }
    }
}

/** The keypad open for Asha, an amount typed and a note being written. */
@Composable
internal fun KeypadPicture(targets: TourTargets) {
    Named(targets) {
        NumPad(
            person = WalkthroughSamples.asha,
            input = WalkthroughSamples.TYPED,
            onKey = {},
            onSettleUp = {},
            onAdd = {},
            onSubtract = {},
            note = WalkthroughSamples.NOTE,
            onNoteChange = {},
            showNote = true,
            onToggleNote = {}
        )
    }
}

/** Asha's row with her menu open under it, as a long press leaves it. */
@Composable
internal fun PersonPicture(targets: TourTargets) {
    val theme = LocalAppTheme.current
    Column(Modifier.fillMaxWidth().background(theme.background)) {
        Named(targets) {
            SampleRow(WalkthroughSamples.asha, isSelected = false)
            Box(Modifier.fillMaxWidth().padding(end = Spacing.sm), contentAlignment = Alignment.TopEnd) {
                Surface(
                    modifier = Modifier.tourTarget(TourId.PersonMenu),
                    shape = Shapes.medium,
                    color = MenuDefaults.containerColor,
                    tonalElevation = MenuDefaults.TonalElevation,
                    shadowElevation = MenuDefaults.ShadowElevation
                ) {
                    Column(Modifier.width(IntrinsicSize.Max).padding(vertical = Spacing.sm)) {
                        PersonMenuItems(
                            person = WalkthroughSamples.asha,
                            onDismiss = {}, onSettleUp = {}, onSendReminder = {}, onShareSummary = {},
                            onShareLedger = {}, onMoveDebt = {}, onEditClick = {}, onDelete = {},
                            onShowQr = {}, qrOnlyInRupees = false
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

/** ₹1,800 shared three ways, Asha's share locked. */
@Composable
internal fun SplitPicture(targets: TourTargets) {
    val theme = LocalAppTheme.current
    val total = WalkthroughSamples.SPLIT_TOTAL
    val each = total / 3
    Column(Modifier.fillMaxWidth().background(theme.background)) {
        SplitStepHeading(
            title = "Adjust the shares",
            supporting = "Splitting ${amountString(total, "INR")}"
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Named(targets) { Share(id = 0, name = "You", amount = each, locked = false) }
            Share(id = 1, name = "Asha", amount = each, locked = true)
            Share(id = 2, name = "Bilal", amount = each, locked = false)
        }
        Named(targets) {
            SplitPrimaryButton("Save split", enabled = true, modifier = Modifier.tourTarget(TourId.SaveSplit)) {}
        }
    }
}

@Composable
private fun Share(id: Long, name: String, amount: Long, locked: Boolean) = SplitShareRow(
    participant = SplitParticipant(id = id, name = name, currency = "INR"),
    sourceCurrency = "INR",
    amountInSourceMinor = amount,
    convertedAmountMinor = null,
    locked = locked,
    animateShare = false,
    onAmountChange = {},
    onToggleLock = {}
)

/** The top of "Goa, October": who pays whom, and where you stand. */
@Composable
internal fun GroupPicture(targets: TourTargets) {
    val theme = LocalAppTheme.current
    val names = MemberNames(WalkthroughSamples.members)
    val group = WalkthroughSamples.group
    val (first, second) = WalkthroughSamples.transfers
    Column(Modifier.fillMaxWidth().background(theme.background)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
            Text(group.name, style = MeraPaisaType.screenTitle, color = theme.textPrimary)
            Text(
                "${WalkthroughSamples.members.size} members, ${WalkthroughSamples.EXPENSES} expenses",
                style = MeraPaisaType.label,
                color = theme.textSecondary
            )
        }
        SectionHeading("Who pays whom")
        Named(targets) {
            Box(Modifier.tourTarget(TourId.WhoPaysWhom)) {
                TransferRow(names.pays(first.fromPersonId, first.toPersonId), first.amountMinor, group.currency)
            }
        }
        RowDivider(TextRowInset)
        TransferRow(names.pays(second.fromPersonId, second.toPersonId), second.amountMinor, group.currency)
        SectionHeading("Where everyone stands")
        MemberBalanceRow(name = "You", isYou = true, amountMinor = WalkthroughSamples.YOUR_POSITION, currency = group.currency)
        Named(targets) {
            MoveToLedgerButton(onClick = {}, modifier = Modifier.tourTarget(TourId.MoveToLedger))
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}

/** The group's two buttons, which sit at the foot of its screen under everything else. */
@Composable
internal fun GroupFootPicture(targets: TourTargets) {
    val theme = LocalAppTheme.current
    Column(Modifier.fillMaxWidth().background(theme.background)) {
        Named(targets) {
            FootActions {
                PrimaryAction("Add an expense", enabled = true, modifier = Modifier.tourTarget(TourId.AddExpense)) {}
                SecondaryAction("Settle up", enabled = true) {}
            }
        }
    }
}
