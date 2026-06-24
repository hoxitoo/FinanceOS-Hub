package com.financeos.hub.core.notifications

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
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
class PushNotificationListener : NotificationListenerService() {

    @Inject lateinit var parserEngine   : ParserEngine
    @Inject lateinit var transactionDao : TransactionDao
    @Inject lateinit var classifier     : CategoryClassifier
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var transferRouter : TransferRouter
    @Inject lateinit var accountLinker  : AccountLinker

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        android.util.Log.e("PushListener", "Push processing failed", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val sender = PACKAGE_TO_SENDER[sbn.packageName] ?: return
        scope.launch {
            if (!userPreferences.pushListenerEnabled.first()) return@launch
            val extras = sbn.notification?.extras ?: return@launch
            val title   = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: ""
            val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            // The expanded ("big text") view carries the full body — e.g. the
            // "Остаток: … ; ··2548" line that the collapsed EXTRA_TEXT omits. Prefer it so
            // the balance and destination card mask are available for account linking.
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""
            val detail  = if (bigText.length > text.length) bigText else text
            // Some banks put the amount in the title, details in text — join both
            val body    = listOf(title, detail).filter { it.isNotBlank() }.joinToString(" ")
            if (body.isBlank()) return@launch
            processPush(sender, body, sbn.postTime)
        }
    }

    private suspend fun processPush(sender: String, body: String, ts: Long) {
        val parsed = parserEngine.parse(sender, body, ts) ?: return
        val pushId = "push_${sender}_${ts}_${body.hashCode()}"
        if (transactionDao.existsBySmsId(pushId)) return
        // Cross-channel dedup: skip if an SMS transaction with the same amount already arrived
        // within ±5 minutes (same bank event delivered via both SMS and push notification).
        val window = 5 * 60 * 1000L
        if (transactionDao.existsSimilarSmsOrPush(parsed.amountKopecks, ts - window, ts + window)) return

        val categoryId = classifier.classify(parsed.merchant, null)
            ?: CategoryDefaults.forType(parsed.type)
        val accountId  = accountLinker.resolveAccountId(parsed.cardMask, parsed.bankId)
        val entity = TransactionEntity(
            id            = UUID.randomUUID().toString(),
            accountId     = accountId,
            categoryId    = categoryId,
            type          = parsed.type,
            source        = TransactionSource.PUSH,
            amountKopecks = parsed.signedKopecks(),
            merchant      = parsed.merchant,
            description   = null,
            timestamp     = ts,
            smsId         = pushId,
            sourceMask       = parsed.cardMask,
            counterpartyMask = parsed.counterpartyMask,
        )
        val rowIds = transactionDao.insertAll(listOf(entity))
        if (rowIds.firstOrNull() != -1L) {
            accountLinker.syncBalance(accountId, parsed.balanceKopecks, entity.amountKopecks)
            transferRouter.onTransactionInserted(entity, parsed.rawSms, parsed.counterpartyMask)
        }
    }

    companion object {
        /** Banking app package names → synthetic sender id recognised by each BankParser */
        val PACKAGE_TO_SENDER: Map<String, String> = mapOf(
            // P1
            "ru.sberbankmobile"                 to "SBERBANK",
            "ru.sberbank.sbbol"                 to "SBERBANK",
            "ru.tinkoff.cardsnew"               to "TINKOFF",
            "com.idamob.tinkoff.android"         to "TINKOFF",
            "ru.vtb24.mobilebanking.android"     to "VTB",
            "ru.vtb24.android"                  to "VTB",
            "ru.alfabank.mobile.android"         to "ALFABANK",
            "ru.alfabank.oavdo.amc"             to "ALFABANK",
            "ru.gazprombank.android.mobilebank"  to "GAZPROMBANK",
            // P2
            "ru.raiffeisenmobile.android"        to "RAIFFEISEN",
            "com.raiffeisenmobile.android"       to "RAIFFEISEN",
            "ru.rosbank.android"                 to "ROSBANK",
            "ru.ftc.otkritie"                   to "OTKRITIE",
            "ru.openbank.mobile"                 to "OTKRITIE",
            // P3
            "ru.mtsbank.mobilebank"             to "MTSB",
            "ru.pochtabank.android"             to "POSTABANK",
            "ru.rshb.mbank"                     to "RSHB",
            // KG
            "com.maanavan.mb_kyrgyzstan"        to "MBANK",
            // P4
            "ru.mkb.mobilebank"                 to "MKB",
            "ru.mkb.android"                    to "MKB",
        )

        fun isPermissionGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
    }
}
