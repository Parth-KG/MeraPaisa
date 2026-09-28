package com.kg.merapaisa.data

/**
 * What happened when a share link was applied.
 *
 * Every arm is a sentence the import screen has to be able to show. "Nothing happened" is not an
 * acceptable outcome for a screen that just told the user it was about to change their ledger, so
 * the refusals carry the detail needed to explain themselves.
 */
sealed interface ImportOutcome {

    /** Entries were written. [netMinor] is what the balance moved by, already mirrored. */
    data class Applied(val entryCount: Int, val netMinor: Long) : ImportOutcome

    /**
     * A link that was compared against this ledger rather than simply appended to it.
     *
     * Reported separately from [Applied] because the sentence afterwards is a different sentence:
     * "recorded 3 entries" and "added 1, updated 1, removed 1" describe very different events, and
     * a user who has just agreed to delete something off their own ledger deserves to be told that
     * it happened rather than being shown a count of additions.
     */
    data class Reconciled(
        val added: Int,
        val updated: Int,
        val removed: Int,
        /** What the balance moved by. Updates and removals move it too — see the DAO. */
        val netMinor: Long
    ) : ImportOutcome {
        val changedNothing: Boolean get() = added == 0 && updated == 0 && removed == 0
    }

    /**
     * This exact link had already been applied. Not an error — forwarding a message or tapping
     * it twice is ordinary — so it reports when it first landed rather than complaining.
     */
    data class AlreadyApplied(val appliedAt: Long) : ImportOutcome

    /**
     * The link's currency is not the currency of the person it was going to be filed against.
     *
     * Refused rather than converted. The amounts in a payload are minor units with no rate
     * attached, so writing 34000 INR-shaped units onto a USD person would silently claim $340
     * instead of ₹340. Converting would need a rate, and a rate the sender never agreed to is
     * not something to invent inside an import.
     */
    data class CurrencyMismatch(val payloadCurrency: String, val personCurrency: String) : ImportOutcome
}
