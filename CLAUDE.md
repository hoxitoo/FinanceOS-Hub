# FinanceOS-Hub — Session Context

> Read this file at the start of every session to resume without re-reading the full roadmap.
> It describes the **current state** of the project plus the invariants that keep getting
> re-broken. Deep detail lives in `README.md` (user-facing) and `docs/CONTEXT.md` (technical).

## Project
Android offline-first personal finance app. Reads bank SMS/push → auto-categorizes transactions →
shows analytics.
- **Platform:** Android (Kotlin + Jetpack Compose, BOM 2024.06)
- **Package:** `com.financeos.hub`
- **Min SDK:** 26, **Target:** 34
- **DB schema:** Room v23
- **Distribution:** sideloaded APK from GitHub Releases + in-app self-update

## Branch Strategy
```
main  ← stable releases only (PR from dev). A push here triggers release-apk.yml.
dev   ← integration (PR from feature branches)
  claude/project-setup-design-sndr3y  ← current working branch
```
**Never commit directly to main or dev.**

## Build & verification
The project **cannot be built in this container** (no Android SDK; the network policy blocks the
AGP download). **CI is the compiler:** `.github/workflows/android.yml` runs `test` +
`assembleDebug` + `lintDebug` on any PR targeting `dev` or `main`.

### Ошибки процесса, которые повторялись чаще всего
- **Пуш в ветку САМ ПО СЕБЕ не запускает ничего.** Workflow висит на `pull_request` к `dev`/`main`,
  поэтому без открытого PR коммит не компилируется вообще, а ожидание «сейчас придёт CI»
  затягивается на часы. Порядок ровно такой: коммит → пуш → **сразу открыть PR к `dev`** →
  дождаться `Build & Test` → и только потом говорить, что код собирается. Без зелёного CI любая
  фраза «готово» — догадка: локально нет ни компилятора, ни Android SDK.
- **После squash-мержа ветка «отстаёт», хотя всё её содержимое уже в `dev`.** История в `dev`
  переписана в один коммит, и обычный пуш будет отвергнут. Правильно — начать ветку заново от
  свежего `dev` (`git checkout -B <branch> origin/dev`), а не мержить и не копить.
- **Релиз — это ДВА PR:** ветка → `dev`, затем `dev` → `main`. Пуш в `main` запускает
  `release-apk.yml`, который и собирает APK с новым номером сборки.
- **`dev` → `main` сливается МЕРЖ-КОММИТОМ, а не squash.** Squash кладёт в `main` новый коммит с
  тем же содержимым, но с другой историей: `main` и `dev` расходятся, база слияния остаётся в
  прошлом, и следующий релиз приходит с конфликтом в файлах, которые правились в обеих волнах
  (первым — этот файл). Хуже того, конфликтный PR не собирается вообще: GitHub не может построить
  merge-ref, и CI по нему просто не запускается — выглядит как «проверки не пришли».
  Squash уместен на первом шаге (ветка → `dev`), где история ветки и правда лишняя.
- **Документация — часть правки, а не последующая уборка.** Всё, что стоит помнить, идёт в этот
  файл В ТОМ ЖЕ PR. Проверено на себе: инварианты, записанные «потом», не записываются.

There are no instrumented/UI tests — gestures, rendering and screen behaviour are verified by
review and reasoning only. State that honestly when reporting.

## Architecture
Clean Architecture + MVVM + Hilt + Room + Compose + Coroutines/Flow

```
app/
├── core/
│   ├── database/     (entities, daos, converters, FosDatabase, migrations)
│   ├── parser/       (BankParser, ParserEngine, banks/ — 13 штук, TransferPatterns, PromoFilter,
│   │                  CreditNoticeParser, AmountParser, MerchantNames, CyrillicRegex/ciRegex,
│   │                  InvestmentTransfers — пополнение брокера = перевод)
│   ├── classifier/   (DictionaryClassifier, CategoryDefaults)
│   ├── sms/          (SmsReader, SmsReceiver — ТОЛЬКО SMS)
│   ├── auth/         (BiometricHelper)
│   ├── account/      (AccountLinker)
│   ├── bank/         (BankRegistry — единственный список банков: имя, буква, цвет, ключи)
│   ├── invest/       (BrokerEvents, BrokerPushParser — пуши БКС, Portfolio — позиции и результат,
│   │                  MarginAlerts — предупреждения брокера, BrokerEventMapper — строка ↔ событие,
│   │                  SecurityGroups — группа бумаги по тикеру;
│   │                  режим «Инвестор», инварианты #44, #47)
│   ├── credit/       (CreditMath — debt, free limit, cycle, min payment, due payment;
│   │                  CreditNoticeApplier)
│   ├── edit/         (TransactionEditor — правка операции на оба экрана; TransactionEditRules —
│   │                  чистые правила баланса и цели при смене счёта/суммы/даты)
│   ├── transfer/     (TransferRouter)
│   ├── finance/      (SavingsMath — вклад: прогноз, срок, требуемый взнос;
│   │                  GoalPlan — копилка без процентов: остаток, взнос к сроку, свой темп)
│   ├── calendar/     (CalendarEvent, PaymentDates, CalendarBuilder, FreeMoney, ObligationMatcher,
│   │                  ObligationSyncer)
│   ├── analytics/    (AnalyticsEngine, ScoreCalculator, InsightGenerator,
│   │                  BehavioralAnalyzer, NarrativeEngine, AnalyticsWorker,
│   │                  LifetimeStats — итоги и разбивки за годы;
│   │                  MonthOverMonth — каждый месяц против своего предыдущего)
│   ├── ml/           (ModelLoader, TextFeatureExtractor, MLCategoryClassifier,
│   │                  SpendingPredictor, BehavioralCluster)
│   ├── pdf/          (PdfImporter, PdfTransactionParser)
│   ├── backup/       (BackupManager, BackupCrypto)
│   ├── update/       (UpdateChecker, UpdateCheckWorker)
│   └── notifications/(NotificationHelper — 4 channels; **PushNotificationListener живёт ЗДЕСЬ**,
│                      не в sms/; ListenerHealth + ListenerRebindReceiver + ListenerWatchdogWorker
│                      + ListenerNotice — живучесть привязки службы)
├── data/
│   ├── repositories/ (Tx, Account, Card, Category, Budget, Goal, TransferRoute)
│   └── preferences/  (UserPreferences via DataStore)
├── di/               (DatabaseModule, ParserModule, RepositoryModule, MLModule, AnalyticsModule)
├── features/         (dashboard, transactions, analytics, budget, goals, calculator, calendar,
│                      subscriptions, categories, credit, onboarding, settings, auth/LockScreen,
│                      investor — переключатель «Кошелёк | Инвестор», главная инвестора, своя нижняя
│                      панель и вкладки Операции/Аналитика/Календарь/Счета, ручной ввод)
├── navigation/       (FosNavHost, FosRoutes)
├── widget/           (BalanceWidget)
└── ui/
    ├── theme/        (FosColors, FosType, FosDimens, FosSurface, FosTheme, FosFormatter,
    │                  AmountVisualTransformation, Shimmer, BankColors, BioluminescentIndication)
    └── components/   (FosFormSheet — лист формы с подтверждением выхода; AccountPicker —
                       выбор банк→счёт; остальное см. README)
```

---

# Critical Design Rules (NEVER violate)

1. `FosColors.Positive` (#4DFFA0) = income, success, savings ONLY
2. `FosColors.Negative` (#FF6B6B) = expenses, errors, overrun ONLY — **expense amounts in
   TransactionRow MUST use Negative**; TRANSFER renders NEUTRAL (`TextPrimary`, "↔ amount")
3. All monetary/numeric `Text` → `fontFeatureSettings = "tnum"` (tabular-nums)
4. `InsightCard` — colored left border ONLY, no icon inside. Border color = severity
   (CRITICAL→Negative, WARNING→Warning, INFO→Info)
5. Net Worth negative → Negative color
6. `ScoreDonut` slices never use Negative — red reads as "error", not "category"
7. **Никогда не рисуй карточку руками.** `clip + background(Surface)` в фиче — это баг: экран
   сливается в одно полотно. Только `Modifier.fosCard / fosCardSurface / fosHeroCard / fosInset`
   из `ui/theme/FosSurface.kt`.
   - `FosCardStyle` выбирается по **роли** блока: `Raised` — главный блок экрана (один на экран),
     `Rail` — блок с финансовым направлением, `Sunken` — вложенный список внутри карточки,
     `Outline` — призыв к действию/пустое состояние, `Plain` — всё остальное.
   - `FosTone` подчиняется правилам #1/#2: `Positive` только доход/успех, `Negative` только
     расход/превышение. Блоку без направления — `Neutral`.
   - Красная огранка на КАЖДОЙ строке списка расходов запрещена: когда красное всё, не выделено
     ничего. В `TransactionRow` полосу получает только доход.
   - **Признак, повторяющийся почти на каждой строке, показывается ТИХО.** Метка банка/карты в
     списке операций подчиняется той же логике, что и красный кант: у человека с двенадцатью
     счетами в двух банках цветная метка на всех строках — это фон, а не метка. Заметность
     обратна частоте: доминирующий источник (≥ 40 % видимых строк) идёт серой подписью, редкий —
     цветом банка; если доминирующего нет, список разнороден по-настоящему и цветные все.
     Считает это СПИСОК (`buildSourceLabels` по отфильтрованным операциям), а не строка: строка о
     соседях ничего не знает. Отфильтровал по одному счёту — метки гаснут сами.
   - Заголовок группы — `FosSectionHeader` (галочка тона + линейка), не голый `Text(SectionCap)`.
     `SectionCap` остаётся только для подписи поля внутри формы/шита.
   - Карточка, содержимое которой заливает её целиком (арт целей), дополнительно получает
     `fosCardEdge` — обычная рамка рисуется ДО детей и оказывается под артом.
8. **Режим «Инвестор» — единственное исключение из #1/#2:** результат позиции законно зелёный при
   росте и красный при убытке (решение пользователя — так читает любой брокер). Акцент режима —
   индиго `FosColors.Invest` (`FosTone.Invest`), фон — `FosColors.InvestBackground`. Мятный режиму
   не принадлежит: «Кошелёк» в переключателе выбран нейтрально, мятный значит «доход»

## Amounts Storage
- Store as `Long` kopecks (×100), convert to Double only in `FosFormatter`
- Negative kopecks = expense, positive = income, TRANSFER signed by direction
- `currency` is per-transaction (not just per-account) — RUB/USD/EUR/KGS

## SMS Deduplication
`smsId = "${sender}_${timestamp}_${body.hashCode()}"` — checked before insert, then
`existsSimilarSmsOrPush(signedAmount, ±5 min)` catches the SMS↔push twin of the same event.
Сумма **знаковая**, не по модулю — см. инвариант #14, иначе вторая нога перевода между своими
счетами молча съедается.

## Categories (19)
13 расходных + 3 доходных + «Букмекер» + «Подписки» + «Инвестиции». Список **append-only** — см.
инвариант #9. «Инвестиции» (`cat_invest`) — категория ПЕРЕВОДА брокеру и от него, а не трата
(инвариант #43).
«Подписки» отделены от «Развлечений»: кинотеатр и купленная в Steam игра — разовая покупка,
Netflix и Яндекс Плюс — ежемесячное списание, и в бюджете это разные вещи.

## Supported Banks (13)
- **P1:** Сбербанк, Т-Банк, ВТБ, Альфа-Банк, Газпромбанк
- **P2:** Райффайзен, Росбанк, Открытие
- **P3:** МТС Банк, Почта Банк, Россельхозбанк, **МКБ**
- **KG:** МБанк (multi-currency USD/KGS/EUR/RUB)

12 из 13 закрыты тестами (~7 случаев на банк). **`MkbParser` теста не имеет** — он
зарегистрирован в `ParserModule` и работает, но его форматы («Карта *5933 Покупка 1500р Магазин
Остаток 24686.88р») ничем не закреплены: любая правка `TransferPatterns` или `AmountParser` может
сломать его молча. Тест на него — дешёвый и давно напрашивающийся долг.

---

# Hard-won invariants

These are the defects that recurred across many sessions. Re-read before touching the
corresponding area.

### 1. Room accounts use `@Upsert`, never `@Insert(onConflict = REPLACE)`
REPLACE = DELETE + INSERT in SQLite, and `CardEntity` has `ForeignKey(onDelete = CASCADE)`. Every
balance edit / manual op / delete-reversal therefore wiped the account's cards. This was the root
cause of the entire "cards keep detaching themselves" saga (5 failed symptom-fixes before it).

### 2. Balance is decoupled from the transaction row
- A bank «Остаток» is an **absolute snapshot**; a message without one applies a **delta**.
- On a **dedup hit** the row is dropped but `applyAuthoritativeBalance(cardMask, ostatok)` still
  runs — the dropped twin often carries the balance the kept row lacked.
- `snapToAuthoritativeIfNewer` compares the snapshot timestamp against `account.updatedAt`, so
  re-linking a card never reverts a fresher manual correction.
- Deleting a **delta-applied** row (`accountId != null && balanceKopecks == null`, source ≠ PDF)
  reverses the balance. A row carrying a real «Остаток» is left alone.
- `AccountLinker` resolves against **active accounts only**; `linkOrphansToAccount` also reclaims
  rows stranded on a deactivated ("ghost") account, never one already on a live account.

### 3. Soft delete leaves references behind
`deactivate` sets `is_active = 0` and nothing else. `deleteAccount` must also deactivate the
account's cards and drop its ACCOUNT goal-routes, or a re-created account (new id) leaves a
zombie card and a goal linked to a ghost.

### 4. Compose Rules of Hooks
`remember` / `LaunchedEffect` / `rememberInfiniteTransition` / `animate*AsState` must be called
**unconditionally, before any early return** — an `if (!enabled) return` above them corrupts the
slot table when the toggle flips at runtime. Inactive animation = `1f..1f` transition, not a
skipped call. Per-item `remember` needs a key (`remember(tx.id)`) or a reused sheet shows the
previous item's data. `LazyColumn` items always take a stable `key`.

### 5. `runCatching` swallows `CancellationException`
That breaks `collectLatest` / `mapLatest` cancellation — the "cancelled" work runs to completion
anyway. Always re-throw it.

### 6. `stateIn` belongs to the ViewModel, not to a function call
`fun historyFor(id) = flow.stateIn(...)` leaks a coroutine per call, and a composable body calls
it on every recomposition. Cache per id in the ViewModel **and** `remember(id)` at the call site.

### 7. Money input fields
State holds the **raw** string; grouping goes through `AmountVisualTransformation`. Formatting
`value` directly desynchronises the caret (typing `12345` produced `12354`).
`parseAmountInput` **rounds** — `(1417.59 * 100).toLong()` truncates a kopeck.
`sanitizeAmountInput` allows one separator and ≤2 decimals so the field can't show `1,23` while
saving 0 ₽.

### 8. Analytics windows
Score pillars that need history use offsets `1..3` — **completed** months only. `buildScoreInput`
falls back to the last completed month when the current one has no income yet, otherwise the
score craters every 1st of the month. The Analytics period chips filter only the category/daily
aggregates — **never** an `analyticsEngine.*` call.

### 9. Categories are append-only in the seed list
`sort_order` is the list index and existing installs keep their order via `INSERT OR IGNORE`, so
a new category goes **last** and the colour list grows with it (17 cats / 17 colours). Adding
categories/rules = re-run both seed helpers in a new migration.

### 10. Categorisation does not learn
Rule-based + a frozen TFLite model. Correcting a transaction changes that row only. A new
merchant needs a merchant rule (or offline retraining + a new `.tflite`).

### 11. Fail open on the lock screen
`MainActivity` renders nothing until the biometric preference is known, both reads default to
"off" on failure, and the lock screen always offers device-PIN. A tester once had to reinstall
and lost all history.

### 12. A credit account's balance is a NEGATIVE debt, and its «Доступно» is not a balance
`AccountEntity.kind` splits money you own (`CASH`) from money you owe (`CREDIT`). On a credit card
`balanceKopecks` is zero-or-negative and its magnitude is the debt, so every existing delta path
stays correct with no sign special-case; the free limit is `creditLimitKopecks + balanceKopecks`.

The trap is the bank's own figure. A **confirmed real Сбер push** reads
«Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽ Счёт карты МИР •• 6703», and 18 699 + 411 301 = 430 000 —
the card's limit. So on a credit card «Баланс» is the FREE LIMIT, printed under the very same label a
debit card uses: **the text can never disambiguate, only `AccountEntity.kind` can.** Stored naively it
would book 411k as money you own.

`balanceFromReportedFigure(account, reported)` is the single translation point, used by `syncBalance`,
`applyAuthoritativeBalance` and `snapToAuthoritativeIfNewer`: pass-through for CASH, `reported − limit`
for CREDIT, and **null** (→ caller falls back to the transaction delta) when the limit is unknown or
smaller than the reported figure — a stale limit would otherwise invert the debt into money owned.

Net worth, the widget and the score's cushion pillar are all **CASH-only** (`sumCashBalances`);
the cushion additionally subtracts `sumCreditDebt()`. With no credit cards every one of these is
byte-identical to the pre-v11 behaviour.

### 13. Parser hygiene
- `PromoFilter` runs in `ParserEngine.parse()` **before** any bank parser — marketing pushes
  ("лимит 163 000 ₽") were being booked as real transfers.
- Transfer keywords are stem-anchored with a Cyrillic lookahead so «переводами» ≠ «Перевод».
- Every sender-matching parser is tried (`firstNotNullOfOrNull`), not just the first.
- `AmountParser` is null-safe (a throw aborted the whole 90-day import) and handles NBSP.
- Card-mask regexes require the masking glyph — a merchant ending in 4 digits was read as a card.
- **Кириллический паттерн без учёта регистра создаётся ТОЛЬКО через `ciRegex()`.**
  `RegexOption.IGNORE_CASE` = `Pattern.CASE_INSENSITIVE`, который сворачивает регистр только
  US-ASCII: «Покупка» не совпадает с «ПОКУПКА» (проверено на JDK 21), и пуш с заголовком капсом
  молча терялся — `null`, без ошибки и без лога. `UNICODE_CASE` в `RegexOption` нет, поэтому
  `ciRegex` включает его встроенным `(?u)`. Голый `IGNORE_CASE` законен только для чисто
  латинского паттерна. Та же ловушка у SQLite `LIKE` и у `\b`/`\w` (они ASCII-only — идиома
  проекта: `(?![А-Яа-яёЁ])` / `(?<![\p{L}\p{N}])`).

---

# Feature Status

Everything below is **implemented and shipped** unless marked otherwise.

## Core
- [x] Gradle skeleton, AndroidManifest, design system, database, navigation, onboarding
- [x] 13 bank parsers + `ParserEngine` (@IntoSet DI) + `TransferPatterns` + `PromoFilter`
- [x] `SmsReceiver` (real-time, `goAsync`), `SmsReader` (90-day import), `PushNotificationListener`
      (reads **every** notification text extra)
- [x] Живучесть привязки службы уведомлений (`core/notifications/`): `ListenerHealth` отличает
      ВЫДАННОЕ РАЗРЕШЕНИЕ от РАБОТАЮЩЕЙ службы, `ListenerRebindReceiver` чинит привязку после
      обновления APK и перезагрузки, `ListenerWatchdogWorker` раз в час просит мягко,
      `ListenerNotice` сообщает, если жёсткий перезапуск сбросил разрешение — см. инвариант #28
- [x] SMS is **opt-in** (`sms_realtime_enabled`, default false)
- [x] `DictionaryClassifier` (~253 rules, 19 категорий), `CategoryDefaults.forType` income fallback.
      **Словарь идёт ПЕРВЫМ, модель — вторая.** Модель заморожена на 13 метках и категории,
      добавленные позже («Букмекер», «Подписки»), назвать не может; при обратном порядке они
      остались бы навсегда пустыми.
- [x] `AccountLinker` (card→account, authoritative balance, orphan re-link, recency guard)
- [x] `AccountKind` (CASH / CREDIT / INVESTMENT) + credit terms on `AccountEntity`; net worth,
      widget and score cushion are CASH-only
- [x] `CreditNoticeParser` — «Платёж по кредитной карте / Внесите платёж X до ДД.ММ.ГГ» разбирается
      как **факт о карте, не операция**: ничего не вставляется, пишутся сумма и дата платежа.
      Идёт **до `PromoFilter`** (тот режет пуш на слове «беспроцентным»). Карта определяется по
      банку и только когда ответ однозначен. В 90-дневном импорте не применяется — старое
      напоминание затёрло бы текущее.
- [x] `TransferRouter` (goal routing by account/card/keyword, counterparty leg, internal pairing)
- [x] `SavingsMath` (`core/finance/`) — одна помесячная симуляция на три задачи: что накопится,
      за сколько наберётся, сколько откладывать. Капитализация, момент взноса, индексация взноса,
      инфляция, НДФЛ, эффективная ставка, точка перелома. 24 юнит-теста.
- [x] All 7 repositories, `UserPreferences` (DataStore, ~20 keys)

## Screens
- [x] Dashboard (3 hero variants, month label, bank cards, clickable recent ops, account CRUD)
- [x] Transactions (одна строка фильтров: лупа-поиск, «Тип операции» меню — Все/Расходы/Доходы/
      **Переводы**, «Дата» — календарь на один день или отрезок; swipe-left-to-reveal delete,
      detail/edit sheet with source diagnostics **и правкой счёта, второй стороны перевода и даты**
      (инвариант #39), «↑ Экспорт» CSV, «↓ Импорт» PDF, manual add incl.
      **Перевод** with destination account). На каждой строке — метка источника «•• 6703» в цвете
      банка, тихая у доминирующего счёта и цветная у редкого (см. правило огранки #7)
- [x] Analytics (period chips + 4 tabs — see README for the per-tab breakdown; вверху «Трендов» —
      «Месяц к месяцу», инвариант #41); плитка «За всё
      время» после финансового здоровья → экран `features/analytics/lifetime` (итоги, нарастающие
      кривые, бары по годам с категориями, доли, источники; инвариант #40)
- [x] Budget (envelopes, CRUD, throttled alerts)
- [x] Цели — карточка с кольцом выполнения (процент ВНУТРИ кольца, цвет по теме цели), действия
      в ряд (история / ± / привязка / удалить, удаление с подтверждением), «Калькулятор» словом
      по центру шапки, выполненные цели под сворачиваемым заголовком «Выполненные» внизу.
      Форма цели: иконки разложены по девяти категориям в два столбца (без бокового скролла),
      компактные яркие поля, ДВЕ даты — «Начало» (v17→v18, `goals.started_at`) и «Срок»,
      привязка счетов чипами банк→счёт с множественным выбором и подтверждением выхода
- [x] Subscriptions, Categories CRUD, Settings, Onboarding
- [x] Калькулятор накоплений — `features/calculator`, вход из «Целей» (🧮). Три режима, тонкая
      настройка, разбивка «своё / проценты», столбики и таблица по годам. Подставляет ваш темп
      (средний остаток за 3 закрытых месяца) и суммы ваших целей.
- [x] Календарь и «Свободно» — `features/calendar`, вход плиткой на главной под кредиткой.
      Полоса ближайших дат + список событий + подтверждение найденных подписок + раздел «уже
      прошло». Источники: объявленные платежи, платёж по кредитке, конец беспроцентного периода,
      найденные подписки, дедлайны целей. Два режима: полоса и **сетка месяца** — сетка работает
      фильтром, выбранный день оставляет в списке только свои события.
- [x] Кредитные карты — плитка на главной (под hero, один вставочный пункт → все 3 варианта героя)
      + экран `features/credit` (сводка, блок на карту с датой/суммой платежа, полоса беспроцентного
      периода, ставка, утилизация, история операций, лист редактирования условий). Экран сжат:
      свободный лимит и утилизация не дублируются, условия тарифа свёрнуты под «Тариф ▾» —
      переполненный экран из одинаковых строк не читается, а листается

## Analytics
- [x] `ScoreCalculator` (4 pillars, 0–100) + `ScoreDonut` multi-colour rendering
- [x] `InsightGenerator` (6 rules), `NarrativeEngine` (8 templates), `AnalyticsEngine`
- [x] `BehavioralAnalyzer` — payday effect, fatigue curve, impulse classification, anomalies,
      subscription gaps, fixed/variable (CV ≤ 15%)
- [x] `AnalyticsWorker` (daily, `@HiltWorker`), `WhatIfSimulator`, `ExpensePyramid`

## ML (`core/ml/`) — pre-trained, inference-only
- [x] `merchant_classifier.tflite` (256→13), `spending_predictor.tflite`, `behavioral_cluster.tflite`
      — all bundled in `assets/models/`; every one falls back gracefully when absent
- [x] Interpreter calls are `Mutex`-guarded (TFLite `Interpreter` is not thread-safe)

## Platform
- [x] Backup/restore — 9 наборов (accounts, cards, categories, goals, budgets, routes,
      **planned**, transactions, **brokerEvents**) → файл **открытого JSON**, restore additive + FK-safe.
      **Копия НЕ шифруется** — см. инвариант #27. `BackupCrypto` остался только на чтение старых
      `.fose`, и в коде он вызывается ровно в одном месте — в `restoreFrom`
- [x] Notifications — 4 channels, allowlisted deep-links, permission-guarded
- [x] Biometric lock (fail-open, device-PIN escape hatch), 2×2 home-screen widget
- [x] In-app self-update + `UpdateCheckWorker` (12 h) + `release-apk.yml` pipeline
- [x] Shimmer layer («Анимации» / «Атмосфера») + «Кот-режим» mascot & paw particles

## Release pipeline
- `release-apk.yml` on push to `main` → builds the debug APK with
  `FOS_BUILD_NUMBER=${{ github.run_number }}`, publishes GitHub Release `v0.1.0.<run>` with the
  APK attached.
- Stable signing via the committed `app/debug.keystore` (password `android`, debug-only) —
  without one shared signature the in-app updater hits a signature mismatch. No
  `applicationIdSuffix`, so updates replace the installed package.

---

# Changelog (condensed)

| Phase | Content |
|-------|---------|
| **1** | Skeleton, design system, DB, 5 P1 parsers, all screens, score, insights, charts |
| **2A** | Behavioural analytics — heatmap, payday, fatigue, impulse, anomalies, waterfall, narratives, what-if, pyramid |
| **3** | TFLite ML layer, Settings, notifications |
| **Post-3** | Manual entry/edit, search, goals & budget CRUD, account management, categories CRUD, CSV export, push listener, P2/P3 parsers, biometrics, widget |
| **Transfers** | TRANSFER as a first-class type, `TransferRouter`, goal auto-routing by account/card/keyword, bidirectional account routing |
| **Shimmer** | «Анимации» + «Атмосфера» layers (particles, tilt/sheen, breathing hero, bioluminescent ripple, currency reef) |
| **Cat mode** | Mood-matched mascot + paw particles, mood tiers identical to the score tiers |
| **Distribution** | Release pipeline, in-app updater, background update notifications, резервные копии (шифрование ключом устройства позже снято — инвариант #27) |
| **Credit cards** | `AccountKind`, схема v10→v12, плитка + экран, разбор реальных пушей Сбера, погашение переводом, оценка процентов |
| **Improvement cycle (batches 1–5)** | Score donut, biometric lockout fix, goal transfers + history + pixel art, money-input rewrite, bank→account picker, budget-alert throttling, «Букмекер» + marketplace/bookmaker rules, Trends tab rebuilt for readability, Categories 3D pie + drill-down, analytics period chips |
| **UI system** | `FosSurface` — огранка карточек по роли (Raised/Rail/Sunken/Outline/Plain) + тон по правилам цвета; `FosSectionHeader`; `fosCardEdge` для карточек с артом на всю площадь; пояснение прогноза трат |
| **Подписки + калькулятор** | Категория «Подписки» (v13→v14) с переводом старых правил стриминга через UPDATE; словарь стал приоритетнее замороженной модели; `SavingsMath` + экран калькулятора накоплений |
| **Календарь** | `planned_payments` (v15→v16), `CalendarEvent`/`PaymentDates`/`CalendarBuilder`/`FreeMoney`/`ObligationMatcher`, экран календаря, плитка «Свободно» на главной, подтверждение найденных подписок |
| **Календарь: сетка + отчёт с устройства** | Сетка месяца как фильтр; `ObligationSyncer` (запись сопоставления вне экрана); `rejected_tx_id` (v16→v17); отсечка по `createdAt`; вход в календарь перестал быть условным (инвариант #21) |
| **Защита ввода** | `FosFormSheet` — подтверждение выхода из заполненной формы через `confirmValueChange`; `AccountPicker` (банк → счёт) вынесен в общий компонент |
| **Кириллица в разборе** | `ciRegex()` с `(?u)`: `IGNORE_CASE` на JVM сворачивает только ASCII, и пуш капсом молча терялся. 55 паттернов переписаны, `CyrillicCaseTest` фиксирует поведение |
| **Реальные пуши** | Входящий перевод по СБП («Перевод … от …» — это приход), `RUR` как рубль, списание по номеру счёта, `RealPushFormatsTest` |
| **Живучесть службы уведомлений** | `ListenerHealth`/`ListenerRebindReceiver`/`ListenerWatchdogWorker`/`ListenerNotice` — автоматическое переподключение после обновления APK и перезагрузки (инвариант #28) |
| **Переводы двумя ногами** | Ручной перевод пишется двумя строками с общим `transferPairId`, удаление откатывает оба счёта (инвариант #16 распространён с погашения на любой перевод) |
| **Операции: фильтры** | Одна строка фильтров — лупа-поиск, меню «Тип операции» с **Переводами**, фильтр по дате (день/период); «↓ Импорт»/«↑ Экспорт»; убран двойной системный отступ (инварианты #25, #26) |
| **Цели: карточка и форма** | Процент внутри кольца и цвет по теме цели; карточка вдвое ниже (действия в ряд); иконки по категориям в два столбца вместо ленты вбок; `goals.started_at` (v17→v18); привязка счетов чипами банк→счёт с множественным выбором — и в режиме правки она наконец сохраняется (инвариант #29) |
| **Цели: расчёт и остаток** | `GoalPlan` (`core/finance/`) — сколько осталось, сколько откладывать в месяц, успевает ли собственный темп; блок «РАСЧЁТ» прямо в форме цели. Ручная правка суммы — вводом ОСТАТКА (инвариант #32). Привязка к счёту засчитывает любое движение денег на нём (инвариант #31) |
| **Метка источника в списке операций** | На каждой операции видно, чья это карта: «•• 6703» в цвете банка. Заметность обратна частоте (`buildSourceLabels`, `TxSourceLabelsTest`) — иначе при 90 % операций с одного банка метка стала бы фоном |
| **Цели: автопополнение и список** | Ручная операция дошла до маршрутизатора (инвариант #30 — раньше зачисления не было, а списание при удалении было); лист автопополнения перестроен (что привязано — сверху, счета по банкам, карты с именами, ручной ввод под «Ещё», повторный тап отвязывает); удаление цели спрашивает подтверждение; выполненные цели — под сворачиваемым заголовком внизу |
| **Счёт по частям + дубликаты подписок** | Обязательство закрывается НЕСКОЛЬКИМИ операциями одного дня (`matched_tx_ids`, v18→v19) — «Телефон, интернет 2 000 ₽», оплаченные 550 + 1 500, больше не просрочены; кнопка «оплачено» для того, чего приложение не увидит никогда; группы подписок одного бренда с ОДИНАКОВОЙ ценой схлопываются в одну строку (инварианты #36, #37) |
| **Платёж по кредитке: внесён раньше срока** | `duePayment` вычитает погашения, сделанные после напоминания банка (`due_payment_seen_at` наконец читается): карта показывает «внесён» вместо требования, календарь перестаёт вычитать оплаченное из «Свободно», срок для плитки на главной приходит из календаря (инвариант #38) |
| **Правка счёта и даты операции** | В карточке операции правятся счёт списания/зачисления, вторая сторона перевода и дата — пуш без реквизитов больше не надо удалять и вводить заново. `TransactionEditor` — одна правка на оба экрана, только изменённые поля поверх свежей строки; лежит ли сумма строки в балансе, хранится в `balance_detached` (v19→v20), а не выводится из дат (инвариант #39) |
| **За всё время** | Плитка «Всего потрачено / всего заработано» в аналитике и экран `LifetimeScreen`: итоги за год / 2 / 10 / 20 лет / всё время, две нарастающие кривые с шагом месяц / полгода / год / 2 года, бары по годам с категориями внутри (касание раскрывает год), доли по категориям, источники трат и дохода (инвариант #40) |
| **Месяц к месяцу** | Вверху «Трендов»: траты и доход по месяцам наложенными барами (месяц поверх бледного предыдущего), окно 6 мес / год, каждый месяц против своего предыдущего через границу года; незаконченный месяц сравнивается в полную силу и помечен; касание раскрывает разбивку по категориям (инвариант #41) |
| **Реестр банков** | `BankRegistry` — одна запись на банк вместо четырёх копий (цвет, буква значка, выбор банка, ключи привязки); все 14 банков в выборе; привязка РСХБ без маски заработала (инвариант #42) |
| **Брокер: разметка операций** | Пополнение брокерского счёта («Получатель платежа BKS Mir Investitsiy») — ПЕРЕВОД с категорией «Инвестиции» (`cat_invest`, v20→v21), а не расход «Другое»: не попадает в траты, бюджет, оценку и подписки; в списке «↔ 10 000 ₽ · → брокеру». Миграция переразмечает уже записанные пополнения (инвариант #43) |
| **Режим «Инвестор»: подготовка** | Переключатель «Кошелёк \| Инвестор» в шапке главной (запоминается); палитра индиго; экран инвестора — портфель, брокеры, позиции, заявки и сделки — с честным пустым состоянием и примером на реальных пушах БКС; `BrokerPushParser` (пополнение, заявка активна / отменена / исполнена), `Portfolio` (средняя цена, свободные деньги, результат по цене последней сделки); служба уведомлений замечает имя пакета приложения брокера (инвариант #44) |
| **Инвестор: события брокера** | `broker_events` (v22→v23): пуши найденного приложения брокера записываются; перевод между счетами брокера (итог не меняется), предупреждение «критично низкий баланс» — янтарная карточка, закрывается деньгами, пришедшими на счёт после него; выбор счёта «Весь портфель / счёт», как у БКС; точка на «Инвестор» в переключателе; события брокера в резервной копии (инвариант #47) |
| **Выгрузка тестера** | Сбер-пуши: сумма не склеивается с цифрами названия, игривые заголовки отрезаются, оплата по СБП в магазине — покупка, «+» — приход, деньги «в Альфа-Банк» — перевод; v21→v22 чинит уже записанную историю и раскладывает «Другое» по пополненному словарю; незнакомая карта больше не переписывает баланс единственного счёта банка — подсказка на главной и переезд операций при добавлении карты; CSV с переводами и валютой (инварианты #45, #46) |

**Audits 1–11** produced ~90 fixes. The ones worth remembering are distilled into
*Hard-won invariants* above; the rest are visible in `git log`.

### Known limitations
- Counterparty card mask is only available when the bank spells «на карту/счёт *NNNN». Several
  push formats omit it — those rely on keyword or account routing.
- Rows ingested before a schema addition don't backfill (e.g. a transaction stored before the
  `currency` column stays RUB).
- An internal transfer whose two legs arrive more than 10 min apart can't be paired.
- Sberbank `parsePush()` anchors on «В запасе:» — other balance labels need coverage.
- У обязательства помнится **одна** отвергнутая операция (`rejected_tx_id`), не список. Отвязать
  вторую подходящую операцию того же обязательства нечем — она встанет на место первой. У счёта,
  закрытого по частям, «Отвязать» запоминает только основную часть; на практике остальные части в
  одиночку под сумму не подходят, и обязательство остаётся открытым.
- Составной платёж собирается только из операций ОДНОГО календарного дня: счёт, оплаченный вчера и
  сегодня, останется открытым. Это сознательная сторона ошибки (инвариант #36) — закрыть период
  вручную можно кнопкой «оплачено».
- Две подписки одного сервиса по ОДИНАКОВОЙ цене покажутся одной строкой (инвариант #37).
- Правка счёта не знает о ручной правке баланса, сделанной после операции: якорем при привязке
  считается только банковский «Остаток» (инвариант #39). Привязка сироты к счёту, баланс которого
  человек уже выправил руками, сдвинет его ещё раз.
- Удаление непарного банковского перевода снимает деньги со второго счёта без учёта «Остатка»,
  пришедшего по нему позже, — прежнее поведение, правкой не затронуто.
- Вторую сторону перевода, пришедшую из сообщения банка, в карточке не поменять — её сдвиг баланса
  задним числом не восстановить. Перевод, переименованный в расход, оставляет вторую ногу как есть.
- **Возврат покупки и кэшбэк на кредитке засчитываются как погашение.** Оба приходят
  положительной суммой и от перевода на карту неотличимы (`asRepaymentIfCredit` намеренно приводит
  их к одному виду), поэтому возврат за покупку закроет обязательный платёж, хотя банк его так не
  засчитает. Ограничение общее с `statementDueDebt` и существует с самого появления расчёта: данных,
  по которым их можно развести, в сообщении банка нет.
- `MkbParser` не покрыт тестами (13-й банк, добавлен вместе с отображением карт).
- Резервная копия — открытый JSON (инвариант #27); класть её в общую папку нельзя.
- Брокер узнаётся по имени ПОЛУЧАТЕЛЯ (инвариант #43). Не распознаются: пополнение без получателя или
  с другим именем («Перевод между счетами» на брокерский счёт того же банка); перевод, который
  разобрал `TransferPatterns` (у него получатель всегда «Перевод»); ручная операция — её тип выбирает
  человек. Зато выбранная вручную категория «Инвестиции» у ПЕРЕВОДА делает его переводом брокеру.
- Старая история переразмечается только из машинных категорий: строку, которую замороженная модель
  (при включённой «ИИ классификации») отнесла, скажем, в «Покупки», миграция не тронет.
- Брокер — это и работодатель: зарплата от «БКС» станет переводом. Признак по имени не различает, за
  что пришли деньги.
- Деньги у брокера видны в кошельке только плиткой «У брокера» (#53), в «Всего» и в оценку не входят.
- Склейка пополнения требует точной суммы: перевод с комиссией банка (ушло 10 050, пришло 10 000)
  не склеится и будет предложен к записи — «Это не пополнение» его уберёт.
- Остатка по отдельному счёту брокера нет (#47): сделки приходят без номера счёта. Деньги у брокера
  считаются по всем счетам вместе и без комиссий.
- Пуши брокера записываются, только начиная с версии v23 и только после того, как приложение брокера
  найдено. Пришедшие раньше в историю не попадут.
- Удалённые операции брокера возвращаются при восстановлении копии, снятой до удаления (#49).
- Ручная сделка вводится в штуках; лот у ручного ввода всегда 1.
- Котировки Мосбиржи — с задержкой 15 минут и раз в сутки (#55). Внебиржевые бумаги (3800_HK) на бирже
  не торгуются — у них цена своих сделок. Результат за период в рублях считается по ТЕКУЩЕМУ курсу:
  изменение курса доллара за месяц в нём не видно. История цен копится только с момента включения.
- Предупреждение о низком балансе закрывается только деньгами, которые видны в пушах. Пополнение,
  о котором брокер не прислал пуш, или закрытая брокером позиция его не закроют — для этого кнопка
  «Закрыть».
- **Незнакомый номер карты с «Остатком» больше не ложится на единственный счёт банка, если у того есть
  свои номера** (#45). Если банк пишет хвост номера СЧЁТА вместе с остатком, а у счёта записан номер
  КАРТЫ, операция останется без счёта, пока этот номер не добавят к счёту (подсказка это предлагает).
  Сообщение без «Остатка» с чужим номером по-прежнему ложится на счёт банка и двигает его дельтой.
- Переезд операций на добавленную карту забирает только строки с «Остатком». Строка без «Остатка»,
  лёгшая раньше на чужой счёт по банку, остаётся там: её сумма уже в чужом балансе, и переносить её
  надо правкой счёта в карточке операции (#39).
- Починка истории Сбера (v22) не трогает строки без «Остатка», с целью, с парой перевода и любые,
  которые человек правил. Если тот же платёж пришёл ещё и SMS (оно включается вручную), а пуш был
  записан с неверной суммой, дедуп их не склеил — после починки они могут оказаться двумя строками.
- Игривый заголовок Сбера узнаётся по эмодзи, «!», запятой или словам вроде «покупка», «трата»,
  «денежки» перед «в». Новая фраза без этих признаков останется в названии целиком — лишнее слово,
  а не потерянная операция.

---

# Next Steps
- **Инвестиции** — сделано: пополнение брокера = перевод (#43), каркас режима «Инвестор» (#44).
  Следующий шаг, по порядку:
  1. ~~Запись событий брокера~~ — **сделано** (#47): `broker_events` (v22→v23), приём пушей
     найденного приложения мимо `ParserEngine`, портфель по своим данным, счета брокера,
     переводы между ними, предупреждения о низком балансе.
  2. ~~Склейка ног пополнения~~ — **сделано вычисляемой склейкой** (#53): пополнение у брокера
     знает, откуда пришли деньги; перевод без зачисления предлагается записать; в кошельке —
     плитка «У брокера». `AccountKind.INVESTMENT` по-прежнему не используется: брокерский счёт
     живёт в событиях брокера, второй копии баланса в `accounts` не заведено.
  3. ~~Размер лота по бумаге~~ — **берётся с Мосбиржи** при включённых котировках (#55). Комиссии,
     вывод с брокерского счёта, продажа,
     частичное исполнение, покупка облигаций (цена в % от номинала), купоны, пополнение счёта
     «Облигации» — только по реальным пушам, форматы не угадывать.
  4. ~~Группы бумаг, как у БКС~~ — **сделано справочником** (#48); точный тип даст сеть.
- ~~Своя нижняя панель для режима «Инвестор»~~ — **сделано** (#50). «Настройки» — общие для обоих
  режимов (решение пользователя): шестерёнка в шапке одна. Наполнение «Календаря» ждёт пушей о купонах и
  дивидендах (или котировок из сети).
- ~~Котировки из сети~~ — **сделано** (#55): ISS Мосбиржи раз в сутки по переключателю, итог в
  рублях, размер лота и группа бумаги — с биржи.
- **Тест на `MkbParser`** — 13-й банк работает и ничем не закреплён. Дешевле всего сделать сразу.
- **Шифрование копии парольной фразой** — вернуть защиту, не повторяя ключ устройства
  (инвариант #27). До этого README честно пишет, что копия открытая.
- Polish: localization review, dark-mode visual QA
- Consider: cross-channel dedup window tuning (currently ±5 min, conservative)
- Consider: signed **release** APK channel (keystore in GitHub Secrets)
- Await more Sberbank push format variants

## Перед большой правкой — короткий чек-лист
1. Прочитать инварианты по затрагиваемой области: они описывают дефекты, которые УЖЕ возвращались.
2. Схема БД растёт только миграцией; категория добавляется ДВУМЯ операциями (инвариант #9 и #18).
3. Новый экран/лист с вводом — через `FosFormSheet`; новая карточка — через `FosSurface`,
   не `clip + background`.
4. Кириллический паттерн — только `ciRegex()`.
5. Изменилось поведение, которое человек увидит, — строка в README; появился дефект, который
   может вернуться, — инвариант здесь. В ТОМ ЖЕ PR.
6. Коммит → пуш → **PR к `dev`** → зелёный CI. Без него «собирается» — это гипотеза.

## Credit cards — remaining work
Заходы 1–2 (фундамент + плитка/экран) сделаны. Осталось:
Заход 2.5 (парсер по реальным пушам Сбера) сделан: покупка по кредитке и напоминание о платеже
больше не теряются, «Баланс» кредитки конвертируется в долг, цифра банка показывается вместо
расчётной с явной пометкой источника. Осталось:

3. **Погашение** — сделано в объёме, который можно проверить: кнопка «Погасить» (лист с суммой и
   выбором счёта), проводка ПЕРЕВОДОМ на карту, а не расходом; входящие деньги на кредитку при
   приёме переклассифицируются из дохода в перевод (`asRepaymentIfCredit`) — иначе погашение
   считалось бы заработком; дедуп по знаковой сумме, чтобы обе ноги выжили.
   **Не сделано:** распознавание автоплатежа и явное спаривание двух банковских пушей в одну
   операцию — **пуша погашения у пользователя нет**, писать вслепую нечего проверять. Сейчас
   две ноги просто остаются двумя строками с верным итогом.
3.5. **Платёж, внесённый раньше срока** — сделано, см. инвариант #38. Требование банка гасится
   погашениями после напоминания; карта, календарь и плитка на главной согласованы.
4. **Проценты** — сделано. `accruedInterest` (простое начисление за дни просрочки) и
   `minimumPaymentOutlook` (помесячная симуляция «плачу только минимум»). Обе цифры на экране
   ЯВНО помечены оценкой. Точна ровно одна: ноль внутри беспроцентного периода.
   Минимальный платёж моделируется как процент от долга **но не меньше 300 ₽** — без порога
   симуляция не сходится: платёж уменьшается вместе с остатком и никогда не достигает нуля.

### 14. Кросс-канальный дедуп сравнивает ЗНАКОВУЮ сумму, не модуль
Две доставки одного события всегда одного знака; две ноги перевода между своими счетами — всегда
разных. Погашение кредитки — ровно это: −50 000 с дебетовой и +50 000 на кредитку с разницей в
секунды. Сравнение по модулю молча съедало вторую ногу, и погашение не появлялось в истории карты.

### 15. Расчётный цикл НИКОГДА не показывает просрочку — это может только банк
Якорение на последней ЗАКРЫТОЙ выписке означало, что между сроком платежа и следующим закрытием
(девять дней в месяц при типовых 30/20) карта постоянно горела «просрочена» и накручивала
выдуманные проценты — тому, кто заплатил вовремя. Подтверждения оплаты приложение не видит, поэтому
после прошедшего срока цикл переходит к СЛЕДУЮЩЕЙ выписке. Реальная просрочка не теряется: её несёт
пуш-напоминание банка с настоящей прошедшей датой, и `duePayment` предпочитает его расчётному.

### 16. Погашение пишется ДВУМЯ строками с общим `transferPairId`
Одна строка двигала бы оба баланса, но откатить можно только тот счёт, на котором она лежит: при
удалении второй счёт остаётся испорченным навсегда. Логика удаления уже рассчитана на это —
она исключает `transferPairId != null` из отката встречной ноги.
Остаток по выписке считает покупки и погашения РАЗДЕЛЬНО: при зачёте друг против друга сумма к
оплате не уменьшалась после платежа, и лист погашения подставлял её снова.

### 17. Условия карты описываются так, как их печатает банк
Первая модель («день выписки + дней на оплату») описывает классическую грейс-карту и НЕ описывает
120-дневную СберКарту: там обязательный платёж ежемесячный, а беспроцентный период отсчитывается
от покупки. Пользователь не смог заполнить эти два поля — в экране «Тариф» дня выписки просто нет.
Теперь форма повторяет тариф (лимит, ставка, неустойка, обязательный платёж % + «не менее», длина
беспроцентного периода, комиссия за наличные), у каждого поля написано, где его взять, а два поля
расписания помечены необязательными: сумму и дату банк присылает сам, они лишь подстраховка.

### 18. Правило категоризации бьёт модель, а не наоборот
`MLCategoryClassifier` спрашивает `DictionaryClassifier` ПЕРВЫМ и возвращает его ответ, если тот
есть. Модель заморожена на 13 метках (`CATEGORY_IDS`), а категорий уже 18 — «Букмекер» и
«Подписки» она физически назвать не может. При обратном порядке новая категория остаётся
навсегда пустой при включённой «ИИ классификации», и это выглядит как сломанная функция, а не как
ограничение модели. Модель по-прежнему отвечает там, где правила молчат.

Следствие для добавления категории: **мало вставить правило.** Правила идут через
`INSERT OR IGNORE`, а классификатор берёт ПЕРВОЕ совпадение (`ORDER BY priority DESC`, дальше
rowid). Дубликат паттерна с новым id встанет позже старого и не сработает никогда — существующую
строку нужно переписывать `UPDATE`, как это делает `MIGRATION_13_14` для шести правил стриминга.
Историю это не трогает: категория лежит в самой транзакции, правила влияют только на будущий разбор.

### 19. «Свободно» — это не остаток, и не всякое событие календаря его двигает
`CalendarEvent.affectsFree` отделяет ПЛАТЁЖ от СРОКА. Конец беспроцентного периода и дедлайн цели —
даты: денег в этот день никуда не уходит. Вычесть беспроцентный период отдельной строкой значило бы
посчитать один и тот же долг дважды — он уже сидит в платеже по карте.

Горизонт отбрасывает ЗАКРЫТЫЕ поступления так же, как и расчёт. Зарплата, сопоставленная на пару
дней раньше срока, иначе схлопывала бы окно на свою же дату: весь остаток месяца выпадал из расчёта,
и «Свободно» завышалось ровно после получки, когда на счёте максимум.

Платёж по кредитке гасится отдельным флагом, а не сам собой: `duePayment` честно держит присланную
банком сумму 45 дней, а сообщения «вы заплатили» банк не шлёт. Без флага оплаченная карта вычиталась
бы из «Свободно» ещё полтора месяца — те же деньги дважды.

Ожидаемые поступления считаются, но НЕ прибавляются: неполученная зарплата, посчитанная тратимой, —
прямой путь к перерасходу, а «Свободно» существует ровно для того, чтобы его не было. Валюты не
смешиваются (курса у офлайн-приложения нет), но чужая валюта и не выбрасывается — иначе долларовая
подписка молча завысила бы свободные деньги.

Горизонт по умолчанию — до следующего поступления, а не до конца месяца. Откат на конец месяца
обязан проверять, что тот ещё ВПЕРЕДИ: 31-го числа окно схлопывалось бы в один день, все
обязательства выпадали из расчёта, и раз в месяц — именно в день с наибольшим числом платежей —
показывался бы весь остаток.

### 20. Сопоставление обязательства с операцией — это ЗАПИСЬ, и она живёт отдельно от экрана
`ObligationMatcher` — чистая функция, и посчитать её внутри построения календаря соблазнительно.
Но её результат исчезает вместе с экраном: обязательство остаётся незакрытым, пока на календарь
кто-нибудь не посмотрит, а отметка нужна и плитке на главной, и самому «Свободно».
`ObligationSyncer` — `@Singleton` со стартом из `Application`, который пишет `matched_through`.
Во ViewModel ему не место и по второй причине: VM привязана к своему `NavBackStackEntry`, у главной
и у календаря они разные, и сборщик запускался бы дважды, записывая одно и то же в две руки.

Обязательство НЕ ОПИСЫВАЕТ время до своего появления. Без отсечки по `createdAt` подтверждённая
сегодня подписка вытаскивала прошлые месяцы как «ПРОСРОЧЕНО» — долг, которого нет, — и тут же
закрывала их старыми покупками. Из-за этого «Отвязать» выглядело сломанным: снятая отметка
мгновенно возвращалась, только на месяц раньше.

Подтверждённой считается подписка ЖИВОГО обязательства. Считать и удалённые казалось правильным
(«не всплывёт обратно»), но это ровно наоборот: удалив строку, человек либо ошибся, либо передумал,
и подписка обязана вернуться в предложения — иначе она исчезает отовсюду навсегда, и вернуть её
нечем. По той же причине операция, закрывшая удалённое обязательство, снова свободна.

«Отвязать» обязано оставлять след (`rejected_tx_id`). Без него сборщик на следующем же проходе
находит ту же операцию — она снова свободна и по-прежнему подходит — и закрывает обязательство
опять: кнопка, после которой всё возвращается назад. Помнится одна отвергнутая операция, а не
список: смысл действия — «нет, это не она», дальше ищем ДРУГУЮ.

Матчер берёт БЛИЖАЙШУЮ к сроку операцию, а не первую подходящую: список приходит по убыванию
времени, и «первая» значит «самая свежая» — у недельного обязательства платёж следующей недели
закрывал бы предыдущую, а свой период после этого не закрывался бы уже никогда.

Ошибки здесь несимметричны: жадное сопоставление завышает «Свободно» и делает человека беднее,
строгое — занижает и делает осторожнее. При сомнении обязательство остаётся открытым.
`openDueDates` берёт САМУЮ РАННЮЮ незакрытую дату, а не ближайшую будущую: иначе отметка
перепрыгивала бы неоплаченные месяцы. Цикл «запись → перечитывание» конечен, потому что закрытая
дата уходит из выдачи, а занятые операции исключаются заранее.

### 21. Единственный вход в функцию нельзя делать условным
Плитка «Свободно» пряталась, пока в календаре нет обязательств — «показывать нечего». Но добавить
обязательство можно только НА экране календаря, а попасть туда можно было только через эту плитку.
Замкнутый круг: функция вышла в релиз и для человека просто не существовала. Условие «есть что
показать» законно для ВТОРОГО входа и незаконно для единственного; пустое состояние — это часть
функции, а не повод её спрятать.

### 22. Форму нельзя закрыть молча — но и спрашивать на каждое закрытие нельзя
Смахивание вниз — самый лёгкий жест на экране, и он же был необратимым: заполненная анкета
закрывалась без сохранения и без вопроса, а восстановить ввод нечем. Все листы с вводом идут через
`FosFormSheet`.

Перехват — `confirmValueChange`, а не `onDismissRequest`: первый отклоняет САМ ПЕРЕХОД, и лист
остаётся на месте; второй срабатывает, когда лист уже уехал вниз, и его пришлось бы возвращать —
виден отскок. Отсюда же следствие: лист владеет своим `SheetState` сам, потому что
`confirmValueChange` задаётся при создании состояния и должен видеть «грязность» формы.

Вопрос задаётся ТОЛЬКО когда есть что терять. Для новой записи это любой ввод, для правки —
отличие от сохранённого. Диалог на каждое закрытие приучает жать «Выйти» не глядя, и защита
перестаёт работать ровно тогда, когда нужна. Признак «грязности» у формы с десятком полей живёт
рядом с полями (`CreditTermsState.differsFrom`), а не в листе: разъехавшийся список «что сравнивать»
— это поле, правку которого форма не считает изменением и теряет без вопроса.

### 23. Ранний `return` в композабле не убирает `item {}` из списка
Плитка, решающая внутри себя не показываться, оставляет в `LazyColumn` пустую ячейку — и `spacedBy`
честно добавляет ей отступ. На главной появляется дыра без содержимого. Решение «показывать ли»
принимает ВЫЗЫВАЮЩИЙ, снаружи `item`. Та же ловушка у полосы календаря в пустом месяце.

### 24. Калькулятор считает симуляцией, а не формулой
`SavingsMath.simulate` идёт по месяцам. Замкнутая формула аннуитета короче ровно до первого
реального требования: капитализация раз в квартал, взнос в начале месяца, ежегодная индексация
взноса — каждое ломает формулу и не ломает симуляцию.
- Обе обратные задачи опираются на ту же симуляцию: срок — прогон до достижения суммы, взнос —
  два прогона (итог линеен по взносу), а не подбор делением пополам.
- Округление ОДИН раз, на выходе. Проценты = разность округлённых величин, а не округление
  разности, иначе «ваши + проценты ≠ итог» на копейку — и это первое, что замечает глаз.
- Потолок `MAX_MONTHS = 600`. Недостижимая цель возвращает `null` («никогда»), а не 600 месяцев.

### 25. Вложенный `Scaffold` отступает от системных панелей ВТОРОЙ раз
Экраны живут внутри `Scaffold`'а навигации, и тот уже отдал содержимому отступ под нижнюю панель
(`NavigationBar` сам добавляет к своей высоте вставку системной навигации). Свой `Scaffold` внутри
экрана по умолчанию отступает ещё раз — снизу остаётся пустая полоса цвета фона высотой в системную
навигацию. На тёмной теме это выглядит как «чёрная плата», приклеенная к экрану, и объяснить её
нельзя ничем: под ней нет содержимого. Лечится `contentWindowInsets = WindowInsets(0, 0, 0, 0)` на
ВНУТРЕННЕМ `Scaffold`, а не подгонкой отступов. `WindowInsets(0)` не компилируется — у фабрики нет
одноаргументной перегрузки, только четыре стороны.

### 26. Фильтр, сузивший список до пустого, обязан это сказать
«Операций пока нет» при выставленном фильтре — ложь: операции есть, их отсеяли. Человек, выбравший
вчерашний день или тип «Переводы», решит, что история потерялась. Пустое состояние смотрит на ВСЕ
сужения сразу (поиск, тип, даты, категория), а не на одно.

### 27. Резервная копия НЕ шифруется — и это решение, а не недоделка
Первая версия шифровала экспорт AES-GCM-256 ключом из Android Keystore. Ключ **привязан к
устройству**, поэтому копия не открывалась ровно там, где копия и нужна: на новом телефоне и после
переустановки. Защита от постороннего обернулась защитой от владельца, и `d3154fd` перевёл экспорт
на открытый JSON. `BackupCrypto.decrypt` остался только чтобы читать старые `.fose`, и вызывается
в единственном месте — `restoreFrom`, с откатом на UTF-8.

Отсюда два следствия, и оба обязательны:
- **Файл копии — это полная финансовая история открытым текстом.** Любая документация, обещающая
  шифрование, — ложь пользователю о приватности; в README и здесь это написано прямо.
- Вернуть шифрование можно **только с ключом, который человек может унести с собой** (PIN/парольная
  фраза, KDF). Ключ устройства — уже пройденный тупик, повторять его нельзя.

### 28. Разрешение на доступ к уведомлениям ≠ работающая служба
`NotificationManagerCompat.getEnabledListenerPackages()` читает СПИСОК РАЗРЕШЕНИЙ. Android рвёт
привязку `PushNotificationListener` при обновлении APK (а приложение раздаётся файлом и обновляется
часто), при перезагрузке и при агрессивном энергосбережении — разрешение при этом остаётся. Экран
настроек зелёными буквами писал «уведомления обрабатываются», пока не обрабатывалось ничего:
молчаливый отказ, который ещё и успокаивает.

- `requestRebind` (мягкая просьба) **после обновления APK не поднимает службу** — проверено на
  One UI. Единственное, что работает, — перезапуск компонента через `setComponentEnabledSetting`.
- Перезапуск компонента иногда СБРАСЫВАЕТ разрешение. Поэтому он делается не чаще раза в сутки,
  повторное включение стоит в `finally`, а при потере разрешения человек получает уведомление —
  догадаться самому невозможно, операции просто перестают появляться.
- Ежечасный сторож делает **только мягкую** просьбу. Жёсткий перезапуск по расписанию — это
  лотерея с разрешением пользователя раз в час.

### 29. Элемент управления, который ничего не меняет, хуже отсутствующего
В форме цели был выбор счёта, и в режиме ПРАВКИ он просто не доезжал до сохранения: колбэк
принимал пять значений и выбрасывал пятое (`onSave = { …, _ -> }`). Человек выбирал счёт, нажимал
«Сохранить», получал закрытый лист — и ни ошибки, ни привязки. Отсутствующая кнопка отправила бы
искать привязку в другом месте (лист «🔗» умел это всегда); присутствующая — убедила, что дело
сделано.

Причина глубже опечатки: привязка цели к счёту живёт не в самой цели, а в `transfer_routes`, и
форма, правящая ОДНУ таблицу, молча не могла записать в другую. Поэтому счета синхронизируются
отдельным действием (`syncAccountRoutes`), и оно трогает только маршруты типа ACCOUNT — привязки
по карте и ключевому слову настраиваются в другом листе, и стереть их заодно значило бы отменить
чужую настройку.

Проверка при правке формы: ДОХОДИТ ли каждое поле до записи. Поле, которое некуда сохранить,
удаляется из формы, а не оставляется «на будущее».

### 30. Операция, созданную приложением, проходит мимо конвейера, через который идёт банковская
`TransferRouter.onTransactionInserted` вызывается из `SmsReceiver`, `SmsReader` и
`PushNotificationListener` — и НЕ вызывался из `insertManual`. Ручной перевод на привязанный к цели
счёт цель не двигал. При этом УДАЛЕНИЕ такой операции честно звало `onTransactionReversed`:
зачисления не было, списание было, и цель могла уйти в минус относительно собственной истории.
«Автопополнение не работает» — это оно и было.

Чинить прямым вызовом того же метода нельзя: он написан под банковское сообщение. Банк присылает
перевод ОДНИМ пушем на ОДИН счёт, поэтому метод сам двигает баланс встречного счёта и ищет парную
строку. У ручной операции обе ноги уже написаны и оба баланса уже применены — тот же код добавил бы
деньги второй раз и переписал бы `transferPairId` своим. Поэтому у ручных строк свой узкий вход
`onManualRowInserted`: только привязка к цели, без балансов и без спаривания.

Правило шире одного места: **у каждого способа создать операцию должен быть один и тот же список
последствий.** Добавляя источник (импорт, виджет, шаблон), проверь по списку: баланс счёта,
привязка к цели, сопоставление обязательства, дедуп. Пропущенный пункт не падает — он молча
не случается.

### 31. Цель, привязанная к счёту, следует за ДЕНЬГАМИ на нём, а не за типом операции
Первая версия двигала цель только переводами: `onTransactionInserted` выходил первой же строкой,
если тип не TRANSFER. На накопительном счёте это значило, что зачисление («Зачисление 50 000 ₽»)
цель не двигает, хотя деньги пришли ровно туда, куда копят. Правило теперь одно и знаковое:
пришло на привязанный счёт — прибавилось, ушло — убавилось, независимо от того, назвали операцию
зачислением, тратой или переводом.

Следствия, которые придётся держать в голове:
- **Привязка к карте, с которой тратят, будет дёргать цель на каждой покупке.** Это не дефект, а
  прямое следствие правила; в листе привязки так и написано, чтобы выбор был осознанным.
- **Смена типа операции в правке меняет ЗНАК** (расход → доход): старое зачисление снимается,
  новое применяется. Раньше правка типа просто отвязывала операцию от цели, и цель оставалась с
  вкладом, которого в истории больше нет.
- Откат (`onTransactionReversed`) обязан искать привязку **именно к счёту этой строки**. У цели
  может быть несколько привязанных счетов, и «первая попавшаяся привязка типа ACCOUNT» давала
  обратный знак: удаление зачисления УВЕЛИЧИВАЛО бы цель.
- Привязка НЕ переносит деньги, уже лежащие на счёте: она описывает будущие движения. Стартовую
  сумму человек ставит сам.

### 32. Ручную правку суммы цели человек вводит ОСТАТКОМ, а не разностью
Было «прибавить / снять» — и это требовало считать в уме: на цели 40 000, стало 45 000, вводи
5 000. Ошибка в этом счёте попадала прямо в цель (ввёл 45 000 вместо 5 000 — цель прыгнула), а
откатить её можно было только такой же ручной разностью. Человек знает СВОЙ ОСТАТОК, а не дельту;
разность считает приложение и показывает её до нажатия. Поле открывается заполненным текущей
суммой, поэтому случайное «Сохранить» ничего не меняет.

Внутри — та же `GoalRepository.contribute` со знаковой разностью: у неё мьютекс общий с
автоматическими зачислениями и единственное место, где цель закрывается и открывается обратно.

### 33. Запрос списка не должен прятать то, ради чего на список смотрят
Экран целей читал `observeActive()` — `WHERE is_completed = 0`. Цель исчезала с экрана ровно в тот
момент, когда сумма сходилась: ни снять деньги, ни поправить, ни посмотреть историю, ни удалить.
Вместе с ней оказался мёртвым и весь раздел «Выполненные», и ветки оформления достигнутой цели —
код, который невозможно увидеть.

Фильтр «активные» законен там, где закрытая запись действительно не нужна (календарь, калькулятор —
у набранной цели нечего планировать), и незаконен в списке, который эту запись показывает. Решает
ЭКРАН, а не запрос: `observeAll()` + разделение в UI.

Проверка при добавлении раздела в список: доходят ли до него данные вообще. Раздел, который не
может наполниться, не падает — он просто никогда не появляется.

### 34. Ссылка без внешнего ключа переживает своего владельца
У `transfer_routes` нет `ForeignKey` на `goals`, и удаление цели оставляло её привязки живыми.
Маршрутизатор берёт ПЕРВЫЙ подходящий маршрут — сирота, стоящая раньше, перехватывала зачисления у
новой цели на том же счёте, и та не пополнялась вообще. Диалог удаления при этом прямо обещал, что
привязки исчезнут.

Две меры, и обе нужны: `deactivateByGoal` при удалении цели (как `deactivateByAccountValue` при
удалении счёта — там этот случай давно закрыт) и `contribute`, возвращающая `false`, когда цели уже
нет. Без второй операция получала `goal_id` несуществующей цели: откатывать при удалении нечего, в
истории цели такая строка не появится никогда.

### 35. Форма пишет поверх СВЕЖЕЙ строки, а не поверх снимка
`GoalDao.upsert` — это `@Insert(REPLACE)`, он переписывает строку целиком. Форма правки держала
снимок цели, сделанный при открытии, и сохранение затирало `savedKopecks`: зачисление, прилетевшее
пушем, пока лист открыт, исчезало без следа. То же с «сколько отложено сейчас» — разность считалась
от суммы, показанной при открытии диалога.

Правило: перед записью перечитать строку и наложить на неё только те поля, которые правит форма.
Окно тем шире, чем длиннее форма и чем чаще приходят автоматические изменения, — а оба множителя
в этом заходе выросли.

### 36. Обязательство закрывается НАБОРОМ операций, и занят весь набор
Счёт за связь платят двумя переводами: телефон 550 ₽, интернет 1 500 ₽ — при объявленных 2 000 ₽.
По отдельности ни один не похож на обязательство, и оно висело просрочкой, занижая «Свободно» ровно
у того, кто заплатил. Матчер после неудачи с одиночным совпадением складывает операции ОДНОГО
календарного дня.

Складывать произвольные траты, пока не сойдётся сумма, — это та самая жадность, от которой защищает
весь остальной файл: две случайные покупки недели сложатся в 2 000 ₽ без труда. Поэтому набор обязан
выглядеть как ОДИН платёж, разбитый на части: один день, не больше трёх частей, каждая ≥ 20 % суммы
(иначе мелочь «добивает» 1 900 до 2 000), не больше восьми кандидатов в дне, непротиворечивые
категории. Одиночное совпадение всегда важнее составного.

**Занятыми становятся ВСЕ части, а не основная.** `matched_tx_ids` (v18→v19) хранит список;
`last_matched_tx_id` остался как самая крупная часть — её показывает карточка события. Оставь вторую
ногу свободной — она закроет соседнее обязательство, и один платёж посчитается дважды. По той же
причине `matchedTxIds` попал в резервную копию: восстановленное обязательство иначе считало бы свои
же части свободными. У строк до v19 колонка пуста, и `matchedTxIdList` откатывается на одиночный id.

**У осторожной эвристики обязан быть выход.** Платёж наличными, перевод с чужой карты, счёт,
разбитый непохожим образом, приложение не увидит НИКОГДА — и без кнопки «оплачено» единственным
исходом остаётся вечная просрочка. Кнопка закрывает РОВНО эту дату (`matched_through`), а не
обязательство целиком. У платежа по кредитке её нет: эту дату приложение считает само, и «оплачено»
там означало бы спор с банком.

### 37. Одна подписка под двумя именами — это дубликат, две по разной цене — нет
`MerchantNames.groupKey` намеренно различает продукты одного бренда по уточнению из строки биллинга
(«ADOBE *CREATIVE CLOUD» ≠ «ADOBE *ACROBAT»). Но тот же сервис банк присылает по-разному («ChatGPT»,
«VTS OPENAI *CHATGPT SUBSC», через разных посредников), ключи расходились, и на экране было ШЕСТЬ
подписок вместо трёх.

Спор разрешает ЦЕНА, а не текст: одна подписка стоит одинаково, как её ни назови. Три условия
слияния, и каждое закрывает свою ошибку:
- **допуск 2 % (`MERGE_PRICE_TOLERANCE`), а не общие 25 %.** У человека ДВЕ подписки ChatGPT — 19,99
  через Google Play и 22,40 напрямую; между ними 10,8 %, и общий допуск слил бы их в одну строку,
  занизив расходы ровно там, где человек прямо сказал, что подписок две. Широкий допуск внутри
  группы законен — там речь о подорожании ОДНОГО списания; здесь вопрос «одно ли оно вообще».
- **бренд опознан** (`MerchantNames.isKnownBrand`). У безымянной строки `groupKey` кладёт в ту же
  позицию весь очищенный текст, ключи структурно неразличимы, и «совпадение по первой части» стало
  бы случайным: два магазина с одинаковым чеком слиплись бы в подписку.
- **валюта совпадает.** 19,99 $ и 19,99 ₽ — не одна подписка, а курса у приложения нет.

Хост — группа с БОЛЬШИМ числом списаний: осколки присоединяются к обжитой, не наоборот. Принятая
цена решения: два аккаунта одного сервиса по одинаковой цене покажутся одной строкой и итог будет
занижен. Случай редкий, а дубликаты — ежедневные у всех, кому банк меняет описание.

### 38. Требование банка гасится платежами, сделанными ПОСЛЕ него
Банк присылает напоминание и замолкает: сообщения «вы заплатили» не существует, приложение видит
только сам перевод. `duePayment` честно держал присланную цифру до срока и **не смотрел на
погашения вообще** — на устройстве «внести 989,84 ₽ до 30 сентября» висело после двух платежей на
40 000 ₽, а плитка на главной подгоняла «платёж через 4 дня».

Колонка для этого была с самого начала: `accounts.due_payment_seen_at` пишется `CreditNoticeApplier`
при каждом напоминании. **Её никто не читал** — поле, заполняемое и никем не используемое, ничем не
отличается от отсутствующего (то же, что инвариант #29, только со стороны данных). Миграции правка
не потребовала.

Отсчёт идёт от МОМЕНТА НАПОМИНАНИЯ, а не от начала месяца и не от срока: банк считал свою цифру,
уже зная обо всём, что было раньше, и зачесть более ранний платёж значило бы посчитать его дважды.
Ошибки здесь несимметричны, как и у обязательств (#20): не засчитать платёж — попросить заплатить
ещё раз; засчитать лишний — объявить требование закрытым, когда оно не закрыто, и это стоит
неустойки. Поэтому при неизвестном `seenAt` не засчитывается НИЧЕГО.

Следствия, разошедшиеся по трём экранам, и все обязательны:
- `DuePayment` теперь несёт `amountKopecks` (сколько ПОТРЕБОВАЛИ, не уменьшается) **и**
  `paidKopecks`/`remainingKopecks`. Показывать надо остаток, а помнить — требование: «внесено
  40 000 из 0 ₽» читается как ошибка приложения.
- Расчётная ветка вычитает ноль: `statementDueDebt` уже НЕТТО. Второе вычитание закрыло бы выписку
  вдвое меньшим платежом.
- Календарь кладёт в событие `remainingKopecks`, а `settled` получает ВТОРОЕ условие —
  `due.isSettled`. На 120-дневной карте работает только оно: дня выписки там нет, `cycle == null`,
  и ветка «долг по выписке закрыт» не срабатывает никогда.
- Погашение — это ПОЛОЖИТЕЛЬНАЯ сумма (долг лежит отрицательным балансом, #12). Покупка после
  напоминания требование не уменьшает.
- `isSettled` требует `amountKopecks > 0`: нулевое требование — это «банк ничего не просил», а не
  «вы заплатили», и писать «внесён» там нельзя.

**Повторная доставка того же напоминания момент НЕ двигает** (`noticeSeenAt`). Одно требование
приходит не один раз: SMS и пуш о нём — две доставки одного события, и банк повторяет напоминание
по мере приближения срока. Вставки тут нет, поэтому дедуп операций до этого места не достаёт, а
сдвиг `seenAt` на «сейчас» обнулял бы зачёт — оплаченное требование воскресало бы при каждом
повторе, ровно от той причины, ради которой зачёт и делался. «То же самое» — совпадение суммы И
срока: новая цифра означает новый период, и отсчёт обязан начаться заново.

**Внесённый платёж не бывает просроченным**, даже когда его срок позади: неустойку берут за
пропущенный обязательный платёж, а он сделан. Без этой проверки карта писала бы «внесён» зелёным и
тут же «срок прошёл, проценты идут» с оценкой по штрафной ставке — два противоположных утверждения
в одном блоке. Та же поправка нужна огранке карточки и чипу «через N дней».

**Экран переключается по ОСТАТКУ, а не по `isSettled`.** Закрытая выписка расчётной ветки даёт
требование в ноль, у которого нечего «вносить», и прежняя ветка рисовала «Внести до 20 июля · 0 ₽»
с чипом срочности — при том что календарь считал её закрытой. Два экрана об одной карте обязаны
говорить одно и то же.

**Беспроцентный период живёт ВНЕ веток про платёж.** Это отдельный, более поздний срок, и исчезать
вместе с закрытым требованием ему незачем — а он исчезал, потому что полоса стояла внутри ветки
«надо платить».

**Срок платежа переехал с главной в календарь.** Плитка кредитки считала его сама из `AccountEntity`,
а главный экран читает только текущий месяц (`observeCurrentMonth`): платёж, сделанный до первого
числа, для неё не существует, и она продолжала бы подгонять уже оплаченную карту. Теперь
`CalendarState.nextCreditDueInDays` — единственный источник, тот же, что у «Свободно», и обе плитки
всегда согласны. `CreditSummary.daysUntilDue` из модели УДАЛЁН, а не оставлен пустым: поле, которое
владелец всегда заполняет null, — это следующий #29.

### 39. Лежит ли сумма строки в балансе — ХРАНИТСЯ, а не выводится из дат
Пуш без реквизитов приходит без счёта: сумма есть, откуда ушли деньги — нет. Единственным выходом
было удалить операцию и занести руками — и потерять исходный текст, остаток банка, связь с целью и
с обязательством. Теперь счёт, вторая сторона перевода и дата правятся в карточке.

Главный вопрос правки — **что делать с балансом**. Первая версия ВЫВОДИЛА ответ из дат: «был ли у
счёта банковский «Остаток» позже операции». Ревью нашло, что это ломает удаление: ручная трата
ВЧЕРАШНИМ числом, введённая после утреннего пуша с «Остатком», легла поверх банковской цифры, но
по датам «Остаток» позже неё — и удаление перестало возвращать деньги. Важен порядок, в котором
приложение ОБРАБОТАЛО строку и «Остаток», а не время событий, и из дат он не восстанавливается.

Поэтому факт хранится: `transactions.balance_detached` (v19→v20). Строка «владеет» сдвигом, если у
неё есть счёт, нет «Остатка», она не из PDF и флаг не стоит (`balanceEffectOf`) — это прежнее
правило удаления плюс флаг. По умолчанию флаг 0, и все существующие пути вставки и удаление ведут
себя ровно как до правки.
- **Откат** — только по факту: владела строка сдвигом — вернуть его. Никаких дат.
- **Новый сдвиг** при смене счёта — кроме случая, когда у НОВОГО счёта банковский «Остаток» позже
  операции: тогда банк уже учёл эти деньги, строка привязывается с `balance_detached = 1` и баланс
  не трогает. Здесь время событий законно: вопрос «знал ли банк об этих деньгах, называя остаток».
- Якорем считается только «Остаток», который приложение смогло применить: на кредитке с неизвестным
  лимитом цифра банка непереводима (#12), баланс шёл дельтами, и такой «Остаток» не якорь.
- Тот же счёт — флаг не меняется, сдвигается разница сумм: расход −1 500 → доход +1 500 — это
  +3 000. Раньше смена типа считалась «только для аналитики», и строка с обратным знаком (как было
  с СБП «от …») чинилась в истории и оставалась испорченной на счёте. Только дата — ничего.
- Строка с «Остатком», привязанная к новому счёту, становится его снимком через
  `snapToAuthoritativeIfNewer` — как усыновлённая сирота.

**Накладываются только изменённые поля, поверх свежей строки** (#35). Карточка отправляет `null`
для нетронутых полей. Отправь она снимок целиком — правка одной заметки отвязала бы счёт, который
пуш привязал, пока лист был открыт, и разорвала бы пару перевода.

Цель — одно правило (`goalPlan`) вместо удалённого `applyRetype`. Строка без цели получает её только
при смене СЧЁТА и только от привязки, существовавшей на момент операции
(`TransferRouter.onRowReassigned` сравнивает `route.createdAt`): иначе исправление знака у
полугодовой операции пополнило бы цель, заведённую вчера (#31). Цель по счёту при сдвиге денег —
откат и новая привязка; цель по карте переживает смену своего счёта, но не уход из перевода.

Вторая сторона перевода правится, только когда её нет или её записало само приложение. Сторона из
сообщения банка («на счёт *3583») двигала баланс по условиям, которые задним числом не восстановить,
а отдельная банковская строка правится в своей карточке. Новая нога — своя строка с общим
`transferPairId` (#16), с тем же правилом якоря. Перевод, засчитанный в цель ПО КАРТЕ получателя,
второй раз через новую ногу не идёт; цель на счёте-источнике — другая сторона события, и приёмник
своё получает. Непарный банковский перевод, переименованный в расход, снимает деньги со второго
счёта тем же путём, что удаление, и забывает маску — иначе возврат типа снял бы их второй раз.

Дата: меняется день, время суток остаётся. У пары сдвигается только нога, записанная приложением:
у банковской строки дата — факт банка, а её «Остаток» — якорь для всего счёта.

Список счетов в карточке — только в валюте операции. Выбор хранится как id счёта: пустой выбор из-за
того, что счёта нет в списке, не означает «отвязать».

Правка — один `TransactionEditor` на оба экрана: у главной и «Операций» были две копии (#30).
### 40. «За всё время» считается по ВСЕЙ истории, а не по состоянию экрана
`AnalyticsState.transactions` — это операции ВЫБРАННОГО периода (чипы «месяц / квартал …»), а не
история. Плитка «Всего потрачено / всего заработано», посчитанная из них, показывала бы месяц под
заголовком «за всё время». Поэтому итоги живут отдельным полем `lifetimeTotals`, которое считается
по полному списку до фильтра. Общее правило: прежде чем брать список из чужого состояния, проверить,
чем он уже обрезан.

Правила экрана `LifetimeScreen` (`LifetimeStats`), общие для всех пяти блоков:
- **Переводы не считаются** ни тратой, ни заработком. Иначе каждое пополнение копилки попадало бы в
  «потрачено», а снятие — в «заработано».
- **Валюты не складываются.** Итоги — по каждой валюте; кривые, бары, доли и источники — в ОСНОВНОЙ
  валюте (`primaryCurrency`): той, в которой больше всего ОПЕРАЦИЙ, при равенстве — рубль. Жёсткий
  рубль оставил бы человеку с одними сомами плитку «0 ₽ / 0 ₽»; «рубль, если он есть хоть раз» —
  переключал бы весь экран на рубли из-за одной случайной покупки; оборот не годится, потому что
  копейки разных валют несравнимы. Прочие валюты — в итогах отдельной строкой.
- **Трата без категории — «Другое»** (`cat_other`), как на вкладке «Категории»: иначе одни и те же
  деньги на двух экранах лежат в разных долях.
- **Плитка и экран — одним фильтром** (`lifetimeTotals` отбрасывает будущие даты так же, как экран):
  плитка, показывающая одну цифру и открывающая экран с другой, — это то, чего #40 и запрещает.
- **Один горизонт на весь экран.** Итоги, график, бары, доли и источники всегда об одном отрезке;
  разные окна у соседних блоков давали бы цифры, которые не сходятся. Последняя точка кривой
  обязана совпадать с итогами — это закреплено тестом.
- **Пустые корзины не выбрасываются**: месяц без трат — горизонтальный участок, иначе время
  сжимается и рост выглядит круче, чем был.
- Источники группируются по **отображаемому** имени (`MerchantNames.display`): «RECR GOOGLE *ChatGPT,
  855-…» и «ChatGPT» — одна строка. Безымянные зачисления (зарплата) группируются по категории.
- Плитка — **единственный** вход на экран и стоит вне блока «Финансового здоровья», который
  показывается только при посчитанной оценке (инвариант #21).
- **Выбор на графике переживает пересчёт**, а точек может стать меньше: окно «Год» скользит, и после
  полуночи или нового пуша старая корзина выпадает. Индекс за краем — падение при рисовании; график
  отбрасывает его сам. Общее правило для любого выбора, хранящегося отдельно от списка.
- Смена шага пересчитывает **только кривую** (`computeBase` + `curveOf`), а не группировку всей
  истории по продавцам; оба расчёта — вне главного потока, окно — через `mapLatest`.

### 41. «Месяц к месяцу» сравнивает месяц со СВОИМ предыдущим — и первый месяц окна тоже
Главное требование: январь сравнивается с декабрём прошлого года. Поэтому расчёт (`MonthOverMonth`)
тянет данные на месяц дальше окна: у первого бара предыдущий месяц лежит за краем, и без этого
первому бару было бы не с чем сравниваться — или, хуже, он сравнивался бы с нулём и показывал
«новое». Считается всегда год; «6 мес» — его хвост, а не отдельный расчёт.

- **Полный месяц сравнивается с полным** — решение пользователя, а не упрощение. Текущий месяц не
  пересчитывается «на тот же день», а помечается незаконченным: бар полупрозрачный, «•» у подписи,
  строка «прошло 28 из 30 дней», и изменение показано СЕРЫМ. Частичная сумма против полного месяца
  почти всегда выглядит экономией, и зелёный выдал бы её за результат.
- **Цвет изменения — «лучше / хуже»**, не «больше / меньше»: рост трат красный, рост дохода зелёный.
- **Пустой месяц остаётся баром нулевой высоты.** Выбросить его — значит сравнить июль с маем и
  назвать это «месяц к месяцу».
- **Ушедшая категория видна в разбивке**: «было 5 000, стало 0» — тоже причина разницы.
- Выбор месяца хранится МЕСЯЦЕМ, а не индексом: при смене окна индексы сдвигаются.
- Данные — по всей истории, а не по чипу периода над вкладками (та же ловушка, что в #40), и
  ОТДЕЛЬНЫМ потоком (`AnalyticsViewModel.monthOverMonth`): в общем состоянии они пересчитывались бы
  на каждое касание чипа, на который не влияют.
- **Прежний блок «МЕСЯЦ К МЕСЯЦУ» ниже по вкладке удалён** вместе с `waterfallBars`: он складывал
  валюты, а новый нет, и под одним заголовком на одном экране стояли бы две разные цифры.
- **Упавший доход — янтарный (`Warning`), не красный**: красный — только траты и перерасход
  (правило #2). То же правило теперь у `MoMComparison`.
- На двенадцати барах над баром только стрелка: колонка ~22 dp, и «▲125» обрезалось бы до «▲12» —
  неверная цифра хуже отсутствующей. Полное изменение — в детализации; больше 999 % — «999+».
- «Против августа», а не «против августом»: после «против» — родительный падеж.

### 42. Банк описывается ОДНОЙ записью в `BankRegistry`
Имя, цвет, буква значка и ключевые слова банка жили в четырёх местах (`bankBrand`,
`BankSymbolBadge`, выбор банка, `AccountLinker.BANK_KEYWORDS`), и каждая копия успела разойтись:
МКБ и Цифры не было в выборе банка, значок читал «Gazprombank» как МБанк, а таблица привязки ждала
у Россельхозбанка id `rosselkhozbank`, тогда как разборщик называет себя `rosselkhoz` — операция РСХБ
без маски карты не привязывалась к счёту НИКОГДА. Новый банк = одна `BankSpec`.

- **Ключей два набора, и сливать их нельзя.** `aliases` узнают банк для оформления — ошибка стоит
  цвета, поэтому ключи широкие («мтс»). `linkKeywords` кладут операцию на счёт — ошибка стоит денег
  на чужом счёте, поэтому ключи узкие («мтс банк»). Объединение молча расширило бы привязку.
- **Порядок списка значим**: побеждает первое совпадение, а «Газпромбанк» содержит «мбанк».
  `every bank resolves to itself` ловит неверную перестановку.
- **`displayName` — это то, что сохраняется в `AccountEntity.bank`.** Переименовать банк в реестре
  значит разойтись со всеми уже заведёнными счетами; менять только вместе с миграцией.
- `parserId` берётся У РАЗБОРЩИКА (`BankParser.bankId`), а не пишется по памяти: тест проверяет, что
  у каждого из 13 разборщиков есть запись с непустыми ключами привязки.
- Цвет хранится числом ARGB: `core/` не зависит от Compose.
- `BankRegistryTest` держит дословные копии прежних функций и сравнивает с ними реестр на наборе
  реальных имён. Цвета и привязка совпадают целиком (кроме РСХБ). Буква значка берётся из тех же
  ключей, что и цвет, поэтому латинские имена, которых прежний значок не знал («Gazprombank» →
  было «М» по подстроке «mbank», «MTS Bank», «post bank», «rshb»), теперь получают букву своего банка.

### Реальные форматы пушей (проверено на устройстве)
Тела склеены так же, как их собирает `PushNotificationListener`: заголовок, затем текст, через
пробел. Все они закреплены тестами (`SberCreditPushTest`, `RealPushFormatsTest`) — менять тексты
нельзя, в этом их ценность.

| Банк | Текст | Как обрабатывается |
|---|---|---|
| Сбер | `Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽ Счёт карты МИР •• 6703` | `parsePush` → EXPENSE; «Баланс» = свободный лимит |
| Сбер | `Платёж по кредитной карте / Внесите платёж 373,98р до 31.08.26 …беспроцентным периодом.` | `CreditNoticeParser` → не операция, пишет сумму и дату |
| Сбер | `Перевод по СБП от АНДРЕЙ ВЛАДИМИРОВИЧ Л. + 1 200 ₽ — Счёт карты VISA •• 3387 "Перевод денежных средств"` | TRANSFER **входящий**, карта 3387 |
| Альфа | `Уведомление Перевод на сумму 8000.00 RUR из Кошелек ЦУПИС (Мобильная карта) от Андрей Л. по СБП.` | TRANSFER **входящий**; «RUR» — валюта, маски нет |
| Альфа | `-7 000 ₽. Перевод проведен Перевод 7 000,00 RUB со счета 4*1139 на счет 4*3583 проведен успешно` | TRANSFER исходящий, 1139 → 3583 |
| Альфа | `-200 ₽ Списание со счета 408*01139; Сумма: 200,00 RUB; Получатель платежа BKS Mir Investitsiy; 2 сентября 08:56` | разборщик: EXPENSE, счёт 1139, получатель «BKS Mir Investitsiy»; `ParserEngine` → TRANSFER «Инвестиции» (#43) |
| Альфа | `-10 000 ₽ Списание со счета 408*01139; Сумма: 10 000,00 RUB; Получатель платежа BKS Mir Investitsiy; 1 октября 07:53` | TRANSFER исходящий, «Инвестиции», счёт 1139 (`InvestmentTransfersTest`) |
| Сбер | `Шикарный перекус в DODO PIZZA PERM-5 1 034 ₽ — В запасе: 1 121,07 ₽ Счёт карты МИР •• 1238` | EXPENSE 1 034 (не 51 034), «DODO PIZZA PERM-5» (#46) |
| Сбер | `За кулинарные шедевры в R15173685 90 ₽ — В запасе: 764,90 ₽ …` | EXPENSE 90 (не 1 517 368 590) |
| Сбер | `Ловкость лапок и оплата по СБП удалась в AVPERM_SBP 760,32 ₽ — В запасе: …` | EXPENSE, не перевод |
| Сбер | `Выплата процентов + 77,23 ₽ — Баланс: 10 213,38 ₽ Накопительный счет •• 4958` | INCOME «Проценты» |
| Сбер | `🎉 Йуху! Деньги отправились в Альфа-Банк 6 000 ₽ — В запасе: … Плат. счёт •• 4102` | TRANSFER исходящий «Альфа-Банк» |
| Сбер | Погашение кредитки | **пуша нет вовсе** (подтвердил пользователь) — погашение вносится кнопкой «Погасить»; вывод погашения из роста свободного лимита отклонён пользователем |

Два правила, которые эти тексты продиктовали:
- **«Перевод … от …» — это ПРИХОД**, даже если между стеблем и «от» стоят слова («по СБП от»,
  «на сумму … из … от»). Раньше такие пуши падали в OUTGOING по слову «СБП», и пришедшие деньги
  записывались как ушедшие — с обратным знаком, что хуже потери операции. Правило требует
  ОТДЕЛЬНОГО слова «от»: иначе оно поймало бы приставку в «**От**правлен перевод».
- **`RUR` — тоже рубль.** Устаревший код, но Альфа шлёт им переводы по СБП. `detect` при
  ненайденной сумме возвращает null, поэтому перевод пропадал целиком.

### 43. Деньги брокеру — ПЕРЕВОД своих денег, а не трата
Альфа присылает пополнение брокерского счёта обычным списанием («Списание со счета 408*01139; …
Получатель платежа BKS Mir Investitsiy»), и оно ложилось расходом «Другое»: в траты, в бюджет, в
оценку финансового здоровья, в найденные «подписки». Деньги при этом никуда не делись.

- **Распознавание — одно место, `InvestmentTransfers`, и один проход в `ParserEngine.parse`** после
  разборщика банка. Поэтому живые SMS, пуши и 90-дневный импорт видят один ответ; импорт PDF
  вызывает тот же `isBroker` (#30: у каждого пути один список последствий).
- **По ПОЛУЧАТЕЛЮ, а не по тексту.** «Инвестиции» встречаются в рекламе и подписях банка; получатель —
  это ровно то, куда ушли деньги.
- **Признак — НАЗВАНИЕ брокера, а не общее слово.** Первая версия ловила «инвестиц» и «брокер», и
  ревью нашло цену: «Инвестиционно-строительная компания» (договор с застройщиком), «Инвестиционный
  банк» (кредит), «страховой брокер» становились переводами, а зарплата от «УК Инвестиционные
  решения» пропадала из дохода — обратный знак хуже потерянной строки. Теперь «инвестиции»/«брокер»
  — только рядом с названием брокера («Т-Инвестиции», «Открытие Брокер»), короткие названия (БКС,
  Финам) — только отдельным словом. Новый брокер — новая строка в `BROKER_PATTERNS` и в тесте.
- **Меняются тип и категория, а не сумма.** Списание → исходящий TRANSFER, зачисление от брокера →
  входящий, знак прежний. Поэтому баланс, дедуп по знаковой сумме (#14) и откат при удалении
  (`balanceEffectOf` от типа не зависит) работают как до правки.
- Категория задаётся ПРИ РАЗБОРЕ (`ParsedTransaction.categoryId`) и бьёт классификатор: модель
  про «Инвестиции» не знает (#18) и угадала бы что-нибудь своё.
- **Маршрутизатор не спаривает такой перевод** (`TransferRouter` + условие в `findTransferCounterpart`):
  вторая сторона — брокер, её среди строк нет, а «Сбер → Альфа 10 000» и через минуту «Альфа → BKS
  10 000» — обычный порядок пополнения, и пара склеилась бы с чужим переводом. Уведомления
  «перевод не распределён» тоже нет — известно, куда ушли деньги. Привязка к цели по счёту работает
  как раньше; привязки по КЛЮЧЕВОМУ СЛОВУ и карте получателя теперь видят и пополнение брокера (как
  любой исходящий перевод) — так цель «Инвестиции» можно пополнять словом «BKS».
- **Обязательство в календаре такой перевод закрывает** (`ObligationMatcher`): из «Свободно» он
  уводит деньги так же, как трата, а до разметки ежемесячное пополнение закрывалось расходом. Без
  этой ветки оно повисло бы просроченным. Обычный перевод между своими счетами по-прежнему не
  закрывает ничего.
- **`MIGRATION_20_21` переразмечает историю** — исключение из правила «категория задним числом не
  меняется», узкое, как у `MIGRATION_14_15`: только строки, всё ещё лежащие в машинной категории
  («Другое», пусто, «Прочие доходы»), и только через `needsRelabel`/`isBroker` в Kotlin, а не
  отдельный LIKE (два списка признаков разошлись бы, а LIKE не сворачивает регистр кириллицы, #13).
  **Восстановление копии зовёт ту же `relabel`**: копия, снятая до v21, иначе вернула бы все старые
  пополнения расходами.
- **Категории при восстановлении — `@Upsert`, не `@Insert(REPLACE)`.** REPLACE — это DELETE + INSERT,
  а `transactions.category_id` — `ON DELETE SET NULL`: каждое восстановление обнуляло категорию у ВСЕХ
  операций на устройстве (та же ловушка, что #1 с картами). Ошибка старая, но с v21 она стирала бы и
  пометку брокера — перевод без категории снова спаривался бы с чужим.
- `isInvestmentTransfer` требует И тип TRANSFER, И категорию: строка, которую человек перевёл в
  расход, — его выбор, и считается расходом.
- **Брокер показывается по-русски** (`brokerName` → `MerchantNames.display`): Альфа пишет получателя
  транслитом «BKS Mir Investitsiy», а в списке стоит «БКС». Исходный текст в операции не меняется —
  по нему ищет поиск, он виден в карточке. Признак и название — одна таблица `BROKERS`, поэтому
  «узнан как брокер» и «назван брокером» не могут разойтись.
- **Новости и акции приложения брокера** («Новая публикация BCS_Platform: ⚡ БКС Мир инвестиций…»)
  не разбираются: ни пополнения, ни заявки в них нет. Закреплено `BrokerPushParserTest`.

### 44. Режим «Инвестор» — другие деньги, и кошелёк их не видит ФИЗИЧЕСКИ
Пуши брокера (пополнение брокерского счёта, заявки, сделки) и сам брокерский счёт показываются
ТОЛЬКО в режиме инвестора, а покупки, переводы, карты и балансы банков — только в кошельке.

- **Отдельная модель, а не `TransactionEntity`.** `BrokerEvent` (`core/invest/`) не лежит в таблице
  операций. Иначе каждый из десятка запросов кошелька (аналитика, бюджет, оценка, «За всё время»,
  подписки, календарь) нуждался бы в фильтре — и однажды его бы забыли. Изоляция по построению, а не
  по дисциплине.
- **Деньги — копейки, цена бумаги — миллионные доли валюты**: биржа печатает «по 2.0985», копеек не
  хватает. Перевод в копейки — одна функция `microsToKopecks` с округлением.
- **Деньги двигает только исполненная заявка.** Одна заявка приходит несколькими пушами (активна →
  исполнена); в ленте — её последний статус, в портфеле — только исполнение.
- **Стоимость — по цене последней СВОЕЙ сделки**, и экран это пишет. Котировок у офлайн-приложения
  нет; выдавать цену сделки за рыночную нельзя. Комиссий в пушах нет — деньги на счёте приблизительны.
- **Размер лота пуш не сообщает** («4760 лотов LQDT»). По умолчанию лот = 1 бумага — у LQDT так и есть
  (4760 × 2.0985 = 9 988,86 ₽ при пополнении на 10 000). Для бумаги с лотом 10 стоимость будет занижена
  вдесятеро, пока лот не задан.
- **Форматы брокера не угадываются.** Вывод денег, продажа, частичное исполнение — только по реальным
  пушам: неверно разобранная сделка молча врёт в стоимости портфеля, а пропущенная хотя бы видна.
- **Имя пакета приложения брокера замечается, а не пишется по памяти.** Неверное имя — пуши молча не
  доходят. Служба уведомлений проверяет уведомления НЕЗНАКОМЫХ приложений `BrokerPushParser`-ом и
  сохраняет только имя пакета (`broker_package`), текст — нет. Экран инвестора показывает, найдено ли.
  Отсеиваются до чтения текста: само приложение, приложение SMS по умолчанию (БКС может дублировать
  пуш смской), «идущие» уведомления и сводки групп. Найденное НЕ перезаписывается (пересланный в
  мессенджер пуш переписывал бы имя при каждой пересылке) — запись условная, одной правкой
  (`setBrokerPackageIfAbsent`), а ошибочное имя сбрасывается на экране инвестора («Не то
  приложение?»). После находки или при выключенной службе чужие уведомления не читаются вовсе:
  `onNotificationPosted` идёт в главном потоке, поэтому оба выключателя держатся в `@Volatile`-полях
  службы, а не читаются из настроек на каждое уведомление каждого приложения.
- **Заголовок уведомления читается `getCharSequence`, не `getString`.** Заголовок с оформлением
  (Spanned) `getString` молча отдаёт null — и «LQDT: заявка исполнена» (а у банков — «Покупка»)
  пропадал из текста целиком. Правка общая для всех пушей, не только брокерских.
- Переключатель меряет свои подписи (`rememberTextMeasurer`) и, если не помещаются (кот в шапке,
  крупный системный шрифт), показывает значки 👛 / 📈 — обрезанное «Инвесто» хуже значка.
- **Что включено — видно цветом, а не только подписью.** Ползунок — выпуклая плашка (градиент, блик,
  тень), переезжает пружиной; кошелёк — светлое серебро, инвестор — индиго, цвет перетекает при
  переключении, рамка дорожки и свечение красятся цветом режима. Мятный для кошелька запрещён (#1).
  Все `animate*AsState` — без условий (#4); ширина сегментов равная, иначе ползунок менял бы размер.
- Переключатель — `UserPreferences.investorMode`; пока не прочитан (`null`), не рисуется ни переключатель,
  ни содержимое: показать инвестору кошелёк на долю секунды — значит показать не те деньги.
- Хуки экрана инвестора — в `DashboardScreen` ДО `LazyColumn` и без условий (#4); ветвление — внутри
  `LazyListScope` через `return@LazyColumn`, где правил хуков нет.
- **Пример — по кнопке и с пометкой.** Пока своих данных нет, экран умеет показать портфель,
  посчитанный тем же разбором и расчётом из реальных пушей БКС от 1 октября (`InvestorViewModel.sample`),
  с плашкой «ПРИМЕР · скрыть». Когда появятся данные, кнопка уходит вместе с пустым состоянием.

### 45. Незнакомая карта не ложится на счёт «по банку», если у счёта есть свои номера
У тестера в приложении один счёт Сбера — карта •• 8937. Пуши шли и по накопительному •• 0471, и по
картам •• 1238, •• 9334, •• 4102. `resolveAccountId` при ненайденном номере брал ЕДИНСТВЕННЫЙ счёт
банка — и все эти операции ложились на •• 8937, а их «Остаток» становился его балансом: «Баланс:
100 590 ₽» накопительного счёта на главной выглядел как деньги на дебетовой карте.

- **Запасной путь по банку закрыт ровно для одного случая** (`mayFallBackToBank`): номер в сообщении
  есть, он не совпал ни с одним номером счёта, у которого номера записаны, И сообщение несёт
  «Остаток». Только «Остаток» переписывает баланс — значит, только тогда чужая карта портит счёт.
  Такая операция остаётся без счёта и баланс не трогает. Без «Остатка» — прежнее поведение: Альфа
  пишет хвост номера СЧЁТА («Списание со счета 408*01139», остатка нет), а у счёта записан номер
  карты, и эти операции обязаны и дальше ложиться на счёт, двигать его дельтой и пополнять цели (ревью
  поймало именно эту регрессию в первой версии, где номер решал всё).
- **Без счёта — не значит потеряна.** Строка видна в истории с меткой «•• 1238», а главная
  показывает подсказку «Карты, которых нет в приложении» (`observeUnknownMasks`, `UnknownCardsHint`)
  с номерами: касание открывает новый счёт с подставленным номером, «Не добавлять» прячет эти номера
  (`dismissed_unknown_masks`), новый незнакомый номер появится снова. Без подсказки человек видел бы
  операции и не понимал, почему баланс стоит.
- **Добавленная карта забирает свои операции** (`relinkOrphans` → `rehomeFromBankFallback`): и
  висящие без счёта, и лёгшие на чужой счёт до правки. Переезжают только строки с «Остатком» — их
  сумма в балансе не лежит; без цели и без пары; не с кредитки (там при неизвестном лимите баланс
  шёл дельтами, #12). Строка остаётся, если прежний счёт сам владеет этим
  номером. Если текущий баланс прежнего счёта — «Остаток» переехавшей карты, он возвращается к своему
  последнему «Остатку»; иначе не трогается (цифра его собственная или ручная). Ручное «пересчитать»
  делает то же для всех номеров счёта; приём каждого пуша — нет, это лишний проход по таблице.
- Решение лежит в `resolveAccountId`, поэтому SMS, пуш и 90-дневный импорт ведут себя одинаково (#30).

### 46. Сумма пуша Сбера — по правилам денег, а название — без игривого заголовка
Прежний `pushAmtRe` начинал сумму с ЛЮБОЙ цифры и тянул через пробелы: «DODO PIZZA PERM-5 1 034 ₽» →
51 034 ₽, «R15173685 90 ₽» → 1 517 368 590 ₽ — у тестера «трат на сто миллионов». Строгий шаблон
(`pushAmtStrict`): 1–3 цифры, дальше группы РОВНО по три, и не сразу после буквы или цифры. Не нашёл
(сумма без разбивки «10000 ₽») — прежний шаблон: **каждое новое правило — сначала, старый путь —
запасной**, чтобы ни один пуш, разбиравшийся раньше, не пропал.

- **Игривые заголовки** («Котан, покупка в …», «🎉 Йуху! Деньги отправились в …», «За кулинарные
  шедевры в …») — `SberPushTitle`. Слово операции в начале («Покупка DNS») — прежний путь, один в
  один. Иначе: фраза до « в / из / прошла » с эмодзи, «!», запятой или узнаваемым словом отрезается.
  Без признаков текст остаётся целиком — «Кофе в зёрнах» не режется.
- **Оплата по СБП в магазине — покупка.** `TransferPatterns.OUTGOING` считает переводом любое «СБП»,
  поэтому «оплата по СБП удалась в AVPERM_SBP» Сбер разбирает пушем ДО `TransferPatterns`, если в
  тексте нет слова «перевод». Входящий «Перевод по СБП от …» идёт прежним путём.
- **«+» перед суммой — приход**, только отдельно стоящий: «СберПрайм+ 399 ₽» — название, не знак.
  Известные приходы названы словом: «Проценты», «Пенсия», «Зарплата», «Зачисление».
- **Деньги в банк — перевод, но только в игривом заголовке** («Деньги отправились в Альфа-Банк»,
  «Денежки уже в Яндекс Банк»): название с отдельным словом «банк» → исходящий TRANSFER. «Оплата Почта
  Банк» — платёж по кредиту, это трата, и она закрывает обязательство в календаре (перевод его не
  закрыл бы, #43). «Сбербанк» слитно и «Банкомат» — не банк.
- **Сумма не начинается после «.»/«,»**: иначе в «1500,00 ₽» строгий шаблон нашёл бы «00» — ноль, и пуш
  пропал бы; в «12345,67 ₽» — «67». Нулевая строгая сумма тоже уходит на прежний шаблон.
- **История чинится миграцией v21→v22** (`SberPushRepair`): строка меняется, только если она РОВНО
  такая, какой её записал прежний разбор (дословная копия старого алгоритма — защита, а не ответ), у
  неё есть «Остаток» (баланс задан банком, от суммы строки не зависит), нет цели и пары, и она не на
  кредитке (живой приём делает приход на кредитку погашением, миграция — сделала бы доходом). Сменился тип
  — категория подбирается заново. Затем расходы в «Другом» раскладываются по словарю тем же правилом
  первого совпадения, что у `DictionaryClassifier` (`ORDER BY priority DESC, rowid`). Ручные операции
  не трогаются: «Другое» у них мог выбрать человек.
- **Словарь** пополнен по выгрузке тестера: Яндекс Go, MAGNIT, Монетка, Fix Price, APTEKA, DODO, WB
  (отдельным словом — регулярным правилом r540, подстрока «wb» сидит в случайных латинских именах),
  ЯндексПлюс слитно, энергосбыт и др.
- Выгрузка CSV: переводы — «Перевод (исходящий/входящий)», а не «Доход»; колонка «Валюта»; сумма
  через `BigDecimal` — `Double.toString` писал крупные суммы экспонентой.

### 47. События брокера живут в `broker_events`, и счёт брокера — это не баланс
Пуши найденного приложения брокера (`broker_package`, #44) идут в `BrokerEventRepository.ingestPush`
и ТОЛЬКО туда: `PushNotificationListener` проверяет пакет брокера до `PACKAGE_TO_SENDER`, и в
`ParserEngine` кошелька они не попадают. Иначе «Перевод между счетами 189 RUB» банковский разбор
прочитал бы переводом в кошельке. Текст, который `BrokerPushParser` не узнал (новости, акции), не
сохраняется. Пуш, по которому приложение брокера было НАЙДЕНО, тоже записывается — иначе первое
пополнение пропадало бы. Повтор доставки — тот же текст в окне ±2 мин (`existsSameText`).

- **Одна плоская таблица на все виды событий**, вид — строкой (`CASH | TRANSFER | ORDER | ALERT`).
  Новый вид (вывод, купон) — новое значение, а не правка схемы. Перевод строка ↔ событие — только
  `BrokerEventMapper`, и тест гоняет каждый вид туда и обратно: потерянное поле молча врало бы в
  портфеле. Строка неизвестного вида пропускается, а не роняет экран.
- **Перевод между счетами брокера итог не меняет** (`BrokerInternalTransfer`): деньги остались у
  того же брокера. Записать его пополнением значило бы посчитать их дважды.
- **Остаток по счёту брокера не считается** (решение пользователя): пуш о сделке не пишет, с какого
  счёта она прошла, и угаданный остаток врал бы. Счета (`Portfolio.contracts`) узнаются из пушей —
  номер и название в скобках, «3468071/25 (Облигации)». В выбранном счёте видны его движения
  («пришло · ушло по пушам») и предупреждения; бумаги и сделки — только во «Всём портфеле», и экран
  это пишет. Номер сравнивается через `contractKey` (без «№», без регистра — в номере кириллица).
- **Предупреждение брокера — факт о счёте, а не движение денег** (`BrokerMarginAlert`), как
  напоминание банка о платеже (#38). Закрывается деньгами, пришедшими на ЭТОТ счёт НЕ РАНЬШЕ
  предупреждения: пополнением или переводом с другого счёта (`MarginAlerts.incomingAfter`). Брокер
  считал цифру, уже зная о прежних деньгах. Частичное пополнение уменьшает остаток требования.
  Повтор с той же суммой — одна карточка; повтор ЗАКРЫТОГО вручную требования открывает карточку
  снова (брокер повторяет — значит, не оплачено); новая сумма по тому же счёту заменяет старую.
  Открытые предупреждения видны при ЛЮБОМ выбранном счёте — требование по соседнему счёту не
  прячется за выбором. «Закрыть» — для того, чего приложение не увидит. Без суммы в тексте
  предупреждение не разбирается: «пополните на ?» хуже отсутствующей карточки.
- **Цвет предупреждения — янтарный (`Warning`), не красный**: красный — траты и перерасход (#2).
- **Точка на «Инвестор» в переключателе** горит, пока предупреждение открыто и открыт кошелёк. Это
  единственное, что кошелёк знает о брокере, — знак «загляни», без сумм и текста (#44).
- **Резервная копия несёт `brokerEvents`** (девятый набор): пуши брокера второй раз не придут.
  Восстановление — `IGNORE` по ключу, поэтому закрытое на устройстве предупреждение старая копия
  не откроет. Копия до v23 набора не несёт — ничего не стирается.
- Пример на экране — только пока своих событий нет; появились свои — пример уходит сам.
- **«Не то приложение? Сбросить»** доступно и при данных (строка внизу экрана) и удаляет события,
  записанные от этого пакета (ключ строки начинается с имени пакета): пересланный в мессенджер пуш
  иначе остался бы в портфеле навсегда.
- Пока служба не прочитала настройки (только что поднята), пуш незнакомого пакета проверяется в
  корутине по самим настройкам, а не теряется. Пуш брокера — тоже признак жизни службы
  (`markPushSeen`).

### 48. Экран инвестора повторяет структуру БКС, но не выдаёт свои цифры за рыночные
Решение пользователя по скриншотам БКС: главный блок — выбор счёта, крупная сумма, пилюля
«результат · % за всё время», кнопки «История» и «Заявки»; ниже — сворачиваемые группы «Валюта /
Акции / Облигации / Фонды / Внебиржевые активы / Прочее», строка бумаги «4 760 шт. · 2,0985 ₽ →
2,0985 ₽», стоимость, результат в ₽ и %.

- **Сумма — по цене ВАШИХ сделок, и это написано под ней.** У БКС — по рынку, поэтому цифры
  расходятся; без подписи расхождение выглядело бы ошибкой приложения. «→ текущая цена» до
  котировок из сети равна цене последней своей сделки.
- **Группа — по тикеру** (`SecurityGroups`): справочник фондов, валютные инструменты биржи
  («USD000UTSTOM», «CNYRUB_TOM»), ОФЗ и ISIN «RU000A…», «3800_HK» — внебиржевые, четыре буквы (+P) —
  акции. Не узнали — «Прочее». Единственная догадка — «четыре буквы = акция»: фонд, которого нет в
  `FUNDS`, ляжет в «Акции» (цифры верны, неверна полка). Новый фонд — строка в `FUNDS` и в тесте.
- **Свободные деньги могут уйти в минус** — комиссий в пушах нет; строка тогда подписана
  «без комиссий, приблизительно», а не «свободные деньги».
- Кнопки главного блока — `fosCardSurface` → `clickable` → `padding`: при `fosInset().clickable()`
  поле вокруг подписи не нажималось бы.
- **Свободные деньги лежат в «Валюте»**, как у БКС («Российский рубль 203,68 ₽»); итог группы «Валюта»
  совпадает с «деньгами» портфеля — закреплено тестом.
- **Валюты не складываются** и в группе: сумма — строкой на валюту.
- «История» и «Заявки» — листы (`InvestorSheets`), а не разделы главной: длинная лента отодвигала
  портфель вниз. Лента истории — одна функция `historyFeed` и для листа, и для выбранного счёта.
- Состояние «свёрнута» у группы — `rememberSaveable` внутри элемента с ключом `grp_<группа>`.
- **Скругление режет текст.** `clip(RoundedCornerShape(RadiusChip))` (20 dp) на строке или ссылке,
  у которой нет бокового отступа больше радиуса, срезает углы первой буквы и цифры: на устройстве
  «Валюта» читалось как «ʙалюта», «9 988,86» — как «ↄ 988,86». У ссылок и строк-заголовков — без
  `clip` (рябь прямоугольная), у чипов — боковой отступ не меньше половины их высоты.
- Кнопки «История» / «Заявки» — без значков (решение пользователя), подпись по центру.
- **Пилюля результата — с выбором «24 часа / Месяц / Всё время»** (`Portfolio.ResultPeriod`, как у
  БКС). Результат за период = изменение ВСЕГО у брокера (бумаги + деньги) минус деньги, заведённые
  за период (`periodResults`); процент — от того, что было в начале, плюс заведённое. Без вычета
  пополнение на 10 000 показывалось бы как +10 000 «за день». «Месяц» — календарный, от этого же
  числа прошлого месяца. Без котировок стоимость меняется только на своих сделках, поэтому между
  сделками результат за период — ноль, и под пилюлей это написано. «Всё время» теперь тоже по этой
  формуле (итог минус заведённое) и включает зафиксированный результат продаж.

### 49. Всё, что приходит пушем, человек может ввести и удалить сам
Пуши брокера могут не прийти вовсе (служба отключена, приложение брокера ещё не найдено, пуш
смахнули), прийти не все или в формате, которого разбор не знает. Портфель, собранный только из
пушей, в таком случае врёт молча. Поэтому в режиме инвестора есть «Добавить» (главный блок и
пустое состояние) → операция / актив / счёт, а удалить можно любое событие — и ручное, и из пуша.

- **Ручной ввод — те же события, что и пуши** (`ManualEntry` → `BrokerEventRepository.addManual`):
  пополнение и вывод — `BrokerCashMove`, перевод — `BrokerInternalTransfer`, покупка и продажа —
  исполненная `BrokerOrder` в ШТУКАХ (лот = 1). Поэтому портфель, результат за период, группы и
  предупреждения считают их без единой ветки «а если вручную».
- **Ручная запись — одна группа id** (`manual_<uuid>_<n>`) и удаляется целиком
  (`deleteEvent` по префиксу группы). Это нужно активу: «Добавить актив» пишет ПОПОЛНЕНИЕ на его
  стоимость И ПОКУПКУ — иначе свободные деньги ушли бы в минус на стоимость бумаги, а результат
  «за всё время» показал бы её доходом. Удалить одну покупку без денег значило бы вернуть ту же
  ошибку. Событие из пуша удаляется одной строкой.
- **Счёт — отметка `BrokerAccountMark` в той же таблице** (вид `ACCOUNT`), а не новая таблица:
  без миграции и без отдельного набора в копии. Строка одна на счёт (`acct_<брокер>_<номер>`,
  `@Upsert`): «завёл» и «удалил» её перезаписывают, действует последняя. Удалённый счёт скрыт, даже
  если пуш его упоминает; операции по нему остаются в истории и в итоге — их удаляют отдельно, и
  диалог это говорит. Id счёта НЕ начинается с `manual_`: удаление ручной записи по префиксу группы
  снесло бы все счета брокера.
- **Цена — отметка `BrokerPriceMark`** (вид `PRICE`): меняет только «текущую цену» уже купленной
  бумаги, не количество и не деньги. Котировок нет (бэклог), и без неё стоимость стояла бы на
  цене последней своей сделки вечно. Цена бумаги, которой нет, не делает ничего.
- **У сделки есть счёт** (`BrokerOrder.contract`): пуш его не пишет, ручной ввод знает.
- Нажатие на операцию в «Истории»/«Заявках» и на выбранном счёте — подтверждение удаления; на бумагу
  в группе — лист «Указать цену / Удалить актив»; в выборе счёта — «Удалить» и «+ Добавить счёт».
  У примера id нет — его строки не нажимаются.
- Ручная операция помечена «· вручную», сделка — в «шт.», а не «лот.».
- **Удаление заявки из пушей снимает всю её цепочку** («активна → исполнена/отменена»: та же бумага,
  сторона и лоты, не позже удаляемой строки). Удали только последний статус — и «активна» ожила бы:
  на экране «Заявки · 1» по несуществующей заявке. Активную заявку удалить нельзя — её следующий пуш
  ещё придёт.
- **Счета примера удалить нельзя**: пример собран из настоящих пушей, и «скрыть» его счёт значило бы
  скрыть настоящий счёт человека навсегда. Подсказка «нажмите, чтобы удалить» у примера не
  показывается — его строки не нажимаются.
- Цена × количество проверяется `multiplyExact`: абсурдный ввод не переполнит `Long` в
  отрицательную сумму.
- Удалённый пуш не вернётся тем же пушем (ключ дедупа — строка), но восстановление СТАРОЙ копии
  вернёт его: копия добавляет строки (`IGNORE`), а не повторяет удаления.

### 50. У режима «Инвестор» своя нижняя панель, и «Портфель» — посередине
Решение пользователя: пять вкладок — Операции · Аналитика · **Портфель** · Календарь · Счета;
«Портфель» посередине, крупнее остальных и всегда подсвечен индиго (круг с градиентом, как ползунок
переключателя); переключатель «Кошелёк | Инвестор» остаётся сверху, в шапке «Портфеля».

- **Панель выбирает `FosNavHost` по режиму** (`InvestorViewModel` на уровне хоста: `investorMode`,
  `hasOpenAlert`). Пока режим не прочитан — панели нет совсем: вкладки кошелька на миг у инвестора —
  это «не те деньги» (#44). Вкладки кошелька и инвестора не смешиваются.
- **«Портфель» — это `Dashboard`** в режиме инвестора, а не новый маршрут: там переключатель режима, и
  обе панели ведут на одну и ту же главную. Остальные — `invest_ops / invest_analytics /
  invest_calendar / invest_accounts` (не deep-link).
- **Выбранный счёт — общий для вкладок** (`InvestSelection`, `@Singleton`): у каждой вкладки свой
  `InvestorViewModel`, и «Открыть в портфеле» на «Счетах» иначе не дошло бы до «Портфеля».
- **Вкладки работают только со СВОИМИ данными.** Пример — только на «Портфеле»; ни правка, ни
  аналитика к нему неприменимы.
- **Всё считается одним `Portfolio.compute`** — «Аналитика» и «Портфель» не могут разойтись.
  «Состав» — в основной валюте портфеля (где больше всего денег), валюты не складываются.
- **Ручной ввод на вкладках — те же листы** (`InvestManualOverlays` + `InvestManualState`), что и на
  «Портфеле» (#49).
- «Операции»: фильтр по виду (Все / Деньги / Сделки) и по счёту; пустой список при фильтре говорит
  «под фильтр ничего не попало» (#26). Сделки без счёта (пуш его не пишет) видны только во «Всех
  счетах».
- «Календарь» честно пуст по выплатам (купоны, дивиденды, погашения не угадываются) и показывает то,
  что уже требует действия: открытые предупреждения брокера.
- Янтарная точка открытого предупреждения — на «Портфеле» в панели (карточка живёт там) и на
  «Инвесторе» в переключателе, когда открыт кошелёк.
- **Стек экранов не смешивает режимы.** Вкладки делают `popUpTo(Dashboard)`, а не
  `findStartDestination()`: стартовый экран — онбординг, он убирает себя из стека, и `popUpTo` к
  отсутствующему экрану не делал ничего — каждая вкладка ложилась поверх, и после переключения
  режима «Назад» открывал операции брокера в кошельке. Смена режима снимает стек до главной, а панель
  показывается только на вкладках ТЕКУЩЕГО режима.
- **`fillMaxHeight()` внутри `NavigationBar` запрещён.** У панели только МИНИМАЛЬНАЯ высота, а
  `Scaffold` отдаёт `bottomBar` весь экран: v0.1.0.62 растянул панель инвестора на весь экран, и
  человек не мог ни переключить режим, ни выйти. Вертикальное выравнивание — `align(CenterVertically)`.
- Строки ленты на вкладках — с ключом по id события, не по индексу (#4). «Состав» — в валюте, где
  больше всего бумаг, без отрицательных денег; целое — сумма показанных долей.

### 51. Ручная запись инвестора ПРАВИТСЯ, и деньги у брокера бывают не в рублях
Человек ошибся во вводе — единственным выходом было удалить и ввести заново (правилась только цена
бумаги). Теперь нажатие на операцию открывает КАРТОЧКУ ПРАВКИ (`InvestManualState.editing`), а
удаление — внизу неё. «Портфель», «Операции» и листы «История»/«Заявки» ходят в одну карточку:
главная больше не держит своих копий листов, а зовёт тот же `InvestManualOverlays` (#30).

- **Правится ЗАПИСЬ, а не строка** (`InvestorViewModel.recordOf` → `ManualEntry.draftOf`). Актив —
  это пополнение на стоимость + покупка (+ цена): правка одной покупки развела бы деньги и бумагу, и
  свободные деньги ушли бы в минус. Ручная запись переписывается целиком под ТЕМ ЖЕ префиксом группы
  (`BrokerEventRepository.replace`), поэтому удаление потом снова уберёт её целиком.
- **Пуш правится на месте**: тот же id и тот же исходный текст. По тексту ловится повторная доставка
  (`existsSameText`) — без него тот же пуш, пришедший снова, лёг бы второй строкой. Статус и подпись
  брокера у заявки из пуша сохраняются (`keepPushedOrder`): правка цены отменённой заявки не делает её
  исполненной; количество в ней — лоты, и поле так и подписано.
- **Время не сбрасывается на «сейчас»** (`editedTimestamp`): тот же день — тот же миг до миллисекунды,
  другой день — то же время суток. Иначе правка опечатки переставляла бы операцию в истории и двигала
  результат «за 24 часа».
- **Сохранить можно только изменённое** — кнопка неактивна, пока форма совпадает с записью, а выход
  без сохранения спрашивает подтверждение только при правке (#22).
- **Валюта — выбор, а не рубли по умолчанию.** Пополнение, вывод, перевод и сделка вводятся в RUB / USD
  / CNY / EUR / HKD с дробной суммой. Остаток валюты БКС держит под тикером «USD000SMALL» /
  «CNY000SMALL» (центы и фэни лотом не купишь): такой тикер в «Активе» записывается ПОПОЛНЕНИЕМ в своей
  валюте (`SecurityGroups.cashCurrency`, `ManualEntry.currencyCash`), а не бумагой в штуках. Валюты по-
  прежнему не складываются: у главного блока строка на валюту.
- **`currencySymbol` знает юань (¥), гонконгский доллар и фунт.** Раньше всё незнакомое печаталось
  «₽» — 41,60 юаня выглядели бы рублями.

- **Правка пуша-заявки меняет только цену, счёт и валюту** (`keepPushed`). Бумага, сторона, лоты и
  время связывают её с прежними пушами той же заявки («активна → исполнена»): смени их — и «активна»
  стала бы последним словом заявки и ожила на экране. У пуша-перевода сохраняются названия счетов
  («Облигации») — их пишет только брокер. Неизменённая текущая цена актива сохраняет своё время, а не
  «сейчас». Перенос записи на сегодня не уводит её в будущее. Ручная запись переписывается одной
  транзакцией (`withTransaction`).
- **Карточка бумаги показывает её СДЕЛКИ** (`BrokerPositionSheet.trades`). Позиция — сумма сделок, и
  исправляется не она, а сделка: без списка из карточки бумаги нельзя было попасть ни к одной правке
  — человек видел только «Указать цену» и «Удалить актив» и считал, что правка не работает.
  Нажатие на сделку закрывает карточку бумаги и открывает карточку правки.
- `cashCurrency` узнаёт только остаток («…000SMALL») и голый код: «USD000UTSTOM» — инструмент,
  купленный лотами за рубли, это позиция, а не доллары на счёте.

### 52. Рыночная заявка приходит БЕЗ цены — и это всё равно сделка
«LQDT: заявка исполнена Рыночная заявка на покупку 20 лотов LQDT» (снято 5 октября) — без «по …».
`orderBody` требовал цену, пуш возвращал `null` и молча терялся: покупка не попадала ни в портфель, ни
в ленту, а деньги на счёте оставались «свободными».

- **Цена необязательна в разборе**, `BrokerOrder.UNKNOWN_PRICE` (0) — «не пришла». Цена, которая
  есть, но не читается, — по-прежнему не тот формат. Тикер без «по …» обязан кончаться словом.
- **Портфель оценивает её по последней известной цене бумаги** (своя сделка или указанная вручную) и
  кладёт в `Result.estimates`; строка сделки пишет «≈ по …» и янтарную подсказку. Если по бумаге не
  известно НИЧЕГО — сделка в портфель не идёт (`Result.unpriced`), в ленте видна с «цена не пришла»:
  выдуманная цена врала бы и в бумагах, и в деньгах. Точная цена — в карточке правки (#51): поле цены
  у такой сделки пустое, а не «0».
- **Удаление пуша-заявки снимает только СВОЮ цепочку**: строку и «активна» перед ней, но не дальше
  прошлой завершённой заявки того же вида. Рыночная и лимитная покупки по 20 лотов за минуту — две
  заявки; прежнее правило «тот же тикер и лоты, не позже» удаляло бы обе.
- Пуш, пришедший ДО этой версии, потерян: разбор его не записал. Его вводят вручную.

### 53. Пополнение брокера — одно событие с двух концов, и склейка ВЫЧИСЛЯЕТСЯ, а не хранится
«Альфа −10 000 → BKS» в кошельке (#43) и «Вы пополнили счёт №… на 10 000 RUB» у брокера (#47) — одно
пополнение. `DepositLinks.link` склеивает их заново из обеих историй при каждом изменении; ссылки нет
ни в одной таблице, поэтому правка или удаление любой стороны не оставляет осиротевшей связи и
миграция не понадобилась.

- **Пара — только точная**: та же сумма до копейки со встречным знаком, та же валюта, тот же брокер
  (`InvestmentTransfers.brokerName` = `BrokerCashMove.broker`), окно трое суток. Ближайшие по времени —
  первыми, каждая сторона — один раз: два пополнения по 10 000 за неделю — две пары.
- **Перевод без пары — ПРЕДЛОЖЕНИЕ, а не запись** (карточка «Перевод брокеру без зачисления» на
  «Портфеле»: «Записать пополнение» / «Это не пополнение»). Молча дописанное пополнение посчиталось бы
  дважды, когда пуш брокера всё-таки придёт (#20: ошибки несимметричны). Записанное — обычная ручная
  запись (#49), и склейка тут же находит ей пару, поэтому предложение уходит само. «Это не
  пополнение» помнится в `dismissed_broker_legs`.
- **Предлагается не всё**: перевод моложе ОКНА склейки (трое суток — пуш ещё может склеиться; запись
  на второй день заняла бы пару, и пуш третьего дня посчитался бы вторым) и перевод раньше первого
  ПРИШЕДШЕГО ПУШЕМ события брокера (портфель тогда не вёлся; ручной актив «куплен в 2023-м» этот
  момент не сдвигает) не предлагаются. Без пушей брокера не предлагается ничего. Время идёт само —
  склейка пересчитывается раз в час, а не только при изменении данных. Нажатая кнопка карточки
  исчезает сразу: двойное касание записало бы два пополнения.
- **Кошелёк видит ИТОГ брокера и только его** — плитка «У брокера» под «Свободно». Это осознанное
  расширение #44: без неё перевод брокеру уводил деньги из итога кошелька «в никуда». В «Всего» итог
  не входит (стоимость — по цене своих сделок, не по рынку); бумаги, сделки и результат кошелёк
  по-прежнему не видит. Валюты — строкой на валюту.
- Склейка — в `InvestorViewModel.links`: кошелёк отдаёт ей только СВОИ переводы с «Инвестициями».
  У пополнения в ленте инвестора — «из Альфа-Банк •• 1139».

### 54. Карточка категории считает ТОТ ЖЕ период, что строка, по которой нажали
Строка «Покупки · 64 672 ₽» при чипе «Год», а внутри карточки — «Этот месяц 0 ₽ / Прошлый месяц
7 775 ₽» и пять сентябрьских операций; у «Развлечений» — 12 800 ₽ в строке и «0 ₽ / 0 ₽» внутри.
`categoryOperations` намеренно игнорировал чипы («сравнение месяц к месяцу — фиксированный вопрос»), и
это было ошибкой: человек нажимает на ЦИФРУ и ждёт увидеть, из чего она сложилась.

- Окно — одна функция (`AnalyticsWindows.current`) и для диаграммы, и для карточки; список операций
  карточки обязан сходиться с суммой строки.
- Сравнение — с таким же отрезком ПЕРЕД окном (`previous`): месяц с прошлым месяцем, полгода с
  прошлым полугодием, год с прошлым годом; у «Всего времени» сравнения нет.
- Список прежнего отрезка — только у месяца; у полугода и года сравнение дано суммой.
- Сравнение «месяц к месяцу» по категориям по-прежнему есть на «Трендах» (#41) — для этого карточка
  не нужна.

### 55. Котировки Мосбиржи — раз в сутки, по переключателю, и с честным временем
Стоимость портфеля стояла на цене последней СВОЕЙ сделки, а результат «за 24 часа» между сделками был
нулём. Теперь — открытый ISS Мосбиржи (iss.moex.com, без ключа, с задержкой 15 минут; без задержки —
только платно или через API брокера с доступом к счёту, это отклонено). Решения пользователя: раз в
сутки достаточно, итог — в рублях, как у БКС, валюта — только внутри «Валюты».

- **Выключено по умолчанию** (Настройки → «Инвестиции» → «Котировки Мосбиржи»): первый выход
  приложения в сеть за ДАННЫМИ. Уходят только тикеры своих бумаг — без сумм, количества и счетов.
  Включили — сразу запрос; дальше `MarketQuotesWorker` раз в сутки при сети, и экран инвестора
  догоняет устаревший снимок при открытии (`refreshIfStale`, старше ~20 ч).
- **Разбор — по ИМЕНАМ колонок ISS**, а не по позициям: биржа добавляет колонки, позиционный разбор
  читал бы чужое поле. Режим торгов — основной (`TQBR` акции, `TQTF/TQTD/TQTE/TQIF/TQPI` фонды,
  `TQOB/TQCB/…` облигации), не «неполные лоты» `SMAL`. Вне сессии `LAST` пуст — берётся текущая цена,
  потом цена закрытия. Облигация = номинал × % / 100 + НКД. Сломанный ответ — пустой результат, старый
  снимок остаётся.
- **История цен по дням** (`MarketQuotes.Cache.history`, ~400 дней, одна точка в сутки) — чтобы
  «24 часа» и «месяц» считались по рынку: прошлое состояние портфеля оценивается ценой на тот момент.
  Цена прошлого закрытия кладётся точкой сутками раньше — «24 часа» работают с первого обновления.
- **Своя цена новее биржевой — побеждает она** (сделка или «Указать цену» после снимка).
- **Лот с биржи умножает только ПУШИ** (там «20 лотов»); ручной ввод уже в штуках (#49).
- **Группа бумаги — по режиму торгов**, справочник #48 остаётся запасным.
- **Итог в рублях** (`Result.totalRubKopecks`, `periodRub`) — по биржевым курсам USD/CNY/EUR/HKD
  (`USD000UTSTOM`, `CNYRUB_TOM`, …). Нет курса хоть одной валюты — по строке на валюту, как раньше
  (#40: без курса не складывать). Плитка «У брокера» в кошельке — тоже одной суммой в рублях.
- Подпись под суммой — «по ценам Мосбиржи на ДД.ММ ЧЧ:ММ»: снимок суточный, «сейчас» было бы неправдой.
- Кэш — файл `moex_quotes.json` (запись через временный файл), не база: котировки — улучшение, а не
  данные человека; в резервную копию не идут, после восстановления обновятся сами.
- `org.json` в юнит-тестах — настоящий (`testImplementation("org.json:json")`): в `android.jar` он
  заглушка, и тест разбора упал бы на «Stub!».

## Planned — Account Types & Card UI (NOT implemented)
Full spec: `docs/CONTEXT.md` → "Roadmap — Planned Features".
1. ~~Bank registry refactor~~ — **сделано**, `core/bank/BankRegistry`, инвариант #42.
2. **Branded card UI** — per-bank gradient/logo `CardSkin` (trademark caveat for stores).
3. **Brokerage accounts** — `AccountKind.INVESTMENT` (column exists, nothing consumes it yet):
   separate subtotal, excluded from cash net worth.

---

# Key File Locations
| Layer | Path |
|---|---|
| Theme | `app/src/main/kotlin/com/financeos/hub/ui/theme/` |
| Components | `app/src/main/kotlin/com/financeos/hub/ui/components/` |
| Database | `app/src/main/kotlin/com/financeos/hub/core/database/` |
| Analytics | `app/src/main/kotlin/com/financeos/hub/core/analytics/` |
| Parsers | `app/src/main/kotlin/com/financeos/hub/core/parser/` |
| Features | `app/src/main/kotlin/com/financeos/hub/features/` |
| DI Modules | `app/src/main/kotlin/com/financeos/hub/di/` |
| Служба пушей | `app/src/main/kotlin/com/financeos/hub/core/notifications/` |
| Тесты | `app/src/test/kotlin/com/financeos/hub/` (53 файла, 591 случай) |

# Design Reference
- Technical spec, schema, formulas, screen contracts: `docs/CONTEXT.md`
- User-facing overview: `README.md`
- Goal art generation prompts: `docs/GOAL_ART_PROMPTS.md`
- `docs/DESIGN_HANDOFF.md` — **архив** (бриф на дизайн-ревью, август 2025). Концепция «Мерцание»
  по нему реализована; как источник о текущем состоянии не годится и помечен об этом сверху.
- Colour tokens: `FosColors.kt` · Typography: `FosType.kt`
- Огранка карточек: `ui/theme/FosSurface.kt` · Заголовки и «?»-пояснения: `ui/components/FosSection.kt`
