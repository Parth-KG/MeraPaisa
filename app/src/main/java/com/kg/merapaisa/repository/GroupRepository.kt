package com.kg.merapaisa.repository

import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.GroupDao
import com.kg.merapaisa.data.GroupMember
import com.kg.merapaisa.data.GroupSummary
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Groups, and the person that is you. Every group includes you as a member — a trip you are
 * not part of is somebody else's ledger.
 */
class GroupRepository(
    private val groupDao: GroupDao,
    private val personDao: PersonDao,
    private val notifier: LedgerChangeNotifier = LedgerChangeNotifier.None
) {

    suspend fun self(): Person = personDao.ensureSelf()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun groupSummaries(): Flow<List<GroupSummary>> =
        flow { emit(self().id) }.flatMapLatest { groupDao.getGroupSummaries(it) }

    fun members(groupId: Long): Flow<List<Person>> = groupDao.getMembers(groupId)

    /** Creates a group with you and the chosen people in it. */
    suspend fun createGroup(name: String, currency: String, memberIds: List<Long>): Long {
        val selfId = self().id
        val groupId = groupDao.insertGroup(Group(name = name, currency = currency))
        val everyone = (memberIds + selfId).distinct()
        groupDao.addMembers(everyone.map { GroupMember(groupId = groupId, personId = it) })
        notifier.onLedgerChanged()
        return groupId
    }

    suspend fun deleteGroup(groupId: Long) {
        groupDao.deleteGroup(groupId)
        notifier.onLedgerChanged()
    }
}
