package com.kg.merapaisa.ui

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.data.AppDatabase
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.repository.GroupDetail
import com.kg.merapaisa.repository.GroupRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guards the `distinctUntilChanged` in [MainViewModel.openGroup].
 *
 * Every field of `MainUiState` shares one flow, so typing a digit or opening a dialog re-emits
 * the same `openGroupId`. `Flow.map` does not dedupe, so without the filter each of those
 * restarted `groupDetail` — a `combine` of three Room queries — for a group that had not
 * changed. This drives the same operator chain the ViewModel builds and counts what SQLite is
 * actually asked to run.
 */
@RunWith(AndroidJUnit4::class)
class OpenGroupQueryCountTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: GroupRepository

    /** Counts only the three statements `groupDetail` is made of, not Room's own bookkeeping. */
    private val detailQueries = AtomicInteger(0)

    /** How many times the chain resubscribed to `groupDetail`. */
    private val resubscriptions = AtomicInteger(0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).setQueryCallback(
            { sql, _ ->
                val isDetailQuery = (sql.contains("FROM persons") && sql.contains("group_members")) ||
                    (sql.contains("FROM expenses") && sql.contains("groupId")) ||
                    sql.contains("FROM expense_shares")
                if (isDetailQuery) detailQueries.incrementAndGet()
            },
            Executors.newSingleThreadExecutor()
        ).build()
        repo = GroupRepository(db.groupDao(), db.personDao())
    }

    @After
    fun tearDown() = db.close()

    /** The chain exactly as [MainViewModel.openGroup] builds it. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun openGroupChain(source: MutableStateFlow<FakeUiState>): Flow<GroupDetail?> = source
        .map { it.openGroupId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else {
                resubscriptions.incrementAndGet()
                repo.groupDetail(id)
            }
        }

    private data class FakeUiState(val openGroupId: Long? = null, val input: String = "")

    @Test
    fun unrelatedUiStateChangesDoNotRerunTheGroupQueries() = runBlocking {
        val asha = db.personDao().insertPerson(Person(name = "Asha", currency = "INR"))
        val groupId = repo.createGroup("Goa", "INR", listOf(asha))
        repo.addExpense(groupId, "Hotel", 500_00, repo.self().id, listOf(repo.self().id, asha))

        val source = MutableStateFlow(FakeUiState())
        val collector = launch { openGroupChain(source).collect { } }
        delay(SETTLE_MS)

        // Open the group. This is the one subscription that is supposed to happen.
        source.update { it.copy(openGroupId = groupId) }
        delay(SETTLE_MS)

        val queriesAfterOpen = detailQueries.get()
        val subscriptionsAfterOpen = resubscriptions.get()
        assertEquals("opening a group subscribes to groupDetail once", 1, subscriptionsAfterOpen)

        // Now change state that has nothing to do with which group is open, the way typing an
        // amount or opening a dialog does. Spaced out, because a MutableStateFlow conflates: a
        // tight loop collapses into one or two emissions and would understate the problem, while
        // real keystrokes arrive far enough apart that every one of them is observed.
        repeat(UNRELATED_CHANGES) { i ->
            source.update { it.copy(input = "$i") }
            delay(KEYSTROKE_GAP_MS)
        }
        delay(SETTLE_MS)

        assertEquals(
            "$UNRELATED_CHANGES unrelated state changes must not resubscribe to groupDetail",
            subscriptionsAfterOpen,
            resubscriptions.get()
        )
        assertEquals(
            "$UNRELATED_CHANGES unrelated state changes must not re-run any group query",
            queriesAfterOpen,
            detailQueries.get()
        )

        collector.cancel()
    }

    private companion object {
        const val SETTLE_MS = 1_500L
        const val UNRELATED_CHANGES = 20
        const val KEYSTROKE_GAP_MS = 100L
    }
}
