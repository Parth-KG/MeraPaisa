package com.kg.merapaisa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A full screen laid over the ledger must not pass taps through to it.
 *
 * Screens are switched on by state and drawn on top of the people list. Tapping an empty patch of
 * a person's history, beside its title, opened Settings from behind it. Found on a phone.
 */
@RunWith(AndroidJUnit4::class)
class FullScreenCoversLedgerTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aTapOnEmptySpaceNeverReachesWhatIsBehind() {
        var behind = 0
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    Box(Modifier.fillMaxSize()) {
                        // Stands in for the ledger: a button filling the whole screen underneath.
                        Button(onClick = { behind++ }, modifier = Modifier.fillMaxSize()) {
                            Text("Behind")
                        }
                        ScreenFrame(title = "In front", onBack = {}) { }
                    }
                }
            }
        }

        // The middle of an empty screen: nothing of the frame's own is there to take the tap.
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()

        assertEquals("a tap on the screen in front reached the ledger behind it", 0, behind)
    }
}
