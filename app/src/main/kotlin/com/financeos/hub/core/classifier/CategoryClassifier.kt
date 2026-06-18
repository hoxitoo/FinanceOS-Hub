package com.financeos.hub.core.classifier

interface CategoryClassifier {
    /** Returns categoryId or null if no match found */
    suspend fun classify(merchant: String?, description: String?): String?
}
