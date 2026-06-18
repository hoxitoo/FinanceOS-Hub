package com.financeos.hub.core.classifier

import com.financeos.hub.core.database.daos.MerchantRuleDao
import javax.inject.Inject

class DictionaryClassifier @Inject constructor(
    private val merchantRuleDao: MerchantRuleDao,
) : CategoryClassifier {

    override suspend fun classify(merchant: String?, description: String?): String? {
        val haystack = listOfNotNull(merchant, description)
            .joinToString(" ")
            .lowercase()
        if (haystack.isBlank()) return null

        val rules = merchantRuleDao.getAll()
        return rules.firstOrNull { rule ->
            if (rule.isRegex) {
                Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(haystack)
            } else {
                haystack.contains(rule.pattern)
            }
        }?.categoryId
    }
}
