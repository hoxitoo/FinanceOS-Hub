package com.financeos.hub.core.pdf

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Parses bank statement PDF text into raw transaction records.
 *
 * Handles the most common Russian bank PDF formats (Sberbank, T-Bank, Alfa-Bank, VTB, etc.)
 * which all share a tabular layout: Date | Description | Amount | Balance.
 *
 * Strategy:
 *  1. Process line-by-line; skip lines without a DD.MM.YYYY date.
 *  2. Detect all candidate amounts on the same line via a signed-amount regex.
 *  3. Treat the first negative amount as EXPENSE; the first positive (no-negative present) as INCOME.
 *  4. Build a dedup key from date + amount + merchant to prevent double-import.
 */
object PdfTransactionParser {

    private val zone     = ZoneId.systemDefault()
    private val dmyFmt   = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // DD.MM.YYYY (also accepts DD/MM/YYYY — normalised before parsing)
    private val DATE_RE  = Regex("""(\d{2}[./]\d{2}[./]\d{4})""")

    // Signed amounts: handles minus/en-dash, NBSP/NNBS thousands separators, ₽/RUB suffix
    // Captures the full raw token so we can detect the sign.
    private val AMOUNT_RE = Regex(
        """([−\-]\s*\d{1,3}(?:[\s  ]\d{3})*[.,]\d{2}|""" +
        """\d{1,3}(?:[\s  ]\d{3})*[.,]\d{2})\s*(?:₽|RUB|руб\.?)?""",
        setOf(RegexOption.IGNORE_CASE),
    )

    data class RawPdfTx(
        val timestampMillis : Long,
        val type            : TransactionType,
        val amountKopecks   : Long,
        val merchant        : String?,
        val balanceKopecks  : Long?,
        val dedupKey        : String,
    )

    fun parse(pdfText: String): List<RawPdfTx> {
        val results = mutableListOf<RawPdfTx>()

        for (rawLine in pdfText.lines()) {
            val line = rawLine.trim().replace('\t', ' ')
            if (line.isBlank()) continue

            val dateMatch = DATE_RE.find(line) ?: continue
            val timestamp = parseDate(dateMatch.groupValues[1]) ?: continue

            val amountMatches = AMOUNT_RE.findAll(line).toList()
            if (amountMatches.isEmpty()) continue

            // Signed detection: a match is negative if the raw text starts with − or -
            fun isNeg(m: MatchResult) =
                m.groupValues[1].trimStart().let { it.startsWith('-') || it.startsWith('−') }

            // Primary: first negative amount → EXPENSE; else first positive amount → INCOME
            val primaryMatch = amountMatches.firstOrNull { isNeg(it) }
                ?: amountMatches.first()

            val kopecks = AmountParser.toKopecks(primaryMatch.groupValues[1])
            if (kopecks <= 0L) continue

            val type = if (isNeg(primaryMatch)) TransactionType.EXPENSE else TransactionType.INCOME

            // Balance: last distinct amount (often the running-balance column)
            val balMatch = amountMatches.lastOrNull { it.range != primaryMatch.range }
            val balKopecks = balMatch?.let { AmountParser.toKopecks(it.groupValues[1]) }
                ?.takeIf { it > 0L }

            // Merchant: text between date end and first amount start
            val textAfterDate = line.substring(dateMatch.range.last + 1).trimStart()
            val firstAmtInSuffix = AMOUNT_RE.find(textAfterDate)
            val merchantRaw = (firstAmtInSuffix?.let { textAfterDate.substring(0, it.range.first) }
                ?: textAfterDate)
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
                .trimEnd(',', '.', ':', '/')
                .takeIf { it.isNotBlank() && it.length <= 80 }

            val dedupKey = "pdf_${timestamp}_${kopecks}_${merchantRaw.hashCode()}"

            results += RawPdfTx(
                timestampMillis = timestamp,
                type            = type,
                amountKopecks   = kopecks,
                merchant        = merchantRaw,
                balanceKopecks  = balKopecks,
                dedupKey        = dedupKey,
            )
        }

        return results
    }

    private fun parseDate(raw: String): Long? = try {
        val s = raw.replace('/', '.')
        LocalDate.parse(s, dmyFmt)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) { null }
}
