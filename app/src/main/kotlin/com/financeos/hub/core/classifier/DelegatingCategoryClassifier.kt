package com.financeos.hub.core.classifier

import com.financeos.hub.core.ml.MLCategoryClassifier
import com.financeos.hub.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes classification to the ML or dictionary classifier based on the user's
 * `ml_classification_enabled` preference.
 *
 * The preference is read lazily inside [classify] (a suspend function), so no
 * blocking DataStore read happens during Hilt graph construction — this avoids the
 * main-thread ANR that a `runBlocking { … }` provider would cause. The toggle takes
 * effect on the next classification, with no app restart required.
 */
@Singleton
class DelegatingCategoryClassifier @Inject constructor(
    private val mlClassifier        : MLCategoryClassifier,
    private val dictionaryClassifier: DictionaryClassifier,
    private val userPreferences     : UserPreferences,
) : CategoryClassifier {

    override suspend fun classify(merchant: String?, description: String?): String? {
        val mlEnabled = userPreferences.mlClassificationEnabled.first()
        val delegate = if (mlEnabled) mlClassifier else dictionaryClassifier
        return delegate.classify(merchant, description)
    }
}
