package com.kg.merapaisa

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kg.merapaisa.ui.AppLockViewModel
import com.kg.merapaisa.ui.LockedScreen
import com.kg.merapaisa.ui.MainScreen
import com.kg.merapaisa.ui.MainViewModel
import com.kg.merapaisa.ui.promptToUnlock
import com.kg.merapaisa.ui.theme.MeraPaisaTheme

/**
 * A FragmentActivity rather than a plain ComponentActivity because BiometricPrompt needs one.
 */
class MainActivity : FragmentActivity() {

    /**
     * Lock state lives in a ViewModel, not in a field here: an Activity field is rebuilt on
     * every rotation, which re-prompted for a fingerprint the user had just given.
     */
    private val lock: AppLockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash window covers startup on its own; no artificial delay on top.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start secure and relax later. The lock setting is read from disk, so for the first
        // frames we do not yet know whether this ledger is meant to be private; assuming it is
        // not would put it in the recents thumbnail of everyone who turned the lock on.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            val context = this
            val themeName by ThemeStore.getTheme(context).collectAsState(initial = "Midnight")
            val currentTheme = getThemeByName(themeName)
            // null while DataStore is still answering — neither enabled nor disabled yet.
            val lockEnabled by SecurityStore.isAppLockEnabled(context).collectAsState(initial = null)
            val known = lockEnabled != null
            val showLock = lockEnabled == true && lock.locked

            // Hold the splash rather than show a ledger that may be meant to be behind a lock.
            splash.setKeepOnScreenCondition { !known }

            LaunchedEffect(lockEnabled) {
                if (lockEnabled == false) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            // Ask as soon as we are locked, so the prompt appears without the user tapping.
            LaunchedEffect(showLock) {
                if (showLock) promptToUnlock(this@MainActivity) { lock.onUnlocked() }
            }

            MeraPaisaTheme(theme = currentTheme) {
                CompositionLocalProvider(LocalAppTheme provides currentTheme) {
                    when {
                        !known -> Unit
                        showLock -> LockedScreen(
                            onUnlock = { promptToUnlock(this@MainActivity) { lock.onUnlocked() } }
                        )
                        else -> {
                            val viewModel: MainViewModel = viewModel()
                            MainScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lock.onStopped()
    }

    override fun onStart() {
        super.onStart()
        lock.onStarted()
    }
}
