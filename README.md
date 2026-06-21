# FinanceOS Hub

Offline-first Android finance app that reads bank SMS messages and automatically tracks your spending — zero manual input required.

## Features

| Feature | Details |
|---------|---------|
| **Auto-import** | Reads SMS from 11 Russian banks (P1/P2/P3); imports last 90 days on first launch; native permission dialog in onboarding |
| **Push capture** | `PushNotificationListener` captures bank app notifications in real-time alongside SMS |
| **Real-time** | New transactions appear instantly via `SmsReceiver` BroadcastReceiver (with `goAsync()`) |
| **Smart categorization** | Dictionary classifier with 60+ merchant rules; optional TFLite ML layer |
| **Account linking** | Card mask from SMS/push (e.g. ••2548) auto-links transactions to the correct account; "Остаток" balance synced from bank messages |
| **Multi-currency** | RUB / USD / EUR / KGS (Сом) support per account; hero shows each currency on its own line |
| **Manual entry** | Add (with account picker + income-source presets), edit, delete transactions |
| **Swipe-to-delete** | Swipe left on transactions and accounts to delete |
| **PDF import** | Import bank statements (Alfa-Bank "Операции по счету" layout) via SAF |
| **Financial score** | 0–100 score based on savings rate, stability, mandatory/cushion ratios (cushion uses real account balances) |
| **Behavioral analytics** | Heatmap, fatigue curve, payday effect, anomaly detection, subscription gaps |
| **Transfer routing** | Bank transfers (СБП/перевод) classified as TRANSFER type; auto-routed to savings goals or paired between accounts |
| **Budget envelopes** | Monthly/weekly limits per category with dynamic color bar (green→amber→red) |
| **Savings goals** | Goal rings, contribution dialogs, transfer-to-goal auto-routing, link by card or keyword |
| **Subscriptions** | Auto-detected recurring expenses, missed-payment alerts, monthly total |
| **Insights & narratives** | 8 Russian narrative templates, CRITICAL/WARNING/INFO severity alerts |
| **What-if simulator** | Interactive sliders for 6/12/24-month savings projections |
| **Notifications** | Budget alerts, weekly summaries, critical insight push (3 channels) |
| **Deep-links** | Notification taps navigate directly to the relevant screen |
| **Settings** | Hero variant, budget alert threshold, biometric lock, ML toggle, categories CRUD |
| **Biometric lock** | Locks on background; falls back to device credential (PIN) when no biometric enrolled |
| **Home-screen widget** | 2×2 balance widget via `AppWidgetProvider` |
| **100% offline** | No internet permission; all data stays on device |

## Supported Banks

| Tier | Bank | SMS sender | Push package |
|------|------|-----------|-------------|
| P1 | Сбербанк | SBERBANK, 900 | ru.sberbankmobile, ru.sberbank.sbbol |
| P1 | Т-Банк | TINKOFF, TBANK, 2200 | ru.tinkoff.cardsnew, com.idamob.tinkoff.android |
| P1 | ВТБ | VTB | ru.vtb24.mobilebanking.android |
| P1 | Альфа-Банк | ALFABANK, ALFA | ru.alfabank.mobile.android |
| P1 | Газпромбанк | GAZPROMBANK, GPB | ru.gazprombank.android.mobilebank |
| P2 | Райффайзен | RAIFFEISEN | ru.raiffeisenmobile.android |
| P2 | Росбанк | ROSBANK | ru.rosbank.android |
| P2 | Открытие | OTKRITIE | ru.ftc.otkritie |
| P3 | МТС Банк | MTSB | ru.mtsbank.mobilebank |
| P3 | Почта Банк | POSTABANK | ru.pochtabank.android |
| P3 | Россельхозбанк | RSHB | ru.rshb.mbank |
| KG | МБанк (Кыргызстан) | MBANK | com.maanavan.mb_kyrgyzstan |

## Screens

1. **Dashboard** — net worth hero (3 variants: Calm/Contrast/Minimal), income/expense metrics, accounts scroll with volumetric bank cards, recent transactions, forecast
2. **Transactions** — grouped list, search, filter chips (All/Expense/Income), swipe-to-delete, detail/edit sheet, "↑ CSV" export, "↓ PDF" import
3. **Analytics** — 4 tabs: Overview (score ring + expense pyramid + what-if), Categories (fixed/variable badge), Trends (daily chart + heatmap + waterfall), Insights (alerts + anomalies + narratives)
4. **Budget** — envelope cards with dynamic progress bars, subscriptions button
5. **Goals** — goal rings, contribution dialog, link transfers by card or keyword
6. **Subscriptions** — auto-detected recurring expenses, missed-payment alerts
7. **Settings** — hero variant, ML toggle, budget alert threshold, biometric, categories CRUD
8. **Onboarding** — native SMS permission request, 90-day import progress, skip option

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
- `SmsReceiver.onReceive()` uses `goAsync()` so the process is not killed before the DB write completes
- All `POST_NOTIFICATIONS` calls are guarded by runtime permission check on API 33+
- All Room database inserts in `FosDatabase.PREPOPULATE_CALLBACK` use parameterized `execSQL()` — no SQL injection surface
- Coroutine errors in `SmsReceiver` are caught by `CoroutineExceptionHandler` — no silent crashes
- CSV export uses RFC 4180 quoting + formula-injection neutralization (`=`, `+`, `-`, `@` prefix with `'`)
- Deep-link routes are validated against an allowlist before navigation (prevents attacker-controlled crash via exported Activity)
- `PushNotificationListener` → `TransactionSource.PUSH` requires user to explicitly enable the notification listener in system Settings
