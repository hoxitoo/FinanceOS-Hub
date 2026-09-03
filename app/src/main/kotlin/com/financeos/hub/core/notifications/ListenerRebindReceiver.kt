package com.financeos.hub.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Возвращает чтение банковских пушей после событий, которые гарантированно рвут привязку.
 *
 * Их два, и оба происходят БЕЗ участия человека в приложении:
 *
 *  - **обновление APK** (`MY_PACKAGE_REPLACED`). Приложение раздаётся файлом, обновляется часто, и
 *    каждое обновление отвязывает службу. Именно так и выглядела жалоба с устройства: разрешение на
 *    месте, в системном списке галочка стоит, а пуши не читаются со дня предыдущей установки;
 *  - **перезагрузка телефона** (`BOOT_COMPLETED`).
 *
 * Мягкой просьбы после обновления не хватает — проверено на One UI: `requestRebind` не поднимает
 * службу вовсе. Поэтому здесь идёт [ListenerHealth.forceRebind] с перезапуском компонента, но не
 * чаще раза в сутки ([ListenerHealth.mayAutoForce]) и с честным исходом: если перезапуск сбросил
 * разрешение, человек получает уведомление, а не молчание.
 */
class ListenerRebindReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return

        val app = context.applicationContext
        if (!ListenerHealth.permissionGranted(app)) return

        // goAsync даёт ~10 секунд — с запасом на обе паузы внутри forceRebind. Без него процесс
        // могли бы усыпить сразу после возврата из onReceive, и переподключение не доехало бы.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (!ListenerHealth.mayAutoForce(app)) {
                    ListenerHealth.healIfNeeded(app)
                    return@launch
                }
                ListenerHealth.markAutoForced(app)
                val outcome = ListenerHealth.forceRebind(app)
                if (outcome == ListenerHealth.RebindOutcome.PERMISSION_LOST) {
                    // Худший исход перезапуска компонента. Молчать здесь нельзя: человек не может
                    // догадаться, что доступ пропал, — операции просто перестанут появляться.
                    ListenerNotice.notifyAccessLost(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED,
        )
    }
}
