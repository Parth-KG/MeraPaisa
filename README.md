<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/hero-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="docs/assets/hero-light.svg">
  <img alt="Mera Paisa: who owes whom, in everyone's own currency" src="docs/assets/hero-light.svg" width="100%">
</picture>

<br>

[![Download APK](https://img.shields.io/github/v/release/Parth-KG/MeraPaisa?label=Download%20APK&color=E8A33D&labelColor=171C24&style=for-the-badge)](https://github.com/Parth-KG/MeraPaisa/releases/latest)

[![Android](https://img.shields.io/badge/android-7.0%2B-6FBF8B?style=flat-square&labelColor=171C24&logo=android&logoColor=white)](#build-from-source)
[![Kotlin](https://img.shields.io/badge/kotlin-100%25-E8A33D?style=flat-square&labelColor=171C24&logo=kotlin&logoColor=white)](#tech)
[![Compose](https://img.shields.io/badge/jetpack%20compose-Material%203-89B4FA?style=flat-square&labelColor=171C24)](#tech)
[![License](https://img.shields.io/badge/license-MIT-A2AAB6?style=flat-square&labelColor=171C24)](LICENSE)

<br>

<table>
<tr>
<td align="center" width="33%"><img src="docs/screenshots/balances.jpeg" width="235" alt="Balances screen in the Neel theme, navy on cool white. Overall, owed to you ₹13,505.50 and $1,050.25 on separate lines above a double rule, then four people: Asha owes you ₹1,200, you owe Bilal ₹40, Chaitanya owes you ₹12,345.50, Diego owes you $1,050.25. Add a person and Split an expense sit at the foot"><br><sub><b>Balances</b><br>one figure per currency, never summed</sub></td>
<td align="center" width="33%"><img src="docs/screenshots/split.jpeg" width="235" alt="Adjust the shares screen in the Diya theme, marigold on blue-grey, splitting ₹2,400 for dinner at Trishna four ways. Asha is locked at ₹800 and Bilal at ₹533.33; you take ₹533.34 and Chaitanya ₹533.33, and the shares add up to ₹2,400"><br><sub><b>Split</b><br>edit one share, the rest redistribute</sub></td>
<td align="center" width="33%"><img src="docs/screenshots/groups.jpeg" width="235" alt="Group screen for Goa, October in the Kamal theme, pine green with a lotus-pink accent: four members and three expenses. Who pays whom lists three payments to you of ₹4,500 each, from Asha, Bilal and Chaitanya, with fewest payments on. Where everyone stands shows you owed ₹13,500 and each of the other three owing ₹4,500"><br><sub><b>Groups</b><br>a trip, kept apart from your balances</sub></td>
</tr>
</table>

</div>

---

## Install

Grab the APK from the **[latest release](https://github.com/Parth-KG/MeraPaisa/releases/latest)** and open it on your phone. Android 7.0 or newer.

Android warns about installing outside the Play Store, which is expected for a directly distributed APK. You will need to allow installs from your browser or file manager once.

Every release lists the APK's SHA-256. Run `shasum -a 256` on the file if you would rather verify than trust.

> **Updating?** Install over the top. Every schema change ships with a tested migration, so an update carries your ledger forward. Uninstalling deletes it, so do not uninstall first.

## Why

Splitting an expense is easy. Remembering it three weeks later is not, and every app that solves it wants an account, a phone number, and a copy of who you owe money to.

Mera Paisa keeps all of that on your phone.

## Features

| | |
|---|---|
| **Per-person balances** | Each person has their own currency, picture and history |
| **Net position** | Where you stand overall, one line per currency, never added together |
| **Multi-currency** | ₹, $, €, £, ¥ with live rates from [Frankfurter](https://www.frankfurter.app/). Balances stay in each person's currency and are never silently converted |
| **Expense splitting** | Equal or custom shares, with lock-on-edit redistribution |
| **Groups** | A trip or a flatshare: log who paid for what, then settle everyone in the fewest payments. Kept apart from your personal balances, in its own currency |
| **Settle up** | Close a balance after one confirmation, and reopen it later without losing its history |
| **Move a debt** | Hand part of what one person owes onto someone else, converted if they keep a different currency |
| **History** | Notes, timestamps, edit or delete any entry, reverse an entry and everything newer, or clear the history |
| **Export and summaries** | The whole ledger as CSV, or a plain-text summary of one person's balance |
| **Update links (beta)** | Send a link that records the mirror of your entries in their app, and compares before writing |
| **Reminders** | An editable message, optionally with the history attached |
| **Home screen widget** | Your net position and who owes what, in your chosen theme |
| **App lock** | Your fingerprint, face or screen lock before the ledger opens, and nothing on the widget or in screenshots while it is locked |
| **In-app updates** | Checks GitHub for a newer release and installs it only if it is signed with the same key |
| **Accessible** | TalkBack reads every figure with its direction in words, and every screen works at large text and on a phone turned sideways |
| **Ten themes** | Diya, Jamun, Monsoon, Kaapi and Kamal by night, Tulsi, Khadi, Gulab, Kansa and Neel by day, with amounts and grey text at 7:1 in every one |
| **Backup** | Everything in one file, saved when you like or every week to a folder you pick, with a preview before any restore. Android's own backup also copies your ledger and settings to your Google account |

## Worth a closer look

> **Money is never a floating-point number**
> Amounts are whole minor units, paise or cents, not `Double`. `10.50` is `1050`, and it is still `10.50` after a hundred additions. Currencies with no minor unit in circulation, like the yen, are stored the same way and only lose their decimals when displayed.

> **The balance is not a stored number**
> There is no balance column to drift out of step with the history. A balance is the sum of that person's entries, derived on read, so the figure on screen and the entries behind it cannot disagree.

> **An update never changes what you are owed**
> The database has been through ten schema versions with seven hand-written migrations, each tested against a seeded database before release. There is no destructive fallback anywhere, so no update can wipe a ledger. The version that removed the stored balance column had to prove first that the entries summed to it, and wrote a reconciling entry where they did not.

> **Lock-on-edit redistribution**
> Change one person's share and that row locks, and the remaining unlocked rows split what is left. Leftover paise go to the first person rather than quietly disappearing. If the locked shares leave nothing that adds up, the screen says by how much and why, and the split cannot be saved until it does, because a split that does not add up records money nobody spent.

> **Settling a group in the fewest payments**
> Inside a group, who paid whom for any single expense stops mattering once you only care about ending even. All that survives is each member's net position. If A owes B and B owes C the same amount, B is a pass-through and A can pay C directly. Matching the largest debtor against the largest creditor settles at least one member with every payment, so a group of *n* people never needs more than *n* minus 1 payments. You can turn this off per group and keep every debt attached to the expense that created it. Either way, a group where everyone is even asks for no payments at all.

> **Two ledgers that can disagree, and say so**
> An update link carries a stable id per entry, so the receiving app can tell a new entry from an edited one from one the sender deleted. It shows you the differences and writes nothing you have not ticked. Additions are ticked for you; an edit or a deletion never is, because nothing about a link proves who sent it. The ids of entries you delete or clear are kept, so the other phone's next link cannot quietly bring them back: a cleared entry already counts in the opening balance, and a deleted one arrives unticked for you to decide.

> **No account, no backend**
> Data lives in Room on the device and rides Android's own backup to your Google account, which is a system feature rather than anything of ours. Reinstall on a new phone that uses the same Google account and it is there. There is no account to create, no server, and nothing of yours on a machine I control. Exactly three things leave the phone: that system backup, links and messages you choose to send, and requests to Frankfurter for an exchange rate and GitHub for an update check. No names, no notes, no analytics, no crash reporting. The manifest asks for `INTERNET` for those requests, and `REQUEST_INSTALL_PACKAGES` so the updater can hand a checked APK to Android's installer. Not contacts, not storage, not location.

<details>
<summary><b>How each part works</b></summary>

<br>

### Balances

Add people with names, pictures and a currency; a new person starts in the currency you used last. Tap a name to open the numpad, type the amount, add a note if you want one, and say which way it went: **You paid for them** or **They paid for you**. The top of the list shows where you stand overall, a line per currency, closed with a double rule. Long-press a name for everything else: settle up, a reminder, a summary, an update link, moving the debt, editing or deleting them.

### Active, Settled, and settling up

A person is in **Settled** because you put them there, not because their balance happened to reach zero. **Settle up** says the amount it will record and asks first, then records a closing entry for exactly what is outstanding and files them away. **Reopen** brings them back without resurrecting the old balance, since the closing entry is real history and stays. Recording money against a settled person reopens them: if you are lending again, the balance is live again.

### Multi-currency

Each person has their own currency and balances stay in it. Your net position is reported per currency and never summed across them: a rate is a guess about a day, and folding ₹ and $ together would report a number nobody owes. When a split or a moved debt crosses currencies, amounts convert through Frankfurter at the day's rate, fetched once and reused while you work, and are recorded in each recipient's own currency. Changing someone's currency can rewrite their whole history at that rate, or just relabel it; the first is irreversible, and the dialog says so.

### Splitting an expense

1. Tap **Split an expense**, enter the total, and pick the currency.
2. Choose people, including yourself if you are part of it.
3. If someone is not in your ledger yet, tap **Add a person** in the picker, then tap their name to bring them in.
4. Adjust the shares. Equal by default. Editing a row locks it and redistributes the rest, and the padlock toggles that by hand.
5. An optional note attaches to every entry the split creates.

### Groups

A group is a trip, a flatshare, a dinner: anywhere several people keep paying on each other's behalf. You are always a member, because a trip you are not part of is somebody else's ledger. A group is its own ledger in its own currency: nothing in it changes anyone's balance on Active or Settled, and settling someone there leaves their groups alone. Anyone can join a group, whatever currency their own balance is kept in.

Log an expense with a description, an amount and who paid. Shares are equal by default and split so the parts always add back to the whole, since paise cannot be divided three ways. Each member's position is what they paid out less what they were assigned, so a group always nets to zero.

**Settle up** turns those positions into a list of payments, and records each one when it happens. **Edit group** renames it or brings more people in; someone who joins late owes nothing for what was spent before. Deleting an expense or a group asks first, and touches nothing outside the group. Deleting a member hands their share of everyone else's expenses to whoever paid, so the group still adds up.

### History, corrections and rollback

Every person has a log, grouped by day. Tap an entry to correct its amount or note, or delete it after a confirmation, because a mistyped figure does not have to live in the history forever. **Reverse entries** is the bulk version: the undo arrow beside an entry names the amount and adds one entry that cancels it and everything newer, rather than deleting rows, so the correction stays visible as a correction. **Clear history** asks first and carries the balance across as an opening balance, so what you are owed never changes, and an update link sent afterwards does not send the other phone what it already has.

### Schema changes and updates

Room's escape hatch for a changed schema is to drop the database and rebuild it. That is disabled here. Every migration is written by hand and tested by creating a database at the old version, seeding it with rows in the old shape, running the real migration and asserting on the result, including that the resulting schema is exactly what Room expects. A subtly wrong migration fails a test rather than a phone. The full chain is tested end to end as well, because a set of passing pairs proves nothing about the sequence a real upgrade runs.

The interesting one is version 4, which removed the stored balance column. Dropping it is only safe if the entries already sum to it, and in real ledgers they sometimes did not, because early versions wrote balances without logging an entry. So the migration compares each person's rows against their stored balance first and writes a single reconciling entry for any difference, dated before their earliest entry. Nobody's balance moved. That is also why there is no balance column now: the two numbers could disagree, so one of them had to go.

### Export, summaries and update links

**Export as CSV**, in Settings, writes the whole ledger to CSV and hands it to the share sheet. **Share a summary** sends one person's position and recent activity as plain text, with the running balance after each entry, ready to paste into a chat.

An **update link** is different: it carries your entries so their app can record the mirror, and both ledgers end up agreeing. The payload rides in the URL fragment, which is never sent to a server, so the page hosting the link cannot see anything. A link is unauthenticated by nature, so the import screen shows exactly what it will write and never applies anything without a tap.

### Reminders

Long-press a name and choose **Send a reminder**. An editable message appears with the exact amount in it, and you can attach the history. It goes out through Android's share sheet, so anything that accepts text works.

### Themes

Ten, dark then light: **Diya**, **Jamun**, **Monsoon**, **Kaapi** and **Kamal**, then **Tulsi**, **Khadi**, **Gulab**, **Kansa** and **Neel**. Settings, **Theme** opens your own balances drawn in whichever theme you tap, and nothing changes until you tap **Use**. Each sets every colour it uses, down to its key colour, its dividers, its button edges and the text on its buttons. Amounts and grey text reach 7:1 against the page and against a highlighted row, past the WCAG AAA line, everything else clears 4.5:1, and the edges of fields, buttons and switches reach 3:1; tests enforce it rather than an eye. Direction is written as well as coloured: what you owe carries a real minus sign, what you are owed carries none, and the words beside each figure say which way it runs, so telling green from red is never the only way to read a balance.

Some start from palettes other people made, all under the MIT licence: Monsoon is [Catppuccin](https://github.com/catppuccin/catppuccin) Mocha, Khadi is [Flexoki](https://stephango.com/flexoki)'s light scheme by Steph Ango, and Jamun is [Pastelón de Amarillos](https://github.com/sonofmartinus/pastelon-de-amarillos-tinted-theme) by Richard Martinez. Gulab takes its colours from the [traditional colours of Japan](https://nipponcolors.com/). Where a theme departs from its source, it is to reach those contrast figures.

### Backup and restore

Android's own backup carries the database to your Google account. That is a system feature, not something Mera Paisa runs. There is also a backup you control, under **Back up and restore** in Settings: **Save a backup** writes everything to one file, and **Restore from a file** reads it back, either adding what is missing or replacing everything. **Choose a folder** and it saves one there every week, keeping the last 12. A restore is previewed before it runs, and the whole thing is one database transaction, so a failure changes nothing.

</details>

## Tech

Kotlin and Jetpack Compose with Material 3. Room for storage, with exported schemas and hand-written migrations. DataStore for preferences. Glance for the widget. WorkManager for the weekly backup. Coil for pictures. No dependency injection framework and no navigation library: screens switch on state.

Type is Anek Latin by Ek Type for amounts and titles and Figtree for everything else, shipped as static subset instances because API 24 and 25 ignore variable font axes.

421 tests on the JVM covering money arithmetic, settle-up, per-currency totals, amount formatting, CSV, summaries, the share codec and theme contrast, and 184 instrumentation tests covering every migration, the full migration chain, and the settle, reopen, rollback, edit, delete, reconcile and move-debt paths against a real database.

## Build from source

```sh
git clone https://github.com/Parth-KG/MeraPaisa.git
cd MeraPaisa
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Debug builds install as `com.kg.merapaisa.debug`, alongside a release build rather than replacing it, so testing cannot put a real ledger at risk. Release signing is described in [docs/RELEASE.md](docs/RELEASE.md).

## Status

Working and in daily use. Built for me and a few friends, so the roadmap is whatever annoys us next.

## License

MIT. See [LICENSE](LICENSE). The fonts (SIL Open Font License) and the Catppuccin, Flexoki and Pastelón de Amarillos palettes (MIT) keep their own licences, in [third_party](third_party).
