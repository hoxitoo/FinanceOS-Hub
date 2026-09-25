package com.financeos.hub.features.transactions

import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Заметность метки источника обратна его частоте.
 *
 * Смысл правила — не оформление, а читаемость списка: у человека с двенадцатью счетами в двух
 * банках цветная метка на каждой строке превращается в фон, и тогда она не различает ничего.
 * Поэтому проверяется именно поведение на РЕАЛЬНЫХ раскладах: «почти всё с одной карты»,
 * «всё поровну», «одна карта на весь список».
 */
class TxSourceLabelsTest {

    private fun tx(id: String, accountId: String? = null, mask: String? = null) = TransactionEntity(
        id            = id,
        accountId     = accountId,
        categoryId    = null,
        type          = TransactionType.EXPENSE,
        source        = TransactionSource.PUSH,
        amountKopecks = -100_00L,
        merchant      = "Магазин",
        description   = null,
        timestamp     = 1_700_000_000_000L,
        smsId         = null,
        sourceMask    = mask,
    )

    private fun account(id: String, name: String, bank: String, mask: String?) = AccountEntity(
        id             = id,
        name           = name,
        bank           = bank,
        cardMask       = mask,
        balanceKopecks = 0L,
    )

    private val sber = account("a1", "сбер", "Сбербанк", "3387")
    private val alfa = account("a2", "текущий", "Альфа-Банк", "1139")

    @Test
    fun `the dominant source goes quiet and the rare one gets colour`() {
        // Девять покупок Сбером и одна Альфой — ровно тот случай, ради которого правило и есть.
        val list = List(9) { tx("s$it", accountId = "a1") } + tx("x", accountId = "a2")
        val labels = buildSourceLabels(list, listOf(sber, alfa), emptyList())

        assertFalse("доминирующий источник обязан молчать", labels.getValue("acc:a1").prominent)
        assertTrue("редкий источник обязан выделяться", labels.getValue("acc:a2").prominent)
    }

    @Test
    fun `an evenly mixed list highlights everything`() {
        // Три карты по трети: доминирующего нет, различать нужно каждую строку, и пестрота здесь
        // не шум — это и есть информация.
        val third = account("a3", "тревел", "Т-Банк", "3583")
        val list = List(3) { tx("s$it", accountId = "a1") } +
            List(3) { tx("m$it", accountId = "a2") } +
            List(3) { tx("t$it", accountId = "a3") }
        val labels = buildSourceLabels(list, listOf(sber, alfa, third), emptyList())

        assertTrue(labels.values.all { it.prominent })
    }

    @Test
    fun `a single source is quiet - there is nothing to tell apart`() {
        val list = List(5) { tx("s$it", accountId = "a1") }
        val labels = buildSourceLabels(list, listOf(sber, alfa), emptyList())

        assertEquals(1, labels.size)
        assertFalse("одна карта на весь список — выделять её не от чего", labels.getValue("acc:a1").prominent)
    }

    @Test
    fun `exactly at the threshold the leader counts as dominant`() {
        // 4 из 10 — ровно 40 %. Порог включающий: на границе метка иначе мигала бы туда-сюда от
        // одной новой операции, а «тихий» и «цветной» — это не то, что должно меняться от каждого
        // пуша. Тихим может стать ТОЛЬКО лидер: в списке 4/3/3 выделять двоих из троих незачем.
        val third = account("a3", "тревел", "Т-Банк", "3583")
        val list = List(4) { tx("s$it", accountId = "a1") } +
            List(3) { tx("m$it", accountId = "a2") } +
            List(3) { tx("t$it", accountId = "a3") }
        val labels = buildSourceLabels(list, listOf(sber, alfa, third), emptyList())

        assertFalse("лидер ровно на пороге — тихий", labels.getValue("acc:a1").prominent)
        assertTrue(labels.getValue("acc:a2").prominent)
        assertTrue(labels.getValue("acc:a3").prominent)
    }

    @Test
    fun `just below the threshold nobody is quiet`() {
        // Лидер — 3 из 8, то есть 37,5 %. Приглушить его значило бы назвать фоном того, кого в
        // списке чуть больше трети.
        val third = account("a3", "тревел", "Т-Банк", "3583")
        val list = List(3) { tx("s$it", accountId = "a1") } +
            List(3) { tx("m$it", accountId = "a2") } +
            List(2) { tx("t$it", accountId = "a3") }
        val labels = buildSourceLabels(list, listOf(sber, alfa, third), emptyList())

        assertTrue(labels.values.all { it.prominent })
    }

    @Test
    fun `the label is the card mask, because that is what the bank prints`() {
        val labels = buildSourceLabels(listOf(tx("1", accountId = "a1")), listOf(sber), emptyList())
        assertEquals("•• 3387", labels.getValue("acc:a1").label)
        assertEquals("Сбербанк", labels.getValue("acc:a1").bank)
    }

    @Test
    fun `an account without a card falls back to its name`() {
        val cash = account("a9", "наличные", "Наличные", mask = null)
        val labels = buildSourceLabels(listOf(tx("1", accountId = "a9")), listOf(cash), emptyList())
        assertEquals("наличные", labels.getValue("acc:a9").label)
    }

    @Test
    fun `a long account name is cut so it cannot break the row`() {
        val long = account("a8", "минимальный остаток", "Сбербанк", mask = null)
        val labels = buildSourceLabels(listOf(tx("1", accountId = "a8")), listOf(long), emptyList())
        assertTrue("имя не должно расталкивать строку", labels.getValue("acc:a8").label.length <= 14)
    }

    @Test
    fun `an unlinked operation still shows the mask from the message`() {
        // Счёта нет, но банк напечатал четыре цифры — человек свою карту по ним узнаёт. Прятать
        // единственное, что о строке известно, только потому что карта не заведена, нельзя.
        val labels = buildSourceLabels(listOf(tx("1", mask = "9999")), emptyList(), emptyList())
        assertEquals("•• 9999", labels.getValue("mask:9999").label)
        assertEquals("", labels.getValue("mask:9999").bank)
    }

    @Test
    fun `an unlinked mask borrows the bank colour from a card that has it`() {
        // Та же карта заведена на счёте — значит банк известен, даже если операция не привязана.
        val card = CardEntity(id = "c1", accountId = "a1", cardMask = "7777")
        val labels = buildSourceLabels(listOf(tx("1", mask = "7777")), listOf(sber), listOf(card))
        assertEquals("Сбербанк", labels.getValue("mask:7777").bank)
    }

    @Test
    fun `rows with no source at all produce no labels`() {
        val labels = buildSourceLabels(listOf(tx("1")), listOf(sber), emptyList())
        assertTrue(labels.isEmpty())
        assertNull(sourceKeyOf(tx("1")))
    }

    @Test
    fun `an empty list is not a crash`() {
        assertTrue(buildSourceLabels(emptyList(), listOf(sber), emptyList()).isEmpty())
    }
}
