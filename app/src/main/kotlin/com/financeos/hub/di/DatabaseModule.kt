package com.financeos.hub.di

import android.content.Context
import androidx.room.Room
import com.financeos.hub.core.database.FosDatabase
import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.BudgetDao
import com.financeos.hub.core.database.daos.CategoryDao
import com.financeos.hub.core.database.daos.GoalDao
import com.financeos.hub.core.database.daos.MerchantRuleDao
import com.financeos.hub.core.database.daos.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): FosDatabase =
        Room.databaseBuilder(ctx, FosDatabase::class.java, "financeos.db")
            .addCallback(FosDatabase.PREPOPULATE_CALLBACK)
            .build()

    @Provides fun provideAccountDao(db: FosDatabase): AccountDao             = db.accountDao()
    @Provides fun provideTransactionDao(db: FosDatabase): TransactionDao     = db.transactionDao()
    @Provides fun provideCategoryDao(db: FosDatabase): CategoryDao           = db.categoryDao()
    @Provides fun provideBudgetDao(db: FosDatabase): BudgetDao               = db.budgetDao()
    @Provides fun provideGoalDao(db: FosDatabase): GoalDao                   = db.goalDao()
    @Provides fun provideMerchantRuleDao(db: FosDatabase): MerchantRuleDao   = db.merchantRuleDao()
}
