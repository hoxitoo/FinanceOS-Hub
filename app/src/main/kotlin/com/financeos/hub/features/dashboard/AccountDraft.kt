package com.financeos.hub.features.dashboard

import com.financeos.hub.core.database.entities.AccountKind

/**
 * What the "new account" sheet collects, before it becomes an [com.financeos.hub.core.database.entities.AccountEntity].
 *
 * A named object rather than a widening positional lambda: the callback already carried five
 * parameters and credit cards add five more, at which point `(name, bank, mask, kopecks, currency,
 * limit, apr, day, days, minBp)` at every call site is one silent transposition away from storing
 * the APR as the statement day.
 *
 * [balanceKopecks] follows the entity's convention — for [AccountKind.CREDIT] it is the debt as a
 * NON-POSITIVE number. The sheet asks the user for the debt as a plain positive amount and negates
 * it once, here at the boundary.
 */
data class AccountDraft(
    val name          : String,
    val bank          : String,
    val cardMask      : String?,
    val balanceKopecks: Long,
    val currency      : String,
    val kind          : AccountKind = AccountKind.CASH,
    // CREDIT-only; all null when the user left them empty.
    val creditLimitKopecks: Long? = null,
    val aprBp             : Int?  = null,
    val statementDay      : Int?  = null,
    val dueDays           : Int?  = null,
    val minPaymentBp      : Int?  = null,
)
