package com.financeos.hub.features.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.finance.SavingsMath
import com.financeos.hub.ui.components.FosHairline
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard

/**
 * Накопление по годам двумя частями: снизу — ваши деньги, сверху — проценты.
 *
 * Одна линия итога показала бы «растёт» и ничего больше. Разделение — единственная картинка, на
 * которой видно то, ради чего вообще открывают такой калькулятор: как со временем верхняя часть
 * столбика догоняет и обгоняет нижнюю.
 *
 * Столбики рисуются прямоугольниками, а не Canvas: их максимум 50, каждый — два вложенных Box, и
 * компоновщик справляется с этим лучше, чем ручная арифметика по пикселям.
 */
@Composable
fun SavingsChart(schedule: List<SavingsMath.YearPoint>) {
    if (schedule.isEmpty()) return

    // На пятидесяти годах подписи под каждым столбиком превращаются в кашу — оставляем каждую N-ю.
    val step = when {
        schedule.size <= 12 -> 1
        schedule.size <= 25 -> 2
        else                -> 5
    }
    val max = schedule.maxOf { it.balanceKopecks }.coerceAtLeast(1L)

    Column(
        modifier            = Modifier.fillMaxWidth().fosCard(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment     = Alignment.Bottom,
        ) {
            schedule.forEach { p ->
                val total    = p.balanceKopecks.coerceAtLeast(0L)
                val interest = p.interestTotalKopecks.coerceAtLeast(0L)
                val own      = (total - interest).coerceAtLeast(0L)
                val heightFraction = (total.toFloat() / max).coerceIn(0.02f, 1f)

                Column(
                    modifier            = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // weight() внутри вертикальной колонки делит именно высоту столбика,
                        // поэтому доли не надо пересчитывать в dp — и они не «поедут» на другом экране.
                        // Доли, а не сами копейки: вес — Float, и на суммах за сотню миллионов
                        // копеек мантиссы уже не хватает, столбик начинает делиться неверно.
                        if (interest > 0) {
                            Box(
                                Modifier
                                    .weight(interest.toFloat() / total)
                                    .fillMaxWidth()
                                    .background(FosColors.Positive)
                            )
                        }
                        if (own > 0) {
                            Box(
                                Modifier
                                    .weight(own.toFloat() / total)
                                    .fillMaxWidth()
                                    .background(FosColors.Info)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            schedule.forEachIndexed { i, p ->
                Text(
                    text      = if ((i + 1) % step == 0 || i == schedule.lastIndex) "${p.year}" else "",
                    style     = FosType.Micro,
                    color     = FosColors.TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines  = 1,
                    modifier  = Modifier.weight(1f),
                )
            }
        }
        Text("год накопления", style = FosType.Micro, color = FosColors.TextMuted)
    }
}

/**
 * Та же таблица цифрами. График отвечает на «как это выглядит», таблица — на «сколько именно
 * будет к пятому году», и второй вопрос задают чаще, чем принято думать.
 */
@Composable
fun YearTable(schedule: List<SavingsMath.YearPoint>) {
    if (schedule.isEmpty()) return

    Column(
        modifier            = Modifier.fillMaxWidth().fosCard(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Cell("Год",      Modifier.width(44.dp), FosColors.TextMuted, FosType.Micro)
            Cell("Ваши",     Modifier.weight(1f),   FosColors.TextMuted, FosType.Micro, end = true)
            Cell("Проценты", Modifier.weight(1f),   FosColors.TextMuted, FosType.Micro, end = true)
            Cell("Итого",    Modifier.weight(1f),   FosColors.TextMuted, FosType.Micro, end = true)
        }
        FosHairline()
        schedule.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Cell("${p.year}", Modifier.width(44.dp), FosColors.TextSecondary, FosType.MicroNum)
                Cell(
                    FosFormatter.compact(p.contributedTotalKopecks),
                    Modifier.weight(1f), FosColors.TextSecondary, FosType.MicroNum, end = true,
                )
                Cell(
                    FosFormatter.compact(p.interestTotalKopecks),
                    Modifier.weight(1f), FosColors.Positive, FosType.MicroNum, end = true,
                )
                Cell(
                    FosFormatter.compact(p.balanceKopecks),
                    Modifier.weight(1f), FosColors.TextPrimary, FosType.SmallBold, end = true,
                )
            }
        }
    }
}

@Composable
private fun Cell(
    text    : String,
    modifier: Modifier,
    color   : Color,
    style   : TextStyle,
    end     : Boolean = false,
) {
    Text(
        text      = text,
        style     = style,
        color     = color,
        maxLines  = 1,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier  = modifier.padding(horizontal = 2.dp),
    )
}
