package com.financeos.hub.features.credit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.credit.debtKopecks
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.features.dashboard.CreditTermsFields
import com.financeos.hub.features.dashboard.accountSheetFieldColors
import com.financeos.hub.features.dashboard.sanitizeDayInput
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

/**
 * Edits a credit card's terms, plus a manual debt correction.
 *
 * The debt field matters more than it looks: until the parser can read Сбер's credit-card pushes,
 * the balance only moves by transaction deltas, so an occasional hand correction is the escape
 * hatch that keeps the number honest. It is entered as a positive amount — the sign convention
 * belongs to the storage layer, not to the person typing.
 *
 * The five term fields are the same composable the "new account" sheet uses, so a limit means the
 * same thing whether it was set at creation or edited later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditTermsSheet(
    account   : AccountEntity,
    sheetState: SheetState,
    onDismiss : () -> Unit,
    onSave    : (debtKopecks: Long, limitKopecks: Long?, aprBp: Int?, statementDay: Int?, dueDays: Int?, minPaymentBp: Int?) -> Unit,
) {
    // Keyed on the account id: reusing this sheet for a second card must not show the first card's
    // numbers (the recurring "sheet shows the previous item" defect).
    var debtText by remember(account.id) {
        mutableStateOf(FosFormatter.amountInput(account.debtKopecks))
    }
    var limitText by remember(account.id) {
        mutableStateOf(account.creditLimitKopecks?.let { FosFormatter.amountInput(it) } ?: "")
    }
    var aprText by remember(account.id) {
        mutableStateOf(account.aprBp?.let { FosFormatter.amountInput(it.toLong()) } ?: "")
    }
    var statementDayText by remember(account.id) { mutableStateOf(account.statementDay?.toString() ?: "") }
    var dueDaysText      by remember(account.id) { mutableStateOf(account.dueDays?.toString() ?: "") }
    var minPaymentText   by remember(account.id) {
        mutableStateOf(account.minPaymentBp?.let { FosFormatter.amountInput(it.toLong()) } ?: "")
    }

    val currencySymbol = FosFormatter.currencySymbol(account.currency)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = FosColors.Surface,
        contentColor     = FosColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FosDimens.ScreenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
        ) {
            Text(account.name, style = FosType.ScreenTitle, color = FosColors.TextPrimary)

            OutlinedTextField(
                value                = debtText,
                onValueChange        = { debtText = FosFormatter.sanitizeAmountInput(it, allowNegative = false) },
                visualTransformation = AmountVisualTransformation,
                label                = { Text("Текущий долг, $currencySymbol", style = FosType.Label) },
                supportingText       = {
                    Text(
                        "Сколько сейчас должны банку",
                        style = FosType.Micro,
                        color = FosColors.TextMuted,
                    )
                },
                singleLine           = true,
                keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                colors               = accountSheetFieldColors(),
                modifier             = Modifier.fillMaxWidth(),
            )

            CreditTermsFields(
                currencySymbol   = currencySymbol,
                limitText        = limitText,
                onLimitChange    = { limitText = FosFormatter.sanitizeAmountInput(it, allowNegative = false) },
                aprText          = aprText,
                onAprChange      = { aprText = FosFormatter.sanitizeAmountInput(it, allowNegative = false) },
                statementDayText = statementDayText,
                onStatementDayChange = { statementDayText = sanitizeDayInput(it, max = 31) },
                dueDaysText      = dueDaysText,
                onDueDaysChange  = { dueDaysText = sanitizeDayInput(it, max = 90) },
                minPaymentText   = minPaymentText,
                onMinPaymentChange = { minPaymentText = FosFormatter.sanitizeAmountInput(it, allowNegative = false) },
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick  = {
                    onSave(
                        FosFormatter.parseAmountInput(debtText) ?: 0L,
                        FosFormatter.parseAmountInput(limitText),
                        // parseAmountInput scales by 100 and rounds — "29,8" → 2980 basis points.
                        FosFormatter.parseAmountInput(aprText)?.toInt(),
                        statementDayText.toIntOrNull()?.takeIf { it in 1..31 },
                        dueDaysText.toIntOrNull()?.takeIf { it in 1..90 },
                        FosFormatter.parseAmountInput(minPaymentText)?.toInt(),
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(FosDimens.RadiusCard),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = FosColors.Positive,
                    contentColor   = FosColors.Background,
                ),
            ) {
                Text("Сохранить", style = FosType.BodySemi)
            }
        }
    }
}
