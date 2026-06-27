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
import kotlinx.coroutines.cancel
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

    override fun onDestroy() {
        super.onDestroy()
        // Cancel in-flight coroutines when the service is torn down (e.g. permission revoked
        // or system kill/restart). Without this, the SupervisorJob stays live indefinitely and
        // any in-flight suspend calls keep references to DAOs/repos after the service is gone.
        scope.cancel()
    }

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
        if (transactionDao.existsBySmsId(pushId)) {
            // Exact re-post of a notification we already stored — still apply its balance, because
            // the first delivery may have lacked the "Остаток" line this copy carries.
            accountLinker.applyAuthoritativeBalance(parsed.cardMask, parsed.balanceKopecks)
            return
        }
        // Cross-channel dedup: skip if an SMS/PUSH transaction with the same amount already arrived
        // within ±5 minutes (same bank event delivered via both channels, or a collapsed→expanded
        // notification re-post). The dropped copy is often the RICHER one (it carries the authoritative
        // "Остаток" + card mask the first copy lacked), so apply its balance before bailing — otherwise
        // the figure is lost forever and the account freezes at the last fully-delivered value.
        val window = 5 * 60 * 1000L
        if (transactionDao.existsSimilarSmsOrPush(parsed.amountKopecks, ts - window, ts + window)) {
            accountLinker.applyAuthoritativeBalance(parsed.cardMask, parsed.balanceKopecks)
            return
        }

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
            balanceKopecks   = parsed.balanceKopecks,
            currency         = parsed.currency,
        )
        val rowIds = transactionDao.insertAll(listOf(entity))
        if (rowIds.firstOrNull() != -1L) {
            accountLinker.syncBalance(accountId, parsed.balanceKopecks, entity.amountKopecks)
            transferRouter.onTransactionInserted(entity, parsed.rawSms, parsed.counterpartyMask)
            // Self-heal even when THIS row didn't resolve an account at insert (e.g. the bank-name
            // fallback was ambiguous): resolve the owning account from the card mask so an orphaned
            // debit on a registered card still adopts earlier orphans and snaps to the latest "Остаток".
            val healId = accountId ?: accountLinker.resolveAccountByCardMask(parsed.cardMask)
            if (healId != null) accountLinker.reconcileAccount(healId)
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
