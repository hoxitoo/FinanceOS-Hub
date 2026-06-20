package com.financeos.hub.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailSheet(
    bank         : String,
    accounts     : List<AccountEntity>,
    cards        : List<CardEntity>,
    sheetState   : SheetState,
    onDismiss    : () -> Unit,
    onAddAccount : (name: String, bank: String, cardMask: String?, balanceKopecks: Long, currency: String) -> Unit,
    onAddCard    : (card: CardEntity) -> Unit,
    onEditBalance: (account: AccountEntity, newKopecks: Long) -> Unit,
    onDelete     : (accountId: String) -> Unit,
) {
    var showAddAccount    by remember { mutableStateOf(false) }
    var addCardForAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var cardMaskInput     by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = FosColors.Surface,
    ) {
        LazyColumn(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = FosDimens.ScreenPadding)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap),
        ) {
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(bank, style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                    val totals = accounts.groupBy { it.currency }
                        .mapValues { (_, l) -> l.sumOf { it.balanceKopecks } }
                    Column(horizontalAlignment = Alignment.End) {
                        totals.entries.take(3).forEach { (cur, kopecks) ->
                            Text(
                                FosFormatter.amount(kopecks, FosFormatter.currencySymbol(cur)),
                                style = FosType.BodySemi,
                                color = FosColors.TextPrimary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = FosColors.Border)
            }

            items(accounts, key = { it.id }) { account ->
                val accountCards = cards.filter { it.accountId == account.id }
                AccountRow(
                    account       = account,
                    cards         = accountCards,
                    onAddCard     = { addCardForAccount = account; cardMaskInput = "" },
                    onEditBalance = onEditBalance,
                    onDelete      = onDelete,
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = { showAddAccount = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(FosDimens.RadiusCard),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = FosColors.Positive,
                        contentColor   = FosColors.Background,
                    ),
                ) { Text("+ Добавить счёт", style = FosType.BodySemi) }
            }
        }
    }

    // Add card dialog
    addCardForAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { addCardForAccount = null },
            containerColor   = FosColors.Surface,
            title   = {
                Text(
                    "Карта к «${account.name}»",
                    style = FosType.BodySemi,
                    color = FosColors.TextPrimary,
                )
            },
            text    = {
                OutlinedTextField(
                    value         = cardMaskInput,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) cardMaskInput = it },
                    label         = { Text("Последние 4 цифры", style = FosType.Label) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = FosColors.Info,
                        unfocusedBorderColor = FosColors.BorderStrong,
                        focusedLabelColor    = FosColors.Info,
                        unfocusedLabelColor  = FosColors.TextMuted,
                        cursorColor          = FosColors.Info,
                        focusedTextColor     = FosColors.TextPrimary,
                        unfocusedTextColor   = FosColors.TextPrimary,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = cardMaskInput.length == 4,
                    onClick = {
                        onAddCard(
                            CardEntity(
                                id          = UUID.randomUUID().toString(),
                                accountId   = account.id,
                                cardMask    = cardMaskInput,
                            )
                        )
                        addCardForAccount = null
                    },
                ) { Text("Добавить", color = FosColors.Positive) }
            },
            dismissButton = {
                TextButton(onClick = { addCardForAccount = null }) {
                    Text("Отмена", color = FosColors.TextSecondary)
                }
            },
        )
    }

    // Add account sheet for this bank
    if (showAddAccount) {
        val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AddAccountSheet(
            sheetState  = addSheetState,
            initialBank = bank,
            onDismiss   = { showAddAccount = false },
            onSave      = { name, b, mask, kopecks, currency ->
                onAddAccount(name, b, mask, kopecks, currency)
                showAddAccount = false
            },
        )
    }
}

@Composable
private fun AccountRow(
    account      : AccountEntity,
    cards        : List<CardEntity>,
    onAddCard    : () -> Unit,
    onEditBalance: (AccountEntity, Long) -> Unit,
    onDelete     : (String) -> Unit,
) {
    var editText by remember { mutableStateOf("") }
    var showEdit by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Surface2)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showEdit = true
                    editText = (account.balanceKopecks / 100).toString()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column {
                Text(account.name, style = FosType.BodySemi, color = FosColors.TextPrimary)
                Text(
                    FosFormatter.amount(account.balanceKopecks, FosFormatter.currencySymbol(account.currency)),
                    style = FosType.SmallBold,
                    color = if (account.balanceKopecks >= 0) FosColors.TextPrimary else FosColors.Negative,
                )
            }
            TextButton(
                onClick        = onAddCard,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("+ Карта", style = FosType.Label, color = FosColors.Info)
            }
        }
        // Card chips
        val allMasks = listOfNotNull(account.cardMask) + cards.map { it.cardMask }
        if (allMasks.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allMasks.forEach { mask ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(FosColors.Surface)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("•• $mask", style = FosType.Micro, color = FosColors.TextMuted)
                    }
                }
            }
        }
    }

    if (showEdit) {
        val kopecks = editText.replace(",", ".").toDoubleOrNull()
            ?.let { (it * 100).toLong() } ?: account.balanceKopecks
        AlertDialog(
            onDismissRequest = { showEdit = false },
            containerColor   = FosColors.Surface,
            title   = {
                Text(account.name, style = FosType.BodySemi, color = FosColors.TextPrimary)
            },
            text    = {
                OutlinedTextField(
                    value           = editText,
                    onValueChange   = { editText = it },
                    label           = {
                        Text(
                            "Баланс, ${FosFormatter.currencySymbol(account.currency)}",
                            style = FosType.Label,
                        )
                    },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = FosColors.Info,
                        unfocusedBorderColor = FosColors.BorderStrong,
                        focusedLabelColor    = FosColors.Info,
                        unfocusedLabelColor  = FosColors.TextMuted,
                        cursorColor          = FosColors.Info,
                        focusedTextColor     = FosColors.TextPrimary,
                        unfocusedTextColor   = FosColors.TextPrimary,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onEditBalance(account, kopecks); showEdit = false }) {
                    Text("Сохранить", color = FosColors.Positive)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onDelete(account.id); showEdit = false }) {
                        Text("Удалить", color = FosColors.Negative)
                    }
                    TextButton(onClick = { showEdit = false }) {
                        Text("Отмена", color = FosColors.TextSecondary)
                    }
                }
            },
        )
    }
}
