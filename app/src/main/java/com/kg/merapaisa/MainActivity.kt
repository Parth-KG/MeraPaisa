package com.kg.merapaisa

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
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

    /**
     * A share link that arrived from outside the app, held until the ledger is actually on screen.
     *
     * It cannot be handed straight to the ViewModel: that would show an import dialog over the lock
     * screen, so tapping a link would reveal who you owe money to without a fingerprint. The
     * delivery happens inside the unlocked branch of [setContent] below, which is what makes the
     * lock apply to imports as well.
     */
    private var pendingShareLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash window covers startup on its own; no artificial delay on top.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Bar styling is applied from the chosen theme once it is known, in the effect below.
        // A bare enableEdgeToEdge() here would take SystemBarStyle.auto, which reads the phone's
        // dark mode rather than the app's theme: Paper on a phone set to dark drew white icons
        // on white paper, and Midnight on a phone set to light drew dark icons on near black.
        enableEdgeToEdge()

        pendingShareLink = shareLinkFrom(intent)

        // Start secure and relax later. The lock setting is read from disk, so for the first
        // frames we do not yet know whether this ledger is meant to be private; assuming it is
        // not would put it in the recents thumbnail of everyone who turned the lock on.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            val context = this
            val themeName by ThemeStore.getTheme(context).collectAsState(initial = "Midnight")
            val currentTheme = getThemeByName(themeName)
            // null while DataStore is still answering. Neither enabled nor disabled yet.
            val lockEnabled by SecurityStore.isAppLockEnabled(context).collectAsState(initial = null)
            val known = lockEnabled != null
            val showLock = lockEnabled == true && lock.locked

            // Hold the splash rather than show a ledger that may be meant to be behind a lock.
            splash.setKeepOnScreenCondition { !known }

            LaunchedEffect(lockEnabled) {
                if (lockEnabled == false) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            // Re-applied on every theme change, not once at startup. The theme is not known in
            // onCreate, since it arrives from DataStore, and switching theme in Settings used to
            // repaint the whole app while leaving the system bars as they were at process start.
            LaunchedEffect(currentTheme) {
                val bars = if (currentTheme.isDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
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

                            // Reached only once unlocked, so an incoming link waits for the
                            // fingerprint like everything else. Cleared immediately so a rotation
                            // does not re-open an import the user already dismissed.
                            LaunchedEffect(pendingShareLink) {
                                pendingShareLink?.let { link ->
                                    pendingShareLink = null
                                    viewModel.onShareLinkReceived(link)
                                }
                            }

                            MainScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    /**
     * The activity is `singleTask`, so a link tapped while the app is already open arrives here
     * rather than starting a second copy. Without this the link would be silently ignored.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShareLink = shareLinkFrom(intent)
    }

    /**
     * The link text, or null if this intent is not one. The whole URI is passed on, fragment
     * included: the payload lives in the fragment, so dropping it would leave an empty link.
     */
    private fun shareLinkFrom(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.toString() else null

    override fun onStop() {
        super.onStop()
        lock.onStopped()
    }

    override fun onStart() {
        super.onStart()
        lock.onStarted()
    }
}
