package com.financeos.hub.core.classifier

import com.financeos.hub.core.database.daos.MerchantRuleDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryClassifier @Inject constructor(
    private val merchantRuleDao: MerchantRuleDao,
) : CategoryClassifier {

    private data class CompiledRule(
        val isRegex   : Boolean,
        val literal   : String,   // lowercased substring (when !isRegex)
        val regex     : Regex?,   // compiled once (when isRegex)
        val categoryId: String?,
    )

    private val mutex = Mutex()
    @Volatile private var cache: List<CompiledRule>? = null

    /**
     * Loads and compiles the merchant rules once. Previously this re-queried the DB and
     * recompiled every regex on every single transaction — O(messages × rules) during import.
     * Merchant rules are seeded once and effectively static at runtime, so a one-time cache
     * is safe.
     */
    private suspend fun compiledRules(): List<CompiledRule> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: merchantRuleDao.getAll().map { rule ->
                CompiledRule(
                    isRegex    = rule.isRegex,
                    literal    = rule.pattern.lowercase(),
                    regex      = if (rule.isRegex) Regex(rule.pattern, RegexOption.IGNORE_CASE) else null,
                    categoryId = rule.categoryId,
                )
            }.also { cache = it }
        }
    }

    override suspend fun classify(merchant: String?, description: String?): String? {
        val haystack = listOfNotNull(merchant, description)
            .joinToString(" ")
            .lowercase()
        if (haystack.isBlank()) return null

        return compiledRules().firstOrNull { rule ->
            if (rule.isRegex) rule.regex?.containsMatchIn(haystack) == true
            else haystack.contains(rule.literal)
        }?.categoryId
    }
}
