package com.financeos.hub.core.notifications

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.ParserEngine
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

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        android.util.Log.e("PushListener", "Push processing failed", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val sender = PACKAGE_TO_SENDER[sbn.packageName] ?: return
        scope.launch {
            if (!userPreferences.pushListenerEnabled.first()) return@launch
            val extras = sbn.notification?.extras ?: return@launch
            val title  = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: ""
            val text   = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            // Some banks put the amount in the title, details in text — join both
            val body   = if (text.isNotBlank()) "$title $text" else title
            if (body.isBlank()) return@launch
            processPush(sender, body, sbn.postTime)
        }
    }

    private suspend fun processPush(sender: String, body: String, ts: Long) {
        val parsed = parserEngine.parse(sender, body, ts) ?: return
        val pushId = "push_${sender}_${ts}_${body.hashCode()}"
        if (pushId in transactionDao.getAllSmsHashes()) return

        val categoryId = classifier.classify(parsed.merchant, null)
        transactionDao.insertAll(
            listOf(
                TransactionEntity(
                    id            = UUID.randomUUID().toString(),
                    accountId     = null,
                    categoryId    = categoryId,
                    type          = parsed.type,
                    source        = TransactionSource.PUSH,
                    amountKopecks = if (parsed.type == TransactionType.EXPENSE)
                        -parsed.amountKopecks else parsed.amountKopecks,
                    merchant      = parsed.merchant,
                    description   = null,
                    timestamp     = ts,
                    smsId         = pushId,
                )
            )
        )
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
        )

        fun isPermissionGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
    }
}
