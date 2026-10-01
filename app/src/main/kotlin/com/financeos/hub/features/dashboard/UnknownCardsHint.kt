package com.financeos.hub.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard

/**
 * «Есть операции с карт, которых нет в приложении» (инвариант #45).
 *
 * Операция с незнакомой карты больше не ложится на единственный счёт своего банка — её «Остаток»
 * переписывал бы чужой баланс. Она остаётся без счёта, и без этой подсказки человек не узнал бы,
 * почему баланс не двигается: операции в списке есть, а на карточке счёта — нет.
 *
 * Касание номера открывает новый счёт с этим номером. Карта того же счёта (вторая карта к одному
 * счёту) добавляется в карточке счёта — об этом сказано в подписи. Добавленная карта забирает свои
 * операции сама: и висящие без счёта, и лёгшие раньше на чужой счёт.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnknownCardsHint(
    masks    : List<String>,
    onAdd    : (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier            = Modifier.fillMaxWidth().fosCard(FosCardStyle.Outline, FosTone.Info),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Карты, которых нет в приложении", style = FosType.BodySemi, color = FosColors.TextPrimary)
        Text(
            "Операции по ним видны в истории, но не входят ни в один счёт и не меняют баланс. " +
                "Нажмите на номер, чтобы завести счёт, или добавьте карту к уже заведённому счёту " +
                "в его карточке — операции перейдут на него сами.",
            style = FosType.Label,
            color = FosColors.TextSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            masks.forEach { mask ->
                AssistChip(
                    onClick = { onAdd(mask) },
                    label   = {
                        Text(
                            "+ •• $mask",
                            style = FosType.Label.merge(TextStyle(fontFeatureSettings = "tnum")),
                        )
                    },
                    shape   = RoundedCornerShape(FosDimens.RadiusChip),
                    colors  = AssistChipDefaults.assistChipColors(labelColor = FosColors.Info),
                )
            }
        }
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Не добавлять", style = FosType.Label, color = FosColors.TextMuted)
        }
    }
}
