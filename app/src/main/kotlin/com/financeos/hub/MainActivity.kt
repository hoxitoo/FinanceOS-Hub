package com.financeos.hub

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.financeos.hub.navigation.FosRoute
import com.financeos.hub.ui.theme.FosTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var userPreferences: UserPreferences

    private var isLocked by mutableStateOf(true)
    private var promptActive = false
    private var pendingDeepRoute by mutableStateOf<String?>(null)
    // Cached so onStop can lock synchronously without launching a new coroutine that
    // may be cancelled before writing isLocked = true (race condition on rapid backgrounding).
    private var biometricEnabledCache = false

    // Device-credential (PIN/pattern/password) fallback when no biometric is enrolled.
    private val credentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        promptActive = false
        if (result.resultCode == RESULT_OK) isLocked = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepRoute = FosRoute.sanitizeDeepLink(intent.getStringExtra(NotificationHelper.EXTRA_ROUTE))
        setContent {
            FosTheme {
                if (isLocked) {
                    LockScreen(onUnlockRequested = { triggerAuth() })
                } else {
                    FosNavHost(initialDeepRoute = pendingDeepRoute)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Drive the deep link through state — never call setContent twice (it would leak
        // and recreate the whole NavHost, losing the back stack).
        FosRoute.sanitizeDeepLink(intent.getStringExtra(NotificationHelper.EXTRA_ROUTE))?.let {
            pendingDeepRoute = it
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            biometricEnabledCache = userPreferences.biometricEnabled.first()
            if (!biometricEnabledCache) {
                isLocked = false
                return@launch
            }
            if (isLocked && !promptActive) triggerAuth()
        }
    }

    override fun onStop() {
        super.onStop()
        // Use the cached value to avoid launching a coroutine that can be cancelled before
        // setting isLocked = true, which would leave the screen unlocked after backgrounding.
        if (biometricEnabledCache) {
            isLocked = true
            promptActive = false
        }
    }

    private fun triggerAuth() {
        if (BiometricHelper.canAuthenticate(this)) {
            promptActive = true
            BiometricHelper.showPrompt(
                activity  = this,
                onSuccess = { isLocked = false; promptActive = false },
                onError   = { _ -> promptActive = false },   // stay locked; user can retry
            )
            return
        }

        // No usable biometric. Do NOT silently unlock — fall back to the device credential
        // if the device has a secure lock; only release the lock when the device has none.
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard != null && keyguard.isDeviceSecure) {
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent(
                getString(R.string.lock_biometric_prompt_title),
                getString(R.string.lock_biometric_prompt_subtitle),
            )
            if (intent != null) {
                promptActive = true
                credentialLauncher.launch(intent)
            } else {
                isLocked = false
            }
        } else {
            // Device has no secure lock set — there is nothing for the app lock to enforce.
            isLocked = false
        }
    }
}
