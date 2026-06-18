package com.financeos.hub.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.financeos.hub.core.database.converters.FosTypeConverters
import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.BudgetDao
import com.financeos.hub.core.database.daos.CategoryDao
import com.financeos.hub.core.database.daos.GoalDao
import com.financeos.hub.core.database.daos.MerchantRuleDao
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.BudgetEntity
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.core.database.entities.MerchantRuleEntity
import com.financeos.hub.core.database.entities.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        MerchantRuleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(FosTypeConverters::class)
abstract class FosDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
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
            )
            val colors = listOf(
                "#FFB84D", "#4DFFA0", "#4D9FFF", "#FF6B6B", "#C084FC",
                "#F472B6", "#34D399", "#A78BFA", "#60A5FA", "#FB923C",
                "#E879F9", "#2DD4BF", "#94A3B8",
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
                // Entertainment
                Triple("r090", "кинотеатр",     "cat_entertain"),
                Triple("r091", "netflix",       "cat_entertain"),
                Triple("r092", "spotify",       "cat_entertain"),
                Triple("r093", "okko",          "cat_entertain"),
                Triple("r094", "more.tv",       "cat_entertain"),
                Triple("r095", "иви",           "cat_entertain"),
                Triple("r096", "яндекс.музыка", "cat_entertain"),
                Triple("r097", "steam",         "cat_entertain"),
                Triple("r098", "playstation",   "cat_entertain"),
                // Beauty
                Triple("r100", "л'этуаль",      "cat_beauty"),
                Triple("r101", "летуаль",       "cat_beauty"),
                Triple("r102", "рив гош",       "cat_beauty"),
                Triple("r103", "салон красоты", "cat_beauty"),
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
