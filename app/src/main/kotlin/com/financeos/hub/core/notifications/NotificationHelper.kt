package com.financeos.hub.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.financeos.hub.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_BUDGET  = "fos_budget"
        const val CHANNEL_WEEKLY  = "fos_weekly"
        const val CHANNEL_INSIGHT = "fos_insight"

        private const val ID_BUDGET_ALERT = 1001
        private const val ID_WEEKLY       = 1002
        private const val ID_INSIGHT_BASE = 2000
    }

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
    }

    fun sendBudgetAlert(categoryName: String, spentPercent: Int) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Бюджет: $categoryName")
            .setContentText("Использовано $spentPercent% лимита")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        NotificationManagerCompat.from(context).notify(ID_BUDGET_ALERT, notification)
    }

    fun sendWeeklySummary(
        totalSpentKopecks : Long,
        comparedToLastWeek: Int,   // percent change, negative = less
    ) {
        val direction = if (comparedToLastWeek >= 0) "больше" else "меньше"
        val abs       = kotlin.math.abs(comparedToLastWeek)
        val body      = "Расходы за неделю: ${formatCompact(totalSpentKopecks)}" +
                        if (abs > 0) " ($abs% $direction прошлой недели)" else ""

        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("Еженедельный отчёт")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        NotificationManagerCompat.from(context).notify(ID_WEEKLY, notification)
    }

    fun sendInsightNotification(id: Int, text: String) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_INSIGHT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Финансовый инсайт")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        NotificationManagerCompat.from(context).notify(ID_INSIGHT_BASE + id, notification)
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
