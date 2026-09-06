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
    val archived: Boolean = false
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
    val timestamp: Long = System.currentTimeMillis()
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
