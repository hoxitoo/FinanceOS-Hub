package com.financeos.hub.features.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPdfSheet(
    vm         : TransactionsViewModel,
    sheetState : SheetState,
    onDismiss  : () -> Unit,
) {
    val pdfState by vm.pdfImportState.collectAsState()

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { vm.importPdf(it) }
    }

    ModalBottomSheet(
        onDismissRequest  = {
            vm.dismissPdfResult()
            onDismiss()
        },
        sheetState        = sheetState,
        containerColor    = FosColors.Surface,
        dragHandle        = null,
    ) {
        Column(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(FosDimens.ScreenPadding)
                .padding(bottom = 24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = "Импорт выписки PDF",
                style = FosType.ScreenTitle,
                color = FosColors.TextPrimary,
            )

            when (val s = pdfState) {
                is PdfImportState.Idle -> {
                    IdleContent(onPickFile = { pdfPicker.launch("application/pdf") })
                }

                is PdfImportState.Loading -> {
                    LoadingContent()
                }

                is PdfImportState.Success -> {
                    SuccessContent(
                        result    = s.result,
                        onDismiss = {
                            vm.dismissPdfResult()
                            onDismiss()
                        },
                        onImportMore = {
                            vm.dismissPdfResult()
                            pdfPicker.launch("application/pdf")
                        },
                    )
                }

                is PdfImportState.Error -> {
                    ErrorContent(
                        message  = s.message,
                        onRetry  = { pdfPicker.launch("application/pdf") },
                        onDismiss = {
                            vm.dismissPdfResult()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(onPickFile: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FosDimens.RadiusCard))
                .border(1.dp, FosColors.Border, RoundedCornerShape(FosDimens.RadiusCard))
                .background(FosColors.Surface2)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("📄", style = FosType.HeroAmount)
                Text(
                    text      = "Загрузите выписку из банка\nв формате PDF",
                    style     = FosType.Body,
                    color     = FosColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = "Сбербанк · Т-Банк · Альфа-Банк\nВТБ · Газпромбанк и другие",
                    style     = FosType.Micro,
                    color     = FosColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Button(
            onClick  = onPickFile,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
            ),
            shape = RoundedCornerShape(FosDimens.RadiusCard),
        ) {
            Text("Выбрать PDF файл", style = FosType.Label)
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 24.dp),
    ) {
        CircularProgressIndicator(
            color     = FosColors.Positive,
            modifier  = Modifier.size(48.dp),
        )
        Text(
            text  = "Читаем PDF...",
            style = FosType.Body,
            color = FosColors.TextSecondary,
        )
    }
}

@Composable
private fun SuccessContent(
    result     : PdfImportResult,
    onDismiss  : () -> Unit,
    onImportMore: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("✅", style = FosType.HeroAmount)
        Text(
            text  = "Найдено: ${result.found}  ·  Добавлено: ${result.inserted}",
            style = FosType.Body,
            color = FosColors.TextPrimary,
        )
        if (result.found > result.inserted) {
            Text(
                text  = "${result.found - result.inserted} уже есть в базе — пропущены",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
            ),
            shape = RoundedCornerShape(FosDimens.RadiusCard),
        ) {
            Text("Готово", style = FosType.Label)
        }

        TextButton(onClick = onImportMore) {
            Text("Импортировать ещё PDF", style = FosType.Label, color = FosColors.Info)
        }
    }
}

@Composable
private fun ErrorContent(
    message  : String,
    onRetry  : () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("⚠️", style = FosType.HeroAmount)
        Text(
            text      = "Не удалось прочитать PDF",
            style     = FosType.Body,
            color     = FosColors.Negative,
        )
        Text(
            text      = message,
            style     = FosType.Micro,
            color     = FosColors.TextMuted,
            textAlign = TextAlign.Center,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("Отмена", style = FosType.Label, color = FosColors.TextSecondary)
            }
            Button(
                onClick  = onRetry,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = FosColors.Negative,
                    contentColor   = FosColors.Background,
                ),
                shape = RoundedCornerShape(FosDimens.RadiusCard),
            ) {
                Text("Выбрать другой", style = FosType.Label)
            }
        }
    }
}
