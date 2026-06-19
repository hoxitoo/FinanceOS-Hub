package com.financeos.hub.core.analytics

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financeos.hub.core.notifications.NotificationHelper
import com.financeos.hub.data.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AnalyticsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val analyticsEngine  : AnalyticsEngine,
    private val notificationHelper: NotificationHelper,
    private val userPreferences  : UserPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val notificationsEnabled = userPreferences.notificationsEnabled.first()

            analyticsEngine.computeScore()
            val insights = analyticsEngine.generateInsights()

            if (notificationsEnabled) {
                // Send top CRITICAL insight as notification
                insights.firstOrNull { it.severity == InsightSeverity.CRITICAL }
                    ?.let { insight -> notificationHelper.sendInsightNotification(0, insight.text) }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "fos_analytics_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AnalyticsWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
