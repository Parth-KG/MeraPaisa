package com.kg.merapaisa.ui

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.core.content.ContextCompat
import com.kg.merapaisa.SecurityStore

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

/**
 * Holds whether the ledger is currently unlocked.
 *
 * A ViewModel rather than an Activity field because its lifetime is exactly the lock's
 * semantics: it survives a configuration change, and it dies with the process. Rotating the
 * phone is not leaving the app, so it must not re-prompt; the process going away is, so it
 * must. Held on the Activity, [locked] was rebuilt as `true` on every rotation and
 * [SecurityStore.GRACE_MILLIS] could not help, because `backgroundedAt` was rebuilt as `0`
 * and any phone up more than the grace period then read as "away long enough, re-lock".
 *
 * Deliberately not a `Bundle`: the system may restore saved instance state after process
 * death, which would hand back an unlocked ledger without ever asking.
 */
class AppLockViewModel : ViewModel() {

    /** Starts locked. A ledger showing before authentication would defeat the point. */
    var locked by mutableStateOf(true)
        private set

    private var backgroundedAt = 0L

    fun onUnlocked() {
        locked = false
    }

    fun onStopped() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Re-locks only after the app has actually been away a while, so switching out to copy a
     * number and straight back does not demand a fingerprint every time. Only ever locks: a
     * freshly built instance is already locked, so a device whose uptime is below the grace
     * period cannot be talked into starting unlocked.
     */
    fun onStarted() {
        if (SystemClock.elapsedRealtime() - backgroundedAt > SecurityStore.GRACE_MILLIS) {
            locked = true
        }
    }
}
