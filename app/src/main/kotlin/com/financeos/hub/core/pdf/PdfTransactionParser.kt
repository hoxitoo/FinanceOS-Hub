package com.financeos.hub.core.pdf

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Parses bank statement PDF text into raw transaction records.
 *
 * Tuned for Russian bank statements (Alfa-Bank, Sberbank, T-Bank, VTB, …) which share a
 * tabular layout:  Дата проводки | Код операции | Описание | Сумма в валюте счёта [ | Остаток ].
 *
 * Why a line-by-line parser is not enough
 * ---------------------------------------
 * PDF text extraction wraps a single statement row across several physical lines (the
 * Описание column is long). A naive "date and amount on the same line" parser therefore
 * either misses the row entirely or latches onto fragments. It also confuses the two kinds
 * of numbers a card row contains:
 *   - the **posting amount** in the account column: `-2 500,00 RUR`  (comma decimal, signed)
 *   - an **informational** inner amount: `на сумму: 40.00 RUR`        (dot decimal, unsigned)
 * and the two kinds of dates:
 *   - the **posting date**: `19.12.2025`                              (4-digit year)
 *   - an **informational** inner date: `дата совершения операции: 19.12.25` (2-digit year)
 *
 * Strategy
 * --------
 *  1. Reconstruct *logical rows*: a row begins at a line starting with `DD.MM.YYYY` and
 *     absorbs every following wrapped line until the next such date.
 *  2. Posting date  = the leading 4-digit-year date (inner 2-digit dates never match).
 *  3. Posting amount = the first **comma-decimal** amount with a currency suffix
 *     (inner `на сумму:` amounts use a dot, so they are excluded by construction).
 *  4. Sign           = a leading `-`/`−` on the posting amount → EXPENSE, else INCOME.
 *  5. Dedup key      = the unique operation code (`CRD_…`, `C…`, `B…`, `MPL…`) when present.
 */
object PdfTransactionParser {

    private val zone   = ZoneId.systemDefault()
    private val dmyFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // A logical row starts with a posting date (4-digit year) at the very start of a line.
    private val ROW_START_RE = Regex("""^\s*(\d{2}[.\-/]\d{2}[.\-/]\d{4})\b""")

    // Posting amount — account column. COMMA decimal + currency suffix uniquely identifies it,
    // separating it from inner dot-decimal "на сумму: 40.00 RUR" values.
    private val ACCOUNT_AMOUNT_RE = Regex(
        """([−\-]?)\s*(\d{1,3}(?:[\s  ]\d{3})*,\d{2})\s*(?:₽|RUB|RUR|руб\.?)""",
        RegexOption.IGNORE_CASE,
    )

    // Fallback 1: any comma-decimal amount, currency suffix optional.
    private val ANY_COMMA_AMOUNT_RE = Regex(
        """([−\-]?)\s*(\d{1,3}(?:[\s  ]\d{3})*,\d{2})""",
    )

    // Fallback 2 (dot-decimal banks): rightmost dot amount WITH a currency suffix.
    private val DOT_AMOUNT_SUFFIX_RE = Regex(
        """([−\-]?)\s*(\d{1,3}(?:[\s  ]\d{3})*\.\d{2})\s*(?:₽|RUB|RUR|руб\.?)""",
        RegexOption.IGNORE_CASE,
    )

    // Unique per-transaction operation code → ideal dedup key.
    private val OP_CODE_RE = Regex(
        """\b(CRD_[A-Z0-9]+|MPL_?[0-9A-Z]+|[CBD]\d{12,})\b""",
        RegexOption.IGNORE_CASE,
    )

    // Card rows carry the human-readable merchant after this marker.
    private val PLACE_RE      = Regex("""место совершения операции:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val MCC_RE        = Regex("""\s*MCC\s*\d+\s*""", RegexOption.IGNORE_CASE)
    private val LOC_PREFIX_RE = Regex("""^\d+i[A-Za-z]+?i""")                       // "38830849iRUPermi"
    private val CARD_OP_RE    = Regex("""Операция по карте:\s*[\d+]+,?""", RegexOption.IGNORE_CASE)
    private val INNER_SUM_RE  = Regex("""на сумму:\s*[\d\s.,]+(?:₽|RUB|RUR|руб\.?)?,?""", RegexOption.IGNORE_CASE)
    private val INNER_DATE_RE = Regex("""дата совершения операции:\s*[\d.]+,?""", RegexOption.IGNORE_CASE)
    private val LONG_REF_RE   = Regex("""\b[CBD]\d{12,}\b""")
    private val MULTISPACE_RE = Regex("""\s{2,}""")

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
        val seen    = HashSet<String>()
        for (block in splitIntoRows(pdfText)) {
            val tx = parseBlock(block) ?: continue
            if (seen.add(tx.dedupKey)) results += tx
        }
        return results
    }

    /** Groups wrapped physical lines into one logical row per posting date. */
    private fun splitIntoRows(pdfText: String): List<String> {
        val blocks  = mutableListOf<String>()
        val current = StringBuilder()
        for (rawLine in pdfText.lines()) {
            val line = rawLine.replace('\t', ' ').trim()
            if (line.isBlank()) continue
            if (ROW_START_RE.containsMatchIn(line)) {
                if (current.isNotBlank()) blocks += current.toString()
                current.setLength(0)
                current.append(line)
            } else if (current.isNotBlank()) {
                // Continuation of the current row's wrapped description.
                current.append(' ').append(line)
            }
            // Lines before the first dated row (column headers, bank header) are ignored.
        }
        if (current.isNotBlank()) blocks += current.toString()
        return blocks
    }

    private fun parseBlock(block: String): RawPdfTx? {
        val dateMatch = ROW_START_RE.find(block) ?: return null
        val timestamp = parseDate(dateMatch.groupValues[1]) ?: return null

        // Posting amount: comma-with-suffix → any comma → rightmost dot-with-suffix.
        val amountMatch = ACCOUNT_AMOUNT_RE.find(block)
            ?: ANY_COMMA_AMOUNT_RE.find(block)
            ?: DOT_AMOUNT_SUFFIX_RE.findAll(block).lastOrNull()
            ?: return null

        val sign    = amountMatch.groupValues[1].trim()
        val isNeg   = sign.startsWith('-') || sign.startsWith('−')
        val kopecks = AmountParser.toKopecks(amountMatch.groupValues[2])   // unsigned number group
        if (kopecks <= 0L) return null

        val type = if (isNeg) TransactionType.EXPENSE else TransactionType.INCOME

        val afterDate = block.substring(dateMatch.range.last + 1)
        val opCode    = OP_CODE_RE.find(afterDate)?.value
        val merchant  = extractMerchant(afterDate, opCode)

        val dedupKey = if (opCode != null) {
            "pdf_${opCode.uppercase()}"
        } else {
            "pdf_${timestamp}_${kopecks}_${merchant.hashCode()}"
        }

        return RawPdfTx(
            timestampMillis = timestamp,
            type            = type,
            amountKopecks   = kopecks,
            merchant        = merchant,
            balanceKopecks  = null,
            dedupKey        = dedupKey,
        )
    }

    /** Builds a readable merchant name from the description column. */
    private fun extractMerchant(afterDate: String, opCode: String?): String? {
        // Drop the leading operation-code column.
        var desc = afterDate
        if (opCode != null) {
            val idx = desc.indexOf(opCode)
            if (idx >= 0) desc = desc.substring(idx + opCode.length)
        }
        // Cut everything from the posting amount onward (description ends before the amount column).
        ANY_COMMA_AMOUNT_RE.find(desc)?.let { desc = desc.substring(0, it.range.first) }

        // Card rows: the real merchant lives in "место совершения операции: …".
        val place = PLACE_RE.find(desc)?.groupValues?.get(1)
        val base  = if (place != null) cleanPlace(place) else desc

        val cleaned = base
            .let { CARD_OP_RE.replace(it, "") }
            .let { INNER_SUM_RE.replace(it, " ") }
            .let { INNER_DATE_RE.replace(it, " ") }
            .let { LONG_REF_RE.replace(it, " ") }
            .let { MULTISPACE_RE.replace(it, " ") }
            .trim()
            .trim(',', '.', ':', ';', '/', '-', '—', '·')
            .trim()

        return cleaned.takeIf { it.isNotBlank() }?.take(60)
    }

    /** "38830849iRUPermiTRANSPORT PERM TPP MCC4131" → "TRANSPORT PERM TPP". */
    private fun cleanPlace(place: String): String =
        place
            .let { LOC_PREFIX_RE.replace(it, "") }
            .let { MCC_RE.replace(it, " ") }
            .let { MULTISPACE_RE.replace(it, " ") }
            .trim()

    private fun parseDate(raw: String): Long? = try {
        val normalized = raw.replace('/', '.').replace('-', '.')
        LocalDate.parse(normalized, dmyFmt)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
