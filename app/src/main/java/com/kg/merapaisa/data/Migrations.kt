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
 * person who no longer exists. Those rows are counted by no balance and shown in no history —
 * they are invisible, and SQLite would refuse to apply the foreign key while they are present.
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
