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

## Supported Banks (P1)
Сбербанк, Т-Банк, ВТБ, Альфа-Банк, Газпромбанк

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

## Next Steps
- Polish: localization review, dark-mode visual QA
- Train and bundle .tflite model files (model stubs in `assets/models/` — app runs without them via fallback)
- Add Sberbank/Tbank push-notification deep-link intent on budget alert tap

## Completed Post-Phase-3 Polish
- [x] BudgetViewModel wired to NotificationHelper (fires alert at configurable threshold, once per session per budget)
- [x] BehavioralAnalyzerTest — 28 unit tests covering all 7 public methods + edge cases

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
