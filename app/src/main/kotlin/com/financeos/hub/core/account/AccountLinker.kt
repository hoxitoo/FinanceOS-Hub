package com.financeos.hub.core.account

import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.CardDao
import com.financeos.hub.core.database.daos.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Links an incoming parsed transaction to one of the user's accounts.
 *
 * Resolution order:
 *  1. Card mask present → exact match in accounts.card_mask or card table.
 *  2. Card mask absent OR unmatched (e.g. account-number tail "408*01139" → "1139") →
 *     find the ONE active account whose bank name contains a known keyword for [bankId]
 *     (e.g. "sberbank" → "сбер"). Returns null when 0 or 2+ accounts match (ambiguous —
 *     user must link manually via the account card).
 *
 * Balance sync prefers the bank-provided "Остаток"/"Доступно" (authoritative
 * post-op balance); if absent, the signed transaction amount is used as a delta.
 * A reported balance of exactly 0 is authoritative — only null falls through to delta.
 */
@Singleton
class AccountLinker @Inject constructor(
    private val accountDao: AccountDao,
    private val cardDao: CardDao,
    private val transactionDao: TransactionDao,
) {
    /**
     * Returns the id of the account that owns [cardMask], or — when no mask is
     * present — the id of the single account whose bank name matches [bankId].
     */
    suspend fun resolveAccountId(cardMask: String?, bankId: String? = null): String? {
        // Card mask first (exact, then digit-tolerant) — this is the reliable path.
        resolveAccountByCardMask(cardMask)?.let { return it }
        // No card mask, or mask matched nothing — fall back to bank-sender identity.
        if (bankId == null) return null
        val keywords = BANK_KEYWORDS[bankId.lowercase()] ?: return null
        val matches = accountDao.getAllActive().filter { acc ->
            keywords.any { kw -> acc.bank.lowercase().contains(kw) }
        }
        // Only link when unambiguous (exactly one account for this bank).
        return if (matches.size == 1) matches.first().id else null
    }

    /**
     * Resolves the account that owns [cardMask] using the CARD MASK ONLY — never the ambiguous
     * bank-name fallback. Returns null when the mask is blank or maps to no registered card/account.
     * Used for balance reconciliation, where routing a balance to the wrong account of a multi-account
     * bank (e.g. one of five Alfa accounts) is worse than not updating at all.
     *
     * Matching is digit-tolerant: exact `card_mask` match first, then a fallback that compares the
     * LAST 4 DIGITS only. The fallback rescues the reported case where a parsed mask "2548" is shown
     * on the operation yet doesn't link — because the stored card_mask drifted in format (a stray
     * space/glyph from an old add path or a backup restore) so the exact SQL match silently missed.
     */
    suspend fun resolveAccountByCardMask(cardMask: String?): String? {
        val mask = cardMask?.trim()?.takeIf { it.isNotBlank() } ?: return null
        // Resolve against ACTIVE accounts only. Deactivating an account does NOT deactivate its
        // cards, so a stale card row can still point at a now-deleted ("ghost") account and win a
        // bare LIMIT 1 lookup — sending the balance to an account the user can't see. We therefore
        // filter both the account's own mask and registered card rows to live accounts.
        val activeAccounts = accountDao.getAllActive()
        val activeIds = activeAccounts.mapTo(HashSet()) { it.id }
        val activeCards = cardDao.getAllActive().filter { it.accountId in activeIds }

        // Exact match: account's own (primary) mask, then a registered card.
        activeAccounts.firstOrNull { it.cardMask == mask }?.let { return it.id }
        activeCards.firstOrNull { it.cardMask == mask }?.let { return it.accountId }

        // Digit-tolerant fallback: compare the LAST 4 DIGITS (rescues a format-drifted stored mask).
        val last4 = mask.filter(Char::isDigit).takeLast(4)
        if (last4.length == 4) {
            activeAccounts.firstOrNull { it.cardMask?.filter(Char::isDigit)?.takeLast(4) == last4 }
                ?.let { return it.id }
            activeCards.firstOrNull { it.cardMask.filter(Char::isDigit).takeLast(4) == last4 }
                ?.let { return it.accountId }
        }
        return null
    }

    /**
     * Updates [accountId]'s balance. Uses [ostatokKopecks] (the bank's reported balance) when
     * present and non-negative, otherwise nudges by [signedDelta] (income +, expense/transfer −).
     * No-op when [accountId] is null.
     */
    suspend fun syncBalance(accountId: String?, ostatokKopecks: Long?, signedDelta: Long) {
        val id = accountId ?: return
        if (ostatokKopecks != null && ostatokKopecks >= 0L) {
            accountDao.updateBalance(id, ostatokKopecks)
        } else {
            val acc = accountDao.getById(id) ?: return
            accountDao.updateBalance(id, acc.balanceKopecks + signedDelta)
        }
    }

    /**
     * Applies a bank-authoritative "Остаток" to the account that owns [cardMask], independent of any
     * transaction row. This is the safety net for the dedup-drop path: a bank commonly delivers the
     * SAME event more than once (a collapsed notification re-posted as expanded, or an SMS twin of a
     * push). The first (skeletal) copy may lack the "Остаток"/card line and so leaves the balance
     * unset; the richer second copy is then dropped by [TransactionDao.existsSimilarSmsOrPush] BEFORE
     * [syncBalance] can run, discarding the authoritative figure. Calling this on every drop keeps the
     * balance current without inserting a duplicate transaction. Resolves by card mask only and never
     * overwrites with a negative/absent value. No-op when the mask can't be resolved to an account.
     */
    suspend fun applyAuthoritativeBalance(cardMask: String?, ostatokKopecks: Long?) {
        if (ostatokKopecks == null || ostatokKopecks < 0L) return
        val accountId = resolveAccountByCardMask(cardMask) ?: return
        accountDao.updateBalance(accountId, ostatokKopecks)
    }

    /**
     * Retroactively attaches orphan SMS/PUSH transactions for [cardMask] to [accountId] and
     * reconciles the account to the latest bank-authoritative balance. Call this when the user
     * registers a card or creates an account: transactions for that card may have been ingested
     * earlier while the app could not resolve them (e.g. the card was unknown and the bank has
     * several accounts → ambiguous fallback → balance silently dropped). No-op when [cardMask]
     * is blank or nothing was orphaned.
     */
    suspend fun relinkOrphans(accountId: String, cardMask: String?) {
        val mask = cardMask?.trim()?.takeIf { it.isNotBlank() } ?: return
        val linked = transactionDao.linkOrphansToAccount(accountId, mask)
        if (linked > 0) {
            // Reconcile to the most recent authoritative "Остаток" across the account's cards.
            transactionDao.latestBalanceForAccount(accountId)?.let { authoritative ->
                accountDao.updateBalance(accountId, authoritative)
            }
        }
    }

    /**
     * Full repair of [accountId]: adopts EVERY orphan SMS/PUSH transaction whose card mask belongs
     * to this account (its own mask + every registered card), then snaps the balance to the most
     * recent bank-authoritative "Остаток" among the account's transactions.
     *
     * This is the reliable fix for the recurring "card linked after its transactions" gap: a debit
     * on a second card that didn't resolve at ingest stays orphaned (account_id NULL) and so never
     * moved the balance — [relinkOrphans] only covers a single mask and only reconciles when that
     * mask linked something. Idempotent (only adopts still-unlinked rows, never applies a delta), so
     * it's safe to call after every ingest and from a manual "пересчитать" action.
     */
    suspend fun reconcileAccount(accountId: String, force: Boolean = false) {
        var linked = 0
        accountMasks(accountId).forEach { linked += transactionDao.linkOrphansToAccount(accountId, it) }
        // Snap to the bank's latest "Остаток" only when we actually adopted orphans (auto path) or
        // the user explicitly asked (force). This keeps the routine post-ingest call a no-op in the
        // common case, so it never overrides the per-transaction delta that syncBalance just applied.
        if (linked > 0 || force) {
            transactionDao.latestBalanceForAccount(accountId)?.let { authoritative ->
                accountDao.updateBalance(accountId, authoritative)
            }
        }
    }

    /** All card last-4s that belong to [accountId]: the account's own mask + its registered cards. */
    private suspend fun accountMasks(accountId: String): List<String> {
        val own = accountDao.getById(accountId)?.cardMask?.trim()?.takeIf { it.isNotBlank() }
        val cardMasks = cardDao.getAllActive()
            .filter { it.accountId == accountId }
            .map { it.cardMask.trim() }
            .filter { it.isNotBlank() }
        return (listOfNotNull(own) + cardMasks).distinct()
    }

    companion object {
        /** Maps parser bankId (lowercase) → substrings to look for in AccountEntity.bank (lowercase). */
        private val BANK_KEYWORDS: Map<String, List<String>> = mapOf(
            "alfabank"       to listOf("альфа", "alfa"),
            "sberbank"       to listOf("сбер", "sber"),
            "tbank"          to listOf("т-банк", "тинькофф", "tinkoff", "тинк", "tbank"),
            "vtb"            to listOf("втб", "vtb"),
            "gazprombank"    to listOf("газпром", "gazprom"),
            "raiffeisen"     to listOf("райф", "raiff"),
            "rosbank"        to listOf("росбанк", "rosbank"),
            "otkritie"       to listOf("открыт", "otkrit"),
            "mtsbank"        to listOf("мтс банк", "mts bank", "мтсб"),
            "postabank"      to listOf("почта банк", "pochta", "pochtabank"),
            "rosselkhozbank" to listOf("россельхоз", "рсхб", "rshb"),
            "mbank"          to listOf("mbank", "мбанк", "m bank"),
            "mkb"            to listOf("мкб", "mkb", "московский кредитный"),
        )
    }
}
