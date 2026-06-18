package com.financeos.hub

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.financeos.hub.core.analytics.AnalyticsWorker
import com.financeos.hub.core.notifications.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FinanceOsApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory      : HiltWorkerFactory
    @Inject lateinit var notificationHelper : NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        AnalyticsWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
