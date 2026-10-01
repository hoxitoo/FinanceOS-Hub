package com.financeos.hub.core.database

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.InvestmentTransfers
import com.financeos.hub.core.parser.ParserEngine
import com.financeos.hub.core.parser.TransferPatterns
import com.financeos.hub.core.parser.banks.SberbankParser
import com.financeos.hub.core.parser.ciRegex

/**
 * Починка уже записанных пушей Сбера после правки разбора (v21→v22).
 *
 * Прежний разбор склеивал цифры в конце названия с суммой («DODO PIZZA PERM-5 1 034 ₽» → 51 034 ₽,
 * «R15173685 90 ₽» → 1 517 368 590 ₽), считал оплату по СБП в магазине переводом, «Выплату процентов
 * + 77,23» — тратой, перевод в другой банк — покупкой и оставлял в названии игривый заголовок. Новые
 * пуши разбираются верно, но история за месяцы осталась бы с «тратами на сто миллионов».
 *
 * Правило одно: **строка меняется, только если она ровно такая, какой её записал прежний разбор.**
 * Для этого здесь лежит дословная копия прежнего алгоритма ([legacy]) — она защита, а не ответ.
 * Ответ даёт настоящий [SberbankParser] через [ParserEngine], тот же, что у живых пушей. Человек,
 * поправивший сумму, тип или название руками, совпадения не даст — и строку не тронут.
 *
 * Дополнительно не трогаются строки, у которых:
 * - нет банковского «Остатка» — их сумма лежит в балансе счёта (`balanceEffectOf`), и смена суммы
 *   или знака разошлась бы с балансом. У строки с «Остатком» баланс задан банком, сумма на него не
 *   влияет;
 * - есть цель или пара перевода — откатить чужое зачисление или разорвать пару молча нельзя;
 * - «Остаток» нового разбора не совпал со старым — значит, разбор понял текст по-другому целиком.
 */
object SberPushRepair {

    /** Строка истории в том виде, в каком её видит миграция. */
    data class Stored(
        val type           : TransactionType,
        val amountKopecks  : Long,          // знаковая
        val merchant       : String?,
        val balanceKopecks : Long?,
        val goalId         : String?,
        val transferPairId : String?,
        val rawText        : String?,
        val timestamp      : Long,
    )

    /** Что записать вместо прежнего. [typeChanged] — категорию надо подобрать заново. */
    data class Fix(
        val type          : TransactionType,
        val amountKopecks : Long,
        val merchant      : String?,
        val typeChanged   : Boolean,
    )

    private val engine by lazy { ParserEngine(setOf(SberbankParser())) }

    fun plan(s: Stored): Fix? {
        if (s.goalId != null || s.transferPairId != null || s.balanceKopecks == null) return null
        val raw = s.rawText?.let(::normalize) ?: return null

        val old = legacy(raw) ?: return null
        if (old.type != s.type || old.signed != s.amountKopecks || old.merchant != s.merchant) return null

        val new = engine.parse("SBERBANK", raw, s.timestamp) ?: return null
        if (new.balanceKopecks != s.balanceKopecks) return null
        val signed = new.signedKopecks()
        if (new.type == s.type && signed == s.amountKopecks && new.merchant == s.merchant) return null
        return Fix(new.type, signed, new.merchant, typeChanged = new.type != s.type)
    }

    // ── Прежний разбор, дословно ─────────────────────────────────────────────────
    // Копия `SberbankParser.parse` до правки — только ветки, которые дают пуш: перевод через
    // TransferPatterns и разбор по «В запасе / Баланс». SMS-форматы не менялись и сюда не входят:
    // строка из них не совпадёт с [legacy] и останется как есть.

    internal data class Legacy(val type: TransactionType, val signed: Long, val merchant: String?)

    private val pushBalRe = ciRegex(
        "(?:В\\s+запасе|Баланс|Остаток|Доступно):\\s*([\\d][\\d \\u00A0\\u202F]*(?:[.,]\\d{1,2})?)\\s*₽")
    private val pushOpPrefix = ciRegex(
        "^(?:Покупка|Оплата|Списание|Зачисление|Пополнение|Перевод|Платёж|Платеж)(?![А-Яа-яёЁ])[\\s:—–-]*")
    private val pushAmtRe    = Regex("([\\d][\\d \\u00A0\\u202F]*(?:[.,]\\d{1,2})?)\\s*₽")
    private val pushIncomeKw = ciRegex("(?:Зачисление|Пополнение)")

    internal fun legacy(body: String): Legacy? {
        TransferPatterns.detect(body)?.let { r ->
            val merchant = if (r.outgoing) "Перевод" else "Перевод (входящий)"
            val signed = if (r.outgoing) -r.amountKopecks else r.amountKopecks
            return finish(TransactionType.TRANSFER, signed, merchant)
        }
        val balMatch = pushBalRe.find(body) ?: return null
        if (AmountParser.toKopecks(balMatch.groupValues[1]) < 0L) return null
        val before = body.substring(0, balMatch.range.first)
        val amtMatch = pushAmtRe.findAll(before).lastOrNull() ?: return null
        val amount = AmountParser.toKopecks(amtMatch.groupValues[1])
        if (amount <= 0L) return null
        val income = pushIncomeKw.containsMatchIn(body)
        val merchant = before.substring(0, amtMatch.range.first)
            .trim().trim('.', ',', ';', '-', '—', '–', ' ')
            .replace(pushOpPrefix, "")
            .trim()
            .takeIf { it.isNotBlank() }
        return if (income) finish(TransactionType.INCOME, amount, merchant)
        else finish(TransactionType.EXPENSE, -amount, merchant)
    }

    // Проход по брокерам шёл и тогда — в ParserEngine, после разборщика банка.
    private fun finish(type: TransactionType, signed: Long, merchant: String?): Legacy {
        if (!InvestmentTransfers.isBroker(merchant)) return Legacy(type, signed, merchant)
        return Legacy(TransactionType.TRANSFER, signed, merchant)
    }

    private fun normalize(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    // ── Подбор категории по словарю ──────────────────────────────────────────────

    /** Правило словаря в том виде, в каком его читает `DictionaryClassifier`. */
    class Rule(pattern: String, isRegex: Boolean, val categoryId: String?) {
        private val literal = pattern.lowercase()
        // Как в DictionaryClassifier: регулярка компилируется один раз; битая — просто не совпадает.
        private val regex = if (isRegex) runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() else null
        private val regexRule = isRegex

        fun matches(haystack: String): Boolean =
            if (regexRule) regex?.containsMatchIn(haystack) == true else haystack.contains(literal)
    }

    /**
     * То же правило, что `DictionaryClassifier.classify`: первое совпадение в порядке
     * `priority DESC`, дальше — порядок вставки. Здесь нет корутин и DAO, потому что миграция
     * работает с курсором напрямую; правила те же самые строки таблицы.
     */
    fun categorize(merchant: String?, rules: List<Rule>): String? {
        val haystack = merchant?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return rules.firstOrNull { it.matches(haystack) }?.categoryId
    }
}
