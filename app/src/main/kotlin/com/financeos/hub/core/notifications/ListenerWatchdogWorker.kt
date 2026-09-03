package com.financeos.hub.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Сторож привязки службы уведомлений.
 *
 * Проверка уже была — раз в 12 часов, попутно в проверяльщике обновлений. Полсуток слишком много:
 * ровно столько времени операции могут не появляться, и человек об этом не узнает, потому что
 * ошибки нет — есть тишина. Час ближе к цене вопроса, а сама проверка почти ничего не стоит:
 * если привязка на месте, работы нет вообще.
 *
 * Здесь только МЯГКАЯ просьба. Жёсткий перезапуск компонента иногда сбрасывает разрешение, и
 * повторять такое ежечасно нельзя ни при каких обстоятельствах — он делается один раз после
 * обновления приложения ([ListenerRebindReceiver]) и по явному нажатию человека.
 */
class ListenerWatchdogWorker(
    appContext: Context,
    params    : WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ListenerHealth.ensureComponentEnabled(applicationContext)
        ListenerHealth.healIfNeeded(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "fos_listener_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ListenerWatchdogWorker>(1, TimeUnit.HOURS)
                .build()
            // KEEP, а не UPDATE: пересоздание расписания при каждом запуске приложения сбрасывало
            // бы отсчёт периода, и у человека, который открывает приложение часто, сторож не
            // срабатывал бы никогда.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
