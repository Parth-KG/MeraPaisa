package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.GroupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Archiving a group (v3.7.0): it moves lists, nothing in it changes, and a backup keeps it. */
@RunWith(AndroidJUnit4::class)
class GroupArchiveTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: GroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = GroupRepository(db.groupDao(), db.personDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun archiveAndUnarchiveMoveTheGroupBetweenLists() = runBlocking {
        val asha = db.personDao().insertPerson(Person(name = "Asha", currency = "INR"))
        val group = repo.createGroup("Goa", "INR", listOf(asha))
        val me = repo.self().id
        repo.addExpense(group, "Hotel", 1_000_00, me, listOf(me, asha))

        repo.setArchived(group, true)
        assertTrue(repo.groupSummaries().first().none { it.group.id == group })
        val archived = repo.archivedGroupSummaries().first().single()
        assertEquals(group, archived.group.id)
        assertEquals("your position is the same while archived", 500_00L, archived.yourBalanceMinor)
        assertEquals("it still opens with everything in it", 1, repo.groupDetail(group).first().expenses.size)

        repo.setArchived(group, false)
        assertEquals(group, repo.groupSummaries().first().single().group.id)
        assertTrue(repo.archivedGroupSummaries().first().isEmpty())
    }

    @Test
    fun anArchivedGroupStaysArchivedThroughABackup() = runBlocking {
        val group = repo.createGroup("Goa", "INR", emptyList())
        repo.setArchived(group, true)
        val snapshot = BackupSnapshot(
            persons = db.personDao().getAllPersonsForBackup(),
            transactions = emptyList(),
            groups = db.groupDao().getAllGroupsForBackup(),
            groupMembers = db.groupDao().getAllGroupMembersForBackup(),
            expenses = emptyList(), expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        val back = (decodeBackup(encodeBackup(snapshot, 1, "3.7.0")) as BackupResult.Ok).snapshot
        assertEquals(true, back.groups.single().archived)
    }
}
