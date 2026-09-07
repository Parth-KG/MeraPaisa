# Mera Paisa — audit ledger

## Run 1 — 2026-09-08

Files read: 87 (66 Kotlin, 12 XML, 4 Room schemas, 5 Gradle/config/ProGuard).
Lint: 34 issues at start → 25 at end (0 errors throughout).
Tests: 69/69 JVM unit, 32/32 instrumentation on RMX3785 (Android 15). 0 failures at end.

**Device evidence.** Cold start `TotalTime` 2977 ms before / 2618 ms after (single samples,
debug build — startup variance is wide, so this is not claimed as an improvement). TOTAL PSS
148.7 MB → 150.8 MB (+1.4%, within noise). Debug APK 24,723,445 bytes. No `FATAL` in logcat and
an empty crash buffer across both launches. First launch logged `Choreographer: Skipped 54
frames` and `HWUI Davey! duration=1170ms`; neither recurred on the second launch, which points
at cold JIT and `ProfileInstaller` rather than anything in the app.

**Blocker: the device keyguard is secure and stayed locked for the whole run.**
`mIsShowing=true, mInputRestricted=true`; `adb shell wm dismiss-keyguard` does not clear it.
So `monkey`, targeted `adb shell input`, rotation and back-navigation were **not run** — the
protocol's interactive device scenario did not happen this run. What did run on the locked
device: `installDebug`, `am start -W`, `dumpsys meminfo/gfxinfo/package`, logcat, and the full
instrumentation suite (Room tests need no foreground window). Every finding below therefore
rests on code paths, lint, or an instrumentation test — none on monkey output.

### Fixed

- `MainActivity.kt:37` — **Cold start showed the ledger before the app-lock setting was known.**
  `isAppLockEnabled(...).collectAsState(initial = false)` treats "DataStore has not answered
  yet" as "no lock", so for the first frames of every launch the people list composed and
  `FLAG_SECURE` was not yet set — the exact thing the lock exists to prevent, and the file's own
  comment says so ("A ledger showing before authentication would defeat the point").
  *Changed:* `FLAG_SECURE` is now added in `onCreate` before `setContent` and only cleared once
  the setting reads back `false`; the initial value is `null`, and the splash is held via
  `setKeepOnScreenCondition` until it resolves, so nothing of the ledger composes meanwhile.
  *Evidence it's gone:* the `initial = false` default is removed; the `when` has no branch that
  renders `MainScreen` while `lockEnabled == null`. Build green, 101 tests green.

- `widget/DebtWidget.kt:51` — **The home-screen widget listed every name and balance regardless
  of the app lock.** `provideGlance` never consulted `SecurityStore`, so with the lock on, the
  ledger the app demands a fingerprint for was readable on the home screen by anyone holding the
  unlocked phone.
  *Changed:* `provideGlance` checks `isAppLockEnabled` first and, when set, renders a new
  `LockedWidgetContent` and returns **before** the repository is constructed, so the database is
  not read at all. *Evidence:* the `PersonRepository` construction is now unreachable on that
  path; build green.

- `MainScreen.kt:485`, `data/GroupDao.kt`, `repository/GroupRepository.kt`, `ui/MainViewModel.kt`
  — **Deleting a person silently deleted group expenses they had paid for.**
  `Expense.paidByPersonId` cascades (`Group.kt:43`), so deleting someone removes every expense
  they fronted and, through `expense_shares`, moves what every other member of those groups owes.
  The confirmation promised only "their balance ... and N transactions".
  *Changed:* added `GroupDao.expenseCountPaidBy(personId): Flow<Int>`, surfaced it through
  `GroupRepository.expensesPaidBy` and `MainViewModel.getGroupExpenseCount`, and the dialog now
  names the count and says the other members' figures will move. *Evidence:* the sentence is
  derived from the same table the cascade acts on. **Behaviour is unchanged — this discloses the
  loss, it does not prevent it.** See "Needs a human decision".

- `ui/SplitAdjustmentsScreen.kt:120` — **One exchange-rate request per foreign participant per
  keystroke.** `LaunchedEffect(amountsInSource, participants)` calls `convertCurrency`, and
  `amountsInSource` is rewritten on every digit, so typing "1234" in a two-currency split fired
  four rounds of HTTPS calls to `api.frankfurter.app` for a number still being typed. Cancelled
  attempts also leave a blocked socket read on an IO thread for up to the 5 s read timeout
  (`ExchangeRateApi.kt:50`), because cancellation cannot interrupt `HttpURLConnection`.
  *Changed:* a 400 ms settle (`CONVERSION_SETTLE_MS`) at the top of the effect, taken only when a
  participant is actually in a different currency, so same-currency splits still update instantly.

- `data/GroupDao.kt:121-128` — Two consecutive KDoc blocks on `recordExpense`, near-identical.
  Removed the stale first one.

- `res/values/colors.xml` — Removed 7 template colours (`purple_*`, `teal_*`, `black`, `white`)
  that lint reported unused and nothing referenced. `splash_background` kept; `themes.xml` uses it.

- `AndroidManifest.xml:18` — Removed `android:label` on `MainActivity`, which repeated the
  application label (lint `RedundantLabel`).

- `ui/groups/AddExpenseDialog.kt:38` — `mutableStateOf` holding a `Long` boxed on every write
  (lint `AutoboxingStateCreation`); now `mutableLongStateOf`.

**Lint delta confirms the last three:** 34 → 25 issues, exactly the 7 `UnusedResources` +
1 `RedundantLabel` + 1 `AutoboxingStateCreation` removed. The remaining 25 are 18 dependency
version bumps (out of scope), 5 `UseKtx` style hints, and 2 `UnusedAttribute` on
`widget_info.xml` (API-31 attributes that degrade correctly at minSdk 24).

### Reverted

- `data/PersonDao.kt:132` — **I claimed `settle()` was wrong and it was not.** I found that
  `settle` closes out `getBalanceNow` (transactions only) while the row shows transactions *plus*
  the person's slice of group activity, and wrote an instrumentation test that reproduced it on
  the device: `expected:<0> but was:<10000>` — a person filed under Settled still showing ₹100.
  I added `getVisibleBalanceNow(personId, selfId)` and settled against that.
  *Why the fix failed:* it broke the existing test
  `settlingClosesTheDirectDebtAndLeavesTheGroupPositionAlone`, and that test is right. Group
  balances derive from `expense_shares`, which `settle` does not touch — so after my change the
  group would still propose "A pays you ₹100" in settle-up, and recording that payment would
  drive the row to **−₹100**. My fix introduced a double-count in the direction of real money.
  Reverted `PersonDao.kt` and deleted my test, both from `loop-audit/backups/run-1/`.
  The design is deliberate and internally consistent: `MainScreen.kt:72` and `PersonDao.kt:128`
  both state that Settled is a filing decision, not a claim that the balance is zero.

### Needs a human decision

- **A person can sit in Settled with a non-zero row.** This is the residue of the reverted item
  and is a real contradiction in the UI, even though the arithmetic is right. Resolving it means
  choosing between: leave it (Settled means "filed"), hide such people from Settled until the
  group is squared, or label the row. That is a product call, not a defect I can fix unilaterally.
- **The CSV export's `balance` column does not equal the sum of its own `amount` rows.**
  `PersonRepository.ledgerSnapshot()` pairs a group-aware balance (`getPersonsWithBalancesNow`)
  with transaction-only rows (`getAllTransactionsNow`), so anyone with group activity exports a
  file that does not reconcile against itself. Both candidate fixes change what the export means
  — drop the group slice from the balance, or add group rows to the file. Not touched this run.

### Noted, left alone

- `data/PersonDao.kt:72` — `@Insert(onConflict = REPLACE)` on `insertPerson`. SQLite REPLACE is
  delete-then-insert, and `transactions.personId` cascades on delete, so calling this with an
  existing id would wipe that person's history. No current call path passes a real id (both
  callers use `id = 0`), so it is a loaded gun rather than a live bug.
- `data/AppDatabase.kt:25` — migrations start at 3→4 and exported schemas start at `3.json`.
  An install still on database version 1 or 2 would fail to open. No `fallbackToDestructiveMigration`,
  so it would crash rather than lose data. No evidence any such install exists.
- `ui/PfpView.kt:37` — `File(...).isFile` is a disk stat run during composition, once per row per
  key change. Real main-thread I/O, but small and un-evidenced without StrictMode.
- `TransactionHistoryDialog.kt:60`, `SettleUpSheet.kt:58`, `SettingsDialog.kt:83` — `items()`
  with no `key`. None of the three item bodies hold per-item state, so nothing is currently
  mis-associated.
- `proguard-rules.pro:5-7` — `-keep class ... { *; }` on `Person`, `Transaction`,
  `PersonWithBalance` keeps their field names readable in the release APK. Room accesses fields
  directly rather than reflectively, and the four group entities have no keeps and ship fine,
  which suggests these three are unnecessary. Removing them is a release-only behaviour change
  and wants its own run with a release build to verify.
- Debug APK measured 23,758,707 bytes before this run's rebuilds and 24,723,445 after. My changes
  add roughly a dozen lines and remove seven resources, so a ~965 KB growth is not explained by
  them. The earlier figure may have been a stale artifact. Carry to run 2 and measure properly.
- `allowBackup="true"` with the Room database included in `backup_rules.xml` and
  `data_extraction_rules.xml` is deliberate and documented in both files (a sideloaded app has no
  other recovery path). Not raised as a finding.
- `keystore.properties` confirmed gitignored, and no key material is inlined in a build file.
  Per protocol, not raised again.

### Checked and found sound

Worth recording so later runs do not re-litigate: every `!!` in `app/src/main` is guarded
(`EditTransactionDialog.kt:60,86` by `isValid`; `MainScreen.kt:389` by a null check;
`AddExpenseDialog.kt:121` by `enabled = valid`; `PersonDao.kt:115` by the insert above it).
`AddPersonDialog.kt:61` and `EditPersonDialog.kt:65` both already delete an uncommitted photo on
dismiss — I suspected an orphaned-file leak and was wrong. `ExchangeRateApi` is HTTPS with
timeouts on both ends. No `GlobalScope`, `runBlocking`, or raw `Thread` anywhere in `app/src/main`.
