package com.financeos.hub.features.goals

import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.ui.components.GoalArtKind
import com.financeos.hub.ui.components.goalArtFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Иконка цели выбирает не только глиф, но и ПОДЛОЖКУ карточки (`goalArtFor`).
 *
 * Связь держится на двух списках в разных файлах — группах в форме и карте `EMOJI_KIND` в
 * `GoalArt.kt`, — и расходятся они молча: иконка без темы не ломает ничего, она просто получает
 * подложку «покупки». Заметить это можно только глазами и только на той цели, которую кто-то
 * создал именно с этой иконкой.
 */
class GoalIconsTest {

    /** Какой темой обязана обернуться каждая группа формы. */
    private val expected = mapOf(
        "Путешествия" to GoalArtKind.VACATION,
        "Жильё"       to GoalArtKind.HOME,
        "Транспорт"   to GoalArtKind.CAR,
        "Техника"     to GoalArtKind.TECH,
        "Образование" to GoalArtKind.EDUCATION,
        "Здоровье"    to GoalArtKind.HEALTH,
        "Праздники"   to GoalArtKind.GIFT,
        "Накопления"  to GoalArtKind.SAVINGS,
        "Покупки"     to GoalArtKind.PURCHASE,
    )

    private fun goalWith(emoji: String) = GoalEntity(
        id            = "g1",
        name          = "Цель",           // нейтральное имя: тема должна прийти от иконки
        emoji         = emoji,
        targetKopecks = 100_000L,
        deadlineAt    = null,
    )

    @Test
    fun `every offered icon resolves to its own group's artwork`() {
        ICON_GROUPS.forEach { group ->
            val kind = expected[group.title]
                ?: error("У группы «${group.title}» не объявлена тема — список в тесте отстал от формы")
            group.emojis.forEach { emoji ->
                assertEquals(
                    "Иконка $emoji из группы «${group.title}» получает чужую подложку",
                    kind,
                    goalArtFor(goalWith(emoji)),
                )
            }
        }
    }

    @Test
    fun `the test knows about every group in the form`() {
        // Обратная сторона: новая группа в форме не должна проскочить мимо проверки выше.
        assertEquals(expected.keys, ICON_GROUPS.map { it.title }.toSet())
    }

    @Test
    fun `no icon appears in two groups`() {
        // Один глиф в двух группах означает, что выбор подсвечен сразу в двух местах, а тема
        // достаётся той, что стоит раньше в EMOJI_KIND, — то есть не той, на которую нажали.
        val all = ICON_GROUPS.flatMap { it.emojis }
        assertEquals("иконки повторяются: ${all.groupBy { it }.filter { it.value.size > 1 }.keys}",
            all.size, all.toSet().size)
    }

    @Test
    fun `an unknown emoji still falls back instead of failing`() {
        // Старые цели могли быть созданы с иконкой, которой в форме больше нет.
        assertTrue(goalArtFor(goalWith("🦄")) == GoalArtKind.PURCHASE)
    }

    @Test
    fun `the goal name decides when the emoji says nothing`() {
        // Подстраховка для целей, пришедших из резервной копии с эмодзи по умолчанию.
        val byName = GoalEntity(
            id = "g2", name = "Отпуск в Греции", emoji = "🎯",
            targetKopecks = 100_000L, deadlineAt = null,
        )
        assertEquals(GoalArtKind.VACATION, goalArtFor(byName))
    }
}
