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

    /** Groups a raw digit string with NBSP thousands separators: "200000" → "200 000". */
    fun groupDigits(digits: String): String {
        val clean = digits.filter { it.isDigit() }.trimStart('0').ifEmpty { if (digits.any { it == '0' }) "0" else "" }
        if (clean.isEmpty()) return ""
        return formatInteger(clean.toLong())
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
