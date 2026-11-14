package com.kg.merapaisa.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PaymentQuestion
import com.kg.merapaisa.data.QUESTION_REMINDER
import com.kg.merapaisa.ui.ButtonLabel
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * "Did these go through?", on opening, when payments wait to be asked about (Parth's mockup, 14 Nov
 * 2026): a reminder sent with a pay link, or a Pay never answered because the app closed while the
 * UPI app was open. Nothing is recorded without a tap on "Record".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentQuestionsSheet(
    questions: List<PaymentQuestion>,
    /** The person's name for a question, or null if they have gone. */
    nameOf: (Long) -> String?,
    onRecord: (PaymentQuestion) -> Unit,
    onNotYet: (PaymentQuestion) -> Unit,
    onDifferentAmount: (PaymentQuestion) -> Unit,
    onStopAsking: (PaymentQuestion) -> Unit,
    onAskLater: () -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()

    SheetFrame(onDismissRequest = onAskLater, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
        ) {
            Text(
                "Did these go through?",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Nothing is recorded until you say so.",
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.md))

            questions.forEachIndexed { index, question ->
                val name = nameOf(question.personId) ?: return@forEachIndexed
                if (index > 0) RowDivider(TextRowInset)
                Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                    Text(questionLine(question, name), style = MeraPaisaType.bodyStrong, color = theme.textPrimary)
                    Text(questionWhen(question), style = MeraPaisaType.label, color = theme.textSecondary)
                    Spacer(Modifier.height(Spacing.sm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Button(
                            onClick = { onRecord(question) },
                            shape = Shapes.small,
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onAccent
                            )
                        ) { ButtonLabel("Record ${amountString(abs(question.amountMinor), "INR")}") }
                        QuestionAction("Not yet") { onNotYet(question) }
                        QuestionAction("Paid a different amount") { onDifferentAmount(question) }
                        QuestionAction("Stop asking") { onStopAsking(question) }
                    }
                }
            }
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Ask me later", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onAskLater() }
            }
        }
    }
}

@Composable
private fun QuestionAction(label: String, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        ButtonLabel(label, color = theme.primary)
    }
}

/** "Did Asha pay you ₹500?" for a reminder; "Did your ₹40 to Bilal go through?" for a Pay. */
internal fun questionLine(question: PaymentQuestion, name: String): String {
    val amount = amountString(abs(question.amountMinor), "INR")
    return if (question.kind == QUESTION_REMINDER) "Did $name pay you $amount?" else "Did your $amount to $name go through?"
}

/** When the reminder went, or when the UPI app was opened. */
internal fun questionWhen(question: PaymentQuestion, locale: Locale = Locale.getDefault()): String {
    val day = SimpleDateFormat("d MMM", locale).format(Date(question.createdAt))
    return if (question.kind == QUESTION_REMINDER) "Reminder sent $day" else "UPI app opened $day"
}
