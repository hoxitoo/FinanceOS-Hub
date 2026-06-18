package com.financeos.hub.core.sms

import android.content.Context
import android.provider.Telephony
import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.ParserEngine
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

                val parsed = parserEngine.parse(sender, body, ts)
                if (parsed != null && parsed.smsId !in knownIds) {
                    val categoryId = classifier.classify(parsed.merchant, null)
                    val entity = TransactionEntity(
                        id            = UUID.randomUUID().toString(),
                        accountId     = null,
                        categoryId    = categoryId,
                        type          = parsed.type,
                        source        = TransactionSource.SMS,
                        amountKopecks = if (parsed.type == TransactionType.EXPENSE)
                            -parsed.amountKopecks else parsed.amountKopecks,
                        merchant      = parsed.merchant,
                        description   = null,
                        timestamp     = parsed.timestamp,
                        smsId         = parsed.smsId,
                    )
                    transactionDao.insertAll(listOf(entity))
                    knownIds.add(parsed.smsId)
                    imported++
                }
                processed++
                emit(ImportProgress(processed, imported, total))
            }
        }
        emit(ImportProgress(processed, imported, total, done = true))
    }
}
