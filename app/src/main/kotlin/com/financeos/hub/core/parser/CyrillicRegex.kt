package com.financeos.hub.core.parser

/**
 * Регистронезависимое сравнение, которое работает и для КИРИЛЛИЦЫ.
 *
 * `RegexOption.IGNORE_CASE` на JVM превращается в `Pattern.CASE_INSENSITIVE`, а он по документации
 * сворачивает регистр **только US-ASCII**. Проверено на JDK 21:
 *
 * ```
 * Pattern.compile("Purchase", CASE_INSENSITIVE).matcher("PURCHASE").find()  // true
 * Pattern.compile("Покупка",  CASE_INSENSITIVE).matcher("ПОКУПКА").find()   // FALSE
 * Pattern.compile("(?u)Покупка", CASE_INSENSITIVE).matcher("ПОКУПКА").find() // true
 * ```
 *
 * Для приложения, которое читает русские СМС, это молчаливая потеря операции: банк присылает
 * заголовок капсом — «ПОКУПКА», «ОПЛАТА», «БАЛАНС» — парсер не узнаёт свой же ключевой глагол и
 * возвращает `null`. Ошибки нет, лога нет, в истории просто нет платежа. Ровно тот класс дефектов,
 * который в этом проекте дороже всего: невидимый.
 *
 * Kotlin не даёт `UNICODE_CASE` среди [RegexOption], поэтому флаг включается встроенным `(?u)`.
 * Ставится он в начало паттерна и на якорь `^` не влияет — inline-флаги не потребляют символов.
 *
 * Правило простое: **паттерн с кириллицей и без учёта регистра создаётся только через [ciRegex].**
 * Голый `RegexOption.IGNORE_CASE` оставлен там, где паттерн чисто латинский — там он работает.
 */
internal fun ciRegex(pattern: String, vararg options: RegexOption): Regex =
    Regex("(?u)$pattern", setOf(RegexOption.IGNORE_CASE, *options))
