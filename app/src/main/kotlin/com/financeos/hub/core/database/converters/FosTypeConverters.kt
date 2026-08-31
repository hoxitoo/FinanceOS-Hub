package com.financeos.hub.core.database.converters

import androidx.room.TypeConverter
import com.financeos.hub.core.database.entities.AccountKind
import com.financeos.hub.core.database.entities.BudgetPeriod
import com.financeos.hub.core.database.entities.PaymentDirection
import com.financeos.hub.core.database.entities.PaymentSchedule
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.database.entities.TransferMatchType

class FosTypeConverters {
    @TypeConverter fun txTypeToString(v: TransactionType): String = v.name
    @TypeConverter fun stringToTxType(v: String): TransactionType = TransactionType.valueOf(v)

    @TypeConverter fun txSourceToString(v: TransactionSource): String = v.name
    @TypeConverter fun stringToTxSource(v: String): TransactionSource = TransactionSource.valueOf(v)

    @TypeConverter fun budgetPeriodToString(v: BudgetPeriod): String = v.name
    @TypeConverter fun stringToBudgetPeriod(v: String): BudgetPeriod = BudgetPeriod.valueOf(v)

    @TypeConverter fun transferMatchTypeToString(v: TransferMatchType): String = v.name
    @TypeConverter fun stringToTransferMatchType(v: String): TransferMatchType = TransferMatchType.valueOf(v)

    @TypeConverter fun paymentDirectionToString(v: PaymentDirection): String = v.name
    // Терпимо на чтении, как и у AccountKind: неизвестное значение не должно ронять весь календарь.
    // OUT — безопасное падение назад: обязательство уменьшит «Свободно», а не раздует его.
    @TypeConverter fun stringToPaymentDirection(v: String): PaymentDirection =
        runCatching { PaymentDirection.valueOf(v) }.getOrDefault(PaymentDirection.OUT)

    @TypeConverter fun paymentScheduleToString(v: PaymentSchedule): String = v.name
    // ONCE — тоже безопасное: разовый платёж не размножится по будущим датам сам собой.
    @TypeConverter fun stringToPaymentSchedule(v: String): PaymentSchedule =
        runCatching { PaymentSchedule.valueOf(v) }.getOrDefault(PaymentSchedule.ONCE)

    @TypeConverter fun accountKindToString(v: AccountKind): String = v.name
    // Tolerant on read: an unknown value (older/newer build, hand-edited DB) must not crash the
    // whole account list — degrade to CASH, which is how every pre-v11 row behaved anyway.
    @TypeConverter fun stringToAccountKind(v: String): AccountKind =
        runCatching { AccountKind.valueOf(v) }.getOrDefault(AccountKind.CASH)
}
