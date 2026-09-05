package com.kg.merapaisa.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat

/**
 * The app lock leans entirely on what the phone already trusts — fingerprint, face, or the
 * device PIN/pattern as a fallback. Nothing is stored or verified by this app, so there is no
 * secret of ours to leak and no PIN screen of our own to get wrong.
 */
private const val ALLOWED_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** True when this device can actually prompt for something — otherwise the toggle is pointless. */
fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * Shows the system authentication sheet. [onUnlocked] runs only on success; a cancel or a
 * failure leaves the caller locked, which is the safe direction to fail in.
 */
fun promptToUnlock(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
        }
    )

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Mera Paisa")
            .setSubtitle("Your ledger is locked")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()
    )
}
