package com.financeos.hub.core.notifications

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay

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
    private const val KEY_LAST_FORCE_AT   = "last_force_at"

    /** Сколько ждать после мягкой просьбы: система привязывает службу асинхронно. */
    private const val SOFT_WAIT_MS = 1_200L
    /** После перезапуска компонента системе нужно заметно больше времени. */
    private const val HARD_WAIT_MS = 2_500L
    /** Автоматический жёсткий перезапуск — не чаще раза в сутки. */
    private const val AUTO_FORCE_COOLDOWN_MS = 24L * 60 * 60 * 1000

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
     * Мягкая просьба к системе привязать службу обратно. Без побочных эффектов: если служба уже
     * привязана, вызов ничего не делает; если разрешения нет — тихо не срабатывает.
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

    // ── Жёсткое переподключение ──────────────────────────────────────────────

    enum class RebindOutcome {
        /** Служба поднялась — пуши снова читаются. */
        RECONNECTED,
        /** Разрешения нет: чинить нечего, нужен системный список. */
        NO_PERMISSION,
        /** Разрешение исчезло ПОСЛЕ перезапуска компонента — редкий, но известный исход. */
        PERMISSION_LOST,
        /** Ничего не помогло; остаётся системный переключатель. */
        STILL_DOWN,
    }

    /**
     * Две попытки подряд: сначала мягкая, потом перезапуск компонента.
     *
     * Раньше здесь стоял только [requestRebind], и в комментарии было написано, что
     * `setComponentEnabledSetting` трогать нельзя — на части прошивок он выбивает приложение из
     * списка доступа к уведомлениям. Осторожность оказалась дороже проблемы: на реальном устройстве
     * (One UI) после обновления APK мягкая просьба не поднимает службу ВООБЩЕ, и «Переподключить»
     * молча ничего не делало. Неработающая кнопка — тоже необратимая поломка, только каждый раз.
     *
     * Поэтому перезапуск компонента здесь есть, но обставлен так, что худший исход виден:
     *  - он идёт ВТОРЫМ, только когда мягкая просьба не помогла;
     *  - разрешение проверяется ПОСЛЕ и, если оно пропало, вызывающий об этом узнаёт
     *    ([PERMISSION_LOST]) и говорит человеку, а не оставляет тихо сломанным.
     *
     * Паузы обязательны: система привязывает службу асинхронно и вызывает `onListenerConnected`
     * позже. Без ожидания ответ «не помогло» был бы неправдой в большинстве случаев.
     */
    suspend fun forceRebind(context: Context): RebindOutcome {
        if (!permissionGranted(context)) return RebindOutcome.NO_PERMISSION

        ensureComponentEnabled(context)
        requestRebind(context)
        delay(SOFT_WAIT_MS)
        if (connectedInProcess) return RebindOutcome.RECONNECTED

        restartComponent(context)
        requestRebind(context)
        delay(HARD_WAIT_MS)

        return when {
            connectedInProcess            -> RebindOutcome.RECONNECTED
            !permissionGranted(context)   -> RebindOutcome.PERMISSION_LOST
            else                          -> RebindOutcome.STILL_DOWN
        }
    }

    /**
     * Погасить и сразу зажечь компонент службы — то, что система воспринимает как «появился новый
     * слушатель» и привязывает заново.
     *
     * Включение идёт в `finally`: если между двумя вызовами что-то бросит, выключенный компонент
     * остался бы выключенным навсегда, а выключенный компонент не привяжется никогда и никаким
     * `requestRebind` этого не исправить.
     */
    private fun restartComponent(context: Context) {
        val pm = context.applicationContext.packageManager
        try {
            runCatching {
                pm.setComponentEnabledSetting(
                    component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        } finally {
            ensureComponentEnabled(context)
        }
    }

    /**
     * Автоматический жёсткий перезапуск — не чаще раза в сутки.
     *
     * Ограничение по частоте не про батарею, а про доверие: перезапуск компонента иногда сбрасывает
     * разрешение, и повторять такое в цикле нельзя. Одной попытки после обновления приложения
     * достаточно, дальше решает человек.
     */
    fun mayAutoForce(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_FORCE_AT, 0L)
        return System.currentTimeMillis() - last > AUTO_FORCE_COOLDOWN_MS
    }

    fun markAutoForced(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_FORCE_AT, System.currentTimeMillis()).apply()
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
