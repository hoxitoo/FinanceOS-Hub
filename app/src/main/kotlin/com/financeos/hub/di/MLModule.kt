package com.financeos.hub.di

import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.classifier.DelegatingCategoryClassifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the single [CategoryClassifier] used across the app.
 *
 * [DelegatingCategoryClassifier] picks ML vs dictionary at classification time based on
 * the user preference, so there is no blocking DataStore read during graph construction.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MLModule {

    @Binds
    @Singleton
    abstract fun bindCategoryClassifier(impl: DelegatingCategoryClassifier): CategoryClassifier
}
