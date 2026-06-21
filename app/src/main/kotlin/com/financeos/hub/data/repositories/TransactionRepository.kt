package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) {
    fun observeAll(): Flow<List<TransactionEntity>> = dao.observeAll()

    fun observeCurrentMonth(): Flow<List<TransactionEntity>> {
        val zone  = ZoneId.systemDefault()
        val month = YearMonth.now()
        val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to    = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        return dao.observeByPeriod(from, to)
    }

    fun observeByPeriod(from: Long, to: Long): Flow<List<TransactionEntity>> =
        dao.observeByPeriod(from, to)

    fun observeExpensesByCategory(from: Long, to: Long) =
        dao.getExpensesByCategory(from, to)

    suspend fun getAllSmsHashes(): List<String> = dao.getAllSmsHashes()

    suspend fun getById(id: String): TransactionEntity? = dao.getById(id)

    suspend fun insert(tx: TransactionEntity) = dao.insertAll(listOf(tx))

    suspend fun update(tx: TransactionEntity) = dao.update(tx)

    suspend fun softDelete(id: String) = dao.softDelete(id)

    suspend fun deleteAllHistory() = dao.deleteAll()

    suspend fun sumExpensesThisMonth(): Long {
        val zone  = ZoneId.systemDefault()
        val month = YearMonth.now()
        val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to    = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        return dao.sumExpenses(from, to) ?: 0L
    }

    suspend fun sumIncomeThisMonth(): Long {
        val zone  = ZoneId.systemDefault()
        val month = YearMonth.now()
        val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to    = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        return dao.sumIncome(from, to) ?: 0L
    }
}
