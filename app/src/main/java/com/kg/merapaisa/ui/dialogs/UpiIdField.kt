package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.isUpiId
import com.kg.merapaisa.data.readPastedUpi
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing

/**
 * What is wrong with a UPI ID as typed, in a sentence, or null when it is fine (or left empty: the
 * field is optional). It catches typos, not a wrong but valid ID.
 */
internal fun upiIdProblem(value: String): String? = when {
    value.isBlank() -> null
    isUpiId(value) -> null
    else -> "That doesn't look like a UPI ID. It needs a name, an @ and the bank's handle, like asha@okaxis."
}

/**
 * A person's UPI ID, optional, in Add and Edit person.
 *
 * Typed, or pasted: a pasted `upi://pay` link (from a QR or a message) fills in only the ID, and
 * the name too if none has been typed yet. Its amount and note are ignored, see [readPastedUpi].
 * UPI is rupees only, so for anyone kept in another currency the field gives way to a line saying so.
 */
@Composable
internal fun UpiIdField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
    /** A name a pasted link carried, offered to the Name field when that is still empty. */
    onPastedName: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    SheetHeading("UPI ID")
    if (currency != "INR") {
        Text(
            "UPI is for rupees only, so paying by UPI needs this person kept in INR.",
            style = MeraPaisaType.body,
            color = theme.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        return
    }
    val problem = upiIdProblem(value)
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            val pasted = if (typed.trimStart().startsWith("upi://", ignoreCase = true)) readPastedUpi(typed) else null
            if (pasted != null) {
                onValueChange(pasted.id)
                pasted.name?.let(onPastedName)
            } else {
                onValueChange(typed)
            }
        },
        label = { Text("Their UPI ID (optional)") },
        placeholder = { Text("name@bank") },
        isError = problem != null,
        supportingText = {
            Text(problem ?: "Only used to open your UPI app when you pay them. Paste a UPI link to fill it in.")
        },
        textStyle = MeraPaisaType.body,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        shape = Shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
    )
}

/**
 * The UPI ID to store once the sheet is saved. Blank clears it. For anyone not kept in INR the field
 * is hidden, so whatever they had is kept as it was rather than silently dropped.
 */
internal fun savedUpiIdFor(typed: String, currency: String, previous: String?): String? =
    if (currency != "INR") previous
    else typed.trim().takeIf { it.isNotEmpty() && isUpiId(it) }
