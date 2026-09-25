package com.financeos.hub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType

/**
 * Кольцо выполнения цели с процентом внутри.
 *
 * Процент стоит В КОЛЬЦЕ, а не рядом: круг и есть изображение этого числа, и когда цифра лежала
 * отдельной строкой справа, карточка показывала одно и то же двумя способами в разных местах —
 * глазу приходилось сверять их между собой.
 *
 * [color] задаёт вызывающий, потому что цвет здесь — ПРИЗНАК ЦЕЛИ, а не её состояния: зелёное
 * кольцо у каждой из пяти целей превращало список в одинаковые строки. Достигнутая цель — другое
 * дело, там зелёный честен по правилу #1, и решает это тоже вызывающий.
 */
@Composable
fun GoalRing(
    progress : Float,   // 0f..1f
    modifier : Modifier = Modifier,
    color    : Color = FosColors.Positive,
    /** Показывать процент внутри кольца. Выключается там, где рядом уже есть подпись. */
    showLabel: Boolean = true,
) {
    val clamped = progress.coerceIn(0f, 1f)

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke  = size.minDimension * 0.12f
            val inset   = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // Дорожка. Тон цели, приглушённый: своя окружность у каждой цели читается даже при
            // нулевом прогрессе, когда дуги ещё нет вовсе.
            drawArc(
                color       = color.copy(alpha = 0.18f),
                startAngle  = -90f,
                sweepAngle  = 360f,
                useCenter   = false,
                topLeft     = topLeft,
                size        = arcSize,
                style       = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (clamped > 0f) {
                drawArc(
                    color       = color,
                    startAngle  = -90f,
                    sweepAngle  = 360f * clamped,
                    useCenter   = false,
                    topLeft     = topLeft,
                    size        = arcSize,
                    style       = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        if (showLabel) {
            Text(
                // Округление ВНИЗ: 99,7 % — это ещё не «100 %», и подпись, обогнавшая дугу,
                // выглядит как ошибка расчёта.
                if (clamped >= 1f) "100%" else "${(clamped * 100).toInt()}%",
                style = FosType.SmallBold,
                color = color,
            )
        }
    }
}
