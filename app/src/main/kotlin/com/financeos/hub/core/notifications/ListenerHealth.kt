package com.financeos.hub.core.notifications

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Жива ли на самом деле служба чтения банковских уведомлений.
 *
 * Проблема, которую это чинит: разрешение на доступ к уведомлениям и РАБОТАЮЩАЯ служба — две
 * разные вещи, и приложение путало их. Android рвёт привязку [PushNotificationListener], когда
 * переустанавливает приложение (а оно раздаётся APK-файлом, то есть при каждом обновлении),
 * когда систему перезагружают, и особенно охотно — когда производительский диспетчер питания
 * усыпляет приложение на низком заряде. Привязка при этом рвётся, а разрешение остаётся выданным.
 *
 * `NotificationManagerCompat.getEnabledListenerPackages()` читает СПИСОК РАЗРЕШЕНИЙ, поэтому
 * продолжал возвращать true, и экран настроек зелёными буквами писал «уведомления
 * обрабатываются», пока на деле не обрабатывалось ничего. Тихий отказ, который ещё и успокаивает,
 * — худший из возможных: заметить его можно только по тому, что операции перестали появляться.
 *
 * Здесь три вещи: запись фактов (когда служба подключалась, когда отвалилась, когда последний раз
 * приходил банковский пуш), просьба к системе переподключиться и честный ответ на вопрос «сейчас
 * работает или нет».
 *
 * Намеренно на `SharedPreferences`, а не на DataStore: писать надо из колбэков службы, синхронно и
 * без корутин, а читать — из composable без ожидания. DataStore здесь дал бы точку приостановки
 * ровно там, где её быть не должно.
 */
object ListenerHealth {

    private const val PREFS               = "fos_listener_health"
    private const val KEY_CONNECTED_AT    = "connected_at"
    private const val KEY_DISCONNECTED_AT = "disconnected_at"
    private const val KEY_LAST_PUSH_AT    = "last_push_at"

    /**
     * Привязана ли служба ПРЯМО СЕЙЧАС. Живёт в процессе, и это правильно: служба работает в том же
     * процессе, что и приложение, поэтому «процесс перезапустился» и «привязки нет» — одно и то же
     * состояние, и флаг честно начинает с false.
     */
    @Volatile
    private var connectedInProcess = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun component(context: Context) =
        ComponentName(context.applicationContext, PushNotificationListener::class.java)

    // ── Факты ────────────────────────────────────────────────────────────────

    fun markConnected(context: Context) {
        connectedInProcess = true
        prefs(context).edit().putLong(KEY_CONNECTED_AT, System.currentTimeMillis()).apply()
    }

    fun markDisconnected(context: Context) {
        connectedInProcess = false
        prefs(context).edit().putLong(KEY_DISCONNECTED_AT, System.currentTimeMillis()).apply()
    }

    /**
     * Отметка «служба жива»: ставится на ЛЮБОЙ пуш от банка, даже если он оказался рекламой и в
     * операции не превратился. Признак жизни — то, что уведомление вообще дошло до службы, а не то,
     * что из него получилась строка в истории.
     */
    fun markPushSeen(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_PUSH_AT, System.currentTimeMillis()).apply()
    }

    fun isConnected(): Boolean = connectedInProcess

    /** 0 — не было ни разу. */
    fun connectedAt(context: Context): Long    = prefs(context).getLong(KEY_CONNECTED_AT, 0L)
    fun disconnectedAt(context: Context): Long = prefs(context).getLong(KEY_DISCONNECTED_AT, 0L)
    fun lastPushAt(context: Context): Long     = prefs(context).getLong(KEY_LAST_PUSH_AT, 0L)

    fun permissionGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    // ── Починка ──────────────────────────────────────────────────────────────

    /**
     * Просьба к системе снова привязать службу. Ровно то, что рекомендует документация Android
     * после потери привязки, и единственный способ починки БЕЗ побочных эффектов: если служба уже
     * привязана, вызов ничего не делает, если разрешения нет — тихо не срабатывает.
     *
     * Намеренно НЕ трогаем `setComponentEnabledSetting`: погасить и снова зажечь компонент —
     * известный способ добиться перепривязки, но на части прошивок он выбивает приложение из списка
     * доступа к уведомлениям, и тогда молчаливая поломка становится необратимой без участия
     * пользователя. Менять восстановимую проблему на невосстановимую нельзя. Если `requestRebind`
     * не помог, экран настроек предлагает открыть системный список — там переключатель чинит всё
     * гарантированно, и делает это осознанно человек.
     */
    fun requestRebind(context: Context) {
        runCatching { NotificationListenerService.requestRebind(component(context)) }
    }

    /**
     * Самовосстановление при открытии приложения и по расписанию: если разрешение есть, а привязки
     * нет — попросить систему привязать службу обратно.
     */
    fun healIfNeeded(context: Context) {
        if (!permissionGranted(context)) return
        if (connectedInProcess) return
        requestRebind(context)
    }

    /**
     * Компонент службы обязан быть включён. Вызывается на старте и идемпотентен — страховка на
     * случай, если он когда-либо окажется выключен: выключенный компонент система не привяжет
     * никогда, и никакой `requestRebind` этого не исправит.
     */
    fun ensureComponentEnabled(context: Context) {
        runCatching {
            context.applicationContext.packageManager.setComponentEnabledSetting(
                component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
