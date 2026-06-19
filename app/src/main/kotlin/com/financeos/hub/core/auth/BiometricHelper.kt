package com.financeos.hub.core.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    fun canAuthenticate(context: Context): Boolean {
        val result = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showPrompt(
        activity   : FragmentActivity,
        onSuccess  : () -> Unit,
        onError    : (Int) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationFailed() { /* wrong finger — prompt stays visible */ }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode)
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("FinanceOS")
            .setSubtitle("Подтвердите личность для доступа")
            .setNegativeButtonText("Отмена")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
