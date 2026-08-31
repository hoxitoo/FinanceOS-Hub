package com.financeos.hub.core.calendar

import com.financeos.hub.data.repositories.PlannedPaymentRepository
import com.financeos.hub.data.repositories.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отмечает обязательства закрытыми, когда в истории появляется подходящая операция.
 *
 * Живёт в приложении, а не во ViewModel экрана, по двум причинам.
 *
 * **Это запись, а не отображение.** Результат чистой функции исчезает вместе с экраном: считай мы
 * сопоставление при построении календаря, обязательство оставалось бы незакрытым до тех пор, пока
 * на календарь кто-нибудь не посмотрит. Отметка нужна и плитке на главной, и виджету, и самому
 * «Свободно» — то есть местам, где календарь не открыт.
 *
 * **Экземпляр должен быть один.** ViewModel привязана к своему `NavBackStackEntry`, и у главной с
 * календарём они разные: сборщик во ViewModel запускался бы дважды и писал бы одно и то же в две
 * руки. `@Singleton` со стартом из `Application` решает это раз и навсегда.
 *
 * Цикл «запись → перечитывание → запись» конечен: закрытая дата уходит из [ObligationMatcher
 * .openDueDates], следующий проход не находит для неё кандидата и ничего не пишет. Уже занятые
 * операции исключаются заранее, иначе один платёж закрывал бы соседние обязательства по кругу.
 */
@Singleton
class ObligationSyncer @Inject constructor(
    private val plannedRepo: PlannedPaymentRepository,
    private val txRepo     : TransactionRepository,
) {
    private val zone = ZoneId.systemDefault()

    /** Пережидает уходы экранов: это работа приложения, а не открытого календаря. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var started = false

    fun start() {
        // Application.onCreate вызывается один раз, но идемпотентность здесь дешевле, чем разбор
        // двойной подписки при первой же перестановке вызова.
        if (started) return
        started = true

        scope.launch {
            combine(plannedRepo.observeActive(), txRepo.observeAll()) { planned, txList ->
                planned to txList
            }.collectLatest { (planned, txList) ->
                val today = LocalDate.now()
                val since = System.currentTimeMillis() - MATCH_WINDOW_MS
                // Занятыми считаются операции ЛЮБОГО обязательства, включая отключённые: иначе
                // выключенная строка отпускала бы свою операцию, и та закрывала бы чужой период.
                val taken = plannedRepo.getAll().mapNotNullTo(HashSet()) { it.lastMatchedTxId }

                val matches = ObligationMatcher.match(
                    payments     = planned,
                    dueDates     = ObligationMatcher.openDueDates(planned, today, zone, LOOKBACK_DAYS),
                    transactions = txList.filter { it.timestamp >= since && it.id !in taken },
                    zone         = zone,
                )
                for (m in matches) {
                    if (m.payment.lastMatchedTxId == m.transaction.id) continue
                    plannedRepo.markMatched(
                        id                 = m.payment.id,
                        txId               = m.transaction.id,
                        throughEpochMillis = m.dueDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                    )
                }
            }
        }
    }

    private companion object {
        /** Насколько далеко назад ищем непогашенные просроченные обязательства. */
        const val LOOKBACK_DAYS = 60L

        /**
         * Какие операции вообще рассматриваются как закрывающие. Шире окна поиска дат: операция
         * может прийти на неделю позже срока, и запас нужен с обеих сторон.
         */
        const val MATCH_WINDOW_MS = (LOOKBACK_DAYS + 14L) * 24 * 60 * 60 * 1000
    }
}
