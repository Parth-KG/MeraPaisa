package com.kg.merapaisa.ui.whatsnew

/**
 * One version's headline changes, in plain words, for the "What's new" sheet after an update.
 *
 * Written at every release, before the version bump (docs/RELEASE.md), most important first: after
 * skipped versions the sheet takes each version's first line before anyone's second. A release with
 * nothing worth telling gets an entry with no lines, and then shows nothing. `WhatsNewLinesTest`
 * fails a bump that has no entry.
 */
data class ReleaseLines(
    val versionCode: Int,
    val versionName: String,
    val lines: List<String>,
    /** Offer "Show me how it works", for the release that brought the walkthrough. */
    val offersWalkthrough: Boolean = false
)

/** Oldest first. The newest is always the version about to ship, or the one that shipped. */
val RELEASE_LINES: List<ReleaseLines> = listOf(
    ReleaseLines(
        versionCode = 25,
        versionName = "3.8.0",
        lines = listOf(
            "Settings has a new row, How Mera Paisa works: a short tour of the app, with arrows on " +
                "its real screens.",
            "After an update, this sheet says what's changed, once."
        ),
        offersWalkthrough = true
    )
)

/** The most lines the sheet shows, however many versions were skipped. */
const val MAX_WHATS_NEW_LINES = 4

/** The longest a line may be, so the sheet stays a glance at double type. */
const val MAX_WHATS_NEW_LINE_CHARS = 120

/** What the sheet says: the newest version shown, and the lines across every version shown. */
data class WhatsNew(
    val versionName: String,
    val lines: List<String>,
    val offersWalkthrough: Boolean,
    /** The newest version shown, to remember as seen once the sheet is closed. */
    val newestCode: Int
)

/**
 * What changed since [lastSeen], or null when there is nothing to tell.
 *
 * Every version after [lastSeen] counts, newest first, taking each version's first line, then
 * each one's second, and so on, up to [MAX_WHATS_NEW_LINES]. A version with no lines adds nothing.
 */
fun whatsNewSince(lastSeen: Int, releases: List<ReleaseLines> = RELEASE_LINES): WhatsNew? {
    val shown = releases.filter { it.versionCode > lastSeen }.sortedByDescending { it.versionCode }
    val told = shown.filter { it.lines.isNotEmpty() }
    if (told.isEmpty()) return null
    val lines = mutableListOf<String>()
    var depth = 0
    while (lines.size < MAX_WHATS_NEW_LINES && told.any { depth < it.lines.size }) {
        for (release in told) {
            if (lines.size == MAX_WHATS_NEW_LINES) break
            release.lines.getOrNull(depth)?.let(lines::add)
        }
        depth++
    }
    return WhatsNew(
        versionName = told.first().versionName,
        lines = lines,
        offersWalkthrough = told.any { it.offersWalkthrough },
        newestCode = shown.first().versionCode
    )
}
