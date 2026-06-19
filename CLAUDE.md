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

## Next Steps
- Polish: localization review, dark-mode visual QA

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
