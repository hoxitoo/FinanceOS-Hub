package com.financeos.hub.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.financeos.hub.core.database.FosDatabase
import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.BudgetDao
import com.financeos.hub.core.database.daos.CardDao
import com.financeos.hub.core.database.daos.CategoryDao
import com.financeos.hub.core.database.daos.GoalDao
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.daos.TransferRouteDao
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.BudgetEntity
import com.financeos.hub.core.database.entities.BudgetPeriod
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.database.entities.TransferMatchType
import com.financeos.hub.core.database.entities.TransferRouteEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whole-database backup to / restore from a single self-describing JSON file.
 *
 * The file is plain JSON (no external dependency — uses [org.json]) with a [SCHEMA_VERSION]
 * header so a backup taken today can still be read after the schema evolves. Restore is
 * additive and idempotent: rows are upserted by primary key inside one DB transaction, so
 * re-importing the same file twice changes nothing and partial failures roll back cleanly.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db              : FosDatabase,
    private val accountDao      : AccountDao,
    private val cardDao         : CardDao,
    private val categoryDao     : CategoryDao,
    private val goalDao         : GoalDao,
    private val budgetDao       : BudgetDao,
    private val transferRouteDao: TransferRouteDao,
    private val transactionDao  : TransactionDao,
) {
    data class RestoreCounts(
        val accounts    : Int,
        val cards       : Int,
        val categories  : Int,
        val goals       : Int,
        val budgets     : Int,
        val routes      : Int,
        val transactions: Int,
    ) {
        val total: Int get() = accounts + cards + categories + goals + budgets + routes + transactions
    }

    // ─── Export ───────────────────────────────────────────────────────────────

    /** Reads every table and writes an AES-GCM-256 encrypted backup to the user-chosen [uri]. */
    suspend fun exportTo(uri: Uri) {
        val plaintext = buildJson().toByteArray(Charsets.UTF_8)
        val bytes     = BackupCrypto.encrypt(plaintext)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(bytes)
        } ?: error("Не удалось открыть файл для записи")
    }

    private suspend fun buildJson(): String {
        val root = JSONObject()
        root.put("schema", SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "FinanceOS-Hub")

        root.put("accounts",   JSONArray().apply { accountDao.getAllActive().forEach { put(it.toJson()) } })
        root.put("cards",      JSONArray().apply { cardDao.getAllActive().forEach { put(it.toJson()) } })
        root.put("categories", JSONArray().apply { categoryDao.getAll().forEach { put(it.toJson()) } })
        root.put("goals",      JSONArray().apply { goalDao.getAllForBackup().forEach { put(it.toJson()) } })
        root.put("budgets",    JSONArray().apply { budgetDao.getAllActive().forEach { put(it.toJson()) } })
        root.put("routes",     JSONArray().apply { transferRouteDao.getAllActive().forEach { put(it.toJson()) } })
        root.put("transactions", JSONArray().apply { transactionDao.getAllForBackup().forEach { put(it.toJson()) } })
        return root.toString(2)
    }

    // ─── Import ─────────────────────────────────────────────────────────────────

    /** Reads a backup file at [uri] and upserts its contents in one transaction. */
    suspend fun restoreFrom(uri: Uri): RestoreCounts {
        val raw  = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось открыть файл")
        val text = runCatching { BackupCrypto.decrypt(raw).toString(Charsets.UTF_8) }.getOrNull()
            ?: error("Не удалось расшифровать файл")
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: error("Файл повреждён или не является резервной копией")
        if (!root.has("accounts") && !root.has("transactions")) {
            error("Это не похоже на резервную копию FinanceOS")
        }

        val accounts    = root.optArray("accounts").map   { it.toAccount() }
        val categories  = root.optArray("categories").map { it.toCategory() }
        val goals       = root.optArray("goals").map      { it.toGoal() }
        val routes      = root.optArray("routes").map     { it.toRoute() }
        val rawCards    = root.optArray("cards").map       { it.toCard() }
        val rawBudgets  = root.optArray("budgets").map     { it.toBudget() }
        val rawTxs      = root.optArray("transactions").map { it.toTransaction() }

        // Room enforces foreign keys, so a child row pointing at a parent that isn't part of
        // this backup would abort the whole restore. Drop / null-out dangling references first.
        val accountIds  = accounts.map { it.id }.toHashSet()
        val categoryIds = categories.map { it.id }.toHashSet()
        val cards   = rawCards.filter { it.accountId in accountIds }
        val budgets = rawBudgets.filter { it.categoryId in categoryIds }
        val transactions = rawTxs.map { tx ->
            tx.copy(
                accountId  = tx.accountId?.takeIf { it in accountIds },
                categoryId = tx.categoryId?.takeIf { it in categoryIds },
            )
        }

        // FK-safe order: parents (categories, accounts) before children (cards, budgets, transactions).
        // Use upsertAll (REPLACE) for categories so that user-renamed categories survive restore.
        db.withTransaction {
            categoryDao.upsertAll(categories)
            accounts.forEach { accountDao.upsert(it) }
            goals.forEach    { goalDao.upsert(it) }
            cards.forEach    { cardDao.insert(it) }
            budgets.forEach  { budgetDao.upsert(it) }
            routes.forEach   { transferRouteDao.insert(it) }
            transactionDao.insertAll(transactions)
        }

        return RestoreCounts(
            accounts.size, cards.size, categories.size,
            goals.size, budgets.size, routes.size, transactions.size,
        )
    }

    // ─── Entity → JSON ────────────────────────────────────────────────────────

    private fun AccountEntity.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("bank", bank)
        putNullable("cardMask", cardMask)
        put("balanceKopecks", balanceKopecks); put("currency", currency)
        put("isActive", isActive); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun CardEntity.toJson() = JSONObject().apply {
        put("id", id); put("accountId", accountId); put("cardMask", cardMask)
        put("isActive", isActive); put("createdAt", createdAt)
    }

    private fun CategoryEntity.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("emoji", emoji); put("color", color)
        put("isSystem", isSystem); put("isActive", isActive); put("sortOrder", sortOrder)
    }

    private fun GoalEntity.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("emoji", emoji)
        put("targetKopecks", targetKopecks); put("savedKopecks", savedKopecks)
        putNullable("deadlineAt", deadlineAt)
        put("isCompleted", isCompleted); putNullable("completedAt", completedAt)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun BudgetEntity.toJson() = JSONObject().apply {
        put("id", id); put("categoryId", categoryId); put("limitKopecks", limitKopecks)
        put("period", period.name); put("isActive", isActive); put("createdAt", createdAt)
    }

    private fun TransferRouteEntity.toJson() = JSONObject().apply {
        put("id", id); put("goalId", goalId); put("matchType", matchType.name)
        put("matchValue", matchValue); put("isActive", isActive); put("createdAt", createdAt)
    }

    private fun TransactionEntity.toJson() = JSONObject().apply {
        put("id", id)
        putNullable("accountId", accountId); putNullable("categoryId", categoryId)
        put("type", type.name); put("source", source.name)
        put("amountKopecks", amountKopecks)
        putNullable("merchant", merchant); putNullable("description", description)
        put("timestamp", timestamp); putNullable("smsId", smsId)
        putNullable("goalId", goalId); putNullable("transferPairId", transferPairId)
        put("isDeleted", isDeleted); putNullable("deletedAt", deletedAt)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    // ─── JSON → Entity ────────────────────────────────────────────────────────

    private fun JSONObject.toAccount() = AccountEntity(
        id = getString("id"), name = getString("name"), bank = getString("bank"),
        cardMask = optStringOrNull("cardMask"),
        balanceKopecks = getLong("balanceKopecks"),
        currency = optString("currency", "RUB"),
        isActive = optBoolean("isActive", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun JSONObject.toCard() = CardEntity(
        id = getString("id"), accountId = getString("accountId"),
        cardMask = getString("cardMask"),
        isActive = optBoolean("isActive", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
    )

    private fun JSONObject.toCategory() = CategoryEntity(
        id = getString("id"), name = getString("name"),
        emoji = optString("emoji", "💳"), color = optString("color", "#94A3B8"),
        isSystem = optBoolean("isSystem", false),
        isActive = optBoolean("isActive", true),
        sortOrder = optInt("sortOrder", 0),
    )

    private fun JSONObject.toGoal() = GoalEntity(
        id = getString("id"), name = getString("name"), emoji = optString("emoji", "🎯"),
        targetKopecks = getLong("targetKopecks"),
        savedKopecks = optLong("savedKopecks", 0L),
        deadlineAt = optLongOrNull("deadlineAt"),
        isCompleted = optBoolean("isCompleted", false),
        completedAt = optLongOrNull("completedAt"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun JSONObject.toBudget() = BudgetEntity(
        id = getString("id"), categoryId = getString("categoryId"),
        limitKopecks = getLong("limitKopecks"),
        period = runCatching { BudgetPeriod.valueOf(optString("period", "MONTHLY")) }
            .getOrDefault(BudgetPeriod.MONTHLY),
        isActive = optBoolean("isActive", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
    )

    private fun JSONObject.toRoute() = TransferRouteEntity(
        id = getString("id"), goalId = getString("goalId"),
        matchType = runCatching { TransferMatchType.valueOf(getString("matchType")) }
            .getOrDefault(TransferMatchType.KEYWORD),
        matchValue = getString("matchValue"),
        isActive = optBoolean("isActive", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
    )

    private fun JSONObject.toTransaction() = TransactionEntity(
        id = getString("id"),
        accountId = optStringOrNull("accountId"), categoryId = optStringOrNull("categoryId"),
        type = runCatching { TransactionType.valueOf(getString("type")) }
            .getOrDefault(TransactionType.EXPENSE),
        source = runCatching { TransactionSource.valueOf(optString("source", "MANUAL")) }
            .getOrDefault(TransactionSource.MANUAL),
        amountKopecks = getLong("amountKopecks"),
        merchant = optStringOrNull("merchant"), description = optStringOrNull("description"),
        timestamp = getLong("timestamp"), smsId = optStringOrNull("smsId"),
        goalId = optStringOrNull("goalId"), transferPairId = optStringOrNull("transferPairId"),
        isDeleted = optBoolean("isDeleted", false),
        deletedAt = optLongOrNull("deletedAt"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
    )

    companion object {
        const val SCHEMA_VERSION = 1

        /** Suggested filename for the save dialog, e.g. financeos-backup-2026-06-22.fose */
        fun suggestedFileName(now: Long = System.currentTimeMillis()): String {
            val d = java.time.Instant.ofEpochMilli(now)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            return "financeos-backup-%04d-%02d-%02d.fose".format(d.year, d.monthValue, d.dayOfMonth)
        }
    }
}

// ─── org.json null-safe helpers ──────────────────────────────────────────────

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optArray(key: String): JSONArray =
    if (has(key) && !isNull(key)) optJSONArray(key) ?: JSONArray() else JSONArray()

private inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null
