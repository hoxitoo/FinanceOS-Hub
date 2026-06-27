package com.financeos.hub.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.classifier.CategoryDefaults
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.parser.ParserEngine
import com.financeos.hub.core.transfer.TransferRouter
import com.financeos.hub.data.preferences.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
    @Inject lateinit var prefs: UserPreferences

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        android.util.Log.e("SmsReceiver", "SMS processing failed", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.takeIf { it.isNotEmpty() } ?: return
        // goAsync() tells Android not to kill the process until pendingResult.finish() is called,
        // giving the coroutine time to write to the DB even when the app is in the background.
        val pendingResult = goAsync()
        scope.launch {
            try {
                // Honour the user's choice — real-time capture is opt-in. A fresh install
                // (toggle off) never ingests SMS until the user enables it or runs an import.
                if (!prefs.smsRealtimeEnabled.first()) return@launch
                messages.forEach { sms ->
                    val sender = sms.originatingAddress ?: return@forEach
                    val body   = sms.messageBody ?: return@forEach
                    val ts     = sms.timestampMillis
                    processSms(sender, body, ts)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processSms(sender: String, body: String, ts: Long) {
        val parsed = parserEngine.parse(sender, body, ts) ?: return
        if (transactionDao.existsBySmsId(parsed.smsId)) {
            accountLinker.applyAuthoritativeBalance(parsed.cardMask, parsed.balanceKopecks)
            return
        }
        // Cross-channel dedup: if the same bank event was already ingested via push (arrived
        // slightly before the SMS), skip rather than double-insert. Uses a ±5 min window on the
        // absolute amount — the same guard used in SmsReader and PushNotificationListener. The
        // dropped twin may carry the authoritative "Остаток" the kept copy lacked, so apply its
        // balance before bailing rather than discarding it.
        val window = 5 * 60 * 1000L
        if (transactionDao.existsSimilarSmsOrPush(parsed.amountKopecks, ts - window, ts + window)) {
            accountLinker.applyAuthoritativeBalance(parsed.cardMask, parsed.balanceKopecks)
            return
        }

        val categoryId = classifier.classify(parsed.merchant, null)
            ?: CategoryDefaults.forType(parsed.type)
        val accountId  = accountLinker.resolveAccountId(parsed.cardMask, parsed.bankId)
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
            sourceMask       = parsed.cardMask,
            counterpartyMask = parsed.counterpartyMask,
            balanceKopecks   = parsed.balanceKopecks,
            currency         = parsed.currency,
        )
        val rowIds = transactionDao.insertAll(listOf(entity))
        if (rowIds.firstOrNull() != -1L) {
            accountLinker.syncBalance(accountId, parsed.balanceKopecks, entity.amountKopecks)
            transferRouter.onTransactionInserted(entity, parsed.rawSms, parsed.counterpartyMask)
            // Self-heal even when THIS row didn't resolve an account at insert: resolve the owning
            // account from the card mask so an orphaned debit on a registered card still adopts
            // earlier orphans and snaps the balance to the bank's latest "Остаток".
            val healId = accountId ?: accountLinker.resolveAccountByCardMask(parsed.cardMask)
            if (healId != null) accountLinker.reconcileAccount(healId)
        }
    }
}
