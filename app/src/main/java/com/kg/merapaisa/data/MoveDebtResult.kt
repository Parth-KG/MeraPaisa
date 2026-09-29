package com.kg.merapaisa.data

/**
 * What happened when part of one person's balance was moved onto another.
 *
 * Every refusal is its own arm rather than a boolean, because each needs a different sentence on
 * screen. "You can't move more than Rondu owes you" and "Rondu is in rupees and Sasti is in
 * dollars" are not the same problem, and collapsing them into "couldn't do that" would leave
 * somebody re-tapping a button that is never going to work.
 */
sealed interface MoveDebtResult {

    data class Moved(val amountMinor: Long) : MoveDebtResult

    /** Moving a debt onto the person who already owes it changes nothing. */
    data object SamePerson : MoveDebtResult

    /** Zero, or negative. Neither means anything here. */
    data object NotAnAmount : MoveDebtResult

    /**
     * The two people are tracked in different currencies.
     *
     * Refused rather than converted, for the same reason an incoming share link in the wrong
     * currency is refused: the amounts are minor units with no rate attached, so moving ₹100 onto
     * a dollar balance would silently claim $100.
     */
    data class CurrencyMismatch(val from: String, val to: String) : MoveDebtResult

    /** They owe you nothing, or you owe them, so there is no debt of theirs to hand on. */
    data class NothingToMove(val balanceMinor: Long) : MoveDebtResult

    /** One of the two people was deleted while the sheet was open. */
    data object PersonGone : MoveDebtResult

    /** More than they actually owe. [availableMinor] is the most that could move. */
    data class MoreThanOwed(val availableMinor: Long) : MoveDebtResult
}
