package com.financeos.hub.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Единственное уведомление, которое приложение шлёт про самого себя: «доступ к уведомлениям
 * пропал».
 *
 * Живёт отдельно от [NotificationHelper] намеренно. Тот внедряется через Hilt и предполагает живой
 * граф зависимостей, а это сообщение нужно ровно там, где графа может не быть: в
 * [ListenerRebindReceiver], поднятом системой после обновления приложения.
 *
 * Отправляется только в одном случае — когда перезапуск компонента службы сбросил разрешение.
 * Такое молчание было бы худшим из возможных: человек не может догадаться, что доступ исчез,
 * потому что операции не появляются с ошибкой, они просто не появляются.
 */
object ListenerNotice {

    private const val ID_ACCESS_LOST = 5001

    @SuppressLint("MissingPermission")
    fun notifyAccessLost(context: Context) {
        if (!canPost(context)) return

        val text = "Android сбросил доступ FinanceOS к уведомлениям — банковские пуши сейчас " +
            "не читаются. Нажмите, чтобы включить обратно."

        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_INSIGHT)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Пуши банков не читаются")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(ID_ACCESS_LOST, notification)
        }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
