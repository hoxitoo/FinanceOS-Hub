package com.financeos.hub.di

import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.classifier.DictionaryClassifier
import com.financeos.hub.core.ml.MLCategoryClassifier
import com.financeos.hub.data.preferences.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MLModule {

    /**
     * Provides the active CategoryClassifier:
     *   - MLCategoryClassifier when ml_classification_enabled = true
     *   - DictionaryClassifier otherwise
     *
     * The choice is made at injection time; the app must restart to switch.
     * In practice, SmsReceiver and ParserEngine are singletons so this is fine.
     */
    @Provides
    @Singleton
    fun provideCategoryClassifier(
        mlClassifier        : MLCategoryClassifier,
        dictionaryClassifier: DictionaryClassifier,
        userPreferences     : UserPreferences,
    ): CategoryClassifier {
        val mlEnabled = runBlocking { userPreferences.mlClassificationEnabled.first() }
        return if (mlEnabled) mlClassifier else dictionaryClassifier
    }
}
