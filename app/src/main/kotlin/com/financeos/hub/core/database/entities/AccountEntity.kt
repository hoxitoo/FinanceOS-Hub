package com.financeos.hub.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What kind of money an account holds. Drives how it is aggregated into net worth.
 *
 *  - [CASH]       — own money (debit card, current account, cash). Counts toward net worth.
 *  - [CREDIT]     — the bank's money. `balanceKopecks` is ZERO OR NEGATIVE and its magnitude is
 *                   the outstanding debt; the free limit is `creditLimitKopecks + balanceKopecks`.
 *                   Excluded from the cash net worth and shown as debt instead.
 *  - [INVESTMENT] — brokerage/deposit. Reserved: the column exists so adding investment accounts
 *                   later needs no second migration, but nothing consumes it yet.
 */
enum class AccountKind { CASH, CREDIT, INVESTMENT }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bank: String,
    @ColumnInfo(name = "card_mask") val cardMask: String?,          // last 4 digits
    /**
     * CASH/INVESTMENT: what you own (may go negative on an overdraft).
     * CREDIT: what you OWE, stored as a non-positive number (50 000 ₽ of debt → −5_000_000).
     * Keeping debt negative means every existing delta path (a purchase subtracts, a repayment
     * adds) stays correct without a single sign special-case.
     */
    @ColumnInfo(name = "balance_kopecks") val balanceKopecks: Long,
    val currency: String = "RUB",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),

    // ── Account kind ──────────────────────────────────────────────────────────
    val kind: AccountKind = AccountKind.CASH,

    // ── CREDIT-only terms ─────────────────────────────────────────────────────
    // All nullable: they are unknown until the user enters them, and no bank message carries
    // them. Null means "not configured" — the UI then omits the grace timeline / minimum payment
    // instead of inventing a number.
    /** Total credit line, kopecks. Free limit = this + balanceKopecks. */
    @ColumnInfo(name = "credit_limit_kopecks") val creditLimitKopecks: Long? = null,
    /** Annual rate in BASIS POINTS — 29.8% → 2980. Integer so no float drift creeps into money. */
    @ColumnInfo(name = "apr_bp") val aprBp: Int? = null,
    /** Day of month the statement closes, 1..31 (clamped to the month's length). */
    @ColumnInfo(name = "statement_day") val statementDay: Int? = null,
    /** Days allowed to pay after the statement closes (Сбер: 20). */
    @ColumnInfo(name = "due_days") val dueDays: Int? = null,
    /** Minimum payment as basis points of the debt — 5% → 500. */
    @ColumnInfo(name = "min_payment_bp") val minPaymentBp: Int? = null,
)
