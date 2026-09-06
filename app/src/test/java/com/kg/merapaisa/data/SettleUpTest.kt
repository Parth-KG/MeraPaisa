package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SettleUpTest {

    private fun balances(vararg pairs: Pair<Long, Long>) = pairs.map { MemberBalance(it.first, it.second) }

    /** The property that actually matters: after the transfers, everyone is square. */
    private fun assertSettlesEveryone(input: List<MemberBalance>, transfers: List<Transfer>) {
        val net = input.associate { it.personId to it.amountMinor }.toMutableMap()
        transfers.forEach { t ->
            net[t.fromPersonId] = (net[t.fromPersonId] ?: 0L) + t.amountMinor
            net[t.toPersonId] = (net[t.toPersonId] ?: 0L) - t.amountMinor
        }
        net.forEach { (id, remaining) ->
            assertEquals("person $id should end square", 0L, remaining)
        }
    }

    @Test
    fun anAlreadySquareGroupNeedsNoPayments() {
        assertTrue(settleUp(balances(1L to 0L, 2L to 0L, 3L to 0L)).isEmpty())
        assertTrue(settleUp(emptyList()).isEmpty())
    }

    @Test
    fun onePayerAndOneDebtorIsASinglePayment() {
        val input = balances(1L to 500_00L, 2L to -500_00L)
        val transfers = settleUp(input)
        assertEquals(listOf(Transfer(fromPersonId = 2L, toPersonId = 1L, amountMinor = 500_00L)), transfers)
        assertSettlesEveryone(input, transfers)
    }

    @Test
    fun aPassThroughIsCollapsed() {
        // A owes B 100, B owes C 100. B nets to zero and should not appear at all.
        val input = balances(1L to -100_00L, 2L to 0L, 3L to 100_00L)
        val transfers = settleUp(input)
        assertEquals(listOf(Transfer(fromPersonId = 1L, toPersonId = 3L, amountMinor = 100_00L)), transfers)
        assertTrue("the middle member should not be involved", transfers.none { it.fromPersonId == 2L || it.toPersonId == 2L })
    }

    @Test
    fun onePayerManyDebtors() {
        val input = balances(1L to 900_00L, 2L to -300_00L, 3L to -300_00L, 4L to -300_00L)
        val transfers = settleUp(input)
        assertEquals(3, transfers.size)
        assertTrue("everyone pays the one who fronted it", transfers.all { it.toPersonId == 1L })
        assertSettlesEveryone(input, transfers)
    }

    @Test
    fun neverMoreThanOneFewerPaymentsThanMembers() {
        val input = balances(1L to 250_00L, 2L to 130_00L, 3L to -40_00L, 4L to -90_00L, 5L to -250_00L)
        val transfers = settleUp(input)
        assertTrue("expected at most 4 transfers, got ${transfers.size}", transfers.size <= input.size - 1)
        assertSettlesEveryone(input, transfers)
    }

    @Test
    fun noTransferIsEverZeroOrNegative() {
        val input = balances(1L to 100_00L, 2L to 0L, 3L to -60_00L, 4L to -40_00L)
        settleUp(input).forEach { assertTrue("transfer must be a real amount: $it", it.amountMinor > 0) }
    }

    @Test
    fun theOrderOfInputDoesNotChangeTheOutcome() {
        val a = balances(1L to 300_00L, 2L to -100_00L, 3L to -200_00L)
        val b = balances(3L to -200_00L, 1L to 300_00L, 2L to -100_00L)
        assertEquals(settleUp(a).toSet(), settleUp(b).toSet())
    }

    @Test
    fun everyoneEndsSquareAcrossManyRandomGroups() {
        val random = Random(20260906)
        repeat(300) {
            val size = random.nextInt(2, 9)
            val ids = (1L..size.toLong()).toList()
            // Build balances that sum to zero, as a fully shared-out group always does.
            val raw = ids.dropLast(1).map { random.nextLong(-500_00L, 500_00L) }
            val input = ids.zip(raw + listOf(-raw.sum())).map { MemberBalance(it.first, it.second) }

            val transfers = settleUp(input)
            assertSettlesEveryone(input, transfers)
            assertTrue("too many transfers for $size members", transfers.size <= size - 1)
        }
    }

    // --- shares ---

    @Test
    fun evenSharesAlwaysAddBackUpToTheWhole() {
        listOf(100_00L to 3, 10_01L to 2, 1L to 4, 999_99L to 7, 0L to 5).forEach { (amount, members) ->
            val ids = (1L..members.toLong()).toList()
            val shares = evenShares(amount, ids)
            assertEquals("$amount over $members must not lose a paisa", amount, shares.values.sum())
            assertEquals(members, shares.size)
        }
    }

    @Test
    fun evenSharesSpreadTheRemainderOneUnitAtATime() {
        val shares = evenShares(100_00L, listOf(1L, 2L, 3L))
        assertEquals(listOf(3334L, 3333L, 3333L), listOf(shares[1L], shares[2L], shares[3L]))
    }

    @Test
    fun groupBalancesAreWhatYouPaidLessWhatYouOwe() {
        val result = groupBalances(
            memberIds = listOf(1L, 2L, 3L),
            paidByPerson = mapOf(1L to 120_00L),
            sharesByPerson = mapOf(1L to 40_00L, 2L to 40_00L, 3L to 40_00L)
        )
        assertEquals(
            listOf(MemberBalance(1L, 80_00L), MemberBalance(2L, -40_00L), MemberBalance(3L, -40_00L)),
            result
        )
        assertEquals("a fully shared-out group nets to zero", 0L, result.sumOf { it.amountMinor })
    }

    @Test
    fun aMemberWhoNeitherPaidNorOwesStillAppears() {
        val result = groupBalances(listOf(1L, 2L), emptyMap(), emptyMap())
        assertEquals(listOf(MemberBalance(1L, 0L), MemberBalance(2L, 0L)), result)
    }

    // --- the whole loop, using the same functions the app does ---

    /** Mirrors how the app records things: an expense is a payer plus a set of shares. */
    private data class Spend(val paidBy: Long, val amountMinor: Long, val sharedWith: List<Long>)

    private fun balancesFrom(members: List<Long>, spends: List<Spend>): List<MemberBalance> {
        val paid = mutableMapOf<Long, Long>()
        val owed = mutableMapOf<Long, Long>()
        spends.forEach { spend ->
            paid[spend.paidBy] = (paid[spend.paidBy] ?: 0L) + spend.amountMinor
            evenShares(spend.amountMinor, spend.sharedWith).forEach { (id, share) ->
                owed[id] = (owed[id] ?: 0L) + share
            }
        }
        return groupBalances(members, paid, owed)
    }

    @Test
    fun recordingEverySuggestedPaymentLeavesTheGroupSquare() {
        val members = listOf(1L, 2L, 3L, 4L)
        val spends = listOf(
            Spend(paidBy = 1L, amountMinor = 4_000_00L, sharedWith = members),   // hotel
            Spend(paidBy = 2L, amountMinor = 1_250_00L, sharedWith = members),   // dinner
            Spend(paidBy = 3L, amountMinor = 300_00L, sharedWith = listOf(3L, 4L)) // a cab those two took
        )

        val before = balancesFrom(members, spends)
        assertEquals("a fully shared-out group nets to zero", 0L, before.sumOf { it.amountMinor })

        // A settlement is just an expense the payer covered entirely on the payee's behalf,
        // which is exactly how GroupRepository.recordTransfer writes it.
        val settlements = settleUp(before).map {
            Spend(paidBy = it.fromPersonId, amountMinor = it.amountMinor, sharedWith = listOf(it.toPersonId))
        }
        assertTrue("should not need more than one payment per member", settlements.size <= members.size - 1)

        val after = balancesFrom(members, spends + settlements)
        after.forEach { assertEquals("${it.personId} should end square", 0L, it.amountMinor) }
    }

    @Test
    fun aGroupWhereOnePersonPaysForEverythingSettlesInOneRound() {
        val members = listOf(1L, 2L, 3L)
        val spends = listOf(Spend(paidBy = 1L, amountMinor = 900_00L, sharedWith = members))
        val before = balancesFrom(members, spends)
        val transfers = settleUp(before)

        assertEquals(2, transfers.size)
        assertTrue(transfers.all { it.toPersonId == 1L })
        val after = balancesFrom(
            members,
            spends + transfers.map { Spend(it.fromPersonId, it.amountMinor, listOf(it.toPersonId)) }
        )
        after.forEach { assertEquals(0L, it.amountMinor) }
    }

    @Test
    fun anIndivisibleAmountStillSettlesExactly() {
        // 100.01 across three people cannot divide evenly; nobody should be left a paisa out.
        val members = listOf(1L, 2L, 3L)
        val spends = listOf(Spend(paidBy = 1L, amountMinor = 100_01L, sharedWith = members))
        val before = balancesFrom(members, spends)
        assertEquals(0L, before.sumOf { it.amountMinor })

        val after = balancesFrom(
            members,
            spends + settleUp(before).map { Spend(it.fromPersonId, it.amountMinor, listOf(it.toPersonId)) }
        )
        after.forEach { assertEquals("${it.personId} should end square", 0L, it.amountMinor) }
    }
}
