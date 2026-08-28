package com.financeos.hub.ui.theme

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object FosFormatter {
    private val RU = Locale("ru", "RU")
    // Non-breaking space as thousands separator
    private val NBSP = " "

    private val monthYearFmt  = DateTimeFormatter.ofPattern("LLLL yyyy", RU)
    private val dayLabelFmt   = DateTimeFormatter.ofPattern("d MMMM", RU)
    private val fullDateFmt   = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
    private val shortTimeFmt  = DateTimeFormatter.ofPattern("HH:mm", RU)

    /** Converts Long (kopecks) → display string, e.g. 123456 → "1 234,56 ₽" */
    fun amount(kopecks: Long, currency: String = "₽"): String {
        val rubles = kopecks / 100L
        val cents  = Math.abs(kopecks % 100L)
        val sign   = if (kopecks < 0) "−" else ""
        return "$sign${formatInteger(Math.abs(rubles))},${cents.toString().padStart(2, '0')}$NBSP$currency"
    }

    /** "+1 234,56 ₽" for income, "−1 234,56 ₽" for expense */
    fun signedAmount(kopecks: Long, currency: String = "₽"): String {
        val prefix = if (kopecks >= 0) "+" else ""
        return "$prefix${amount(kopecks, currency)}"
    }

    /** 0.75 → "75%" */
    fun percent(value: Double, decimals: Int = 0): String {
        val pct = value * 100.0
        return if (decimals == 0) "${pct.toLong()}%" else String.format(RU, "%.${decimals}f%%", pct)
    }

    /** Epoch millis → "июнь 2025" */
    fun monthYear(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return date.format(monthYearFmt).replaceFirstChar { it.titlecase(RU) }
    }

    /** LocalDate → "Июнь 2025". Same wording as the epoch-millis overload, no timezone hop. */
    fun monthYear(date: LocalDate): String =
        date.format(monthYearFmt).replaceFirstChar { it.titlecase(RU) }

    /** Current month name, capitalised — e.g. "Июль". Used to label "this month" metric blocks. */
    fun currentMonthName(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("LLLL", RU))
            .replaceFirstChar { it.titlecase(RU) }

    /** Epoch millis → "18 июня" or "Сегодня" / "Вчера" */
    fun dayLabel(epochMillis: Long): String {
        val date  = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today            -> "Сегодня"
            today.minusDays(1) -> "Вчера"
            else             -> date.format(dayLabelFmt)
        }
    }

    /**
     * Like [dayLabel] but always shows the year for non-relative dates: "18 июня 2025".
     * Used in transaction history, where imported statements can span past years.
     */
    fun dayLabelYear(epochMillis: Long): String {
        val date  = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today              -> "Сегодня"
            today.minusDays(1) -> "Вчера"
            else               -> date.format(fullDateFmt)
        }
    }

    /** Epoch millis → "18 июня 2025, 14:35" */
    fun fullDateTime(epochMillis: Long): String {
        val ldt = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        return "${ldt.toLocalDate().format(fullDateFmt)}, ${ldt.format(shortTimeFmt)}"
    }

    /** Compact amount for chips/labels: "1 234 ₽" (no kopecks if .00) */
    fun compact(kopecks: Long, currency: String = "₽"): String {
        val rubles = kopecks / 100L
        val cents  = Math.abs(kopecks % 100L)
        val sign   = if (kopecks < 0) "−" else ""
        return if (cents == 0L) {
            "$sign${formatInteger(Math.abs(rubles))}$NBSP$currency"
        } else {
            amount(kopecks, currency)
        }
    }

    /** Epoch millis → "18 июня 2026" (no relative "Сегодня") — for deadlines. */
    fun date(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return date.format(fullDateFmt)
    }

    /** LocalDate → "18 июня 2026". Для сроков, которые считаются в датах, а не в метках времени. */
    fun date(date: LocalDate): String = date.format(fullDateFmt)

    /** Maps an ISO currency code to a display symbol. */
    fun currencySymbol(code: String): String = when (code.uppercase()) {
        "USD" -> "$"
        "EUR" -> "€"
        "KGS" -> "сом"
        else  -> "₽"
    }

    /** Groups a raw digit string with NBSP thousands separators: "200000" → "200 000". */
    fun groupDigits(digits: String): String {
        val clean = digits.filter { it.isDigit() }.trimStart('0').ifEmpty { if (digits.any { it == '0' }) "0" else "" }
        if (clean.isEmpty()) return ""
        return formatInteger(clean.toLong())
    }

    /**
     * Kopecks → the raw string an amount INPUT should start from: `141759` → `"1417,59"`,
     * `141700` → `"1417"`. Integer division alone (`kopecks / 100`) silently dropped the kopecks,
     * so editing a balance of 1 417,59 ₽ used to reset it to 1 417 ₽ the moment the field opened.
     */
    fun amountInput(kopecks: Long): String {
        val rubles = Math.abs(kopecks) / 100L
        val cents  = Math.abs(kopecks % 100L)
        val sign   = if (kopecks < 0) "-" else ""
        return if (cents == 0L) "$sign$rubles"
               else "$sign$rubles,${cents.toString().padStart(2, '0')}"
    }

    /**
     * Normalises what an amount field stores: digits, at most ONE decimal separator, at most two
     * decimals, an optional leading `-`, and no leading zeros.
     *
     * Sanitising in `onValueChange` (rather than only when formatting for display) is what keeps the
     * stored text and the displayed text the same characters — see [AmountVisualTransformation],
     * whose caret mapping depends on that. It also closes two silent-data bugs: a second separator
     * used to make the value unparseable (saving 0 ₽ while the field still showed a number), and a
     * third decimal was hidden by the display yet still counted when saving.
     */
    fun sanitizeAmountInput(raw: String, allowNegative: Boolean = false): String {
        val negative = allowNegative && raw.trimStart().startsWith("-")
        val sb = StringBuilder()
        var sepSeen = false
        var decimals = 0
        for (ch in raw) {
            when {
                ch.isDigit() && !sepSeen -> sb.append(ch)
                ch.isDigit()             -> if (decimals < 2) { sb.append(ch); decimals++ }
                (ch == ',' || ch == '.') && !sepSeen && sb.isNotEmpty() -> {
                    sepSeen = true; sb.append(',')
                }
                else -> Unit   // drop everything else, including a stray '-' mid-string
            }
        }
        var out = sb.toString()
        // Strip leading zeros ("007" → "7") but keep a lone "0" and "0,50".
        val sepIdx = out.indexOf(',')
        val intPart = if (sepIdx >= 0) out.substring(0, sepIdx) else out
        val rest    = if (sepIdx >= 0) out.substring(sepIdx) else ""
        val trimmed = intPart.trimStart('0').ifEmpty { if (intPart.isEmpty()) "" else "0" }
        out = trimmed + rest
        return if (negative && out.isNotEmpty()) "-$out" else out
    }

    /**
     * Groups the integer part of a money INPUT for display while the user types, keeping any
     * decimal part intact: `"1417,59"` → `"1 417,59"`. Unlike [groupDigits] this tolerates a
     * decimal separator, so amount fields show thousands spaced instead of a run-on `1000`.
     */
    fun groupAmountInput(raw: String): String {
        if (raw.isBlank()) return ""
        val negative = raw.trimStart().startsWith("-")
        val sepIndex = raw.indexOfFirst { it == ',' || it == '.' }
        val intRaw   = (if (sepIndex >= 0) raw.substring(0, sepIndex) else raw).filter { it.isDigit() }
        val decRaw   = if (sepIndex >= 0) raw.substring(sepIndex + 1).filter { it.isDigit() }.take(2) else null

        val intPart = if (intRaw.isEmpty()) "" else formatInteger(intRaw.trimStart('0').ifEmpty { "0" }.toLong())
        val sign    = if (negative) "-" else ""
        return when {
            decRaw == null -> "$sign$intPart"
            else           -> "$sign$intPart,$decRaw"
        }
    }

    /**
     * Kopecks → the RAW string a money field holds: ungrouped, comma for kopecks, nothing else.
     *
     * The inverse of [parseAmountInput], and it must stay raw. Feeding a field the *formatted*
     * value (with NBSP groups or a currency sign) is what desynchronises the caret — grouping is
     * [AmountVisualTransformation]'s job, and it runs on top of this string, not instead of it.
     * Whole rubles come back without ",00": a prefilled "10 000,00" invites the user to delete
     * three characters before typing.
     */
    fun plainAmountInput(kopecks: Long): String {
        val rubles = kopecks / 100L
        val cents  = Math.abs(kopecks % 100L)
        return if (cents == 0L) rubles.toString() else "$rubles,%02d".format(cents)
    }

    /** Parses a (possibly grouped) money input back to kopecks. Rounds, never truncates. */
    fun parseAmountInput(raw: String): Long? {
        val cleaned = raw.replace(NBSP, "").replace(" ", "").replace(',', '.')
        val value   = cleaned.toDoubleOrNull() ?: return null
        // Math.round, not toLong(): 1417.59 * 100 is 141758.999… in binary floating point, which
        // toLong() would truncate to 141758 — losing a kopeck on every edit.
        return Math.round(value * 100.0)
    }

    private fun formatInteger(n: Long): String {
        if (n < 1000L) return n.toString()
        val s = n.toString()
        val sb = StringBuilder()
        val rem = s.length % 3
        s.forEachIndexed { i, c ->
            if (i > 0 && (i - rem + 3) % 3 == 0) sb.append(NBSP)
            sb.append(c)
        }
        return sb.toString()
    }
}
