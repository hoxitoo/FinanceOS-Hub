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

    suspend fun getById(id: String): AccountEntity? = dao.getById(id)

    suspend fun upsert(account: AccountEntity) = dao.upsert(account)

    suspend fun updateBalance(id: String, kopecks: Long) = dao.updateBalance(id, kopecks)

    suspend fun deactivate(id: String) = dao.deactivate(id)
}
