package com.kg.merapaisa.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Confirm split" hands back [convertedAmounts], which starts empty and is only filled once the
 * conversion effect has run — and that effect waits [CONVERSION_SETTLE_MS] before it fetches a
 * rate for anyone in a different currency. So there is a guaranteed window, right after the
 * screen opens, in which the button is enabled and the map behind it is still empty. Confirming
 * in that window recorded nothing at all and closed the flow, which reads as a saved split.
 */
@RunWith(AndroidJUnit4::class)
class SplitConfirmTimingTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var viewModel: MainViewModel

    /** A participant in a different currency, which is what makes the effect wait to convert. */
    private val foreignParticipant = PersonWithBalance(
        person = Person(id = 1, name = "Dollar", currency = "USD"),
        balanceMinor = 0L
    )

    @Before
    fun setUp() {
        viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    fun confirmingBeforeTheConversionResolvesCannotRecordAnEmptySplit() {
        var confirmed: Map<Long, Long>? = null

        // Hold the clock so the settle delay cannot elapse, reproducing the window exactly
        // rather than racing it.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SplitAdjustmentsScreen(
                viewModel = viewModel,
                amountMinor = 1_000_00,
                sourceCurrency = "INR",
                selectedPersons = listOf(foreignParticipant),
                includeMe = true,
                note = "",
                onNoteChange = {},
                onBack = {},
                onCancel = {},
                onConfirm = { confirmed = it }
            )
        }
        // Far short of the 400 ms the conversion effect waits before it fetches anything.
        compose.mainClock.advanceTimeBy(50)

        compose.onNodeWithText("Confirm split").performClick()
        compose.mainClock.advanceTimeBy(50)

        assertNull(
            "confirming before any amount has been converted must not record a split — " +
                "an empty or partial map silently writes nothing while the flow closes as if " +
                "it had saved. Got: $confirmed",
            confirmed
        )
    }

    /**
     * The other half of the same guard: blocking a premature confirm is only correct if a
     * settled one still goes through. Everyone shares the source currency here, so the screen
     * converts without a delay or a network call and the button must become live.
     */
    @Test
    fun confirmingOnceTheAmountsAreConvertedStillRecordsTheSplit() {
        var confirmed: Map<Long, Long>? = null
        val sameCurrency = PersonWithBalance(
            person = Person(id = 7, name = "Asha", currency = "INR"),
            balanceMinor = 0L
        )

        compose.setContent {
            SplitAdjustmentsScreen(
                viewModel = viewModel,
                amountMinor = 1_000_00,
                sourceCurrency = "INR",
                selectedPersons = listOf(sameCurrency),
                includeMe = true,
                note = "",
                onNoteChange = {},
                onBack = {},
                onCancel = {},
                onConfirm = { confirmed = it }
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Converting…").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithText("Confirm split").performClick()
        compose.waitForIdle()

        assertNotNull("a converted split must still be confirmable", confirmed)
        assertEquals("only the other person is recorded; \"You\" is dropped", setOf(7L), confirmed!!.keys)
        assertEquals("half of 1000.00 goes to the other person", 500_00L, confirmed!![7L])
    }
}
