package com.moto.files.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Native biometric prompt with strict routing: a negative button ("Use app PIN") is always
 * set, so cancel / negative-button / lockout / errors all route to the app's OWN PIN screen.
 * The framework disallows device-credential fallback when a negative button is present,
 * so the OS lock screen is never shown.
 */
object BiometricGate {

    sealed interface Outcome {
        data object Success : Outcome
        data object UseAppPin : Outcome
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onUseAppPin: () -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            onUseAppPin()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val dialog = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) = onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onUseAppPin()
                }
            }
        )
        dialog.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Moto Files")
                .setSubtitle("Fingerprint or face")
                .setNegativeButtonText("Use app PIN")
                .setAllowedAuthenticators(BiometricPrompt.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }
}
