package com.kg.merapaisa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A trip, a flatshare, a dinner. Named `expense_groups` because `groups` is a reserved word
 * in SQLite.
 */
@Entity(tableName = "expense_groups")
data class Group(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val currency: String = "INR",
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
    /**
     * How this group's settle-up plan is worked out.
     *
     * `true` nets everyone's position down to the fewest payments that square the group up — if A
     * owes B and B owes C the same amount, B drops out and A pays C directly. `false` leaves the
     * debts as they actually arose: whoever shared an expense owes whoever paid for it, netted
     * only between those two people.
     *
     * Neither is more correct. Simplified means fewer payments; unsimplified means every payment
     * traces back to something that actually happened, which some people would rather see.
     *
     * **A presentation choice, not a record.** Switching it changes how the plan is computed and
     * writes nothing to any log, because nothing happened.
     */
    val simplifyDebts: Boolean = true
)

/** Who is in a group. Members are ordinary people, including you. */
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "personId"],
    indices = [Index("personId")],
    foreignKeys = [
        ForeignKey(Group::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class GroupMember(
    val groupId: Long,
    val personId: Long
)

/** One thing somebody paid for, to be shared out among members. */
@Entity(
    tableName = "expenses",
    indices = [Index("groupId"), Index("paidByPersonId")],
    foreignKeys = [
        ForeignKey(Group::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Person::class, ["id"], ["paidByPersonId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val description: String,
    val amountMinor: Long,
    val paidByPersonId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * Whether this row is somebody paying somebody back, rather than somebody buying something.
     *
     * A settlement genuinely is an expense in the arithmetic — the payer covered an amount shared
     * entirely with the payee, which cancels both positions exactly and needs no second mechanism.
     * But it is not *spending*, and until v2.4 the two were told apart only by the description
     * reading "Settlement". So a ₹500 dinner and a ₹500 repayment looked identical in the list,
     * and anyone who named a real expense "Settlement" would have joined them.
     *
     * The flag keeps the shared arithmetic while separating the two in the eye.
     */
    val isSettlement: Boolean = false
)

/**
 * How much of an expense one member is responsible for. Shares always add back up to the
 * expense, so a group's balances net to zero and settle-up can square everyone exactly.
 */
@Entity(
    tableName = "expense_shares",
    primaryKeys = ["expenseId", "personId"],
    indices = [Index("personId")],
    foreignKeys = [
        ForeignKey(Expense::class, ["id"], ["expenseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class ExpenseShare(
    val expenseId: Long,
    val personId: Long,
    val shareMinor: Long
)
