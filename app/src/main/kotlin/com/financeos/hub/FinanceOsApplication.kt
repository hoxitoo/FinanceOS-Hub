package com.financeos.hub

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.financeos.hub.core.analytics.AnalyticsWorker
import com.financeos.hub.core.calendar.ObligationSyncer
import com.financeos.hub.core.notifications.NotificationHelper
import com.financeos.hub.core.update.UpdateCheckWorker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FinanceOsApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory      : HiltWorkerFactory
    @Inject lateinit var notificationHelper : NotificationHelper
    @Inject lateinit var obligationSyncer   : ObligationSyncer

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        notificationHelper.createChannels()
        AnalyticsWorker.schedule(this)
        UpdateCheckWorker.schedule(this)
        // Сопоставление обязательств — работа приложения, а не открытого календаря: отметка нужна
        // плитке на главной и «Свободно» даже тогда, когда на календарь никто не заходил.
        obligationSyncer.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
