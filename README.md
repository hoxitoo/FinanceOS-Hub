# FinanceOS Hub

Offline-first Android finance app that reads bank SMS messages and automatically tracks your spending — zero manual input required.

## Features

| Feature | Details |
|---------|---------|
| **Auto-import** | Reads SMS from 12 banks (11 RU + МБанк KG); imports last 90 days on request; SMS is **opt-in** — a fresh install never reads messages until you say so |
| **Push capture** | `PushNotificationListener` captures bank app notifications in real-time alongside SMS; reads **every** text extra (title/text/bigText/subText/summary/info/inbox lines/ticker) so a balance line in any field is seen |
| **Real-time** | New transactions appear instantly via `SmsReceiver` BroadcastReceiver (with `goAsync()`); cross-channel SMS↔push dedup within ±5 min |
| **Marketing filter** | `PromoFilter` drops credit-card offers and cashback ads before any parser runs — a "лимит 163 000 ₽" promo is never booked as a transfer |
| **Smart categorization** | Deterministic dictionary classifier (~183 merchant rules across 18 categories incl. transit, marketplaces, bookmakers, subscriptions, income); optional pre-trained TFLite ML layer (inference-only, no on-device learning). **Rules always win over the model** — it is frozen at 13 labels and cannot name a category added later |
| **Subscriptions category** | Netflix, Яндекс Плюс, Кинопоиск, СберПрайм, YouTube, iCloud, Adobe и ещё три десятка сервисов идут в отдельную категорию «Подписки», а не в «Развлечения»: билет в кино — разовая покупка, ежемесячное списание — совсем другая строка бюджета |
| **Account linking** | Card mask from SMS/push (e.g. ··2548) auto-links transactions to the correct account; the bank's «Остаток» is applied as an authoritative snapshot, with a recency guard so a fresher manual edit is never reverted |
| **Credit cards** | `AccountKind.CREDIT` — the balance is a **debt**, never mixed into net worth. Dashboard tile (free limit + debt + deadline) opens a dedicated screen: payment amount and date, interest-free period bar, rate, utilisation, repayment, per-card history |
| **Credit push parsing** | On a credit card Сбер prints the **free limit** under the same «Баланс» label a debit card uses — only the account kind can tell them apart, so it is converted to a debt using the card's limit. The «Внесите платёж … до …» reminder is filed as a **fact about the card, not an operation**, and the bank's own figure outranks anything the app infers |
| **Repayment** | «Погасить» books a **TRANSFER** off one of your accounts, never an expense (the purchases were already counted). Two rows, one per account, so deleting either undoes its own side. Money arriving on a credit card is classified as repayment, not income |
| **Interest estimate** | «Переплата сейчас» — an exact **zero** inside the interest-free period, an estimate once the deadline passes. «Если платить только минимум» simulates the real cost of carrying a balance. Both are labelled estimates; both are hidden without a rate |
| **Multi-currency** | RUB / USD / EUR / KGS (сом) per account **and per transaction**; hero shows each currency on its own line |
| **Manual entry** | Add (bank→account picker, income presets, Расход/Доход/**Перевод**), edit, delete; transfers credit the destination account too |
| **Swipe-to-delete** | Swipe **left** to reveal a red trash button, tap it to confirm — a flick alone never deletes |
| **PDF import** | Import bank statements (Alfa-Bank "Операции по счету" layout) via SAF |
| **Financial score** | 0–100 across 4 pillars; rendered as a **multi-colour donut** (one arc per pillar, dimmed shortfall) so a weak pillar is visible at a glance |
| **Behavioral analytics** | Heatmap, fatigue curve, payday effect, impulse classification, anomaly detection, subscription gaps |
| **Analytics period** | Месяц / 6 мес / Год / Всё время chips drive the category + daily breakdown (the health score keeps its own point-in-time window by design) |
| **Category drill-down** | Interactive pseudo-3D pie — tap a slice to explode it, then open every operation of that category for this **and** last month |
| **Transfer routing** | Bank transfers (СБП/перевод) classified as TRANSFER; auto-routed to savings goals by account / card / keyword, or paired between accounts so net worth is unchanged |
| **Budget envelopes** | Monthly/weekly limits per category, dynamic color bar (green→amber→red), alerts throttled to **once per budget per month, max 2/day** (persisted, survives restart) |
| **Savings goals** | Goal cards with 9 bundled pixel-art backdrops, **± dialog to add or withdraw**, per-goal **history** of routed operations, link by account / card / keyword |
| **Subscriptions** | Auto-detected recurring expenses, missed-payment alerts, monthly total |
| **Insights & narratives** | 8 Russian narrative templates, CRITICAL/WARNING/INFO severity alerts |
| **What-if simulator** | Interactive sliders for 6/12/24-month savings projections |
| **Календарь и «Свободно»** | Главное число экрана — не остаток и не прогноз, а «сколько можно потратить, ничего не сломав»: деньги на счетах минус незакрытые обязательства до горизонта минус ваш резерв. Горизонт по умолчанию — до **следующего поступления**, а не до конца месяца. Ожидаемая зарплата показывается, но НЕ прибавляется. Источники: объявленные платежи, платёж по кредитке, конец беспроцентного периода, найденные подписки, дедлайны целей |
| **Закрытие обязательств** | Когда в истории появляется подходящая операция (та же валюта, то же направление, ±15 % по сумме, окно −5/+7 дней, счёт если указан), обязательство помечается оплаченным и перестаёт вычитаться из «Свободно». Сопоставление намеренно осторожное: при сомнении обязательство остаётся открытым, а любую отметку можно снять кнопкой «Отвязать» |
| **Savings calculator** | Отдельный экран с тремя режимами: что накопится за срок, за сколько наберётся нужная сумма, сколько для этого откладывать. Капитализация (месяц/квартал/год/без), взнос в начале или конце месяца, ежегодная индексация взноса, инфляция → «в сегодняшних деньгах», НДФЛ 13 %, эффективная ставка, разбивка «своё / проценты» столбиками и таблицей по годам, и **точка перелома** — год, когда проценты начинают приносить больше ваших взносов. Подставляет ваш собственный темп накопления и суммы ваших целей |
| **Backup / restore** | Full 9-table export to a `.fose` file, AES-GCM-256 encrypted via Android Keystore; restore is additive, idempotent and FK-safe |
| **Notifications** | Budget alerts, weekly summaries, critical insights, update-available (4 channels) |
| **Deep-links** | Notification taps navigate directly to the relevant screen (allowlisted routes) |
| **Settings** | Hero variant, animations/atmosphere/cat mode, budget alert threshold, biometric lock, ML toggle, SMS opt-in, categories CRUD, backup, updates |
| **Biometric lock** | Off by default and never prompts until the preference is confirmed; always offers a device-PIN escape hatch so a broken sensor can't lock you out |
| **Shimmer & Cat mode** | Optional atmosphere layer (particles, tilt/sheen cards, breathing hero, bioluminescent ripple) and a mood-matched cat mascot with paw-print particles |
| **In-app update** | Checks GitHub Releases, downloads and installs a newer APK; a 12 h background worker pushes a notification when one appears |
| **Home-screen widget** | 2×2 balance widget via `AppWidgetProvider` |
| **Offline by design** | The updater is the only networked component; no financial data ever leaves the device |

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

1. **Dashboard** — net worth hero (3 variants: Calm/Contrast/Minimal), current-month label, income/expense/forecast metrics, **credit-card tile** (free limit + debt + nearest deadline), **«Свободно» tile** (появляется, только когда в календаре есть обязательства), accounts with volumetric bank cards, clickable recent transactions
2. **Transactions** — grouped list, search, filter chips (All/Expense/Income), swipe-left-to-reveal delete, detail/edit sheet with source diagnostics, "↑ CSV" export, "↓ PDF" import
3. **Analytics** — period chips + 4 tabs:
   - **Обзор** — multi-colour score donut with a per-pillar legend, expense pyramid, what-if simulator, archetype card
   - **Категории** — interactive 3D pie (tap to explode), ТОП-3 траты, full category list; tap any category for a month-vs-month drill-down of its operations
   - **Тренды** — daily spending curve, «Когда ты тратишь» as two tappable donuts (weekday / 4-hour bucket), «Усталость бюджета» bar chart, «Месяц к месяцу» diverging bars with `было → стало`, «Импульсивность» with the actual flagged purchases. Every section has a «?» badge explaining the heuristic in plain language
   - **Инсайты** — alerts, anomalies, narratives
4. **Budget** — envelope cards with dynamic progress bars, subscriptions button
5. **Goals** — pixel-art goal cards, ± dialog to add **or withdraw**, «История ›» of routed operations, 🔗 link transfers by account / card / keyword, «🧮 Калькулятор» in the header
6. **Калькулятор** — three modes over one monthly simulation; fine-tuning panel; «ваш темп» and goal chips prefill from your own data; every figure is explicitly labelled an estimate
7. **Календарь** — «Свободно» героем с разложенной арифметикой (счета − обязательства − резерв), два режима — полоса ближайших дат и сетка месяца (точки по видам событий, выбранный день фильтрует список), список событий с пометкой источника (цифра банка / объявлено вами / найдено), подтверждение найденных подписок одним касанием, раздел «уже прошло» с возможностью отвязать
8. **Кредитные карты** — total free limit and debt, per-card block (payment amount and date large, interest-free period bar, rate, utilisation, «Погасить»), combined history across cards
9. **Subscriptions** — auto-detected recurring expenses, missed-payment alerts
10. **Settings** — hero variant, customization (animations / atmosphere / cat mode), SMS opt-in + 90-day import, bank push listener, ML toggle, budget alert threshold, biometric, categories CRUD, backup/restore, updates
11. **Onboarding** — explicit choice between "импортировать из SMS за 90 дней" and "добавлю вручную"

## Tech Stack

- **Kotlin** + **Jetpack Compose** (BOM 2024.06, Material 3, custom dark theme)
- **Hilt** — dependency injection with `@IntoSet` multibinding for parsers
- **Room 2.6.1** — local SQLite, schema **v14**, amounts as Long kopecks (×100). Account writes use `@Upsert` (never `@Insert(REPLACE)`, which would CASCADE-delete the account's cards)
- **DataStore** — ~20 preference keys (hero variant, notifications, ML, shimmer/cat mode, SMS opt-in, budget-alert throttle state, update prefs)
- **WorkManager** + **HiltWorkerFactory** — daily analytics job + 12 h update check
- **TFLite 2.14.0** — optional ML layer (graceful fallback when model files absent)
- **PdfBox-Android 2.0.27.0** — statement import
- **Android Keystore (AES-GCM-256)** — encrypted backups
- **Clean Architecture + MVVM**

## Project Structure

```
app/
├── core/
│   ├── database/       # Entities, DAOs, FosDatabase (v17 — 18 categories, ~183 merchant rules)
│   ├── parser/         # BankParser, ParserEngine, 12 bank parsers, TransferPatterns, PromoFilter, CreditNoticeParser, AmountParser
│   ├── classifier/     # DictionaryClassifier, CategoryDefaults, CategoryClassifier interface
│   ├── sms/            # SmsReceiver (real-time), SmsReader (90-day import), PushNotificationListener
│   ├── account/        # AccountLinker (card→account resolution, authoritative balance, orphan re-link)
│   ├── credit/         # CreditMath (debt, free limit, cycle, min payment, interest), CreditNoticeApplier
│   ├── transfer/       # TransferRouter (goal routing, internal pairing, counterparty leg)
│   ├── finance/        # SavingsMath (прогноз накоплений, срок до цели, требуемый взнос)
│   ├── calendar/       # CalendarEvent, PaymentDates, CalendarBuilder, FreeMoney, ObligationMatcher, ObligationSyncer
│   ├── analytics/      # AnalyticsEngine, ScoreCalculator, InsightGenerator, BehavioralAnalyzer, NarrativeEngine
│   ├── ml/             # ModelLoader, TextFeatureExtractor, MLCategoryClassifier, SpendingPredictor, BehavioralCluster
│   ├── pdf/            # PdfImporter, PdfTransactionParser
│   ├── backup/         # BackupManager, BackupCrypto (AES-GCM via Keystore)
│   ├── update/         # UpdateChecker, UpdateCheckWorker
│   └── notifications/  # NotificationHelper (4 channels + deep-links + permission guard)
├── data/
│   ├── repositories/   # Tx, Account, Card, Category, Budget, Goal, TransferRoute
│   └── preferences/    # UserPreferences (DataStore)
├── di/                 # DatabaseModule, ParserModule, RepositoryModule, MLModule, AnalyticsModule
├── features/           # dashboard, transactions, analytics, budget, goals, calculator, calendar, subscriptions, categories, credit, onboarding, settings
├── navigation/         # FosNavHost, FosRoutes, bottom nav
├── widget/             # BalanceWidget
└── ui/
    ├── theme/          # FosColors, FosType, FosDimens, FosSurface (огранка карточек), FosTheme, FosFormatter, AmountVisualTransformation, Shimmer
    └── components/     # TransactionRow, FosSection (заголовки + «?»), LineChart, ScoreRing, ScoreDonut, Pie3D, AnalyticsCharts,
                        # GoalRing, GoalArt, HeatmapGrid, ExpensePyramid, WhatIfSimulator,
                        # SwipeToRevealDelete, CatMascot, ParticleLayer, CurrencyReef, ShimmerCardFx
```

## Build

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lintDebug
```

CI (`.github/workflows/android.yml`) runs all three on every PR targeting `dev` or `main`.

**Unit tests** (`app/src/test/`):

| Suite | Coverage |
|-------|----------|
| 12 × `*ParserTest` | every bank parser, ~7 cases each |
| `TransferPatternsTest` | transfer detection, card-mask extraction, stem anchoring |
| `PromoFilterTest` | marketing pushes are dropped, real operations pass |
| `PdfTransactionParserTest` | Alfa statement layout, logical-row reconstruction |
| `BehavioralAnalyzerTest` | all 7 public methods + edge cases (28 cases) |
| `InsightGeneratorTest` | all 6 rules + sort order (28 cases) |

There are **no instrumented/UI tests** — screen behaviour, gestures and Compose rendering are
verified by review, not automatically.

## Install & Update (sideload)

The app ships as a sideloadable APK — no Play Store needed.

**Get the APK.** Every push to `main` triggers the `Release APK` workflow
(`.github/workflows/release-apk.yml`), which builds the debug APK and publishes it as a
**GitHub Release**. Download `FinanceOS-Hub-vX.apk` from the
[Releases page](https://github.com/hoxitoo/financeos-hub/releases/latest), open it on an
Android device, and allow installation from this source.

**Self-update.** Inside the app, **Настройки → Обновления → Проверить обновления** queries
the GitHub Releases API, compares the latest tag against the installed `versionName`, and —
if newer — downloads the APK and launches the system installer. This is the *only* feature
that uses the network (`INTERNET` + `REQUEST_INSTALL_PACKAGES`); no user data leaves the
device.

**Stable signing.** All debug APKs (CI and local) are signed with the repo-committed
`app/debug.keystore` (password `android`, debug-only). A single shared signature is what lets
the in-app updater install a newer build over an older one without a signature mismatch. The
distributed debug build drops the `.debug` applicationId suffix so updates replace the same
package the user installed.

## ML Classification (pre-trained, inference-only)

The ML layer uses **frozen, pre-trained** TFLite models bundled in
`app/src/main/assets/models/` — they run inference on-device but **never train or learn
on-device**. The weights are fixed at build time; correcting a transaction's category does
not retrain anything. To improve the model you retrain offline and ship a new `.tflite`.

| File | Shape | Purpose |
|------|-------|---------|
| `merchant_classifier.tflite` | `float[1][256]` → `float[1][13]` | SMS merchant → category (13 expense classes, ≥0.40 confidence else dictionary) |
| `spending_predictor.tflite` | `float[1][30][1]` → `float[1][1]` | End-of-month spend forecast |
| `behavioral_cluster.tflite` | `float[1][7]` → `float[1][5]` | User archetype (5 clusters) |

Without the model files the app runs entirely on the rule-based classifiers — no runtime
errors. Income categories and any merchant the model can't place fall back to the
dictionary rules + `CategoryDefaults`.

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
- SMS reading is **opt-in** (`sms_realtime_enabled`, default `false`) — a fresh install never touches the inbox until the user enables it
- Backups are encrypted with an AES-GCM-256 key held in the Android Keystore (hardware-backed where available); the `FOSENC1:` header keeps older plaintext exports restorable
- `UpdateChecker` refuses any release asset URL that is not `https://` before opening a connection
