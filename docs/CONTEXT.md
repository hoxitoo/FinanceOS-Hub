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
amount: Long          ← kopecks! negative=expense, positive=income
type: TransactionType (INCOME/EXPENSE/TRANSFER)
source: TransactionSource (SMS/PUSH/MANUAL)
merchant: String?
rawSmsBody: String?   ← preserved for re-parsing
transactionDate: Long (epoch ms)
smsId: String?        ← "${sender}_${timestamp}_${body.hashCode()}"
isDeleted: Boolean    ← soft delete
deletedAt: Long?
```

### AccountEntity
```
id, bankId, bankName, lastFour, displayName
balanceKopecks: Long
currency: String
isActive: Boolean
updatedAt: Long
```

### CategoryEntity (system + user)
```
id, name, icon (Material Symbols), colorHex
type: TransactionType
parentId: String?     ← sub-categories
isSystem: Boolean     ← system categories can't be deleted
sortOrder: Int
```

### BudgetEntity
```
id, categoryId, limitKopecks: Long
period: BudgetPeriod (MONTHLY/WEEKLY)
month: Int, year: Int
rollover: Boolean     ← accumulative envelope
```

### GoalEntity
```
id, name, icon, colorHex
targetKopecks, savedKopecks: Long
monthlyRate: Long     ← how much to save per month
deadline: Long?
linkedAccountId: String?
isCompleted: Boolean
```

## Default Categories (13)
```
EXPENSE: groceries(#4DFFA0), transport(#4D9FFF), cafe(#FFB84D), shopping(#C18CFF),
         health(#FF6B6B), entertainment(#FF8FB1), utilities(#5AC8FF),
         subscriptions(#9AA7FF), family(#FF8C42), other(#7A8499)
INCOME:  salary(#4DFFA0), transfer_in(#4D9FFF), other_income(#4DFFA0)
```

## SMS Parsers (P1 Banks)

### Pattern matching
Each `BankParser` declares `senderPatterns: List<String>` matched against SMS sender.
`ParserEngine` uses Hilt `@IntoSet` multibinding — add new bank = new class + 1 `@Binds` line.

### Sberbank examples (Appendix B)
```
SBERBANK: "Покупка. Карта *8830. 1500р. Баланс: 22329.61р. Пятёрочка"
900:      "Зачисление 62000р. Карта *8830. Баланс: 84329р."
SBERBANK: "Перевод 6000р. Карта *8830. Баланс: 16329р. Иванов И.И."
900:      "Оплата 299р. Карта *8830. Баланс: 16030р."
```

## Financial Score Formula (Appendix C)
```kotlin
savings_score   = (savingsRate.coerceIn(0,40) / 40.0 * 30).toInt()   // max 30
stability_score = ((1 - expenseVariability.coerceIn(0,1)) * 20).toInt() // max 20
mandatory_score = ((1 - mandatoryRatio.coerceIn(0,1)) * 25).toInt()   // max 25
cushion_score   = (cushionMonths.coerceIn(0,6) / 6.0 * 25).toInt()   // max 25
// Total max = 100

// Thresholds:
// 75-100 → Positive color, "Хорошее здоровье"
// 50-74  → Warning color,  "Есть над чем работать"
// 0-49   → Negative color, "Требует внимания"
```

## Navigation Routes
```
onboarding/welcome
onboarding/permissions
onboarding/setup
dashboard
transactions
analytics
budget
goals
transaction/{id}   ← detail sheet
settings
```

## DataStore Keys (UserPreferences)
```
hero_variant: Int       (0/1/2 — dashboard hero style)
biometric: Boolean
onboarding_done: Boolean
sms_import_done: Boolean
currency: String        ("RUB")
analytics_tab: String   (last open tab)
trends_mode: String     ("bar"/"line")
```

## Screen: Transactions (Critical Fix)
expense amount color = `FosColors.Negative` (#FF6B6B), NOT TextPrimary
```kotlin
val amtColor = if (transaction.amount > 0) FosColors.Positive else FosColors.Negative
```

## Screen: Analytics — Trends Tab (Critical Fix)
- SVG line chart (two lines: income/expense) as PRIMARY display
- Bar chart as secondary (toggle via icon in header)
- Implementation: Compose `Canvas` DrawScope — zero external dependencies
```kotlin
enum class TrendsChartMode { LINE, BAR }
```
