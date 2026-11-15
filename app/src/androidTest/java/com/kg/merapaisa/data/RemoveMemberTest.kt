package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.GroupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Removing a member (v3.7.0): only someone nothing in the group involves, never you. */
@RunWith(AndroidJUnit4::class)
class RemoveMemberTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: GroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = GroupRepository(db.groupDao(), db.personDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun person(name: String) = db.personDao().insertPerson(Person(name = name, currency = "INR"))
    private suspend fun memberIds(group: Long) = repo.groupDetail(group).first().members.map { it.id }.toSet()

    @Test
    fun someoneNothingInvolvesIsRemovedAndTheRestStay() = runBlocking {
        val asha = person("Asha"); val bilal = person("Bilal"); val chai = person("Chaitanya"); val dev = person("Dev")
        val group = repo.createGroup("Goa", "INR", listOf(asha, bilal, chai, dev))
        val me = repo.self().id
        repo.addExpense(group, "Hotel", 900_00, asha, listOf(asha, bilal))
        repo.recordTransfer(group, chai, asha, 1_00)
        val before = repo.groupDetail(group).first().balances

        repo.editGroup(group, "Goa", emptyList(), listOf(asha, bilal, chai, dev, me))

        assertEquals("only Dev, whom nothing names, leaves; you never do", setOf(me, asha, bilal, chai), memberIds(group))
        assertEquals("no balance moved", before.filter { it.personId != dev }, repo.groupDetail(group).first().balances)
    }

    @Test
    fun aRemovalIsRefusedOnceAnExpenseNamesThem() = runBlocking {
        val asha = person("Asha")
        val group = repo.createGroup("Goa", "INR", listOf(asha))
        val me = repo.self().id
        // The sheet offered Asha for removal, then an expense naming her arrived before Save.
        repo.addExpense(group, "Cab", 100_00, me, listOf(me, asha))
        repo.editGroup(group, "Goa", emptyList(), listOf(asha))
        assertEquals(setOf(me, asha), memberIds(group))
    }
}
