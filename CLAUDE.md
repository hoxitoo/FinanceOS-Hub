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
- **DB schema:** Room v12
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
`assembleDebug` + `lintDebug` on any PR targeting `dev` or `main`. Open a **draft PR to `dev`** to
get a compile check without touching the release pipeline (which only fires on push to `main`).

There are no instrumented/UI tests — gestures, rendering and screen behaviour are verified by
review and reasoning only. State that honestly when reporting.

## Architecture
Clean Architecture + MVVM + Hilt + Room + Compose + Coroutines/Flow

```
app/
├── core/
│   ├── database/     (entities, daos, converters, FosDatabase, migrations)
│   ├── parser/       (BankParser, ParserEngine, banks/, TransferPatterns, PromoFilter, AmountParser)
│   ├── classifier/   (DictionaryClassifier, CategoryDefaults)
│   ├── sms/          (SmsReader, SmsReceiver, PushNotificationListener)
│   ├── account/      (AccountLinker)
│   ├── credit/       (CreditMath — debt, free limit, cycle, min payment, due payment;
│   │                  CreditNoticeApplier)
│   ├── transfer/     (TransferRouter)
│   ├── analytics/    (AnalyticsEngine, ScoreCalculator, InsightGenerator,
│   │                  BehavioralAnalyzer, NarrativeEngine, AnalyticsWorker)
│   ├── ml/           (ModelLoader, TextFeatureExtractor, MLCategoryClassifier,
│   │                  SpendingPredictor, BehavioralCluster)
│   ├── pdf/          (PdfImporter, PdfTransactionParser)
│   ├── backup/       (BackupManager, BackupCrypto)
│   ├── update/       (UpdateChecker, UpdateCheckWorker)
│   └── notifications/(NotificationHelper — 4 channels)
├── data/
│   ├── repositories/ (Tx, Account, Card, Category, Budget, Goal, TransferRoute)
│   └── preferences/  (UserPreferences via DataStore)
├── di/               (DatabaseModule, ParserModule, RepositoryModule, MLModule, AnalyticsModule)
├── features/         (dashboard, transactions, analytics, budget, goals,
│                      subscriptions, categories, credit, onboarding, settings)
├── navigation/       (FosNavHost, FosRoutes)
├── widget/           (BalanceWidget)
└── ui/
    ├── theme/        (FosColors, FosType, FosDimens, FosTheme, FosFormatter,
    │                  AmountVisualTransformation, Shimmer)
    └── components/   (see README project structure)
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

## Amounts Storage
- Store as `Long` kopecks (×100), convert to Double only in `FosFormatter`
- Negative kopecks = expense, positive = income, TRANSFER signed by direction
- `currency` is per-transaction (not just per-account) — RUB/USD/EUR/KGS

## SMS Deduplication
`smsId = "${sender}_${timestamp}_${body.hashCode()}"` — checked before insert, then
`existsSimilarSmsOrPush(|amount|, ±5 min)` catches the SMS↔push twin of the same event.

## Supported Banks (12)
- **P1:** Сбербанк, Т-Банк, ВТБ, Альфа-Банк, Газпромбанк
- **P2:** Райффайзен, Росбанк, Открытие
- **P3:** МТС Банк, Почта Банк, Россельхозбанк
- **KG:** МБанк (multi-currency USD/KGS/EUR/RUB)

All 12 have unit tests (~7 cases each).

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

---

# Feature Status

Everything below is **implemented and shipped** unless marked otherwise.

## Core
- [x] Gradle skeleton, AndroidManifest, design system, database, navigation, onboarding
- [x] 12 bank parsers + `ParserEngine` (@IntoSet DI) + `TransferPatterns` + `PromoFilter`
- [x] `SmsReceiver` (real-time, `goAsync`), `SmsReader` (90-day import), `PushNotificationListener`
      (reads **every** notification text extra)
- [x] SMS is **opt-in** (`sms_realtime_enabled`, default false)
- [x] `DictionaryClassifier` (143 rules), `CategoryDefaults.forType` income fallback
- [x] `AccountLinker` (card→account, authoritative balance, orphan re-link, recency guard)
- [x] `AccountKind` (CASH / CREDIT / INVESTMENT) + credit terms on `AccountEntity`; net worth,
      widget and score cushion are CASH-only
- [x] `CreditNoticeParser` — «Платёж по кредитной карте / Внесите платёж X до ДД.ММ.ГГ» разбирается
      как **факт о карте, не операция**: ничего не вставляется, пишутся сумма и дата платежа.
      Идёт **до `PromoFilter`** (тот режет пуш на слове «беспроцентным»). Карта определяется по
      банку и только когда ответ однозначен. В 90-дневном импорте не применяется — старое
      напоминание затёрло бы текущее.
- [x] `TransferRouter` (goal routing by account/card/keyword, counterparty leg, internal pairing)
- [x] All 7 repositories, `UserPreferences` (DataStore, ~20 keys)

## Screens
- [x] Dashboard (3 hero variants, month label, bank cards, clickable recent ops, account CRUD)
- [x] Transactions (search, filters, swipe-left-to-reveal delete, detail/edit sheet with source
      diagnostics, CSV export, PDF import, manual add incl. **Перевод** with destination account)
- [x] Analytics (period chips + 4 tabs — see README for the per-tab breakdown)
- [x] Budget (envelopes, CRUD, throttled alerts), Goals (art backdrops, history, 🔗 routing)
- [x] Subscriptions, Categories CRUD, Settings, Onboarding
- [x] Кредитные карты — плитка на главной (под hero, один вставочный пункт → все 3 варианта героя)
      + экран `features/credit` (сводка, блок на карту с датой/суммой платежа, полоса беспроцентного периода,
      ставка, утилизация, история операций, лист редактирования условий)

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
- [x] Backup/restore — 8 tables → `.fose`, AES-GCM-256 via Android Keystore, additive + FK-safe
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
| **Distribution** | Release pipeline, in-app updater, background update notifications, encrypted backups |
| **Improvement cycle (batches 1–5)** | Score donut, biometric lockout fix, goal transfers + history + pixel art, money-input rewrite, bank→account picker, budget-alert throttling, «Букмекер» + marketplace/bookmaker rules, Trends tab rebuilt for readability, Categories 3D pie + drill-down, analytics period chips |

**Audits 1–11** produced ~90 fixes. The ones worth remembering are distilled into
*Hard-won invariants* above; the rest are visible in `git log`.

### Known limitations
- Counterparty card mask is only available when the bank spells «на карту/счёт *NNNN». Several
  push formats omit it — those rely on keyword or account routing.
- Rows ingested before a schema addition don't backfill (e.g. a transaction stored before the
  `currency` column stays RUB).
- An internal transfer whose two legs arrive more than 10 min apart can't be paired.
- Sberbank `parsePush()` anchors on «В запасе:» — other balance labels need coverage.

---

# Next Steps
- Polish: localization review, dark-mode visual QA
- Consider: cross-channel dedup window tuning (currently ±5 min, conservative)
- Consider: encrypt the backup with a user PIN (the key is device-scoped, no extra auth today)
- Consider: signed **release** APK channel (keystore in GitHub Secrets)
- Await more Sberbank push format variants

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
4. **Проценты** — сделано. `accruedInterest` (простое начисление за дни просрочки) и
   `minimumPaymentOutlook` (помесячная симуляция «плачу только минимум»). Обе цифры на экране
   ЯВНО помечены оценкой. Точна ровно одна: ноль внутри беспроцентного периода.
   Минимальный платёж моделируется как процент от долга **но не меньше 300 ₽** — без порога
   симуляция не сходится: платёж уменьшается вместе с остатком и никогда не достигает нуля.

### 14. Кросс-канальный дедуп сравнивает ЗНАКОВУЮ сумму, не модуль
Две доставки одного события всегда одного знака; две ноги перевода между своими счетами — всегда
разных. Погашение кредитки — ровно это: −50 000 с дебетовой и +50 000 на кредитку с разницей в
секунды. Сравнение по модулю молча съедало вторую ногу, и погашение не появлялось в истории карты.

### Реальные форматы пушей Сбера (проверено на устройстве)
| Что | Текст | Как обрабатывается |
|---|---|---|
| Покупка по кредитке | `Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽ Счёт карты МИР •• 6703` | `parsePush` → EXPENSE; «Баланс» = свободный лимит |
| Напоминание о платеже | `Платёж по кредитной карте / Внесите платёж 373,98р до 31.08.26 …беспроцентным периодом.` | `CreditNoticeParser` → не операция, пишет сумму и дату |
| Погашение / зачисление | — | **формат неизвестен**, ждём

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

# Design Reference
- Technical spec, schema, formulas, screen contracts: `docs/CONTEXT.md`
- User-facing overview: `README.md`
- Goal art generation prompts: `docs/GOAL_ART_PROMPTS.md`
- Colour tokens: `FosColors.kt` · Typography: `FosType.kt`
