package com.financeos.hub.core.account

import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.CardDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Links an incoming parsed transaction to one of the user's accounts using the card mask the
 * bank put in the SMS/push (e.g. Alfa "…; ••2548"), and keeps that account's balance in sync.
 *
 * Resolution order for the mask:
 *  1. an account whose own [card_mask] equals the mask, then
 *  2. an added [CardEntity] (extra card) pointing at an account.
 *
 * Balance sync prefers the bank-provided "Остаток"/"Доступно" (authoritative post-op balance);
 * if the bank didn't include it, the signed transaction amount is applied as a delta instead.
 */
@Singleton
class AccountLinker @Inject constructor(
    private val accountDao: AccountDao,
    private val cardDao: CardDao,
) {
    /** Returns the id of the account that owns [cardMask], or null if none / no mask. */
    suspend fun resolveAccountId(cardMask: String?): String? {
        val mask = cardMask?.trim()?.takeIf { it.isNotBlank() } ?: return null
        accountDao.findByCardMask(mask)?.let { return it.id }
        return cardDao.findAccountIdByMask(mask)
    }

    /**
     * Updates [accountId]'s balance. Uses [ostatokKopecks] (the bank's reported balance) when it
     * is a sane positive value, otherwise nudges the existing balance by [signedDelta]
     * (income +, expense/outgoing −). No-op when [accountId] is null.
     */
    suspend fun syncBalance(accountId: String?, ostatokKopecks: Long?, signedDelta: Long) {
        val id = accountId ?: return
        if (ostatokKopecks != null && ostatokKopecks > 0L) {
            accountDao.updateBalance(id, ostatokKopecks)
        } else {
            val acc = accountDao.getById(id) ?: return
            accountDao.updateBalance(id, acc.balanceKopecks + signedDelta)
        }
    }
}
