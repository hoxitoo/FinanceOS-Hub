package com.financeos.hub.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.parser.ParserEngine
import dagger.hilt.android.AndroidEntryPoint
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        val entity = TransactionEntity(
            id             = UUID.randomUUID().toString(),
            accountId      = null,
            categoryId     = categoryId,
            type           = parsed.type,
            source         = TransactionSource.SMS,
            amountKopecks  = if (parsed.type == com.financeos.hub.core.database.entities.TransactionType.EXPENSE)
                -parsed.amountKopecks else parsed.amountKopecks,
            merchant       = parsed.merchant,
            description    = null,
            timestamp      = parsed.timestamp,
            smsId          = parsed.smsId,
        )
        transactionDao.insertAll(listOf(entity))
    }
}
