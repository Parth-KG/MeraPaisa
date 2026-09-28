package com.kg.merapaisa.data

/**
 * A complete backup of the ledger, and the only thing in this app that can actually restore you.
 *
 * The CSV export is for reading — it opens in a spreadsheet and flattens nicely. It is *not* a
 * backup: it carries people and transactions only, so a CSV round trip silently loses every group,
 * every expense and every share. This format carries all of it, which is why "restore" means this
 * file and not that one.
 *
 * Two things it still cannot carry, both worth knowing before trusting it:
 *
 *  - **Profile photos.** `pfpValue` holds a path into the app's private files directory, not the
 *    image. Restoring onto a different phone leaves those people showing initials, which is what
 *    the app already falls back to when a photo file has gone missing. Nothing breaks; a picture
 *    is lost.
 *  - **Nothing else.** Every other table is here, including `applied_payloads` — leave that out and
 *    restoring an old backup would let a share link you had already applied land a second time.
 */

/** Everything in the database, read together. Row ids are kept so the tables still join up. */
data class BackupSnapshot(
    val persons: List<Person>,
    val transactions: List<Transaction>,
    val groups: List<Group>,
    val groupMembers: List<GroupMember>,
    val expenses: List<Expense>,
    val expenseShares: List<ExpenseShare>,
    val appliedPayloads: List<AppliedPayload>
) {
    val isEmpty: Boolean
        get() = persons.none { !it.isSelf } && transactions.isEmpty() && groups.isEmpty()
}

/** The outcome of reading a backup file, with a distinct arm per sentence the UI has to show. */
sealed interface BackupResult {
    data class Ok(
        val snapshot: BackupSnapshot,
        val exportedAt: Long,
        val appVersion: String
    ) : BackupResult

    /** Valid JSON, but not one of ours. */
    data object NotABackup : BackupResult

    /** Ours, but damaged: truncated by a failed write, or edited into an invalid shape. */
    data object Damaged : BackupResult

    /** A newer format. Refused rather than partially understood. */
    data class TooNew(val version: Int) : BackupResult
}

const val BACKUP_FORMAT = "mera-paisa-backup"
const val BACKUP_VERSION = 1

/** Renders a snapshot as indented JSON. */
fun encodeBackup(snapshot: BackupSnapshot, exportedAt: Long, appVersion: String): String =
    writeJson(
        jsonObject(
            "format" to BACKUP_FORMAT.json(),
            "version" to BACKUP_VERSION.json(),
            "exportedAt" to exportedAt.json(),
            "appVersion" to appVersion.json(),
            "persons" to jsonArray(snapshot.persons.map { p ->
                jsonObject(
                    "id" to p.id.json(),
                    "name" to p.name.json(),
                    "pfpType" to p.pfpType.json(),
                    "pfpValue" to p.pfpValue.json(),
                    "pfpColor" to p.pfpColor.json(),
                    "sortOrder" to p.sortOrder.json(),
                    "isSettled" to p.isSettled.json(),
                    "currency" to p.currency.json(),
                    "isSelf" to p.isSelf.json(),
                    "lastSharedAt" to p.lastSharedAt.json()
                )
            }),
            "transactions" to jsonArray(snapshot.transactions.map { t ->
                jsonObject(
                    "id" to t.id.json(),
                    "personId" to t.personId.json(),
                    "amountMinor" to t.amountMinor.json(),
                    "timestamp" to t.timestamp.json(),
                    "note" to t.note.json(),
                    "uid" to t.uid.json(),
                    "fromShare" to t.fromShare.json()
                )
            }),
            "groups" to jsonArray(snapshot.groups.map { g ->
                jsonObject(
                    "id" to g.id.json(),
                    "name" to g.name.json(),
                    "currency" to g.currency.json(),
                    "createdAt" to g.createdAt.json(),
                    "archived" to g.archived.json(),
                    // Missed when the toggle shipped in v2.4, so a restore silently put every
                    // group back to "fewest payments" regardless of what it had been set to.
                    "simplifyDebts" to g.simplifyDebts.json()
                )
            }),
            "groupMembers" to jsonArray(snapshot.groupMembers.map { m ->
                jsonObject(
                    "groupId" to m.groupId.json(),
                    "personId" to m.personId.json()
                )
            }),
            "expenses" to jsonArray(snapshot.expenses.map { e ->
                jsonObject(
                    "id" to e.id.json(),
                    "groupId" to e.groupId.json(),
                    "description" to e.description.json(),
                    "amountMinor" to e.amountMinor.json(),
                    "paidByPersonId" to e.paidByPersonId.json(),
                    "timestamp" to e.timestamp.json(),
                    // Missed when the flag shipped in v2.4, alongside simplifyDebts. Without it a
                    // restore turned every repayment back into a purchase, putting settlements
                    // back in the expense list — the exact confusion v2.4 existed to end.
                    "isSettlement" to e.isSettlement.json()
                )
            }),
            "expenseShares" to jsonArray(snapshot.expenseShares.map { s ->
                jsonObject(
                    "expenseId" to s.expenseId.json(),
                    "personId" to s.personId.json(),
                    "shareMinor" to s.shareMinor.json()
                )
            }),
            "appliedPayloads" to jsonArray(snapshot.appliedPayloads.map { a ->
                jsonObject(
                    "payloadId" to a.payloadId.json(),
                    "appliedAt" to a.appliedAt.json(),
                    "personId" to a.personId.json(),
                    "senderName" to a.senderName.json(),
                    "entryCount" to a.entryCount.json(),
                    "netMinor" to a.netMinor.json()
                )
            })
        )
    )

/**
 * Reads a backup file back.
 *
 * Strict throughout: a missing or wrongly-typed field fails the whole file rather than defaulting.
 * A backup is read exactly when someone has lost data and is relying on it, which is the worst
 * possible moment to quietly substitute a zero for an amount that would not parse.
 */
fun decodeBackup(text: String): BackupResult {
    val root = parseJson(text) as? JsonObject ?: return BackupResult.NotABackup
    if (root.string("format") != BACKUP_FORMAT) return BackupResult.NotABackup

    val version = root.long("version")?.toInt() ?: return BackupResult.Damaged
    if (version > BACKUP_VERSION) return BackupResult.TooNew(version)
    if (version < 1) return BackupResult.Damaged

    val exportedAt = root.long("exportedAt") ?: return BackupResult.Damaged
    val appVersion = root.string("appVersion") ?: return BackupResult.Damaged

    val persons = root.objects("persons")?.map { o ->
        Person(
            id = o.long("id") ?: return BackupResult.Damaged,
            name = o.string("name") ?: return BackupResult.Damaged,
            pfpType = o.string("pfpType") ?: return BackupResult.Damaged,
            pfpValue = o.string("pfpValue") ?: return BackupResult.Damaged,
            pfpColor = o.string("pfpColor") ?: return BackupResult.Damaged,
            sortOrder = o.long("sortOrder")?.toInt() ?: return BackupResult.Damaged,
            isSettled = o.bool("isSettled") ?: return BackupResult.Damaged,
            currency = o.string("currency") ?: return BackupResult.Damaged,
            isSelf = o.bool("isSelf") ?: return BackupResult.Damaged,
            lastSharedAt = o.long("lastSharedAt") ?: return BackupResult.Damaged
        )
    } ?: return BackupResult.Damaged

    val transactions = root.objects("transactions")?.map { o ->
        Transaction(
            id = o.long("id")?.toInt() ?: return BackupResult.Damaged,
            personId = o.long("personId") ?: return BackupResult.Damaged,
            amountMinor = o.long("amountMinor") ?: return BackupResult.Damaged,
            timestamp = o.long("timestamp") ?: return BackupResult.Damaged,
            note = o.string("note") ?: return BackupResult.Damaged,
            // Absent in a backup written before v2.5. A fresh uid is the only honest answer — the
            // entry has never been shared under any name, so inventing a stable-looking one from
            // its contents would risk colliding with the other phone's idea of a different debt.
            uid = o.string("uid") ?: newEntryUid(),
            fromShare = o.bool("fromShare") ?: false
        )
    } ?: return BackupResult.Damaged

    val groups = root.objects("groups")?.map { o ->
        Group(
            id = o.long("id") ?: return BackupResult.Damaged,
            name = o.string("name") ?: return BackupResult.Damaged,
            currency = o.string("currency") ?: return BackupResult.Damaged,
            createdAt = o.long("createdAt") ?: return BackupResult.Damaged,
            archived = o.bool("archived") ?: return BackupResult.Damaged,
            // Absent in a backup from v2.4 or earlier. True is what every group did before the
            // toggle existed, so an older backup restores to the behaviour it actually had.
            simplifyDebts = o.bool("simplifyDebts") ?: true
        )
    } ?: return BackupResult.Damaged

    val groupMembers = root.objects("groupMembers")?.map { o ->
        GroupMember(
            groupId = o.long("groupId") ?: return BackupResult.Damaged,
            personId = o.long("personId") ?: return BackupResult.Damaged
        )
    } ?: return BackupResult.Damaged

    val expenses = root.objects("expenses")?.map { o ->
        Expense(
            id = o.long("id") ?: return BackupResult.Damaged,
            groupId = o.long("groupId") ?: return BackupResult.Damaged,
            description = o.string("description") ?: return BackupResult.Damaged,
            amountMinor = o.long("amountMinor") ?: return BackupResult.Damaged,
            paidByPersonId = o.long("paidByPersonId") ?: return BackupResult.Damaged,
            timestamp = o.long("timestamp") ?: return BackupResult.Damaged,
            // Absent in a backup from v2.4 or earlier, where the description was the only signal a
            // row carried. Falling back to it here is the same rule MIGRATION_7_8 applies to rows
            // already in the database, so an old backup restores to the same state an old database
            // upgrades to — rather than the two disagreeing about what a settlement is.
            isSettlement = o.bool("isSettlement") ?: (o.string("description") == "Settlement")
        )
    } ?: return BackupResult.Damaged

    val expenseShares = root.objects("expenseShares")?.map { o ->
        ExpenseShare(
            expenseId = o.long("expenseId") ?: return BackupResult.Damaged,
            personId = o.long("personId") ?: return BackupResult.Damaged,
            shareMinor = o.long("shareMinor") ?: return BackupResult.Damaged
        )
    } ?: return BackupResult.Damaged

    // Absent rather than empty is tolerated here, and only here: a v1 backup written by a build
    // before share links existed has no such list, and refusing it would make old backups
    // unrestorable for no gain.
    val appliedPayloads = if (root["appliedPayloads"] == null) emptyList() else {
        root.objects("appliedPayloads")?.map { o ->
            AppliedPayload(
                payloadId = o.string("payloadId") ?: return BackupResult.Damaged,
                appliedAt = o.long("appliedAt") ?: return BackupResult.Damaged,
                personId = o.long("personId") ?: return BackupResult.Damaged,
                senderName = o.string("senderName") ?: return BackupResult.Damaged,
                entryCount = o.long("entryCount")?.toInt() ?: return BackupResult.Damaged,
                netMinor = o.long("netMinor") ?: return BackupResult.Damaged
            )
        } ?: return BackupResult.Damaged
    }

    // Referential integrity, checked before anything is offered as restorable. Room's foreign keys
    // would reject these rows anyway, but they would do it halfway through writing — and a restore
    // that fails in the middle is how a half-ledger happens.
    val personIds = persons.map { it.id }.toSet()
    val groupIds = groups.map { it.id }.toSet()
    val expenseIds = expenses.map { it.id }.toSet()
    if (transactions.any { it.personId !in personIds }) return BackupResult.Damaged
    if (groupMembers.any { it.groupId !in groupIds || it.personId !in personIds }) return BackupResult.Damaged
    if (expenses.any { it.groupId !in groupIds || it.paidByPersonId !in personIds }) return BackupResult.Damaged
    if (expenseShares.any { it.expenseId !in expenseIds || it.personId !in personIds }) return BackupResult.Damaged

    return BackupResult.Ok(
        snapshot = BackupSnapshot(
            persons = persons,
            transactions = transactions,
            groups = groups,
            groupMembers = groupMembers,
            expenses = expenses,
            expenseShares = expenseShares,
            appliedPayloads = appliedPayloads
        ),
        exportedAt = exportedAt,
        appVersion = appVersion
    )
}

/** `mera-paisa-backup-20260928-143000.json` — sorts chronologically, readable at a glance. */
fun backupFileName(stamp: String): String = "mera-paisa-backup-$stamp.json"
