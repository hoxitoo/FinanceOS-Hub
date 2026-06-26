package com.financeos.hub.core.notifications

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.financeos.hub.MainActivity
import com.financeos.hub.R
import com.financeos.hub.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catMode = AtomicBoolean(false)

    // Cat-face bitmap, decoded once on first use. `lazy` is thread-safe by default
    // (LazyThreadSafetyMode.SYNCHRONIZED), preventing the double-decode race that
    // @Volatile alone cannot prevent.
    private val catLargeIconCache: Bitmap? by lazy {
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.cat_face_mono)
        }.getOrNull()
    }

    init {
        scope.launch { prefs.catModeEnabled.collect { catMode.set(it) } }
    }

    /** Prepends 🐱 to the title when Cat Mode is active. */
    private fun title(text: String): String =
        if (catMode.get()) "🐱 $text" else text

    /** Returns the cat-face bitmap when Cat Mode is on, else null (no large icon). */
    private fun catLargeIcon(): Bitmap? =
        if (catMode.get()) catLargeIconCache else null
    companion object {
        const val CHANNEL_BUDGET  = "fos_budget"
        const val CHANNEL_WEEKLY  = "fos_weekly"
        const val CHANNEL_INSIGHT = "fos_insight"
        const val CHANNEL_UPDATE  = "fos_update"

        const val EXTRA_ROUTE = "fos_route"

        private const val ID_BUDGET_ALERT = 1001
        private const val ID_WEEKLY       = 1002
        private const val ID_INSIGHT_BASE = 2000
        private const val ID_TRANSFER     = 3001
        private const val ID_UPDATE       = 4001
    }

    private fun deepLinkIntent(route: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            route.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ROUTE, route)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUDGET,
                "Бюджет",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Оповещения о превышении бюджета" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WEEKLY,
                "Еженедельный отчёт",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Сводка расходов за неделю" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INSIGHT,
                "Инсайты",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Финансовые наблюдения и рекомендации" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATE,
                "Обновления",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Уведомления о новой версии приложения" }
        )
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Guarded by hasNotificationPermission(); lint can't trace the helper.
    @SuppressLint("MissingPermission")
    fun sendBudgetAlert(categoryName: String, spentPercent: Int) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setLargeIcon(catLargeIcon())
            .setContentTitle(title("Бюджет: $categoryName"))
            .setContentText("Использовано $spentPercent% лимита")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(deepLinkIntent("budget"))
            .build()

        NotificationManagerCompat.from(context).notify(ID_BUDGET_ALERT, notification)
    }

    @SuppressLint("MissingPermission")
    fun sendWeeklySummary(
        totalSpentKopecks : Long,
        comparedToLastWeek: Int,
    ) {
        if (!hasNotificationPermission()) return
        val direction = if (comparedToLastWeek >= 0) "больше" else "меньше"
        val abs       = kotlin.math.abs(comparedToLastWeek)
        val body      = "Расходы за неделю: ${formatCompact(totalSpentKopecks)}" +
                        if (abs > 0) " ($abs% $direction прошлой недели)" else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setLargeIcon(catLargeIcon())
            .setContentTitle(title("Еженедельный отчёт"))
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(deepLinkIntent("analytics"))
            .build()

        NotificationManagerCompat.from(context).notify(ID_WEEKLY, notification)
    }

    @SuppressLint("MissingPermission")
    fun sendInsightNotification(id: Int, text: String) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_INSIGHT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(catLargeIcon())
            .setContentTitle(title("Финансовый инсайт"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(deepLinkIntent("analytics"))
            .build()

        NotificationManagerCompat.from(context).notify(ID_INSIGHT_BASE + id, notification)
    }

    /**
     * Fired when an outgoing transfer wasn't auto-routed (no matching own-account counterpart and no
     * goal link). It may be money sent to someone (a расход) or a savings contribution — the user
     * decides, so this deep-links to the operations list where the transaction's type can be changed.
     */
    @SuppressLint("MissingPermission")
    fun notifyUnroutedTransfer(magnitudeKopecks: Long) {
        if (!hasNotificationPermission()) return
        val amount = com.financeos.hub.ui.theme.FosFormatter.amount(magnitudeKopecks)
        val text = "Перевод $amount — это расход или в накопительную цель? Нажмите, чтобы выбрать."
        val notification = NotificationCompat.Builder(context, CHANNEL_INSIGHT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(catLargeIcon())
            .setContentTitle(title("Перевод не распределён"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(deepLinkIntent("transactions"))
            .build()

        NotificationManagerCompat.from(context).notify(ID_TRANSFER, notification)
    }

    /** Fired by [UpdateCheckWorker] when a newer GitHub release is found. Deep-links to Settings. */
    @SuppressLint("MissingPermission")
    fun sendUpdateAvailable(versionName: String, notes: String) {
        if (!hasNotificationPermission()) return
        val body = buildString {
            append("Доступна версия $versionName. Откройте настройки, чтобы обновить.")
            if (notes.isNotBlank()) append("\n\n").append(notes.take(240))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setLargeIcon(catLargeIcon())
            .setContentTitle(title("Новая версия FinanceOS"))
            .setContentText("Доступна версия $versionName — нажмите, чтобы обновить")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(deepLinkIntent("settings"))
            .build()

        NotificationManagerCompat.from(context).notify(ID_UPDATE, notification)
    }

    private fun formatCompact(kopecks: Long): String {
        val rub = kopecks / 100.0
        return when {
            rub >= 1_000_000 -> "${(rub / 1_000_000).toInt()} млн ₽"
            rub >= 1_000     -> "${(rub / 1_000).toInt()} тыс ₽"
            else             -> "${rub.toInt()} ₽"
        }
    }
}
