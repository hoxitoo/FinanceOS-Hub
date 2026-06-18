package com.financeos.hub.features.onboarding

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    LaunchedEffect(state.done) {
        if (state.done) onFinished()
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
            OnboardingStep.WELCOME -> WelcomeStep(onNext = vm::onRequestSmsPermission)
            OnboardingStep.IMPORT  -> ImportStep(progress = state.importProgress)
            OnboardingStep.DONE    -> {}
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Button(
        onClick = onNext,
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
