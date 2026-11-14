package com.kg.merapaisa.ui.pay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.QrImage
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * A QR for someone who owes you to scan and pay you, face to face: a `upi://pay` link to your UPI
 * ID for the amount they owe. Their UPI app opens with it filled in and asks for their PIN.
 *
 * Nothing is recorded from the QR itself. "Record" records the full amount now, with the UPI mark;
 * "Not yet" only closes the sheet, recording nothing and leaving nothing to ask about. A part
 * payment goes through Settle up, without the mark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayQrSheet(
    personName: String,
    amountMinor: Long,
    link: String,
    myUpiId: String,
    onNotYet: () -> Unit,
    onPaid: () -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    val amount = amountString(amountMinor, "INR")

    SheetFrame(onDismissRequest = onNotYet, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
        ) {
            Text(
                "$personName pays you $amount",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "$personName scans this with their UPI app, which opens with $amount to $myUpiId " +
                    "filled in. Nothing is recorded until you say they paid.",
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QrImage(text = link, description = "QR code for $personName to pay you $amount")
            }
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Not yet", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onNotYet() }
            }
            PrimaryAction("Record $amount", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onPaid() }
            }
        }
    }
}
