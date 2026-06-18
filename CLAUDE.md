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
│   └── analytics/  (AnalyticsEngine, ScoreCalculator, InsightGenerator)
├── data/
│   ├── repositories/
│   └── preferences/ (UserPreferences via DataStore)
├── di/             (Hilt modules)
├── features/       (dashboard, transactions, analytics, budget, goals, onboarding)
├── navigation/     (FosNavigation, FosRoutes)
└── ui/
    ├── theme/      (FosColors, FosType, FosDimens, FosTheme, FosFormatter)
    └── components/ (FosCard, TransactionRow, Sparkline, ScoreRing, etc.)
```

## Critical Design Rules (NEVER violate)
1. `FosColors.Positive` (#4DFFA0) = income, success, savings ONLY
2. `FosColors.Negative` (#FF6B6B) = expenses, errors, overrun ONLY — **expense amounts in TransactionRow MUST use Negative**
3. All monetary/numeric `Text` → `fontFeatureSettings = "tnum"` (tabular-nums)
4. `InsightCard` — colored left border ONLY, no icon inside
5. Net Worth negative → Negative color

## Amounts Storage
- Store as `Long` kopecks (×100), convert to Double only in `FosFormatter`
- Negative kopecks = expense, positive = income

## SMS Deduplication
`smsId = "${sender}_${timestamp}_${body.hashCode()}"` — checked before insert

## Supported Banks (P1)
Сбербанк, Т-Банк, ВТБ, Альфа-Банк, Газпромбанк

## Current Phase Status
- [x] Plan approved, branches created
- [x] Bootstrap commit (main)
- [x] Gradle project skeleton
- [x] Design system (FosColors, FosType, FosDimens, FosTheme, FosFormatter)
- [x] Database layer (entities, DAOs, FosDatabase, DI)
- [x] Parser layer (5 P1 banks, ParserEngine, SmsReceiver)
- [x] DictionaryClassifier
- [x] Navigation skeleton
- [x] UserPreferences (DataStore)
- [x] Onboarding screens
- [x] Dashboard screen (3 hero variants, metrics, accounts, recent tx, forecast)
- [x] Transactions screen (red expenses ✓, grouped list, filter chips)
- [x] Analytics screen (SVG line chart ✓, score ring, heatmap, insights)
- [x] Budget screen (envelopes, dynamic bar color)
- [x] Goals screen (SVG goal ring)
- [ ] Unit tests for parsers
- [ ] Analytics engine (ScoreCalculator, InsightGenerator)
- [ ] WorkManager (AnalyticsWorker)

## Key File Locations
| Layer | Path |
|---|---|
| Theme | `app/src/main/kotlin/com/financeos/hub/ui/theme/` |
| Components | `app/src/main/kotlin/com/financeos/hub/ui/components/` |
| Database | `app/src/main/kotlin/com/financeos/hub/core/database/` |
| Parsers | `app/src/main/kotlin/com/financeos/hub/core/parser/` |
| Features | `app/src/main/kotlin/com/financeos/hub/features/` |
| Navigation | `app/src/main/kotlin/com/financeos/hub/navigation/` |
| DI Modules | `app/src/main/kotlin/com/financeos/hub/di/` |

## Design Reference
- Interactive prototype: `FinanceOS.dc.html` (uploaded)
- Full roadmap: `FINANCE___ROADMAP.md` (uploaded)
- Color tokens: see `FosColors.kt`
- Typography: see `FosType.kt`
