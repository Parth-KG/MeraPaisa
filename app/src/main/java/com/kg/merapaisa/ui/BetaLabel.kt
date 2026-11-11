package com.kg.merapaisa.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/**
 * A label for update links, which are still in beta: [words], then "(beta)" set in italics.
 *
 * Every place that names update links goes through this, so the six labels cannot drift apart:
 * the person menu, Settings, the share sheet's title, and the record and check screens' titles.
 * Only the tag is slanted; the words before it read as they always did. The text is the same
 * characters as before, so TalkBack says exactly what it said. The bundled fonts have no italic
 * cut, so Android slants them itself.
 */
fun betaLabel(words: String): AnnotatedString = buildAnnotatedString {
    append(words)
    append(" ")
    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("(beta)") }
}
