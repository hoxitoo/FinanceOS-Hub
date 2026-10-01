package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.AlfabankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пополнение брокерского счёта — перевод своих денег, а не трата.
 *
 * Тексты пушей сняты с устройства и склеены так же, как их собирает `PushNotificationListener`
 * (заголовок, затем текст). Менять их нельзя.
 */
class InvestmentTransfersTest {

    private val ts     = 1_759_300_000_000L
    private val engine = ParserEngine(setOf(AlfabankParser()))

    // ── Реальные пуши Альфы ──────────────────────────────────────────────────────

    @Test
    fun `Alfa top-up of a BKS brokerage account is an outgoing transfer`() {
        // Скриншот пользователя, 1 октября: в списке операций это было «−10 000 ₽ · Другое».
        val body = "-10 000 ₽ Списание со счета 408*01139; Сумма: 10 000,00 RUB; " +
            "Получатель платежа BKS Mir Investitsiy; 1 октября 07:53"
        val tx = engine.parse("ALFABANK", body, ts)

        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER, tx!!.type)
        assertTrue("деньги ушли со счёта", tx.outgoing)
        // Знаковая сумма та же, что у прежнего расхода: баланс и дедуп по знаковой сумме не меняются.
        assertEquals(-1_000_000L, tx.signedKopecks())
        assertEquals(InvestmentTransfers.CATEGORY, tx.categoryId)
        assertEquals("BKS Mir Investitsiy", tx.merchant)
        assertEquals("1139", tx.cardMask)
    }

    @Test
    fun `the earlier small BKS top-up is a transfer as well`() {
        val body = "-200 ₽ Списание со счета 408*01139; Сумма: 200,00 RUB; " +
            "Получатель платежа BKS Mir Investitsiy; 2 сентября 08:56"
        val tx = engine.parse("ALFABANK", body, ts)!!
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(-20_000L, tx.signedKopecks())
    }

    @Test
    fun `an ordinary payee keeps its expense and gets no category hint`() {
        val body = "-200 ₽ Списание со счета 408*01139; Сумма: 200,00 RUB; " +
            "Получатель платежа FONBET; 2 сентября 08:56"
        val tx = engine.parse("ALFABANK", body, ts)!!
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertNull("категорию решает классификатор, как раньше", tx.categoryId)
    }

    // ── Направление ──────────────────────────────────────────────────────────────

    private fun parsed(type: TransactionType, merchant: String?) = ParsedTransaction(
        type = type, amountKopecks = 500_000L, merchant = merchant, cardMask = null,
        balanceKopecks = null, timestamp = ts, bankId = "alfabank", rawSms = "", smsId = "s",
    )

    @Test
    fun `money coming back from the broker is an incoming transfer, not earnings`() {
        val r = InvestmentTransfers.reclassify(parsed(TransactionType.INCOME, "АО БКС Банк брокер"))
        assertEquals(TransactionType.TRANSFER, r.type)
        assertFalse(r.outgoing)
        assertEquals(500_000L, r.signedKopecks())
        assertEquals(InvestmentTransfers.CATEGORY, r.categoryId)
    }

    @Test
    fun `a non-broker message is returned untouched`() {
        val p = parsed(TransactionType.EXPENSE, "Пятёрочка")
        assertSame(p, InvestmentTransfers.reclassify(p))
    }

    // ── Кто брокер, а кто нет ────────────────────────────────────────────────────

    @Test
    fun `broker names are recognised in any case and script`() {
        listOf(
            "BKS Mir Investitsiy", "БКС Мир Инвестиций", "ООО Компания БКС", "BCS Broker",
            "Т-Инвестиции", "АЛЬФА-ИНВЕСТИЦИИ", "ВТБ Мои Инвестиции", "Tinkoff Investicii",
            "АО ФИНАМ", "Finam", "Freedom Finance", "Фридом Финанс", "Открытие Брокер",
            "Сбер Инвестиции",
        ).forEach { assertTrue(it, InvestmentTransfers.isBroker(it)) }
    }

    @Test
    fun `ordinary merchants are not brokers`() {
        listOf(
            "Пятёрочка", "Ozon", "FONBET", "InvestStroy", "Investment Bank Shop",
            "ABKSHOP", "Бксмарт", "Озон Банк", "", null,
            // Замечания ревью: общие слова «инвестиц» и «брокер» — это и застройщик, и банк по
            // кредиту, и работодатель, и страховой брокер. Ни одного из них переводом не считать.
            "Инвестиционно-строительная компания", "КБ Инвестиционный Банк",
            "ООО УК Инвестиционные решения", "Investitsionnaya kompaniya",
            "Страховой брокер Ингосстрах", "Ипотечный брокер", "Pawnbroker", "Финамарт",
        ).forEach { assertFalse(it.toString(), InvestmentTransfers.isBroker(it)) }
    }

    @Test
    fun `a salary from an investment-sounding employer stays income`() {
        // Ошибка с обратной стороны хуже потери: зарплата, ставшая переводом, пропала бы из дохода
        // и из оценки сбережений.
        val p = parsed(TransactionType.INCOME, "ООО УК Инвестиционные решения")
        assertSame(p, InvestmentTransfers.reclassify(p))
    }

    // ── Старая история и копии до v21 ────────────────────────────────────────────

    private fun stored(type: TransactionType, category: String?, merchant: String?) = TransactionEntity(
        id = "t", accountId = null, categoryId = category, type = type,
        source = TransactionSource.PUSH, amountKopecks = -1_000_000L, merchant = merchant,
        description = null, timestamp = ts, smsId = null,
    )

    @Test
    fun `an old broker expense in a machine category is relabelled, amount kept`() {
        val r = InvestmentTransfers.relabel(stored(TransactionType.EXPENSE, "cat_other", "BKS Mir Investitsiy"))
        assertEquals(TransactionType.TRANSFER, r.type)
        assertEquals(InvestmentTransfers.CATEGORY, r.categoryId)
        assertEquals(-1_000_000L, r.amountKopecks)
        assertEquals(TransactionType.TRANSFER, InvestmentTransfers.relabel(stored(TransactionType.EXPENSE, null, "BKS")).type)
    }

    @Test
    fun `a row the person categorised by hand is left alone`() {
        val manual = stored(TransactionType.EXPENSE, "cat_shopping", "BKS Mir Investitsiy")
        assertSame(manual, InvestmentTransfers.relabel(manual))
        val transfer = stored(TransactionType.TRANSFER, null, "BKS Mir Investitsiy")
        assertSame(transfer, InvestmentTransfers.relabel(transfer))
        val shop = stored(TransactionType.EXPENSE, "cat_other", "Пятёрочка")
        assertSame(shop, InvestmentTransfers.relabel(shop))
    }

    @Test
    fun `a stored row is an investment transfer only with both type and category`() {
        fun row(type: TransactionType, category: String?) = TransactionEntity(
            id = "t", accountId = null, categoryId = category, type = type,
            source = TransactionSource.PUSH, amountKopecks = -1_000_000L, merchant = "BKS",
            description = null, timestamp = ts, smsId = null,
        )
        assertTrue(InvestmentTransfers.isInvestmentTransfer(row(TransactionType.TRANSFER, InvestmentTransfers.CATEGORY)))
        // Человек сам перевёл строку в расход — это его выбор, и это уже не перевод.
        assertFalse(InvestmentTransfers.isInvestmentTransfer(row(TransactionType.EXPENSE, InvestmentTransfers.CATEGORY)))
        assertFalse(InvestmentTransfers.isInvestmentTransfer(row(TransactionType.TRANSFER, null)))
    }
}
