package com.kg.merapaisa.ui.pay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.amountToRecord
import com.kg.merapaisa.data.overpaidBy
import com.kg.merapaisa.data.parseAmountToMinor
import com.kg.merapaisa.ui.LabelAndAmount
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.backup.ModeOption
import com.kg.merapaisa.ui.format.PlainAmountText
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Before a UPI app opens: who, how much, and their full UPI ID, so nothing is sent on a guess.
 *
 * The amount starts at everything you owe and can be changed (to pay part of it). Paying more than
 * you owe is allowed, with a warning and a choice of what to record if it goes through (Parth, 12 Nov
 * 2026). Nothing is recorded here: "Did it go through?" asks once you are back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaySheet(
    personName: String,
    upiId: String,
    /** What you owe them, positive. */
    owedMinor: Long,
    amountText: String,
    onAmountChange: (String) -> Unit,
    recordOnlyWhatYouOwed: Boolean,
    onRecordOnlyWhatYouOwedChange: (Boolean) -> Unit,
    noUpiApp: Boolean,
    onDismiss: () -> Unit,
    /** The amount to send, and what to record if it goes through. */
    onPay: (sentMinor: Long, recordMinor: Long) -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    val sentMinor = parseAmountToMinor(amountText)?.takeIf { it > 0 }
    val extra = sentMinor?.let { overpaidBy(owedMinor, it) } ?: 0L

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
        ) {
            Text(
                "Pay $personName by UPI",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            LabelAndAmount(
                label = { Text("You owe $personName", style = MeraPaisaType.body, color = theme.textSecondary) },
                amount = { PlainAmountText(owedMinor, "INR") },
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Their UPI ID",
                style = MeraPaisaType.label,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Text(
                upiId,
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )

            Spacer(Modifier.height(Spacing.lg))
            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                label = { Text("Amount to pay (₹)") },
                isError = amountText.isNotBlank() && sentMinor == null,
                supportingText = if (amountText.isNotBlank() && sentMinor == null) {
                    { Text("Type an amount in rupees, like 340 or 340.50.") }
                } else null,
                textStyle = MeraPaisaType.body,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            if (sentMinor != null && extra > 0) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "That's ${amountString(extra, "INR")} more than you owe $personName.",
                    style = MeraPaisaType.body,
                    color = theme.negative,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
                ModeOption(
                    selected = !recordOnlyWhatYouOwed,
                    title = "Record all ${amountString(sentMinor, "INR")}",
                    subtitle = "$personName will then owe you the extra ${amountString(extra, "INR")}.",
                    onClick = { onRecordOnlyWhatYouOwedChange(false) }
                )
                ModeOption(
                    selected = recordOnlyWhatYouOwed,
                    title = "Record only the ${amountString(owedMinor, "INR")} you owe",
                    subtitle = "The extra stays out of the ledger, as a gift or a tip.",
                    onClick = { onRecordOnlyWhatYouOwedChange(true) }
                )
                Text(
                    "Or change the amount above.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Your UPI app opens with this filled in. Check the name it shows before you enter " +
                    "your PIN. Nothing is recorded until you say it went through.",
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            if (noUpiApp) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "No UPI app on this phone. Install one, then try again.",
                    style = MeraPaisaType.body,
                    color = theme.negative,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Don't pay", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            PrimaryAction("Open UPI app", enabled = sentMinor != null) {
                val sent = sentMinor ?: return@PrimaryAction
                onPay(sent, amountToRecord(owedMinor, sent, recordOnlyWhatYouOwed))
            }
        }
    }
}
