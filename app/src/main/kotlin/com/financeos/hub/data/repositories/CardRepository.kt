package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.CardDao
import com.financeos.hub.core.database.entities.CardEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepository @Inject constructor(private val dao: CardDao) {
    fun observeAll(): Flow<List<CardEntity>> = dao.observeAll()
    fun observeByAccount(accountId: String): Flow<List<CardEntity>> = dao.observeByAccount(accountId)
    suspend fun addCard(card: CardEntity) = dao.insert(card)
    suspend fun deactivate(id: String) = dao.deactivate(id)
}
