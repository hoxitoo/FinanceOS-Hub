package com.financeos.hub.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    /** Drawable base name: `goal_art_<asset>` in res/drawable. Optional — see [GoalArtBackdrop]. */
    val asset: String,
    /** Tint used by the procedural fallback while the artwork is not bundled yet. */
    val tint : Color,
) {
    VACATION ("vacation",  Color(0xFF3BC9DB)),
    HOME     ("home",      Color(0xFFFFA94D)),
    CAR      ("car",       Color(0xFF748FFC)),
    TECH     ("tech",      Color(0xFF9B5CFF)),
    EDUCATION("education", Color(0xFF4DABF7)),
    HEALTH   ("health",    Color(0xFF69DB7C)),
    GIFT     ("gift",      Color(0xFFFF87C2)),
    PURCHASE ("purchase",  Color(0xFFFFD43B)),
    SAVINGS  ("savings",   Color(0xFF4DFFA0)),
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
 * The artwork is OPTIONAL: it is resolved by name at runtime, so the app builds and looks finished
 * without it, and each `goal_art_<kind>` drawable starts showing the moment it is dropped into
 * res/drawable (same graceful-degradation approach as the optional .tflite models). Until then a
 * procedural themed gradient stands in.
 *
 * Whatever is drawn stays dim and is covered by a horizontal scrim, so the card's text and numbers
 * keep their contrast — the backdrop must never compete with the amounts.
 */
@Composable
fun GoalArtBackdrop(
    kind    : GoalArtKind,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    @Suppress("DiscouragedApi")
    val resId = remember(kind) {
        context.resources.getIdentifier("goal_art_${kind.asset}", "drawable", context.packageName)
    }

    Box(modifier) {
        if (resId != 0) {
            // NOTE: no filterQuality here — the painter-based Image overload in this Compose
            // version (BOM 2024.06) does not accept it. Pixel art therefore gets bilinear
            // smoothing when upscaled, so generate the art at least as large as the card
            // (docs/GOAL_ART_PROMPTS.md asks for 1024×320, which is wider than any phone card).
            Image(
                painter            = painterResource(resId),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                alpha              = 0.38f,
                modifier           = Modifier.fillMaxSize(),
            )
        } else {
            // Fallback: themed diagonal wash so the card already reads as "has a backdrop".
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
        }
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
