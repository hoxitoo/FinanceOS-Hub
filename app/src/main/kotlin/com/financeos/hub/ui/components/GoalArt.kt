package com.financeos.hub.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.financeos.hub.R
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.ui.theme.FosColors

/**
 * Visual theme of a goal, used to pick its pixel-art backdrop.
 *
 * Derived from the goal's emoji (which the user already picks when creating it) and, failing that,
 * from keywords in its name — so existing goals get artwork with no DB migration and no extra step
 * in the creation flow.
 */
enum class GoalArtKind(
    /**
     * The bundled artwork for this theme.
     *
     * A direct `R.drawable` reference, not a name looked up at runtime. The lookup version was
     * there only while the art did not exist yet; now that all nine files are in place, referencing
     * them properly means the compiler catches a missing or renamed file, the artwork survives
     * resource shrinking (which strips anything it cannot see referenced), and no reflection runs
     * on every card.
     */
    @DrawableRes val art: Int,
    /** Tint for the wash drawn under the artwork, keeping each theme's colour identity. */
    val tint : Color,
) {
    VACATION (R.drawable.goal_art_vacation,  Color(0xFF3BC9DB)),
    HOME     (R.drawable.goal_art_home,      Color(0xFFFFA94D)),
    CAR      (R.drawable.goal_art_car,       Color(0xFF748FFC)),
    TECH     (R.drawable.goal_art_tech,      Color(0xFF9B5CFF)),
    EDUCATION(R.drawable.goal_art_education, Color(0xFF4DABF7)),
    HEALTH   (R.drawable.goal_art_health,    Color(0xFF69DB7C)),
    GIFT     (R.drawable.goal_art_gift,      Color(0xFFFF87C2)),
    PURCHASE (R.drawable.goal_art_purchase,  Color(0xFFFFD43B)),
    SAVINGS  (R.drawable.goal_art_savings,   Color(0xFF4DFFA0)),
}

/** Emoji → theme. Mirrors the emoji list offered in AddGoalSheet. */
private val EMOJI_KIND = mapOf(
    "🏠" to GoalArtKind.HOME,      "🛋" to GoalArtKind.HOME,
    "🚗" to GoalArtKind.CAR,
    "✈" to GoalArtKind.VACATION,  "🏖" to GoalArtKind.VACATION,
    "📱" to GoalArtKind.TECH,     "💻" to GoalArtKind.TECH,     "🎸" to GoalArtKind.TECH,
    "📚" to GoalArtKind.EDUCATION,"🎓" to GoalArtKind.EDUCATION,
    "💊" to GoalArtKind.HEALTH,   "🏋" to GoalArtKind.HEALTH,
    "🎁" to GoalArtKind.GIFT,     "💍" to GoalArtKind.GIFT,
    "💰" to GoalArtKind.SAVINGS,  "⭐" to GoalArtKind.SAVINGS,
)

/** Name keywords → theme, checked when the emoji says nothing useful. */
private val NAME_KIND = listOf(
    GoalArtKind.VACATION  to listOf("отпуск", "путешеств", "поездка", "море", "тур", "виза", "билет", "греци", "трэвел", "тревел"),
    GoalArtKind.CAR       to listOf("авто", "машин", "тачк", "права", "мотоцикл"),
    GoalArtKind.HOME      to listOf("квартир", "дом", "ремонт", "ипотек", "мебел", "дач"),
    GoalArtKind.TECH      to listOf("телефон", "ноут", "комп", "iphone", "макбук", "техник", "гаджет", "консол"),
    GoalArtKind.EDUCATION to listOf("курс", "учеб", "образован", "школ", "универ", "язык"),
    GoalArtKind.HEALTH    to listOf("лечен", "здоров", "врач", "зуб", "спорт", "зал", "фитнес"),
    GoalArtKind.GIFT      to listOf("подар", "свадьб", "юбилей", "день рожд"),
    GoalArtKind.SAVINGS   to listOf("подушк", "накопл", "резерв", "запас", "сбереж"),
)

fun goalArtFor(goal: GoalEntity): GoalArtKind {
    EMOJI_KIND[goal.emoji.trim()]?.let { return it }
    val name = goal.name.lowercase()
    NAME_KIND.forEach { (kind, keys) -> if (keys.any { it in name }) return kind }
    return GoalArtKind.PURCHASE
}

/**
 * Pixel-art backdrop for a goal card.
 *
 * The themed wash is drawn UNDER the artwork rather than instead of it. It used to be a stand-in
 * for missing files; now it gives each theme a colour of its own even where the art is dark or
 * sparse, so a goal is recognisable at a glance before its picture resolves into anything.
 *
 * Whatever is drawn stays dim and is covered by a horizontal scrim, so the card's text and numbers
 * keep their contrast — the backdrop must never compete with the amounts.
 */
@Composable
fun GoalArtBackdrop(
    kind    : GoalArtKind,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        // Themed diagonal wash, under everything.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0.0f to kind.tint.copy(alpha = 0.20f),
                        1.0f to Color.Transparent,
                        start = Offset.Zero,
                        end   = Offset(700f, 320f),
                    )
                )
        )
        // NOTE: no filterQuality here — the painter-based Image overload in this Compose
        // version (BOM 2024.06) does not accept it. Pixel art therefore gets bilinear
        // smoothing when upscaled, so the art is authored at least as large as the card
        // (docs/GOAL_ART_PROMPTS.md asks for 1024×320, wider than any phone card).
        Image(
            painter            = painterResource(kind.art),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            alpha              = 0.38f,
            modifier           = Modifier.fillMaxSize(),
        )
        // Scrim: keeps the left-hand text legible over any artwork.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to FosColors.Surface.copy(alpha = 0.92f),
                        0.6f to FosColors.Surface.copy(alpha = 0.62f),
                        1.0f to FosColors.Surface.copy(alpha = 0.30f),
                    )
                )
        )
    }
}
