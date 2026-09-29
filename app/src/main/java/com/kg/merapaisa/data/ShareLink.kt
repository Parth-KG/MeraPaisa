package com.kg.merapaisa.data

/**
 * The link that carries a [SharePayload] from one phone to another.
 *
 * Two shapes, both understood on import:
 *
 *  - `https://parth-kg.github.io/MeraPaisa/s#<blob>`: what the app sends. Chat apps make an
 *    `https://` link tappable, which a custom scheme does not get, and Android App Links can
 *    route it straight into the app instead of a browser.
 *  - `merapaisa://share#<blob>`: accepted so a link from a build predating the hosted page, or
 *    typed by hand, still works.
 *
 * **The payload sits after the `#` deliberately.** A URL fragment is never sent to the server, so
 * the host named here only ever serves the fallback page. It cannot see anyone's ledger even in
 * a log. It is a delivery address, not a backend. Moving the payload into the path or the query
 * would quietly turn this into a feature that uploads your debts to GitHub.
 */

/** Host serving the fallback page and the App Links `assetlinks.json`. */
const val SHARE_HOST = "parth-kg.github.io"

/** Path of the fallback page. Matched with `pathPrefix` in the manifest's intent filter. */
const val SHARE_PATH = "/MeraPaisa/s"

/** The custom scheme, kept as a fallback that needs no hosting at all. */
const val SHARE_SCHEME = "merapaisa"

/** The canonical link this build produces. */
fun buildShareLink(blob: String): String = "https://$SHARE_HOST$SHARE_PATH#$blob"

/**
 * The message that goes into the share sheet: a sentence a human can read, then the link.
 *
 * The sentence matters. A bare link asks the recipient to tap something unexplained, and this
 * one writes to their ledger, so it says who it is from and what it will do.
 */
fun buildShareMessage(senderName: String, entryCount: Int, netText: String, link: String): String {
    val entries = if (entryCount == 1) "1 entry" else "$entryCount entries"
    return buildString {
        append(senderName)
        append(" sent you a Mera Paisa update link: ")
        append(entries)
        append(", ")
        append(netText)
        append(".\n\nOpen it in Mera Paisa to record your side:\n")
        append(link)
        append("\n\nNo account needed. You will see exactly what it writes before anything changes.")
    }
}

/**
 * Pulls the payload blob out of whatever the user actually gives us.
 *
 * Handles a bare link, a link with chat-app text wrapped around it, and a bare blob pasted on its
 * own. All three happen in practice, and an import screen that only accepted one of them would
 * look broken for the other two. Returns null when there is nothing payload-shaped present.
 *
 * Hand-parsed rather than via `android.net.Uri`, which is an Android framework stub that throws
 * under JVM unit tests. The grammar here is small enough that parsing it directly costs less than
 * the Robolectric dependency would.
 */
fun extractPayloadBlob(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null

    // The blob runs from the marker to the first character that could not be part of one.
    //
    // The alphabet is known exactly, so that boundary is exact, and every way a chat app or a
    // human mangles the end of a link falls outside it. A trailing full stop, a closing quote or
    // bracket, and a tracking fragment appended after the payload were all being read as part of
    // the blob, which failed later as "this link is damaged": a sentence that blames the sender
    // for the app stopping one character too late.
    for (marker in listOf("$SHARE_HOST$SHARE_PATH#", "$SHARE_SCHEME://share#", "$SHARE_SCHEME://share?d=")) {
        val at = text.indexOf(marker, ignoreCase = true)
        if (at >= 0) {
            val rest = text.substring(at + marker.length)
            return rest.takeWhile(::isBlobChar).ifEmpty { null }
        }
    }

    // A blob on its own. Only accept it if every character could be Base64URL, so ordinary
    // pasted prose is rejected here rather than failing later as a damaged payload.
    val candidate = text.takeWhile { !it.isWhitespace() }
    if (candidate.length == text.length && candidate.length >= 8 && candidate.all(::isBlobChar)) {
        return candidate
    }

    return null
}

private fun isBlobChar(c: Char): Boolean =
    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '+' || c == '/' || c == '='
