package com.kg.merapaisa.ui

import com.kg.merapaisa.data.BackupSnapshot
import com.kg.merapaisa.data.ImportOutcome
import com.kg.merapaisa.data.RestoreMode
import com.kg.merapaisa.data.ReconcilePlan
import com.kg.merapaisa.data.RestorePlan
import com.kg.merapaisa.data.SharePayload

/** Which of the two lists is on screen. */
enum class Tab { Active, Settled, Groups }

/**
 * Everything the main screen remembers between frames. It lives in the ViewModel rather than
 * in `remember`, so a rotation no longer throws away the typed amount, the selected person,
 * the current tab or a half-finished split.
 */
data class MainUiState(
    val tab: Tab = Tab.Active,
    val selectedId: Long? = null,
    val input: String = "",
    val note: String = "",
    val showNote: Boolean = false,
    val showAddDialog: Boolean = false,
    val showCreateGroupDialog: Boolean = false,
    /** The group whose detail screen is open, if any. */
    val openGroupId: Long? = null,
    val showAddExpenseDialog: Boolean = false,
    val showSettleUp: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val editingPersonId: Long? = null,
    val historyPersonId: Long? = null,
    val pendingDeleteId: Long? = null,
    /** The group whose delete is waiting on a confirmation, if any. */
    val pendingGroupDeleteId: Long? = null,
    val pendingReminderId: Long? = null,
    val split: SplitFlowState? = null,
    /** The share-link sheet, absent when it is not open. */
    val share: ShareFlowState? = null,
    /** An incoming share link being read or confirmed, absent when none is in flight. */
    val import: ImportFlowState? = null,
    /** Backup and restore, absent when that screen is closed. */
    val backup: BackupFlowState? = null,
    /** An update being checked for, offered, or downloaded. Absent when nothing is in flight. */
    val update: UpdateFlowState? = null,
    /** Moving part of one person's balance onto another, absent when that sheet is closed. */
    val moveDebt: MoveDebtFlowState? = null
)

/** The multi-step split flow, absent when it is not running. */
data class SplitFlowState(
    val step: Int = 0,
    val amount: String = "",
    /** ISO 4217 code the split is entered in: the user's working currency, not a guess. */
    val currency: String = "INR",
    val note: String = "",
    val selectedIds: Set<Long> = emptySet(),
    val includeMe: Boolean = false
)

/**
 * The outgoing side of a share link: what the next link for this person would contain, shown before
 * anything is sent.
 *
 * [senderName] is editable here because the self row ships called "You", which tells the recipient
 * nothing. Confirming the sheet saves it, so it is asked once rather than every time.
 */
data class ShareFlowState(
    val personId: Long,
    val personName: String,
    val currency: String,
    val senderName: String,
    val entryCount: Int,
    val netMinor: Long,
    /** Ignore the watermark and send everything (the way back from a link that never arrived). */
    val fullHistory: Boolean = false,
    /** Nothing to update with because every new entry came from their own links. */
    val onlyTheirsAreNew: Boolean = false,
    val busy: Boolean = false
) {
    val hasNothingToSend: Boolean get() = entryCount == 0
}

/**
 * The incoming side. A link arrives from outside the app, so this models being unable to read it as
 * a first-class state rather than an error to swallow: a user who tapped a link and saw nothing
 * happen would have no idea whether their ledger changed.
 */
sealed interface ImportFlowState {

    /**
     * The user is pasting a link by hand.
     *
     * Not a fallback for completeness: Android only routes a tapped `https://` link into the app
     * once `assetlinks.json` is live at the domain root, so until then this is the *only* path an
     * incoming link can take. It also covers a chat app that refuses to linkify, and a link
     * forwarded as plain text.
     */
    data class Pasting(val text: String = "") : ImportFlowState

    /** Still decoding. Brief, but a link can arrive before the database is open. */
    data object Reading : ImportFlowState

    /** The link could not be read. Nothing has been written. */
    data class Unreadable(val reason: UnreadableReason) : ImportFlowState

    /**
     * Decoded, and waiting for the user to say where it goes and whether to apply it.
     *
     * Nothing is written until this is confirmed. That is not politeness: the payload is
     * unauthenticated, so the user confirming *is* the only check that the link is genuine.
     *
     * Exactly one of [targetPersonId] and [newPersonName] is set. File it against someone who
     * already exists, or create someone for it.
     */
    data class Confirming(
        val payload: SharePayload,
        val targetPersonId: Long? = null,
        val newPersonName: String? = null,
        val busy: Boolean = false,
        /**
         * How this link compares against what the chosen person already has.
         *
         * Null while it is still being worked out, and null for a brand-new person, who by
         * definition has nothing to compare against. Recomputed whenever the target changes,
         * because the same payload against a different person is a different answer.
         */
        val plan: ReconcilePlan? = null,
        /** The uids ticked to apply. Seeded from [ReconcilePlan.defaultSelection]. */
        val selected: Set<String> = emptySet()
    ) : ImportFlowState {
        val canApply: Boolean
            get() = !busy && (targetPersonId != null || !newPersonName.isNullOrBlank())

        /**
         * Whether to show the comparison at all.
         *
         * A plan that is nothing but new entries is the ordinary case and says nothing worth a
         * section: it is what importing a link has always meant. The comparison earns its space
         * only when it has found something the user did not already assume.
         */
        val showsDifferences: Boolean
            get() = plan != null && plan.comparable &&
                (plan.needsDecision || plan.unchanged.isNotEmpty() || plan.onlyYours.isNotEmpty())
    }

    /** Finished, one way or another. [personName] is who it was filed against, for the message. */
    data class Done(val outcome: ImportOutcome, val personName: String) : ImportFlowState
}

/** Why a link could not be read, each needing a different sentence on screen. */
enum class UnreadableReason {
    /** Nothing payload-shaped was found: a stray tap, or the wrong thing pasted. */
    NotALink,

    /** Payload-shaped but did not survive: clipped by a chat app, or edited by hand. */
    Damaged,

    /** A format this build does not know. Refused rather than guessed at. */
    NewerVersion
}

/**
 * Backup and restore.
 *
 * [Reviewing] is the reason this is a state machine rather than a couple of buttons: a restore can
 * delete a ledger, so what it is about to do has to be on screen, in counts, before it happens.
 */
sealed interface BackupFlowState {

    /** The menu: save a backup, restore one, or set up the weekly job. */
    data class Menu(
        val folderName: String? = null,
        val lastRun: Long = 0,
        val lastResult: String? = null,
        val busy: Boolean = false
    ) : BackupFlowState

    /** Reading or writing a file. Brief, but a large ledger on slow storage is not instant. */
    data object Working : BackupFlowState

    /**
     * A file has been read and understood, and nothing has been written yet.
     *
     * [plan] is recomputed whenever [mode] changes, so the counts on screen always describe the
     * mode currently selected rather than the one that was selected when the file was opened.
     */
    data class Reviewing(
        val source: RestoreSource,
        val incoming: BackupSnapshot,
        val exportedAt: Long?,
        val appVersion: String?,
        val mode: RestoreMode,
        val plan: RestorePlan,
        val busy: Boolean = false
    ) : BackupFlowState {
        /**
         * True when Replace would destroy groups the file cannot put back.
         *
         * A CSV carries no group data at all, so replacing from one deletes every group
         * permanently. That is a sentence the user has to see before tapping, not a footnote.
         */
        val replaceWouldLoseGroups: Boolean
            get() = mode == RestoreMode.Replace &&
                incoming.groups.isEmpty() &&
                plan.deletes.groups > 0
    }

    /** The file could not be used. Nothing was written. */
    data class Unreadable(val title: String, val detail: String) : BackupFlowState

    /** Finished, with a sentence describing what changed. */
    data class Done(val title: String, val detail: String) : BackupFlowState
}

/** Which kind of file a restore came from, since the two can restore different amounts. */
enum class RestoreSource {
    /** A full backup: everything, groups included. */
    Json,

    /** The spreadsheet export: people and transactions only. */
    Csv
}

/**
 * Looking for, and installing, a new version.
 *
 * The app is sideloaded, so nothing else will ever tell you a release exists. This flow is
 * deliberately quiet: it checks once a day in the background and only surfaces when there is
 * genuinely something newer, or when you ask it directly from Settings.
 */
sealed interface UpdateFlowState {

    /** Asking GitHub. Only shown when the user asked; the daily check stays silent. */
    data object Checking : UpdateFlowState

    /** Already on the newest release. Only shown for an explicit check. */
    data object UpToDate : UpdateFlowState

    /** Could not ask: no network, rate limited, or an answer that made no sense. */
    data class Unreachable(val reason: String) : UpdateFlowState

    /** A newer release exists. Nothing is downloaded until the user says so. */
    data class Available(
        val version: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val notes: String
    ) : UpdateFlowState

    /** [percent] is -1 when the server does not report a length. */
    data class Downloading(val version: String, val percent: Int) : UpdateFlowState

    /** Downloaded and signature-checked. The system installer takes it from here. */
    data class ReadyToInstall(val version: String, val path: String) : UpdateFlowState

    /**
     * The download was signed by a different key.
     *
     * Its own state, not a Failed with a message, because it means something quite different: not
     * "try again" but "the file you received is not the app you are running". The only safe action
     * is to stop and go to the releases page by hand.
     */
    data object SignatureMismatch : UpdateFlowState

    /**
     * The download or the installer failed. [downloaded] says which, because the two need
     * different sentences: a failed download has nothing on the phone yet, while a failed
     * installer leaves a checked file that simply never opened.
     */
    data class Failed(val reason: String, val downloaded: Boolean = false) : UpdateFlowState

    /** Android will not let the app install anything until this is granted, per-app, in Settings. */
    data class NeedsPermission(val version: String, val downloadUrl: String, val sizeBytes: Long) : UpdateFlowState
}

/**
 * Moving part of what one person owes onto somebody else.
 *
 * The amount is held as typed text rather than a parsed number so the field behaves like every
 * other amount field in the app. A half-typed "12." is a legal thing to be looking at, and
 * parsing on every keystroke would fight the user.
 */
data class MoveDebtFlowState(
    val fromPersonId: Long,
    val fromName: String,
    val currency: String,
    val availableMinor: Long,
    val amount: String = "",
    val toPersonId: Long? = null,
    val note: String = "",
    val busy: Boolean = false,
    /** Set when the attempt was refused, so the sheet can say why without closing. */
    val problem: String? = null
) {
    val amountMinor: Long? get() = com.kg.merapaisa.data.parseAmountToMinor(amount)

    val canMove: Boolean
        get() = !busy && toPersonId != null && (amountMinor ?: 0L) > 0L &&
            (amountMinor ?: 0L) <= availableMinor
}
