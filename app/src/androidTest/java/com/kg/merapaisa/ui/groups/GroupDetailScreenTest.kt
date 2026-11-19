package com.kg.merapaisa.ui.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.Transfer
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
                transfers = listOf(Transfer(fromPersonId = 2, toPersonId = 1, amountMinor = 250_00)),
                simplifyDebts = true,
                onBack = {},
                onAddExpense = {},
                onSettleUp = {},
                onDeleteExpense = {},
                onSimplifyChange = {}
            )
        }

        compose.onNodeWithText("Hotel").assertIsDisplayed()
        compose.onNodeWithText("Asha").assertIsDisplayed()
        // The settle-up plan is the headline of this screen since v2.4, not a buried sheet.
        compose.onNodeWithText("Who pays whom").assertIsDisplayed()
        compose.onNodeWithText("Bilal pays Asha").assertIsDisplayed()
    }

    @Test
    fun anEmptyGroupSaysSoRatherThanShowingNothing() {
        compose.setContent {
            GroupDetailScreen(
                group = Group(id = 1, name = "Goa", currency = "INR"),
                members = members,
                expenses = emptyList(),
                balances = listOf(MemberBalance(1, 0), MemberBalance(2, 0)),
                transfers = emptyList(),
                simplifyDebts = true,
                onBack = {},
                onAddExpense = {},
                onSettleUp = {},
                onDeleteExpense = {},
                onSimplifyChange = {}
            )
        }

        // Renamed with the screen: the glossary word for a zero balance is "even".
        compose.onNodeWithText("Everyone is even").assertIsDisplayed()
    }

    private fun showYou(balances: List<MemberBalance>, transfers: List<Transfer>) {
        compose.setContent {
            GroupDetailScreen(
                group = Group(id = 1, name = "Goa", currency = "INR", simplifyDebts = false),
                members = listOf(
                    Person(id = 1, name = "You", currency = "INR", isSelf = true),
                    Person(id = 2, name = "Asha", currency = "INR"),
                    Person(id = 3, name = "Ravi", currency = "INR")
                ),
                expenses = listOf(
                    Expense(id = 1, groupId = 1, description = "Stay", amountMinor = 200_00, paidByPersonId = 1),
                    Expense(id = 2, groupId = 1, description = "Fuel", amountMinor = 200_00, paidByPersonId = 3)
                ),
                balances = balances,
                transfers = transfers,
                simplifyDebts = false,
                onBack = {},
                onAddExpense = {},
                onSettleUp = {},
                onDeleteExpense = {},
                onSimplifyChange = {},
                onMoveToLedger = {}
            )
        }
    }

    @Test
    fun moveIsOfferedWhenSettleUpListsYouThoughYouAreEvenOverall() {
        // Audit run 6: Asha owes you 100 and you owe Ravi 100, so you are even overall, but Settle
        // up lists both payments, and the move takes what it lists (v3.7.1).
        showYou(
            balances = listOf(MemberBalance(1, 0), MemberBalance(2, -100_00), MemberBalance(3, 100_00)),
            transfers = listOf(Transfer(2, 1, 100_00), Transfer(1, 3, 100_00))
        )
        compose.onNodeWithText("Move to your ledger").assertExists()
    }

    @Test
    fun noMoveWhenSettleUpHasNothingOfYours() {
        showYou(
            balances = listOf(MemberBalance(1, 0), MemberBalance(2, -100_00), MemberBalance(3, 100_00)),
            transfers = listOf(Transfer(2, 3, 100_00))
        )
        compose.onNodeWithText("Move to your ledger").assertDoesNotExist()
    }
}
