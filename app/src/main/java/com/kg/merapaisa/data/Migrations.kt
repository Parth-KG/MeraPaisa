package com.kg.merapaisa.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 3 -> 4. Money becomes Long minor units, currency becomes an ISO 4217 code, and the stored
 * `balance` column is dropped in favour of summing the transaction rows.
 *
 * Dropping the column is only safe if the sum already equals it, and for this ledger it often
 * does not: balances were written directly before transactions were logged, and currency
 * conversion rewrote balances without recording anything. So before the column goes, every
 * person whose rows do not add up to their stored balance gets one reconciling entry for
 * exactly the difference, dated just before their earliest transaction. Nobody's balance moves.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        // 1. transactions.amount (REAL major units) -> amountMinor (INTEGER hundredths).
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`personId` INTEGER NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `transactions_new` (`id`, `personId`, `amountMinor`, `timestamp`, `note`) " +
                "SELECT `id`, `personId`, CAST(ROUND(`amount` * 100) AS INTEGER), `timestamp`, `note` " +
                "FROM `transactions`"
        )
        db.execSQL("DROP TABLE `transactions`")
        db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")

        // 2. Reconcile every stored balance against its rows, while the column still exists.
        //    Staged in a temp table so the INSERT never reads the table it is writing to.
        db.execSQL(
            "CREATE TEMP TABLE `balance_reconciliation` AS " +
                "SELECT p.`id` AS `personId`, " +
                "CAST(ROUND(p.`balance` * 100) AS INTEGER) - " +
                "COALESCE((SELECT SUM(t.`amountMinor`) FROM `transactions` t WHERE t.`personId` = p.`id`), 0) AS `difference`, " +
                "COALESCE((SELECT MIN(t.`timestamp`) FROM `transactions` t WHERE t.`personId` = p.`id`) - 1, $now) AS `timestamp` " +
                "FROM `persons` p"
        )
        db.execSQL(
            "INSERT INTO `transactions` (`personId`, `amountMinor`, `timestamp`, `note`) " +
                "SELECT `personId`, `difference`, `timestamp`, 'Opening balance' " +
                "FROM `balance_reconciliation` WHERE `difference` != 0"
        )
        db.execSQL("DROP TABLE `balance_reconciliation`")

        // 3. persons: drop `balance`, and store the currency as a code rather than a symbol.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `persons_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`pfpType` TEXT NOT NULL, " +
                "`pfpValue` TEXT NOT NULL, " +
                "`pfpColor` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`isSettled` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `persons_new` " +
                "(`id`, `name`, `pfpType`, `pfpValue`, `pfpColor`, `sortOrder`, `isSettled`, `currency`) " +
                "SELECT `id`, `name`, `pfpType`, `pfpValue`, `pfpColor`, `sortOrder`, `isSettled`, " +
                "CASE `currency` " +
                "WHEN '₹' THEN 'INR' " +
                "WHEN '$' THEN 'USD' " +
                "WHEN '€' THEN 'EUR' " +
                "WHEN '£' THEN 'GBP' " +
                "WHEN '¥' THEN 'JPY' " +
                "ELSE `currency` END " +
                "FROM `persons`"
        )
        db.execSQL("DROP TABLE `persons`")
        db.execSQL("ALTER TABLE `persons_new` RENAME TO `persons`")
    }
}

/**
 * 4 -> 5. Adds an index on transactions.personId and a cascading foreign key to persons.
 *
 * Nothing enforced that relationship before, so the table may already hold rows pointing at a
 * person who no longer exists. Those rows are counted by no balance and shown in no history.
 * They are invisible, and SQLite would refuse to apply the foreign key while they are present.
 * So they are deleted first, before the constrained table is built.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM `transactions` " +
                "WHERE `personId` NOT IN (SELECT `id` FROM `persons`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`personId` INTEGER NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "INSERT INTO `transactions_new` (`id`, `personId`, `amountMinor`, `timestamp`, `note`) " +
                "SELECT `id`, `personId`, `amountMinor`, `timestamp`, `note` FROM `transactions`"
        )
        db.execSQL("DROP TABLE `transactions`")
        db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_personId` ON `transactions` (`personId`)")
    }
}

/**
 * 5 -> 6. Groups.
 *
 * Adds `isSelf` to persons and creates the one row that is you. Until now "You" was a -1L
 * sentinel invented inside the split screen, which meant you could not be a group member, be
 * owed money by the group, or appear in a settle-up. Being a real row fixes all three.
 *
 * The self row is hidden from the people list, so nobody gains a mysterious extra contact.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `persons` ADD COLUMN `isSelf` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "INSERT INTO `persons` (`name`, `pfpType`, `pfpValue`, `pfpColor`, `sortOrder`, `isSettled`, `currency`, `isSelf`) " +
                "VALUES ('You', 'initials', 'You', '#4CAF50', -1, 0, 'INR', 1)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expense_groups` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `group_members` (" +
                "`groupId` INTEGER NOT NULL, " +
                "`personId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`groupId`, `personId`), " +
                "FOREIGN KEY(`groupId`) REFERENCES `expense_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_personId` ON `group_members` (`personId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expenses` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`groupId` INTEGER NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`paidByPersonId` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "FOREIGN KEY(`groupId`) REFERENCES `expense_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`paidByPersonId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_groupId` ON `expenses` (`groupId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_paidByPersonId` ON `expenses` (`paidByPersonId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expense_shares` (" +
                "`expenseId` INTEGER NOT NULL, " +
                "`personId` INTEGER NOT NULL, " +
                "`shareMinor` INTEGER NOT NULL, " +
                "PRIMARY KEY(`expenseId`, `personId`), " +
                "FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_shares_personId` ON `expense_shares` (`personId`)")
    }
}

/**
 * 6 -> 7. Two-sided ledger over share links.
 *
 * Adds the table that makes applying a link idempotent, and a per-person watermark recording
 * where the last share got to so the next one carries only what is new.
 *
 * `lastSharedAt` defaults to 0 rather than to now. An existing install has never shared anything,
 * so its first link should offer the whole history. Defaulting to the current time would silently
 * send an empty payload and look like the feature was broken.
 *
 * Note the absent foreign key on `applied_payloads.personId`: see [AppliedPayload] for why a
 * cascade here would let a forwarded link apply twice.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `persons` ADD COLUMN `lastSharedAt` INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `applied_payloads` (" +
                "`payloadId` TEXT NOT NULL, " +
                "`appliedAt` INTEGER NOT NULL, " +
                "`personId` INTEGER NOT NULL, " +
                "`senderName` TEXT NOT NULL, " +
                "`entryCount` INTEGER NOT NULL, " +
                "`netMinor` INTEGER NOT NULL, " +
                "PRIMARY KEY(`payloadId`))"
        )
    }
}

/**
 * 7 -> 8. How a group's settle-up is worked out, and telling a repayment from a purchase.
 *
 * `simplifyDebts` defaults to 1 because that is what every existing group has been doing since
 * groups shipped: `settleUp` has always netted positions down to the fewest payments. Defaulting
 * to 0 would silently change the plan shown for every group that already exists.
 *
 * `isSettlement` is backfilled from the description, which is the only signal older rows carry.
 * `recordTransfer` has always written exactly "Settlement", so the match is reliable for rows this
 * app produced, and a real expense somebody happened to name "Settlement" being reclassified is a
 * cosmetic misfiling, not an arithmetic one. The flag changes how a row is displayed, never how it
 * is counted.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `simplifyDebts` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `isSettlement` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `expenses` SET `isSettlement` = 1 WHERE `description` = 'Settlement'")
    }
}

/**
 * 8 -> 9. Stable entry ids, and where an entry came from. What makes reconcile possible.
 *
 * Until now the only name an entry had was its local autoincrement id, which is a different number
 * on each phone for the same debt. Two ledgers could disagree (an amount edited on one side, an
 * entry deleted on the other), and there was no way to line them up and say so. `uid` gives both
 * sides one name for the same debt; see [Transaction.uid].
 *
 * Existing rows are given a random uid each rather than one derived from their contents. Deriving
 * it would be reproducible across phones, which sounds useful and is exactly wrong: two people who
 * both recorded "Chai 20" on the same afternoon would mint the same uid for two genuinely
 * different debts, and reconcile would then offer to merge them. `randomblob(8)` is SQLite's own
 * CSPRNG, so this needs no round trip through Kotlin: the whole back-fill is one statement.
 *
 * `fromShare` is 0 for every existing row, and that is the honest answer rather than a convenient
 * one. Entries imported before v2.5 are indistinguishable from typed ones now, so they are treated
 * as yours: reconcile will never offer to delete them on the sender's say-so. It will offer to
 * update one whose uid matches, which is safe, because that only ever happens after a v2 payload
 * has taught both sides the same uid.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `fromShare` INTEGER NOT NULL DEFAULT 0")
        // Per row, not per statement: randomblob is re-evaluated for each one.
        db.execSQL("UPDATE `transactions` SET `uid` = lower(hex(randomblob(8)))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_uid` ON `transactions` (`uid`)")
    }
}
