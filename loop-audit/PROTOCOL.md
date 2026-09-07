# MeraPaisa — Review & Fix Protocol

One run = one full review of the codebase, plus fixes. This gets run 10 times,
each in a fresh session. Read this file at the start of every run.

## Project facts

- MeraPaisa. Kotlin + Jetpack Compose, Gradle Kotlin DSL, version catalog
  (`libs.*`), KSP. Module: `app`. Package: `com.kg.merapaisa`.
- compileSdk 36, minSdk 24, targetSdk 36. Shipping v2.0.2 — live code with
  real users, not a prototype. Personal financial data.
- Room, with exported schemas in `app/schemas/`.
- `keystore.properties` is gitignored by design. Confirm once it's ignored and
  that no key material is inlined in a build file, then never raise it again.
- One physical Android device is connected over adb.
- Under git on `main`. You may run `git status` and `git diff`. You may NOT
  commit, stage, stash, checkout, branch, or reset — the human does all of
  that.

## Every run

### 1. Read the whole tree, fresh

Read every Kotlin file, the manifest, the Gradle files, the version catalog,
ProGuard rules, and the Room schemas. Fresh every run — do not lean on what a
previous run concluded, and do not skip a file because it seems familiar. If
you worked on this code recently, ignore that; judge what is actually there.

### 2. Get real evidence
First, confirm the device is unlocked:
`adb shell dumpsys window | grep -E "mDreamingLockscreen|mShowingLockscreen"`
If the keyguard is showing, STOP and tell the human. Do not proceed with a
partial run — monkey, input, rotation, and back-navigation all silently
do nothing behind a lock screen, and the run will look complete while
missing half its evidence.

```
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew installDebug
```

Then on the device: resolve the launcher activity with
`adb shell cmd package resolve-activity --brief com.kg.merapaisa | tail -1`,
`adb logcat -c`, launch the app, and drive it —
`adb shell monkey -p com.kg.merapaisa -s 42 --throttle 300 --pct-syskeys 0 300`
plus `adb shell input` through the main flows. Use seed 42 every run so runs
compare. Also try rotation, process death (`adb shell am kill`), and no
network. Then read `adb logcat -d`.

### 3. Find problems

Four lenses, all of them, every run:

**Crashes & correctness** — exceptions and force-closes in logcat; ANRs;
main-thread I/O; coroutine scope misuse and leaked jobs; missing cancellation;
state lost across rotation and process death; navigation dead ends; Flow
collection that isn't lifecycle-aware; races on shared state; swallowed
errors. Room specifically: every `@Database` version bump needs a matching
migration in `app/schemas/`. A version bump with no migration and
`fallbackToDestructiveMigration` is silent user data loss — highest priority.

**Performance & memory** — unnecessary recomposition (unstable params, lambdas
allocated in composition, missing `remember`); wrong `LaunchedEffect` keys;
missing `key()` in lazy lists; work during composition; unbounded caches;
Context held by ViewModels or singletons; leaked listeners; startup work on
the critical path.

**Security & data** — hardcoded secrets or endpoints; `allowBackup`,
`debuggable`, cleartext traffic; exported components without permission;
intents that trust extras; WebView settings; tokens or PII in
SharedPreferences, Room, or logs; missing TLS validation; broad permissions;
ProGuard rules that leave model field names readable in release.

**Architecture** — UI touching data sources directly; business logic in
composables; state not hoisted; God-composables; duplicated logic; dead code;
untestable seams; missing tests on paths that just produced a serious finding.

A problem is **serious** only if you can attach evidence: a log line, a stack
trace, a measured number, a `dumpsys` output, or an exact reproduction. If you
believe something is bad but can't evidence it, it isn't serious — write it
down and move on.

### 4. Fix what you found, this run only

Fix everything serious. Fix minor things only when the fix is local and
low-risk. Do not fix something you noticed but didn't write down this run —
leave it for the next run to find. One problem, one focused change.

Before editing a file for the first time this run, copy it to
`loop-audit/backups/run-N/` keeping its path. That copy is your undo.

### 5. Verify

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew installDebug
```

Re-run the same device scenario, same monkey seed. Fixes are accepted only if
the build is green, tests are no worse than at the start of the run, and the
evidence for each fixed problem is gone.

If a fix fails verification, retry once. If it fails again, restore the file
from `loop-audit/backups/run-N/`, note it as reverted, and move on. Never end
a run with a project that doesn't build.

### 6. Write the ledger

Append to `loop-audit/ledger.md`:

```
## Run N — <date>
Files read: <count>. Lint: <n> issues. Tests: <pass>/<fail>.
Fixed:    <file:line> — <problem> — <what changed> — <evidence it's gone>
Reverted: <file:line> — <problem> — <why the fix failed>
Noted:    <file:line> — <minor thing, left alone>
```

Read this ledger at the START of every run. If something listed as Fixed shows
up again, it is a regression — say so explicitly and treat it as the highest
priority item of the run.

## Rules

- Edit `app/src/**` only. Gradle, manifest, and ProGuard only when a specific
  problem requires it.
- No new dependencies. No version bumps to AGP, Kotlin, Gradle, KSP, or
  Compose. No adding detekt or ktlint.
- No new features, no speculative abstractions.
- No renaming, reordering, extracting, or reformatting unless a specific
  problem you found this run requires that exact change.
- Don't bump versionCode or versionName.
- Don't delete or weaken tests to make a build pass.
- No `TODO`, stubs, or commented-out code left behind. If a fix can't be done
  properly, revert it and note it.
- If the same problem gets reverted three runs in a row, stop raising it. Note
  it as needing a human decision.

## When stuck

Don't guess. Note the blocker in the ledger and move to the next problem. Stop
and report only if the device disconnects and doesn't return after
`adb kill-server && adb start-server`, or the project won't build.

## End of run

Stop after finishing one run. Do not start the next one.
