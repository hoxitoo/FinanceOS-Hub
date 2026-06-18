# FinanceOS Hub

Offline-first Android finance app that reads bank SMS messages and automatically tracks your spending — zero manual input required.

## Features

| Feature | Details |
|---------|---------|
| **Auto-import** | Reads SMS from 5 major Russian banks; imports last 90 days on first launch |
| **Real-time** | New transactions appear instantly via `SmsReceiver` BroadcastReceiver |
| **Smart categorization** | Dictionary classifier with 60+ merchant rules; optional TFLite ML layer |
| **Manual entry** | Add, edit, delete transactions and recategorize them inline |
| **Financial score** | 0–100 score based on savings rate, stability, mandatory/cushion ratios |
| **Behavioral analytics** | Heatmap, fatigue curve, payday effect, anomaly detection, subscription gaps |
| **Budget envelopes** | Monthly/weekly limits per category with dynamic color bar (green→amber→red) |
| **Savings goals** | Goal rings, contribution dialogs, auto-complete when target is reached |
| **Insights & narratives** | 8 Russian narrative templates, CRITICAL/WARNING/INFO severity alerts |
| **What-if simulator** | Interactive sliders for 6/12/24-month savings projections |
| **Notifications** | Budget alerts, weekly summaries, critical insight push (3 channels) |
| **Deep-links** | Notification taps navigate directly to the relevant screen |
| **Settings** | Hero variant, budget alert threshold, biometric lock, ML toggle |
| **100% offline** | No internet permission; all data stays on device |

## Supported Banks

| Bank | Sender patterns |
|------|-----------------|
| Сбербанк | SBERBANK, 900 |
| Т-Банк | TINKOFF, TBANK, 2200 |
| ВТБ | VTB |
| Альфа-Банк | ALFABANK, ALFA |
| Газпромбанк | GAZPROMBANK, GPB |

## Screens

1. **Dashboard** — net worth hero (3 variants), income/expense metrics, accounts scroll, recent transactions, forecast
2. **Transactions** — grouped list, search, filter chips (All/Income/Expense/SMS/Manual), detail/edit sheet
3. **Analytics** — 4 tabs: Overview (score ring + pyramid + what-if), Categories (fixed/variable badge), Trends (daily chart + heatmap + waterfall), Insights (alerts + anomalies + narratives)
4. **Budget** — envelope cards with progress bars, create/delete budgets
5. **Goals** — goal rings, contribution dialog, auto-complete

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3, custom dark theme)
- **Hilt** — dependency injection with `@IntoSet` multibinding for parsers
- **Room** — local SQLite, entities stored as Long kopecks (×100)
- **DataStore** — 8 preference keys (hero variant, notifications, ML toggle, etc.)
- **WorkManager** + **HiltWorkerFactory** — daily analytics background job
- **TFLite 2.14.0** — optional ML layer (graceful fallback when model files absent)
- **Clean Architecture + MVVM**

## Project Structure

```
app/
├── core/
│   ├── database/       # Entities, DAOs, FosDatabase (13 categories, 60 merchant rules)
│   ├── parser/         # BankParser interface, ParserEngine, 5 bank parsers (@IntoSet DI)
│   ├── classifier/     # DictionaryClassifier, CategoryClassifier interface
│   ├── sms/            # SmsReceiver (real-time), SmsReader (90-day import)
│   ├── analytics/      # AnalyticsEngine, ScoreCalculator, InsightGenerator, BehavioralAnalyzer
│   ├── ml/             # ModelLoader, TextFeatureExtractor, MLCategoryClassifier, SpendingPredictor, BehavioralCluster
│   └── notifications/  # NotificationHelper (3 channels + deep-links + permission guard)
├── data/
│   ├── repositories/   # 5 repositories (Tx, Account, Category, Budget, Goal)
│   └── preferences/    # UserPreferences (DataStore)
├── di/                 # DatabaseModule, ParserModule, RepositoryModule, MLModule, PreferencesModule, AnalyticsModule
├── features/           # dashboard, transactions, analytics, budget, goals, onboarding, settings
├── navigation/         # FosNavHost, FosRoutes, bottom nav
└── ui/
    ├── theme/          # FosColors, FosType, FosDimens, FosTheme, FosFormatter
    └── components/     # TransactionRow, LineChart, ScoreRing, GoalRing, HeatmapGrid, WaterfallChart, ExpensePyramid
```

## Build

```bash
./gradlew assembleDebug
./gradlew test
```

Parser unit tests: 5 banks × 6 test cases each (`BankParserTest.kt`)  
Behavioral analytics tests: 28 cases (`BehavioralAnalyzerTest.kt`)

## Optional: Enable ML Classification

Place trained TFLite model files in `app/src/main/assets/models/`:

| File | Shape | Purpose |
|------|-------|---------|
| `category_classifier.tflite` | `float[256]` → `float[13]` | SMS merchant → category (13 classes) |
| `spending_predictor.tflite` | `float[1][30][1]` → `float[1][1]` | End-of-month spend forecast |
| `behavioral_cluster.tflite` | `float[1][7]` → `float[1][5]` | User archetype (5 clusters) |

Without model files the app runs entirely on rule-based classifiers — no runtime errors.

## Design System

Dark theme, custom color tokens:

| Token | Hex | Use |
|-------|-----|-----|
| `Positive` | `#4DFFA0` | Income, success, savings — ONLY positive |
| `Negative` | `#FF6B6B` | Expenses, errors, overrun — ONLY negative |
| `Warning` | `#FFB84D` | 70–90% budget usage |
| `Info` | `#4D9FFF` | Links, selection |
| `Background` | `#0A0D12` | Main background |

All monetary `Text` uses `fontFeatureSettings = "tnum"` (tabular-nums).

## Branch Strategy

```
main  ← stable releases only
dev   ← integration branch
  └── feature/... ← PR to dev → PR to main
```

## Security Notes

- `SmsReceiver` is protected with `android:permission="android.permission.BROADCAST_SMS"` — only the system can broadcast to it
- All `POST_NOTIFICATIONS` calls are guarded by runtime permission check on API 33+
- All Room database inserts in `FosDatabase.PREPOPULATE_CALLBACK` use parameterized `execSQL()` — no SQL injection surface
- Coroutine errors in `SmsReceiver` are caught by `CoroutineExceptionHandler` — no silent crashes
