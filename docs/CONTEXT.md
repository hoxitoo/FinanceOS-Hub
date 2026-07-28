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

Room schema **version 10**. Every migration is registered in `DatabaseModule.addMigrations(...)`.

| Migration | Change |
|-----------|--------|
| 2→3 | `transactions.goal_id`, `transactions.transfer_pair_id`, `transfer_routes` table |
| 3→4 | indexes on `goal_id` / `transfer_pair_id` |
| 4→5 | re-seed categories + merchant rules (income categories, transit rules) |
| 6→7 | `transactions.balance_kopecks` |
| 7→8 | `transactions.currency` (`NOT NULL DEFAULT 'RUB'`) |
| 8→9 | `transactions.raw_text` (captured message body, for diagnostics) |
| 9→10 | re-seed (`cat_betting` + marketplace/bookmaker rules) |

### TransactionEntity
```
id: String (UUID)
accountId: String?
categoryId: String?
amountKopecks: Long    ← negative=expense, positive=income, TRANSFER signed by direction
currency: String       ← "RUB" | "USD" | "EUR" | "KGS"  (per-transaction, not just per-account)
type: TransactionType (INCOME/EXPENSE/TRANSFER)
source: TransactionSource (SMS/PUSH/MANUAL/PDF)
merchant: String?
description: String?
sourceMask: String?    ← card last-4 as printed in the message
balanceKopecks: Long?  ← bank-authoritative «Остаток», null when the message had none
rawText: String?       ← exact captured body (shown in the detail sheet as «Исходный текст»)
goalId: String?        ← set when TransferRouter routed it to a savings goal
transferPairId: String? ← set when both legs of one internal transfer were matched
timestamp: Long (epoch ms)
smsId: String?         ← "${sender}_${timestamp}_${body.hashCode()}"
isDeleted: Boolean     ← soft delete
deletedAt: Long?
```

### AccountEntity
```
id, name, bank, cardMask (last 4)
balanceKopecks: Long
currency: String
isActive: Boolean      ← soft delete; deactivating also deactivates its cards and cuts its goal-routes
updatedAt: Long        ← recency arbiter: a stored bank balance only wins if it is NEWER than this
```

### CardEntity / TransferRouteEntity
```
CardEntity:          id, accountId (FK, onDelete = CASCADE), cardMask, isActive
TransferRouteEntity: id, goalId, matchType (ACCOUNT|CARD|KEYWORD), matchValue (lowercased), isActive
```

### CategoryEntity (17 system categories: 14 expense + 3 income)
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

## Default Categories (17)
```
Expense (14):
cat_food, cat_grocery, cat_transport, cat_housing, cat_health,
cat_shopping, cat_telecom, cat_entertain, cat_education, cat_travel,
cat_beauty, cat_pets, cat_other, cat_betting (Букмекер 🎰)
Income (3):
cat_salary (Зарплата 💼), cat_income (Прочие доходы 💰), cat_cashback (Кэшбэк 💸)
```
`sort_order` is the seed-list index, and existing installs keep their stored order via
`INSERT OR IGNORE` — so a **new category must be appended LAST**, never inserted mid-list,
or new and existing installs would disagree on the order. The colour list must be extended
in step (17 categories / 17 colours) to keep `colors[i]` in range.

## Categorisation — how it actually works
Two-stage, **deterministic** — there is no on-device learning loop:
1. `DictionaryClassifier` — 143 seeded merchant rules (literal/regex substring match on
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

## Ingestion pipeline (SMS · push · PDF · manual)

Each `BankParser` declares `senderPatterns: List<Regex>`.
`ParserEngine` uses Hilt `@IntoSet` multibinding — add new bank = new class + 1 `@Binds` line.

```
SmsReceiver / PushNotificationListener / SmsReader
        │
        ├─ PromoFilter.isPromo(body)? ─────────────► drop (marketing offer)
        │
        ├─ ParserEngine.parse(sender, body)
        │     ├─ normalise NBSP/narrow-NBSP → space (once, centrally)
        │     ├─ every parser whose sender matches is tried (firstNotNullOfOrNull),
        │     │  not just the first — a sender can be claimed by several banks
        │     └─ each parser runs TransferPatterns FIRST, so a transfer is never
        │        mis-booked as a purchase or as sign-inverted income
        │
        ├─ dedup: existsBySmsId  →  existsSimilarSmsOrPush(±5 min, same |amount|)
        │     └─ on a dedup hit the row is NOT inserted, but the message's «Остаток»
        │        IS still applied (applyAuthoritativeBalance) — the dropped twin often
        │        carries the balance the kept row lacked
        │
        ├─ AccountLinker.resolveAccountId(cardMask, bankId)
        │     card_mask exact → CardEntity → last-4 digit-tolerant → bank-name (only if unique)
        │     ...all restricted to ACTIVE accounts, so a deactivated "ghost" can never win
        │
        ├─ insert (signedKopecks) + persist currency, balanceKopecks, rawText
        │
        ├─ AccountLinker.syncBalance — authoritative «Остаток» if present (>= 0), else delta
        └─ TransferRouter.onTransactionInserted — goal routing / counterparty leg / pairing
```

**Balance rules (learned the hard way):**
- A bank-reported «Остаток» is an **absolute snapshot**; a message without one applies a **delta**.
- `snapToAuthoritativeIfNewer` only overwrites an account balance when the stored snapshot's
  timestamp is newer than `account.updatedAt` — so re-linking a card never reverts a fresher
  manual correction.
- Deleting a row that was applied as a **delta** (`accountId != null && balanceKopecks == null`,
  any source but PDF) reverses it. A row that carried a real «Остаток» is left alone.

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

**Windowing (why the score does not crater on the 1st of the month):**
`last3Income` / `avg3Expense` / cushion use offsets `1..3` — three **completed** months, never
the current partial one. The savings + mandatory pillars are current-month by definition, so
when the current month still has zero income `buildScoreInput` falls back to the last completed
month for income/expense/mandatory. Without this an empty July 1st scored 0/30 savings and a
falsely perfect 25/25 mandatory.

**Rendering:** `ScoreDonut` draws one arc per pillar, each owning a slice proportional to its
max points (Сбережения 30 → Positive, Стабильность 20 → Info, Обязательные 25 → Warning,
Подушка 25 → GlowViolet). Inside its slice the earned part is full colour, the shortfall stays
dimmed (α .16). `FosColors.Negative` is deliberately never used for a slice — red means
"expense/overrun" in this design system and would read as an error, not a category.

## Navigation Routes
```
onboarding → dashboard (after onboarding_complete = true)
dashboard | transactions | analytics | budget | goals   ← bottom nav
settings | categories | subscriptions                   ← pushed routes
transactions?categoryId=<id>                            ← optional pre-filter (subscription deep-link)
```
Deep-links from notifications are validated against `FosRoute.sanitizeDeepLink` (allowlist) in
both `MainActivity` and `FosNavHost` — the Activity is exported, so an unvalidated route string
is attacker-controllable.

## DataStore Keys (UserPreferences)
```
onboarding_complete: Boolean
hero_variant: String            ("CALM" | "CONTRAST" | "MINIMAL")
biometric_enabled: Boolean       (default false)
default_currency: String         ("RUB")
last_import_at: String
notifications_enabled: Boolean
budget_alert_threshold: String   ("80")
budget_alert_day / _count / _keys        ← alert throttle state (epoch day, counter, ≤50 keys)
ml_classification_enabled: Boolean
push_listener_enabled: Boolean
sms_realtime_enabled: Boolean    (default FALSE — SMS reading is opt-in)
animations_enabled / atmosphere_enabled / cards_variant_b / cat_mode_enabled
update_notifications_enabled: Boolean (default true)
last_notified_version: String
```

## Screen: Transactions (Critical rule)
```kotlin
val amtColor = when (tx.type) {
    EXPENSE  -> FosColors.Negative     // red — ALWAYS
    INCOME   -> FosColors.Positive
    TRANSFER -> FosColors.TextPrimary  // neutral "↔ amount", never red/green
}
```
Deletion is **swipe-left-to-reveal + tap the trash** (`SwipeToRevealDelete`), never an
auto-dismiss on a flick.

## Screen: Analytics

A period chip row (`AnalyticsPeriod`: MONTH / HALF_YEAR / YEAR / ALL) sits under the title. It
filters `categoryExpenses`, `dailyExpenses` and `transactions` only — **every `analyticsEngine.*`
call keeps its own monthly/rolling window by design** (a health score is point-in-time, not a
period aggregate), so the chips cannot perturb the score or the behavioural math.

### Обзор
`ScoreDonut` + per-pillar legend, expense pyramid, what-if simulator, archetype card.

### Категории
`Pie3D` — the disc is squashed vertically (`PERSPECTIVE = .52`) and redrawn a few pixels lower
in a darker shade to fake the extrusion (bounded to ~12 layers so it stays cheap on a 3x screen).
Hit-testing **un-squashes the touch point back into circle space** before measuring the angle;
angles start at 0° = 3 o'clock and run clockwise, matching the draw order exactly. Tapping a
slice explodes it; tapping a category opens `CategoryOpsSheet` — all its operations for the
current AND previous month with a month-over-month headline. That drill-down deliberately
ignores the period chips (it answers a fixed month-vs-month question) and matches
`category_id IS NULL` for the synthetic `cat_other` bucket.

### Тренды
- daily spending curve (`LineChart` — Compose Canvas, cubic bezier, fill + stroke + last dot)
- «Когда ты тратишь» — two tappable `SegmentedDonut`s (weekday / 4-hour bucket). The 7×24
  `HeatmapGrid` was too tall and too fine-grained to read on a phone.
- «Усталость бюджета» — `FatigueBars`, one bar per day of month, average reference line,
  above-average days in Warning, peak day in Negative
- «Месяц к месяцу» — `MoMComparison` diverging bars: left+green = потратили меньше,
  right+red = потратили больше, with `было X → стало Y`. `WaterfallBar.isIncome` flips the
  semantics for the income row (a rise there is GOOD) — without it an income increase paints red.
- «Импульсивность» — proportion bar + the **actual flagged purchases** (merchant, date, hour)
- every section carries an `InfoBadge` («?») explaining the heuristic in plain language,
  including that impulse detection is a time/amount heuristic (< 2 000 ₽ between 21:00–06:00)

### Инсайты
Alerts, anomalies, narratives. `InsightCard` uses a coloured LEFT BORDER only, no icon.

## Money input fields

All five money fields (account balance edit, add-account balance, add-transaction amount, goal
contribution, budget limit) share one contract:
- state holds the **raw** string; grouping is applied by `AmountVisualTransformation`
  (a `VisualTransformation` + exact `OffsetMapping`). Formatting `value` directly desynchronises
  the caret — `BasicTextField` re-applies its retained selection to the supplied text, so typing
  `12345` produced `12354` once a separator appeared.
- `FosFormatter.sanitizeAmountInput` allows one separator and ≤2 decimals, so a second separator
  can't silently make the value unparseable (field showing `1,23`, **0 ₽** saved).
- `FosFormatter.parseAmountInput` **rounds** instead of truncating — `(1417.59 * 100).toLong()`
  is `141758` in binary floating point and lost a kopeck on every edit.

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
- `onReceive()` wraps the work in `goAsync()` and calls `pendingResult.finish()` in a `finally`.
  Without it Android can kill the process before the DB write completes when the app is
  backgrounded. `BalanceWidget` does the same — and must call `finish()` in exactly ONE place
  (a double-finish crashes).

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

### Room: `@Upsert`, never `@Insert(onConflict = REPLACE)` on accounts
In SQLite, REPLACE on an existing row is **DELETE + INSERT**. `CardEntity` has
`ForeignKey(onDelete = CASCADE)`, so every `accountRepo.upsert(account.copy(balance = …))` —
balance edit, manual op, delete-reversal, backup restore — silently deleted all of the account's
cards. This was the root cause of the long-running "cards keep detaching themselves" saga.
`@Upsert` updates in place; no delete, no cascade.

### Soft delete leaves references behind
`deactivate` (`is_active = 0`) does not remove rows that point at the account. `deleteAccount`
must therefore ALSO deactivate the account's cards and drop its ACCOUNT-type goal-routes,
otherwise a re-created account gets a new id and the stale card/route point at a ghost. All
account resolution (`AccountLinker`) filters to active accounts only, and
`linkOrphansToAccount` reclaims rows whose `account_id` points at a non-active account — never
one already on a live account.

### Compose Rules of Hooks
`remember`, `LaunchedEffect`, `rememberInfiniteTransition`, `animate*AsState` must be called
**unconditionally**, before any early return. A guard like `if (!enabled) return` placed above
them corrupts the slot table the moment the toggle flips at runtime. Inactive animations are
expressed as a `1f..1f` transition, not as a skipped call. Every `remember` holding per-item
state also needs a key (`remember(transaction.id)`) or a reused sheet shows the previous item's
data.

### Cancellation must not be swallowed
`runCatching {}` catches `CancellationException`, which breaks `collectLatest` / `mapLatest`
cancellation — the "cancelled" work keeps running to completion. Every guarded block re-throws
it (`onFailure { if (it is CancellationException) throw it }`), or uses `try/catch` that does.

### `stateIn` belongs to the ViewModel, not to a function call
`fun historyFor(id) = flow.stateIn(viewModelScope, …)` leaks one sharing coroutine per
invocation — and a composable body invokes it on every recomposition. Both `GoalsViewModel`
and `AnalyticsViewModel` cache these per id, and call sites still wrap them in `remember(id)`.

### Fail open, never lock the user out
`MainActivity` renders nothing until the biometric preference has been read (`lockChecked`), and
**both** reads default to "biometrics off" if they throw. The lock screen always offers
«Войти по PIN-коду устройства». An earlier version started `isLocked = true` and prompted before
the preference was known — a tester with a broken sensor had to reinstall and lost all history.

---

## Roadmap — Planned Features (Account Types & Card UI)

> Status: **PLANNED, not yet implemented.** Design notes captured so the work can be picked up later.
> Foundational dependency: all three account-related items below want a new
> `AccountEntity.kind: AccountKind (CASH | INVESTMENT | CREDIT | SAVINGS)` column
> (DB migration v6→v7) and a net-worth aggregation split by kind. Build that first,
> then layer the rest. The branded-card UI is an independent UI-only track.

### 1. Bank registry — single source of truth (refactor)
**Problem:** a bank's name/colour/letter/keywords are currently duplicated across
**three** unconnected places, and adding a bank means editing all three (МКБ + Цифра
shipped with the card display but were missing from the account picker for exactly this reason):
- `ui/theme/BankColors.kt` → `bankBrand()` (brand colour mapping)
- `features/dashboard/DashboardScreen.kt` → `BankSymbolBadge()` (letter abbreviation)
- `features/dashboard/AddAccountSheet.kt` → `BANKS` list (picker chips)

**Plan:** collapse into one `BankRegistry` (a `List<BankSpec>` where
`BankSpec(id, displayName, keywords, brand, badge/logo, cardSkin)`).
`bankBrand()`, `BankSymbolBadge`, and the picker all derive from it.
Adding a bank = one `BankSpec` entry. Also lets `AccountLinker.BANK_KEYWORDS`
read from the same source (currently a 4th duplicate).

### 2. Branded card UI redesign
Replace the flat "brand colour + single letter" card with bank-authentic styling.
Extend the brand model to a card *skin*:
```kotlin
data class BankBrand(
    val gradient: List<Color>,   // diagonal brand gradient (BankCard already uses linearGradient)
    val onBg: Color,
    val logo: Int? = null,       // R.drawable.logo_* vector — replaces the letter badge
    val pattern: CardPattern = NONE  // Alfa wave, Tinkoff stripe, etc.
)
```
- Logos: per-bank vector drawables in `res/drawable`; `BankSymbolBadge` renders the
  logo when present, falls back to the letter for unknown banks.
- **Licence caveat:** bank logos are trademarks — fine for a sideloaded "for friends"
  build, risky for Play Store distribution. Keep a letter-only fallback skin.
- User will supply brand references; map each to a `CardSkin`.

### 3. Brokerage / investment accounts (БКС, Альфа-Инвестиции)
- New `AccountKind.INVESTMENT`. Stores ONE number: total portfolio valuation in ₽
  (`balanceKopecks`), updated from a broker push ("Стоимость портфеля: …") or by the
  user manually (weekly is fine for an offline app). No per-security holdings in v1.
- **Excluded from the "Доступно" (cash) net worth.** Dashboard gets a separate
  **"Инвестиции"** section with its own subtotal; net worth shown as two lines —
  "Доступно" (cash) and "Капитал" (cash + investments).
- Topping up a broker account is a **TRANSFER**, not an expense — already handled by
  the existing `TransferRouter` / `TransactionType.TRANSFER`, so it won't pollute
  spend analytics. (No live quotes — app is offline by design.)
- Future (optional): `HoldingEntity(ticker, qty, avgPrice)` for per-security detail —
  a separate large layer, deferred.

### 4. Credit cards (visible in cards, excluded from balance)
- New `AccountKind.CREDIT`. **Excluded from "Доступно" net worth.** Optionally shown as
  a separate "Долг: −N ₽" line (red / `FosColors.Negative`). Card stays visible in the
  card list with a "Кредитная" badge. Conceptually a negative `balanceKopecks` (amount
  owed) plus a credit limit; available-on-card = `limit − debt`.
- **Operation semantics differ — this is the subtle part:**
  | Operation | Meaning | Treatment |
  |---|---|---|
  | Покупка по кредитке | debt ↑, but it IS a spend | counts in spend analytics; NOT in cash balance |
  | Погашение (свой кэш → кредитка) | debt ↓, cash ↓ | **TRANSFER, not an expense** |
  | Пополнение / возврат | debt ↓ | reduces debt |
- **Critical pitfall:** a credit-card repayment must NOT be booked as an expense, or a
  single 1 000 ₽ purchase becomes 2 000 ₽ of "spend" (the purchase + the repayment).
  Catch repayment as a transfer "cash account → credit account" and route it through
  the existing `TransferRouter` (same mechanism as goal funding).

### Suggested implementation order
1. `AccountKind` column + migration + net-worth split by kind (foundation)
2. Credit cards (excluded from balance, transfer-routed repayments)
3. Investment accounts (separate subtotal, transfer-routed top-ups)
4. Bank registry refactor + branded card UI (independent UI track; do once references arrive)
