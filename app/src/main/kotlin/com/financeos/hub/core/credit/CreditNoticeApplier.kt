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
        accountDao.setDuePayment(
            id            = accountId,
            amountKopecks = notice.amountKopecks,
            dueAt         = notice.dueAtMillis,
            seenAt        = System.currentTimeMillis(),
        )
        return true
    }
}
