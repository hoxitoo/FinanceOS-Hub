package com.financeos.hub.core.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financeos.hub.core.notifications.NotificationHelper
import com.financeos.hub.data.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background check for a newer GitHub release.
 *
 * When one is found and the user hasn't already been notified about that exact version, fires a
 * push via [NotificationHelper.sendUpdateAvailable] that deep-links to Settings → «ОБНОВЛЕНИЯ», where
 * the existing in-app download/install flow takes over. The worker NEVER downloads or installs by
 * itself — it only surfaces the prompt, so no large transfer happens without the user's consent.
 *
 * This is the only background network use in the app; it inherits the same offline-respecting posture
 * as [UpdateChecker] (read-only release lookup, no user data leaves the device) and runs only when a
 * network is available (constraint) and the «Уведомлять о новой версии» toggle is on.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updateChecker     : UpdateChecker,
    private val notificationHelper: NotificationHelper,
    private val userPreferences   : UserPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Respect the user's opt-out — return success (not retry) so WorkManager keeps the
            // periodic schedule alive for when they turn it back on, without doing any network work.
            if (!userPreferences.updateNotificationsEnabled.first()) return Result.success()

            when (val result = updateChecker.check()) {
                is UpdateChecker.CheckResult.Available -> {
                    val version = result.release.versionName
                    // De-dupe: don't re-notify every cycle for an update the user already saw.
                    if (userPreferences.lastNotifiedVersion.first() != version) {
                        notificationHelper.sendUpdateAvailable(version, result.release.notes)
                        userPreferences.setLastNotifiedVersion(version)
                    }
                    Result.success()
                }
                // Up-to-date → the user has caught up. Reconcile the sticky de-dupe marker to the
                // installed version so it never holds a stale value: otherwise a future release that
                // happened to reuse a previously-notified version string could be wrongly suppressed.
                is UpdateChecker.CheckResult.UpToDate -> {
                    val current = updateChecker.currentVersion
                    if (userPreferences.lastNotifiedVersion.first() != current) {
                        userPreferences.setLastNotifiedVersion(current)
                    }
                    Result.success()
                }
                // Error (no releases yet, rate limit, transient network) → succeed and wait for the
                // next 12 h cycle rather than Result.retry(), which would hammer the network every
                // backoff interval on a repo that simply has no release — at odds with the app's
                // otherwise fully-offline posture. A missed update just surfaces one cycle later.
                is UpdateChecker.CheckResult.Error    -> Result.success()
            }
        } catch (e: Exception) {
            // Cooperative cancellation must propagate; swallowing it would make WorkManager retry
            // a job the framework deliberately cancelled.
            if (e is CancellationException) throw e
            Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "fos_update_check_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                repeatInterval = 12,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
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
