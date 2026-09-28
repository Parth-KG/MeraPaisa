# Backup and restore (v2.2, Phase E)

Two file formats, deliberately. Only one of them is a backup.

## Which file does what

| | full backup (`.json`) | ledger export (`.csv`) |
|---|---|---|
| People and entries | ✅ | ✅ |
| Groups, expenses, shares | ✅ | ❌ **not carried at all** |
| The self row ("you") | ✅ | ❌ |
| Share-link history (`applied_payloads`) | ✅ | ❌ |
| Profile photos | ❌ | ❌ |
| Opens in a spreadsheet | ❌ | ✅ |

**The CSV is for reading, not for recovering.** It flattens to one row per transaction so it opens
cleanly in a spreadsheet, and that shape has no room for groups. Restoring from one gives you your
people and their direct entries; every group is simply absent.

That has a sharp edge, and the app says so before you hit it: **a Replace restore from a CSV
deletes your groups and restores none of them.** The preview names how many would go.

### Why the CSV's `balance` column is ignored on import

Since v2.0.2 the balance the app shows includes each person's slice of group activity, while the
CSV's `amount` rows cover direct transactions only. For anyone in a group the two therefore
disagree — by design, not by bug. Importing the column would write a balance with no entries to
explain it, so the importer derives balances from the rows and ignores the column entirely.

### What a backup cannot carry

Profile photos. `pfpValue` stores a path into the app's private files directory, not the image, so
restoring on a different phone shows initials for those people — the same fallback the app already
uses when a photo file goes missing. Nothing breaks; a picture is lost.

## Restoring

Settings → **Back up & restore** → *Restore from a file*. Pick either format; the app works out
which it is. Then choose:

- **Add what is missing** — matches people on name and currency, entries on person, time, amount
  and note, and adds only what is absent. Running it twice changes nothing the second time. It
  cannot undo a deletion you made since the backup.
- **Replace everything** — deletes the current ledger, then lays the file down exactly. Right on a
  fresh install, destructive anywhere else. The preview counts what it would delete first.

Nothing is written until you confirm, and the counts on screen always describe the mode currently
selected.

**A restore is one database transaction.** It touches every table, so if it fails partway the whole
thing rolls back and the ledger is exactly as it was. A half-restored ledger — people with no
entries, expenses with no shares — would be worse than the state you were recovering from.

## Weekly automatic backup

Settings → **Back up & restore** → *Choose a folder*. After that a background job writes a
timestamped backup there once a week and keeps the last 12, pruning older ones.

- Only files named `mera-paisa-backup-*.json` are ever pruned. You picked a real folder that may
  hold your own files, and a backup job that tidies up your documents is a far worse bug than one
  that leaves too many backups.
- The folder is a `content://` tree from the system picker, with its permission persisted via
  `takePersistableUriPermission`. **The app holds no storage permission** — it can write to that one
  folder and nowhere else.
- No network, no charger, no battery-optimisation exemption. It asks only that the battery is not
  low.
- Settings shows when it last ran and what happened. That line matters: an automatic job runs when
  nobody is watching, and without it a job that has been failing for a month looks exactly like one
  that was never set up.

If the folder is deleted, unmounted, or its permission revoked, the job reports that and stops
rather than retrying forever. Pick the folder again.

## Verifying it by hand

```sh
# Is the weekly job scheduled?
adb shell dumpsys jobscheduler | grep -A5 com.kg.merapaisa

# Read a backup back
python3 -m json.tool mera-paisa-backup-*.json | head -40
```

A backup is valid JSON with `"format": "mera-paisa-backup"` at the top. It is indented on purpose —
you should be able to open your own backup and read it.

## Implementation notes

- `data/Json.kt` — a small JSON reader and writer, hand-rolled on pure JDK. `org.json` is an
  Android stub that throws under JVM unit tests, and every alternative is a new dependency, which
  `loop-audit/PROTOCOL.md` rules out. **Integers only:** money here is `Long` minor units, so
  refusing `1.5` and `1e3` means a corrupted file fails loudly instead of rounding someone's
  balance.
- `data/RestorePlan.kt` — `planRestore` is a **pure function** over two snapshots. Deciding what to
  insert while inserting it would be both untestable without a device and impossible to preview.
  All the matching and id allocation happens there, under unit tests; applying is a dumb sequence
  of inserts.
- New ids are allocated above everything already in use, so an id in a backup can never land on an
  unrelated local row that happens to share it.
- `androidx.work` is **declared but not new**: Glance already pulls it in for the widget. Pinning
  2.7.1 keeps the scheduled job on a version that cannot drift. `ExistingPeriodicWorkPolicy.UPDATE`
  would read better than `KEEP` but arrived in 2.8.
- `BackupWriter` uses `DocumentsContract` directly rather than `androidx.documentfile`, to avoid a
  dependency for something the framework already does.
