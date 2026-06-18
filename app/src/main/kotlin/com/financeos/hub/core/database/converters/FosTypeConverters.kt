package com.financeos.hub.core.database.converters

import androidx.room.TypeConverter
import com.financeos.hub.core.database.entities.BudgetPeriod
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType

class FosTypeConverters {
    @TypeConverter fun txTypeToString(v: TransactionType): String = v.name
    @TypeConverter fun stringToTxType(v: String): TransactionType = TransactionType.valueOf(v)

    @TypeConverter fun txSourceToString(v: TransactionSource): String = v.name
    @TypeConverter fun stringToTxSource(v: String): TransactionSource = TransactionSource.valueOf(v)

    @TypeConverter fun budgetPeriodToString(v: BudgetPeriod): String = v.name
    @TypeConverter fun stringToBudgetPeriod(v: String): BudgetPeriod = BudgetPeriod.valueOf(v)
}
