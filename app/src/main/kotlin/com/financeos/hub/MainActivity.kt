package com.financeos.hub

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.financeos.hub.core.auth.BiometricHelper
import com.financeos.hub.core.notifications.NotificationHelper
import com.financeos.hub.data.preferences.UserPreferences
import com.financeos.hub.features.auth.LockScreen
import com.financeos.hub.navigation.FosNavHost
import com.financeos.hub.ui.theme.FosTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var userPreferences: UserPreferences

    private var pendingDeepRoute: String? = null
    private var isLocked by mutableStateOf(true)
    private var promptActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepRoute = intent.getStringExtra(NotificationHelper.EXTRA_ROUTE)
        setContent {
            FosTheme {
                if (isLocked) {
                    LockScreen(onUnlockRequested = { triggerBiometric() })
                } else {
                    FosNavHost(initialDeepRoute = pendingDeepRoute)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val bioEnabled = userPreferences.biometricEnabled.first()
            if (!bioEnabled) {
                isLocked = false
                return@launch
            }
            if (isLocked && !promptActive) {
                triggerBiometric()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            if (userPreferences.biometricEnabled.first()) {
                isLocked = true
                promptActive = false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(NotificationHelper.EXTRA_ROUTE)?.let { route ->
            pendingDeepRoute = route
            setContent {
                FosTheme {
                    if (isLocked) {
                        LockScreen(onUnlockRequested = { triggerBiometric() })
                    } else {
                        FosNavHost(initialDeepRoute = route)
                    }
                }
            }
        }
    }

    private fun triggerBiometric() {
        if (!BiometricHelper.canAuthenticate(this)) {
            isLocked = false
            return
        }
        promptActive = true
        BiometricHelper.showPrompt(
            activity  = this,
            onSuccess = { isLocked = false; promptActive = false },
            onError   = { _ -> promptActive = false },
        )
    }
}
