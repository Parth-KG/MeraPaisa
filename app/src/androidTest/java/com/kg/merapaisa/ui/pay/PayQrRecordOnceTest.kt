package com.kg.merapaisa.ui.pay

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Show QR's Record answers once. The sheet slides away after the tap, and a second tap in that time
 * recorded the payment again, so someone who owed you ₹500 ended up owed ₹500 by you.
 */
@RunWith(AndroidJUnit4::class)
class PayQrRecordOnceTest {

    @get:Rule
    val compose = createComposeRule()

    private var paid = 0
    private var notYet = 0

    private fun open() {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    PayQrSheet(
                        personName = "Asha",
                        amountMinor = 500_00,
                        link = "upi://pay?pa=parth@okaxis&am=500.00&cu=INR",
                        myUpiId = "parth@okaxis",
                        onNotYet = { notYet++ },
                        onPaid = { paid++ }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun twoQuickTapsOnRecordRecordOnce() {
        open()
        compose.onNodeWithText("Record ₹500").performClick()
        compose.onNodeWithText("Record ₹500").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(1, paid)
        assertEquals(0, notYet)
    }

    @Test
    fun notYetAfterRecordDoesNotAlsoClose() {
        open()
        compose.onNodeWithText("Record ₹500").performClick()
        compose.onNodeWithText("Not yet").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(1, paid)
        assertEquals(0, notYet)
    }
}
