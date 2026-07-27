package com.financeos.hub.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosType

@Composable
fun LockScreen(
    onUnlockRequested: () -> Unit,
    onUseDeviceCredential: () -> Unit = {},
) {
    // NOTE: no auto-trigger here. MainActivity fires the prompt itself, but only AFTER it has
    // confirmed the lock preference is on. Auto-triggering from composition used to race that
    // read and prompt for a fingerprint even when the setting was off.

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(FosColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔒", style = FosType.HeroAmount)
        Spacer(Modifier.height(16.dp))
        Text("FinanceOS", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Используйте биометрию для входа",
            style = FosType.Body,
            color = FosColors.TextMuted,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onUnlockRequested) {
            Text("Разблокировать", style = FosType.Label, color = FosColors.Info)
        }
        // Escape hatch: a damaged or unrecognised fingerprint must never mean losing access.
        TextButton(onClick = onUseDeviceCredential) {
            Text("Войти по PIN-коду устройства", style = FosType.Label, color = FosColors.TextSecondary)
        }
    }
}
