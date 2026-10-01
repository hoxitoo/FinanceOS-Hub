package com.financeos.hub.core.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Незаведённая карта не ложится на единственный счёт своего банка (инвариант #45).
 *
 * Случай тестера: в приложении один счёт Сбера — карта •• 8937. Пуши приходят и по •• 0471
 * (накопительный), •• 1238, •• 4102. Раньше все они ложились на •• 8937, и «Баланс: 100 590 ₽»
 * накопительного счёта становился балансом дебетовой карты.
 */
class ForeignCardTest {

    @Test
    fun `a message without a card number may use the bank's only account`() {
        assertTrue(AccountLinker.mayFallBackToBank(null, listOf("8937"), hasBalance = true))
        assertTrue(AccountLinker.mayFallBackToBank("", listOf("8937"), hasBalance = true))
    }

    @Test
    fun `an account with no numbers at all may take any card of its bank`() {
        // Сравнивать не с чем: так работала привязка по хвосту номера счёта Альфы «408*01139».
        assertTrue(AccountLinker.mayFallBackToBank("1139", emptyList(), hasBalance = true))
    }

    @Test
    fun `another card of the same bank is not put on the account`() {
        assertFalse(AccountLinker.mayFallBackToBank("0471", listOf("8937"), hasBalance = true))
        assertFalse(AccountLinker.mayFallBackToBank("1238", listOf("8937", "6703"), hasBalance = true))
    }

    @Test
    fun `a message without a balance keeps the old bank fallback`() {
        // «Списание со счета 408*01139» у Альфы: хвост номера счёта, а не карты, и без «Остатка».
        // Переписать чужой баланс такое сообщение не может — оно двигает его дельтой, как раньше.
        assertTrue(AccountLinker.mayFallBackToBank("1139", listOf("2548"), hasBalance = false))
    }

    @Test
    fun `card ownership tolerates stored format drift`() {
        assertTrue(AccountLinker.ownsMask(listOf("8937"), "8937"))
        assertTrue(AccountLinker.ownsMask(listOf("•• 8937"), "8937"))
        assertTrue(AccountLinker.ownsMask(listOf("1234"), "*1234"))
        assertFalse(AccountLinker.ownsMask(listOf("8937"), "0471"))
        assertFalse(AccountLinker.ownsMask(emptyList(), "0471"))
    }
}
