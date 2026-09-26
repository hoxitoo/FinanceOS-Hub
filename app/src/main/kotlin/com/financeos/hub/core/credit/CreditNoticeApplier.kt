package com.financeos.hub.core.credit

import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.parser.CreditNoticeParser.CreditNotice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files a parsed payment reminder against the card it is about.
 *
 * Separate from the transaction ingest path on purpose: a reminder inserts NOTHING. It only
 * records what the bank demanded and by when, which the credit screen then shows in preference to
 * its own inference from the statement day the user typed in.
 *
 * The reminder carries no card mask, so the card is resolved by bank — and only when the answer is
 * unambiguous. With two Сбер credit cards the demand is dropped rather than pinned to whichever
 * one happened to sort first; a payment deadline shown on the wrong card is worse than none.
 */
@Singleton
class CreditNoticeApplier @Inject constructor(
    private val accountDao   : AccountDao,
    private val accountLinker: AccountLinker,
) {
    /** Returns true when the notice was filed against a card. */
    suspend fun apply(notice: CreditNotice): Boolean {
        val accountId = accountLinker.resolveCreditAccountForBank(notice.bankId) ?: return false
        val account   = accountDao.getById(accountId)

        // Момент получения хранится отдельным решением: повторная доставка того же требования не
        // должна его двигать — см. [noticeSeenAt].
        accountDao.setDuePayment(
            id            = accountId,
            amountKopecks = notice.amountKopecks,
            dueAt         = notice.dueAtMillis,
            seenAt        = noticeSeenAt(
                previousAmountKopecks = account?.duePaymentKopecks,
                previousDueAt         = account?.duePaymentAt,
                previousSeenAt        = account?.duePaymentSeenAt,
                amountKopecks         = notice.amountKopecks,
                dueAt                 = notice.dueAtMillis,
                now                   = System.currentTimeMillis(),
            ),
        )
        return true
    }
}

/**
 * Каким остаётся «когда пришло напоминание» после очередной доставки.
 *
 * ТО ЖЕ САМОЕ требование момент не двигает. Одно напоминание приходит не один раз: SMS и пуш о нём
 * — две доставки одного события, и банк повторяет его по мере приближения срока. Вставки здесь нет,
 * поэтому дедуп операций до этого места не достаёт. Сдвинуть момент на «сейчас» значило бы обнулить
 * зачёт погашений (инвариант #38): уже оплаченное требование воскресало бы при каждом повторе —
 * ровно тот дефект, из-за которого зачёт и появился.
 *
 * «То же самое» — совпадение суммы И срока. Новая цифра или новая дата означают новый расчётный
 * период, и отсчёт обязан начаться заново: платежи за прошлый период банк в ней уже учёл.
 */
fun noticeSeenAt(
    previousAmountKopecks: Long?,
    previousDueAt        : Long?,
    previousSeenAt       : Long?,
    amountKopecks        : Long,
    dueAt                : Long,
    now                  : Long,
): Long {
    val sameDemand = previousAmountKopecks == amountKopecks && previousDueAt == dueAt
    return previousSeenAt?.takeIf { sameDemand } ?: now
}
