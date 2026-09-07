# MeraPaisa — Audit & Repair Loop Protocol

You are running an audit-and-repair loop on this Android project. It runs for
as many loops as it takes to converge — the stopping condition is defined
below, not a fixed count. Work autonomously. Do not stop to ask for approval
between loops.

## Project facts

- App: MeraPaisa, Kotlin + Jetpack Compose, Gradle Kotlin DSL, version catalog
  (`libs.*`), KSP.
- Module under audit: `app`
- applicationId / namespace: `com.kg.merapaisa`
- compileSdk 36, minSdk 24, targetSdk 36. Currently shipping v2.0.2
  (versionCode 7) — this is live code with real users, not a prototype.
- Room is in use with exported schemas in `app/schemas/`.
- Release signing reads `keystore.properties`, which is gitignored by design.
- `proguard-rules.pro` exists and is in scope.
- testInstrumentationRunner: `androidx.test.runner.AndroidJUnitRunner`
- Launcher activity: `<LAUNCHER_ACTIVITY>`   <-- FILL THIS IN BEFORE LOOP 1
- One physical Android device is connected over adb. Use it every loop.
- The project IS under git, on branch `main`. You may run `git status` and
  `git diff` to review your own changes. You may NOT commit, stage, stash,
  checkout, branch, reset, or otherwise write to git — the human handles all
  of that. Rollback within a loop is via the file snapshots in Phase A.

## Preflight (once, before loop 1)

1. `adb devices` — require exactly one device in `device` state. If zero or
   more than one, STOP and report. Do not substitute an emulator.
2. Read the Gradle files and the version catalog. Record: AGP version, Kotlin
   version, compileSdk, minSdk, targetSdk, Compose BOM/compiler version, KSP
   version, and every dependency. Everything you do later must stay inside
   these versions.
3. `mkdir -p loop-audit/backups`
4. Establish a baseline in `loop-audit/baseline.md`:
   - `./gradlew assembleDebug`
   - `./gradlew installDebug`
   - cold start: `adb shell am force-stop com.kg.merapaisa` then
     `adb shell am start -W -n com.kg.merapaisa/<LAUNCHER_ACTIVITY>` —
     record TotalTime
   - `adb shell dumpsys gfxinfo com.kg.merapaisa framestats` — record janky
     frame %
   - `adb shell dumpsys meminfo com.kg.merapaisa` — record TOTAL PSS
   - debug APK size
   - `./gradlew testDebugUnitTest` — record pass/fail counts
   These five numbers are the yardstick for every later comparison.
5. Create `loop-audit/STATE.json`:

   ```json
   {"loop": 0, "new_p0_p1_this_loop": null, "consecutive_clean_loops": 0,
    "full_tree_covered": false, "regressions_this_loop": 0,
    "converged": false, "reason": "not started"}
   ```

   You rewrite this at the end of every loop. A shell driver reads it to
   decide whether to run another loop, so it must always be valid JSON.

## The loop (repeat until the convergence test passes, N = 1, 2, 3, ...)

### Phase A — Snapshot

`cp -a app/src loop-audit/backups/loop-N-src` and copy every Gradle
build/config file alongside it. This snapshot is the rollback path inside a
loop. Never edit anything inside `loop-audit/backups/`.

### Phase B — Critique the whole codebase, fresh

Re-read the source tree from scratch this loop. Do not rely on notes,
conclusions, or a file map from an earlier loop — re-derive them. List every
Kotlin file, every Compose screen, the manifest, all Gradle files, the version
catalog, ProGuard rules, Room schemas, and every resource directory, then
actually open them. Coverage of the whole tree matters more than depth on any
one file; if the tree is large, work package by package until all of it has
been read this loop.

Run the static tooling and read the output, don't just note that it ran:

- `./gradlew lintDebug` (read the report, not just the exit code)
- `./gradlew detekt` and `./gradlew ktlintCheck` only if already configured —
  do not add them
- `./gradlew testDebugUnitTest`
- Compose compiler metrics if they can be enabled through existing config:
  look at stability inference and restartable/skippable counts

Then get device evidence for this loop:

- `./gradlew installDebug`
- `adb logcat -c`, launch the app, then drive it:
  `adb shell monkey -p com.kg.merapaisa -s 42 --throttle 300 --pct-syskeys 0 300`,
  plus targeted `adb shell input` sequences for the app's main flows. Use seed
  42 every loop so runs compare.
- Exercise the hard paths deliberately: rotation, process death
  (`adb shell am kill com.kg.merapaisa` then relaunch), airplane mode / no
  network, and back-navigation to the root.
- Capture `adb logcat -d` and re-collect the same startup, jank, and memory
  metrics as the baseline.

Critique through all four lenses. Every finding needs a file:line and concrete
evidence — a log line, a metric delta, a code path — not a general principle.

**1. Crashes & correctness** — unhandled exceptions and force-closes in
logcat; ANRs; main-thread I/O or blocking calls; coroutine scope misuse and
leaked jobs; unstructured concurrency; missing cancellation; null and index
assumptions; state lost across rotation and process death (SavedStateHandle,
rememberSaveable); back-stack and navigation dead ends; Flow collection that
isn't lifecycle-aware; races on shared mutable state; error paths that
silently swallow failures.

Room specifically: check every `@Database` version bump against
`app/schemas/` for a matching migration. A version increment with no migration
and `fallbackToDestructiveMigration` in place is silent user data loss on
update — treat it as P0. Verify migrations actually run by installing over the
previously shipped APK rather than a clean install.

**2. Performance & memory** — unnecessary recomposition (unstable parameters,
lambdas allocated in composition, missing `remember`, `derivedStateOf`
candidates); `LaunchedEffect`/`DisposableEffect` with wrong keys; missing
`key()` in `LazyColumn`/`LazyRow` items; work done during composition or in
draw/layout; allocations in hot paths; unbounded lists or caches; image
decoding and sizing; Context or View references held by ViewModels or
singletons; leaked listeners, receivers, and observers; startup work on the
critical path; jank and memory measured against baseline.

**3. Security & data handling** — hardcoded secrets, keys, or endpoints;
`android:allowBackup`, `android:debuggable`, cleartext traffic and network
security config; exported activities/services/receivers/providers without
permission (cross-check `adb shell dumpsys package com.kg.merapaisa`); intent
handling that trusts extras; WebView settings (JS enabled, file access,
`addJavascriptInterface`); tokens or PII in SharedPreferences, Room, logs, or
crash reports; missing TLS validation or custom TrustManagers; broad or unused
permissions; missing/incorrect ProGuard and R8 rules; PendingIntent
mutability flags.

`keystore.properties` is intentionally gitignored and read at build time.
Confirm once that it is in `.gitignore` and that no key material is inlined in
a build file; after that, do not raise it again. This app handles personal
financial data, so weight the following heavily: what is written to the Room
database unencrypted, what reaches logcat in release builds, what lands in
SharedPreferences, and whether ProGuard rules leave model classes and their
field names readable in the release APK.

**4. Architecture & code quality** — layering violations and UI touching data
sources directly; God-classes and God-composables; state that isn't hoisted;
business logic inside composables; DI wiring inconsistencies; duplicated
logic; dead code and unused resources; untestable seams (hard-coded
dispatchers, static singletons); naming and module boundaries; missing test
coverage on the code paths that just produced P0/P1 findings.

Write `loop-audit/loop-N-critique.md`. It must OPEN with a coverage inventory:
every source file in the tree, each marked READ or SKIPPED-with-reason this
loop. A loop that did not cover the tree cannot count as a clean loop, no
matter what it found.

Then one row per finding:

`ID | severity | lens | file:line | evidence | why it matters | proposed fix`

Severity: **P0** crash, data loss, or exploitable security hole. **P1** wrong
behaviour, leak, measurable performance regression, or sensitive-data
exposure. **P2** contained code-quality or robustness problem. **P3** cosmetic
or stylistic.

Evidence gates severity, and you do not get to argue around it. A finding is
P0 or P1 only if you can attach one of: a logcat line, a stack trace, a
measured metric delta against baseline, a `dumpsys` output, or an exact input
sequence that reproduces the behaviour. A finding you believe is serious but
cannot evidence is a P2. Do not promote a P2 to keep the loop alive, and do
not demote a P0 to end it — both corrupt the stopping condition, which is the
only thing that says when this run is finished.

Cross-check against `loop-audit/ledger.md` (create it in loop 1). If a finding
was fixed in an earlier loop and has come back, mark it REGRESSION and treat
it as P0 regardless of its original severity.

### Phase C — Fix, this loop's findings only

Fix every P0 and P1 from THIS loop's report, plus P2s whose fix is local and
low-risk. Log P3s to the ledger and leave them. Do not fix something you
noticed but did not write into this loop's report — save it for the next
loop's critique. One finding, one focused change; no drive-by refactors
bundled in.

### Phase D — Verify on the device

`./gradlew assembleDebug`, `./gradlew testDebugUnitTest`,
`./gradlew installDebug`, then re-run the exact same device scenario (same
monkey seed, same input sequence) and re-collect startup time, jank %, and PSS.

A loop's fixes are accepted only if: the build is green, unit tests are no
worse than at loop start, the specific logged evidence for each fixed finding
is gone, and no baseline metric has regressed more than 10%.

If verification fails, retry the fix once. If it fails again, restore the
affected files from `loop-audit/backups/loop-N-src`, mark the finding REVERTED
with the reason, and move on. Never end a loop with a project that does not
build or install.

### Phase E — Close and reset

Write `loop-audit/loop-N-report.md`: findings fixed, exact files changed,
verification evidence, metric deltas vs baseline, and anything deferred or
reverted with the reason. Append all outcomes to `loop-audit/ledger.md`. Then
rewrite `loop-audit/STATE.json`:

```json
{"loop": N,
 "new_p0_p1_this_loop": 0,
 "consecutive_clean_loops": 0,
 "full_tree_covered": true,
 "regressions_this_loop": 0,
 "converged": false,
 "reason": "one sentence: why it converged, or what is still open"}
```

Then discard your working context for this loop — the file map, the mental
model, the shortlist of "the files that matter." Loop N+1 begins with a full
re-read of the tree as if you had never seen it. If loop N+1 runs as a
separate process, the process boundary does this for you; if you are
continuing in the same session, do it deliberately. A loop that only
re-examines what the previous loop touched is a failed loop.

## Scope boundaries

- May edit: `app/src/**`, and Gradle/manifest/ProGuard files only when a
  specific finding requires it.
- May not: add, remove, or version-bump any dependency; change AGP, Kotlin,
  Gradle wrapper, KSP, or Compose versions; add lint/detekt/ktlint tooling
  that isn't already configured; touch `loop-audit/backups/**`; write to git;
  modify anything outside this repo.
- No new features, no speculative abstractions, no reformat-only diffs.
- No `TODO`, stub, placeholder, or commented-out code left behind. If a fix
  can't be completed properly, revert it and log it instead.
- Do not delete or rewrite existing tests to make a build pass.
- Do not bump versionCode or versionName.

## Churn brakes

An open-ended loop drifts into rewriting things for taste. These stop that.

- **Per-file freeze.** If a file has been edited in three loops and every
  finding against it since has been P2 or P3, add it to
  `loop-audit/frozen.md` and stop editing it. Only a new evidence-backed
  P0/P1 unfreezes it.
- **No taste edits.** Renaming, reordering, extracting functions, rewriting
  comments, and reformatting are forbidden unless a specific numbered finding
  in the current loop requires that exact change.
- **Intractable findings.** If the same finding is raised in three loops and
  the fix is reverted each time, mark it INTRACTABLE in the ledger, stop
  raising it, and carry it to the final summary as needing a human decision.
- **No new abstractions.** You may not introduce an interface, wrapper, base
  class, or module boundary that no finding demands.

## Convergence test (end of every loop)

The run is converged when ALL of these hold:

1. Two consecutive loops produced zero new P0 and zero new P1 findings.
2. Both of those loops covered the full source tree, per their coverage
   inventories.
3. Zero regressions in those two loops.
4. Build green, unit tests no worse than baseline, and no baseline metric
   regressed more than 5% — stricter than the per-loop gate, because what is
   being checked here is cumulative drift across the whole run.

P2 and P3 findings never block convergence. If they did this would never
terminate; there is always another P3.

A loop that produced no P0/P1 but skipped part of the tree resets
`consecutive_clean_loops` to 0. So does any loop containing a regression.

## Ceiling

Stop unconditionally at loop 25 even if not converged, and say so plainly in
the summary rather than implying the work is done. Also stop and report if
three consecutive loops produce fixes with no measurable metric movement and
no P0/P1 — that means the remaining problems need a person, not another pass.

## When blocked

Do not guess. Write the blocker to the current loop's report and continue with
the next finding. Escalate — stop the run and print a summary — only if: the
device disconnects and doesn't return after `adb kill-server && adb
start-server`; the baseline build fails before loop 1; or a snapshot restore
fails and the project is left un-buildable.

## Final summary

When `converged` is true, or the ceiling is hit, write
`loop-audit/summary.md`: all findings by severity across all loops; what was
fixed vs deferred vs reverted; every regression caught; final metrics against
baseline; how many loops it took; at which loop each lens stopped producing
P0/P1 findings; everything frozen or marked intractable; and a ranked list of
what a human should look at next, with reasons.
