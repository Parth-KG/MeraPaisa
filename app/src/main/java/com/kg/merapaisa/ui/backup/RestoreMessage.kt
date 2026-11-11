package com.kg.merapaisa.ui.backup

import com.kg.merapaisa.data.RestoreMode
import com.kg.merapaisa.data.RestorePlan

/**
 * The title and sentence shown once a restore has been applied.
 *
 * A pure function of the plan, so every wording is checked by a JVM test. A Merge that only gave
 * photos to people already here said "Added 0 people, 0 entries", which read as if nothing had
 * happened; it now says the photos were added.
 */
internal fun restoreDoneMessage(plan: RestorePlan): Pair<String, String> {
    val inserted = plan.inserts
    val photos = plan.photosForPeopleAlreadyHere
    val photoSentence = when (photos) {
        0 -> ""
        1 -> "1 person already here now has the photo from the file."
        else -> "$photos people already here now have their photos from the file."
    }

    if (plan.mode == RestoreMode.Merge && inserted.isZero && photos > 0) {
        val title = if (photos == 1) "Photo added" else "Photos added"
        return title to "$photoSentence Nothing else changed."
    }

    val detail = buildString {
        if (plan.mode == RestoreMode.Replace) append("Your ledger now matches the file exactly. ")
        append("Added ${inserted.people} ${if (inserted.people == 1) "person" else "people"}, ")
        append("${inserted.transactions} ${if (inserted.transactions == 1) "entry" else "entries"}")
        if (inserted.groups > 0) append(", ${inserted.groups} ${if (inserted.groups == 1) "group" else "groups"}")
        append(".")
        val skipped = plan.alreadyPresent
        if (plan.mode == RestoreMode.Merge && !skipped.isZero) {
            val one = skipped.transactions == 1
            append(" ${skipped.transactions} ${if (one) "entry was" else "entries were"} already here and ${if (one) "was" else "were"} left alone.")
        }
        if (photoSentence.isNotEmpty()) append(" ").append(photoSentence)
    }
    return "Ledger restored" to detail
}
