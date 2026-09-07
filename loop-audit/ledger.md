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

## Run 2 — 2026-09-08

Files read: 91 (54 Kotlin main, 13 Kotlin test, 12 XML/manifest/res, 4 Room schemas,
6 Gradle/catalog/properties/ProGuard, 2 backup rule files).
Lint: 25 issues at start → 25 at end (0 errors throughout).
Tests: 69/69 JVM unit at start → 76/76 at end (7 added, 0 failures). Instrumentation not run
this session — see the blocker below.

**Device evidence.** The keyguard blocker that stopped run 1 was gone: RMX3785 (Android 15)
came up unlocked (`mIsShowing=false`, `mInputRestricted=false`, `mDreamingLockscreen=false`),
so the interactive scenario ran for the first time.

- Cold start of `com.kg.merapaisa.debug`: `am start -W` WaitTime 3083 ms. Three main-thread
  stalls during startup — `Choreographer: Skipped 34 frames` / `71 frames` / `41 frames` at
  00:39:27.795, 00:39:29.144 and 00:39:30.583. The splash is what holds the last stretch:
  `VRI[MainActivity]: performTraversals: cancelAndRedraw, mLastPerformTraversalsSkipDrawReason
  predraw_androidx.core.splashscreen.SplashScreen$Impl31$setKeepOnScreenCondition$1` logged at
  29.072 and 29.149, with `draw finished` only at 30.523. That is run 1's `setKeepOnScreenCondition
  { !known }` doing what it was written to do; whether ~1.4 s is the right price is a separate
  question and is noted below.
- **Monkey, seed 42, 300 events, throttle 300, `--pct-syskeys 0`: clean.** No `FATAL EXCEPTION`,
  no `ANR in`, empty `logcat -b crash`, and the process (pid 14538) was the same before and
  after, so nothing restarted. 7 `// Injection Failed` lines, which is a dialog holding focus,
  not a fault. Zero `Choreographer: Skipped` during the whole monkey run.
- Debug APK 24,723,445 bytes — **identical to run 1's closing figure**, which settles run 1's
  carried-over question: the earlier 23,758,707 was a stale artifact, not ~965 KB of growth.
  Nothing to explain. Item closed.

**Blocker: the phone left USB mid-run and did not come back, so rotation, process death,
no-network and the post-fix device re-verification did not happen.** This was my doing. While
driving the app-lock toggle with `adb shell input`, a tap landed on the notification shade's
"Use USB for" sheet and a following back-press selected **Charging only**, which drops ADB.
`adb kill-server && adb start-server` does not help and `system_profiler SPUSBDataType` shows
nothing on the bus, so it is a device-side USB mode change, not a daemon problem. Two
consequences the human needs to know:
  1. **App lock is currently ON for `com.kg.merapaisa.debug`.** I enabled it deliberately to
     test the rotation path. It is the debug package only — the real app is untouched — and it
     is not a lockout: the normal fingerprint/PIN opens it, or `adb shell pm clear
     com.kg.merapaisa.debug` resets it.
  2. Neither fix below has been exercised on the device. Both are green on build, unit tests
     and lint, and the conversion fix has direct evidence from the live API (below), but the
     protocol's §5 re-run did not happen.

**Protocol defect, worth fixing before run 3.** §2 says
`adb shell monkey -p com.kg.merapaisa ...` while §2 also says `./gradlew installDebug`, which
installs `com.kg.merapaisa.debug` (`applicationIdSuffix = ".debug"`). Both packages are on the
device. Followed literally, the protocol fires 300 random events at the **release** app — the
one holding the real ledger — which can add, edit, settle and delete real people and real
money. I ran the monkey against `com.kg.merapaisa.debug` instead. The protocol line should say
`.debug`.

### Fixed

- `network/ExchangeRateApi.kt:26` — **Changing the currency of anyone you owe money to always
  failed, and blamed the user's internet for it.** `convert` passed the raw signed balance
  through as `amount=$major`, and a balance you owe is negative. The rate service rejects that:
  ```
  curl -sL "https://api.frankfurter.app/latest?amount=-500.0&from=INR&to=USD"
    → HTTP 422  {"message":"invalid amount"}
  curl -sL "https://api.frankfurter.app/latest?amount=500.00&from=INR&to=USD"
    → HTTP 200  {"amount":500.0,"base":"INR","date":"2026-09-07","rates":{"USD":5.29}}
  ```
  `fetch` sees `responseCode != HTTP_OK`, returns null, and `MainViewModel.savePersonEdit`
  surfaces *"Couldn't get a … rate. Check your connection, or choose Keep as-is to relabel
  without converting."* — so the one path that is definitely not the problem is the one the
  user is told to go and check. Half the ledger is affected: every person in the red.
  *Changed:* the request now carries the magnitude (`requestAmountMajor`) and the sign is
  restored on the way back (`convertedMinor`), so what you owe converts to what you owe rather
  than failing or flipping.
  *Evidence it's gone:* the two functions are now covered by
  `ExchangeRateApiTest` (7 cases). Reverting just those two function bodies to the old
  behaviour fails 5 of the 7 — `the amount asked about is never negative`, `every plausible
  balance is requested as a magnitude`, `converting what you owe stays owed by you`, `the
  converted amount is rounded to the nearest minor unit`, `a large balance keeps its sign and
  its magnitude` — so the test genuinely holds the fix rather than merely passing beside it.
  **Not yet exercised against the live API on the device.**

- `ui/MainViewModel.kt:94` — **Three Room queries re-ran on every unrelated UI state change
  while a group was open.** `openGroup` is
  `_uiState.map { it.openGroupId }.flatMapLatest { … groupRepository.groupDetail(id) }`.
  `Flow.map` does not dedupe, and every field of `MainUiState` shares that one flow, so a tab
  switch, a numpad key, a note edit or a dialog opening re-emitted the *same* `openGroupId` and
  `flatMapLatest` cancelled and restarted `groupDetail` — which is a `combine` of
  `getMembers`, `getExpenses` and `getSharesForGroup`.
  *Changed:* `.distinctUntilChanged()` between the `map` and the `flatMapLatest`.
  *Evidence:* the restart is unconditional on `openGroupId` being unchanged, which
  `distinctUntilChanged` now filters; the operator cannot alter observable output here because
  the downstream only ever depended on the id's value, not on its re-emission. Build green,
  76 tests green. **Not measured on the device** — a query-count comparison is what run 3
  should take if it wants the number.

### Added

- `app/src/test/java/com/kg/merapaisa/network/ExchangeRateApiTest.kt` — 7 cases. Currency
  conversion had **no test coverage at all** before this run, across 69 unit tests and 32
  instrumentation tests, which is how a sign error survived into a shipping build.

### Reverted

- Nothing. Neither fix needed a retry.

### Wrong turn worth recording

- I suspected `URL("$BASE_URL?amount=$amountMajor&…")` broke for large balances, because Java
  renders a `Double` ≥ 10⁷ in scientific notation — confirmed at the JVM level:
  `amountMinor=1_000_000_000` → `amount=1.0E7`. It is **not** a bug: the service accepts it.
  ```
  curl -sL ".../latest?amount=1.0E7&from=INR&to=USD" → HTTP 200 {"amount":10000000.0,…}
  ```
  Recorded so run 3 does not chase it. Testing the API rather than reasoning about it is also
  what turned up the real defect sitting next to it.

### Needs a human decision

- **Carried from run 1, unchanged:** a person can sit in Settled with a non-zero row. I re-read
  `PersonDao.settle` and `getPersonsWithBalances` fresh this run and reached run 1's conclusion
  independently — `settle` closes out transactions only while the row also carries the group
  slice, and that is deliberate and internally consistent. Still a product call. Not re-raised
  as a defect.
- **Carried from run 1, unchanged:** the CSV export's `balance` column does not reconcile
  against its own `amount` rows for anyone with group activity.
- **New: `BASE_URL` is a permanent redirect.** `https://api.frankfurter.app/latest` answers
  `HTTP/2 301` with `location: https://api.frankfurter.dev/v1/latest?…`. `HttpURLConnection`
  follows it (same protocol, GET, `followRedirects` defaults true), so conversion works today —
  every request just costs an extra round trip. Repointing the constant at the `.dev/v1` host is
  a one-line change but it swaps the endpoint the app depends on, so it wants a deliberate
  decision and a device test, not a quiet edit during an audit.

### Noted, left alone

- `MainActivity.kt:24,25` — **rotation with the app lock on re-prompts for authentication.**
  `locked` and `backgroundedAt` are plain fields on the Activity, the manifest declares no
  `android:configChanges`, so a rotation builds a new instance with `locked = true` and
  `backgroundedAt = 0L`. `SecurityStore.GRACE_MILLIS` exists to stop exactly this nuisance but
  cannot help, because `onStart` then computes `elapsedRealtime() - 0 > 30_000`, which is true
  on any phone up more than 30 seconds. The fix I had drafted is to hold both in a `ViewModel`,
  whose lifetime — survives a configuration change, dies with the process — is exactly the
  lock's semantics, and is safer than a `Bundle`, which the system may restore after process
  death and would then unlock the ledger without asking. **Not applied: I could not reproduce
  it on the device before losing USB, and this is security-relevant code that I will not change
  on reasoning alone.** First item for run 3, with the device attached.
- WorkManager initialises on the startup critical path although the app never uses it:
  `WM-WrkMgrInitializer: Initializing WorkManager with default configuration` at 00:39:26.900,
  then `PackageManager: … SetEnabledSetting(… androidx.work.impl.background.systemjob.
  SystemJobService, 1, 0)` at 00:39:27.138 — ~240 ms before the Activity draws. It arrives
  transitively through Glance's `androidx.startup` provider. Suppressing it needs a manifest
  `tools:node="remove"` plus an `Application` implementing `Configuration.Provider`, and Glance
  may rely on WorkManager for widget updates, so this is a measure-first item, not an edit.
- `data/PersonDao.kt:72` — `@Insert(onConflict = REPLACE)` on `insertPerson`. Re-derived this
  run: still a loaded gun rather than a live bug, both callers pass `id = 0`.
- `data/AppDatabase.kt:25` — migrations start at 3→4 and exported schemas start at `3.json`, so
  an install still on version 1 or 2 cannot open. Confirmed against the schema files this run:
  `3.json` … `6.json` only. No `fallbackToDestructiveMigration`, so it fails loudly rather than
  destroying data.
- `ui/SplitAdjustmentsScreen.kt:80` — `redistribute` clamps each unlocked share with
  `max(0L, share)`, so locking rows above the total silently stops the parts adding up. The
  screen does warn ("Warning: totals don't match"), but `Confirm split` stays enabled —
  `enabled = conversionError == null` does not consider `totalsMatch`. Whether a deliberately
  mismatched split should be recordable is a product question.
- `ui/PfpView.kt:37` — `File(...).isFile` during composition. Still guarded by `remember`, so
  once per row per key change. Unchanged from run 1.
- `TransactionHistoryDialog.kt:60`, `SettleUpSheet.kt:58`, `SettingsDialog.kt:83` — `items()`
  with no `key`. Re-checked: none of the three item bodies hold per-item state.
- `proguard-rules.pro:5-7` — the three `-keep … { *; }` rules. Unchanged from run 1; still wants
  a release build to verify removal.

### Checked and found sound

Recorded so later runs do not re-open them. `AddExpenseDialog` shows each member's share with
`evenShares(amountMinor, sharedWith.toList().sorted())` and confirms with the same
`sharedWith.toList().sorted()`, which `GroupRepository.addExpense` feeds to the same
`evenShares` — displayed and recorded shares cannot disagree. `evenShares` and `equalSplit` both
add back up to the whole for negative totals as well as positive. `GroupRepository.recordTransfer`
credits the payer and debits the payee by the same figure, so group balances still net to zero
after a settle-up. `PersonDao.clearTransactionsForPerson` reads and rewrites the same
transactions-only sum it deletes, so "The current balance won't change" is true even for someone
with group activity. `GroupDetailScreen` prefixes its LazyColumn keys (`balance-`/`expense-`),
so the shared key space does not collide. All of run 1's eight fixes are still in place — no
regressions.
