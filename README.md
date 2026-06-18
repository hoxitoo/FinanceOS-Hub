# FinanceOS Hub

Offline-first Android finance app that reads bank SMS messages and automatically tracks your spending — zero manual input.

## Features
- **Auto-import** — reads SMS from 5+ major Russian banks on first launch (last 90 days)
- **Real-time** — new transactions appear instantly via SMS BroadcastReceiver
- **Smart categorization** — dictionary classifier + merchant rules (expandable to ML)
- **5 screens** — Dashboard, Transactions, Analytics, Budget, Goals
- **100% offline** — no internet permission in base version, all data stays on device
- **Financial score** — 0–100 score based on savings rate, stability, cushion

## Supported Banks (Phase 1)
| Bank | Sender |
|------|--------|
| Сбербанк | SBERBANK, 900 |
| Т-Банк | TINKOFF, TBANK, 2200 |
| ВТБ | VTB |
| Альфа-Банк | ALFABANK, ALFA |
| Газпромбанк | GAZPROMBANK, GPB |

## Tech Stack
- **Kotlin** + **Jetpack Compose** (Material 3, custom dark theme)
- **Hilt** — dependency injection
- **Room** — local SQLite database
- **DataStore** — preferences
- **WorkManager** — background analytics
- **Clean Architecture + MVVM**

## Project Structure
```
app/
├── core/           # Business logic: database, parser, sms, classifier, analytics
├── data/           # Repositories, preferences
├── di/             # Hilt modules
├── features/       # UI: dashboard, transactions, analytics, budget, goals, onboarding
├── navigation/     # NavHost, routes, bottom nav
└── ui/             # Theme tokens, reusable components
```

## Build
```bash
./gradlew assembleDebug
./gradlew test --tests "*.parser.*"
```

## Design
Based on interactive prototype `FinanceOS.dc.html`. Dark theme, custom color system:
- `#4DFFA0` — income, positive metrics (green)
- `#FF6B6B` — expenses, alerts (red)
- `#FFB84D` — warnings, 70-90% budget usage (amber)
- `#4D9FFF` — info, links (blue)

## Branch Strategy
- `main` — stable releases
- `dev` — active development
- Feature branches → PR to `dev` → PR to `main`

## Roadmap
See `docs/CONTEXT.md` for full technical spec. Implementation follows phases:
1. Foundation (design system + DB + parsers)
2. Dashboard + Transactions
3. Analytics + Budget + Goals
4. Polish + tests + Play Store
