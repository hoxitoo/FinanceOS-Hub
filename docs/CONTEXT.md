# FinanceOS — Technical Context

## Design Tokens

### Colors
```kotlin
Background  = #0A0D12   // main app background
Surface     = #111620   // cards, sheets, modals
Surface2    = #181E2A   // nested elements, chips
Border      = #1E2738   // dividers, card outlines

Positive    = #4DFFA0   // income, growth, success, savings — ONLY positive
Negative    = #FF6B6B   // expense, alert, overrun — ONLY negative
Warning     = #FFB84D   // warning, progress 70-90%
Info        = #4D9FFF   // info, links

TextPrimary   = #E8ECF4
TextSecondary = #7A8499
TextMuted     = #5A6478
TextDark      = #3A4358
```

### Typography (key styles)
| Style | Size | Weight | Notes |
|-------|------|--------|-------|
| HeroAmount | 34sp | ExtraBold | tabular-nums, letter-spacing -1.4 |
| ScreenTitle | 22sp | ExtraBold | letter-spacing -0.6 |
| CardAmount | 16sp | ExtraBold | tabular-nums |
| TxMerchant | 13.5sp | SemiBold | |
| TxAmount | 13.5sp | Bold | tabular-nums |
| SectionCap | 11sp | Bold | letter-spacing 1.2, ALL CAPS |

### Dimensions
| Token | Value | Use |
|-------|-------|-----|
| ScreenPadding | 16dp | horizontal screen padding |
| CardPadding | 16dp | card inner padding |
| CardGap | 10dp | vertical gap between cards |
| RadiusCard | 18dp | main cards |
| RadiusChip | 20dp | pill chips, filters |
| RadiusIcon | 10dp | category icon squares |
| RadiusBar | 5dp | progress bars |

## Database Schema

### TransactionEntity
```
id: String (UUID)
accountId: String
categoryId: String?
amountKopecks: Long   ← negative=expense, positive=income
type: TransactionType (INCOME/EXPENSE/TRANSFER)
source: TransactionSource (SMS/MANUAL)
merchant: String?
description: String?
timestamp: Long (epoch ms)
smsId: String?        ← "${sender}_${timestamp}_${body.hashCode()}"
isDeleted: Boolean    ← soft delete
deletedAt: Long?
```

### AccountEntity
```
id, name, bank, cardMask (last 4)
balanceKopecks: Long
currency: String
isActive: Boolean
```

### CategoryEntity (16 system categories: 13 expense + 3 income)
```
id, name, emoji, color (hex)
isSystem: Boolean
isActive: Boolean
sortOrder: Int
```

### BudgetEntity
```
id, categoryId, limitKopecks: Long
period: BudgetPeriod (MONTHLY/WEEKLY)
isActive: Boolean
```

### GoalEntity
```
id, name, emoji
targetKopecks, savedKopecks: Long
deadlineAt: Long?
isCompleted: Boolean
```

## Default Categories (16)
```
Expense (13):
cat_food, cat_grocery, cat_transport, cat_housing, cat_health,
cat_shopping, cat_telecom, cat_entertain, cat_education, cat_travel,
cat_beauty, cat_pets, cat_other
Income (3):
cat_salary (Зарплата 💼), cat_income (Прочие доходы 💰), cat_cashback (Кэшбэк 💸)
```

## Categorisation — how it actually works
Two-stage, **deterministic** — there is no on-device learning loop:
1. `DictionaryClassifier` — ~90 seeded merchant rules (literal/regex substring match on
   `merchant + description`, lowercased, first match wins, compiled once and cached).
2. `MLCategoryClassifier` (optional, behind the ML toggle) — a **pre-trained, frozen**
   TFLite model (`merchant_classifier.tflite`, 256→13 softmax). Inference only; the
   weights ship in the APK and never change on device. Below 0.40 confidence it defers
   to the dictionary. Its output space is the 13 **expense** categories only.
3. Fallback — `CategoryDefaults.forType(type)`: any INCOME row that still has no category
   defaults to `cat_income`. Applied at all 3 ingestion sites.

The classifier does **not** learn from manual category edits. Correcting a transaction
changes only that row; to teach the app a new merchant, add a merchant rule (the ML model
would need offline retraining + a new `.tflite`).

## SMS Parsers (P1 Banks)
Each `BankParser` declares `senderPatterns: List<Regex>`.
`ParserEngine` uses Hilt `@IntoSet` multibinding — add new bank = new class + 1 `@Binds` line.

### Sberbank examples
```
"VISA1234 18.06.25 12:34 Оплата 1 500р МАГАЗИН Баланс: 12 345,67р"
"VISA1234 18.06.25 10:00 Зачисление 50 000р"
```

## Financial Score Formula
```kotlin
// ScoreCalculator.kt — max 100 pts
savings   = min(30, (savingsRate / 0.20 * 30))   // target ≥ 20% savings rate
stability = (monthsWithIncome / 3) * 20           // income in last 3 months
mandatory = 25 if mandatoryRatio ≤ 50%            // housing + telecom + health
cushion   = min(25, (balanceMonths / 3.0) * 25)  // target ≥ 3 months buffer

// Color thresholds:
// 70–100 → Positive   "Хорошее здоровье"
// 40–69  → Warning    "Есть над чем работать"
// 0–39   → Negative   "Требует внимания"
```

## Navigation Routes
```
onboarding → dashboard (after onboarding_complete = true)
dashboard | transactions | analytics | budget | goals  ← bottom nav
```

## DataStore Keys (UserPreferences)
```
onboarding_complete: Boolean
hero_variant: String      ("CALM" | "CONTRAST" | "MINIMAL")
biometric_enabled: Boolean
default_currency: String  ("RUB")
last_import_at: String
```

## Screen: Transactions (Critical rule)
```kotlin
val amtColor = if (tx.type == EXPENSE) FosColors.Negative else FosColors.Positive
```

## Screen: Analytics — Trends Tab
- SVG line chart (Compose Canvas, cubic bezier) as PRIMARY
- `LineChart.kt` — fill area + stroke + dot at last point

---

## Behavioral Analytics Vision (Phase 2)

> Source: product spec. Most features implemented in pure Kotlin. TFLite only for Phase 3 ML clustering.

### Phase 2A — Pure Kotlin (no ML, implement next)

#### Spending Heatmap
- 7×24 grid (X = day of week, Y = hour of day)
- Cell color intensity = sum of expenses at that slot / max slot
- Component: `HeatmapGrid.kt` (Canvas DrawScope)
- Data: `groupBy { dayOfWeek, hourOfDay }` from TransactionEntity.timestamp

#### Payday Effect Detection
- Find income transactions (type=INCOME, amount > median income)
- Compare spending sum in D+1..D+3 vs baseline (same 3-day window in other weeks)
- Alert if ratio > 1.3: "После зарплаты ты тратишь на X% больше в первые 3 дня"

#### Budget Fatigue Curve
- Group expenses by `dayOfMonth` (1..31), average across last 3 months
- Line chart showing discipline decay curve
- Visible in Trends tab as secondary chart

#### Impulse vs Planned Classification
- Heuristic rules (no ML needed):
  - Impulse: amount < 2000₽ AND hour ∈ [22..23, 0..5]
  - Planned: amount > 3000₽ AND hour ∈ [8..12] AND dayOfWeek ∈ WEEKDAY
- Metric: impulse% = impulseCount / totalCount, tracked monthly

#### Smart Category Anomaly Alerts
- Per-category rolling 3-month average (avgKopecks, stdDev)
- Alert if currentMonth > avg * 1.3: "Продукты на 34% выше среднего. Пик — 3 покупки 12 июня."
- Also: subscription gap detection — if category had ≥1 tx/month for 3+ months but 0 this month

#### Waterfall Chart (Month-over-Month)
- Bars: income delta, each expense category delta, net result
- Visual: green bars up (savings/income), red bars down (expense growth)
- Shows exactly WHAT changed between months

#### Rolling 3-Month Average
- Per-category, displayed as dashed reference line on bar/line charts
- Removes noise, shows real trend

#### What-If Simulator
- Inputs: category delta (e.g. "food −3000/mo"), income delta
- Output: projected annual savings delta, progress toward active goals
- Pure arithmetic — no ML

#### Savings Projection
- Formula: `(currentIncome - currentExpense) * months`
- Show 6 / 12 / 24 month projections with goal milestones highlighted

#### Narrative Insights (Personal)
- Generated monthly/weekly, stored in local DB:
  - "Твой самый дорогой день — 14 марта, 47 800 ₽"
  - "За 3 месяца ты тратишь на еду в среднем N ₽ в день"
  - "Savings rate вырос с 8% до 26% за 3 месяца"
- Template-based, filled from analytics engine output

#### Expense Pyramid
- Tier 1 (Обязательные): housing, telecom, transport, health
- Tier 2 (Регулярные необязательные): grocery, subscriptions, cafe
- Tier 3 (Дискреционные): shopping, entertainment, travel
- If Tier 1 > 60% income → critical insight
- Component: vertical stacked bar with 3 colors

#### Fixed vs Variable Expenses
- Fixed: same merchant/category ±10% for 3+ consecutive months
- Variable: everything else
- Ratio shown as metric: "Вы контролируете X% расходов"

### Phase 3 — TFLite ML (requires pre-trained model)

#### Behavioral Clustering
- Input features: hour-of-day, day-of-week, category, amount bucket, merchant frequency
- Goal: cluster transactions into behavioral patterns (e.g. "weekly grocery run", "evening impulse")
- Model: TFLite classification model bundled as asset
- Dependency to add: `org.tensorflow:tensorflow-lite:2.14.0`

#### Predictive Spending
- Time series model: 30-day expense history → next 7-day forecast
- More accurate than simple linear extrapolation
- TFLite LSTM or MobileNet-based regressor

#### Smart Merchant Categorization
- Embedding-based: merchant name → category vector similarity
- Better than dictionary lookup for unknown merchants
- TFLite text embedding model

### Implementation priority order (Phase 2A)
1. `HeatmapGrid.kt` — visual impact, pure Canvas
2. Payday effect + budget fatigue → `AnalyticsEngine` methods
3. Category anomaly alerts → `InsightGenerator` rules
4. Impulse classification → `TransactionAnalyzer` (new class)
5. Waterfall chart → `WaterfallChart.kt` (Canvas)
6. Narrative insights → `NarrativeEngine` (template system)
7. What-if simulator → `WhatIfSimulator.kt`
8. Savings projection → extend `AnalyticsEngine`
9. Expense pyramid + fixed/variable → `StructuralAnalyzer`

---

## Architecture Decisions (Post-Audit)

### Amount sign convention
- `EXPENSE` transactions are stored with **negative** `amountKopecks` in Room
- When computing totals for UI display, always apply `abs()`: `sumOf { abs(it.amountKopecks) }`
- `FosFormatter` handles sign display — do not negate twice

### Flow.first() vs blocking collect
- Use `flow.first()` inside `suspend fun` to read a single snapshot — never use `flow.collect { result = it; return@collect }` which is a broken pattern that only coincidentally reads the first element in a cold flow
- In Hilt `@Provides` functions (which cannot be `suspend`), use `runBlocking(Dispatchers.IO) { pref.first() }` — never `runBlocking` without a dispatcher on the main thread

### Coroutine safety in BroadcastReceiver
- `SmsReceiver` uses a `CoroutineScope` with `SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler`
- The `CoroutineExceptionHandler` logs to `android.util.Log.e` — exceptions do not propagate and crash the app
- `goAsync()` is NOT used because each SMS is processed independently and the receiver returns quickly

### SQL injection surface
- All `db.execSQL()` calls in `FosDatabase.PREPOPULATE_CALLBACK` use the parameterized two-arg form: `execSQL(sql, arrayOf(...))`
- No user input is ever interpolated into SQL strings anywhere in the codebase

### Notification permission guard (API 33+)
- `NotificationHelper.hasNotificationPermission()` checks `Manifest.permission.POST_NOTIFICATIONS` on API ≥ TIRAMISU
- All three `notify()` entry points call this guard — budget alert, weekly summary, insight notification
- Onboarding requests the permission at launch; if denied, the app still works but sends no notifications

### LazyColumn key stability
- All `items()` calls use `key = { it.id }` or equivalent unique key
- This prevents Compose from reusing wrong item composables when the list is filtered/sorted
