package com.kg.merapaisa

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kg.merapaisa.ui.LockedScreen
import com.kg.merapaisa.ui.MainScreen
import com.kg.merapaisa.ui.MainViewModel
import com.kg.merapaisa.ui.promptToUnlock
import com.kg.merapaisa.ui.theme.MeraPaisaTheme

/**
 * A FragmentActivity rather than a plain ComponentActivity because BiometricPrompt needs one.
 */
class MainActivity : FragmentActivity() {

    /** Starts locked. A ledger showing before authentication would defeat the point. */
    private val locked = mutableStateOf(true)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash window covers startup on its own; no artificial delay on top.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = this
            val themeName by ThemeStore.getTheme(context).collectAsState(initial = "Midnight")
            val currentTheme = getThemeByName(themeName)
            val lockEnabled by SecurityStore.isAppLockEnabled(context).collectAsState(initial = false)
            val showLock = lockEnabled && locked.value

            // Keep the ledger out of the recents thumbnail and screenshots while locking is on.
            LaunchedEffect(lockEnabled) {
                if (lockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Ask as soon as we are locked, so the prompt appears without the user tapping.
            LaunchedEffect(showLock) {
                if (showLock) promptToUnlock(this@MainActivity) { locked.value = false }
            }

            MeraPaisaTheme(theme = currentTheme) {
                CompositionLocalProvider(LocalAppTheme provides currentTheme) {
                    if (showLock) {
                        LockedScreen(
                            onUnlock = { promptToUnlock(this@MainActivity) { locked.value = false } }
                        )
                    } else {
                        val viewModel: MainViewModel = viewModel()
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock only after the app has actually been away a while, so switching out to copy
        // a number and straight back does not demand a fingerprint every time.
        if (SystemClock.elapsedRealtime() - backgroundedAt > SecurityStore.GRACE_MILLIS) {
            locked.value = true
        }
    }
}
