package com.financeos.hub.di

import com.financeos.hub.core.analytics.AnalyticsWorker
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// AnalyticsEngine, ScoreCalculator, InsightGenerator — all @Singleton with @Inject constructors.
// AnalyticsWorker uses @AssistedInject — registered automatically via HiltWorkerFactory.
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule
