package com.financeos.hub.core.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.financeos.hub.core.database.entities.MerchantRuleEntity

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules ORDER BY priority DESC")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<MerchantRuleEntity>)

    @Query("SELECT COUNT(*) FROM merchant_rules")
    suspend fun count(): Int
}
