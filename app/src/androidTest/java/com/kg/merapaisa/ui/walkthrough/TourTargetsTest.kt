package com.kg.merapaisa.ui.walkthrough

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.PersonRow
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The names an arrow points at are only collected inside a walkthrough page. In the app the same
 * rows carry the same names and record nothing.
 */
@RunWith(AndroidJUnit4::class)
class TourTargetsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onlyTheRowInsideAPageIsFound() {
        val targets = TourTargets()
        val theme = themes.first()
        var page: LayoutCoordinates? = null
        var outside: LayoutCoordinates? = null
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    Column(Modifier.onGloballyPositioned { page = it }) {
                        // As on Balances: no page around it.
                        Box(Modifier.onGloballyPositioned { outside = it }) {
                            Row(PersonWithBalance(Person(id = 2, name = "Bilal", currency = "INR"), -40_00))
                        }
                        CompositionLocalProvider(LocalTourTargets provides targets) {
                            Row(PersonWithBalance(Person(id = 1, name = "Asha", currency = "INR"), 1_200_00))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(setOf<Any>(TourId.PersonFigure, TourId.PersonHistory), targets.keys)
        compose.runOnIdle {
            // Both rows carry the names; only Asha's, below Bilal's, was recorded.
            val bilalBottom = page!!.localBoundingBoxOf(outside!!).bottom
            val figure = targets.boundsIn(page!!, TourId.PersonFigure)!!
            assertTrue("the figure found is Asha's, not Bilal's", figure.top >= bilalBottom)
        }
    }

    @androidx.compose.runtime.Composable
    private fun Row(person: PersonWithBalance) = PersonRow(
        person = person,
        isSelected = false,
        onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {}, onEditClick = {},
        onSettleUp = {}, onShareSummary = {}, onShareLedger = {}, onMoveDebt = {}
    )
}
