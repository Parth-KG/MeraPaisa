# Share links (v2.1, Phase D)

How the two-sided ledger travels, and the one hosting step that is not in this repo.

## The link

```
https://parth-kg.github.io/MeraPaisa/s#<payload>
```

The payload is deflated and Base64URL-encoded, and it sits **after the `#`**. That placement is
the privacy guarantee, not a formatting choice: a URL fragment is never sent to the server, so the
host serves `docs/s/index.html` and never sees a single amount, name or note. Moving the payload
into the path or the query would turn this into a feature that uploads people's debts to GitHub.

`merapaisa://share#<payload>` is also accepted. It needs no hosting, so it works the moment a
build is installed.

## What must be hosted, and where

Two separate things, and they do **not** live in the same place.

### 1. The fallback page — this repo

`docs/s/index.html` is served at `https://parth-kg.github.io/MeraPaisa/s` once GitHub Pages is
enabled for this repository:

> Settings → Pages → Source: *Deploy from a branch* → Branch `main`, folder `/docs`

Anyone without the app, or whose Android has not yet verified the link, lands here and is told what
to do.

### 2. `assetlinks.json` — a *different* repo

This is the step that is easy to get wrong. Android verifies App Links against the **domain root**:

```
https://parth-kg.github.io/.well-known/assetlinks.json
```

It will **not** look at `https://parth-kg.github.io/MeraPaisa/.well-known/assetlinks.json`. Because
`parth-kg.github.io` is a GitHub *user* site, its root is served by a repository named
`Parth-KG.github.io` — not by this one. So:

1. Create a public repo named exactly **`Parth-KG.github.io`**.
2. Copy `docs/.well-known/assetlinks.json` from this repo to `.well-known/assetlinks.json` at that
   repo's root.
3. **Add an empty `.nojekyll` file at that repo's root.** This is not optional and it is the easiest
   step to miss: GitHub Pages runs Jekyll, and Jekyll skips any directory whose name begins with a
   dot. Without `.nojekyll`, `.well-known/assetlinks.json` returns 404 no matter how correct its
   contents are, and App Links silently never verify. (`docs/.nojekyll` in this repo does the same
   job for the copy kept here.)
4. Enable Pages on it.
5. Confirm it is live and served as JSON:
   ```sh
   curl -sI https://parth-kg.github.io/.well-known/assetlinks.json | head -1
   curl -s  https://parth-kg.github.io/.well-known/assetlinks.json
   ```

The copy in `docs/.well-known/` is kept here as the source of truth for its contents. It is not
the copy Android reads.

### Until that file is live

A tapped link opens a browser or a chooser rather than the app. **This is not a broken feature** —
the app accepts a pasted link at *Settings → Record a shared update*, which is why that path
exists and why the fallback page explains it. Everything works; it is one tap longer.

## The fingerprint

`assetlinks.json` names the release signing certificate:

```
4D:5C:0A:55:33:71:BC:A5:AB:BF:44:0E:33:49:7D:B8:25:DD:28:01:4F:0A:BE:59:BC:0D:10:4F:31:DA:22:44
```

Taken from the shipped APK rather than typed from memory:

```sh
apksigner verify --print-certs merapaisa-v2.0.2.apk
```

Two consequences worth knowing:

- **Debug builds will never auto-verify.** They are `com.kg.merapaisa.debug` and are signed with the
  per-machine debug key, so neither the package name nor the fingerprint matches. Test the deep link
  with a release build; use paste for debug.
- **If the signing key ever changes, this file must change too** — and see `docs/RELEASE.md` for why
  changing the key is already close to unthinkable.

## Verifying on a device

```sh
# Did Android verify the domain for the installed app?
adb shell pm get-app-links com.kg.merapaisa

# Force a re-check after publishing assetlinks.json
adb shell pm verify-app-links --re-verify com.kg.merapaisa

# Fire a link straight at the app, bypassing verification entirely
adb shell am start -a android.intent.action.VIEW \
  -d "merapaisa://share#PASTE_A_BLOB_HERE"
```

`pm get-app-links` should report `verified` for `parth-kg.github.io`. While it says
`legacy_failure` or `1024`, the file is not being served correctly — usually Pages not enabled, or
the file at the project path instead of the domain root.

## What a link cannot do

- **It is unauthenticated.** Anyone can craft one; nothing proves the sender. The import screen
  states this and never writes without a confirmation tap. That tap is the only check there is.
- **It cannot apply twice.** Each payload carries an id, recorded in `applied_payloads`. Forwarding
  a link or tapping it twice does nothing the second time.
- **It cannot cross currencies.** A payload's amounts are minor units with no rate attached, so
  importing INR onto a USD person is refused rather than converted at a rate the sender never
  agreed to.
- **It cannot exhaust your phone.** Entry count, note length, amount size and inflated size are all
  capped, because a link is hostile input that arrives by being tapped.
