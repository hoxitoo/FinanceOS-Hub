package com.financeos.hub.ui.components

/**
 * How [com.financeos.hub.core.analytics.AnalyticsEngine.forecastMonthEnd] actually works, in
 * words, so the number is never just asserted.
 *
 * Written to match the code and to be honest about where it is weak — the same weakness is the
 * reason the figure sits behind an amber rail instead of next to the measured amounts.
 */
const val FORECAST_EXPLANATION =
    "Уже потрачено с 1-го числа плюс дневной темп трат, умноженный на число оставшихся дней. " +
    "Темп берётся из ваших расходов за последние дни этого месяца.\n\n" +
    "Чего прогноз пока не учитывает: что по выходным тратится иначе, чем по будням; что после " +
    "зарплаты расходы подскакивают; что подписки спишутся в известные даты. Поэтому в первые дни " +
    "месяца, когда данных мало, а дней впереди много, он особенно неточен.\n\n" +
    "Это оценка, а не обязательство банка или ваш план."
