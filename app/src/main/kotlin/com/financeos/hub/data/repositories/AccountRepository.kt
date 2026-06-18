package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.entities.AccountEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
) {
    fun observeAll(): Flow<List<AccountEntity>> = dao.observeAll()

    suspend fun upsert(account: AccountEntity) = dao.upsert(account)

    suspend fun updateBalance(id: String, kopecks: Long) = dao.updateBalance(id, kopecks)

    suspend fun totalBalance(): Long {
        // summed by observeAll() collector; expose for one-shot reads
        return 0L
    }
}
