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
    // All nullable: they are unknown until the user enters them, and no bank message carries them.
    // Null means "not configured" — the UI then omits the interest-free period and the minimum
    // payment instead of inventing a number.
    /** Total credit line, kopecks. Free limit = this + balanceKopecks. */
    @ColumnInfo(name = "credit_limit_kopecks") val creditLimitKopecks: Long? = null,
    /** Annual rate in BASIS POINTS — 29.8% → 2980. Integer so no float drift creeps into money. */
    @ColumnInfo(name = "apr_bp") val aprBp: Int? = null,
    /** Day of month the statement closes, 1..31 (clamped to the month's length). */
    @ColumnInfo(name = "statement_day") val statementDay: Int? = null,
    /** Days allowed to pay after the statement closes (Сбер: 20). */
    @ColumnInfo(name = "due_days") val dueDays: Int? = null,
    /** Obligatory monthly payment as basis points of the debt — «до 10% от долга» → 1000. */
    @ColumnInfo(name = "min_payment_bp") val minPaymentBp: Int? = null,
    /**
     * Floor under that payment, kopecks — «но не менее 150 руб.» → 15_000.
     *
     * Not cosmetic. A payment that is only a percentage of the balance shrinks with it and never
     * reaches zero, so without a floor the pay-off simulation runs forever. It used to be a
     * hardcoded guess; taking it from the tariff makes the estimate the user's own.
     */
    @ColumnInfo(name = "min_payment_floor_kopecks") val minPaymentFloorKopecks: Long? = null,
    /**
     * Length of the interest-free period in DAYS, counted from a purchase — «до 120 дней».
     *
     * A different thing from [statementDay]/[dueDays], which describe the monthly obligatory
     * payment. A card can demand a small payment every month AND leave the purchase itself
     * interest-free for four months; conflating the two was the original modelling mistake.
     */
    @ColumnInfo(name = "interest_free_days") val interestFreeDays: Int? = null,
    /** Penalty rate once an obligatory payment is missed — «36% годовых» → 3600. */
    @ColumnInfo(name = "penalty_apr_bp") val penaltyAprBp: Int? = null,
    /** Cash-withdrawal / transfer fee, percentage part in basis points — «5,9%» → 590. */
    @ColumnInfo(name = "cash_fee_bp") val cashFeeBp: Int? = null,
    /** …plus its fixed part, kopecks — «+ 590 ₽» → 59_000. */
    @ColumnInfo(name = "cash_fee_fixed_kopecks") val cashFeeFixedKopecks: Long? = null,

    // ── What the BANK itself said about the next payment ──────────────────────
    // Straight from a «Платёж по кредитной карте» reminder push. This outranks anything computed
    // from statementDay/dueDays: those are the user's recollection of their contract, this is the
    // bank's own demand. Null until such a push arrives.
    /** Obligatory payment the bank asked for, kopecks. */
    @ColumnInfo(name = "due_payment_kopecks") val duePaymentKopecks: Long? = null,
    /** Its deadline, epoch millis at the start of that day. */
    @ColumnInfo(name = "due_payment_at") val duePaymentAt: Long? = null,
    /** When the reminder arrived — lets the UI ignore a demand that is long past. */
    @ColumnInfo(name = "due_payment_seen_at") val duePaymentSeenAt: Long? = null,
)
