package com.kg.merapaisa.ui.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.Person
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Balances and expenses share one LazyColumn, so they share one key space. Person ids and
 * expense ids both count from 1, so keying either on a bare id made Compose throw
 * "Key 1 was already used" the moment a group's first expense was added.
 */
@RunWith(AndroidJUnit4::class)
class GroupDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val members = listOf(
        Person(id = 1, name = "Asha", currency = "INR"),
        Person(id = 2, name = "Bilal", currency = "INR")
    )

    @Test
    fun rendersWhenAnExpenseIdCollidesWithAPersonId() {
        compose.setContent {
            GroupDetailScreen(
                group = Group(id = 1, name = "Goa", currency = "INR"),
                members = members,
                expenses = listOf(
                    Expense(id = 1, groupId = 1, description = "Hotel", amountMinor = 500_00, paidByPersonId = 1),
                    Expense(id = 2, groupId = 1, description = "Cab", amountMinor = 200_00, paidByPersonId = 2)
                ),
                balances = listOf(MemberBalance(1, 250_00), MemberBalance(2, -250_00)),
                onBack = {},
                onAddExpense = {},
                onSettleUp = {},
                onDeleteExpense = {}
            )
        }

        compose.onNodeWithText("Hotel").assertIsDisplayed()
        compose.onNodeWithText("Asha").assertIsDisplayed()
    }

    @Test
    fun anEmptyGroupSaysSoRatherThanShowingNothing() {
        compose.setContent {
            GroupDetailScreen(
                group = Group(id = 1, name = "Goa", currency = "INR"),
                members = members,
                expenses = emptyList(),
                balances = listOf(MemberBalance(1, 0), MemberBalance(2, 0)),
                onBack = {},
                onAddExpense = {},
                onSettleUp = {},
                onDeleteExpense = {}
            )
        }

        compose.onNodeWithText("Everyone is square").assertIsDisplayed()
    }
}
