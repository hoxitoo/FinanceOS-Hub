package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.SberbankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Two REAL Сбер credit-card notifications, transcribed verbatim from the user's phone.
 *
 * Both were silently dropped before this: the purchase because [SberbankParser.parsePush] anchored
 * only on «В запасе:», the reminder because [PromoFilter] rejects «беспроцентным». One was a lost
 * 18 699 ₽ operation, the other a lost payment deadline.
 */
class SberCreditPushTest {

    private val parser = SberbankParser()
    private val zone   = ZoneId.of("Europe/Moscow")
    private val ts     = 1_756_000_000_000L

    // ── Real push #1: a purchase on the credit card ───────────────────────────
    // The notification listener joins title and body, which is what reaches the parser.
    private val purchase = "Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽ Счёт карты МИР •• 6703"

    @Test
    fun `credit card purchase is parsed`() {
        val p = parser.parse("900", purchase, ts)
        assertNotNull("push must not be dropped", p)
        assertEquals(TransactionType.EXPENSE, p!!.type)
        assertEquals(18_699_00L, p.amountKopecks)
        assertEquals("6703", p.cardMask)
    }

    @Test
    fun `the operation word is stripped from the merchant`() {
        // "Покупка DNS" as a merchant matches no rule and reads as the operation, not the shop.
        assertEquals("DNS", parser.parse("900", purchase, ts)!!.merchant)
    }

    @Test
    fun `the reported figure is carried through verbatim`() {
        // 411 301 is the card's FREE LIMIT, not money owned — but that translation belongs to
        // AccountLinker, which knows the account kind. The parser reports what the bank printed.
        assertEquals(411_301_00L, parser.parse("900", purchase, ts)!!.balanceKopecks)
    }

    @Test
    fun `the purchase push survives the promo filter`() {
        assertTrue(!PromoFilter.isPromo(purchase))
    }

    @Test
    fun `the older В запасе format still parses`() {
        val p = parser.parse("900", "Пятёрочка 1 240 ₽ В запасе: 8 001,89 ₽ Карта •1234", ts)
        assertNotNull(p)
        assertEquals(1_240_00L, p!!.amountKopecks)
        assertEquals(8_001_89L, p.balanceKopecks)
        assertEquals("1234", p.cardMask)
    }

    // ── Real push #2: the payment reminder ────────────────────────────────────
    private val reminder =
        "Платёж по кредитной карте Внесите платёж 373,98р до 31.08.26 на кредитную карту, " +
            "чтобы не допустить просрочки и продолжать пользоваться беспроцентным периодом."

    @Test
    fun `the reminder is recognised as a notice`() {
        val n = CreditNoticeParser.parse("900", reminder, zone)
        assertNotNull("reminder must be recognised", n)
        assertEquals(373_98L, n!!.amountKopecks)
        assertEquals(
            LocalDate.of(2026, 8, 31).atStartOfDay(zone).toInstant().toEpochMilli(),
            n.dueAtMillis,
        )
    }

    @Test
    fun `the reminder never becomes a transaction`() {
        // No money moved. Booking 373,98 ₽ would invent a purchase AND double-count the real
        // payment when it arrives. It must fall out of the transaction path entirely.
        assertNull(parser.parse("900", reminder, ts))
    }

    @Test
    fun `the promo filter is exactly why the notice needs its own entry point`() {
        // Documents the coupling: «беспроцентным» trips the marketing marker, so a notice routed
        // through the normal parse() path would never be seen. If this ever stops being true the
        // separate entry point can be reconsidered.
        assertTrue(PromoFilter.isPromo(reminder))
    }

    @Test
    fun `a real marketing push is still not mistaken for a notice`() {
        val promo = "Одобрили кредитку! Получите карту с лимитом 163 000 ₽ и беспроцентным " +
            "периодом до 120 дней."
        assertNull(CreditNoticeParser.parse("900", promo, zone))
    }

    @Test
    fun `a notice from another sender is ignored`() {
        assertNull(CreditNoticeParser.parse("MTS", reminder, zone))
    }

    @Test
    fun `a message about something other than a credit card is ignored`() {
        val loan = "Внесите платёж 5 000р до 10.09.26 по потребительскому кредиту."
        assertNull(CreditNoticeParser.parse("900", loan, zone))
    }

    @Test
    fun `a four digit year is accepted`() {
        val n = CreditNoticeParser.parse(
            "900",
            "Внесите платёж 1 200,50р до 05.09.2026 на кредитную карту.",
            zone,
        )
        assertNotNull(n)
        assertEquals(1_200_50L, n!!.amountKopecks)
    }

    @Test
    fun `a malformed date is rejected rather than guessed`() {
        // Single-digit day/month is not a shape any bank sends; parsing it loosely would risk
        // reading "1.2.26" as some other date entirely.
        assertNull(
            CreditNoticeParser.parse("900", "Внесите платёж 100р до 1.2.26 на кредитную карту.", zone)
        )
    }

    @Test
    fun `the card is named in either declension`() {
        // «по кредитной карте» in the title, «на кредитную карту» in the body — both must count.
        assertNotNull(
            CreditNoticeParser.parse("900", "Внесите платёж 500р до 10.09.26 на кредитную карту.", zone)
        )
        assertNotNull(
            CreditNoticeParser.parse("900", "Платёж по кредитной карте. Внесите 500р до 10.09.26.", zone)
        )
    }
}
