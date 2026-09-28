package com.kg.merapaisa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Every share link this phone has already applied.
 *
 * A link is a file in a chat thread: it can be forwarded, re-sent, or tapped twice by accident,
 * and each of those would otherwise write the same debt again. The payload carries an id minted
 * once when it was built, so recording that id here is what makes applying a link idempotent.
 *
 * **Deliberately no foreign key to `persons`.** Every other table in this schema cascades from
 * the person it belongs to, and this one must not: if you delete someone and then tap their old
 * link again, a cascade would have removed the row that remembers it and the entries would land a
 * second time. The dedupe record has to outlive the person it was about. The cost is that
 * `personId` may point at a row that no longer exists — which is why nothing reads it as a join.
 * It is kept for the audit trail, so "where did this entry come from" has an answer.
 */
@Entity(tableName = "applied_payloads")
data class AppliedPayload(
    @PrimaryKey
    val payloadId: String,
    val appliedAt: Long,
    /** Who it was filed against at the time. Not a foreign key — see the class comment. */
    val personId: Long,
    /** The name the payload claimed, kept as sent. Unverified, like everything in a link. */
    val senderName: String,
    val entryCount: Int,
    val netMinor: Long
)
