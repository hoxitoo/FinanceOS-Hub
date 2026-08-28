package com.financeos.hub.features.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.analytics.SubscriptionDetector
import com.financeos.hub.ui.components.FosExplain
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosCardSurface

/**
 * Что списывается само, без вашего участия.
 *
 * Экран отвечает на один вопрос: сколько уходит каждый месяц на то, что вы однажды подключили и
 * забыли. Поэтому строка здесь — КОНКРЕТНЫЙ продавец, а не категория: «Продукты» тоже случаются
 * каждый месяц, но отписаться от них нельзя, и в этом списке им не место.
 */
@Composable
fun SubscriptionsScreen(
    onBack         : () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    vm             : SubscriptionsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .background(FosColors.Background),
        contentPadding      = PaddingValues(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Подписки", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                    // По строке на валюту. Один общий итог потребовал бы конвертации по курсу,
                    // которого у приложения нет — оно работает без сети.
                    state.totals.forEach { total ->
                        Text(
                            "≈ ${FosFormatter.compact(total.kopecks, FosFormatter.currencySymbol(total.currency))} в месяц",
                            style = FosType.Body,
                            color = FosColors.TextMuted,
                        )
                    }
                }
                TextButton(onClick = onBack) {
                    Text("← Назад", style = FosType.Label, color = FosColors.TextSecondary)
                }
            }
        }

        if (!state.isLoading && state.active.isEmpty() && state.missed.isEmpty()) {
            item {
                Column(
                    modifier            = Modifier.fillMaxWidth().fosCard(FosCardStyle.Outline, FosTone.Info),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Подписок пока не видно", style = FosType.BodySemi, color = FosColors.TextPrimary)
                    Text(
                        "Списание попадёт сюда, когда повторится хотя бы трижды с ровным " +
                            "промежутком и похожей суммой. Ждать необязательно: отнесите операцию " +
                            "к категории «Подписки», и она появится здесь сразу.",
                        style = FosType.Micro,
                        color = FosColors.TextSecondary,
                    )
                }
            }
        }

        if (state.missed.isNotEmpty()) {
            item { FosSectionHeader("ПРОПУЩЕНЫ", tone = FosTone.Negative) }
            items(state.missed, key = { it.key }) { sub ->
                SubscriptionCard(sub) { sub.categoryId?.let(onCategoryClick) }
            }
        }

        if (state.active.isNotEmpty()) {
            item { FosSectionHeader("РЕГУЛЯРНЫЕ СПИСАНИЯ") }
            items(state.active, key = { it.key }) { sub ->
                SubscriptionCard(sub) { sub.categoryId?.let(onCategoryClick) }
            }
        }

        if (!state.isLoading) {
            item {
                FosExplain(
                    text  = "Подписка — это конкретный продавец, который списывает сам: три и " +
                        "больше списаний, ровный промежуток (неделя, месяц, квартал или год) и " +
                        "похожая сумма. Разброс до 25 % допускается — цены поднимают.\n\n" +
                        "Отдельно показывается всё, что вы сами отнесли к категории «Подписки»: " +
                        "ваше слово сильнее любой эвристики, поэтому хватает одного списания. " +
                        "Промежуток по одному списанию неизвестен, и месячная оценка для такой " +
                        "строки — предположение.\n\n" +
                        "Категории в определении не участвуют. «Продукты» тоже покупаются каждый " +
                        "месяц, но отписаться от них нельзя.\n\n" +
                        "Валюты не складываются: курса у приложения нет, оно работает без сети.",
                    label = "как определяются подписки",
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SubscriptionCard(
    sub    : SubscriptionDetector.Subscription,
    onClick: () -> Unit,
) {
    // Пропущенное списание получает красную огранку: это единственная строка на экране, где что-то
    // не так, и её видно в списке одинаковых подписок без чтения подписи.
    val missed = sub.isMissed
    val tone   = if (missed) FosTone.Negative else FosTone.Neutral
    val style  = if (missed) FosCardStyle.Rail else FosCardStyle.Plain
    val symbol = FosFormatter.currencySymbol(sub.currency)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fosCardSurface(style, tone)
            .clickable(onClick = onClick)
            .padding(FosDimens.CardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sub.title, style = FosType.BodySemi, color = FosColors.TextPrimary, maxLines = 1)

            val period = sub.period
            Text(
                when {
                    missed         -> "Ждали списание, но его не было"
                    period != null -> "${period.label} · последнее ${FosFormatter.dayLabelYear(sub.lastChargeAt)}"
                    else           -> "последнее ${FosFormatter.dayLabelYear(sub.lastChargeAt)}"
                },
                style    = FosType.Micro,
                color    = if (missed) FosColors.Negative else FosColors.TextSecondary,
                maxLines = 1,
            )

            // На чём основано утверждение. «Мы это посчитали» и «вы это сказали» — заявления разной
            // силы, и вторая строка ещё может оказаться разовой покупкой не в той категории.
            Text(
                when (sub.evidence) {
                    SubscriptionDetector.Evidence.Regular ->
                        "повторилось ${sub.chargeCount} ${pluralTimes(sub.chargeCount)}"
                    SubscriptionDetector.Evidence.Labelled ->
                        "из категории «Подписки» · промежуток пока неизвестен"
                },
                style    = FosType.Micro,
                color    = FosColors.TextMuted,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                FosFormatter.compact(sub.typicalKopecks, symbol),
                style = FosType.SmallBold,
                color = if (missed) FosColors.Negative else FosColors.TextPrimary,
            )
            // Приведённую к месяцу сумму показываем ТОЛЬКО когда она отличается от самого списания:
            // «≈ 599 ₽ в мес.» под «599 ₽» просто повторяет строку выше.
            if (sub.monthlyKopecks != sub.typicalKopecks) {
                Text(
                    "≈ ${FosFormatter.compact(sub.monthlyKopecks, symbol)} в мес.",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            } else {
                Text("в месяц", style = FosType.Micro, color = FosColors.TextMuted)
            }
        }
    }
}

private fun pluralTimes(n: Int): String {
    val mod100 = n % 100
    val mod10  = n % 10
    return when {
        mod100 in 11..14 -> "раз"
        mod10 in 2..4    -> "раза"
        else             -> "раз"
    }
}
