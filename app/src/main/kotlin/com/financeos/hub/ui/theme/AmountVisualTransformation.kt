package com.financeos.hub.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** The non-breaking space used as the thousands separator (must match [FosFormatter]). */
private const val NBSP = ' '

/**
 * Shows an amount field grouped ("1 000") while the field's stored text stays raw ("1000").
 *
 * This MUST be a VisualTransformation rather than formatting the `value` passed to the text field.
 * `BasicTextField(value: String, …)` keeps the caret offset in its own state and re-applies it to
 * whatever text it is handed; if that text is longer than what the user typed, the caret ends up in
 * the wrong place. Concretely, with `value = groupAmountInput(state)` typing `12345` produced
 * `12354` — after the separator appeared at `1 234`, the next digit was inserted one position early.
 *
 * A VisualTransformation avoids that by construction: the field keeps the raw text, and the mapping
 * below translates caret positions across the inserted separators.
 *
 * Requires the stored text to contain only the characters that also appear in the display (digits,
 * one `,`, an optional leading `-`) — use [FosFormatter.sanitizeAmountInput] in `onValueChange`, so
 * the transformation only ever ADDS separators and the mapping stays exact.
 */
object AmountVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw       = text.text
        val formatted = FosFormatter.groupAmountInput(raw)

        val mapping = object : OffsetMapping {
            /** raw index → display index: skip over every separator we inserted. */
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                var seen = 0
                formatted.forEachIndexed { i, c ->
                    if (c != NBSP) {
                        if (seen == offset) return i
                        seen++
                    }
                }
                return formatted.length
            }

            /** display index → raw index: count only the characters that exist in the raw text. */
            override fun transformedToOriginal(offset: Int): Int {
                var seen = 0
                for (i in 0 until offset.coerceAtMost(formatted.length)) {
                    if (formatted[i] != NBSP) seen++
                }
                return seen.coerceAtMost(raw.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
