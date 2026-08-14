package com.financeos.hub.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.financeos.hub.core.database.converters.FosTypeConverters
import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.BudgetDao
import com.financeos.hub.core.database.daos.CardDao
import com.financeos.hub.core.database.daos.CategoryDao
import com.financeos.hub.core.database.daos.GoalDao
import com.financeos.hub.core.database.daos.MerchantRuleDao
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.daos.TransferRouteDao
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.BudgetEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.core.database.entities.MerchantRuleEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransferRouteEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        MerchantRuleEntity::class,
        CardEntity::class,
        TransferRouteEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
@TypeConverters(FosTypeConverters::class)
abstract class FosDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun cardDao(): CardDao
    abstract fun transferRouteDao(): TransferRouteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cards` (
                        `id` TEXT NOT NULL,
                        `account_id` TEXT NOT NULL,
                        `card_mask` TEXT NOT NULL,
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_account_id` ON `cards`(`account_id`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN goal_id TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN transfer_pair_id TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transfer_routes` (
                        `id` TEXT NOT NULL,
                        `goal_id` TEXT NOT NULL,
                        `match_type` TEXT NOT NULL,
                        `match_value` TEXT NOT NULL,
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_routes_goal_id` ON `transfer_routes`(`goal_id`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_goal_id` ON `transactions`(`goal_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transfer_pair_id` ON `transactions`(`transfer_pair_id`)")
            }
        }

        // Adds income categories (Зарплата / Прочие доходы / Кэшбэк) and the public-transport +
        // income merchant rules to EXISTING installs. Both insert helpers use INSERT OR IGNORE,
        // so re-running them only adds the new ids and leaves user data untouched.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                insertDefaultCategories(db)
                insertDefaultMerchantRules(db)
            }
        }

        // Persists the source / destination account masks parsed from bank SMS & push, so the
        // transaction detail sheet can show "Счёт списания" / "Счёт зачисления". Pre-existing
        // rows keep NULL (rendered as "неизвестно") — no backfill is possible, the raw body
        // was never stored.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN source_mask TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN counterparty_mask TEXT")
            }
        }

        // Persists the bank-reported post-operation balance ("Остаток"/"Доступно") on each
        // ingested SMS/PUSH transaction. Lets a later account/card link reconcile the account
        // to the authoritative balance instead of dropping it. Pre-existing rows keep NULL.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN balance_kopecks INTEGER")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'RUB'")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN raw_text TEXT")
            }
        }

        // Adds the «Букмекер» expense category and the new marketplace / bookmaker merchant rules
        // to EXISTING installs. Both helpers are INSERT OR IGNORE, so re-running them only adds the
        // new ids and never touches a user's own categories, renames or rules.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                insertDefaultCategories(db)
                insertDefaultMerchantRules(db)
            }
        }

        /**
         * Account kinds + credit-card terms.
         *
         * `kind` is NOT NULL DEFAULT 'CASH' so every pre-existing account keeps behaving exactly as
         * before: it counts toward net worth and toward the cushion pillar of the health score. The
         * credit term columns are all nullable — nothing knows a card's limit or rate until the user
         * types them in, and inventing a default would produce a confident wrong interest-free period.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN kind TEXT NOT NULL DEFAULT 'CASH'")
                db.execSQL("ALTER TABLE accounts ADD COLUMN credit_limit_kopecks INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN apr_bp INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN statement_day INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN due_days INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN min_payment_bp INTEGER")
            }
        }

        /**
         * The bank's own credit-card payment demand, straight from a reminder push, plus a
         * merchant rule for DNS (whose purchase push is what exposed the parser gap).
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN due_payment_kopecks INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN due_payment_at INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN due_payment_seen_at INTEGER")
                // Rules are INSERT OR IGNORE, so re-running the seed only adds what is new.
                insertDefaultCategories(db)
                insertDefaultMerchantRules(db)
            }
        }

        /**
         * The rest of a real credit-card tariff, as printed in the bank's own «Тариф» screen.
         *
         * The first pass modelled a card as "statement day + days to pay", which fits a classic
         * grace card but not Сбер's 120-day СберКарта: there the obligatory payment is monthly
         * while the interest-free period runs from each purchase. These columns let the card be
         * described as the bank describes it, instead of forcing it into the wrong shape.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN min_payment_floor_kopecks INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN interest_free_days INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN penalty_apr_bp INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN cash_fee_bp INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN cash_fee_fixed_kopecks INTEGER")
            }
        }

        /**
         * Категория «Подписки» + перевод уже существующих правил стриминга на неё.
         *
         * Одного `insertDefaultMerchantRules` здесь мало, и это важная тонкость. Правила
         * вставляются через INSERT OR IGNORE, а `DictionaryClassifier` берёт ПЕРВОЕ совпадение
         * при сортировке `priority DESC` (у всех сеяных правил priority = 0, дальше порядок по
         * rowid). Значит на старой установке строка r091 «netflix → Развлечения» никуда не
         * денется, а добавленный дубликат с новым id встанет ПОЗЖЕ и никогда не сработает: новая
         * категория осталась бы навсегда пустой, и выглядело бы это как сломанная функция.
         *
         * Поэтому шесть строк переписываются явным UPDATE. Условие `category_id = 'cat_entertain'`
         * — страховка: если пользовательская правка правил когда-нибудь появится, чужой выбор не
         * будет молча перезаписан.
         *
         * Уже проведённые операции НЕ переразмечаются: категория хранится в самой строке
         * транзакции, а правила влияют только на разбор будущих сообщений (инвариант «категоризация
         * не учится»). История остаётся ровно такой, какой пользователь её видел.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                insertDefaultCategories(db)
                insertDefaultMerchantRules(db)
                db.execSQL(
                    """
                    UPDATE merchant_rules SET category_id = 'cat_subscription'
                    WHERE id IN ('r091','r092','r093','r094','r095','r096')
                      AND category_id = 'cat_entertain'
                    """.trimIndent()
                )
            }
        }

        val PREPOPULATE_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                insertDefaultCategories(db)
                insertDefaultMerchantRules(db)
            }
        }

        private fun insertDefaultCategories(db: SupportSQLiteDatabase) {
            val cats = listOf(
                Triple("cat_food",       "Еда и рестораны",  "🍔"),
                Triple("cat_grocery",    "Продукты",         "🛒"),
                Triple("cat_transport",  "Транспорт",        "🚇"),
                Triple("cat_housing",    "Жильё и ЖКХ",      "🏠"),
                Triple("cat_health",     "Здоровье",         "💊"),
                Triple("cat_shopping",   "Покупки",          "🛍️"),
                Triple("cat_telecom",    "Связь",            "📱"),
                Triple("cat_entertain",  "Развлечения",      "🎬"),
                Triple("cat_education",  "Образование",      "📚"),
                Triple("cat_travel",     "Путешествия",      "✈️"),
                Triple("cat_beauty",     "Красота",          "💅"),
                Triple("cat_pets",       "Животные",         "🐾"),
                Triple("cat_other",      "Другое",           "💳"),
                // Income categories — used for auto-categorised INCOME transactions
                Triple("cat_salary",     "Зарплата",         "💼"),
                Triple("cat_income",     "Прочие доходы",    "💰"),
                Triple("cat_cashback",   "Кэшбэк",           "💸"),
                // Appended LAST on purpose: sort_order is the list index, and existing installs
                // keep their stored order (INSERT OR IGNORE), so adding in the middle would only
                // reshuffle new installs for no benefit.
                Triple("cat_betting",    "Букмекер",         "🎰"),
                Triple("cat_subscription", "Подписки",       "🔄"),
            )
            val colors = listOf(
                "#FFB84D", "#4DFFA0", "#4D9FFF", "#FF6B6B", "#C084FC",
                "#F472B6", "#34D399", "#A78BFA", "#60A5FA", "#FB923C",
                "#E879F9", "#2DD4BF", "#94A3B8",
                "#4DFFA0", "#22D3A6", "#38BDF8",
                "#F87171",   // cat_betting
                "#818CF8",   // cat_subscription
            )
            cats.forEachIndexed { i, (id, name, emoji) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO categories(id, name, emoji, color, is_system, is_active, sort_order) VALUES(?, ?, ?, ?, 1, 1, ?)",
                    arrayOf(id, name, emoji, colors[i], i),
                )
            }
        }

        private fun insertDefaultMerchantRules(db: SupportSQLiteDatabase) {
            val rules = listOf(
                // Food & restaurants
                Triple("r001", "макдональдс",   "cat_food"),
                Triple("r002", "mcdonald",      "cat_food"),
                Triple("r003", "kfc",           "cat_food"),
                Triple("r004", "бургер кинг",   "cat_food"),
                Triple("r005", "domino",        "cat_food"),
                Triple("r006", "пицца",         "cat_food"),
                Triple("r007", "суши",          "cat_food"),
                Triple("r008", "кофе",          "cat_food"),
                Triple("r009", "starbucks",     "cat_food"),
                Triple("r010", "ресторан",      "cat_food"),
                Triple("r011", "кафе",          "cat_food"),
                // Grocery
                Triple("r020", "пятёрочка",     "cat_grocery"),
                Triple("r021", "пятерочка",     "cat_grocery"),
                Triple("r022", "магнит",        "cat_grocery"),
                Triple("r023", "перекрёсток",   "cat_grocery"),
                Triple("r024", "перекресток",   "cat_grocery"),
                Triple("r025", "лента",         "cat_grocery"),
                Triple("r026", "ашан",          "cat_grocery"),
                Triple("r027", "вкусвилл",      "cat_grocery"),
                Triple("r028", "дикси",         "cat_grocery"),
                Triple("r029", "metro",         "cat_grocery"),
                Triple("r030", "spar",          "cat_grocery"),
                // Transport
                Triple("r040", "яндекс.такси",  "cat_transport"),
                Triple("r041", "uber",          "cat_transport"),
                Triple("r042", "ситимобил",     "cat_transport"),
                Triple("r043", "метро",         "cat_transport"),
                Triple("r044", "аэрофлот",      "cat_transport"),
                Triple("r045", "rzd",           "cat_transport"),
                Triple("r046", "ржд",           "cat_transport"),
                Triple("r047", "автобус",       "cat_transport"),
                Triple("r048", "самокат",       "cat_transport"),
                // Housing
                Triple("r050", "жкх",           "cat_housing"),
                Triple("r051", "квартплата",    "cat_housing"),
                Triple("r052", "электроэнерг",  "cat_housing"),
                Triple("r053", "газ",           "cat_housing"),
                Triple("r054", "домофон",       "cat_housing"),
                Triple("r055", "мосэнерго",     "cat_housing"),
                // Health
                Triple("r060", "аптека",        "cat_health"),
                Triple("r061", "pharmacy",      "cat_health"),
                Triple("r062", "клиника",       "cat_health"),
                Triple("r063", "поликлиник",    "cat_health"),
                Triple("r064", "стоматолог",    "cat_health"),
                Triple("r065", "медцентр",      "cat_health"),
                // Shopping
                Triple("r070", "wildberries",   "cat_shopping"),
                Triple("r071", "ozon",          "cat_shopping"),
                Triple("r072", "wildber",       "cat_shopping"),
                Triple("r073", "avito",         "cat_shopping"),
                Triple("r074", "lamoda",        "cat_shopping"),
                Triple("r075", "zara",          "cat_shopping"),
                Triple("r076", "h&m",           "cat_shopping"),
                Triple("r077", "ikea",          "cat_shopping"),
                // Telecom
                Triple("r080", "мтс",           "cat_telecom"),
                Triple("r081", "билайн",        "cat_telecom"),
                Triple("r082", "мегафон",       "cat_telecom"),
                Triple("r083", "теле2",         "cat_telecom"),
                Triple("r084", "ростелеком",    "cat_telecom"),
                // Entertainment. A cinema ticket and a game bought on Steam are one-off purchases —
                // they stay here. Anything that renews itself every month lives in cat_subscription.
                Triple("r090", "кинотеатр",     "cat_entertain"),
                Triple("r097", "steam",         "cat_entertain"),
                Triple("r098", "playstation",   "cat_entertain"),
                // Subscriptions. r091..r096 keep their original ids on purpose: existing installs
                // already hold those rows, and INSERT OR IGNORE would skip a renumbered copy. The
                // rows themselves are re-pointed by MIGRATION_13_14 — see there.
                Triple("r091", "netflix",       "cat_subscription"),
                Triple("r092", "spotify",       "cat_subscription"),
                Triple("r093", "okko",          "cat_subscription"),
                Triple("r094", "more.tv",       "cat_subscription"),
                Triple("r095", "иви",           "cat_subscription"),
                Triple("r096", "яндекс.музыка", "cat_subscription"),
                // Beauty
                Triple("r100", "л'этуаль",      "cat_beauty"),
                Triple("r101", "летуаль",       "cat_beauty"),
                Triple("r102", "рив гош",       "cat_beauty"),
                Triple("r103", "салон красоты", "cat_beauty"),
                // Public transport — city transit cards & operators (e.g. "Транспорт Перми")
                Triple("r110", "транспорт",     "cat_transport"),
                Triple("r111", "тройка",        "cat_transport"),
                Triple("r112", "подорожник",    "cat_transport"),
                Triple("r113", "проездной",     "cat_transport"),
                Triple("r114", "метрополитен",  "cat_transport"),
                Triple("r115", "трамвай",       "cat_transport"),
                Triple("r116", "троллейбус",    "cat_transport"),
                Triple("r117", "маршрутка",     "cat_transport"),
                Triple("r118", "электричка",    "cat_transport"),
                Triple("r119", "мосгортранс",   "cat_transport"),
                Triple("r120", "такси",         "cat_transport"),
                Triple("r121", "gett",          "cat_transport"),
                Triple("r122", "ситидрайв",     "cat_transport"),
                Triple("r123", "делимобиль",    "cat_transport"),
                Triple("r124", "каршеринг",     "cat_transport"),
                // Income — salary, cashback, generic incoming credits
                Triple("r130", "зарплата",          "cat_salary"),
                Triple("r131", "заработная плата",  "cat_salary"),
                Triple("r132", "аванс",             "cat_salary"),
                Triple("r133", "оклад",             "cat_salary"),
                Triple("r134", "кэшбэк",            "cat_cashback"),
                Triple("r135", "кешбэк",            "cat_cashback"),
                Triple("r136", "cashback",          "cat_cashback"),
                Triple("r137", "поступление",       "cat_income"),
                Triple("r138", "зачисление",        "cat_income"),
                Triple("r139", "пополнение",        "cat_income"),
                Triple("r140", "проценты на остаток","cat_income"),

                // ── Маркетплейсы (топ РФ). Банки шлют мерчанта и латиницей, и кириллицей,
                // поэтому обе формы. Латинские "ozon"/"wildberries" уже есть выше (r070–r071).
                Triple("r150", "озон",              "cat_shopping"),
                Triple("r151", "ozon.ru",           "cat_shopping"),
                Triple("r152", "вайлдберриз",       "cat_shopping"),
                Triple("r153", "вайлдберрис",       "cat_shopping"),
                Triple("r154", "wb.ru",             "cat_shopping"),
                Triple("r155", "яндекс маркет",     "cat_shopping"),
                Triple("r156", "yandex market",     "cat_shopping"),
                Triple("r157", "ya.market",         "cat_shopping"),
                Triple("r158", "мегамаркет",        "cat_shopping"),
                Triple("r159", "megamarket",        "cat_shopping"),
                Triple("r160", "сбермегамаркет",    "cat_shopping"),
                Triple("r161", "aliexpress",        "cat_shopping"),
                Triple("r162", "алиэкспресс",       "cat_shopping"),
                Triple("r163", "авито",             "cat_shopping"),
                Triple("r164", "ламода",            "cat_shopping"),
                Triple("r165", "детский мир",       "cat_shopping"),
                Triple("r166", "detmir",            "cat_shopping"),
                Triple("r167", "мвидео",            "cat_shopping"),
                Triple("r168", "м.видео",           "cat_shopping"),
                Triple("r169", "mvideo",            "cat_shopping"),
                Triple("r170", "эльдорадо",         "cat_shopping"),
                Triple("r171", "eldorado",          "cat_shopping"),
                Triple("r172", "днс",               "cat_shopping"),
                Triple("r173", "dns-shop",          "cat_shopping"),
                Triple("r174", "ситилинк",          "cat_shopping"),
                Triple("r175", "citilink",          "cat_shopping"),
                // Сбер печатает мерчанта коротко — "Покупка DNS". Правило r173 ("dns-shop")
                // такую форму не ловит.
                Triple("r176", "dns",               "cat_shopping"),

                // ── Букмекеры / ставки → cat_betting
                Triple("r180", "фонбет",            "cat_betting"),
                Triple("r181", "fonbet",            "cat_betting"),
                Triple("r182", "winline",           "cat_betting"),
                Triple("r183", "винлайн",           "cat_betting"),
                Triple("r184", "1xbet",             "cat_betting"),
                Triple("r185", "1хставка",          "cat_betting"),
                Triple("r186", "лига ставок",       "cat_betting"),
                Triple("r187", "ligastavok",        "cat_betting"),
                Triple("r188", "betboom",           "cat_betting"),
                Triple("r189", "бетбум",            "cat_betting"),
                Triple("r190", "betcity",           "cat_betting"),
                Triple("r191", "бетсити",           "cat_betting"),
                Triple("r192", "марафон",           "cat_betting"),
                Triple("r193", "marathonbet",       "cat_betting"),
                Triple("r194", "олимпбет",          "cat_betting"),
                Triple("r195", "olimpbet",          "cat_betting"),
                Triple("r196", "париматч",          "cat_betting"),
                Triple("r197", "parimatch",         "cat_betting"),
                Triple("r198", "тенниси",           "cat_betting"),
                Triple("r199", "pari.ru",           "cat_betting"),
                // Subscriptions — services that charge every month on their own.
                // Deliberately NOT here: bare "яндекс", "apple", "google", "telegram". Each of them
                // is a substring of dozens of unrelated merchant names (Яндекс.Такси, Яндекс.Еда,
                // Google Ads…), and matching is a plain `contains`, so one loose word would swallow
                // whole other categories. Only the billing descriptors that actually identify a
                // subscription are listed.
                Triple("r200", "яндекс плюс",       "cat_subscription"),
                Triple("r201", "яндекс.плюс",       "cat_subscription"),
                Triple("r202", "yandex plus",       "cat_subscription"),
                Triple("r203", "кинопоиск",         "cat_subscription"),
                Triple("r204", "kinopoisk",         "cat_subscription"),
                Triple("r205", "сбер прайм",        "cat_subscription"),
                Triple("r206", "сберпрайм",         "cat_subscription"),
                Triple("r207", "sberprime",         "cat_subscription"),
                Triple("r208", "ozon premium",      "cat_subscription"),
                Triple("r209", "озон премиум",      "cat_subscription"),
                Triple("r210", "wink",              "cat_subscription"),
                Triple("r211", "megogo",            "cat_subscription"),
                Triple("r212", "мегого",            "cat_subscription"),
                Triple("r213", "amediateka",        "cat_subscription"),
                Triple("r214", "амедиатека",        "cat_subscription"),
                Triple("r215", "литрес",            "cat_subscription"),
                Triple("r216", "litres",            "cat_subscription"),
                Triple("r217", "bookmate",          "cat_subscription"),
                Triple("r218", "букмейт",           "cat_subscription"),
                Triple("r219", "youtube",           "cat_subscription"),
                Triple("r220", "google play",       "cat_subscription"),
                Triple("r221", "google one",        "cat_subscription"),
                Triple("r222", "apple.com/bill",    "cat_subscription"),
                Triple("r223", "itunes",            "cat_subscription"),
                Triple("r224", "icloud",            "cat_subscription"),
                Triple("r225", "apple music",       "cat_subscription"),
                Triple("r226", "telegram premium",  "cat_subscription"),
                Triple("r227", "vk музыка",         "cat_subscription"),
                Triple("r228", "vk combo",          "cat_subscription"),
                Triple("r229", "deezer",            "cat_subscription"),
                Triple("r230", "openai",            "cat_subscription"),
                Triple("r231", "chatgpt",           "cat_subscription"),
                Triple("r232", "dropbox",           "cat_subscription"),
                Triple("r233", "notion",            "cat_subscription"),
                Triple("r234", "adobe",             "cat_subscription"),
                Triple("r235", "jetbrains",         "cat_subscription"),
                Triple("r236", "figma",             "cat_subscription"),
                Triple("r237", "canva",             "cat_subscription"),
                Triple("r238", "duolingo",          "cat_subscription"),
                Triple("r239", "подписка",          "cat_subscription"),
                Triple("r200", "букмекер",          "cat_betting"),
                Triple("r201", "ставка на спорт",   "cat_betting"),
            )
            rules.forEach { (id, pattern, catId) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO merchant_rules(id, pattern, category_id, priority, is_regex) VALUES(?, ?, ?, 0, 0)",
                    arrayOf(id, pattern, catId),
                )
            }
        }
    }
}
