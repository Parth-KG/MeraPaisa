package com.kg.merapaisa.ui.whatsnew

/** What the first open of this version shows, once the app is unlocked. */
sealed interface FirstOpen {
    /** Nobody has used this ledger yet: the walkthrough. */
    data object Walkthrough : FirstOpen

    /** An update: what changed since the version last seen. */
    data class News(val whatsNew: WhatsNew) : FirstOpen

    /** Seen already, or nothing worth telling. */
    data object Nothing : FirstOpen
}

/**
 * Tells a new user from an existing one, and an update from an ordinary open (v3.8.0).
 *
 * [lastSeen] is the version last shown, absent on a first run and on every phone that had the app
 * before v3.8.0. Without it, [hasData] decides: anyone in the ledger but you, or any group, is an
 * existing user, who gets this version's news and never the beginner walkthrough. There is no way
 * to know which version they came from, so it is this version's lines only.
 */
fun firstOpen(
    lastSeen: Int?,
    hasData: Boolean,
    current: Int,
    releases: List<ReleaseLines> = RELEASE_LINES
): FirstOpen {
    if (lastSeen == null && !hasData) return FirstOpen.Walkthrough
    val news = whatsNewSince(lastSeen ?: (current - 1), releases)
    return if (news != null) FirstOpen.News(news) else FirstOpen.Nothing
}

/**
 * The version to remember once [open] has been dealt with: this one, or the newest whose lines
 * have now been shown or skipped past. After the walkthrough that is every version written so far,
 * so a new user is never told the news of the version they started on.
 */
fun seenAfter(open: FirstOpen, current: Int, releases: List<ReleaseLines> = RELEASE_LINES): Int = when (open) {
    FirstOpen.Walkthrough -> maxOf(current, releases.maxOfOrNull { it.versionCode } ?: current)
    is FirstOpen.News -> maxOf(current, open.whatsNew.newestCode)
    FirstOpen.Nothing -> current
}
