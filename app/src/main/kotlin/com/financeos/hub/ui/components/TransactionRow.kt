package com.financeos.hub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.MerchantNames
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.fosCardSurface
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.bankBrand
import com.financeos.hub.ui.theme.FosType

/**
 * Откуда пришла операция — какая карта какого банка.
 *
 * [prominent] решает не строка и не банк, а СПИСОК: заметность зависит от того, насколько источник
 * редок среди того, что сейчас на экране. У человека с двенадцатью счетами в двух банках цветная
 * метка на каждой строке — это не метка, а фон; ровно по той же причине расход в этом списке не
 * красится красным кантом. Поэтому доминирующий источник говорит тихо, а редкий — цветом: глаз
 * ловит исключение, а не повторяющийся фон.
 *
 * Считает это [com.financeos.hub.features.transactions.TransactionsState.sourceOf], а не строка:
 * строка о соседях ничего не знает.
 */
data class TxSource(
    /** Что писать: «•• 6703», иначе имя счёта. */
    val label    : String,
    /** Имя банка — из него берётся цвет бренда. */
    val bank     : String,
    /** Выделять цветом (редкий источник) или оставить тихой серой подписью (доминирующий). */
    val prominent: Boolean,
)

@Composable
fun TransactionRow(
    transaction  : TransactionEntity,
    categoryName : String,
    modifier     : Modifier = Modifier,
    /** Метка источника. `null` — не показывать (экран одного счёта, где она ничего не различает). */
    source       : TxSource? = null,
    /**
     * Операция прошла по КРЕДИТНОЙ карте, то есть потрачены деньги банка, а не свои.
     *
     * Это единственное свойство строки, которое нельзя вывести из неё самой: сумма, дата и
     * категория у покупки за свои и за кредитные одинаковы, а последствия — нет. Одна попадёт в
     * долг, по ней тикает беспроцентный период и однажды придёт обязательный платёж. Без пометки
     * в списке они неразличимы.
     */
    onCredit     : Boolean = false,
    onClick      : (() -> Unit)? = null,
) {
    // Зелёную полосу получает только доход. Расход НЕ помечается красным краем намеренно: в списке,
    // где расход — это почти каждая строка, красная огранка перестаёт что-либо выделять и экран
    // снова превращается в однородное полотно. Сумма справа и так красная (правило #2).
    val tone  = if (transaction.type == TransactionType.INCOME) FosTone.Positive else FosTone.Neutral
    val style = if (tone == FosTone.Neutral) FosCardStyle.Plain else FosCardStyle.Rail

    Row(
        modifier = modifier
            .fillMaxWidth()
            .fosCardSurface(style, tone, FosDimens.RadiusCardSmall)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(FosDimens.CardPaddingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Left — merchant + meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Разобранное имя, а не строка биллинга: «RECR GOOGLE *ChatGPT, 855-836-3987»
                // занимает всю ширину и не читается. Исходный текст никуда не делся — он лежит в
                // самой операции, виден в её карточке и по нему по-прежнему ищет поиск.
                text  = MerchantNames.display(transaction.merchant) ?: categoryName,
                style = FosType.TxMerchant,
                color = FosColors.TextPrimary,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                source?.let { src ->
                    val brand = bankBrand(src.bank)
                    if (src.prominent) {
                        // Редкий источник: цвет банка в заливке. Это единственная цветная деталь
                        // слева, и она честно означает «а вот это — не как обычно».
                        Text(
                            text     = src.label,
                            style    = FosType.MicroNum,
                            color    = brand.bg,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(FosDimens.RadiusChip))
                                .background(brand.bg.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    } else {
                        // Доминирующий источник: та же информация, но тоном подписи. Убрать её
                        // совсем нельзя — тогда на строке не написано, чья это карта, — а
                        // повторять цветом двадцать раз подряд значит выключить цвет как признак.
                        Text(
                            text     = src.label,
                            style    = FosType.MicroNum,
                            color    = FosColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (onCredit) {
                    // Плашка, а не цвет суммы: цвет уже занят направлением операции (правило #2),
                    // и красить кредитную покупку иначе значило бы сломать единственный признак,
                    // по которому расход отличается от дохода.
                    Text(
                        text     = "КРЕДИТКА",
                        style    = FosType.Micro,
                        color    = FosColors.Warning,
                        modifier = Modifier
                            .clip(RoundedCornerShape(FosDimens.RadiusChip))
                            .background(FosColors.Warning.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text  = "${FosFormatter.dayLabelYear(transaction.timestamp)} · ${transaction.description?.takeIf { it.isNotBlank() } ?: categoryName}",
                    style = FosType.Micro,
                    color = FosColors.TextSecondary,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Right — amount.
        // CRITICAL: expenses = Negative red, income = Positive green.
        // A TRANSFER is neither income nor expense → render neutral (never red, never green).
        val symbol = FosFormatter.currencySymbol(transaction.currency)
        when (transaction.type) {
            TransactionType.TRANSFER -> {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text  = "↔ ${FosFormatter.amount(kotlin.math.abs(transaction.amountKopecks), symbol)}",
                        style = FosType.TxAmount,
                        color = FosColors.TextPrimary,
                    )
                    if (transaction.goalId != null) {
                        Text(
                            text  = "→ в цель",
                            style = FosType.Micro,
                            color = FosColors.TextSecondary,
                        )
                    }
                }
            }
            else -> {
                val isExpense = transaction.type == TransactionType.EXPENSE
                val amtColor  = if (isExpense) FosColors.Negative else FosColors.Positive
                Text(
                    text  = FosFormatter.signedAmount(transaction.amountKopecks, symbol),
                    style = FosType.TxAmount,
                    color = amtColor,
                )
            }
        }
    }
}
