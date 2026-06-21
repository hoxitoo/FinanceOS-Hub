package com.financeos.hub.features.onboarding

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.done) {
        if (state.done) onFinished()
    }

    // Request both SMS permissions together; the system dialog fires once for both.
    val smsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val readGranted = perms[android.Manifest.permission.READ_SMS] == true
        if (readGranted) vm.onSmsPermissionGranted()
        else             vm.onPermissionDenied()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .padding(FosDimens.ScreenPadding),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = "💰",
            style     = FosType.HeroLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(FosDimens.SectionGap))
        Text(
            text      = "FinanceOS",
            style     = FosType.ScreenTitle,
            color     = FosColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(FosDimens.ItemGap))
        Text(
            text      = "Полный контроль над финансами.\nАвтоматически, из SMS.",
            style     = FosType.Body,
            color     = FosColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))

        when (state.step) {
            OnboardingStep.WELCOME -> WelcomeStep(
                permissionDenied    = state.permissionDenied,
                onRequestPermission = {
                    val alreadyGranted = ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.READ_SMS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (alreadyGranted) {
                        vm.onSmsPermissionGranted()
                    } else {
                        smsLauncher.launch(arrayOf(
                            android.Manifest.permission.READ_SMS,
                            android.Manifest.permission.RECEIVE_SMS,
                        ))
                    }
                },
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                onSkip = vm::onSkip,
            )
            OnboardingStep.IMPORT  -> ImportStep(progress = state.importProgress)
            OnboardingStep.DONE    -> {}
        }
    }
}

@Composable
private fun WelcomeStep(
    permissionDenied    : Boolean,
    onRequestPermission : () -> Unit,
    onOpenSettings      : () -> Unit,
    onSkip              : () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick  = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(FosDimens.RadiusButton),
            colors = ButtonDefaults.buttonColors(
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
            ),
        ) {
            Text("Разрешить доступ к SMS", style = FosType.BodySemi)
        }
        if (permissionDenied) {
            Spacer(Modifier.height(12.dp))
            Text(
                text      = "Доступ к SMS отклонён. Включите разрешение в настройках приложения.",
                style     = FosType.Body,
                color     = FosColors.Negative,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Открыть настройки", style = FosType.BodySemi, color = FosColors.Info)
            }
        }
        Spacer(Modifier.height(FosDimens.ItemGap))
        TextButton(onClick = onSkip) {
            Text("Пропустить", style = FosType.Body, color = FosColors.TextMuted)
        }
    }
}

@Composable
private fun ImportStep(progress: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = "Импортирую SMS…",
            style = FosType.SubHeader,
            color = FosColors.TextPrimary,
        )
        Spacer(Modifier.height(FosDimens.ItemGap))
        Text(
            text  = "${(progress * 100).toInt()}%",
            style = FosType.CardAmount,
            color = FosColors.Positive,
        )
    }
}
