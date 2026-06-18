package com.financeos.hub.di

import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.classifier.DictionaryClassifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {
    @Binds
    @Singleton
    abstract fun bindClassifier(impl: DictionaryClassifier): CategoryClassifier
}
