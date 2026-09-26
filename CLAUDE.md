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
- **DB schema:** Room v19
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
│   │                  CreditNoticeParser, AmountParser, MerchantNames, CyrillicRegex/ciRegex)
│   ├── classifier/   (DictionaryClassifier, CategoryDefaults)
│   ├── sms/          (SmsReader, SmsReceiver — ТОЛЬКО SMS)
│   ├── auth/         (BiometricHelper)
│   ├── account/      (AccountLinker)
│   ├── credit/       (CreditMath — debt, free limit, cycle, min payment, due payment;
│   │                  CreditNoticeApplier)
│   ├── transfer/     (TransferRouter)
│   ├── finance/      (SavingsMath — вклад: прогноз, срок, требуемый взнос;
│   │                  GoalPlan — копилка без процентов: остаток, взнос к сроку, свой темп)
│   ├── calendar/     (CalendarEvent, PaymentDates, CalendarBuilder, FreeMoney, ObligationMatcher,
│   │                  ObligationSyncer)
│   ├── analytics/    (AnalyticsEngine, ScoreCalculator, InsightGenerator,
│   │                  BehavioralAnalyzer, NarrativeEngine, AnalyticsWorker)
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
│                      subscriptions, categories, credit, onboarding, settings, auth/LockScreen)
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

## Amounts Storage
- Store as `Long` kopecks (×100), convert to Double only in `FosFormatter`
- Negative kopecks = expense, positive = income, TRANSFER signed by direction
- `currency` is per-transaction (not just per-account) — RUB/USD/EUR/KGS

## SMS Deduplication
`smsId = "${sender}_${timestamp}_${body.hashCode()}"` — checked before insert, then
`existsSimilarSmsOrPush(signedAmount, ±5 min)` catches the SMS↔push twin of the same event.
Сумма **знаковая**, не по модулю — см. инвариант #14, иначе вторая нога перевода между своими
счетами молча съедается.

## Categories (18)
13 расходных + 3 доходных + «Букмекер» + «Подписки». Список **append-only** — см. инвариант #9.
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
- [x] 12 bank parsers + `ParserEngine` (@IntoSet DI) + `TransferPatterns` + `PromoFilter`
- [x] `SmsReceiver` (real-time, `goAsync`), `SmsReader` (90-day import), `PushNotificationListener`
      (reads **every** notification text extra)
- [x] Живучесть привязки службы уведомлений (`core/notifications/`): `ListenerHealth` отличает
      ВЫДАННОЕ РАЗРЕШЕНИЕ от РАБОТАЮЩЕЙ службы, `ListenerRebindReceiver` чинит привязку после
      обновления APK и перезагрузки, `ListenerWatchdogWorker` раз в час просит мягко,
      `ListenerNotice` сообщает, если жёсткий перезапуск сбросил разрешение — см. инвариант #28
- [x] SMS is **opt-in** (`sms_realtime_enabled`, default false)
- [x] `DictionaryClassifier` (~216 rules, 18 категорий), `CategoryDefaults.forType` income fallback.
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
      detail/edit sheet with source diagnostics, «↑ Экспорт» CSV, «↓ Импорт» PDF, manual add incl.
      **Перевод** with destination account). На каждой строке — метка источника «•• 6703» в цвете
      банка, тихая у доминирующего счёта и цветная у редкого (см. правило огранки #7)
- [x] Analytics (period chips + 4 tabs — see README for the per-tab breakdown)
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
- [x] Backup/restore — 8 наборов (accounts, cards, categories, goals, budgets, routes,
      **planned**, transactions) → файл **открытого JSON**, restore additive + FK-safe.
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
- `MkbParser` не покрыт тестами (13-й банк, добавлен вместе с отображением карт).
- Резервная копия — открытый JSON (инвариант #27); класть её в общую папку нельзя.
- Пополнение брокерского счёта считается расходом — см. «Инвестиции» ниже.

---

# Next Steps
- **Инвестиции** — `AccountKind.INVESTMENT` + `EventKind.INVESTMENT` (место в календаре уже
  зарезервировано: нужна одна функция `fromInvestments(...)` в `CalendarBuilder`).
  Начинать разумно с РАСПОЗНАВАНИЯ, а не с экрана: пополнение брокерского счёта («Получатель
  платежа BKS Mir Investitsiy») сейчас считается тратой и занижает финансовое здоровье, хотя деньги
  никуда не делись. Это дешёвая правка с немедленным эффектом; экран и события в календаре — вторым
  заходом.
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

**Срок платежа переехал с главной в календарь.** Плитка кредитки считала его сама из `AccountEntity`,
а главный экран читает только текущий месяц (`observeCurrentMonth`): платёж, сделанный до первого
числа, для неё не существует, и она продолжала бы подгонять уже оплаченную карту. Теперь
`CalendarState.nextCreditDueInDays` — единственный источник, тот же, что у «Свободно», и обе плитки
всегда согласны. `CreditSummary.daysUntilDue` из модели УДАЛЁН, а не оставлен пустым: поле, которое
владелец всегда заполняет null, — это следующий #29.

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
| Альфа | `-200 ₽ Списание со счета 408*01139; Сумма: 200,00 RUB; Получатель платежа BKS Mir Investitsiy; 2 сентября 08:56` | EXPENSE, счёт 1139, продавец «BKS Mir Investitsiy» |
| Сбер | Погашение / зачисление | **формат неизвестен**, ждём

Два правила, которые эти тексты продиктовали:
- **«Перевод … от …» — это ПРИХОД**, даже если между стеблем и «от» стоят слова («по СБП от»,
  «на сумму … из … от»). Раньше такие пуши падали в OUTGOING по слову «СБП», и пришедшие деньги
  записывались как ушедшие — с обратным знаком, что хуже потери операции. Правило требует
  ОТДЕЛЬНОГО слова «от»: иначе оно поймало бы приставку в «**От**правлен перевод».
- **`RUR` — тоже рубль.** Устаревший код, но Альфа шлёт им переводы по СБП. `detect` при
  ненайденной сумме возвращает null, поэтому перевод пропадал целиком.

### Пополнение брокерского счёта — пока обычный расход
`Получатель платежа BKS Mir Investitsiy` разбирается корректно, но попадает в расходы: перекладывание
денег на брокерский счёт показывается как трата и занижает финансовое здоровье. Это **известное
ограничение до экрана инвестиций**, а не дефект разбора — пример сохранён здесь как ориентир.

## Planned — Account Types & Card UI (NOT implemented)
Full spec: `docs/CONTEXT.md` → "Roadmap — Planned Features".
1. **Bank registry refactor** — bank name/colour/letter/keywords are duplicated across
   `BankColors.bankBrand()`, `DashboardScreen.BankSymbolBadge()`, `AddAccountSheet.BANKS`,
   `AccountLinker.BANK_KEYWORDS`. Collapse into one `BankRegistry`.
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
| Тесты | `app/src/test/kotlin/com/financeos/hub/` (32 файла, 410 случаев) |

# Design Reference
- Technical spec, schema, formulas, screen contracts: `docs/CONTEXT.md`
- User-facing overview: `README.md`
- Goal art generation prompts: `docs/GOAL_ART_PROMPTS.md`
- `docs/DESIGN_HANDOFF.md` — **архив** (бриф на дизайн-ревью, август 2025). Концепция «Мерцание»
  по нему реализована; как источник о текущем состоянии не годится и помечен об этом сверху.
- Colour tokens: `FosColors.kt` · Typography: `FosType.kt`
- Огранка карточек: `ui/theme/FosSurface.kt` · Заголовки и «?»-пояснения: `ui/components/FosSection.kt`
