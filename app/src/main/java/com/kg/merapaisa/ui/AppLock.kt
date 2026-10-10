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
 * The app lock leans entirely on what the phone already trusts: fingerprint, face, or the
 * device PIN/pattern as a fallback. Nothing is stored or verified by this app, so there is no
 * secret of ours to leak and no PIN screen of our own to get wrong.
 */
private const val ALLOWED_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** True when this device can actually prompt for something. Otherwise the toggle is pointless. */
fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * Shows the system authentication sheet to open a locked ledger. [onUnlocked] runs only on
 * success; a cancel or a failure leaves the caller locked, which is the safe direction to fail in.
 */
fun promptToUnlock(activity: FragmentActivity, onUnlocked: () -> Unit) =
    askForCredential(activity, "Unlock Mera Paisa", "Your ledger is locked", onConfirmed = onUnlocked)

/**
 * Shows the system authentication sheet with [title] and [subtitle]: fingerprint, face, or the
 * phone's own PIN or pattern. [onConfirmed] runs only on success. [onEnded] runs once the sheet has
 * gone, either way, and before [onConfirmed] on a success.
 */
fun askForCredential(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onConfirmed: () -> Unit,
    onEnded: (confirmed: Boolean) -> Unit = {}
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onEnded(true)
                onConfirmed()
            }

            // A cancel, a lockout, or the sheet dismissed: over, with nothing proven. A single
            // failed finger is not the end and arrives elsewhere.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onEnded(false)
            }
        }
    )

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()
    )
}

/** What the phone's prompt says when the lock is turned on: a title, and a line under it. */
internal val LOCK_ON_PROMPT = "Turn on the app lock" to "Your ledger will ask for this before it opens"

/** What it says when the lock is turned off. */
internal val LOCK_OFF_PROMPT = "Turn off the app lock" to "Your ledger will open without asking"

/**
 * Turning the lock on or off, asked of the phone first, both ways.
 *
 * The switch used to flip at a tap. Off, that let anyone holding the phone with the ledger open,
 * or inside the grace period, switch the lock off without a fingerprint. On, nothing showed the
 * lock could be passed before the ledger depended on it.
 *
 * [ask] shows the prompt and calls back only on a success; [store] writes the setting. A cancel or
 * a failure writes nothing, so the switch, which shows the stored value, stays where it was.
 */
fun changeAppLock(
    enabled: Boolean,
    ask: (title: String, subtitle: String, onConfirmed: () -> Unit) -> Unit,
    store: (Boolean) -> Unit
) {
    val (title, subtitle) = if (enabled) LOCK_ON_PROMPT else LOCK_OFF_PROMPT
    ask(title, subtitle) { store(enabled) }
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
class AppLockViewModel(
    /** The phone's clock since boot. Passed in so a test can move it. */
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
) : ViewModel() {

    /** Starts locked. A ledger showing before authentication would defeat the point. */
    var locked by mutableStateOf(true)
        private set

    private var backgroundedAt = 0L

    /** See [onAskStarted]. */
    private var asking = false
    private var stoppedWhileAsking = false

    fun onUnlocked() {
        locked = false
    }

    fun onStopped() {
        backgroundedAt = now()
        if (asking) stoppedWhileAsking = true
    }

    /**
     * Re-locks only after the app has actually been away a while, so switching out to copy a
     * number and straight back does not demand a fingerprint every time. Only ever locks: a
     * freshly built instance is already locked, so a device whose uptime is below the grace
     * period cannot be talked into starting unlocked.
     *
     * Coming back from the app's own prompt decides nothing here; [onAskEnded] does. Once only,
     * so a prompt whose answer never arrives cannot keep the next return from locking.
     */
    fun onStarted() {
        if (asking) {
            asking = false
            return
        }
        if (now() - backgroundedAt > SecurityStore.GRACE_MILLIS) {
            locked = true
        }
    }

    /**
     * The app has asked for the fingerprint or PIN itself, to turn the lock on or off.
     *
     * A PIN is typed on the system's own screen, which stops this activity. One typed slowly came
     * back past the grace period, and the ledger locked under the very prompt that was proving who
     * held the phone: turning the lock on asked twice, and turning it off showed the lock screen
     * where the ledger should have been, and never wrote the change.
     */
    fun onAskStarted() {
        asking = true
        stoppedWhileAsking = false
    }

    /**
     * A confirmed prompt is an unlock, and restarts the clock the grace period runs on, so a return
     * that is reported after the answer cannot lock behind it. A cancelled one locks if the app
     * was away long enough.
     */
    fun onAskEnded(confirmed: Boolean) {
        val away = stoppedWhileAsking && now() - backgroundedAt > SecurityStore.GRACE_MILLIS
        asking = false
        stoppedWhileAsking = false
        when {
            confirmed -> {
                locked = false
                backgroundedAt = now()
            }
            away -> locked = true
        }
    }
}
