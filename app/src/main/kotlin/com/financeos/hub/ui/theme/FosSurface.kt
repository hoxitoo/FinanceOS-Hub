package com.financeos.hub.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One definition of what a card looks like, so every screen inherits the same faceting.
 *
 * The problem this solves: cards were painted by hand in thirty-odd places, all with the same flat
 * `Surface` fill, no border, and one radius. On a dark ground that reads as a single unbroken sheet
 * — you cannot tell where one block ends and the next begins, and nothing looks more important than
 * anything else. Three things fix it, and they are all here rather than scattered:
 *
 *  1. **A border on everything.** A hairline is what turns a filled rectangle into an object. This
 *     alone does most of the work.
 *  2. **Elevation that is actually visible.** [FosColors] steps are far enough apart to read.
 *  3. **Radius and tone by ROLE.** The main block of a screen is rounder and brighter than a row in
 *     a list, and a block about money leaving carries a red rail. Hierarchy before you read a word.
 *
 * Tone is applied only where it is semantically true — the Critical Design Rules reserve green for
 * income and red for spending, and that holds for a surface wash exactly as it holds for a number.
 * A block with nothing to say stays [FosTone.Neutral].
 */

/** Semantic colouring of a card. Neutral means "this block has no financial direction". */
enum class FosTone(val accent: Color?) {
    Neutral (null),
    Positive(FosColors.Positive),   // доход, накопления, успех
    Negative(FosColors.Negative),   // расход, превышение, тревога
    Warning (FosColors.Warning),    // прогноз, приближение к пределу
    Info    (FosColors.Info),       // справка, нейтральная подсказка
}

/** How a card is cut. Pick by the block's role on the screen, not by how it should look. */
enum class FosCardStyle {
    /** Default: card surface + hairline border. Most blocks. */
    Plain,
    /** The one block a screen is about: brighter surface, brighter border, a little lift. */
    Raised,
    /** Plain plus an accent rail down the left edge. For a block with a financial direction. */
    Rail,
    /** No fill, accent border. For a call to action or a caution that must not read as content. */
    Outline,
    /** Below the card surface: nested lists and rows that belong INSIDE another card. */
    Sunken,
}

/**
 * Paints a card and applies its inner padding.
 *
 * Deliberately a plain function rather than a `@Composable` one: it reads nothing from composition,
 * so it can be used anywhere a Modifier can — including inside `items {}` lambdas, where a
 * composable modifier would not compile.
 *
 * Order matters and is fixed here so no call site can get it wrong: lift → clip → fill → border →
 * rail → padding. Clipping before the fill is what keeps the corners honest.
 */
fun Modifier.fosCard(
    style  : FosCardStyle = FosCardStyle.Plain,
    tone   : FosTone      = FosTone.Neutral,
    radius : Dp           = FosDimens.RadiusCard,
    padding: Dp           = FosDimens.CardPadding,
): Modifier = fosCardSurface(style, tone, radius).padding(padding)

/**
 * The same fill, border and rail as [fosCard] but WITHOUT the inner padding.
 *
 * For a row that has to be tappable: the click has to sit between the fill and the padding, or the
 * ripple either escapes the rounded corners or covers only the text. Callers add
 * `.clickable { }.padding(...)` themselves.
 */
fun Modifier.fosCardSurface(
    style  : FosCardStyle = FosCardStyle.Plain,
    tone   : FosTone      = FosTone.Neutral,
    radius : Dp           = FosDimens.RadiusCard,
): Modifier {
    val shape  = RoundedCornerShape(radius)
    val accent = tone.accent

    val fill = when (style) {
        FosCardStyle.Sunken  -> FosColors.SurfaceSunken
        FosCardStyle.Raised  -> FosColors.SurfaceRaised
        FosCardStyle.Outline -> Color.Transparent
        else                 -> FosColors.Surface
    }
    val borderColor = edgeColor(style, accent)

    var m: Modifier = this
    // A raised card is the only one that lifts. Shadows on every card would flatten the hierarchy
    // again, which is the problem we are undoing.
    if (style == FosCardStyle.Raised) {
        m = m.shadow(elevation = 10.dp, shape = shape, clip = false)
    }
    m = m.clip(shape)
    if (fill != Color.Transparent) m = m.background(fill)
    // A whisper of the accent, brightest at the top-left and gone by the middle. Kept under 7% so
    // it registers as "this card is about spending" without ever competing with an amount.
    if (accent != null && style != FosCardStyle.Outline) {
        m = m.background(
            Brush.linearGradient(
                0.0f to accent.copy(alpha = 0.065f),
                0.6f to Color.Transparent,
                start = Offset.Zero,
                end   = Offset(520f, 420f),
            )
        )
    }
    m = m.border(1.dp, borderColor, shape)
    if (style == FosCardStyle.Rail && accent != null) {
        m = m.drawBehind {
            val w = FosDimens.RailWidth.toPx()
            val r = w / 2f
            drawRoundRect(
                color        = accent,
                topLeft      = Offset(0f, 0f),
                size         = Size(w, size.height),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
    return m
}

/** Which colour the edge of a card takes, given its style and (optional) accent. */
private fun edgeColor(style: FosCardStyle, accent: Color?): Color = when {
    // A toned card states its direction through the border too, not only the rail — at a
    // glance across a scroll the edge is what the eye picks up.
    style == FosCardStyle.Outline && accent != null -> accent.copy(alpha = 0.45f)
    style == FosCardStyle.Outline                   -> FosColors.BorderStrong
    accent != null                                  -> accent.copy(alpha = 0.28f)
    style == FosCardStyle.Raised                    -> FosColors.BorderStrong.copy(alpha = 0.55f)
    style == FosCardStyle.Sunken                    -> FosColors.BorderSoft
    else                                            -> FosColors.Border
}

/**
 * The border and rail of [fosCardSurface], drawn ON TOP of the card's own content.
 *
 * For the one case the plain modifier can't serve: a card whose content fills it edge to edge — the
 * goal cards paint pixel art across their whole area, and a border applied in the modifier chain is
 * painted *before* children, so the artwork covered it. The card looked borderless precisely where a
 * border was most needed to separate two adjacent goals.
 *
 * Apply this in addition to [fosCardSurface], not instead of it: the fill still belongs underneath.
 */
fun Modifier.fosCardEdge(
    style : FosCardStyle = FosCardStyle.Plain,
    tone  : FosTone      = FosTone.Neutral,
    radius: Dp           = FosDimens.RadiusCard,
): Modifier = drawWithContent {
    drawContent()
    val accent = tone.accent
    val r      = CornerRadius(radius.toPx(), radius.toPx())
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color        = edgeColor(style, accent),
        topLeft      = Offset(stroke / 2f, stroke / 2f),
        size         = Size(size.width - stroke, size.height - stroke),
        cornerRadius = r,
        style        = Stroke(width = stroke),
    )
    if (style == FosCardStyle.Rail && accent != null) {
        val w = FosDimens.RailWidth.toPx()
        drawRoundRect(
            color        = accent,
            topLeft      = Offset.Zero,
            size         = Size(w, size.height),
            cornerRadius = CornerRadius(w / 2f, w / 2f),
        )
    }
}

/** Convenience for the single most important block on a screen. */
fun Modifier.fosHeroCard(tone: FosTone = FosTone.Neutral): Modifier =
    fosCard(FosCardStyle.Raised, tone, FosDimens.RadiusHero, FosDimens.CardPadding)

/** Convenience for a row nested inside another card — the well, not the card. */
fun Modifier.fosInset(tone: FosTone = FosTone.Neutral, padding: Dp = FosDimens.CardPaddingSmall): Modifier =
    fosCard(FosCardStyle.Sunken, tone, FosDimens.RadiusInset, padding)
