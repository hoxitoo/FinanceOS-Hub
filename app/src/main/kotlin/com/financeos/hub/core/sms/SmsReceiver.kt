package com.financeos.hub.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.parser.ParserEngine
import com.financeos.hub.core.transfer.TransferRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var parserEngine: ParserEngine
    @Inject lateinit var transactionDao: TransactionDao
    @Inject lateinit var classifier: CategoryClassifier
    @Inject lateinit var transferRouter: TransferRouter
    @Inject lateinit var accountLinker: AccountLinker

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        android.util.Log.e("SmsReceiver", "SMS processing failed", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages.forEach { sms ->
            val sender = sms.originatingAddress ?: return@forEach
            val body   = sms.messageBody ?: return@forEach
            val ts     = sms.timestampMillis
            scope.launch {
                processSms(sender, body, ts)
            }
        }
    }

    private suspend fun processSms(sender: String, body: String, ts: Long) {
        val parsed = parserEngine.parse(sender, body, ts) ?: return
        val known  = transactionDao.getAllSmsHashes()
        if (parsed.smsId in known) return

        val categoryId = classifier.classify(parsed.merchant, null)
        val accountId  = accountLinker.resolveAccountId(parsed.cardMask)
        val entity = TransactionEntity(
            id             = UUID.randomUUID().toString(),
            accountId      = accountId,
            categoryId     = categoryId,
            type           = parsed.type,
            source         = TransactionSource.SMS,
            amountKopecks  = parsed.signedKopecks(),
            merchant       = parsed.merchant,
            description    = null,
            timestamp      = parsed.timestamp,
            smsId          = parsed.smsId,
        )
        val rowIds = transactionDao.insertAll(listOf(entity))
        if (rowIds.firstOrNull() != -1L) {
            accountLinker.syncBalance(accountId, parsed.balanceKopecks, entity.amountKopecks)
            transferRouter.onTransactionInserted(entity, parsed.rawSms, parsed.counterpartyMask)
        }
    }
}
