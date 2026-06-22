# FinanceOS-Hub — Session Context

> Read this file at the start of every session to resume without re-reading the full roadmap.

## Project
Android offline-first personal finance app. Reads bank SMS → auto-categorizes transactions → shows analytics.
- **Platform:** Android (Kotlin + Jetpack Compose)
- **Package:** `com.financeos.hub`
- **Min SDK:** 26, **Target:** 34

## Branch Strategy
```
main  ← stable releases only (PR from dev)
dev   ← integration (PR from feature branches)
  claude/project-setup-design-sndr3y  ← current session
```
**Never commit directly to main or dev.**

## Architecture
Clean Architecture + MVVM + Hilt + Room + Compose + Coroutines/Flow

```
app/
├── core/
│   ├── database/   (entities, daos, converters, FosDatabase)
│   ├── parser/     (BankParser interface, ParserEngine, banks/)
│   ├── classifier/ (DictionaryClassifier)
│   ├── sms/        (SmsReader, SmsReceiver)
│   └── analytics/  (AnalyticsEngine, ScoreCalculator, InsightGenerator, AnalyticsWorker)
├── data/
│   ├── repositories/
│   └── preferences/ (UserPreferences via DataStore)
├── di/             (DatabaseModule, ParserModule, PreferencesModule, AnalyticsModule)
├── features/       (dashboard, transactions, analytics, budget, goals, onboarding)
├── navigation/     (FosNavHost, FosRoutes)
└── ui/
    ├── theme/      (FosColors, FosType, FosDimens, FosTheme, FosFormatter)
    └── components/ (TransactionRow, LineChart, ScoreRing, GoalRing)
```

## Critical Design Rules (NEVER violate)
1. `FosColors.Positive` (#4DFFA0) = income, success, savings ONLY
2. `FosColors.Negative` (#FF6B6B) = expenses, errors, overrun ONLY — **expense amounts in TransactionRow MUST use Negative**
3. All monetary/numeric `Text` → `fontFeatureSettings = "tnum"` (tabular-nums)
4. `InsightCard` — colored left border ONLY, no icon inside. Border color = severity (CRITICAL→Negative, WARNING→Warning, INFO→Info)
5. Net Worth negative → Negative color

## Amounts Storage
- Store as `Long` kopecks (×100), convert to Double only in `FosFormatter`
- Negative kopecks = expense, positive = income

## SMS Deduplication
`smsId = "${sender}_${timestamp}_${body.hashCode()}"` — checked before insert

## Supported Banks
- **P1:** Сбербанк, Т-Банк, ВТБ, Альфа-Банк, Газпромбанк
- **P2:** Райффайзен, Росбанк, Открытие
- **P3:** МТС Банк, Почта Банк, Россельхозбанк

## Current Phase Status
- [x] Gradle skeleton + AndroidManifest
- [x] Design system (FosColors, FosType, FosDimens, FosTheme, FosFormatter)
- [x] Database layer (entities, DAOs, FosDatabase, 13 categories + 60 merchant rules)
- [x] Parser layer (5 P1 banks, ParserEngine, @IntoSet DI)
- [x] SmsReceiver (real-time) + SmsReader (90-day import)
- [x] DictionaryClassifier
- [x] All 5 repositories
- [x] Navigation + bottom bar
- [x] UserPreferences (DataStore)
- [x] Onboarding (permission request + import progress)
- [x] Dashboard screen
- [x] Transactions screen (red expenses ✓, grouped list, filter chips)
- [x] Analytics screen (4 tabs: Overview, Categories, Trends, Insights)
- [x] Budget screen (envelopes, dynamic bar color)
- [x] Goals screen (SVG GoalRing)
- [x] ScoreCalculator (savings/stability/mandatory/cushion, 0–100)
- [x] InsightGenerator (6 rules, CRITICAL/WARNING/INFO severity)
- [x] AnalyticsEngine (score + insights + sparkline30Days + forecastMonthEnd)
- [x] AnalyticsWorker (WorkManager daily, HiltWorkerFactory)
- [x] ScoreRing component (Canvas DrawScope)
- [x] LineChart component (SVG Canvas, bezier curve ✓)
- [x] Parser unit tests (5 banks × 6 tests)
- [x] **Phase 2A behavioral analytics** (all 13 items complete)
- [x] **Phase 3 TFLite ML layer + Settings + Notifications** (committed `d6d2111`)
- [x] **Subscriptions screen** — recurring expense detection via BehavioralAnalyzer, missed-payment alerts, monthly total; accessible from Budget screen "↻ Подписки" button

## Phase 2A — Behavioral Analytics ✓ COMPLETE
All pure Kotlin, no TFLite. Committed in 3 batches.

Implemented:
1. `HeatmapGrid.kt` — 7×24 Canvas grid ✓
2. `BehavioralAnalyzer.kt` — payday effect, fatigue curve, impulse classification ✓
3. Category anomaly detection (rolling avg + stdDev) ✓
4. Subscription gap detection ✓
5. Fixed vs variable expense classification (CV ≤ 15%) ✓
6. `WaterfallChart.kt` — running-baseline MoM waterfall ✓
7. `NarrativeEngine.kt` — 8 Russian narrative templates ✓
8. `WhatIfSimulator.kt` — interactive sliders, 6/12/24-month projection ✓
9. `ExpensePyramid.kt` — 3-tier mandatory/regular/discretionary ✓
10. InsightsTab — ОПОВЕЩЕНИЯ + АНОМАЛИИ + НАБЛЮДЕНИЯ sections ✓
11. OverviewTab — pyramid + WhatIfSimulator + archetype card ✓
12. CategoriesTab — fixed/variable badge per category ✓
13. TrendsTab — 5 sections: daily chart, heatmap, fatigue, waterfall, impulse stats ✓

## Phase 3 — TFLite ML + Settings + Notifications ✓ COMPLETE

### ML Layer (`core/ml/`)
- `ModelLoader.kt` — loads .tflite from `assets/models/`, returns null if absent (graceful fallback)
- `TextFeatureExtractor.kt` — 256-dim char n-gram embedding, Fibonacci hash, L2 norm
- `MLCategoryClassifier.kt` — 256→13 softmax; falls back to DictionaryClassifier below 40% confidence
- `SpendingPredictor.kt` — LSTM end-of-month forecast; falls back to linear extrapolation
- `BehavioralCluster.kt` — 5-archetype K-means (Плановик/Импульсивный/Гурман/Экономный/Путешественник); rule-based fallback

### Settings (`features/settings/`)
- `SettingsViewModel.kt` — SettingsState: heroVariant, biometric, notifications, ML toggle, budget threshold
- `SettingsScreen.kt` — hero variant chips, notification toggle + budget threshold slider (50-95%), biometric toggle, ML toggle, info section

### Notifications (`core/notifications/NotificationHelper.kt`)
- 3 channels: fos_budget (HIGH), fos_weekly (DEFAULT), fos_insight (LOW)
- CRITICAL insights sent as push notifications from AnalyticsWorker

### Infrastructure
- `di/MLModule.kt` — injects MLCategoryClassifier or DictionaryClassifier based on preference
- TFLite deps: `org.tensorflow:tensorflow-lite:2.14.0` in libs.versions.toml + build.gradle.kts
- `noCompress += "tflite"` in androidResources
- Settings route wired in FosNavHost; gear icon in DashboardScreen

### Requires (to activate ML)
- Place trained model files in `app/src/main/assets/models/`:
  - `category_classifier.tflite` (256 input → 13 output)
  - `spending_predictor.tflite` (float[1][30][1] → float[1][1])
  - `behavioral_cluster.tflite` (float[1][7] → float[1][5])

## Completed Post-Phase-3 Polish
- [x] BudgetViewModel wired to NotificationHelper (fires alert at configurable threshold, once per session per budget)
- [x] BehavioralAnalyzerTest — 28 unit tests covering all 7 public methods + edge cases (fixed entity field names)
- [x] Manual transaction entry — AddTransactionSheet (FAB in TransactionsScreen)
- [x] Transaction search — search bar filters by merchant, category, description
- [x] Transaction detail/edit sheet — tap row → edit merchant, category, note; soft-delete with confirmation
- [x] Notification deep-links — budget → budget route, weekly/insight → analytics route
- [x] Goals CRUD — AddGoalSheet (emoji picker, name, target), contribute dialog, delete
- [x] Budget CRUD — AddBudgetSheet (period toggle, category picker, limit), delete envelopes

## Security & Bug Audit ✓ COMPLETE

Full audit performed; 9 issues found and fixed:

| Severity | File | Fix |
|----------|------|-----|
| CRITICAL | `AnalyticsEngine.getTxSync` | Replaced blocking `.collect { return@collect }` pattern with `.first()` — was silently reading only first emission but could hang |
| CRITICAL | `di/MLModule.kt` | Added `Dispatchers.IO` to `runBlocking { }` — prevents main thread ANR at DI graph construction |
| CRITICAL | `core/sms/SmsReceiver.kt` | Added `CoroutineExceptionHandler` to scope — unhandled exceptions in SMS processing no longer crash silently |
| HIGH | `core/ml/SpendingPredictor.kt` | Float×Long×Int multiplication now uses Double and `coerceAtMost(Long.MAX_VALUE)` — prevents overflow for extreme values |
| HIGH | `features/dashboard/DashboardViewModel.kt` | Expenses stored as negative kopecks; `sumOf { it.amountKopecks }` returned negative. Fixed to `sumOf { abs(it.amountKopecks) }` |
| HIGH | `core/notifications/NotificationHelper.kt` | Added `hasNotificationPermission()` guard (API 33+ check) before all three `notify()` calls — avoids `SecurityException` |
| MEDIUM | `core/database/FosDatabase.kt` | Replaced string-interpolation SQL in `PREPOPULATE_CALLBACK` with parameterized `execSQL(sql, arrayOf(...))` — eliminates SQL injection surface |
| LOW | Multiple screens | Added `key = { it.id }` to all `LazyColumn items()` calls — prevents item reuse bugs during list updates |
| LOW | `AnalyticsEngine.kt` | Added missing `import kotlinx.coroutines.flow.first` |

## Post-Audit Features
- [x] **Account management UI** — AddAccountSheet (bank picker, name, card mask, initial balance), tap-to-edit account balance, delete via AlertDialog in DashboardScreen
- [x] **InsightGeneratorTest** — 28 unit tests covering all 6 rules + edge cases + sort order
- [x] **Category management** — CategoriesScreen (system=read-only, custom=deletable), AddCategorySheet (emoji picker, 12-color swatch picker, name), wired from Settings → "Категории" row; FosRoute.Categories added
- [x] **CSV export** — "↑ CSV" button in TransactionsScreen header; shares current view as `.csv` via FileProvider (provider_paths.xml + manifest provider entry)
- [x] **Push notification listener** — `PushNotificationListener` (NotificationListenerService + @AndroidEntryPoint); maps 9 banking app package names → parser senders; `TransactionSource.PUSH` added; toggle + permission status in Settings "УВЕДОМЛЕНИЯ ОТ БАНКОВ" section
- [x] **Deep-link: Subscriptions → Transactions** — optional `categoryId` nav arg on Transactions route; tapping a subscription card pre-filters the list; dismissible banner with "× Сбросить"
- [x] **P2 bank parsers** — RaiffeisenParser, RosbankParser, OtkritieParser; registered in ParserModule; push packages added (14 total package mappings)
- [x] **P3 bank parsers** — MtsBankParser, PostaBankParser, RosselkhozParser; registered in ParserModule; push packages added (17 total package mappings)
- [x] **Biometric auth** — BiometricHelper (BIOMETRIC_WEAK, API 26+), LockScreen composable, MainActivity wired (isLocked state, onResume prompt, onStop re-lock, AppCompatActivity base)
- [x] **Home-screen widget** — BalanceWidget (AppWidgetProvider + EntryPointAccessors), widget_info.xml (2×2 cells, 30min update), widget_balance.xml layout, widget_bg.xml drawable; AndroidManifest receiver registered; `AccountDao.sumAllBalances()` + `TransactionDao.getTodayExpenses()` added
- [x] **strings.xml** — 60+ strings covering all screens; appcompat dependency added

## Full Audit #2 ✓ COMPLETE (codebase-wide)

Parallel deep audit of all 105 source files. 20 genuine issues fixed:

| Severity | Area | Fix |
|----------|------|-----|
| CRITICAL | `di/MLModule` + `di/PreferencesModule` | **Build failure**: two `@Singleton` bindings for `CategoryClassifier` in the same component (Dagger duplicate binding). Removed `PreferencesModule`; `MLModule` now `@Binds` a new `DelegatingCategoryClassifier` |
| CRITICAL | `di/MLModule` | `runBlocking` DataStore read during graph construction (ANR). Replaced with `DelegatingCategoryClassifier` that reads the pref lazily inside the suspend `classify()` |
| CRITICAL | `MainActivity.onNewIntent` | Called `setContent` a second time → leaked/recreated the whole NavHost on every notification deep-link. Now state-driven via `mutableStateOf` |
| CRITICAL | `MainActivity` biometric | Silent-unlock bypass: when biometric enabled but none enrolled, app unlocked with no auth. Now falls back to device-credential (PIN/pattern) via `KeyguardManager`; only releases when device has no secure lock |
| HIGH | `core/parser/ParserEngine` | `firstOrNull { canHandle }` dropped valid SMS when the first sender-matching parser failed the body. Now tries every matching parser (`firstNotNullOfOrNull`) |
| HIGH | Parsers (Sberbank/Tbank/Mts) | Shared `900` short code claimed by 3 banks → non-deterministic routing. `900` kept only on Sberbank |
| HIGH | All parsers | `parseAmount` threw `NumberFormatException` on bad input → aborted the whole 90-day import. New null-safe `AmountParser` (also handles NBSP/narrow-NBSP separators, clamps overflow). `SmsReader` rows now individually guarded |
| HIGH | `AlfabankParser` | Amount regex lacked `\s` → could not parse any amount ≥ 1000 with a thousands separator. Fixed |
| HIGH | Sberbank/Tbank income | `Перевод` (transfer) matched as INCOME → sign inversion (outgoing stored as positive). Removed from income patterns |
| HIGH | `MainActivity` deep link | Exported activity navigated to attacker-controllable route string → crash. Added `FosRoute.sanitizeDeepLink` whitelist (validated in MainActivity + NavHost) |
| HIGH | `AnalyticsViewModel` | One-shot `init` computation never refreshed; fragile `listOf<Any?>` positional casts (ClassCastException risk); unstructured `async` (one failure blanked all). Rewritten with `mapLatest` off the tx flow + per-call `runCatching` |
| MEDIUM | `core/parser/ParserEngine` | NBSP/narrow-NBSP thousands separators not matched by `\s`. Normalised to regular space once, centrally |
| MEDIUM | `BehavioralAnalyzer` | stdDev squared `(it-avg)²` in `Long` → overflow for large kopeck sums. Now squared in `Double` |
| MEDIUM | `AnalyticsEngine.forecastMonthEnd` | `daysPassed` from elapsed millis mis-scaled the forecast (DST/long months). Uses `LocalDate.dayOfMonth` |
| MEDIUM | `AnalyticsEngine.generateNarratives` | Per-day category average hard-divided by `90` → understated spend for users with < 90 days. Divides by actual data span |
| MEDIUM | `BalanceWidget` | Unstructured `CoroutineScope` with no error handling (process crash); no `goAsync` (work killed mid-update); no click intent. Added all three |
| MEDIUM | `TransactionsViewModel.buildCsvString` | CSV injection: only commas escaped. Now RFC-4180 quoting + formula-injection (`=+-@`) neutralisation |
| MEDIUM | `DictionaryClassifier` | Re-queried DB and recompiled 60+ regexes per transaction. Now caches compiled rules once (Mutex-guarded) |
| LOW | `AnalyticsWorker` | Matched severity by `.name == "CRITICAL"` string. Now `== InsightSeverity.CRITICAL` |
| LOW | Parsers (Raiffeisen/Otkritie) + `AnalyticsEngine` | Over-broad sender aliases (`RSB`, `DISCOVERY`) and a dead `catNames` query removed |

## ML Model Files
- [x] `merchant_classifier.tflite` — bundled in `app/src/main/assets/models/` (256→128→64→13 softmax, 49KB)
- [x] `spending_predictor.tflite` — trained on 20K synthetic sequences; Dense(30→16→8→1), 4.6KB
- [x] `behavioral_cluster.tflite` — trained on 10K synthetic samples (2K/archetype); Dense(7→32→16→5 softmax), 5.7KB

## ML Audit Fixes (this session)
- `BehavioralCluster.classify` / `SpendingPredictor.predict` — made `suspend`; added `Mutex` guard around `interp.run()` (Interpreter is not thread-safe)
- `MLCategoryClassifier.classify` — added `Mutex` guard around `interp.run()`
- `BehavioralCluster.extractFeatures` — guard against empty expenses list to prevent `NaN` feature vector
- `TextFeatureExtractor.extract` — removed unnecessary `Double→Float→Double` roundtrip in norm computation

## PDF Import (`core/pdf/`)
- `PdfImporter.kt` — extracts text from any PDF via SAF URI (no permissions); uses PdfBox-Android 2.0.27.0; `sortByPosition = true` keeps tabular statements in visual reading order
- `PdfTransactionParser.kt` — **logical-row** parser (rewritten): reconstructs each statement row from wrapped physical lines (row starts at `DD.MM.YYYY`, absorbs continuation lines), then extracts posting date (4-digit year), posting amount (comma-decimal + currency suffix → distinguishes from inner dot-decimal `на сумму:`), sign, op code, and a cleaned merchant; dedup key = `pdf_{opCode}` (unique) with `pdf_{ts}_{kopecks}_{merchant.hashCode()}` fallback
- `PdfTransactionParserTest.kt` — 7 tests against real Alfa-Bank "Операции по счету" layout
- `TransactionSource.PDF` added to enum (stored as string → no Room migration)
- `TransactionsViewModel.importPdf(uri)` — IO-dispatched; auto-classifies via CategoryClassifier; returns found/inserted counts
- `ImportPdfSheet.kt` — bottom sheet with idle/loading/success/error states; "↓ PDF" button in TransactionsScreen header

### PDF parser bug fixes (this session)
- **Sign bug (critical)**: old parser passed the signed token to `AmountParser.toKopecks`, which returns `0` for negatives (`value <= 0`) → **every expense was silently dropped**, only income survived (all imports looked green/`+`). Now the sign is detected separately and only the unsigned magnitude is parsed.
- **Multi-line layout**: old parser required date + amount on the *same physical line*; Alfa descriptions wrap across lines, so it latched onto stray fragments. Now rows are reconstructed before extraction.
- **Inner-value confusion**: card rows contain `на сумму: 40.00 RUR` (dot) and `дата совершения операции: 19.12.25` (2-digit year). These are now excluded by construction (posting amount = comma-decimal, posting date = 4-digit year).
- **Date display**: `FosFormatter.dayLabelYear()` added — transaction history (row meta, day header, detail sheet) now shows the year ("19 декабря 2025") instead of "19 декабря".

## Transfer → Savings-Goal Auto-Routing (`core/transfer/`) ✓ COMPLETE
Bank transfers (перевод / СБП / перечисление) are now first-class `TransactionType.TRANSFER` rows
instead of being mis-booked as expenses. Because every analytics/aggregation query filters on
`type='EXPENSE'`/`'INCOME'`, emitting TRANSFER makes them vanish from spend/income automatically.

- **Detection** — `core/parser/TransferPatterns.kt`: conservative Russian keyword recognition (OUTGOING:
  Перевод/Перечисление/Отправлен перевод/СБП/Списание…перевод; INCOMING: Зачисление перевода/Перевод от/
  Входящий перевод). Captures destination card last-4 from "на карту/счёт *1234" when present. Wired into
  **all 11 bank parsers** as the FIRST matcher in `parse()` so a transfer is never read as a purchase or
  as inverted-sign income (the old "Перевод excluded from income" hack is now handled explicitly).
- **Signed storage** — `ParsedTransaction` gained `counterpartyMask`, `outgoing`, and `signedKopecks()`
  (EXPENSE −, INCOME +, TRANSFER ∓ by `outgoing`). All 3 insert sites (SmsReceiver, PushNotificationListener,
  SmsReader) now use `parsed.signedKopecks()` and call `TransferRouter.onTransactionInserted(...)` only when
  the row was actually inserted (`insertAll` rowId != -1).
- **`TransferRouter`** — (@Singleton) for an inserted TRANSFER: (A) outgoing + matching a linked card/keyword
  → `GoalRepository.contribute(goalId, magnitude)` + `TransactionDao.setGoal`; (B) otherwise pairs an
  opposite-sign equal-magnitude counterpart within ±10 min via `findTransferCounterpart` +
  `markAsPairedTransfer` (net worth unchanged); (C) unrouted outgoing ≥ 1000 ₽ → push. Whole body in
  `runCatching` so routing never breaks ingestion.
- **DB** — `TransferRouteEntity`/`TransferRouteDao`/`TransferRouteRepository` (`transfer_routes` table,
  CARD|KEYWORD match, lowercased value). `TransactionEntity` gained `goal_id` + `transfer_pair_id` columns.
  `FosTypeConverters` handles `TransferMatchType`. **DB bumped v2→v3** with `MIGRATION_2_3` (adds 2 columns +
  `transfer_routes` table + index); registered in `DatabaseModule.addMigrations(...)` + DAO provider.
- **Goal linking UI** — `GoalsViewModel` now combines goals + routes + accounts + cards (array-form combine),
  exposing `cardMasks`, `routes`, `accounts`, and `linkCard/linkKeyword/linkAccount/unlink`. `LinkTransferRouteSheet.kt`
  (🔗 button per goal card) lets the user attach a bank account, destination card chip, or an SMS keyword.
- **`TransactionRow`** — TRANSFER renders NEUTRAL (`TextPrimary`, "↔ amount", never red/green, tabular-nums
  preserved) with a "→ в цель" subtext when `goalId != null`. EXPENSE(red)/INCOME(green) unchanged.
- **`NotificationHelper.notifyUnroutedTransfer(magnitude)`** — fos_insight channel, deep-links to "goals".

## Bidirectional Account-Based Goal Routing ✓ COMPLETE

Link a bank account to a goal (at creation or via 🔗 sheet):
- Transfer INTO that account → `+amount` to goal progress
- Transfer FROM that account → `-amount` from goal progress (clamped at 0)
- Example: "Путешествия" linked to Alfa Travel; перевод 10k → цель +10k; снятие 5k → цель -5k

Changes:
- `TransferMatchType.ACCOUNT` added (stored as string — no DB migration needed)
- `TransferRouter`: ACCOUNT routes checked for both incoming and outgoing before CARD/KEYWORD
- `AddGoalSheet`: "ПРИВЯЗАТЬ СЧЁТ (АВТО)" chip picker — optional at goal creation
- `LinkTransferRouteSheet`: "СЧЁТ В БАНКЕ" section with bidirectional badge
- `GoalsViewModel.createGoal(linkedAccountId)` + `linkAccount(goalId, accountId)`
- `GoalsState.accounts` exposed for pickers in both sheets

### Known limitation
- Counterparty (destination) card mask is only available when the bank SMS spells "на карту/счёт *NNNN".
  Several push/SMS formats omit it (e.g. Alfa push, generic СБП notifications); for those the destination
  mask is null and the user relies on **keyword routing** or **account routing**.

## Post-Transfer Session Features
- [x] **Volumetric bank cards** — `BankCard` with diagonal `Brush.linearGradient` (0%→45%→100%), 1dp gloss overlay, `BankSymbolBadge` (letter abbreviation in frosted corner badge)
- [x] **BankColors.kt** — МБанк brand added; `FosFormatter.currencySymbol()` maps RUB/USD/EUR/KGS
- [x] **HeroAmountMulti** (26sp) — used when account list has 3+ currencies to prevent text overflow
- [x] **AccountLinker** (`core/account/AccountLinker.kt`) — resolves SMS/push card masks to accounts (account.card_mask → CardEntity); `syncBalance()` prefers bank-reported "Остаток" (authoritative) over delta
- [x] **Card chip scroll fix** — card chips inside bank cards replaced with static `Row` (max 4 + "+N"), eliminates horizontal drag conflict with parent scroll
- [x] **Swipe-to-delete** — `SwipeToDismissBox` on TransactionRow (→ softDelete) and AccountRow (→ deactivate)
- [x] **AddTransactionSheet enhancements** — account picker chips (name + ••mask), income-source presets (Зарплата/Перевод/Букмекер/Подарок/Кэшбэк/Инвестиции/Другое), currency symbol follows account
- [x] **Multi-currency hero** — `netWorthByCurrency` map per account group, `CalmHero` shows each currency on its own line

## Audit #3 ✓ COMPLETE (this session)

Deep audit of 4 critical paths. 3 genuine bugs fixed:

| Severity | File | Fix |
|----------|------|-----|
| CRITICAL | `OnboardingScreen.kt` + `OnboardingViewModel.kt` | **SMS permission freeze bug**: `onRequestSmsPermission()` jumped to IMPORT step without showing the Android permission dialog → 0% forever. Replaced with `rememberLauncherForActivityResult(RequestMultiplePermissions)` for READ_SMS + RECEIVE_SMS. Added `permissionDenied` state, "Открыть настройки" deep-link, and "Пропустить" skip option. |
| HIGH | `SmsReceiver.kt` | **Process kill race**: `onReceive()` launched coroutines without `goAsync()` — Android could kill the process before the DB write completed when app is backgrounded. Wrapped in `goAsync()` / `pendingResult.finish()` in finally block. |
| HIGH | `AnalyticsEngine.kt` | **Cushion score always 0**: `buildScoreInput()` and `buildInsightData()` both hardcoded `totalBalance = 0L`. The cushion pillar (25 pts) of the 0–100 financial health score was permanently 0. Fixed to `accountDao.sumAllBalances()`. |

## Post-Audit-3 Fixes (this session)
- [x] **МБанк parser** (`core/parser/banks/MBankParser.kt`) — multi-currency KG bank (USD/KGS/EUR/RUB). Line-oriented push: `Покупка: 22 USD` / merchant line / `Карта: *6461` / `Доступно: 11.96 USD`. Extracts each field independently (amount taken AFTER the type keyword so `Доступно:` is never mis-read as the amount). Registered in `ParserModule` (`bindMBank`); push package `com.maanavan.mb_kyrgyzstan` → `MBANK` added to `PushNotificationListener`. `MBankParserTest` (7 cases). Amounts stored as minor units (×100) in the card's currency.
- [x] **Manual-delete balance bug** — deleting a manually-added operation did not restore the account balance (insert applied a delta, delete did not undo it). `TransactionsViewModel.deleteTransaction` now loads the row first and, for `source == MANUAL` with an `accountId`, reverses `amountKopecks` from the account before soft-deleting. SMS/PUSH balances are bank-authoritative snapshots (not reversed); PDF rows have no account. Added `TransactionRepository.getById`.

## Audit #4 ✓ COMPLETE (this session)

Three parallel deep audits (transfer/goal routing · parsers/ingestion · DB/DI/analytics). 7 genuine bugs fixed:

| Severity | File | Fix |
|----------|------|-----|
| CRITICAL | `TransactionsViewModel.deleteTransaction` + `TransferRouter.onTransactionReversed` | **Goal stayed inflated on delete**: deleting a transfer that funded a goal never un-funded it (`savedKopecks` permanently inflated; repeat add/delete could force completion). New `TransferRouter.onTransactionReversed(tx)` mirrors the original contribution sign (ACCOUNT = signed amount, CARD/KEYWORD = +magnitude) and is called for any `tx.goalId != null` before soft-delete. |
| HIGH | `GoalsViewModel.addContribution` | **Divergent contribution paths**: manual contribute didn't floor-clamp or refresh `updatedAt`, unlike routed `GoalRepository.contribute`. Now delegates to `goalRepo.contribute` — single clamping/completion code path. |
| HIGH | `GoalsViewModel` link methods | **Duplicate routes**: free-text card/keyword entry created duplicate `transfer_routes` rows (and the same account could link to two goals, non-deterministic). New `addRouteIfAbsent()` skips an identical (goal+type+value) route. |
| HIGH | `AlfabankParser.pushMask` / `maskTail` | **Merchant digits read as card mask**: a bare `\d{4}` end-anchor captured a merchant ending in 4 digits (e.g. "АЗС 2024") as the card → wrong account link. Now requires the masking glyph `[*•·]{1,2}` before the digits (both for extraction and merchant-tail stripping). |
| HIGH | `AccountLinker.syncBalance` | **Zero balance ignored**: `ostatokKopecks > 0L` treated an authoritative "Доступно: 0" as "no balance" and fell through to the delta path. Changed to `>= 0L` (only `null` falls through). |
| MEDIUM | `MBankParser` amount/balance regex | **Multi-separator poisoning**: `[\d\s.,]*?` could capture two separators → `toDoubleOrNull` null → amount 0 → transaction dropped. Tightened to digit-grouping + at most one decimal group. |
| MEDIUM | `TransactionDao.findTransferCounterpart` | **Double-count**: a goal-routed transfer leg could still be paired as net-worth-neutral. Query now excludes `goal_id IS NOT NULL` rows from counterpart matching. |

Added `AlfabankParserTest` regression: merchant ending in 4 digits is not mistaken for a card mask.

## Audit #5 ✓ COMPLETE (this session)

### Balance sync fix (balance not updating on Alfa push transfer)
Root cause: `TransferPatterns.detect()` lacked SOURCE_CARD and BALANCE regex extraction; `AccountLinker.resolveAccountId` had no bank-name fallback when card mask is absent; `toParsed()` hardcoded `balanceKopecks = null`.

Fixed across 3 commits:
- `TransferPatterns.kt` — added `SOURCE_CARD` + `BALANCE` regex, fixed AMOUNT regex (`\d{2}` → `\d{1,2}`), `Result.balanceKopecks`, auto-extract `resolvedMask` in `detect()`
- `AccountLinker.kt` — complete rewrite: card mask lookup → CardEntity lookup → bank-name fallback (BANK_KEYWORDS map, only when single match); `syncBalance` `>= 0L` fix already applied (Audit #4)
- `SmsReceiver`, `PushNotificationListener`, `SmsReader` — pass `parsed.bankId` to `resolveAccountId`

### Comprehensive audit fixes (18 bugs, 14 files)

| Severity | File | Fix |
|----------|------|-----|
| HIGH | `DashboardViewModel.kt` | `collect` → `collectLatest` + `.debounce(500)` — prevents 1500+ redundant analytics DB queries during batch SMS import |
| HIGH | `TransactionDao.kt` `findTransferCounterpart` | Added `AND type = 'TRANSFER'` filter — prevented INCOME rows from being mis-classified as transfer legs |
| HIGH | `TransactionDao.kt` `sumExpenses` | `SUM(amount_kopecks)` → `SUM(ABS(amount_kopecks))` — expenses stored as negative kopecks; query returned negative totals |
| HIGH | `GoalRepository.kt` | Added `Mutex` + `withLock` around `contribute()` — eliminated TOCTOU race under concurrent coroutines; fixed `completedAt` reset bug |
| HIGH | `SmsReader.kt` | `imported++` moved inside `rowIds != -1L` check — was over-counting skipped duplicates |
| HIGH | `ScoreCalculator.kt` `calcStability` | `/3` → `/input.last3MonthsIncome.size` — hardcoded divisor broke score when fewer than 3 months of data |
| HIGH | `AnalyticsEngine.kt` | 3 fixes: spanDays computed from EXPENSE rows only; `savingsHistory` uses offsets 1..3 (completed months); weekday/weekend avg per-transaction not per-bucket |
| MEDIUM | `BehavioralAnalyzer.kt` line 77 | `(base1 + base2) / 2` → `/ 2.0` — integer truncation silently dropped payday events with tiny baselines |
| MEDIUM | `BehavioralAnalyzer.kt` line 158 | `expenses.size.coerceAtLeast(1)` → `expenses.size`; `neutralCount = (...).coerceAtLeast(0)` — phantom neutral count of 1 when expense list is empty |
| MEDIUM | `AnalyticsViewModel.kt` | `runCatching` swallowed `CancellationException`, breaking `mapLatest` cancellation. Replaced with `safeAsync` helper that re-throws `CancellationException` |
| MEDIUM | `MainActivity.kt` | Cached `biometricEnabledCache` field; `onStop` now locks synchronously — eliminated coroutine race where `isLocked = true` was never written if lifecycle scope was cancelled first |
| MEDIUM | `BalanceWidget.kt` | Removed `pending.finish()` from `CoroutineExceptionHandler` — `finally` block always calls it; double-finish caused a crash |
| LOW | `FosDatabase.kt` | Added `MIGRATION_3_4` with indexes on `transactions.goal_id` and `transactions.transfer_pair_id`; DB version bumped 3→4 |

## Backup / Restore + Opt-in SMS (this session)

### Full backup to file (`core/backup/BackupManager.kt`)
- Exports **all 8 tables** (accounts, cards, categories, goals, budgets, transfer_routes, transactions) to a single self-describing JSON file via `org.json` (no new dependency). `SCHEMA_VERSION = 1` header for forward-compat.
- **Save:** SAF `CreateDocument("application/json")`, suggested name `financeos-backup-YYYY-MM-DD.json`. **Restore:** SAF `OpenDocument`.
- Restore is additive + idempotent inside one `db.withTransaction { }`; FK-safe order (categories → accounts → goals → cards → budgets → routes → transactions). **Dangling-reference guard:** cards/budgets whose parent isn't in the backup are dropped; transaction `accountId`/`categoryId` not present are nulled — prevents Room FK abort.
- New backup-read DAO methods: `CardDao.getAllActive`, `BudgetDao.getAllActive`, `GoalDao.getAllForBackup`, `TransactionDao.getAllForBackup` (non-deleted).
- UI: Settings → "РЕЗЕРВНАЯ КОПИЯ" (создать / восстановить) with status line. `SettingsViewModel`: `exportBackup(uri)`, `restoreBackup(uri)`, `BackupUi` state.

### SMS now opt-in (no auto-parse on fresh install)
- New pref `SMS_REALTIME_ENABLED` (**default false**). `SmsReceiver` checks `prefs.smsRealtimeEnabled.first()` before processing — a fresh install never ingests SMS until the user opts in.
- Onboarding reframed to an explicit choice: **"Импортировать из SMS за 90 дней"** vs **"Пропустить — добавлю вручную"**. Choosing import sets `SmsRealtimeEnabled = true`; skipping leaves it off.
- Settings → "ОПЕРАЦИИ ИЗ SMS": toggle "Читать входящие SMS" + "Импортировать за 90 дней" (requests READ_SMS/RECEIVE_SMS, shows progress, then count). `SettingsViewModel.importSmsHistory()`, `SmsImportUi` state.
- **Migration note:** existing installs relying on real-time capture must re-enable the toggle (or re-import); intentional per the opt-in design.

## «Мерцание» Shimmer Layer ✓ COMPLETE (all 3 phases)

Two toggles in Settings → «КАСТОМИЗАЦИЯ»:
- **«Анимации»**: countUp, screenTransitions, touchRipple, holographicCards (variant A) / glassCards (variant B), ScoreRing count-up animation
- **«Атмосфера Мерцание»**: particles, particlePulse, surfaceGlow, heroBreathing, depthTimeline, insightBorderGlow, semanticGlow, currencyReef

System flags auto-override: `reduceMotion` (ANIMATOR_DURATION_SCALE==0) disables pulse/breathing; `powerSave` disables particles, forces glass cards, disables reef.

### Phase 0 — Infrastructure
- `Shimmer.kt` — `ShimmerConfig` data class + `LocalShimmer` + `ProvideShimmer` + `rememberSystemReduceMotion` + `rememberPowerSave`
- `UserPreferences` — `ANIMATIONS_ENABLED`, `ATMOSPHERE_ENABLED`, `CARDS_VARIANT_B` DataStore keys
- `SettingsScreen/ViewModel` — «КАСТОМИЗАЦИЯ» section, animated variant-B sub-toggle

### Phase 1 — Анимации
- `AnimatedAmount.kt` — count-up interpolation between Long kopeck values via Float progress
- `ShimmerCardFx.kt` — `rememberDeviceTilt` (accelerometer, exponential smoothing) + `Modifier.shimmerTilt` (±8° graphicsLayer) + `ShimmerCardSheen` (holographic sweep gradient / glass frost)
- `ScoreRing.kt` — Animatable arc sweep count-up from 0 on first render
- `FosNavHost.kt` — `fadeIn(220ms) + scaleIn(0.98f)` / `fadeOut(160ms)` screen transitions

### Phase 2 — Атмосфера
- `ParticleLayer.kt` — 16 firefly dots (sin/cos analytic, frame loop via `withInfiniteAnimationFrameMillis`); `rememberBreathingScale` — always calls `rememberInfiniteTransition` (1f..1f when inactive, per Compose Rules of Hooks)
- `InsightsTab.kt` — particles behind LazyColumn; horizontal gradient glow inward from severity border
- `TransactionsScreen.kt` — depth-of-field alpha on day groups (`1f - groupIndex*0.07f`, min 0.45f), static (safe under reduce-motion)
- `DashboardScreen.kt` — all 3 hero variants: particles + surface glow shadow + breathing scale; `BankCard` tilt + sheen

### Phase 3 — Semantic + Reef
- `ScoreRing.kt` — ambient radial glow (22%→0%) clipped to CircleShape, colour = score tier (Positive/Warning/Negative); never leaks to net-worth text
- `CurrencyReef.kt` — bioluminescent blob organisms per currency (RUB=GlowIndigo, USD=GlowViolet, EUR=GlowPink); triple-layer circles (5/9/14% alpha × sin-pulse); drawn behind multi-currency amount Column in both CalmHero and ContrastHero

### Phase 3.5 — Bioluminescent touch ripple (commit e9faafa)
- `ShimmerRipple.kt` — `Modifier.shimmerRipple()`. Implemented WITHOUT Material `ripple()` (that needs BOM 2024.09+; we're on 2024.06). Observe-only `awaitEachGesture` reads the press position with `requireUnconsumed = false` and never consumes, so the host `clickable {}` still fires; a translucent radial bloom (GlowViolet, peak α≈0.28) expands via `drawWithContent`. Decorative palette only, low alpha keeps numbers readable. Gated by `LocalShimmer.touchRipple`. Wired into `BankCard`.

### Build fixes (commits 5e1bc1b, dc25502)
- **Real CI blocker** (dc25502): `ParticleLayer.kt` used `var time by remember { mutableStateOf(0f) }` but imported only `getValue`, not `setValue` → `Type 'MutableState<Float>' has no method 'setValue(...)'`. Added `import androidx.compose.runtime.setValue`.
- (5e1bc1b) `rememberBreathingScale` early-return before `rememberInfiniteTransition` violated Compose Rules of Hooks → also fixed to always call the transition with `1f..1f` bounds when inactive (latent, would have failed at runtime).

## Audit #6 Fixes (Shimmer layer, this session)

5 Compose Rules of Hooks violations and performance issues fixed (commit 3c3718d):

| Severity | File | Fix |
|----------|------|-----|
| CRITICAL | `ShimmerCardFx.kt` | `shimmerTilt` called `rememberDeviceTilt`/`animateFloatAsState` after early return → slot-table crash on toggle. All composable calls moved before the guard. |
| CRITICAL | `AnimatedAmount.kt` | Early return before `remember`/`LaunchedEffect` → slot-table mismatch when animation toggled live. Removed early return; added `enabled` to LaunchedEffect keys. |
| HIGH | `Shimmer.kt` | `rememberSystemReduceMotion` read scale once via `remember {}` — stale until recomposition. Replaced with `ContentObserver + DisposableEffect` (live, mirrors `rememberPowerSave`). |
| MEDIUM | `InsightsTab.kt` | `itemsIndexed(narratives)` missing key lambda → index-based reuse, wrong items on list reorder. Fixed: `key = { _, n -> n.id }`. |
| LOW | `ShimmerRipple.kt` | `Brush.radialGradient` allocated per-frame per-ripple (GC pressure @ 60 fps). Replaced with 3 concentric `drawCircle` + `Color.copy(alpha)` (zero allocation). |

## Branch / Merge History
- `claude/project-setup-design-sndr3y` → `dev` (PR #2, merged)
- `dev` → `main` (PR #3, merged)
- All branches in sync as of 2026-06-22

## Post-Audit-6 Features (this session)

### Backup encryption (`core/backup/BackupCrypto.kt`)
- AES-GCM-256 via Android Keystore (hardware-backed TEE/SE when available)
- Key alias `fos_backup_key`; 12-byte GCM IV prepended; 128-bit auth tag
- File format: `FOSENC1:` header (8 bytes) + IV (12 bytes) + ciphertext
- Backward compat: files without the header (old plaintext exports) are accepted on restore as-is
- Suggested filename extension changed from `.json` → `.fose`

### Cross-channel SMS↔push dedup (`TransactionDao`, `SmsReader`, `PushNotificationListener`)
- New `TransactionDao.existsSimilarSmsOrPush(magnitude, fromTs, toTs)` query: checks if any SMS/PUSH transaction with the same absolute amount exists within a ±5-minute window
- `PushNotificationListener.processPush` now calls this guard after the `existsBySmsId` check — rejects push if SMS already imported the same event
- `SmsReader.importLast90Days` applies the same guard — rejects SMS if push was already ingested
- Prevents double-counting on banks that send both SMS and push for every operation (e.g. Sberbank)

## Parser Tests — Full Coverage ✓
All 11 bank parsers now have unit tests (7 cases each = 77 total parser test cases):

| Bank | File | Status |
|------|------|--------|
| Сбербанк | `SberbankParserTest` | ✓ (prior) |
| Т-Банк | `TbankParserTest` | ✓ (prior) |
| ВТБ | `VtbParserTest` | ✓ (prior) |
| Альфа-Банк | `AlfabankParserTest` | ✓ (prior) |
| Газпромбанк | `GazprombankParserTest` | ✓ (prior) |
| МБанк | `MBankParserTest` | ✓ (prior) |
| Райффайзен | `RaiffeisenParserTest` | ✓ (this session) |
| Росбанк | `RosbankParserTest` | ✓ (this session) |
| Открытие | `OtkritieParserTest` | ✓ (this session) |
| МТС Банк | `MtsBankParserTest` | ✓ (this session) |
| Почта Банк | `PostaBankParserTest` | ✓ (this session) |
| Россельхоз | `RosselkhozParserTest` | ✓ (this session) |

## Critical Bug Fixed (this session)
- `ShimmerRipple.kt` — `import androidx.compose.ui.graphics.Color` was dropped by the audit fix that replaced Brush.radialGradient. Color is still used in `RippleSpec.color: Color` and the function signature → compile error. Fixed.

## Next Steps
- Polish: localization review, dark-mode visual QA
- feature/app-icon already in main (no action needed)
- Consider: cross-channel dedup window tuning (currently ±5 min, conservative)
- Consider: encrypt backup with user PIN (currently key is device-scoped, no extra auth)

## Key File Locations
| Layer | Path |
|---|---|
| Theme | `app/src/main/kotlin/com/financeos/hub/ui/theme/` |
| Components | `app/src/main/kotlin/com/financeos/hub/ui/components/` |
| Database | `app/src/main/kotlin/com/financeos/hub/core/database/` |
| Analytics | `app/src/main/kotlin/com/financeos/hub/core/analytics/` |
| Parsers | `app/src/main/kotlin/com/financeos/hub/core/parser/` |
| Features | `app/src/main/kotlin/com/financeos/hub/features/` |
| DI Modules | `app/src/main/kotlin/com/financeos/hub/di/` |

## Design Reference
- Full behavioral analytics spec: `docs/CONTEXT.md` → section "Behavioral Analytics Vision"
- Color tokens: `FosColors.kt`
- Typography: `FosType.kt`
