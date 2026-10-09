package com.financeos.hub.core.invest

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financeos.hub.data.repositories.MarketQuotesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Котировки Мосбиржи раз в сутки (#55), только при сети. Выключенный переключатель — ничего не
 * делает и не уходит в сеть; расписание остаётся, чтобы включение сработало без перезапуска.
 */
@HiltWorker
class MarketQuotesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val quotes: MarketQuotesRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        quotes.refresh()
        Result.success()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        // Сбой сети — следующий раз через сутки; повтор с отступами долбил бы биржу впустую.
        Result.success()
    }

    companion object {
        private const val WORK_NAME = "fos_market_quotes_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MarketQuotesWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
