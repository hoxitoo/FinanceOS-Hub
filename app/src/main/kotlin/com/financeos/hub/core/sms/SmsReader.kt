package com.financeos.hub.core.sms

import android.content.Context
import android.provider.Telephony
import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.parser.ParserEngine
import com.financeos.hub.core.transfer.TransferRouter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject

data class ImportProgress(
    val processed: Int,
    val imported: Int,
    val total: Int,
    val done: Boolean = false,
)

class SmsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parserEngine: ParserEngine,
    private val transactionDao: TransactionDao,
    private val classifier: CategoryClassifier,
    private val transferRouter: TransferRouter,
    private val accountLinker: AccountLinker,
) {
    fun importLast90Days(): Flow<ImportProgress> = flow {
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val knownIds = transactionDao.getAllSmsHashes().toHashSet()

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            ),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(cutoff.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return@flow

        val total = cursor.count
        var processed = 0
        var imported  = 0

        cursor.use {
            val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val sender = it.getString(addrIdx) ?: continue
                val body   = it.getString(bodyIdx) ?: continue
                val ts     = it.getLong(dateIdx)

                // Guard each row: a single malformed SMS must not abort the whole import.
                runCatching {
                    val parsed = parserEngine.parse(sender, body, ts)
                    // Cross-channel dedup: skip if a PUSH transaction with the same amount already
                    // arrived within ±5 minutes (same bank event delivered via push before SMS).
                    val window = 5 * 60 * 1000L
                    if (parsed != null && parsed.smsId !in knownIds &&
                        !transactionDao.existsSimilarSmsOrPush(parsed.amountKopecks, parsed.timestamp - window, parsed.timestamp + window)) {
                        val categoryId = classifier.classify(parsed.merchant, null)
                        val accountId  = accountLinker.resolveAccountId(parsed.cardMask, parsed.bankId)
                        val entity = TransactionEntity(
                            id            = UUID.randomUUID().toString(),
                            accountId     = accountId,
                            categoryId    = categoryId,
                            type          = parsed.type,
                            source        = TransactionSource.SMS,
                            amountKopecks = parsed.signedKopecks(),
                            merchant      = parsed.merchant,
                            description   = null,
                            timestamp     = parsed.timestamp,
                            smsId         = parsed.smsId,
                        )
                        val rowIds = transactionDao.insertAll(listOf(entity))
                        knownIds.add(parsed.smsId)  // always add so next loop iteration skips it
                        if (rowIds.firstOrNull() != -1L) {
                            accountLinker.syncBalance(accountId, parsed.balanceKopecks, entity.amountKopecks)
                            transferRouter.onTransactionInserted(entity, parsed.rawSms, parsed.counterpartyMask)
                            imported++
                        }
                    }
                }
                processed++
                emit(ImportProgress(processed, imported, total))
            }
        }
        emit(ImportProgress(processed, imported, total, done = true))
    }
}
