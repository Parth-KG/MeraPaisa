package com.kg.merapaisa.ui.backup

import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.RestoreCounts
import com.kg.merapaisa.data.RestoreMode
import com.kg.merapaisa.data.RestorePlan
import com.kg.merapaisa.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the screen says once a restore is done. */
class RestoreMessageTest {

    private fun plan(
        mode: RestoreMode,
        people: Int = 0,
        entries: Int = 0,
        photos: Int = 0,
        alreadyHere: RestoreCounts = RestoreCounts()
    ) = RestorePlan(
        mode = mode,
        persons = List(people) { Person(id = it + 10L, name = "P$it") },
        transactions = List(entries) { Transaction(id = it + 1, personId = 10, amountMinor = 1) },
        groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
        expenseShares = emptyList(), appliedPayloads = emptyList(),
        photosForPeopleAlreadyHere = photos,
        deletes = RestoreCounts(),
        alreadyPresent = alreadyHere
    )

    @Test
    fun `a merge that only adds a photo says so`() {
        val (title, detail) = restoreDoneMessage(plan(RestoreMode.Merge, photos = 1, alreadyHere = RestoreCounts(people = 2, transactions = 3)))
        assertEquals("Photo added", title)
        assertEquals("1 person already here now has the photo from the file. Nothing else changed.", detail)
    }

    @Test
    fun `a merge that only adds photos says how many`() {
        val (title, detail) = restoreDoneMessage(plan(RestoreMode.Merge, photos = 2))
        assertEquals("Photos added", title)
        assertEquals("2 people already here now have their photos from the file. Nothing else changed.", detail)
    }

    @Test
    fun `a merge that adds people and a photo names both`() {
        val (title, detail) = restoreDoneMessage(plan(RestoreMode.Merge, people = 1, entries = 1, photos = 1, alreadyHere = RestoreCounts(people = 1, transactions = 1)))
        assertEquals("Ledger restored", title)
        assertEquals(
            "Added 1 person, 1 entry. 1 entry was already here and was left alone. " +
                "1 person already here now has the photo from the file.",
            detail
        )
    }

    @Test
    fun `a replace reads as before`() {
        val (title, detail) = restoreDoneMessage(plan(RestoreMode.Replace, people = 7, entries = 38))
        assertEquals("Ledger restored", title)
        assertEquals("Your ledger now matches the file exactly. Added 7 people, 38 entries.", detail)
    }
}
