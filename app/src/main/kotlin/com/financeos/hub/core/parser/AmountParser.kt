package com.financeos.hub.core.parser

/**
 * Robust amount parser for Russian bank SMS/push text.
 *
 * Handles:
 *  - regular spaces, non-breaking space (U+00A0) and narrow NBSP (U+202F) as thousand separators
 *  - both comma and dot decimal separators
 *  - malformed input (returns 0 instead of throwing, so a single bad SMS never aborts an import)
 *  - absurdly large values (clamped to a non-negative Long)
 *
 * Always returns a non-negative amount in kopecks.
 */
object AmountParser {
    private val WHITESPACE = Regex("[\\s\\u00A0\\u202F]")

    fun toKopecks(raw: String): Long {
        val cleaned = raw.trim()
            .replace(WHITESPACE, "")
            .replace(',', '.')
        val value = cleaned.toDoubleOrNull() ?: return 0L
        if (value.isNaN() || value.isInfinite() || value <= 0.0) return 0L
        // Double.toLong() already saturates to Long.MAX_VALUE for huge values.
        return (value * 100.0).toLong().coerceAtLeast(0L)
    }
}
